---
name: aosp-juml-analyzer
description: AOSP (Android Open Source Project) ソースツリーを Juml で UML 図化する手順集。Soong/Android.bp、AIDL/HAL、partition (system/vendor/product/odm/boot/vbmeta)、SELinux sepolicy (.te) の解析・可視化依頼や、Treble/GKI/VINTF 関連の質問があるとき自動ロード。
---

# AOSP × Juml スキル: 図化と解析

## 前提環境

### Juml

- **jar ファイル**: `/home/user/Juml/build/libs/Juml.jar`
  - 未ビルド場合: `cd /home/user/Juml && ./gradlew jar`
- **Java**: 17 以上 (JRE/JDK)
- **メモリ**: `-Xmx4g` (小規模), `-Xmx8g` 推奨, `-Xmx16g` (大規模)

### AOSP チェックアウト

- **慣例的パス**: `~/AOSP/` (ユーザ環境に応じて調整)
- **スペース確保**: 100GB 以上推奨 (フルツリー)
- **API レベル**: 2026 年時点での最新 (通常 API 35+)

---

## AOSP → Juml 入力対応表

| 目標 | 図種 | オプション | 入力パス | 想定サイズ |
|---|---|---|---|---|
| **モジュール内クラス構造** | クラス図 | `-c` | `~/AOSP/frameworks/base/services/core` | 数千クラス |
| **Android ライフサイクル起点** | シーケンス図 | `-Q` | `~/AOSP/packages/services/Car` | 複数ファイル |
| **特定メソッドのコール chain** | シーケンス図 | `-q CarService.onStartUser` | `~/AOSP/packages/services/Car` | 指定エントリ |
| **パッケージ間依存** | パッケージ図 | (クラス図から自動生成) | クラス図と同じ | 俯瞰表示 |
| **Manifest コンポーネント** | Manifest 図 | `-M` | `~/AOSP/packages/apps/Settings` | Activity/Service |
| **モジュール依存** | Gradle 依存図 | `-G` | `~/AOSP/packages/services/Car` | 10～100 モジュール |
| **Soong モジュール** | Markdown + 依存図 | `--android-bp` | `~/AOSP/packages/services/Car` | Android.bp 群 |
| **ビルドグラフ** | Markdown + 依存図 | `--build-ninja` | `~/AOSP/out/soong` | build.ninja |
| **ビルド成果物在庫** | Markdown + 図 | `--intermediates` | `~/AOSP/out/soong` | .intermediates |
| **SELinux policy** | Markdown | `--selinux` | `~/AOSP/system/sepolicy` | *.te 群 |
| **RRO overlay** | Markdown | `--rro-overlays` | `~/AOSP/packages/overlays` | `<overlay>` 検出 |
| **逆参照影響** | Markdown + 図 | `--impact SYMBOL` | module root | FQN / FQN.method |
| **アーキ俯瞰** | Markdown + 図 | `--insights` | module root | entry/hotspot/cycle |
| **全量出力** | 全種類 | `--all` | 上記いずれか | 所要時間: 数分～数十分 |

---

## ワークフロー 1: Soong ビルドシステムを理解する

**目的**: Soong (`.bp` ファイル) で定義されたモジュール構成とその依存関係を可視化する。

### ステップ 1: `--android-bp` で Soong モジュールを第一級解析

> Juml は `Android.bp` (Blueprint) を専用パースする
> (`juml.core.aosp.AndroidBpParser`)。`find` で手探りする前にまずこれを使う。

```sh
java -Xmx8g -jar /home/user/Juml/build/libs/Juml.jar \
  --android-bp -o /tmp/aosp-out/build/ \
  ~/AOSP/packages/services/Car
```

出力: `android-bp.md` (モジュール inventory) + `android-bp.puml/.svg`
(モジュール依存グラフ)。`cc_library` / `java_library` / `android_app` などの
モジュール種別と `static_libs` / `shared_libs` / `deps` 依存を可視化。

### ステップ 2: ビルド成果物まで踏み込む (任意)

```sh
# build.ninja の rule 使用統計 + ターゲット依存 (out/soong を指す)
java -Xmx8g -jar /home/user/Juml/build/libs/Juml.jar \
  --build-ninja -o /tmp/aosp-out/build/ ~/AOSP/out/soong

# .intermediates 成果物の在庫 (module × variant × kind)
java -Xmx8g -jar /home/user/Juml/build/libs/Juml.jar \
  --intermediates -o /tmp/aosp-out/build/ ~/AOSP/out/soong
```

### ステップ 3: モジュールの src をクラス図化

例: `packages/services/Car/service` の main module

```sh
java -Xmx8g -jar /home/user/Juml/build/libs/Juml.jar \
  -c -o /tmp/aosp-out/build/car-service.svg \
  ~/AOSP/packages/services/Car/service/src
```

### ステップ 4: Gradle 採用モジュールは `-G` 依存図

AOSP の一部 (Gradle を使うモジュール) は `-G` でも依存が取れる。

