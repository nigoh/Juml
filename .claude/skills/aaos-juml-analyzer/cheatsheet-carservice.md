# CarService / CarManager Architecture Cheatsheet

## CarService 階層構成

```
packages/services/Car/
├── service/  (main daemon)
│   └── src/
│       ├── CarService.java  (entry point)
│       ├── CarLocalServices.java  (service manager)
│       ├── managers/
│       │   ├── CarPropertyService.java
│       │   ├── CarAudioService.java
│       │   ├── CarOccupantZoneService.java
│       │   ├── CarWindowManagerService.java
│       │   └── ... (more managers)
│       └── hal/
│           ├── HalClient.java  (IVehicle AIDL wrapper)
│           └── ...
├── car-lib/  (client API, framework 搭載)
│   └── src/
│       ├── android/car/  (public API)
│       │   ├── Car.java  (main facade)
│       │   ├── CarManagerFactory.java
│       │   ├── CarPropertyManager.java
│       │   ├── CarAudioManager.java
│       │   ├── CarOccupantZoneManager.java
│       │   └── ... (manager interfaces)
│       └── ...
└── aidl/  (AIDL interface)
    └── android/car/
        ├── ICar.aidl  (main service interface)
        ├── ICarAudio.aidl
        ├── ICarProperty.aidl
        └── ...
```

---

## CarService ライフサイクル

### 起動フロー

```
init.rc
  ↓ spawn
CarService process
  ↓
CarService.onCreate()
  ↓
CarLocalServices.init()
  ├─→ AudioService.init()
  ├─→ OccupantZoneService.init()
  ├─→ PropertyService.init()
  │     ↓
  │   HalClient.connect() → IVehicle (VHAL)
  ├─→ WindowManagerService.init()
  └─→ ... (other managers)
  ↓
CarService.onBootPhase(systemBootPhase)
  (PHASE_SYSTEM_SERVICES_READY)
  ├─→ Manager.onSystemBootPhase()
  └─→ ...
  ↓
onCarServiceReady()  (broadcast intent)
```

### User Lifecycle

```
onUserLifecycle(userId, transitionType, eventTime)
  ├─ eventType: STARTING, SWITCHING, UNLOCKING, STOPPING, etc.
  └─→ all managers.onUserLifecycleEvent()
```

---

## CarLocalServices (Service Registry)

CarLocalServices は **Singleton** サービスレジストリ。

```java
public class CarLocalServices {
    private static final Map<Class<?>, Object> sServiceMap = ...;
    
    public static <T> T getService(Class<T> serviceClass) {
        return (T) sServiceMap.get(serviceClass);
    }
    
    // 初期化時に全 Manager を register
    public void startAllServices(CarService carService) {
        // e.g.,
        // registerService(CarAudioService.class, new CarAudioService(...));
        // registerService(CarPropertyService.class, new CarPropertyService(...));
    }
}
```

### 登録されるサービス

| Class | 役割 |
|---|---|
| `CarAudioService` | Audio policy + priority |
| `CarPropertyService` | Vehicle Property getter/setter + subscription |
| `CarOccupantZoneService` | User ↔ Zone mapping |
| `CarWindowManagerService` | Multi-display management |
| `CarInputService` | Input routing (steering wheel, buttons) |
| `CarSensorService` | Sensor aggregation |
| `CarProjectionService` | Projection (Android Auto) |
| `CarDiagnosticService` | Diagnostic data |
| `CarExperimentalFeatureService` | Experimental features |

---

## CarPropertyService (Vehicle Property Access)

### VHAL との通信パターン

```
CarPropertyManager.getProperty(prop)
  ↓ (Binder IPC)
CarPropertyService.getProperty(prop)
  ↓
HalClient.get(prop)
  ↓ (AIDL call)
IVehicle.get(vehicleProperty)  ← VHAL daemon
```

### subscribe パターン

```
CarPropertyManager.registerCallback(callback, props)
  ↓ (Binder IPC)
CarPropertyService.subscribe()
  ↓
HalClient.subscribe()
  ↓ (AIDL call)
IVehicle.subscribe(listener, props)  ← VHAL daemon

// 値変更時
VHAL: onPropertyEvent(value, timestamp)
  ↓ (Binder callback)
CarPropertyService callback list
  ↓
CarPropertyManager callback
  ↓
Client application
```

---

## CarManager 各種

### 1. CarPropertyManager

```java
public class CarPropertyManager {
    public Object getProperty(int propId) { ... }
    public void setProperty(int propId, Object value) { ... }
    public void registerCallback(CarPropertyManager.CarPropertyEventCallback callback,
                                 int... propIds) { ... }
}
```

**用途**: VehicleProperty (速度, 位置, エンジン状態など) の読み書き。

### 2. CarAudioManager

```java
public class CarAudioManager {
    public void requestAudioFocus(int usage) { ... }
    public void abandonAudioFocus() { ... }
    public int getGroupVolume(int groupId) { ... }
    public void setGroupVolume(int groupId, int index) { ... }
}
```

**用途**: 自動車向けオーディオポリシー (複数 zone, 優先度制御)。

### 3. CarOccupantZoneManager

