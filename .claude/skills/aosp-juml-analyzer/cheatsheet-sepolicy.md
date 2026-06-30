# SELinux / sepolicy Cheatsheet

## sepolicy ディレクトory 構成

```
system/sepolicy/
├── public/           ← shared HAL definitions
│   ├── carservice.te
│   ├── audioserver.te
│   ├── cameraserver.te
│   └── ... (public domain definitions)
├── private/          ← system-only private domains
│   ├── system_server.te
│   ├── init.te
│   └── ...
├── vendor/           ← vendor partition constraints
│   ├── system.te     (vendor-side system constraints)
│   └── ...
└── prebuilts/        ← pre-compiled policy binaries

vendor/sepolicy/     (if OEM provides)
├── device.te
├── custom_daemon.te
└── ...
```

---

## .te (Type Enforcement) ルール文法

### allow ルール (許可)

```
allow <source_domain> <target_type> : <class> <permissions>;

例:
allow carservice carservice : sock_file { write read };
allow audioserver audio_device : chr_file { open read write ioctl };
```

### neverallow ルール (禁止, compile-time チェック)

```
neverallow <source_domain> <target_type> : <class> <permissions>;

例:
neverallow untrusted_app_all system_app_data_file : file write;
```

### type definition

```
type carservice, domain;
type carservice_exec, exec_type, file_type;
```

---

## avc: denied ログの読み方

### 形式

```
avc: denied { open read write } for pid=1234 comm="carservice" \
  scontext=u:r:carservice:s0 tcontext=u:object_r:carservice_data_file:s0 \
  tclass=file permissive=0
```

| フィールド | 意味 |
|---|---|
| `denied { open read write }` | 拒否されたパーミッション |
| `pid=1234` | 犯人プロセス ID |
| `comm="carservice"` | プロセス名 |
| `scontext=u:r:carservice:s0` | ソース domain (carservice) |
| `tcontext=u:object_r:carservice_data_file:s0` | ターゲット type (carservice_data_file) |
| `tclass=file` | リソースクラス (file, sock_file, chr_file 等) |
| `permissive=0` | enforce (1=permissive mode で許可) |

### avc denied 解決フロー

1. **被害プロセス特定**: `comm="carservice"` → carservice daemon
2. **ソース domain**: `scontext=u:r:carservice:s0` → carservice domain
3. **ターゲット type**: `tcontext=u:object_r:carservice_data_file:s0` → carservice_data_file
4. **要求パーミッション**: `denied { open read write }` → 読み書き要求
5. **ルール追加候補**:
   ```
   allow carservice carservice_data_file : file { open read write };
   ```

---

## audit2allow ツール

avc log から allow ルールを自動生成:

```sh
# audit.log から carservice 関連 avc を抽出
grep "carservice" audit.log | audit2allow -M carservice_fix

# output: carservice_fix.te (提案ルール)
```

**注意**: 生成ルール は「許可するための最小限」提案です。
セキュリティレビュー必須。

---

## Juml × sepolicy

### Juml は `.te` を第一級でパースする

> ⚠️ 旧版の「Juml は `.te` を直接解析しない」は **誤り**。
> `--selinux` (`juml.core.aosp.SelinuxPolicyParser`) が `*.te` を再帰走査し、
> type 宣言 / allow / neverallow ルールを分類した Markdown を生成する。

推奨フロー:

1. `--selinux` で sepolicy ツリー全体をレポート化 (一次情報)
2. Java service 側の **権限チェック呼び出し** をクラス図で可視化
3. 両者 (カーネル MAC ⇔ Java 層チェック) を突き合わせて理解

### 例: CarService 権限ポリシー

```sh
# sepolicy を Juml で解析 (type/allow/neverallow 分類)
java -Xmx8g -jar Juml.jar --selinux -o /tmp/aosp-out/sepolicy/ \
  ~/AOSP/system/sepolicy
# → selinux.md

# 特定 domain を絞りたいときは生成された selinux.md を grep で追う
grep -i "carservice" /tmp/aosp-out/sepolicy/selinux.md

# Java 実装側のチェック地点をクラス図で可視化
java -Xmx8g -jar Juml.jar -c -o /tmp/aosp-out/sepolicy/carservice.svg \
  ~/AOSP/packages/services/Car/service/src
```

出力: `selinux.md` (policy ルール) + CarService の permission check 図。

---

## Domain と Type

### Domain (主体)

プロセスの SELinux context (実行権限):

