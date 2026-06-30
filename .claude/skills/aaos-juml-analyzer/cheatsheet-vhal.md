# Vehicle HAL (VHAL) Cheatsheet

## Vehicle HAL アーキテクチャ (2026 年)

```
IVehicle AIDL interface
├── get(vehicleProperty) → VehiclePropertyValue
├── set(vehicleProperty, value) → StatusCode
├── subscribe(listener, propIds) → StatusCode
├── unsubscribe(listener, propIds) → StatusCode
└── ...

VehicleProperty enum (定義)
├── PERF_VEHICLE_SPEED = 291504647
├── GEAR_SELECTION = 289410873
├── ENGINE_OIL_TEMP = 291505926
├── HVAC_*
├── INFO_*
└── ... (400+ properties)

VehiclePropertyValue (値コンテナ)
├── prop (VehicleProperty)
├── timestamp (nanoseconds)
├── status (PropertyStatus)
├── value (union: int32_t, float, string, bytes, etc.)
└── areaId (zone for multi-area properties)
```

---

## IVehicle AIDL インタフェース

### 定義ファイル

```
hardware/interfaces/automotive/vehicle/aidl/
├── android/hardware/automotive/vehicle/
│   ├── IVehicle.aidl
│   ├── IVehicleCallback.aidl
│   ├── VehicleProperty.aidl
│   ├── VehiclePropertyValue.aidl
│   ├── PropertyStatus.aidl
│   ├── GetValueStatus.aidl
│   ├── SetValueStatus.aidl
│   ├── VehicleAreaDoor.aidl
│   ├── VehicleAreaWindow.aidl
│   ├── VehicleAreaSeat.aidl
│   └── ...
└── Android.bp
```

### メインメソッド

```aidl
interface IVehicle {
    GetValueResult get(int propertyId, in GetValueRequest request);
    void set(in SetValueRequest request);
    
    void subscribe(in IVehicleCallback callback, in SubscribeOptions[] options);
    void unsubscribe(in IVehicleCallback callback, int propertyId);
    
    int getInterfaceVersion();
    String getInterfaceHash();
}
```

### VehicleProperty enum (参考値)

```aidl
const int PERF_VEHICLE_SPEED = 291504647;
const int GEAR_SELECTION = 289410873;
const int ENGINE_OIL_TEMP = 291505926;
const int HVAC_POWER_ON = 320865410;
const int HVAC_FAN_SPEED = 356516106;
const int HVAC_TEMPERATURE_SET = 358614275;
const int INFO_MAKE = 286261504;
const int INFO_MODEL = 286261505;
const int INFO_MODEL_YEAR = 287310863;
const int DOOR_LOCK = 354419456;
const int WINDOW_POS = 356518784;
const int SEAT_MEMORY_SELECT = 356518787;
// ... 400+ properties total
```

---

## VHAL プロセスアーキテクチャ

```
system/
  ├── CarService
  │   └── CarPropertyService
  │       ↓ (Binder IPC)
  │       HalClient (IVehicle proxy)
  │           ↓ (AIDL)
  │           IVehicle (Binder)
  │
vendor/
  └── hwbinder daemon (separate process)
      ├── IVehicle implementation (C++ native)
      │   ├── GetValue (read from hardware)
      │   ├── SetValue (write to hardware)
      │   └── Subscribe (set up listener)
      └── Hardware driver interface
          ├── CAN bus
          ├── Serial (e.g., OBD)
          ├── Sensors
          └── Memory-mapped I/O
```

---

## 通信パターン 1: get (同期)

```
CarPropertyManager.getProperty(PERF_VEHICLE_SPEED)
  ↓ (app thread)
CarPropertyService.getProperty()
  ↓ (Binder IPC)
IVehicle_Proxy.get(GetValueRequest)
  ↓ (vendor hwbinder daemon)
IVehicle_Stub::onTransact
  ↓
VehicleImpl::get()
  ↓
readFromHardware(CAN_BUS_SPEED_DATA)
  ↓
return GetValueResult { status, value }
```

---

## 通信パターン 2: subscribe (非同期)

```
CarPropertyManager.registerCallback(callback, PERF_VEHICLE_SPEED)
  ↓ (Binder IPC)
CarPropertyService.subscribe()
  ↓
IVehicle_Proxy.subscribe(listener, SubscribeOptions)
  ↓ (vendor hwbinder)
VehicleImpl::subscribe()
  ├─ register listener
  └─ start monitoring hardware
  
// 値変更時
hardware event (CAN message)
  ↓
VehicleImpl::onPropertyEvent()
  ↓ (Binder callback)
IVehicleCallback_Proxy.onPropertyEvent(VehiclePropertyValue)
  ↓ (system)
CarPropertyService.onPropertyEvent()
  ↓ (callback list)
CarPropertyManager.CarPropertyEventCallback
  ↓
App.onChangeEvent()
```

---

## VehiclePropertyValue 構造

```aidl
parcelable VehiclePropertyValue {
    int propertyId;
    long timestamp;
    int areaId;
    int status;
    int[] int32Values;
    long[] int64Values;
    float[] floatValues;
    String[] stringValue;
    byte[] byteValues;
    GetValueStatus getStatus;
}
```

**用途**: 値のコンテナ。型は property による。

