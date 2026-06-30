# AAOS Security / MultiUser / Permissions Cheatsheet

## Car.PERMISSION_* 権限体系

### 主要権限

| Permission | 用途 | 保護レベル |
|---|---|---|
| `Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME` | オーディオ volume 制御 | dangerous |
| `Car.PERMISSION_CAR_ENERGY` | エネルギー (fuel, battery) property | dangerous |
| `Car.PERMISSION_CAR_POWERTRAIN` | Powertrain (engine, transmission) | dangerous |
| `Car.PERMISSION_CAR_MILEAGE` | Mileage データ | dangerous |
| `Car.PERMISSION_CAR_INFO` | 車両情報 (make, model, year) | normal |
| `Car.PERMISSION_CAR_SPEED` | 速度データ | dangerous |
| `Car.PERMISSION_CAR_DYNAMICS` | 動的データ (accel, brake) | dangerous |
| `Car.PERMISSION_CAR_OCCUPANT_AWARENESS` | 乗員検知 | dangerous |
| `Car.PERMISSION_CAR_DOOR_LOCK` | ドア lock/unlock | dangerous |
| `Car.PERMISSION_CAR_WINDOWS` | ウィンドウ制御 | dangerous |

### Protection Level

- **normal**: user consent 不要 (AndroidManifest 宣言のみ)
- **dangerous**: runtime permission (Permissions API, user approval)
- **signature**: system app のみ (package signature)
- **privileged**: /system/priv-app/ のみ

---

## MultiUser / CarOccupantZone

### ユーザロール

| Role | User ID | 座席 | 権限 |
|---|---|---|---|
| **Driver** | 10 (primary) | Front Driver | 全ての Car.PERMISSION_* |
| **Passenger** | 11+ (secondary) | Front Passenger / Rear | 制限 (PERMISSION_CAR_INFO など読専のみ) |
| **Guest** | 12+ (guest) | Rear | 最小限 (アプリ実行のみ) |

### CarOccupantZone mapping

```
OccupantZoneId 0: ZONE_ROW_1_LEFT (driver)
  ↓ user 10 (primary)
  
OccupantZoneId 1: ZONE_ROW_1_RIGHT (front passenger)
  ↓ user 11
  
OccupantZoneId 2: ZONE_ROW_2_LEFT (rear left)
  ↓ user 12
  
OccupantZoneId 3: ZONE_ROW_2_RIGHT (rear right)
  ↓ user 12
```

### CarOccupantZoneManager API

```java
// Driver 判定
List<CarOccupantZone> driverZones = 
    mOccupantZoneMgr.getOccupantZones(OCCUPANT_TYPE_DRIVER);

// Zone → User mapping
int userId = mOccupantZoneMgr.getAffectedUserId(zoneId);

// User → Zone 逆向き
int zoneId = mOccupantZoneMgr.getOccupantZoneIdForUser(userId);

// Listener
mOccupantZoneMgr.registerOccupantZoneChangeListener(
    new CarOccupantZone.OccupantZoneChangeListener() {
        @Override
        public void onOccupantZoneChanged(
            int flags, List<CarOccupantZone> changedZones) {
            // user switched, zone config changed, etc.
        }
    });
```

---

## Permission Check in CarService

### enforcePermission

CarService 実装側の check pattern:

```java
public class CarPropertyService {
    private final Context mContext;
    private final CarAudioService mAudioService;
    
    public VehiclePropertyValue getProperty(int propId) {
        // permission check
        if (propId == VehicleProperty.PERF_VEHICLE_SPEED) {
            mContext.enforceCallingPermission(
                Car.PERMISSION_CAR_SPEED, "Access speed data");
        }
        
        // occupant zone check (driver only?)
        int userId = UserHandle.getCallingUserId();
        if (!isDriver(userId)) {
            throw new SecurityException("Only driver can access speed");
        }
        
        // call HAL
        return mHalClient.get(propId);
    }
}
```

### Caller Context (calling package)

```java
String callerPackage = getCallingPackage();
int callerUid = Binder.getCallingUid();
int callerPid = Binder.getCallingPid();
int callerUserId = UserHandle.getCallingUserId();
```

---

## AAOS sepolicy (SELinux)

```
system/sepolicy/public/carservice.te:
    type carservice, domain;
    type carservice_exec, exec_type, file_type;
    
    # allow carservice to talk to HAL
    hal_client_domain(carservice, vehicle_hal)
    
    # allow carservice to read audio
    hal_client_domain(carservice, audio)

system/sepolicy/private/carservice_hal.te:
    # vehicle HAL domain
    type vehicle_hal, domain;
    allow vehicle_hal sysfs : file { read write };
    allow vehicle_hal device : dir { search };
    allow vehicle_hal hwbinder_device : chr_file { read write ioctl };
```

### avc: denied debugging for CarService

