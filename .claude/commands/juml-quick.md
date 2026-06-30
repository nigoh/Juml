# /juml-quick

Juml のシンプルな直接実行コマンド。エージェント・スキル不要。

## 説明

AOSP/AAOS パスを指定して、直接 Juml を実行したいときに使用。
図化コマンドを素早く生成します。

## 使用方法

```
/juml-quick <diagram-type> <path> [memory]
```

### パラメータ

| パラメータ | 値 | 説明 |
|---|---|---|
| `<diagram-type>` | `class`, `seq`, `manifest`, `deps`, `summary`, `all`, `impact`, `ref-find`, `vhal-flow`, `aidl-binding`, `er-diagram`, `data-flow`, `screen-flow`, `android-bp`, `selinux`, `rro-overlays` | 図/レポートの種類 |
| `<path>` | ファイル/ディレクトリ | AOSP 内のパス (相対 or 絶対) |
| `[memory]` | `4g`, `8g`, `16g` | メモリ設定 (デフォ 8g) |

### diagram-type の説明

| Type | オプション | 説明 |
|---|---|---|
| `class` | `-c` | クラス図 |
| `seq` | `-Q` | Android ライフサイクルシーケンス図 |
| `manifest` | `-M` | Manifest / コンポーネント図 |
| `deps` | `-G` | Gradle 依存図 |
| `summary` | `--summary` | Markdown サマリー |
| `all` | `--all` | 全種類一括出力 |
| `impact` | `--impact SYMBOL` | 逆参照影響レポート (Markdown + PlantUML)。SYMBOL は FQN もしくは FQN.method |
| `ref-find` | `--ref-find SYMBOL` | シンボルへの参照箇所 grep 互換テキスト出力 |
| `vhal-flow` | `--vhal-flow` | CarPropertyManager の get/set/subscribe フロー (Markdown + PlantUML) |
| `aidl-binding` | `--aidl-binding` | AIDL ↔ Stub 実装クラスの紐付け表 (Markdown) |
| `er-diagram` | `--er-diagram` | Room @Entity の ER 図 (PlantUML) |
| `data-flow` | `--data-flow` | Room Entity / DAO / Database 全体レポート (Markdown + ER PlantUML) |
| `screen-flow` | `--screen-flow` | Intent ベース画面遷移図 (startActivity / setClass / startActivityForResult) (Markdown + PlantUML) |
| `android-bp` | `--android-bp` | AOSP の Android.bp (Soong) モジュール依存図 + Markdown インベントリ |
| `selinux` | `--selinux` | AOSP の SELinux policy (*.te) を type 宣言 / allow / neverallow に分類した Markdown |
| `rro-overlays` | `--rro-overlays` | AndroidManifest 内 `<overlay>` を検出して RRO 一覧 (Markdown) |

## 例

```
# 相対パス (カレント AOSP 下)
/juml-quick class frameworks/base/services/core

# 絶対パス
/juml-quick class ~/AOSP/packages/services/Car/service/src

# メモリ指定
/juml-quick all ~/AOSP/hardware/interfaces/automotive/vehicle/aidl 16g

# Manifest 図
/juml-quick manifest ~/AOSP/packages/apps/CarSystemUI

# Gradle 依存図
/juml-quick deps ~/AOSP/packages/services/Car

# 影響範囲解析 (このクラスを消すと何が壊れる?)
/juml-quick impact android.car.hardware.CarPropertyManager ~/AOSP/packages/services/Car

# 影響範囲解析 (メソッド単位)
/juml-quick impact android.car.hardware.CarPropertyManager.getProperty ~/AOSP/packages/services/Car

# 参照箇所列挙 (grep 互換)
/juml-quick ref-find android.car.hardware.CarPropertyManager.getProperty ~/AOSP/packages/services/Car

# AAOS: VHAL Property フロー (CarPropertyManager.get/set/subscribe)
/juml-quick vhal-flow ~/AOSP/packages/services/Car

# AAOS: AIDL インタフェースと実装クラスの紐付け
/juml-quick aidl-binding ~/AOSP/packages/services/Car

# Room データ層: Entity / DAO / Database を一括レポート
/juml-quick data-flow ~/projects/my-android-app

# Room ER 図のみ (PlantUML)
/juml-quick er-diagram ~/projects/my-android-app

# Intent ベース画面遷移図 (startActivity / setClass)
/juml-quick screen-flow ~/projects/my-android-app

# AOSP の Android.bp (Soong) モジュール依存図
/juml-quick android-bp ~/AOSP/packages/services/Car

# AOSP SELinux policy summary (*.te ファイル群)
/juml-quick selinux ~/AOSP/system/sepolicy

# RRO Overlay 一覧 (AndroidManifest の <overlay> 要素検出)
/juml-quick rro-overlays ~/AOSP/packages/overlays
```

## 出力

生成されたコマンドラインをコピーして実行:

```sh
java -Xmx8g -jar /home/user/Juml/build/libs/Juml.jar \
  -c -o /tmp/aosp-out/frameworks-core.svg \
  ~/AOSP/frameworks/base/services/core
```

---

**Note**: エージェント・スキルのような解析ロジック設計は含みません。
「とにかく図にしたい」「コマンドを教えてほしい」というときに使ってください。