```java
public class CarOccupantZoneManager {
    public List<CarOccupantZone> getOccupantZones(int type) { ... }
    public int getAffectedUserId(int occupantZoneId) { ... }
    public void registerOccupantZoneChangeListener(CarOccupantZone.OccupantZoneChangeListener l) { ... }
}
```

**用途**: User ↔ 座席位置 (driver, passenger, rear) mapping。

### 4. CarInputManager

```java
public class CarInputManager {
    public void setInputEventListener(CarInputManager.CarInputEventListener listener,
                                      int... keys) { ... }
}
```

**用途**: ハンドルボタン, ロータリーコントローラ, タッチスクリーン 入力。

### 5. CarWindowManagerService / DisplayManager

```
// Multi-display (instrument cluster, infotainment, rear display)
registerCarDisplayListener();
onDisplayChanged(displayInfo);
  ├─ main (infotainment)
  ├─ cluster (instrument, not available to 3P apps)
  └─ rear (optionally, for rear seat)
```

---

## Car.java (Client Facade)

Framework の Car API エントリポイント:

```java
public class Car {
    private final static String SERVICE_NAME = "car_service";
    
    public static Car createCar(Context context) {
        // return CarManager instance
        // (IPC connection to CarService)
    }
    
    public <T> T getCarManager(String serviceName) {
        // getCarManager(Car.AUDIO_SERVICE) → CarAudioManager
        // getCarManager(Car.PROPERTY_SERVICE) → CarPropertyManager
        // etc.
    }
}
```

**用途**: App は `Car.createCar()` で IPC 接続を確立。

---

## ICar AIDL Interface

```aidl
package android.car;

interface ICar {
    oneway void setCarServiceHelper(ICarServiceHelper helper);
    
    ICarAudio getAudio();
    ICarProperty getProperty();
    ICarOccupantZone getOccupantZoneService();
    
    void registerEventListener(in ICarEventListener listener, int eventMask);
}
```

**用途**: Binder IPC の low-level インタフェース。
通常 App 開発者は `Car.java` facade を使用 (ICar は隠蔽)。

---

## CarPropertyManager 実装パターン

### get/set (同期)

```java
public Object getProperty(int propId) {
    HalClient.VehiclePropertyValue val = mHalClient.get(propId);
    return val.value;
}
```

### subscribe (非同期コールバック)

```java
public void registerCallback(CarPropertyEventCallback callback, int... propIds) {
    mHalClient.subscribe(propIds, (propId, value) -> {
        for (CarPropertyEventCallback cb : callbacks) {
            cb.onChangeEvent(propId, value);
        }
    });
}
```

---

## Service との連携例

### CarAudioService + PropertyService

```
CarAudioService.playSound(soundType)
  ↓
getProperty(AUDIO_VOLUME_CLASS_MUSIC) ← CarPropertyService
  ↓ (get VHAL)
IVehicle.get(AUDIO_VOLUME) → actual volume
  ↓
setAudioVolume(level)
```

### CarInputService + WindowManagerService

```
key event (steering wheel button)
  ↓ (HAL input event)
CarInputService.onKeyEvent()
  ↓
route to active app's input method
  ↓ (via WindowManagerService)
active window
```

---

## Juml での可視化

### クラス図 (class diagram)

```sh
java -Xmx8g -jar Juml.jar -c -o carservice-class.svg \
  ~/AOSP/packages/services/Car/service/src
```

**可視化ポイント**:
- CarLocalServices (registry)
- Manager 各種 (CarPropertyService, CarAudioService 等)
- Binder proxy / stub
- HalClient (IVehicle wrapper)

### シーケンス図 (sequence diagram)

```sh
java -Xmx8g -jar Juml.jar -q "CarPropertyService.getProperty" \
  -o property-get-seq.svg \
  ~/AOSP/packages/services/Car/service/src
```

**可視化**: CarPropertyManager → CarPropertyService → HalClient → IVehicle call chain。

### Manifest 図 (component diagram)

```sh
java -jar Juml.jar -M -o carservice-manifest.svg \
  ~/AOSP/packages/services/Car
```

**可視化**: CarService 宣言, 権限, 配置。

---

## デバッグコマンド

```sh
# CarService log
adb logcat | grep -i "carservice\|carproperty"

# CarService は privileged service (system_server の一部、またはリモート daemon)
adb shell ps | grep carservice

# Service 状態確認
adb shell dumpsys car_service

# Property list
adb shell dumpsys car_service --list-properties

# 特定 property 値確認
adb shell dumpsys car_service --property VehicleProperty.PERF_VEHICLE_SPEED

# VHAL 接続状態
adb shell dumpsys car_service --vhal-info
```

---

## Module Dependencies

```
packages/services/Car/
├── service/
│   └── depends on car-lib, aidl, framework, ...
├── car-lib/
│   └── depends on framework, aidl
├── aidl/
│   └── no dependencies (definition only)
└── vehicle-hal-client/
    └── depends on car-lib, hardware/interfaces/automotive/vehicle/aidl
```

Juml -G (Gradle 依存図) で可視化可能:

```sh
java -jar Juml.jar -G -o car-deps.svg ~/AOSP/packages/services/Car
```