```sh
# If CarService cannot access VHAL:
avc: denied { ioctl } for pid=XXX \
  scontext=u:r:carservice:s0 \
  tcontext=u:object_r:hwbinder_device:s0 \
  tclass=chr_file

# Fix: add to carservice.te
allow carservice hwbinder_device : chr_file { ioctl };
```

---

## Occupant Zone Listener Pattern

Zone 変更時 (seat switched, user removed):

```
User/Occupant physically moved
  ↓ (detected by seat sensor → VHAL)
VehicleProperty.SEAT_OCCUPANCY_CHANGE
  ↓ (CarPropertyService notified)
CarOccupantZoneManager.onOccupantZoneChanged(flags, zones)
  ↓ (all listeners)
CarService / CarAudioService / CarInputService
  ├─→ remap user_id
  ├─→ update permission checks
  └─→ redirect audio/input to new zone
```

### Listener Implementation

```java
@Override
public void onOccupantZoneChanged(
    int flags, List<CarOccupantZone> changedZones) {
    for (CarOccupantZone zone : changedZones) {
        int zoneId = zone.getZoneId();
        int userId = zone.getOccupantType() == OCCUPANT_TYPE_DRIVER ? 
            UserHandle.USER_SYSTEM : // driver is primary
            mZoneToUserMapping.get(zoneId);  // look up secondary
        
        // update permission cache
        mZonePermissionCache.put(zoneId, userId);
        
        // reroute audio if needed
        if (zoneId == mCurrentAudioZone) {
            updateAudioRouting(zoneId);
        }
    }
}
```

---

## Privilege Escalation Prevention

AAOS では passenger/guest apps が driver-only property にアクセス不可:

```java
// CarPropertyService のチェック
private boolean isDriverOnly(int propId) {
    // SPEED, BRAKE, DOOR_LOCK など
    return DRIVER_ONLY_PROPERTIES.contains(propId);
}

public VehiclePropertyValue getProperty(int propId) {
    int userId = UserHandle.getCallingUserId();
    int zoneId = mOccupantZoneMgr.getOccupantZoneIdForUser(userId);
    boolean isDriver = (zoneId == ZONE_ROW_1_LEFT);
    
    if (isDriverOnly(propId) && !isDriver) {
        throw new SecurityException(
            "Only driver can access property " + propId);
    }
}
```

---

## Permission Enforcement Flow

```
Application (e.g., 3P CarLauncher)
  ↓
CarManager.setProperty(propId, value)
  ↓ (Binder IPC)
CarPropertyService.setProperty()
  ├─ enforceCallingPermission(PERMISSION_CAR_DOOR_LOCK)
  │   ↓ (/system/etc/permissions/platform.xml から読込)
  │   check app manifest <uses-permission>
  │   ↓
  │   if granted, OK; if not, SecurityException
  ├─ check isDriver(userId)
  ├─ check VINTF (SELinux propery domain)
  └─→ IVehicle.set() via HalClient
```

---

## GMS / Google Play Protect Integration

Google Play app:
- PLAY_BILLING, ACCESS_NETWORK_STATE など標準権限

AAOS proprietary:
- Car.PERMISSION_* 群は GMS Play Protect scope 外
- OEM が独自権限スキーム整備必要

---

## Device Admin / Ownership Model

AAOS では device ownership が異なる:

```
Traditional Android:
  Device owner = default user (multi-user OK)
  
AAOS:
  Device owner = OEM / fleet manager
  Driver = primary user (使用者)
  Passenger = secondary user (制限)
```

MDM (Mobile Device Management) / EMM は:
- Device policy 設定 (app installation, permission policy)
- Passenger user へのアプリ制限
- Driver-only setting の保護

---

## SELinux Domain Transitions

```
init
  ↓ exec /system/bin/carservice (carservice_exec type)
carservice domain
  ├─ spawns threads (all carservice domain)
  └─ IPC via Binder
      ↓
HAL daemon (vehicle_hal domain)
```

Domain は process scope。fork/execve で遷移。
Binder IPC では呼び出し元 domain context 保持。

---

## Juml での可視化

### CarService 権限チェック

```sh
java -Xmx8g -jar Juml.jar -c -o carservice-perm.svg \
  ~/AOSP/packages/services/Car/service/src
```

Grep で permission enforcement ポイント特定:

```sh
grep -r "enforceCallingPermission\|checkPermission" \
  ~/AOSP/packages/services/Car/service/src | head -20
```

### Manifest permissions

```sh
java -jar Juml.jar -M -o carservice-manifest.svg \
  ~/AOSP/packages/services/Car
```

宣言 permission, protected components を可視化。

---

## デバッグコマンド

```sh
# current user
adb shell am get-current-user

# occupant zones
adb shell dumpsys car_service --occupant-zones

# permissions granted to app
adb shell dumpsys package com.example.app | grep "requested permissions"

# SELinux status
adb shell getenforce  # enforcing / permissive

# avc denied logs
adb logcat | grep "avc: denied"
```
