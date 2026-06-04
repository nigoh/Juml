Change log
=============

2.1
--------

* **レイアウトリソースの「画面」可視化 + コード↔リソース紐づけ図を追加** (3 機能)
    * **① 画面ワイヤーフレーム図** (`DiagramKind.LAYOUT_SCREEN` / `PlantUmlLayoutScreenDiagram` 新規)
        * 既存の `LAYOUT` 図 (入れ子 rectangle = 構造ツリー) に対し、`res/layout/*.xml` を **PlantUML Salt** のワイヤーフレームで「画面としてどう見えるか」を描画する。`Button` → `[ ボタン ]`、`EditText` → 入力欄、`CheckBox` → チェックボックス、`ImageView` → 画像アイコン等にウィジェット変換。`LinearLayout orientation="horizontal"` の子は横並び (`|` 連結)、それ以外の ViewGroup は縦積みで近似。`android:text` が `@string/foo` のときはプロジェクトの strings.xml で実文言へ解決して表示。`EditText` が空欄なら `android:hint` → id をプレースホルダ表示。
        * GUI: ツールバー/Diagram メニューに図種 `Screen` を追加 (LAYOUT と同じくレイアウトファイル選択ダイアログ経由)。`DiagramRequest.forLayoutScreen` / `DiagramController.openLayoutScreenDiagram` / `DiagramEntryDialogs.pickLayoutScreenFile` で配線。
    * **② コード↔リソース紐づけ図** (`DiagramKind.RESOURCE_LINK` / `ResourceLinkAnalyzer` / `ResourceLinkAnalysis` / `ResourceReference` / `PlantUmlResourceLinkDiagram` 新規)
        * `SCREEN_FLOW` / `SOONG` と同じ「プロジェクトルート再走査」方式で、Java/Kotlin から `setContentView(R.layout.x)` / `inflate(R.layout.x)` / ViewBinding (`XxxBinding.inflate` → レイアウト名を逆算) / `R.string.*` / `R.id.*` を正規表現抽出。参照元クラスはファイル先頭の型宣言から推定。
        * 「**クラス ── レイアウト ── 文字列リソース**」を 1 枚の関係図に描画: クラス⇒レイアウト (太線=`setContentView`/`inflate`/Binding で画面を束ねる) / クラス→レイアウト (細線=その他 `R.layout`) / クラス⋯>文字列 (`R.string`) / レイアウト⋯>文字列 (XML 内 `@string/`)。文字列ノードには実文言を併記。
        * GUI: ツールバー/メニューに図種 `Resources` を追加。`DiagramService` のルート必須経路 (`generateResourceLinkPuml`) で処理。
    * **③ 文字列リソース解析基盤** (`StringResourceParser` / `AndroidStringResources` 新規)
        * `res/values*/strings.xml` (`values-ja/` 等のロケールバリアント含む) をセキュア DOM パース (XXE 防御) し `<string name>value</string>` を抽出。Android の `"..."` 空白保持記法・`\'` `\"` `\n` エスケープを正規化。文字列を含まない values XML (colors.xml 等) は保持しない。
        * `AndroidProjectScanner.includeValues` を追加し values ディレクトリを収集。`AndroidProjectAnalyzer` が走査して `AndroidProjectAnalysis.getStringResourcesByModule()` に格納。`resolveString(ref)` でデフォルトロケール優先の文言解決を提供 (①② から利用)。
    * テスト: 新規 `StringResourceParserTest` / `PlantUmlLayoutScreenDiagramTest` / `ResourceLinkAnalyzerTest` / `PlantUmlResourceLinkDiagramTest` (計 22 ケース)。PlantUML 実レンダリングで Salt/関係図が SVG 化できることも確認。既存テスト全 PASS / checkstyle clean。
    * 目的: 「レイアウトが画面としてどう表示されるか」と「実コードとリソース (レイアウト・文言) がどう紐づくか」を、Java/Gradle 図と同じタブ中心 UI で可視化できるようにするため。

* **Android プロジェクト解析の詳細ドキュメントを追加** (`docs/android-analysis.html` 新規)
    * Android Gradle プロジェクトを Juml がどう解析するかを、ソースから棚卸しして 1 ページに集約。`docs` サイトのナビ / index カードにも `Android 解析` を追加。
    * 内容: 「2 系統の解析」(① 構造解析 `UmlGenerator.extractFromProjectDetailed` / ② Android 資産解析 `AndroidProjectAnalyzer.analyze`)、入口 (CLI `AndroidCommands` 各フラグ / GUI `DiagramKind` / API)、`AndroidProjectScanner` の収集・除外ルール、`inferModuleName` / sourceSet・qualifier 推定、並列パース・Manifest マージ・`DependencyJarIndex`、`AndroidProjectAnalysis` データモデル、各パーサ (Gradle / VersionCatalog / Manifest / Layout / Navigation)、Android 系の図生成、派生解析 (Room / 画面遷移 / 設定 / AOSP / AAOS)。
    * 「**改造・拡張ポイント**」節を独立化: 新しいファイル種を拾う / 新パーサを足す / 新図種を CLI+GUI に足す手順と、改造時のハマりどころ (解析順序・best-effort・並列性・結果順序・FQN バックフィル・XXE 防御・Stage A/B) をまとめた。
    * 目的: 利用者がこのアプリの Android 解析パイプラインを理解し、改造・更新できるようにするため。

* **Soong (Android.bp) 図を GUI に追加** (`DiagramKind.SOONG` 新規 / `DiagramService.generateSoongPuml`)
    * これまで CLI (`--android-bp`) でしか出せなかった Android.bp モジュール依存図を、GUI のツールバー / Diagram メニューから図種 `Soong` として開けるようにした。
    * 既存の `AndroidBpParser` (Android.bp 走査・解析) と `PlantUmlSoongDependencyDiagram` (コンポーネント依存図描画) を再利用し、`DiagramService` でプロジェクトルートを起点に走査する (画面遷移図 `SCREEN_FLOW` と同じ「ルート必須」経路)。
    * Android.bp が 1 つも見つからない / プロジェクト未ロードの場合は、空図でなく案内ノート付きの PlantUML を返す。
    * ツールバーは `Soong` ラベル + Android.bp 依存ツールチップ、ツリーアイコンは `MODULE` を割当。`DIAGRAMS_ANDROID` セットにも追加。
    * テスト: `DiagramServiceTest` に 2 ケース追加 (Android.bp 2 モジュール + 依存エッジ検証 / モジュール不在時の案内ノート)。
    * 目的: AOSP / AAOS プロジェクトを GUI で開いたまま、Java/Gradle の図と同じタブ中心 UI で Soong モジュール依存も可視化できるようにするため。

* **AOSP Phase 3.4: Android.mk (legacy) パーサー** (`AndroidMkParser` 新規)
    * AOSP の legacy GNU Make 形式 `Android.mk` から `LOCAL_MODULE` / `LOCAL_SRC_FILES` / 各種 `LOCAL_*_LIBRARIES` を抽出し、{@link AndroidBpModule} 出力モデルを **再利用** することで Soong (Phase 3.1) の解析結果と統一されたモジュールグラフを構築可能にする。
    * 戦略は完全な make 評価ではなく AOSP のお決まりパターン (Boilerplate) に特化:
        1. 行ベースでパース、末尾 `\` の継続行を 1 行に結合 (`joinContinuations`)
        2. `#` で始まる行とインラインコメントを除去
        3. `include $(CLEAR_VARS)` で `LOCAL_*` 蓄積をリセット (新モジュール開始)
        4. `LOCAL_xxx := / = / += / ?= 値` を捕捉 (`parseAssignment`)
        5. `include $(BUILD_XXX)` で現モジュールを完成させ、`BUILD_XXX` を Soong 風 type にマップ
    * `BUILD_XXX` → Soong type 対応表 (`BUILD_SHARED_LIBRARY` → `cc_library_shared` / `BUILD_STATIC_LIBRARY` → `cc_library_static` / `BUILD_EXECUTABLE` → `cc_binary` / `BUILD_HOST_*` 系 / `BUILD_JAVA_LIBRARY` → `java_library` / `BUILD_PACKAGE` → `android_app` / `BUILD_PREBUILT` → `prebuilt` 等)。未知の `BUILD_XXX` は小文字化して fall-through (例 `build_raw_blob`)。
    * `BUILD_PACKAGE` のとき `LOCAL_PACKAGE_NAME` を `LOCAL_MODULE` の代替として採用。
    * `splitValues` で値を空白分割、Make の変数参照 (`$(LOCAL_PATH)/foo.c`) は括弧深度を加味して 1 トークン扱い。
    * テスト: 新規 `AndroidMkParserTest` 22 ケース (空入力 / 単純 shared library / `BUILD_PACKAGE` + `LOCAL_PACKAGE_NAME` / java library / static + executable / 複数モジュール / 行継続 / `+=` / コメント / 不完全モジュール除外 / `CLEAR_VARS` リセット / 未知 BUILD_XXX / 非 BUILD include 無視 / 4 種類の deps 混在 / category 分類 / prebuilt / host executable / file path 記録 / `joinContinuations` ヘルパ / `parseAssignment` ヘルパ / `splitValues` ヘルパ)。既存 1003 件全 PASS。
    * 目的: AOSP の Android.bp と Android.mk が混在する状況でも一貫したモジュールビュー (`AndroidBpModule` のリスト) を提供し、`category()` 分類 (`cc` / `java` / `android` / `aidl` / `hidl` / `build` / `other`) を Soong/legacy 横断で適用可能にする。Phase 3 (AOSP ビルドシステム最小カバー) の完結 PR。

