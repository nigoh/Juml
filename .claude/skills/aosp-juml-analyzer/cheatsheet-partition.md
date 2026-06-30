# AOSP Partition & Image Layout Cheatsheet

## Partition 構成 (2026 年時点)

| Partition | 役割 | OEM カスタマイズ | 更新 |
|---|---|---|---|
| **system** | AOSP 基本フレームワーク (framework.jar, system services) | ✗ (ロック) | OTA (A/B) |
| **vendor** | SoC/OEM ドライバ、HAL 実装 | ○ | OTA (A/B) |
| **product** | Googleブランド依存部分、GMS 等 | △ (制限) | OTA (A/B) |
| **odm** | OEM カスタム drivers (Treble 導入後) | ○ | OTA (A/B) |
| **system_ext** | system の拡張パッケージ | △ | OTA (A/B) |
| **boot** | kernel + ramdisk | ○ (device/) | 別途ファイル |
| **vbmeta** | Verified Boot チェーン | ○ | 別途ファイル |
| **dtbo** | Device Tree Binary Overlay | ○ | 別途ファイル |
| **super** | A/B partition 用メタ partition (2024+) | ○ | 自動管理 |

---

## Treble 分離 (Project Treble, API 26+)

Treble 以降、vendor は system から独立:

```
system/  (AOSP 本体, Google 管理)
vendor/  (SoC ドライバ, OEM 実装)
odm/     (OEM カスタム, Treble 新規)
```

**利点**:
- system OTA 時に vendor を変更不要 (フォワードコンパティビリティ)
- HAL AIDL stable 化で定義が固定

---

## GKI (Generic Kernel Image, API 32+)

AOSP kernel を標準化。OEM は DTBO (Device Tree Binary Overlay) でカスタマイズ:

```
kernel (Android 標準)
  ↓
+ dtbo (OEM device tree overlay)
  ↓
= 最終 boot イメージ
```

---

## A/B Partition

同時稼働可能な 2 つの system/vendor スロット。安全な OTA:

```
slot_a:  system_a, vendor_a, boot_a
slot_b:  system_b, vendor_b, boot_b

active (起動時) = slot_a or slot_b
```

OTA は非アクティブスロットに書き込み → リブート → スイッチ。

---

## fs_config_dirs

Partition 別ファイルシステム権限設定:

```
system/etc/fs_config_dirs  → system partition での dir mode/owner
vendor/etc/fs_config_dirs  → vendor partition での dir mode/owner
odm/etc/fs_config_dirs     → odm partition での dir mode/owner
```

例: vendor HAL daemon は `system:system` vs `root:root` など。

---

## AOSP 内 APK/JAR 配置 (パーティション別)

### system/app (システム APK)

```
system/app/
├── Settings/
├── SystemUI/
├── Launcher/
└── ...

system/framework/
├── framework.jar       (Android framework)
├── framework-res.apk   (リソース)
└── android.car.jar     (AAOS 自動車フレームワーク)
```

**Juml**: Settings の場合:

```sh
java -Xmx4g -jar Juml.jar -c -o settings-class.svg \
  ~/AOSP/packages/apps/Settings/src
```

### vendor/app (OEM APK)

```
vendor/app/
├── VendorSettings/
├── CarRadio/
└── ... (OEM 固有)
```

**特徴**: vendor partition だから system OTA の影響を受けない。

### odm/app (ODM 独自 APK, 2020+)

Treble odm partition 導入で、OEM がさらに細分化可能:

```
odm/app/  (OEM device module app)
```

---

## APK → Partition へのコンパイル

Soong の `srcs:` の `device/` パスで制御:

```bp
android_app {
    name: "CarSettings",
    srcs: ["src/**/*.java"],
    // ...
    privileged: true,  // system/priv-app へ
    // or
    // odm: true,  (odm/app へ)
}
```

---

## Juml で Partition 別 APK を図化

### ステップ 1: APK ソース確認