```sh
java -jar /home/user/Juml/build/libs/Juml.jar \
  -G -o /tmp/aosp-out/build/car-deps.svg \
  ~/AOSP/packages/services/Car
```

**出力読取ポイント**: Soong は `--android-bp`、Gradle は `-G` と使い分ける。
モジュール間依存、外部 Maven ライブラリ。

---

## ワークフロー 2: Partition / Image 構成を可視化する

**目的**: `system/`, `vendor/`, `product/`, `odm/` パーティション配置と APK/JAR 構成を理解する。

### ステップ 1: 各パーティション向けコンポーネント探索

```sh
# system partition の Settings app
find ~/AOSP/packages/apps/Settings/src -name "*.java" | wc -l

# vendor partition の HAL daemon 候補
find ~/AOSP/hardware/interfaces -name "*Impl.java" | head -10
```

### ステップ 2: パーティション別 APK/JAR を図化

例: system partition 向け Settings App

```sh
java -Xmx4g -jar /home/user/Juml/build/libs/Juml.jar \
  -c -o /tmp/aosp-out/partition/settings-system.svg \
  ~/AOSP/packages/apps/Settings
```

### ステップ 3: Manifest から權限要件を把握

```sh
java -jar /home/user/Juml/build/libs/Juml.jar \
  -M -o /tmp/aosp-out/partition/settings-manifest.svg \
  ~/AOSP/packages/apps/Settings
```

**出力読取**: コンポーネント (Activity/Service), exported 属性, uses-permission リスト。

### ステップ 4: OEM カスタマイズ (RRO) を `--rro-overlays` で検出

product/odm パーティションの OEM カスタマイズは Runtime Resource Overlay で
入ることが多い。`AndroidManifest.xml` の `<overlay targetPackage=...>` を専用検出する
(`juml.core.aosp.RroOverlayDetector`)。

```sh
java -jar /home/user/Juml/build/libs/Juml.jar \
  --rro-overlays -o /tmp/aosp-out/partition/ \
  ~/AOSP/packages/overlays
```

出力: `rro-overlays.md` (overlay → targetPackage の対応一覧)。

---

## ワークフロー 3: HAL / AIDL / HIDL 境界を図にする

**目的**: Hardware Abstraction Layer (HAL) インタフェースと実装を可視化。

### ステップ 1: AIDL インタフェース発見

```sh
find ~/AOSP/hardware/interfaces -name "*.aidl" | grep -i audio | head -10
```

### ステップ 2: AIDL インタフェース図化

```sh
java -Xmx8g -jar /home/user/Juml/build/libs/Juml.jar \
  -c -o /tmp/aosp-out/hal/audio-aidl.svg \
  ~/AOSP/hardware/interfaces/audio/aidl
```

### ステップ 3: HAL 実装 (default implementation)

```sh
find ~/AOSP -path "*/audio/impl/*.java" -o -path "*/audio/default/*.java" | head -5

# default impl をクラス図化
java -Xmx4g -jar /home/user/Juml/build/libs/Juml.jar \
  -c -o /tmp/aosp-out/hal/audio-impl.svg \
  ~/AOSP/hardware/interfaces/audio/default
```

**出力読取**: AIDL インタフェース (Parcelable, interface), 実装クラス継承構造。

### ステップ 4: AIDL ↔ Stub 実装の紐付けを `--aidl-binding` で表化

どの実装クラスがどの AIDL `Stub` を継承しているかを専用解決する
(`juml.core.aaos.AidlBindingResolver`)。HAL/サービス境界の「契約 → 実装」対応に有効。

```sh
java -Xmx8g -jar /home/user/Juml/build/libs/Juml.jar \
  --aidl-binding -o /tmp/aosp-out/hal/ \
  ~/AOSP/hardware/interfaces/audio
```

出力: `aidl-binding.md` (AIDL interface → Stub 継承実装クラスの対応表)。

---

## ワークフロー 4: SELinux sepolicy を調査する

**目的**: SELinux ポリシー (`system/sepolicy/`) とアクセス制御ルールを理解する。

### 注記: Juml は `.te` を第一級でパースする

> ⚠️ 旧版ドキュメントの「Juml は `.te` を直接パースしない」は **誤り**。
> 現在の Juml は **`--selinux`** で `*.te` を再帰走査し、type 宣言 /
> allow / neverallow ルールを分類した Markdown レポートを生成する
> (`juml.core.aosp.SelinuxPolicyParser`)。

### ステップ 1: sepolicy ツリーを `--selinux` で解析

```sh
java -Xmx8g -jar /home/user/Juml/build/libs/Juml.jar \
  --selinux -o /tmp/aosp-out/sepolicy/ \
  ~/AOSP/system/sepolicy
```

出力: `selinux.md` (type 宣言一覧 / allow / neverallow ルールの分類)。

### ステップ 2: Java サービス側のセキュリティチェック箇所を図化

sepolicy (カーネル MAC) と Java 層 (`checkPermission` / `enforcePermission`)
は別レイヤなので、両者を突き合わせると理解が深まる。