* **AOSP Phase 3.3: VINTF manifest パーサー** (`VintfManifest` / `VintfHal` / `VintfInterface` / `VintfManifestParser` 新規 / `AndroidProjectScanner.includeVintf`)
    * AOSP の VINTF (Vendor Interface Object) XML — デバイス側 `manifest.xml` (`<manifest type="device">`)、フレームワーク側 `<manifest type="framework">`、`<compatibility-matrix type="..." level="N">` — を統一モデルでパースする。`<hal>` 配下の `name` / `transport` / 複数 `version` / `<interface>` (name + 複数 instance) / `optional` 属性に対応。`<kernel version="...">` と `<sepolicy><version>...</version></sepolicy>` も取り込む。
    * AndroidManifest.xml ({@code <manifest package="...">}, type 属性なし) との混同を避けるためルート要素名 + type 属性で `VintfManifest.Kind` を判定。type 属性が無い `<manifest>` は `Kind.UNKNOWN` を返して空のままにする。
    * セキュア XML パーサ設定 (DOCTYPE 禁止 / 外部エンティティ無効) を `AndroidManifestParser` と同等に適用。
    * `AndroidProjectScanner.Options.includeVintf` (既定 false) を追加し、`manifest.xml` / `compatibility_matrix.xml` ファイル名を opt-in で収集。`AndroidManifest.xml` と区別される。
    * テスト: 新規 `VintfManifestParserTest` 17 ケース (null セーフ / 空入力 / 不正 XML / AndroidManifest 誤検出回避 / device manifest / framework manifest / compatibility-matrix + level / HIDL 完全構造 / AIDL HAL / 複数 instance / 複数 version + 範囲 / optional 属性 / optional null / kernel + sepolicy / 複数 HAL / interface なし HAL / name なし HAL の除外 / DOCTYPE 拒否)、`AndroidProjectScannerTest` に 3 ケース追加 (`includeVintf` で収集 / 既定無効 / AndroidManifest と独立)。既存 951 件全 PASS。
    * 目的: AOSP の HAL 要求/宣言マッピングを Phase 3.1 (Android.bp) + Phase 3.2 (HIDL) の解析結果と組み合わせて「このデバイスがどの HAL を実装/必要としているか」を図化できる土台を作る。

