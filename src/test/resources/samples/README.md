Juml 統合テスト用サンプルソース
====================================

このディレクトリは `RealWorldSampleParseTest` から参照される、外部 OSS プロジェクト由来の
実ソースファイル群です。Juml の `JavaLexer` / `JavaStructureExtractor` /
`AidlParser` / `AndroidManifestParser` / `GradleScriptParser` /
`PlantUmlClassDiagram` が、現実世界の Java/Android プロジェクトでもクラッシュせず
期待どおりに構造を抽出できることを保証する用途に限って同梱しています。

合成されたユニットテスト用スニペットでは現れにくいパターン
(Apache ライセンスヘッダ、ラムダ、ネスト interface、`Parcelable.Creator`、
`apply plugin` DSL、AOSP の AIDL など) を網羅するために選定しました。

ライセンス: すべて Apache License 2.0
-----------------------------------------

各サンプルの著作権・原ライセンスは下記のとおりです。Apache License 2.0 は
コードの再配布を許可しており、本リポジトリ (MIT) に著作権表示を保ったまま
内包しても問題ありません。

| ディレクトリ | 出典 | ライセンス | 著作権者 |
|---|---|---|---|
| `easypermissions/` | [googlesamples/easypermissions](https://github.com/googlesamples/easypermissions) | Apache 2.0 | Google Inc. |
| `aidl/` | [aosp-mirror/platform_development](https://github.com/aosp-mirror/platform_development) | Apache 2.0 | The Android Open Source Project |

各ファイル先頭の Apache License 2.0 ヘッダはそのまま保持しています。
`easypermissions/LICENSE.txt` は googlesamples/easypermissions リポジトリの
LICENSE ファイル原本のコピーです。

ファイル一覧
-----------------------------------------

### `easypermissions/`

| ファイル | 元パス | 用途 |
|---|---|---|
| `MainActivity.java` | `app/src/main/java/pub/devrel/easypermissions/sample/MainActivity.java` | Activity 解析 (extends/implements/ラムダ/@Override) |
| `EasyPermissions.java` | `easypermissions/src/main/java/pub/devrel/easypermissions/EasyPermissions.java` | ネスト interface (`PermissionCallbacks` / `RationaleCallbacks`) と多くの static メソッド |
| `AfterPermissionGranted.java` | `easypermissions/src/main/java/pub/devrel/easypermissions/AfterPermissionGranted.java` | `@interface` (annotation 宣言) |
| `AppSettingsDialog.java` | `easypermissions/src/main/java/pub/devrel/easypermissions/AppSettingsDialog.java` | `implements Parcelable` + ネスト `Builder` クラス |
| `AndroidManifest.xml` | `app/src/main/AndroidManifest.xml` | `<uses-permission>` 5 件 / ランチャー Activity |
| `app-build.gradle` | `app/build.gradle` | Groovy DSL の `apply plugin` / `android { ... }` / `dependencies` |
| `LICENSE.txt` | リポジトリ直下 `LICENSE` | Apache 2.0 全文 |

### `aidl/`

| ファイル | 元パス | 用途 |
|---|---|---|
| `IRemoteService.aidl` | `samples/ApiDemos/src/com/example/android/apis/app/IRemoteService.aidl` | AIDL `interface` 宣言とメソッド |

更新手順
-----------------------------------------

リファレンス実装の上流が変わってテストが失敗する状況を避けるため、これらの
ファイルは**コピーした時点で凍結**しています。サンプルを差し替えたいときは

1. 上記 URL から取得し、ライセンスヘッダを保ったまま上書き保存する
2. `RealWorldSampleParseTest` の期待値を新ファイルに合わせて更新する

の 2 手で行ってください。