```sh
find ~/AOSP/packages/apps -name "Android.bp" -o -name "Android.mk" | head -20
grep -l "privileged\|odm" ~/AOSP/packages/apps/*/Android.bp
```

### ステップ 2: クラス図生成

```sh
# system/app 向け Settings
java -Xmx4g -jar Juml.jar -c -o /tmp/aosp-out/partition/settings.svg \
  ~/AOSP/packages/apps/Settings/src

# vendor HAL
java -Xmx4g -jar Juml.jar -c -o /tmp/aosp-out/partition/vendor-hal.svg \
  ~/AOSP/hardware/interfaces/audio/default/src
```

### ステップ 3: Manifest 確認 (exports, permissions)

```sh
java -jar Juml.jar -M -o /tmp/aosp-out/partition/settings-manifest.svg \
  ~/AOSP/packages/apps/Settings
```

**読取**: Activity exported 属性, 使用 permission, permission group。

---

## vbmeta (Verified Boot チェーン)

```
vbmeta
  ├─ boot (kernel)
  ├─ system
  ├─ vendor
  ├─ vbmeta_system
  └─ vbmeta_vendor
```

署名チェーン。tamper 検出。セキュリティ境界。

---

## super (A/B dynamic partition)

API 32+ で logical partition 管理:

```
super (物理 partition)
  ├─ system (logical)
  ├─ vendor (logical)
  ├─ product (logical)
  └─ odm (logical)
```

OTA 時に logical partition の境界を変更可能。

---

## Partition 可視化コマンド集

### 全 APK リスト

```sh
find ~/AOSP -path "*/packages/apps/*/src" -type d | head -20
find ~/AOSP -path "*/packages/services/*/src" -type d | head -10
find ~/AOSP -path "*/vendor/*/src" -type d | head -10
```

### 各 Partition APK をクラス図化

```sh
# system/app
java -Xmx4g -jar Juml.jar -c -o /tmp/aosp-out/system-app.svg \
  ~/AOSP/packages/apps/Settings/src

# vendor lib
java -Xmx4g -jar Juml.jar -c -o /tmp/aosp-out/vendor-lib.svg \
  ~/AOSP/hardware/interfaces/audio/default/src

# framework
java -Xmx8g -jar Juml.jar -c -o /tmp/aosp-out/framework.svg \
  ~/AOSP/frameworks/base/core/java
```

### 依存図

```sh
java -jar Juml.jar -G -o /tmp/aosp-out/partition-deps.svg \
  ~/AOSP/packages/apps/Settings
```

---

## Treble 境界の把握

### HAL AIDL (system ↔ vendor)

```
system:
  frameworks/hardware/interfaces/  ← framework-side AIDL
  
hardware/interfaces/
  ├── audio/aidl/
  ├── vehicle/aidl/
  └── ...
  
vendor:
  hardware/interfaces/<svc>/default/  ← implementation
```

Juml で `hardware/interfaces/audio/aidl` をクラス図化して AIDL インタフェース確認。

### SELinux boundary

```
system/sepolicy/public/   ← shared HAL definitions
system/sepolicy/vendor/   ← vendor-side constraints
vendor/sepolicy/          ← OEM カスタム rules
```

---

## Partition 別 sepolicy

```sh
grep -r "carservice\|audio_hal" ~/AOSP/system/sepolicy/ --include="*.te"
grep -r "carservice\|audio_hal" ~/AOSP/vendor/sepolicy/ --include="*.te"
```

vendor は独立した sepolicy domain 空間。

---

## GKI kernel + DTBO 例

### kernel (generic)

```
~/AOSP/common/kernel/  (shared source)
```

### DTBO (OEM specific)

```
device/<oem>/<board>/  (OEM device tree sources)
  └── device_tree_overlay.dts  ← DTBO ソース
```

Juml は binary を直接解析しません。
ソースツリーの構造確認: `find device/ -name "*.dts"` で Device Tree Source を検索。