* **AOSP Phase 3.2: HIDL (`*.hal`) パーサー** (`HidlParser` 新規 / `AndroidProjectScanner.includeHidl`)
    * AOSP の HAL (Hardware Abstraction Layer) で歴史的に使われてきた `.hal` (HIDL: HAL Interface Definition Language) ファイルから、パッケージ宣言・import・interface 宣言とそのメソッドを抽出し、{@link JavaClassInfo} (Kind = `AIDL_INTERFACE`) のリストとして返す。Kind は AIDL と統一することで既存のクラス図・シーケンス図描画ロジックが HIDL にもそのまま適用される。
    * HIDL 固有構文への対応:
        * バージョン付きパッケージ `package android.hardware.foo@1.0;` を `packageName` にそのまま保持 (HIDL と AIDL の判別は `@N.M` suffix の有無で可能)
        * `import android.hardware.foo@1.0::types;` の末尾 `::types` も含めて読む
        * `methodName(params) generates (return_params);` の `generates` 句から戻り値型を取り出す (複数戻り値は最初の 1 つを `returnType` に圧縮)
        * `oneway` 修飾子はメソッド annotation に追加
        * `interface IFooExtra extends IFoo` の単一継承を `superClass` に取り込む (バージョン付き `android.hardware.foo@1.0::IFoo` も保持)
        * ネスト宣言 (`struct` / `enum` / `union` / `typedef`) は本体スキップ。interface 内のメソッドだけ取り込む
        * ジェネリクス `vec<int32_t>` の括弧深度を考慮
    * `AndroidProjectScanner.Options.includeHidl` (既定 false) を追加し、`.hal` ファイルの収集を opt-in でサポート。
    * テスト: 新規 `HidlParserTest` 15 ケース (空入力 / null セーフ / バージョン付きパッケージ / import 各種 / `getValue() generates (...)` / `oneway` / `extends IBase` / バージョン付き parent / ネスト struct/enum/union/typedef のスキップ / 多戻り値 / `vec<T>` / 複数 interface / ファイル先頭 typedef のスキップ / 空 generates)、`AndroidProjectScannerTest` に 2 ケース追加 (`includeHidl=true` で `.hal` を拾う / 既定では拾わない)。既存 920 件全 PASS。
    * 目的: AOSP の HAL 層を AIDL と並列に図化できるようにし、`Android.bp` (PR #61 で既に対応済み) と組み合わせて HAL モジュール → HAL interface のクロスレイヤ解析の入口を開く。

* **AAOS Phase 2.4: シーケンス図に第 1 引数の定数シンボルを併記** (`JavaMethodInfo.Call.firstArgLabel` 新規 / `JavaStructureExtractor` / `PlantUmlSequenceDiagram.formatCallLabel`)
    * `JavaMethodInfo.Call` に nullable な `firstArgLabel` フィールドを追加 (`getFirstArgLabel` / `setFirstArgLabel`)。呼び出しの第 1 引数が単独の定数シンボル参照 (例 `VehiclePropertyIds.HVAC_TEMPERATURE_SET`, `Manifest.permission.READ_PHONE_STATE`, 単独 `MAX_VALUE`) のときにそのフルパス文字列を保持する。
    * `JavaStructureExtractor` に `tryCaptureFirstConstantArgument` を新設。probe-only (idx を進めない) で先頭引数を覗き、ドット区切りの IDENT 連鎖を組み立てて、末尾セグメントが `UPPER_CASE_WITH_UNDERSCORES` 形式 (大文字始まり、英大文字・数字・アンダースコアのみ、長さ 2 以上) で、直後に `,` または `)` が続く場合だけ採用。`FOO + 1` のような複合式や `T` のような 1 文字 (型パラメータの可能性)、`Foo.class` (小文字末尾) は誤検出を避けて拒否。
    * `PlantUmlSequenceDiagram.formatCallLabel` に第 1 引数ラベル対応のオーバーロードを追加し、`emitCall` から `call.getFirstArgLabel()` を渡す。`firstArgLabel` があれば `getProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET)` のように引数まで表示、無ければ従来通り `getProperty()`。
    * テスト: 新規 `JavaStructureExtractorConstantArgTest` 13 ケース (ドット定数 / 単独定数 / 3 段ドット / 小文字変数の拒否 / 数値・文字列リテラル拒否 / `Foo.class` 拒否 / 単一文字 `T` 拒否 / 複合式拒否 / 後続引数があっても先頭を拾う / 数字を含む定数 / 別呼び出し戻り値の拒否)、`PlantUmlSequenceDiagramTest` に 2 ケース追加 (UML 出力反映 + ローカル変数引数のフォールバック)。既存 893 件全 PASS。
    * 目的: AAOS の {@code CarPropertyManager.getProperty(VehiclePropertyIds.XXX)} のような「シンボル定数 1 つを渡す呼び出し」をシーケンス図ラベルに引数まで載せて表示し、VHAL や Permission のシーケンス図を読み取り易くする。

* **AAOS Phase 2.3: AAOS API レベルバッジ** (`AaosPattern.apiLevelBadge` / `PlantUmlClassDiagram`)
    * クラスに付与された AAOS / Android API 要件アノテーションから、{@code "API 33+"} / {@code "Car 34+"} / {@code "Plat TIRAMISU+/Car TIRAMISU+"} のような短いバッジ文字列を生成し、クラス図にステレオタイプとして併記する。
    * サポート対象: `@AddedIn(majorVersion=33)` / `@AddedIn(33)` 位置引数 / `@AddedInOrBefore(...)` / `@MinimumPlatformSdkVersion(N)` / `@MinimumCarVersion(N)` / `@ApiRequirements(minPlatformVersion=..., minCarVersion=...)`。
    * `Car.PLATFORM_VERSION_TIRAMISU_0` のようなプラットフォーム定数シンボルからは正規表現 `PLATFORM_VERSION_(\w+?)(?:_\d+)?$` でコードネーム部 (例: `TIRAMISU`) を抜き出して表示。純数値はそのまま、引用符付きリテラルは中身を採用、その他は前後空白だけ削って返す。
    * 優先順位は `@ApiRequirements` (Plat+Car の両軸表示) → 単軸マーカー (annotation 出現順)。複数 annotation の集約は行わず最初に有効なものを返す (実プロジェクトでクラス宣言に複数 API marker が共存することはまず無いため)。
    * `markAaosCategories=false` で他の AAOS 系ステレオタイプと一括抑制可能。
    * テスト: `AaosPatternTest` に 12 ケース追加 (named arg / positional / 空白許容 / OrBefore / Plat+Car 組み合わせ / 優先順位 / FQN annotation / 未指定 / null)、`PlantUmlClassDiagramAaosStereotypeTest` に 3 ケース追加 (UML 出力への反映 + suppress)。既存 878 件全 PASS。
    * 目的: AAOS クラス図で「このクラスが利用できる最小 API レベル」を一目で確認できるようにし、互換性検討の往復を減らす。

* **AAOS Phase 2.2: Car App Library パターン認識** (`CarAppLibraryPattern` 新規 / `PlantUmlClassDiagram`)
    * `androidx.car.app.*` の Car App Library ベース型を継承するクラスを検出し、クラス図に `<<CarAppService>>` / `<<CarAppSession>>` / `<<CarAppScreen>>` ステレオタイプを付与する。`JetpackPattern` と同じ単純名末尾一致 + パッケージヒントの構造を採用。
    * 判定ロジック:
        * `CarAppService` は車載特有の名前なので superClass の単純名マッチだけで採用 (`extends CarAppService` でも `extends androidx.car.app.CarAppService` でも検出)
        * `Session` / `Screen` は汎用名なので、(a) superClass が FQN で `androidx.car.app.*` であるか、(b) 自クラスが `androidx.car.app.*` パッケージ配下にある、のいずれかを満たす場合だけ採用 (例: `class BackgroundSession extends Session` は採用しないが、`class HomeScreen extends androidx.car.app.Screen` は採用)
        * ジェネリクス後置 (`extends androidx.car.app.Screen<MyTemplate>`) も `JetpackPattern.simpleName` で除去して比較
    * `PlantUmlClassDiagram.stereotype` に組み込み、既存の `<<CarManager>>` / `<<SystemApi>>` / API レベルバッジと併記可能 (重複は除去)。`markAaosCategories=false` で抑制可。
    * テスト: 新規 `CarAppLibraryPatternTest` 13 ケース (CarAppService 単純名/FQN、Session/Screen の FQN 経由・パッケージ経由・誤検出回避、ジェネリクス、null セーフ、ヘルパー単体)、`PlantUmlClassDiagramAaosStereotypeTest` に 3 ケース追加 (UML 出力反映 + suppress)。既存 894 件全 PASS。
    * 目的: AAOS の Car App Library を使うモバイル UI コンポーネント (`CarAppService` / `Session` / `Screen`) を、ユーザコード側で簡単な命名規約に従っていなくても図上で識別できるようにする。

* **AAOS Phase 2.1: Android API 可視性マーカー + AIDL binder impl ステレオタイプ** (`AaosPattern` / `PlantUmlClassDiagram` / `PlantUmlSequenceDiagram`)
    * `AaosPattern.apiVisibilityStereotype(JavaClassInfo)` を新設。クラスの annotation 短名 (`@SystemApi` / FQN `android.annotation.SystemApi` / 引数有り `@SystemApi(client=...)` も吸収) と JavaDoc コメント中の `@hide` マーカーから `Hidden` / `SystemApi` / `TestApi` のいずれかを返す。`@SystemApi` 付きでも JavaDoc に `@hide` があれば `Hidden` を優先表示する (より制限の強い側を優先)。Android プラットフォーム API 全般のマーカーだが、AAOS の CarService / 内部 SDK で多用されるため `AaosPattern` に同居。
    * `AaosPattern.isAidlBinderImpl(JavaClassInfo)` を新設。superClass の末尾セグメントが `Stub` (ジェネリクス後置可) で前段が 1 セグメント以上ある場合に true を返す。`class CarFooService extends ICarFoo.Stub` や深くネストした `Outer.Inner.Stub<T>` も検出。前段なしの単独 `Stub` は誤検出を避けるため除外。
    * **クラス図への反映** (`PlantUmlClassDiagram.stereotype`): `markAaosCategories=true` (既定) のとき、既存 `<<CarManager>>`/`<<CarService>>`/`<<AaosApi>>` カテゴリに加えて `<<SystemApi>>`/`<<TestApi>>`/`<<Hidden>>`/`<<binder>>` を追記。`<<CarService>><<SystemApi>><<binder>>` のような重ね掛けも可能。`markAaosCategories=false` で全 AAOS 系ステレオタイプを抑制できる。
    * **シーケンス図への反映** (`PlantUmlSequenceDiagram`): participant 宣言時に対象クラスが `isAidlBinderImpl` のとき `<<binder>>` ステレオタイプを付与し、IPC 境界が一目で分かるようにする。既存の `<<external>>` / `<<missing>>` / 色付けと独立に追加。
    * テスト: `AaosPatternTest` に 14 ケース追加 (annotation 短名 / FQN / 引数有り、JavaDoc `@hide`、優先順位、Stub 継承パターン)。新規 `PlantUmlClassDiagramAaosStereotypeTest` 7 ケースと `PlantUmlSequenceDiagramTest` に 2 ケースで UML 出力への反映を回帰防止。既存 863 件全 PASS。
    * 目的: AAOS / Android プラットフォーム案件で「内部 SDK か」「IPC 境界はどこか」を図上で一目把握できるようにする。Phase 2 残項目 (Car App Library `Session`/`Screen` パターン、VHAL property 定数解決、`@ApiRequirements` API レベルバッジ) は別 PR で順次対応する。

* **Java 9+/14+/21+ 言語機能のパース範囲を拡張** (`JavaClassInfo.Kind.MODULE` 新規 / `JavaModuleDirective` 新規 / `JavaMethodInfo.Yield` 新規 / `JavaStructureExtractor`)
    * **`module-info.java` のパース対応** (JLS §7.7): `[open] module Foo.Bar { ... }` をトップレベルで認識し、新 `Kind.MODULE` を持つ `JavaClassInfo` として返す。本体の各ディレクティブ (`requires [transitive] [static] X` / `exports X [to Y, Z]` / `opens X [to Y, Z]` / `uses X` / `provides X with Y, Z`) を `JavaModuleDirective` のリストとして `getModuleDirectives()` に保持。`@Deprecated module ...` のアノテーション、`open` 修飾子も取り込む。`extractHeadersOnly()` でも directive は保持する (モジュールグラフ集計用)。
    * **switch 式 (Java 14+) の構造化解析**: `int x = switch(y) { case 1 -> 100; default -> 0; };` のような代入 RHS、`return switch(...)` / `throw switch(...)` / `yield switch(...)` の各経路で switch 本体を `JavaMethodInfo.Block.Kind.SWITCH` として構造化して取り込むようにした (以前は `{}` 内ブロックとして flatten され、case 構造が失われていた)。`case 1, 2, 3 -> ...` の複数ラベルや `case Integer i when i > 0 -> ...` (Java 21+ パターン case + when ガード) のラベル文字列も括弧深度を加味して正しく終端まで読み取る。
    * **`yield` 文 (Java 14+)** を新 `JavaMethodInfo.Yield` Statement として認識。`switchDepth` をパーサで追跡し、switch アーム内でだけ `yield expr;` を Yield 化する (switch の外では従来通り識別子扱いとし、`int yield = 10;` のような変数名宣言を壊さない)。`yield(...)` のメソッド呼び出し・`yield.foo` のフィールドアクセス・`yield =` の代入を直後トークンで除外。
    * **Class 図 / Package 図でモジュール宣言を除外**: `Kind.MODULE` の `JavaClassInfo` は `PlantUmlClassDiagram` / `PlantUmlPackageDiagram` のクラスループから除外し、空パッケージ集計や `class` 描画にノイズが乗らないようにする。モジュールグラフは後続フェーズの専用図種で扱う想定。
    * テスト: `JavaStructureExtractorModernJavaTest` (新規 12 ケース) で module-info の各ディレクティブ・switch 式 RHS / return / pattern case + when・`yield` の捕捉と switch 外での誤検出回避を回帰防止。`JavaStructureExtractorTest` の既存 76 ケースは全 PASS のまま。
    * 目的: Juml の Java パーサーが Java 9 以降の新構文 (module / switch 式 / pattern matching) に追随し、AOSP / AAOS / モダン Android プロジェクトでもクラス図・シーケンス図が穴抜けにならないようにする。後続フェーズの Phase 2 (AAOS の `@SystemApi`/AIDL binder hop 表現) / Phase 3 (AOSP の Android.bp / HIDL) を載せる基盤とする。

* **「関数を変数として設定するメンバー変数」の解析範囲を拡張** (`JavaStructureExtractor` / `JavaMethodInfo.Call` / `PlantUmlClassDiagram`)
    * これまで匿名クラス/ラムダによるフィールド初期化子のみ inline 解析していたのを、以下のパターンにも拡張
        * メソッド参照 (`Runnable r = Foo::bar;` / `a.b.c::method`) を `inlineMethods` に取り込む
        * コンストラクタや任意メソッド内の `this.field = new Listener() {...}` / `this.field = () -> ...` 形式の代入を捕捉し、対応するフィールドの `inlineMethods` に紐づける (宣言順より前で代入されても遅延解決パスでマッチ)
        * メソッド本体内の `view.setOnXxxListener(new ... {...})` や `view.setOnXxxListener(v -> ...)` を `JavaMethodInfo.Call.inlineMethods` (新規) に取り込み、リスナー登録呼び出しのコールバック本体を構造化保持する
        * 未知の SAM 型でも `Listener` / `Handler` / `Callback` / `Observer` / `Action` サフィックスをヒューリスティクスで剥がして SAM メソッド名を推定 (`MyCustomListener` → `myCustom`)
    * クラス図に `showInlineFunctions`（既定 true）を追加し、本機能で捕捉した inline メソッドを `.. fieldName: Type ..` 区切り線の下に列挙
    * シーケンス図側は既存の `findInlineMethod` が `JavaFieldInfo.inlineMethods` を参照するため、新たに取り込んだ代入/メソッド参照もそのまま展開対象になる
    * `parseLambdaExpressionBody` が呼び出し引数中の expression-bodied ラムダで外側 `)` / `,` を食い潰していた不具合を併せて修正
    * 目的: Android / Java で頻出する「フィールドにリスナーをセットする」「コンストラクタでハンドラを差し込む」「`setOnClickListener(v -> ...)` を書く」コードがクラス図・シーケンス図でブラックボックスにならないようにする

* **共通クラス図 (Common Classes Diagram) を追加 + GUI ツールバー導入** (`PlantUmlCommonClassesDiagram` 新規 / `DiagramKind.COMMON` / `DiagramService` / `UmlMainFrame`)
    * 新図種「Common Classes」: プロジェクト内のクラス群を走査し、他クラスから参照される回数 (fan-in) が多い「共通 (= 使い回されている) クラス」を上位 N 件 (既定 20) でハイライト表示する。参照種別は `extends` / `implements` / フィールド型 / メソッド引数型 / 戻り値型を集計し、自己参照は除外、外部ライブラリ (`java.*` / `android.*` / `kotlin.*` 等 + `Origin.EXTERNAL_JAR/MISSING_JAR`) も既定で集計対象外
    * 各ハブクラスは `<<common>>` ステレオタイプ + 黄背景で強調し、ラベルに `N refs` を併記。参照元クラスは破線矢印 `referrer ..> hub : uses` で接続 (`referrersPerClass` で上限制御、既定 5)
    * `DiagramService` に `case COMMON` を追加し、既存の `DiagramScope` フィルタ (パッケージ / モジュール / 正規表現 / seed+hop) と詳細昇格 (`ClassIndex.detail`) を流用
    * **ウィンドウ上部にツールバーを新設**: 既存メニューとショートカットは維持したまま、頻用操作をボタンとして可視化
        * 上段 (Action ツールバー): `Open` / `Save` / `Refresh` / `Back` / `Search` / `Scope` / `Clear Scope` / `Zoom In` / `Zoom Out` / `100%` / `Fit`
        * 下段 (Diagram トグル): `Class` / `Package` / `Sequence` / `Activity` / `Common` / `Component` / `Dependency` / `Manifest` / `Layout` を `JToggleButton` + `ButtonGroup` で配置し、Diagram メニューのラジオ選択と双方向同期 (`ItemListener` 経由)
        * `Sequence` / `Activity` / `Layout` ボタンは追加入力が未指定なら起点選択ダイアログを自動で開く
    * テスト: `PlantUmlCommonClassesDiagramTest` (10 ケース: fan-in 集計 / minReferences フィルタ / interface 実装 / 自己参照除外 / 外部ライブラリ除外 / topN 上限) と `DiagramServiceTest.testCommonClassesDiagram` を追加
    * 目的: AOSP 級プロジェクトでも「実際に共有されている中核クラス」を一目で把握できるようにし、頻用操作をメニュー潜り無しでクリックひとつで起動できるようにする

* **クラス・メソッド・フィールド横断検索ダイアログを追加** (`EntitySearchDialog` 新規 / `UmlMainFrame`)
    * Diagram メニュー → `Search Entities...` (アクセラレータ `Ctrl+Shift+F`) で開くモーダル。クラス・メソッド・フィールドを 1 つの部分一致検索で横断的に絞り込み、Kind チェックボックスで種別を ON/OFF できる
    * 選択結果に応じて: クラス → seed+1hop でクラス図に切り替え / メソッド → シーケンス図を生成 / フィールド → 所属クラス seed+1hop でクラス図 (フィールド型まで含む)
    * フィールドが匿名クラス/ラムダの初期化子を持つ場合は `[+inline]` バッジで可視化
    * ヘッドレス環境 (CI) でも検証できるよう、`EntitySearchDialog.filter(classes, query)` の静的ヘルパを提供 (Swing インスタンスを生成しない)
    * 目的: AOSP 級プロジェクトでも目的のクラス/メソッド/フィールドへ即座にジャンプし、対応する UML を表示できるようにする
* **フィールド宣言時の匿名クラス/ラムダ本体をシーケンス図に展開** (`JavaStructureExtractor` / `JavaFieldInfo` / `PlantUmlSequenceDiagram`)
    * これまで `parseFieldDecl` は `=` の後を `skipUntilSemicolonRespectingBlocks()` で捨てており、`private OnClickListener l = new OnClickListener() { onClick() { ... } };` の本体が解析対象外だった
    * `JavaFieldInfo.inlineMethods` を追加し、匿名クラス本体を `parseClassBody` で再帰解析して取り込む。ラムダ (`() -> body` / `args -> expr`) は SAM 名 (Runnable→run, OnClickListener→onClick, Consumer→accept など 20 種) を組み込みマップで解決、未知の SAM は `<inline>` で fallback
    * `PlantUmlSequenceDiagram.emitCall` の `nextCls == null` フォールバックに `findInlineMethod` を追加。フィールド経由の呼び出し (例: `listener.onClick()`) に対し、解決済みフィールド型の participant をアクティベートしつつ inline body を walk する
    * expression-bodied ラムダ専用の `parseLambdaExpressionBody` を追加 (フィールド終端 `;` を消費しないため安全)
    * 目的: Android UI コードで頻出するリスナー登録パターンが、シーケンス図でブラックボックスにならないようにする
* **依存 JAR/AAR の外部クラスを participant に表示** (`DependencyJarIndex` / `ExternalClassReader` 新規、ASM 9.7 を追加)
    * Gradle 依存宣言 (`implementation 'androidx.appcompat:appcompat:1.7.0'`) から `~/.gradle/caches/modules-2/files-2.1/...` および `~/.m2/repository/...` を再帰探索して JAR/AAR を発見。AAR は内部 `classes.jar` をメモリ展開
    * ASM `ClassReader` (SKIP_CODE / SKIP_DEBUG / SKIP_FRAMES) で各 `.class` のヘッダ (FQN・superclass・interfaces・public methods・public fields) を取り出して `JavaClassInfo` に変換。`Origin.EXTERNAL_JAR` で印付け
    * 起動時には ZipEntry 名のカタログだけを構築し、実際の `.class` 読み込みは `resolve(name)` 呼び出し時の lazy 評価。`ConcurrentHashMap` で並列パース耐性
    * シーケンス図/クラス図で外部クラスは `<<external>>` ステレオタイプ、解決できなかった依存先は `<<missing>> #LightYellow` 警告マーカーで描画 (`PlantUmlSequenceDiagram` / `PlantUmlClassDiagram.stereotype` / `PlantUmlClassLegend.emitOrigins`)
    * `UmlMainFrame` のステータスバーに「N dependency(ies) not resolved」を追記し、JAR が見つからない原因を可視化
    * `PlantUmlSequenceDiagram` 本体のサイズ縮小のため、コメント note 出力を `PlantUmlSequenceComments` に切り出し
    * 目的: `extends AppCompatActivity` のような外部 SDK クラスがクラス図/シーケンス図上で「不在」にならないようにし、依存 JAR が無いプロジェクトでは明示的に警告マーカーで知らせる
* **CLI / GUI: PlantUML レンダリング失敗を検出して救出 puml にフォールバック** (`PlantUmlRenderer` / `Main` / `UmlMainFrame`)
    * `PlantUmlRenderer.renderSvg` がレンダリング結果に同梱 PlantUML のフォールバック マーカー (`"An error has occured"` / `"I love it when a plan comes together"`) を検出した場合、新例外 `PlantUmlRenderFailedException` を投げる。これまでは PlantUML 内部で Smetana の `IllegalStateException` が握り潰され、エラー画像 SVG がそのまま `dependency-graph.svg` 等として保存されていた
    * **CLI `--all`**: 各図 (component / manifest / deeplink / dependency / class) のレンダ失敗を個別に補足し、`<name>.puml` を同じ出力ディレクトリに書き出し、`[juml]     -> X.svg FAILED: ...` を stderr に出して次の図に進む。`--all` 全体は他の図の出力を続行
    * **CLI 単体 (`-c` / `-d` / `-G` / `-M` / `-D`)**: SVG レンダ失敗時に隣接 `.puml` を書き出し、終了コード 2 で終了
    * **GUI `UmlMainFrame`**: SwingWorker のレンダ失敗時、これまでの巨大な base64 を含む `JOptionPane` モーダル ダイアログを廃止。ステータス バーに `<Diagram>: rendering failed — PlantUML layout error (Smetana). See 'PlantUML Source' tab.` を出し、`PlantUML Source` タブに raw puml を残し、Preview ペインをクリアする
    * **Smetana の stderr ノイズ抑制**: `UNSURE_ABOUT: safe_list_append(...)` 等の同梱 Smetana が直接 `System.err` に書くデバッグ ログを、`renderSvg` 呼び出し中だけ捨てるラッパを追加。`-v` (`--verbose`) で抑制を解除可能
    * 目的: `car_app_library` 規模 (~26 ノード) の Gradle 依存グラフで Smetana が落ちた際に、ユーザに壊れた SVG を掴ませず、再レンダリング可能な PUML テキストを救出経路として残す。GUI でも意味のないモーダル ダイアログを廃止して UX を改善する

* **CLI 引数パーサが先頭引数を消費していたバグを修正** (`Main` / `bundle/Juml.sh`)
    * 旧 `optParser.parse(args, 1)` が常に `args[0]` をスキップしていたため、`java -jar Juml.jar -c -o out.svg in.java` のように README 記載どおりに直接呼ぶと `-c` が黙って消費されて GUI モードに落ちていた。同梱 `bundle/Juml.sh` が `java -jar Juml.jar -- $@` で `--` を args[0] に注入する設計に依存していた
    * `Main.java` の `parse(args, 1)` を `parse(args)` に変更。`bundle/Juml.sh` を `java -jar "$DIR/Juml.jar" "$@"` に変更し、`--` 注入を撤去 (`OptionParser.parse` の raw モードに意図せず入って後続オプションが全て位置引数化する事故を防止)
    * `MainCliTest.java` 全 11 箇所の先頭ダミー `"juml"` を除去
    * 目的: README の CLI 例がドキュメントどおりに動くようにする

* **左ペインのプロジェクトツリーが左クリックに反応するように修正** (`ProjectTreePanel` / `UmlMainFrame`)
    * これまで `ProjectTreePanel.notifySelection()` はメソッド / クラス / Manifest / Component に対しては該当ハンドラを発火していたが、`UmlMainFrame` 側でクラス用ハンドラ (`setOnClassSelected`) を登録しておらず、また**パッケージ / モジュール** ノードは左クリックでは何も発火しない構造 (`onClassSelected(null)` に落ちるだけ) だった。結果として「左側のリストをクリックしても何も反応しない」状態だった
    * **クラスノードのクリック**: 当該クラスを `seed` + `neighborHops=1` のスコープでクラス図に切り替える (`UmlMainFrame.onTreeClassSelected`)
    * **パッケージノードのクリック**: 既存の右クリックメニュー経由のドリルダウンと同じ「該当パッケージにスコープしたクラス図」を左クリックでも開けるようにする
    * **モジュールノードのクリック**: `setOnModuleSelected` ハンドラを新設し、当該モジュールに含まれるクラスだけに絞ったクラス図に切り替える (`UmlMainFrame.onTreeModuleSelected`)
    * **メソッドノード選択時の二重発火を除去**: `MethodEntry` 経路で `onClassSelected` が併発呼び出しされていたため、`setOnClassSelected` を登録すると method クリック直後にシーケンス図がクラス図で上書きされる衝突があった。Method ノードは `onMethodSelected` のみ発火する設計に整理
    * **モジュール自動展開バグの修正**: `populate()` 完了時、`tree.expandRow(i)` を `i < root.getChildCount()` で回していたためルート行 (row 0) しか展開対象にならず、モジュールが常に折りたたまれた状態で表示されていた。`TreePath` 経由で各モジュールノードを明示的に展開するよう修正
    * **回帰防止テスト**: `UmlMainFrameSwingTest.testTreeNodeClickFiresScopeChange` で module / package / class それぞれのクリックがステータスバーに `Scope: ...` を出すところまでを GUI 経由で検証
    * 目的: ユーザーが左ペインの項目を選んだときに、それに対応した範囲のクラス図 (または既存のシーケンス図 / Manifest 図) へ自然に切り替わるようにし、「クリックしても無反応」という UX バグを解消する

* **Android 14 / 15 manifest 属性への対応** (`AndroidPropertyInfo` / `ForegroundServiceTypeCatalog` / `AndroidManifestInfo` 拡張)
    * **`<property>` 要素のパース** (Android 12+): application / activity / service / receiver / provider 配下の `<property android:name=... value=.../resource=.../>` を `AndroidPropertyInfo` で保持。`android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 等の Android 14 必須 property を見逃さない
    * **`foregroundServiceType` カタログ化** (`ForegroundServiceTypeCatalog`): Android 10 (`dataSync` / `mediaPlayback` / `phoneCall` / `location` / `connectedDevice` / `mediaProjection`), Android 11 (`camera` / `microphone`), Android 14 (`health` / `remoteMessaging` / `shortService` / `specialUse` / `systemExempted`), Android 15 (`mediaProcessing`) の全 14 種を最小 API レベル + 対応 `FOREGROUND_SERVICE_*` permission とともに保持
    * **`|` 連結値の分解と API レベル算出**: `"dataSync|shortService"` のような連結値を分解し、構成種別の最大 API レベルを返す
    * **FOREGROUND_SERVICE permission の整合チェック**: Markdown サマリーに `Foreground Service Types` 表を追加し、各 service の foregroundServiceType に必要な permission が `<uses-permission>` 宣言されているかを yes / **MISSING** で表示
    * **Application 新属性の抽出**: `usesCleartextTraffic` / `networkSecurityConfig` / `enableOnBackInvokedCallback` (Android 13+ Predictive Back) / `localeConfig` (Android 13+) / `dataExtractionRules` (Android 12+) / `hardwareAccelerated` / `largeHeap` / `appCategory` を `AndroidManifestInfo` で保持
    * **Manifest 図への反映**: Application ノードに上記新属性を行追加。Service ノードの `fgType` 表示に `(API 35+)` のような Android 要求レベルラベルを付与。`FOREGROUND_SERVICE_*` permission は別ステレオタイプ `<<fgs>>` で橙色枠に強調
    * **Markdown サマリーへの反映**: `Application attributes (Android 12+/13+/14+)` / `Application properties` / `Foreground Service Types (Android 14+/15+)` の 3 セクションを追加
* **AndroidManifest 解析の濃厚化 (uses-sdk / 独自 permission / activity-alias / foregroundServiceType / Deep Link)**
    * **`<uses-sdk>` のパース**: `AndroidManifestInfo.minSdkVersion / targetSdkVersion / maxSdkVersion` を新設し、`AndroidManifestParser.parseUsesSdk` が読み込む。Manifest 図の Application ノードと Markdown サマリーにも反映
    * **独自 `<permission>` 宣言の保持** (`AndroidCustomPermission`)
        * アプリ自身が宣言する `<permission>` を `name / protectionLevel / permissionGroup / label / description` で保持
        * `AndroidManifestInfo.getCustomPermissions()` で取得可能。相対名は package を前置して FQN 解決
        * Markdown サマリーに `Custom Permissions (declared by app)` セクションを追加
    * **`<activity-alias>` の `targetActivity` 解決**
        * `AndroidComponentInfo.targetActivity` フィールドを追加。alias は通常 Activity と同じリストに入りつつ、`isActivityAlias()` で区別可能
        * Manifest 図のコンポーネントノードに `→ TargetActivity` の補助表示と `<<alias>>` ステレオタイプを付与
        * Markdown サマリーに `*(alias → ...)*` 表記
    * **`<service>` の `foregroundServiceType` 抽出**
        * Android 14 以降の foreground service で必須化された属性を `AndroidComponentInfo.foregroundServiceType` で保持
        * Manifest 図のコンポーネントノードに `fgType: dataSync|...` を補助表示。Markdown サマリーにも `*(foregroundServiceType: ...)*` 表記
    * **`<intent-filter>` の Deep Link 属性拡張** (`AndroidDataSpec`)
        * `<data>` 要素を 1 つずつ `AndroidDataSpec` に保持 (`scheme / host / port / path / pathPrefix / pathPattern / pathSuffix / pathAdvancedPattern / mimeType`)
        * `AndroidIntentFilter` に `autoVerify` / `order` / `dataSpecs` / `isViewDeepLink()` を追加
        * 既存の `dataSchemes` / `dataMimeTypes` は互換のため並行で埋める
        * `AndroidDataSpec.toDeepLinkUri()` が `scheme://host[:port]/path` 形式の URI を組み立て
* **新規 UML 図種: Deep Link 図** (`PlantUmlDeepLinkDiagram`)
    * `action.VIEW + category.BROWSABLE` を持つ Activity の URI 入口を 1 枚に可視化
    * scheme でグルーピング: `Web (http/https) — App Links` / `Custom scheme: <scheme>://` / `MIME-only`
    * `autoVerify="true"` の intent-filter は `<<autoVerify>>` ステレオタイプと矢印ラベルで強調 (App Links 候補)
    * CLI: `-D` / `--deeplink-diagram` で生成可能。`--all` の出力に `deeplink-diagram.svg` を追加 (出力数 7 → 8)
* **クラス図プレビューの右クリックからシーケンス図へ** (`PlantUmlClassDiagram.Options.interactiveLinks` / `PlantUmlSvgRenderer.LinkArea` / `SvgPreviewPanel.setOnLinkPopup`)
    * GUI でクラス図を表示中、クラスの枠を右クリックすると、そのクラスのメソッド一覧が `JPopupMenu` で開く。メソッドを選ぶと既存の `sequenceEntry` 経路でシーケンス図に置き換わる
    * 仕組み: クラス図生成時に各クラスへ `[[juml://class/<FQN>]]` を埋め込み、PlantUML が SVG に出力する `<a xlink:href>` 領域を `PlantUmlSvgRenderer` が抽出して `RenderedSvg.getLinkAreas()` で返す。`SvgPreviewPanel` は右クリック (`isPopupTrigger`) でその領域をヒットテストし、ヒットすればリスナを発火する
    * URL 埋め込みは GUI プレビュー描画時 (`DiagramRequest.isInteractiveLinks() == true`) のみ。CLI / `Save Diagram As...` / `--per-folder` などの出力には影響しない
    * 抽象メソッドはツリー側と同じく除外する。メソッドラベルは `name(paramType, ...)` 形式
* **フォルダ単位のクラス図一括出力** (`PerFolderClassDiagrams`)
    * プロジェクトを再帰スキャンし、ソースファイルを直接含む各フォルダごとに 1 枚ずつクラス図 (`classes.puml` + `classes.svg`) を生成。出力ディレクトリ配下に元の相対パス階層を維持して書き出す
    * 大規模プロジェクトで「全クラス 1 枚絵」が読めない/レンダリングが重い問題を緩和。`ClassIndex.source(qn)` でクラスをソース配置フォルダごとに分割する
    * CLI: `-P` / `--per-folder` を `-c` と併用し、`-o <output_dir>` でルート出力先を指定 (例: `java -jar Juml.jar -- -c --per-folder -o ./out ~/AndroidStudioProjects/MyApp`)
    * GUI: File メニューに「Export Class Diagrams Per Folder…」を追加。ロード済みプロジェクトに対して出力先を選ぶだけで実行でき、進捗バーと完了ダイアログを表示
    * `--no-legend` / `--no-comments` / `--jetpack` 等の既存表示オーバーライドはそのまま尊重 (`UmlOverrides.applyTo` 経由)
* **新規 UML 図種: Manifest 図** (`PlantUmlManifestDiagram`)
    * AndroidManifest.xml の `<application>` 属性 (package / class / theme / debuggable / allowBackup / meta-data) を中央の `<<application>>` ノードに据え、配下に Activity / Service / Receiver / Provider を種別ごとにグループ化して所属関係 (`*--`) を描く
    * 周辺に `uses-permission` / `uses-feature` を別パッケージで配置し、launcher Activity と `exported=true` は視覚的に強調 (色 / ステレオタイプ)
    * 同モジュール内に複数 manifest (main + debug + flavor 等) がある場合は sourceSet ごとに別 Application ノードを描画し、`<<src:debug>>` 等のステレオタイプを付与
    * CLI: `-M` / `--manifest-diagram` で生成可能。`--all` の出力に `manifest-diagram.svg` を追加 (出力数 6 → 7)
    * GUI: Diagram メニューに「Manifest Diagram」を追加。左ペインのプロジェクトツリーにモジュール直下の `[manifest] AndroidManifest.xml` ノードを表示し、Activities / Services / Receivers / Providers / Permissions / Features を展開可能に。Manifest 系ノードを選択すると Manifest 図に自動切替
    * 右ペインに `Manifest Summary` タブを新設し、`TextSummaryReport.toManifestMarkdown` で AndroidManifest のみに絞った Markdown サマリーを表示
* **AOSP 級プロジェクト対応 (Large project readability)** — 数万クラス規模でも「読み込めて」「図として読める」ようにパイプライン全体を刷新
    * **並列スキャン + 並列パース** (`AndroidProjectScanner`, `UmlGenerator`)
        * `AndroidProjectScanner.walk` を `Files.walkFileTree` ベースに置き換え、深い再帰でも安定動作
        * `UmlGenerator.extractFromProjectDetailed` を専用 ExecutorService (CPU - 1 並列) で並列化
        * `Options.maxFiles` で取り込み上限、`Options.cancelToken` で途中中断、`Options.useAospDefaults` で `prebuilts/.repo/out-soong/test_mapping/.cache` を追加除外
    * **進捗 + キャンセル ユーティリティ** (`juml.util.ProgressListener`, `juml.util.CancelToken`)
        * `silent() / console() / throttled(delegate, ms)` ファクトリで GUI/CLI のどちらでも使える
        * `UmlMainFrame` のステータスバーに `JProgressBar` を追加し、`File → Cancel Loading` で進行中の解析を中断可能
    * **Stage A / Stage B 二相パース** (`JavaStructureExtractor.extractHeadersOnly`, `ClassIndex`)
        * ヘッダ (パッケージ / 名前 / kind / modifiers / super / interfaces / アノテーション) のみで全件保持し、必要なクラスだけ `ClassIndex.detail(qn)` で詳細にフルパースする
        * 50,000 クラスでも数十 MB 程度に収まる想定
    * **ツリーの遅延展開** (`ProjectTreePanel`)
        * モジュール → パッケージ ノードまでだけ初期構築。パッケージ展開時にクラス、クラス展開時にメソッドを生成
        * Gradle 解析結果 (`AndroidProjectAnalyzer.inferModuleName`) と紐付け、`(other)` 集約を解消
        * パッケージノード右クリック → `Show class diagram of this package` でクラス図にドリルダウン
    * **DiagramScope による表示範囲指定** (`DiagramScope`, `DiagramScopeDialog`, `DiagramService.applyScope`)
        * パッケージ前方一致 / モジュール / 正規表現 / シード+N hop / 最大クラス数 を組み合わせて絞り込み
        * Diagram メニュー: `Scope...` で編集、`Clear Scope` で解除
        * 絞り込みで件数が減ったり `maxClasses` で切り詰められたら `footer` 行に警告を出す
    * **永続ディスクキャッシュ** (`PersistentAnalysisCache`)
        * `~/.juml/cache/<hash>/` に Stage A ヘッダ + ソースパス + モジュール紐付けを保存
        * キャッシュキー = プロジェクトルート + (path/mtime/size) 列の SHA-256。ファイルが 1 件でも変われば別ディレクトリで自動無効化
        * `lazyDetails=true` + `useDiskCache=true` (デフォルト) で利用
    * **追加テスト**: `AndroidProjectScannerScaleTest`, `UmlGeneratorParallelTest`, `ClassIndexTest`, `DiagramScopeTest`, `DiagramServiceScopeTest`, `JavaClassInfoCodecTest`, `PersistentAnalysisCacheTest`, `CacheKeyTest`, `CancelTokenTest`, `ProgressListenerTest`, `SyntheticAospScaleTest` (`-DrunPerfTests=true` でのみ実行)
    * **ブラウザ E2E / Swing GUI テスト** (`com.microsoft.playwright:playwright` + `org.assertj:assertj-swing-junit`)
        * `PlantUmlSvgPlaywrightTest` — 生成 SVG を Chromium (Playwright) でレンダリングし、クラス名がページに現れること・スコープ適用時にクラスが消えることを検証。`build/playwright/class-diagram.png` に PNG スクリーンショットを保存
        * `UmlMainFrameSwingTest` — `UmlMainFrame` を AssertJ-Swing で起動し、最小プロジェクトをロードしてツリーが構築されることを検証
        * ヘッドレス CI/サンドボックスでは `Assume.assumeNoException` (Playwright) / `Assume.assumeFalse(isHeadless())` (Swing) で自動 skip。DISPLAY が無ければ `xvfb-run -a ./gradlew test` でラップ
* **シーケンス図のプロジェクト内クラス色付け** (`PlantUmlSequenceDiagram`)
    * 入力 `classes` に含まれる解析済みクラス (= プロジェクト内の独自クラス) の participant を `#LightSkyBlue` で背景塗りつぶしし、外部ライブラリやシステムクラスと視覚的に区別できるようにした
    * `Options.highlightProjectClasses` で機能の ON/OFF、`Options.projectClassColor` で色を変更できる (空文字を指定すれば従来通り色なし)
    * 凡例ブロックにも独自クラスを示す色サンプル行を追加し、図の読み手が一目で判別できるようにした
* **クラス図コメントの色付け** (`PlantUmlClassDiagram`)
    * インラインコメント (`.. text ..`) を `<color:#008800>...</color>` で囲み、クラス本体のメンバーと視覚的に区別できるようにした
    * NOTE スタイルでは `skinparam noteBorderColor` / `skinparam noteFontColor` を自動付与し、注釈ブロックの枠線と文字色を同色に揃える
    * `Options.commentColor` で色を変更でき、空文字を指定すれば従来通り色なしで出力する
* **GUI プレビューをベクター SVG 化** (`PlantUmlSvgRenderer` + `SvgPreviewPanel`)
    * PlantUML 出力を PNG ではなく SVG として描画し、Apache Batik (`batik-bridge`)
      で `GraphicsNode` に変換して `SvgPreviewPanel` 上で直接ペイントする
    * PlantUML の PNG キャンバス 4096x4096 制約に縛られなくなり、巨大な
      クラス図でも切り詰められずに表示できる
    * ズーム時もアンチエイリアスを保ったまま再描画される
    * PNG エクスポートは保存時に `PlantUmlImageRenderer` で再生成する経路に変更
      (プレビューは常にベクターのみを保持)

2.0 (UML-only pivot)
--------

* **Juml を「Java + Android + Gradle 特化の UML ツール」へ完全転換**
    * 旧 PAD (Problem Analysis Diagram) GUI / SPD パーサ / Java→PAD 変換器を全削除 (約 9.7k LoC 減)
    * 新規 UML 専用 Swing GUI を導入 (`juml.app.uml.*`)
        * メニュー: File (Open Project / Save Diagram As... / Exit) / Diagram (5 図種ラジオ + シーケンス図起点選択) / View (Zoom In/Out/Reset/Fit) / Help
        * 左ペイン: プロジェクトのモジュール / パッケージ / クラス ツリー (`ProjectTreePanel`)
        * 右ペイン: タブ式の Preview (ズーム/パン付き `SvgPreviewPanel`) と PlantUML Source (`PumlSourcePanel`)
        * ステータスバーにズーム倍率と解析サマリを表示
    * 起動: 引数なし `java -jar Juml.jar` で UML GUI が直接起動
        * プロジェクトディレクトリを引数で渡せば初期解析
        * 旧 `-j` / `-J` / `-s` (Java→PAD) は廃止
* **新規 UML 図種: パッケージ図** (`PlantUmlPackageDiagram`)
    * パッケージごとのクラス数をボックスで表示し、継承 / 実装 / フィールド型を経由したパッケージ間の参照矢印を集約
* **シーケンス図起点選択ダイアログ**
    * 候補メソッド一覧 + サブストリング絞り込みフィールド
    * Diagram → Choose Sequence Entry... から呼び出し
* **PNG 直接プレビュー**
    * 同梱 PlantUML の PNG 出力経由で `BufferedImage` 化 (Apache Batik を経由しない)
    * SVG エクスポート時のみ `PlantUmlRenderer.renderSvg` を呼ぶ
* **エクスポート機能** (`UmlExporter`)
    * SVG / PNG / PUML の各形式に対応した一元的な保存 API
    * File → Save Diagram As... から拡張子フィルタで切替
* **CLI 整理**
    * 残オプション: `-c -q -d -G -g -m -A -Q --summary --list-methods --seq-depth` ほか UML 系すべて
    * `--all` の `pad.svg` ステップを廃止し 7 → 6 ステップに

1.7
--------

* エディタの「シーケンス図を生成」を `.puml + .svg` のファイル出力に変更
    * 従来はエディタ本文に PlantUML テキストを流し込んでいたが、SPD パーサで描画できず実用しづらかった
    * 起点メソッド選択後に保存ダイアログを出し、`.puml` と同名の `.svg` を同一ディレクトリに書き出す
    * メニュー表記を `シーケンス図を出力 (.puml + .svg)` に変更し、一括出力版と挙動を統一
* クラス図の表現力を強化 (`-c`)
    * **JavaDoc / 直前コメントの取り込み**
        * `/** ... */` ・連続する `// ...` を、直後のクラス/フィールド/メソッドに割り当て
        * 既定はインライン表示 (`.. text ..` セパレータ) で先頭 1 行を出す
        * `--comment-style note` で `note top of` / `note right of Cls::member` 方式に切替
        * `--no-comments` でコメント出力を抑制
    * **enum 定数の表示**
        * `enum E { A, B, C }` の定数を本体内に列挙
        * メンバーが共存する場合は PlantUML の `--` 区切りを自動挿入
        * `--no-enum-constants` で抑制
    * **アノテーション表示**
        * フィールド/メソッドのアノテーション (`@Nullable`, `@Deprecated` 等) を可視化
        * ノイズになりがちな `@Override` / `@SuppressWarnings` は既定で非表示
        * `--no-annotations` で完全に抑制
    * **`final` フィールドのマーキング**
        * `{final}` マーカーを PlantUML 出力に追加
        * `--no-final` で抑制
    * **凡例の自動拡張**
        * 上記が出現したダイアグラムには「メンバー修飾」「注釈」セクションを自動追加

1.6
--------

* PlantUML シーケンス図の出力機能を強化
    * `-Q` / `--sequence-diagrams` を追加: Android プロジェクトを入力に、Activity/Service/Receiver/Provider のライフサイクル起点シーケンス図を `.puml` と `.svg` の両方で `-o` ディレクトリへ一括出力
    * `--all` の `sequence-diagrams/` も `.puml` と `.svg` を併出力 (従来は SVG のみ)
    * エディタの「シーケンス図を一括出力 (ライフサイクル, .puml + .svg)」メニューから GUI でも実行可能
* UML 系 (クラス図 / シーケンス図 / コンポーネント図 / Gradle 依存グラフ) を
  SVG として直接書き出せるようにした
    * PlantUML (`net.sourceforge.plantuml:plantuml`) を同梱
    * `-o foo.svg` を指定するとツール単体で SVG を出力
    * `--all` の既定成果物を `.svg` に変更
      (`class-diagram.svg` / `component-diagram.svg` / `dependency-graph.svg` /
       `pad.svg` + `summary.md`)
    * Graphviz/dot を必要としないよう Smetana レイアウトを自動指定
      (`!pragma layout smetana` を `@startuml` 直後に自動挿入)
    * 従来の `.puml` テキスト出力は互換維持 (拡張子で切替)
* シーケンス図 (`-q`) を強化
    * 多段トレース: 呼び出し先メソッドが入力ソースに含まれていれば本体に再帰的に潜って展開 (デフォルト深さ 5、`--seq-depth N` で調整、0 で無制限)。サイクル検出付き
    * 制御構造: `if/else` → `alt/else`、単一分岐 `if` → `opt`、`while`/`for`/`do-while` → `loop`、`switch` → `alt` (case 列)、`try/catch/finally` → `group/else catch/else finally`、`synchronized` → `critical`
    * `--list-methods` オプションを追加: 入力ソース内のメソッドを `Class.method` 形式で列挙 (fzf 等で起点選択する用)
    * GUI のシーケンス図生成ダイアログを、テキスト入力から **候補リスト + 絞り込みフィールド** に変更
    * `--all` の出力に `methods.txt` (候補一覧) と `sequence-diagrams/` (Activity/Service ライフサイクル起点のシーケンス図群) を追加
* 動作対象 Java を 17 以上に引き上げ
    * `sourceCompatibility` / `targetCompatibility` を 17 に変更
    * Apache Batik を 1.14 → 1.17 へ更新 (Java 17 互換性問題 BATIK-1260 解消)
    * Checkstyle を 10.12.5 → 10.21.1 へ更新
    * 未使用依存 (`org.jfree:jfreesvg`) を除去
* ビルドシステムを Gradle 8.x / 9.x 両対応に
    * `plugins {}` ブロック + `java {}` ブロック方式へ書き換え
    * Task の lazy registration (`tasks.register`) へ移行
    * Gradle Wrapper (9.4.1) をリポジトリ同梱
* `Juml.jar` を fat jar 化
    * 依存ライブラリ (Batik 等) を jar 内に同梱し、`java -jar Juml.jar` 単独で動作可能に
    * 配布 zip も `libs/` ディレクトリ無しのフラット構成に変更

1.5
--------

* Java / Android ソースを入力とした自動図生成機能を追加
    * `-j` / `-J`: Java ソース / Gradle プロジェクトから PAD 図を生成
    * `-c`: Java/AIDL から PlantUML クラス図を生成
        * AAOS パターン (`<<CarManager>>` 等) 認識
        * AndroidManifest.xml 自動マージ (`<<Activity>>` 等)
    * `-q`: 指定メソッドから PlantUML シーケンス図を生成
    * `-d`: AndroidManifest.xml から PlantUML コンポーネント図を生成
    * `-G`: build.gradle / settings.gradle から PlantUML Gradle 依存グラフを生成
    * `-g` / `-m` / `--summary`: Gradle / Manifest / プロジェクト全体の Markdown サマリー
* Gradle Version Catalog (`gradle/libs.versions.toml`) 自動解析
    * `alias(libs.plugins.X)` を正規プラグイン ID に解決
    * `implementation(libs.X.Y)` を実 notation に解決
    * `libs.versions.X.get().toInt()` を整数値に解決
* AIDL ファイル (`.aidl`) パース対応
* エディタにファイルメニュー追加
    * Java からインポート、クラス図/シーケンス図/コンポーネント図/依存グラフ/サマリー生成
* `-v` / `--verbose` でパーサ警告を stderr に出力
* 凡例ブロック追加 (`-L` で PAD 図 ON、`--no-legend` で UML 図 OFF)

1.4
--------

* 利用ライブラリのバージョンをアップデート

* bugfix
    * SVG出力のバグを修正
    * 前提Javaバージョンを 1.8 に変更(ドキュメント上は1.8前提としていたが、一部設定などが1.7となっていた)

1.3
--------

* フォント及び色の指定機能の実装(PR:https://github.com/knaou/juml/pull/5)
* bugfix
    * https://github.com/knaou/juml/pull/5

1.2
--------

* SVG 形式で出力する機能の実装
    * Apache Batik を利用

1.1
--------

* 簡単なリファクタリング
* いくつかのbugfix
* 設定ファイルのサポート
    * ツールバー無効化
    * 「保存」メニュー・ボタンの無効化
* タイトルの改善
    * 新規の場合は NEW, ファイルと紐付いている場合はファイル名を表示
    * 保存すべき変更点がある場合は、タイトルに「*」(アスタリスク)を付与
* Win/Unix系OSのためのラッパを用意
    * Win向けには exe (GUI版とconsole版）(Launch4j を利用)
    * Unix系向けには shスクリプト
* エディタ部分で右クリックメニューを有効化
* 新規作成や保存にショートカットキーを割当
* エディやコンバータエントリポイントを統合化
    * -o オプションを使用すると、エディタを起動せず変換（コンバート）のみを行う
    * 例)
        * Juml_consoiile.exe -- -o pad.png -s 2 pad.spd
        * Juml.sh -o pad.png -s 2 pad.spd


1.0
---------

* 初期バージョン
* PAD図描画に関する基本機能の提供
