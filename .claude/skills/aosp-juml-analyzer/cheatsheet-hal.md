# HAL / AIDL / HIDL Cheatsheet

## HAL 進化系統

| 世代 | 言語 | 定義ファイル | 通信 | 現状 |
|---|---|---|---|---|
| **HIDL** (HAL Interface Definition Language, API 26-34) | C++/Java | `.hal` | Binder | ⚠️ 段階廃止 |
| **AIDL** (Android Interface Definition Language, API 31+) | Java/Kotlin/C++ | `.aidl` | Binder | ✓ 推奨 |
| **Native HAL** (レガシ, ~API 28) | C/C++ | `.h` (C header) | direct function call | ✗ 廃止 |

---

## AIDL (推奨)

### AIDL インタフェース定義

```
hardware/interfaces/audio/aidl/
├── android/hardware/audio/  (namespace)
│   ├── IStreamIn.aidl
│   ├── IStreamOut.aidl
│   ├── IModule.aidl
│   └── common/  (shared types)
│       ├── AudioDevice.aidl
│       └── AudioFormat.aidl
└── Android.bp
```

### AIDL 文法例

```aidl
// IStreamOut.aidl
package android.hardware.audio;

interface IStreamOut {
    oneway void pause();
    int read(in int numFrames);
    int write(in byte[] data);
}

// Parcelable 型
parcelable AudioPort {
    int deviceType;
    String deviceName;
}
```

### AIDL Binder 通信

```
Client (e.g., AudioServer)
    ↓ (Binder call)
AIDL-generated Java proxy
    ↓ (Parcel marshalling)
Kernel (Binder driver)
    ↓ (deserialize)
HAL Daemon process
    ↓
Native implementation (C++)
```

---

## HIDL (段階廃止, API 34 まで)

### HIDL インタフェース定義

```
hardware/interfaces/audio/2.0/  (version)
├── IStreamIn.hal
├── IStreamOut.hal
├── IModule.hal
├── types.hal  (共有型)
└── Android.bp
```

### HIDL 文法例

```hidl
// IStreamOut.hal
package android.hardware.audio@2.0;

interface IStreamOut {
    pause();
    read(uint32_t numFrames) generates (Result result, ...);
    write(vec<uint8_t> data);
};

struct AudioPort {
    int32_t deviceType;
    string deviceName;
};
```

### HIDL → AIDL 移行

```
AudioServer (client)
  ↓
HIDL proxy (deprecated)
  ↓
HAL Daemon (HIDL stable interface)
  ↓
AIDL impl (new) ← 移行対象

// または完全に AIDL に統一
AudioServer (client)
  ↓
AIDL proxy
  ↓
HAL Daemon (AIDL interface)
  ↓
AIDL impl
```

---

## VINTF (Vendor Interface)

HAL インタフェースのバージョン管理・互換性チェック:

```
system/sepolicy/public/
  └── audio.te  (HAL definition)

device/<oem>/<board>/
  ├── manifest.xml      (provided by vendor)
  └── compatibility_matrix.xml (required by system)
```

### manifest.xml (OEM/device 側)

```xml
<manifest>
    <hal format="aidl">
        <name>android.hardware.audio</name>
        <interface>
            <name>IModule</name>
            <instance>default</instance>
        </interface>
    </hal>
</manifest>
```

### compatibility_matrix.xml (system 側)

```xml
<compatibility-matrix>
    <hal format="aidl">
        <name>android.hardware.audio</name>
        <version>1</version>
        <interface>
            <name>IModule</name>
            <instance>default</instance>
        </interface>
    </hal>
</compatibility-matrix>
```

Boot 時に互換性チェック。不一致 → `init` crash。

---

## HAL 実装パターン

### AIDL default implementation

```
hardware/interfaces/audio/aidl/
├── android/hardware/audio/  (definition)
│   └── IModule.aidl
└── default/  (implementation)
    ├── StreamIn.java
    ├── StreamOut.java
    ├── Module.java
    └── Android.bp
```

### C++ HAL Daemon (system service)

```
frameworks/av/services/audioflinger/
  ├── HALInterfaceImpl.cpp  (AIDL impl)
  └── Android.bp
```

---

## binderized vs passthrough (HIDL のみ)

| 方式 | プロセス分離 | 用途 | 現在 |
|---|---|---|---|
| **binderized** | ○ (別プロセス HAL daemon) | 標準 | AIDL も同じ |
| **passthrough** | ✗ (client 同じプロセス link) | low-latency / 小規模 HAL | レガシ |

AIDL は常に binderized。

---

## VNDK (Vendor NDK)

Vendor partition が使用できる native ライブラリの「安定化」セット:

```
system/memory/
  libs/vendor_ndk/  ← vendor がリンク可能
  
vendor/lib/
  (OEM が独自ライブラリ配置)
```

### Juml での捕捉

Native HAL の **C++ 実装本体** は Juml の対象外 (Java/AIDL パーサのため)。
ただし AIDL 層と「契約 → 実装」対応は第一級で捕捉できる:

1. AIDL/Java インタフェース層を `-c` でクラス図化
2. `--aidl-binding` で AIDL interface ↔ `Stub` 継承実装クラスの対応表を生成
3. C++ impl 本体だけは grep で参照箇所を補完

```sh
# AIDL インタフェース図
java -Xmx4g -jar Juml.jar -c -o /tmp/aosp-out/hal/audio-aidl.svg \
  ~/AOSP/hardware/interfaces/audio/aidl

# AIDL ↔ Stub 実装の紐付け表 (Markdown)
java -Xmx4g -jar Juml.jar --aidl-binding -o /tmp/aosp-out/hal/ \
  ~/AOSP/hardware/interfaces/audio

# C++ impl 本体のみ grep で補完 (Juml 対象外)
grep -r "IModule::.*(" ~/AOSP/hardware/interfaces/audio/default
```

---

## HAL をクラス図で捕捉

### ステップ 1: AIDL インタフェース図化

```sh
# Audio HAL AIDL
java -Xmx4g -jar Juml.jar -c -o /tmp/aosp-out/hal/audio-aidl.svg \
  ~/AOSP/hardware/interfaces/audio/aidl

# Vehicle HAL AIDL (AAOS)
java -Xmx4g -jar Juml.jar -c -o /tmp/aosp-out/hal/vehicle-aidl.svg \
  ~/AOSP/hardware/interfaces/automotive/vehicle/aidl
```

**読取**: interface, Parcelable 型、メソッドシグネチャ。

### ステップ 2: Java 実装側図化

```sh
# AudioFlinger HAL binding
java -Xmx4g -jar Juml.jar -c -o /tmp/aosp-out/hal/audioflinger-impl.svg \
  ~/AOSP/frameworks/av/services/audioflinger/aidl_impl/src
```

**読取**: AIDL proxy との連携、実装クラス継承。

### ステップ 3: System service 全体

```sh
java -Xmx8g -jar Juml.jar -c -o /tmp/aosp-out/service/audio-service.svg \
  ~/AOSP/frameworks/av/services/audioflinger
```

---

## HAL 探索コマンド

### AIDL インタフェース一覧

```sh
find ~/AOSP/hardware/interfaces -name "*.aidl" | head -20
find ~/AOSP/hardware/interfaces -path "*/aidl/*" -name "*.aidl"
```

### 特定 HAL (e.g., audio)

```sh
ls -la ~/AOSP/hardware/interfaces/audio/
find ~/AOSP/hardware/interfaces/audio -name "*.aidl" | sort
```

### VINTF マニフェスト確認

```sh
grep -r "android.hardware.audio" ~/AOSP --include="manifest.xml" --include="compatibility_matrix.xml"
```

### 実装パス検索

```sh
find ~/AOSP -path "*/hardware/interfaces/audio/*/default" -type d
find ~/AOSP -path "*/default/*" -name "*impl*.java" -o -name "*Impl.java"
```

---

## HIDL (レガシ) 確認

```sh
# HIDL インタフェース
find ~/AOSP -path "*/@1.0/*.hal" | head -10
find ~/AOSP -path "*/@2.0/*.hal" | head -10

# HIDL をクラス図化 (deprecated でも機能)
java -Xmx4g -jar Juml.jar -c -o /tmp/aosp-out/hal/hidl-audio.svg \
  ~/AOSP/hardware/interfaces/audio/2.0
```

---

## Juml HAL 解析フロー

1. **AIDL 定義** (`hardware/interfaces/<svc>/aidl/`) を `-c` 図化
   → interface メソッド・Parcelable 型 把握
2. **実装** (`default/` or `<impl_path>/`) を `-c` 図化
   → Java 実装クラス、Binder proxy handling
3. **System service** (AudioFlinger, VehicleHal など) 全体を図化
   → HAL client 側の実装, lifecycle
4. **Manifest** (VINTF) を Grep で確認
   → バージョン、互換性制約

---

## HAL Binder 通信の可視化

Juml クラス図から:

```
AudioClient (framework)
  ↓ (AIDL-generated proxy method call)
AudioFlinger (HALInterfaceImpl)
  ↓ (Binder)
AudioHAL daemon (native impl)
  ↓
Hardware (kernel driver)
```

各層が別ファイルでも、クラス図で継承チェーンを追跡できます。