```
type carservice, domain;
type audioserver, domain;
```

プロセス起動時:

```
init (type init)
  ↓ exec /system/bin/carservice
carservice (type carservice)
```

### Type (客体)

ファイル・ディレクトリ・device のラベル:

```
type carservice_data_file, file_type;
type carservice_socket, sock_file;
type device, dev_type;
```

---

## Verified Boot (AVB)

SELinux と併用のセキュリティチェーン:

```
boot partition (signed)
  ↓ verify (public key)
kernel + ramdisk
  ↓
vbmeta (AVB metadata)
  ├─ digest (system, vendor)
  ├─ signature (OEM key)
  └─ enforcement level
```

tamper 検出。

---

## sepolicy 設定の主要ドメイン

| Domain | 役割 | ファイル |
|---|---|---|
| `carservice` | Car Service daemon | `system/sepolicy/public/carservice.te` |
| `audioserver` | Audio service | `system/sepolicy/public/audioserver.te` |
| `cameraserver` | Camera service | `system/sepolicy/public/cameraserver.te` |
| `system_server` | System server (framework) | `system/sepolicy/private/system_server.te` |
| `init` | Init daemon | `system/sepolicy/private/init.te` |
| `hal_audio` | HIDL audio HAL (deprecated) | legacy |
| `vehicle_hal` | Vehicle HAL (new AIDL) | new (if AAOS) |

---

## vendor sepolicy

OEM が独自ドメインを追加:

```
vendor/sepolicy/
├── device.te       (OEM 独自 daemon domain)
├── custom_hal.te   (OEM custom HAL)
└── ...
```

Vendor domain は system domain より制限されうる:

```
# system/sepolicy/vendor/system.te
neverallow (system_server type_not_in_vendor_list) ... ;
```

Vendor から system への「予期しない」アクセスを禁止。

---

## sepolicy デバッグ

### permissive mode (一時的許可)

```sh
# permissive mode 有効化
adb shell setenforce 0

# avc denied が stderr に出ても実行継続
# audit.log に記録

# 通常に戻す
adb shell setenforce 1
```

### audit.log 確認

```sh
# リアルタイム確認
adb logcat | grep "avc: denied"

# audit log 抽出
adb shell dmesg | grep "avc: denied" > audit.log
```

---

## public vs private vs vendor

| レベル | アクセス | 用途 |
|---|---|---|
| **public** | system ↔ vendor HAL 定義 | VINTF 安定化 |
| **private** | system 内部のみ | system 実装詳細 |
| **vendor** | vendor 側 constraints | vendor が system を制約 |

public ドメインを変更すると、ビルド時に vendor HAL 互換性エラー発生。

---

## Juml sepolicy 解析フロー

1. **関連ドメイン検索**:
   ```sh
   grep -r "carservice\|audioserver" ~/AOSP/system/sepolicy --include="*.te"
   ```

2. **ルール確認**:
   ```sh
   # carservice が何を許可されているか
   grep "^allow carservice" ~/AOSP/system/sepolicy/public/carservice.te
   ```

3. **Java 実装側の permission check**:
   ```sh
   grep -r "enforcePermission.*android.permission" \
     ~/AOSP/packages/services/Car/service/src
   ```

4. **クラス図で context を把握**:
   ```sh
   java -Xmx8g -jar Juml.jar -c -o carservice-impl.svg \
     ~/AOSP/packages/services/Car/service/src
   ```

5. **avc denied が出た場合**:
   ```sh
   # ログ確認
   adb logcat | grep "avc: denied"
   
   # ルール追加案
   audit2allow -M carservice_fix < audit.log
   cat carservice_fix.te  # 提案チェック
   ```

---

## Treble 下での sepolicy 調整

Treble 後:

- `system/sepolicy/public/` の変更 → vendor HAL 互換性チェック
- `system/sepolicy/private/` は自由
- `vendor/sepolicy/` は OEM 自由

ビルド時:

```sh
# sepolicy チェック (neverallow 検証)
m sepolicy

# エラー例:
# neverallow rule violated
```

---

## 関連コマンド

```sh
# sepolicy file ダンプ (実行時)
adb shell getprop | grep "sepolicy"

# 実行中プロセスの SELinux context
adb shell ps -Z | grep carservice

# avc 監査ログリアルタイム
adb logcat | grep -i selinux

# sepolicy binary を text に変換 (オフライン)
system/sepolicy/tools/checkpolicy -d out/target/.../sepolicy
```