```sh
java -Xmx8g -jar /home/user/Juml/build/libs/Juml.jar \
  -c -o /tmp/aosp-out/sepolicy/carservice-perm.svg \
  ~/AOSP/packages/services/Car/service/src
```

**読取**: `--selinux` の neverallow と、Java 側の権限チェック呼び出し位置を対応付ける。

---

## CLI オプション一覧 (Juml)

より詳細は `java -jar Juml.jar --help` を参照:

```
# --- 図 (UML) ---
-c, --class-diagram       クラス図 (SVG/PUML/PNG)
-q, --sequence-diagram M  特定メソッド (Class.method) からのシーケンス図
-Q, --sequence-diagrams   Android ライフサイクルエントリ全量シーケンス
-d, --component-diagram   Android コンポーネント図
-M, --manifest-diagram    AndroidManifest 図
-D, --deeplink-diagram    Deep Link / App Links 図
-G, --dependency-graph    Gradle モジュール依存図
-P, --per-folder          フォルダ単位でクラス図を分割出力 (-c と併用)

# --- AOSP / Soong / SELinux ---
--android-bp              Android.bp (Soong) モジュール inventory + 依存図
--build-ninja             build.ninja の rule 統計 + ターゲット依存図
--intermediates           out/soong/.intermediates 成果物の在庫
--selinux                 *.te (SELinux policy) を type/allow/neverallow 分類
--rro-overlays            AndroidManifest の <overlay> から RRO 検出

# --- AAOS / Android ドメイン ---
--vhal-flow               CarPropertyManager get/set/subscribe フロー
--aidl-binding            AIDL interface ↔ Stub 実装クラスの紐付け表
--screen-flow             Intent / ScreenManager 画面遷移図
--er-diagram              Room @Entity の ER 図
--data-flow               Room Entity/DAO/Database レポート + ER 図
--settings                SharedPreferences / Preference XML のキー解析
--init-flow               Application onCreate 等のシーケンス図
--action-map              onClick / menu ハンドラ一覧

# --- 解析・俯瞰 ---
--impact SYMBOL           逆参照影響 (FQN / FQN.method) Markdown + 図
--impact-depth N          --impact の BFS 深さ (デフォ 3)
--ref-find SYMBOL         参照箇所を grep 互換テキスト出力
--insights                entry/hotspot/cycle/dead-code のアーキ俯瞰
--summary                 Markdown プロジェクトサマリー
--list-methods            メソッド候補一覧 (-q の起点選択用)
--function-list           全メソッド + 呼び出し元 + 実行条件

# --- 図の調整 ---
--mode headers-only|full  パースモード (大規模は headers-only が軽量)
--preset minimal|balanced|detailed   可読性プリセット
--exclude-external        java.*/android.*/kotlin.* 等の外部クラス除外
--exclude-package PREFIX  指定パッケージ接頭辞を除外 (複数可)
--public-only             public のみ表示
--jetpack                 Jetpack ステレオタイプ (Fragment/ViewModel/Hilt)
--seq-depth N             シーケンス図トレース深さ (デフォ 5, 0=無制限)
--include-tests           テストソースも解析対象に含める (既定は除外)

# --- 共通 ---
-A, --all                 全種類一括出力 (ディレクトリ指定)
-o FILE                   出力先ファイル / ディレクトリ
-v, --verbose             警告を stderr 出力
```

> 注: スコープ絞り込みは存在しない `--scope` ではなく
> **`--exclude-package PREFIX`** / **`--public-only`** / **`--mode headers-only`**
> で行う。

---

## トラブルシューティング

### OOM (Out of Memory)

```sh
# 増加させる
java -Xmx16g -jar Juml.jar ...

# または、スコープ絞り込み
# → cheatsheet-build.md の Scope オプション参照
```

### 図が複雑すぎて読めない

- `--exclude-package PREFIX` / `--public-only` / `--exclude-external` で要素を削減
- `--preset minimal` や `--seq-depth 2` で簡素化
- `-P` でフォルダ単位に分割、またはパッケージ単位に分割実行

### 起点メソッドが見つからない

```sh
# 全メソッド一覧出力、fzf 等で検索
java -Xmx8g -jar Juml.jar --list-methods ~/AOSP/packages/services/Car
```

---

## 関連チートシート

詳細は以下を参照:

- **Soong/Bazel/make**: [`cheatsheet-build.md`](cheatsheet-build.md)
- **Partition/Image**: [`cheatsheet-partition.md`](cheatsheet-partition.md)
- **HAL/AIDL/HIDL**: [`cheatsheet-hal.md`](cheatsheet-hal.md)
- **SELinux/sepolicy**: [`cheatsheet-sepolicy.md`](cheatsheet-sepolicy.md)

---

## 出力規約 (日本語サマリー)

各図化実行後は、以下の形式でサマリーを記述:

```
## 変更サマリー

- **<English phrase>**: <日本語説明>
  目的: <なぜこれをしたか>
```

例:

```
- **Generated Car Service class diagram**: CarService の クラス構成を可視化
  目的: CarLocalServices に登録される各 Manager の継承関係と依存を理解するため
```