| Property | 値型 | 例 |
|---|---|---|
| PERF_VEHICLE_SPEED | float | 60.5 (km/h) |
| GEAR_SELECTION | int | Gear.DRIVE |
| ENGINE_OIL_TEMP | int | 98 (°C) |
| HVAC_TEMPERATURE_SET | float | 22.0 (°C) |
| INFO_MAKE | String | "Toyota" |
| DOOR_LOCK | boolean (int32) | 1 = locked |

---

## Area / Zone Support

複数エリア対応 (e.g., 複数 HVAC ゾーン):

```
areaId 1: HVAC_TEMPERATURE_SET = 22°C (driver zone)
areaId 2: HVAC_TEMPERATURE_SET = 20°C (passenger zone)
areaId 3: HVAC_TEMPERATURE_SET = 19°C (rear zone)
```

### VehicleAreaDoor / VehicleAreaSeat など

```aidl
const int VEHICLE_AREA_TYPE_DOOR = 1;
const int VEHICLE_AREA_TYPE_SEAT = 2;
const int VEHICLE_AREA_TYPE_WINDOW = 3;
const int VEHICLE_AREA_TYPE_HVAC = 4;

const int VEHICLE_AREA_DOOR_DRIVER = 0x1;
const int VEHICLE_AREA_DOOR_PASSENGER = 0x2;
const int VEHICLE_AREA_DOOR_REAR_LEFT = 0x4;
const int VEHICLE_AREA_DOOR_REAR_RIGHT = 0x8;
```

---

## CarPropertyService との連携

### HalClient クラス

```java
class HalClient {
    private IVehicle mHalProxy;  // Binder proxy to VHAL
    
    public VehiclePropertyValue get(int propId) {
        GetValueRequest req = new GetValueRequest();
        req.propertyId = propId;
        GetValueResult result = mHalProxy.get(propId, req);
        return result.value;
    }
    
    public void subscribe(int[] propIds, OnPropertyEventCallback listener) {
        mHalProxy.subscribe(new IVehicleCallback() {
            @Override
            public void onPropertyEvent(VehiclePropertyValue value) {
                listener.onEvent(value);
            }
        }, propIds);
    }
}
```

---

## Binder Callback (onPropertyEvent)

VHAL daemon → CarPropertyService への逆コール:

```
IVehicleCallback (Binder interface)
├── onPropertyEvent(VehiclePropertyValue[] values)  ← VHAL が call
└── onPropertySetError(int errorCode, int[] propIds)
```

### 実装例 (CarPropertyService 側)

```java
private final IVehicleCallback mHalCallback = new IVehicleCallback.Stub() {
    @Override
    public void onPropertyEvent(VehiclePropertyValue[] values) {
        for (VehiclePropertyValue v : values) {
            notifyPropertyListeners(v);
        }
    }
};
```

---

## Property Status

```aidl
const int AVAILABLE = 0;
const int UNAVAILABLE = 1;
const int ERROR = 2;
```

property が:
- AVAILABLE: 正常
- UNAVAILABLE: hardware 非対応・未接続
- ERROR: hardware エラー

---

## Juml での VHAL 可視化

### AIDL インタフェース図

```sh
java -Xmx4g -jar Juml.jar -c -o ivehicle-aidl.svg \
  ~/AOSP/hardware/interfaces/automotive/vehicle/aidl
```

**可視化**:
- IVehicle interface メソッド
- VehicleProperty enum (定数一覧)
- Parcelable 型 (VehiclePropertyValue, GetValueRequest など)
- IVehicleCallback (callback interface)

### CarPropertyService + VHAL 連携図

```sh
# CarPropertyService 側
java -Xmx8g -jar Juml.jar -q "CarPropertyService.getProperty" \
  -o property-get-chain.svg \
  ~/AOSP/packages/services/Car/service/src
```

**可視化**:
- CarPropertyService.getProperty()
- HalClient.get()
- IVehicle_Proxy.get() (AIDL-generated)

---

## VHAL 実装パターン

### C++ default implementation

```
hardware/interfaces/automotive/vehicle/aidl/default/
├── Vehicle.h / Vehicle.cpp  (IVehicle implementation)
├── VehicleHardware.h / VehicleHardware.cpp  (HW abstraction)
├── VehiclePropertyStore.h / .cpp  (property storage)
└── service.cpp (entry point)
```

Juml で見えるのは AIDL interface 層のみ。
Native C++ impl は別途 Grep / コードレビューで確認。

---

## 全 Property リスト確認

```sh
grep "const int " \
  ~/AOSP/hardware/interfaces/automotive/vehicle/aidl/android/hardware/automotive/vehicle/VehicleProperty.aidl | wc -l

# 出力例: 450+ properties
```

---

## デバッグコマンド

```sh
# VHAL daemon 状態
adb shell dumpsys android.hardware.automotive.vehicle.IVehicle/default

# 特定 property 読み取り (adb コマンド経由)
adb shell getprop | grep -i vehicle

# dumpsys で CarPropertyService 経由に確認
adb shell dumpsys car_service --property PERF_VEHICLE_SPEED
```

---

## VINTF マニフェスト

```xml
<manifest>
    <hal format="aidl">
        <name>android.hardware.automotive.vehicle</name>
        <interface>
            <name>IVehicle</name>
            <instance>default</instance>
        </interface>
    </hal>
</manifest>
```

boot 時にバージョン検証。不一致で init crash。

---

## Binder IPC オーバーヘッド

VHAL は Binder 経由のため、レイテンシあり。
超低レイテンシ HAL (e.g., real-time audio) は別スタック (FastMessenger など)。
通常自動車 sensor readout は millisecond 単位で OK。
