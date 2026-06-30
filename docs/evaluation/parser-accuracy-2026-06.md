# 解析精度改善ログ (2026-06)

`juml-explore` ワークフローで Juml 自身の **Java→PlantUML 解析エンジンの精度 (accuracy)**
を高めるための作業記録。「探索 → 修正 → 検証 → 記録」を **5ループ** 繰り返し、各ループで
1つの弱点を実コード修正＋ユニットテストで潰し、その所見と残課題を本ファイルに残す。

## 概要: 5ループの目的と対象範囲

- 対象: Java 構造抽出 (`core/formats/java/jp/`)・字句解析 (`JavaLexer`)・PlantUML 図生成
  (`PlantUml*Diagram`)・Android/AOSP 軽量パーサ (`core/aosp/`)。
- 各ループは「明示した1パターン」に限定し、軽量パーサの性質を壊す過度な一般化はしない。
- 各ループ = 1コミット（実コード修正＋テスト追加＋本ドキュメント追記）。

| Loop | テーマ | 対象ファイル | 状態 |
|---|---|---|---|
| 1 | SAM 名推定に add/register リスナーを対応 | `JavaParseSupport` | 完了 |
| 2 | Android.bp モジュール名のネスト name シャドウ防止 | `AndroidBpParser` | 完了 |
| 3 | SELinux allow の complement(`~`)/wildcard(`*`) 対応 | `SelinuxPolicyParser` | 完了 |
| 4 | メソッド参照レシーバの FQN/ジェネリクス正規化 | `ExpressionAdapter` | 完了 |
| 5 | Android.bp の defaults 継承解決 | `AndroidBpParser` | 完了 |

---

## Loop 1: Resolve SAM names for add/register listener registrations: add/register 系リスナー登録の SAM 名解決

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/uml/JavaParseSupport.java`
  `resolveSamMethodName(type, nameHint)` は `type` が不明なとき、メソッド名 (`nameHint`) の
  接頭辞解決を **`set` のみ** 対応していた。`add` / `register` / `remove` / `unregister` 系の
  登録メソッドは接頭辞を剥がせず、型名を復元できなかった。
- 症状: シーケンス図でラムダ/メソッド参照の参加者名 (SAM メソッド名) が `<inline>` に落ちる。
  `setOnClickListener(v -> …)` は `onClick` に解決される一方、同義の
  `addOnLayoutChangeListener(v -> …)` / `registerNetworkCallback(c -> …)` は解決されず非対称だった。
- 再現入力（最小例）:
  ```java
  view.addOnLayoutChangeListener(v -> doX());   // before: <inline>
  cm.registerNetworkCallback(c -> handle());    // before: <inline>
  ```

### 修正 (Fix)

- `Add registration-prefix table and shared resolver: 登録メソッド接頭辞テーブルと共有リゾルバを追加`
  目的: `set/add/remove/register/unregister + 型名` から SAM 型名を復元し、シーケンス図の
  参加者名を意味のある SAM メソッド名 (`onLayoutChange` 等) にするため。
- 変更点:
  - `SAM_NAME_PREFIXES = {"unregister","register","remove","add","set"}` を定義
    （`unregister` を `register` より先に並べ、接頭辞の先取りを防止）。
  - `resolveSamFromRegistrationName(nameHint)` ヘルパを新設し、`set` 限定だった分岐を置換。
    接頭辞を剥がした型名を既存の `SAM_FALLBACK` / サフィックス命名規約に再投入する。
- 非登録の `addXxx`/`removeXxx`（例 `addItem`）は従来どおり `<inline>` にフォールバックし、
  挙動互換を維持。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/JavaStructureExtractorInlineTest.java`):
  - `testMethodInternalAddListenerLambdaResolvesSamName` … `addOnLayoutChangeListener` → `onLayoutChange`
  - `testMethodInternalRegisterCallbackLambdaResolvesSamName` … `registerNetworkCallback` → `network`
  - `testAddListenerWithKnownSamMapResolvesToOnClick` … `addOnClickListener` → `onClick`
- 実行: `./gradlew test --tests "juml.core.formats.uml.JavaStructureExtractorInlineTest" --tests "juml.core.formats.uml.PlantUmlSequenceInlineTest"` → BUILD SUCCESSFUL（既存含め全件 PASSED）。

### 残課題 (Open Issues)

- `registerNetworkCallback` の解決結果は `network`（`Callback` サフィックス命名規約）であり、
  実 API の SAM メソッド名（`onAvailable` 等）とは一致しない。SAM 名の厳密化はソルバ併用時の
  別テーマとして将来検討。
- 接頭辞テーブルは代表的な4語に限定。`bind`/`attach` 等は誤検出リスクを避けるため未対応。

---

## Loop 2: Prevent nested-block name from shadowing the Soong module name: ネストブロックの name によるモジュール名シャドウを防止

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/aosp/AndroidBpParser.java` の `buildModule` は
  `NAME_PATTERN.matcher(body).find()` で **body 全体の最初の `name:`** をモジュール名に採用していた。
  `target` / `arch` / `product_variables` などのネストブロック内に `name:` があり、それが
  トップレベルの `name:` より前に出現すると、モジュール名が誤った値で確定する。
- 症状: Soong 依存グラフのノード識別子が誤り、エッジが別ノードに接続される（識別子の誤り）。
- 再現入力（最小例）:
  ```
  cc_library {
      target: { android: { name: "wrong_nested_name" } },
      name: "libfoo",
  }
  ```
  before: モジュール名 = `wrong_nested_name`（誤）/ after: `libfoo`。

### 修正 (Fix)

- `Mask nested blocks before extracting the module name: モジュール名抽出前にネストブロックをマスク`
  目的: モジュールの識別子はトップレベル宣言のみを正とし、ネスト内 `name:` のシャドウを防ぐため。
- 変更点:
  - `maskNestedBlocks(body)` を新設（ブレース深さ ≥ 1 の内側を空白に置換、長さ保持、
    文字列リテラル・ネスト対応）。`stripComments` と同じ長さ保持方針で行情報を壊さない。
  - `NAME_PATTERN` のマッチ対象を `maskNestedBlocks(body)` に変更。
- **意図的な非対象（設計判断）**: `srcs` / `*_libs` のネスト集約は **既存テスト
  `handlesNestedBlocks` が意図的に「全 srcs を集める」挙動を固定** している。これは粗い
  概観図としての設計意図なので変更せず、`name` の識別子確定のみをトップレベル限定にした。
  当初プランの `ignoresNestedArchSrcs`（ネスト srcs を除外）は、この記録済みの意図と矛盾する
  ため採用しない。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/aosp/AndroidBpParserTest.java`):
  - `nameNotShadowedByNestedBlockName` … ネスト `name:` を無視しトップレベルを採用
  - `nestedSrcsStillAggregatedIntoModule` … srcs のネスト集約が維持されている境界を固定
- 実行: `./gradlew test --tests "juml.core.aosp.AndroidBpParserTest"` → BUILD SUCCESSFUL
  （既存 `handlesNestedBlocks` 含め全 11 件 PASSED）。

### 残課題 (Open Issues)

- ネスト srcs/deps をアーキ別に区別して保持する（粗い集約をやめる）かは設計判断が必要なため、
  本ループでは触れない。将来 `AndroidBpModule` にアーキ別プロパティを持たせる拡張で対応可能。

---

## Loop 3: Support complement (~) and wildcard (*) in SELinux allow rules: SELinux allow の補集合(~)・ワイルドカード(*)対応

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/aosp/SelinuxPolicyParser.java` の `ALLOW_RULE` 正規表現は
  subject/target/class/permission の文字クラスに **`*`（ワイルドカード）・`~`（補集合）を含まず**、
  permission の選択肢も `{ ... }` か単一識別子のみだった。
- 症状: AOSP sepolicy で頻出する以下が **マッチ失敗 → ルールごと欠落**:
  - `allow foo_t bar_t:file ~{ append };`（補集合 permission）
  - `allow appdomain *:process fork;`（ワイルドカード target）
  - `allow foo_t self:capability *;`（全許可 permission）
- 結果として SELinux レポート / 図でこれらの allow ルールが見えず、権限解析の網羅性が下がる。

### 修正 (Fix)

- `Allow * and ~ in SELinux allow-rule regex and flag complement sets: allow ルール正規表現に * と ~ を許容し補集合を区別`
  目的: AOSP sepolicy の補集合・ワイルドカード構文を取りこぼさず、補集合 permission を
  「これら以外を許可」の意味として区別して記録するため。
- 変更点:
  - `SelinuxPolicyParser.ALLOW_RULE` の subject/target/class 文字クラスに `*` `~` を追加。
  - permission 部を `(~)?{ ... }`（補集合マーカ付き）と `* | ident`（ワイルドカード単一許可）に拡張。
    グループ番号がずれるため `parseSource` の参照を group(5→complement) / group(6) / group(7) に更新。
  - `SelinuxRule` に `complement` フラグ（`isComplement()` / `Builder.complement()`）を追加。
    `toString()` は補集合時のみ `~{ ... }` を出力し、通常ルールの出力は不変。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/aosp/SelinuxPolicyParserTest.java`):
  - `parsesComplementPermission` … `~{ append }` を補集合として取得
  - `parsesWildcardTarget` … `*` target を取得
  - `parsesWildcardSinglePermission` … 単一 `*` permission を取得
  - `plainAllowRuleIsNotComplement` … 通常ルールは `complement=false` のまま（回帰防止）
- 実行: `./gradlew test --tests "juml.core.aosp.SelinuxPolicyParserTest"` → BUILD SUCCESSFUL
  （既存含め全 13 件 PASSED）。

### 残課題 (Open Issues)

- `.cil`（Treble 以降の主要形式）は依然として走査対象外（`collectTeFiles` は `.te` のみ）。
  Javadoc は `.cil` 対応を謳うので、実装と乖離がある。予備候補として将来ループで解消したい。
- 単一 `~ident`（ブレースなし補集合）は稀なため未対応。必要になれば permission 選択肢を拡張する。

---

## Loop 4: Normalize method-reference receiver (fold FQN, strip generics): メソッド参照レシーバの正規化（FQN 畳み込み・ジェネリクス除去）

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/java/jp/ExpressionAdapter.java` はメソッド参照
  `Scope::id` の receiver を `mr.getScope().toString()` のまま使っていた。
- 検証で判明した事実: JavaParser はシンボル解決なしだと **メソッド参照の Scope を一律
  `TypeExpr`** として返す（`Foo` / `a.b.c` / `com.example.Mapper` / `Foo<Bar>` / `System.out`
  すべて TypeExpr）。そのため「型 FQN」と「インスタンス連鎖」をノード型では区別できない。
- 症状:
  - `stream.map(com.example.Mapper::convert)` → participant が `com.example.Mapper`（冗長）
  - `Foo<Bar>::baz` → participant に `<Bar>` が混入し PlantUML 構文を壊し得る
- 単純に `JpText.outer` を一律適用すると `a.b.c` → `c`、`System.out` → `System` となり、既存の
  意図的挙動（`testInstanceMethodReferenceFieldInitCaptured` が `a.b.c` を期待）を壊す。

### 修正 (Fix)

- `Add methodRefReceiver normalizer keyed on last-segment casing: 末尾セグメントの大文字小文字で型 FQN を見分ける正規化ヘルパを追加`
  目的: 型 FQN とジェネリクスだけを畳み、インスタンス連鎖は従来どおり残して participant 名を
  安定・簡潔にするため。
- 変更点: `methodRefReceiver(MethodReferenceExpr)` を新設し、`walk` と `buildInline` の
  2か所の `mr.getScope().toString()` を置換。表層綴りで判定する:
  - ジェネリクス（最初の `<` 以降）は常に除去（`Foo<Bar>` → `Foo`）
  - 末尾セグメントが大文字始まりの FQN は単純名へ畳む（`com.example.Mapper` → `Mapper`）
  - 小文字終わりの連鎖は原文維持（`a.b.c` / `System.out`、`Worker::tick` の `Worker` も不変）

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/JavaStructureExtractorInlineTest.java`):
  - `testFqnTypeMethodReferenceFoldsToSimpleName` … `com.example.Mapper::convert` → `Mapper`
  - `testGenericMethodReferenceStripsTypeArguments` … `Foo<Bar>::baz` → `Foo`
  - `testInstanceChainMethodReferenceKeptAsIs` … `a.b.c::method` → `a.b.c`（回帰防止）
- 一時的に probe テストで Scope のノード型を実測（全 TypeExpr を確認）し、判定方針を決定後に削除。
- 実行: `./gradlew test --tests "...JavaStructureExtractorInlineTest" --tests
  "...PlantUmlSequenceInlineTest" --tests "...PlantUmlSequenceSyntaxSafetyTest"` → BUILD SUCCESSFUL
  （既存 `testInstanceMethodReferenceFieldInitCaptured` / `testFieldMethodReferenceExpanded` 含め PASSED）。

### 残課題 (Open Issues)

- 綴りヒューリスティック（末尾大文字＝型）なので、定数 receiver（`Constants.FOO::method` のような
  全大文字フィールド）を型と誤認する可能性は残る。ソルバ併用時はノード解決で精密化できる。

---

## Loop 5: Resolve Android.bp defaults inheritance: Android.bp の defaults 継承を解決

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/aosp/AndroidBpParser.java` の `buildModule` はモジュールの
  ローカルプロパティしか読まず、`cc_defaults` / `java_defaults` を `defaults: ["x_defaults"]`
  で継承したときの `shared_libs` 等を展開しなかった。
- 症状: Soong 依存グラフでエッジ欠落。`defaults` 名（`x_defaults`）はノードに出るが、その
  defaults が実際に持ち込む依存（`liblog` 等）が消費側モジュールに反映されない。
- 再現入力（最小例）:
  ```
  cc_defaults { name: "x_defaults", shared_libs: ["liblog"] }
  cc_library  { name: "libx", defaults: ["x_defaults"] }   // before: libx に liblog が出ない
  ```

### 修正 (Fix)

- `Add snapshot-based, cycle-guarded defaults resolver: スナップショット方式で循環に強い defaults リゾルバを追加`
  目的: defaults 経由で継承される srcs/依存を消費側へ展開し、依存グラフの欠落エッジを補うため。
- 変更点:
  - `resolveDefaults(List<AndroidBpModule>)` と再帰ヘルパ `walkDefaults` を新設。`analyzeProject`
    が全ファイルのモジュールを集めた **後** に呼ぶ（defaults が別ファイル定義でも横断解決できる）。
  - 継承対象は「参照先の型が `*_defaults` で終わるモジュール」だけに限定し、通常の `shared_libs`
    依存を推移的に取り込まない（`deps` には defaults 値も集約済みだが型で判別）。
  - チェーン（defaults が defaults を参照）は再帰し、循環は `visited` 集合で停止。各モジュールの
    元 srcs/deps をスナップショットしてから解決するので適用順に依存しない。重複は追加時に除去。
- モデル変更は不要（`AndroidBpModule` はそのまま）。`defaults` 参照名はノードとして保持。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/aosp/AndroidBpParserTest.java`):
  - `inheritsDefaultsDeps` … `x_defaults` の `liblog` / `shared.c` を `libx` が継承、自身の srcs も保持
  - `doesNotTransitivelyExpandNonDefaultsLibs` … 通常 lib 依存は推移展開しない（`libbar` を引き込まない）
  - `resolvesChainedDefaultsWithoutInfiniteLoop` … defaults チェーン＋循環でも停止し `liba`/`libb` を集約
- 実行: `./gradlew test --tests "juml.core.aosp.AndroidBpParserTest"` → 全 14 件 PASSED。

### 残課題 (Open Issues)

- `*_defaults` 型判定に依存するため、別名（type を持たない include 由来の defaults 等）は対象外。
- 依存は集約のみで、どの defaults 由来かの出所はモデルに保持しない。出所追跡が必要なら
  `AndroidBpModule` 拡張が要る。

---

## 全体検証 (Whole-suite verification)

- `./gradlew test checkstyleMain checkstyleTest` → **BUILD SUCCESSFUL**（全テスト + Checkstyle 緑）。
- 各ループは「探索 → 修正 → 検証（新規テスト）→ 記録」を1コミットで完結。既存テストの意図
  （例: `handlesNestedBlocks` の srcs 集約）は尊重し、矛盾する変更は採用しなかった。

## 変更サマリー（CLAUDE.md テンプレート準拠）

- `Resolve SAM names for add/register listeners: add/register 系リスナーの SAM 名を解決`
  目的: シーケンス図の参加者名が `<inline>` に落ちるのを防ぎ、可読性を上げるため。
- `Prevent nested name shadowing the Soong module name: ネスト name によるモジュール名シャドウを防止`
  目的: 依存グラフのノード識別子が誤った名前で確定するのを防ぐため。
- `Support ~/* in SELinux allow rules: SELinux allow の補集合/ワイルドカードに対応`
  目的: AOSP sepolicy の頻出構文を取りこぼさず権限解析の網羅性を上げるため。
- `Normalize method-reference receivers: メソッド参照レシーバを正規化`
  目的: 冗長な FQN/ジェネリクスで participant 名が崩れるのを防ぐため。
- `Resolve Android.bp defaults inheritance: Android.bp の defaults 継承を解決`
  目的: defaults 経由の依存欠落で Soong グラフのエッジが落ちるのを防ぐため。

## 次にやること（あれば）

- `Support SELinux .cil files: SELinux の .cil を走査対象に追加`
  目的: Treble 以降の主要形式 `.cil` を解析できず Javadoc と実装が乖離しているため。
- `Widen VehiclePropertyCatalog constant matching: VehiclePropertyCatalog の定数判定を拡張`
  目的: 修飾子順序違い/アノテーション付き定義の取りこぼしを無くすため。
- `Track arch-specific srcs/deps separately: アーキ別 srcs/deps を区別保持`
  目的: 粗い集約をやめ、Soong 図でアーキ別の構成を表現できるようにするため。

---

# 追加ループ (Loop 6〜15)

Loop 1〜5（PR #166・main マージ済み）に続く第2バッチ。Android/AOSP 系の軽量パーサを中心に、
同じ「探索 → 修正 → 検証 → 記録」で精度の弱点を1ループ1件ずつ潰す。既存テストが現挙動を
意図的に固定している候補（Nav ネスト展開・RRO 複数 overlay 等）は安易に覆さない方針。

| Loop | テーマ | 対象 | 状態 |
|---|---|---|---|
| 6 | Manifest uses-permission/feature を `<manifest>` 直下限定 | `AndroidManifestParser` | 完了 |
| 7 | SELinux stripComments のクオート誤判定（行コメント） | `SelinuxPolicyParser` | 完了 |
| 8 | VersionCatalog インライン値のシングルクオート対応 | `VersionCatalogParser` | 完了 |
| 9 | Gradle `platform()`/`enforcedPlatform()` 依存抽出 | `GradleScriptParser` | 完了 |
| 10 | AndroidMk deps の `$(var)` 未展開ゴミ除外 | `AndroidMkParser` | 完了 |
| 11 | VHAL 呼び出し引数のネスト括弧対応 | `VhalAnalyzer` | 完了 |
| 12 | VINTF `<fqname>`（AIDL HAL）対応 | `VintfManifestParser` | 完了 |
| 13 | AIDL 単純名衝突の imports 絞り込み | `AidlBindingResolver` | 完了 |
| 14 | Navigation deepLink の action/mimeType 対応 | `AndroidNavigationGraphParser` | 完了 |
| 15 | StringResource の `<xliff:g>` プレースホルダ正規化 | `StringResourceParser` | 完了 |

> 注: 当初プランの「Manifest `<data>` 分割属性マージ」は、Android の cross-product セマンティクスが
> 複雑で deep-link 図出力への影響が大きく、リスク管理の観点から本バッチでは見送り（残課題に記載）。
> 代わりに低リスクな AIDL 衝突解決・StringResource 正規化を採用した。

## Loop 6: Collect uses-permission/feature only as direct manifest children: uses-permission/feature を manifest 直下限定で収集

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/android/AndroidManifestParser.java` の
  `parseUsesPermissions` / `parseUsesFeatures` は `root.getElementsByTagName(...)` で
  **子孫全体**を再帰的に集めていた。
- 症状: Android 11+ の `<queries>` 配下に書かれた `<uses-permission>` は「アプリが要求する権限」
  ではないのに、要求権限一覧 (`getPermissions()`) に混入する。
- 再現入力（最小例）:
  ```xml
  <queries><uses-permission android:name="perm.IN_QUERIES"/></queries>
  <uses-permission android:name="perm.TOP_LEVEL"/>
  ```
  before: `IN_QUERIES` と `TOP_LEVEL` の両方 / after: `TOP_LEVEL` のみ。

### 修正 (Fix)

- `Use childElements (direct children) for uses-permission/feature: uses-permission/feature を直下要素で収集`
  目的: `<queries>` などネスト配下の宣言を要求権限/機能として誤収集しないため。
- 変更点: 既存の `childElements(root, name)` ヘルパに切り替え。`uses-permission` / `uses-feature`
  はいずれも `<manifest>` 直下が正しい宣言位置。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/android/AndroidManifestParserStressTest.java`):
  - `testUsesPermissionUnderQueriesNotCollected` … `<queries>` 配下の権限を除外、直下のみ採用。
- 実行: `./gradlew test --tests "...AndroidManifestParserStressTest" --tests
  "...AndroidManifestParserTest"` → BUILD SUCCESSFUL（既存含め PASSED）。

### 残課題 (Open Issues)

- `<application>` 配下に誤って書かれた `uses-permission`（不正だが現実に存在）は仕様上無視されるが、
  lint 的な警告は出していない。可視化用途では現状で十分。

## Loop 7: Close SELinux string state at newline to avoid phantom rules: SELinux 文字列状態を改行で閉じ phantom ルールを防止

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/aosp/SelinuxPolicyParser.java` の `stripComments` は `"` を
  文字列開始として扱うが、対になっていない裸の `"` があると `inString` が **改行を跨いで** 広がる。
- 症状: その間にある `#` 行コメントが除去されず、コメントアウトされた `allow` 行が「生きたルール」
  として ALLOW_RULE にマッチし、phantom（偽の許可）ルールが抽出される。
- 再現入力（最小例・`.te`）:
  ```
  type foo"_t;
  # allow evil_t target_t:file write;
  allow good_t bar_t:file read;
  ```
  before: `evil_t` の allow も抽出される / after: `good_t` の 1 件のみ。

### 修正 (Fix)

- `Reset inString at newline in SELinux stripComments: stripComments で改行時に文字列状態をリセット`
  目的: `.te` に複数行文字列が無い前提で、裸の `"` の影響を当該行に閉じ込め、後続行の `#` コメント
  除去を壊さないため。
- 変更点: `stripComments` の `inString` 分岐の先頭で `\n` を検出したら `inString=false` にして改行を
  そのまま出力。長さ保持は不変（既存 `stripCommentsPreservesLength` は維持）。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/aosp/SelinuxPolicyParserTest.java`):
  - `strayQuoteDoesNotRevivePhantomCommentedRule` … 裸の `"` 後のコメント行が生き返らず、
    生きた allow は `good_t` の 1 件のみ。
- 実行: `./gradlew test --tests "juml.core.aosp.SelinuxPolicyParserTest"` → BUILD SUCCESSFUL（全件 PASSED）。

### 残課題 (Open Issues)

- CIL（`.cil`）の正規文字列は複数行を含み得るが、本パーサは `.te` のみ走査するため影響なし。
  `.cil` 対応時は文字列処理を別系統にする必要がある（既出の `.cil` 残課題と合流）。

## Loop 8: Accept single-quoted values in Version Catalog inline tables: Version Catalog インラインテーブルのシングルクオート値に対応

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/android/VersionCatalogParser.java` の `KV_PATTERN` は
  `= "..."`（ダブルクオート）のみを拾い、`= '...'`（シングルクオート）を無視していた。
- 症状: Gradle はインラインテーブルでシングルクオートも許容するため、`{ id = 'x', version = '1.0' }`
  形式のライブラリ/プラグインが `id`/`module` 等を解決できず登録されない。
- 再現入力（最小例）:
  ```toml
  [libraries]
  material = { module = 'com.google.android.material:material', version = '1.10.0' }
  ```
  before: `material` が解決されない / after: group/name/version を正しく取得。

### 修正 (Fix)

- `Match both single- and double-quoted inline-table values: インラインテーブル値をダブル/シングル両対応`
  目的: TOML 標準（ダブルのみ）と異なり Gradle 実装はシングルも通すため、両方の値を取りこぼさないため。
- 変更点: `KV_PATTERN` を `"([^"]*)"|'([^']*)'` の選択にし、`parseInlineTable` で
  ダブル側(group 2)優先・無ければシングル側(group 3)を採用。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/android/VersionCatalogParserTest.java`):
  - `testInlineTableSingleQuotedValues` … シングルクオートの module/version を解決。
- 実行: `./gradlew test --tests "...VersionCatalogParserTest"` → BUILD SUCCESSFUL（全件 PASSED）。

### 残課題 (Open Issues)

- TOML のリテラル文字列（シングル）とベーシック文字列（ダブル）のエスケープ差異は未考慮。
  バージョン文字列にエスケープが入ることは稀なため実害は小さい。

## Loop 9: Extract platform()/enforcedPlatform() BOM dependencies: platform()/enforcedPlatform() の BOM 依存を抽出

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/android/GradleScriptParser.java` の `DEP_NOTATION` は
  `scope ("座標")` を前提とし、scope と座標文字列の間に `platform(` / `enforcedPlatform(` が挟まる
  ケースを拾えなかった。
- 症状: `implementation platform("androidx.compose:compose-bom:…")` のような **BOM 依存** が
  依存一覧から欠落し、Compose/JUnit BOM などのバージョン整合が図に出ない。
- 再現入力（最小例）:
  ```groovy
  dependencies {
    implementation platform("androidx.compose:compose-bom:2024.02.00")
    api(enforcedPlatform('org.junit:junit-bom:5.10.0'))
  }
  ```
  before: 0 件 / after: 2 件抽出。

### 修正 (Fix)

- `Add DEP_PLATFORM pattern for BOM dependencies: BOM 依存用の DEP_PLATFORM パターンを追加`
  目的: `platform()`/`enforcedPlatform()` でラップされた座標を取りこぼさず依存グラフに反映するため。
- 変更点: `DEP_PLATFORM`（scope と座標の間に `(?:enforcedPlatform|platform)(` を許容）を新設し、
  `parseDependenciesBlock` で抽出。notation は `platform('g:n:v')` 形式で保持し、`seen` 集合で重複防止。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/android/GradleScriptParserStressTest.java`):
  - `testPlatformBomDependencyExtracted` … `platform("…")` と `enforcedPlatform('…')` を 2 件抽出。
- 実行: `./gradlew test --tests "...GradleScriptParserStressTest" --tests "...GradleScriptParserTest"`
  → BUILD SUCCESSFUL（全件 PASSED）。

### 残課題 (Open Issues)

- `implementation(platform(libs.compose.bom))` のような Version Catalog × platform の組み合わせは
  別途 `DEP_CATALOG_REF` 側の対応が要る（本ループのスコープ外）。

## Loop 10: Exclude unexpanded Make variable refs from AndroidMk deps: AndroidMk の依存から未展開 Make 変数参照を除外

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/aosp/AndroidMkParser.java` は `LOCAL_SHARED_LIBRARIES` 等の値を
  `splitValues` でトークン化してそのまま `getDeps()` に入れていた。
- 症状: `$(my_libs)` のような未展開の Make 変数参照が依存名として混入し、依存グラフに実モジュールで
  ない「変数参照ゴミ」ノード/エッジが現れる。
- 再現入力（最小例・`Android.mk`）:
  ```makefile
  LOCAL_SHARED_LIBRARIES := $(my_libs) libfoo
  ```
  before: deps = `[$(my_libs), libfoo]` / after: deps = `[libfoo]`。

### 修正 (Fix)

- `Filter $-prefixed tokens from dependency lists: 依存リストから $ 始まりのトークンを除外`
  目的: 実モジュール名だけを依存として採用し、未展開変数によるノイズを排除するため。
- 変更点: `isMakeVarRef(token)`（`$` 始まりを変数参照と判定）を新設し、`DEP_KEYS` の値を deps へ
  追加する際にフィルタ。`LOCAL_SRC_FILES`（原文保持が必要）は従来どおり無加工。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/aosp/AndroidMkParserTest.java`):
  - `testUnexpandedVariableDepsExcluded` … `$(my_libs)` を除外し `libfoo` のみ残す。
- 実行: `./gradlew test --tests "juml.core.aosp.AndroidMkParserTest"` → BUILD SUCCESSFUL（全件 PASSED）。

### 残課題 (Open Issues)

- 変数の実値展開（`my_libs := ...` を解決して deps に反映）は行わない。簡易パーサの範囲を超えるため、
  必要なら変数テーブルを持つ拡張で対応。

## Loop 11: Capture VHAL call arguments with nested parentheses: ネスト括弧を含む VHAL 呼び出し引数を取得

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/aaos/VhalAnalyzer.java` の `CALL_PATTERN` / `STATIC_CALL_PATTERN` は
  引数列を `\(([^)]*)\)` で捕捉していたため、最初の `)` で切れた。
- 症状: 第1引数自体がメソッド呼び出し（`getProperty(getPropId(VENDOR_X), area)`）だと引数列が
  途中で切れ、Property トークンが壊れ、Area トークン（第2引数）が欠落する。
- 再現入力（最小例）:
  ```java
  mCpm.getProperty(getPropId(VENDOR_X), GLOBAL_AREA);
  ```
  before: property=`getPropId(VENDOR_X`（破損）・area=空 / after: property=`getPropId(VENDOR_X)`・area=`GLOBAL_AREA`。

### 修正 (Fix)

- `Match up to '(' and scan balanced args manually: '(' まで正規表現で当て、引数は対応括弧まで手動走査`
  目的: ネスト括弧の引数でも引数列を完全に取り出し、Property/Area トークンを正確に解決するため。
- 変更点: 両パターンの引数捕捉 `\(([^)]*)\)` を `\(` までに変更（グループ番号も整理）。`balancedArgs(src,
  openParen)` を新設し、対応する `)` までの内側文字列を返す。以降は既存のネスト対応 `splitArgs` に委譲。
  対応括弧が無い場合はスキップ。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/aaos/VhalAnalyzerTest.java`):
  - `handlesNestedParenthesesInArguments` … `getProperty(getPropId(VENDOR_X), GLOBAL_AREA)` で
    property=`getPropId(VENDOR_X)`・area=`GLOBAL_AREA` を取得。
- 実行: `./gradlew test --tests "juml.core.aaos.VhalAnalyzerTest"` → BUILD SUCCESSFUL（既存含め PASSED）。

### 残課題 (Open Issues)

- property トークンが `getPropId(...)` のような動的計算の場合、`VehiclePropertyCatalog` での
  定数名解決はできない（実行時計算のため）。トークン原文の保持までが本ループの範囲。

## Loop 12: Parse AIDL HAL <fqname> instances in VINTF manifests: VINTF の AIDL HAL <fqname> インスタンスを解析

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/aosp/VintfManifestParser.java` の `parseHal` は HIDL 形式の
  `<interface><name>+<instance>` のみを読み、AIDL HAL の `<fqname>IFoo/default</fqname>` を無視していた。
- 症状: 現代 AOSP で主流の AIDL HAL（`format="aidl"`）のインスタンス（`default` 等）が `VintfInterface`
  に入らず、HAL 一覧が name のみで「インタフェース空」になる。
- 再現入力（最小例）:
  ```xml
  <hal format="aidl">
    <name>android.hardware.foo</name>
    <fqname>IFoo/default</fqname>
    <fqname>IFoo/secondary</fqname>
  </hal>
  ```
  before: interface 0 件 / after: `IFoo` に `default`/`secondary` の 2 instance。

### 修正 (Fix)

- `Parse <fqname> and merge instances by interface name: <fqname> を解析し interface 名でインスタンスをマージ`
  目的: AIDL HAL のインスタンス宣言を取りこぼさず、HIDL 形式と同じ `VintfInterface` モデルに統合するため。
- 変更点: `<fqname>` の子要素を走査し `iface/instance` に分解。`findOrAddInterface(h, name)` ヘルパで
  同名 interface を再利用しつつ instance をマージ（`<interface>` 形式との重複もこのヘルパで統合）。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/aosp/VintfManifestParserTest.java`):
  - `testAidlHalFqnameForm` … 2 つの `<fqname>` を同名 `IFoo` の 2 instance にマージ。
- 実行: `./gradlew test --tests "juml.core.aosp.VintfManifestParserTest"` → BUILD SUCCESSFUL（既存 HIDL 含め PASSED）。

### 残課題 (Open Issues)

- `<fqname>` が `android.hardware.foo.IFoo/default` のように完全修飾名を含む場合はそのまま interface 名に
  使う。HAL 名との重複除去や version 子要素（AIDL の `<version>` 整数）の正規化は将来課題。

## Loop 13: Preserve same-simple-name AIDL collisions and narrow by imports: 同名 AIDL の衝突を保持し imports で絞り込み

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/aaos/AidlBindingResolver.java` は AIDL 一覧を
  `Map<simpleName, FQN>` で持ち、`put` で **後勝ち** 上書きしていた。
- 症状: 同名 AIDL（`a.IFoo` と `b.IFoo`）が両方あると一方が消え、実装が `extends IFoo.Stub`
  （単純名）で `import a.IFoo` していても、imports 絞り込みが消えた候補を見つけられず、
  生き残った別パッケージの `IFoo` に誤紐付けされた。
- 再現入力（最小例）: `a.IFoo` と `b.IFoo` があり、`import a.IFoo; class FooImpl extends IFoo.Stub`。
  before: `b.IFoo` に誤紐付け / after: `a.IFoo` に正しく紐付け、`b.IFoo` は空。

### 修正 (Fix)

- `Hold simple-name candidates as a list and disambiguate via imports: 単純名候補をリスト保持し imports で曖昧性解消`
  目的: 同名 AIDL の衝突を潰さず保持し、実装クラスの import でパッケージを特定するため。
- 変更点: `aidlBySimpleName` を `Map<String, List<String>>` 化（`computeIfAbsent`）。`matchAidl` は
  FQN 一致を `aidlFqnSet` で確認、単純名は候補が1つなら即採用、複数なら imports で `candidates` から
  絞り込み、絞れなければ先頭を best-effort 採用。`firstOf` ヘルパを追加。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/aaos/AidlBindingResolverTest.java`):
  - `resolvesSameSimpleNameCollisionViaImports` … `b.IFoo` を後に登録しても `import a.IFoo` により
    `a.IFoo` へ紐付け、`b.IFoo` は空。
- 実行: `./gradlew test --tests "juml.core.aaos.AidlBindingResolverTest"` → BUILD SUCCESSFUL（既存含め PASSED）。

### 残課題 (Open Issues)

- import の無い単純名衝突は依然 best-effort（先頭採用）。同一ファイル内 package 一致など追加ヒント
  での精緻化は将来課題。

## Loop 14: Capture implicit deepLinks declared by action/mimeType: action/mimeType だけの暗黙 deeplink を取得

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/android/AndroidNavigationGraphParser.java` の
  `parseDestination` は `<deepLink>` から `app:uri` のみを取得し、`app:action` / `app:mimeType` を無視。
- 症状: Navigation Component の暗黙 deeplink（`uri` が無く `action`/`mimeType` だけで宣言される）が
  完全に欠落し、画面遷移図に出ない。
- 再現入力（最小例）:
  ```xml
  <deepLink app:action="android.intent.action.VIEW" app:mimeType="image/*"/>
  <deepLink app:action="android.intent.action.SEND"/>
  ```
  before: 0 件 / after: `android.intent.action.VIEW (image/*)` と `android.intent.action.SEND`。

### 修正 (Fix)

- `Synthesize a deepLink descriptor from action/mimeType when uri is absent: uri 不在時に action/mimeType から記述子を合成`
  目的: 暗黙 deeplink を取りこぼさず可視化に反映するため。モデル変更なし（`List<String>` のまま）。
- 変更点: `<deepLink>` 処理で uri が空のとき `buildImplicitDeepLink(action, mimeType)` を呼び、
  `action (mimeType)` / `action` / `mimeType` の記述子を `getDeepLinks()` に追加。uri 有りは従来どおり。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/android/AndroidNavigationGraphParserTest.java`):
  - `testImplicitDeepLinkWithoutUri` … action+mimeType と action のみの 2 件を記述子化。
- 実行: `./gradlew test --tests "...AndroidNavigationGraphParserTest"` → BUILD SUCCESSFUL（既存
  `testDeepLink` 含め PASSED）。

### 残課題 (Open Issues)

- 記述子は表示用の合成文字列で、uri 形式の deeplink と型が同じ `List<String>`。厳密に種別を区別したい
  場合は `NavigationDestination` に構造化 deeplink を持たせるモデル拡張が必要（将来課題）。

## Loop 15: Normalize xliff:g placeholders to {id} tokens in string resources: 文字列リソースの xliff:g を {id} トークンへ正規化

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/android/StringResourceParser.java` は `<string>` の値を
  `getTextContent()` で取得していたため、`<xliff:g id="name">%1$s</xliff:g>` の入れ子構造が
  平坦化され、プレースホルダが「どの引数か」という意味（id）を失っていた。
- 症状: 翻訳プレースホルダのラベルが `%1$s` のような匿名表記に潰れ、可読性が下がる。
- 再現入力（最小例）:
  ```xml
  <string name="welcome">Hello <xliff:g id="name">%1$s</xliff:g>!</string>
  ```
  before: `Hello %1$s!` / after: `Hello {name}!`。

### 修正 (Fix)

- `Walk child nodes and render xliff:g as {id}: 子ノードを走査し xliff:g を {id} として描画`
  目的: 翻訳プレースホルダの意味（引数 id）を表示ラベルに残すため。
- 変更点: `getTextContent()` の代わりに `extractStringText`/`appendNodeText` で子ノードを走査。
  `<xliff:g>`（namespace-aware で localName=`g`）は `id` 属性があれば `{id}`、無ければ中身を連結。
  その他の要素（b/i 等）は従来どおり中身テキストを連結。plain string の挙動は不変。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/android/StringResourceParserTest.java`):
  - `testXliffPlaceholderNormalizedToToken` … `{name}` / `{n}` トークンへ正規化。
- 実行: `./gradlew test --tests "...StringResourceParserTest"` → BUILD SUCCESSFUL（既存含め PASSED）。

### 残課題 (Open Issues)

- `id` の無い `<xliff:g>` は中身（`%1$s` 等）にフォールバックする。`example` 属性の活用は未対応。

---

## 第2バッチ 全体検証 (Loop 6〜15 whole-suite verification)

- `./gradlew test checkstyleMain checkstyleTest` → **BUILD SUCCESSFUL**（全テスト + Checkstyle 緑）。
- 各ループは1コミット（実コード + 新規テスト + 本ドキュメント追記）。既存テストが意図的に固定する
  挙動（`handlesNestedBlocks` / `testComplexIntentFilter` / `testDeepLink` 等）は壊さず維持。

## 変更サマリー（Loop 6〜15・CLAUDE.md テンプレート準拠）

- `Manifest uses-permission/feature direct children only: Manifest の uses-permission/feature を直下限定`
  目的: `<queries>` 配下の権限を要求権限として誤収集しないため。
- `SELinux stripComments newline reset: SELinux stripComments を改行でリセット`
  目的: 裸の `"` でコメントアウト行が phantom ルールに化けるのを防ぐため。
- `Version Catalog single-quoted inline values: Version Catalog のシングルクオート値に対応`
  目的: Gradle のシングルクオート表記のライブラリ/プラグインを取りこぼさないため。
- `Gradle platform()/enforcedPlatform() deps: Gradle の BOM 依存を抽出`
  目的: BOM 依存欠落でバージョン整合が図に出ないのを防ぐため。
- `AndroidMk drop unexpanded var deps: AndroidMk の未展開変数依存を除外`
  目的: `$(var)` ゴミノードを依存グラフから排除するため。
- `VHAL nested-paren arguments: VHAL のネスト括弧引数に対応`
  目的: `getProperty(getPropId(X), area)` で Property/Area トークンが壊れるのを防ぐため。
- `VINTF AIDL <fqname> instances: VINTF の AIDL HAL fqname を解析`
  目的: 現代 AOSP 主流の AIDL HAL インスタンスを取りこぼさないため。
- `AIDL simple-name collision via imports: AIDL 同名衝突を imports で解決`
  目的: 同名 AIDL の取りこぼし・誤紐付けを防ぐため。
- `Navigation implicit deepLinks: Navigation の暗黙 deeplink を取得`
  目的: action/mimeType だけの deeplink 欠落を防ぐため。
- `StringResource xliff:g tokens: StringResource の xliff:g をトークン化`
  目的: 翻訳プレースホルダの意味（引数 id）をラベルに残すため。

## 次にやること（残課題・フォローアップ候補）

- `Manifest <data> cross-product merge: Manifest の <data> 分割属性をマージ`
  目的: scheme/host 分割宣言を Android セマンティクス通り 1 URI に統合するため（リスク高につき要設計）。
- `Gradle compileSdk preview names: Gradle の compileSdk プレビュー名に対応`
  目的: `compileSdk = "android-VanillaIceCream"` の文字列 SDK を取りこぼさないため（モデル拡張要）。
- `RRO multiple overlay elements: RRO の複数 overlay 対応`
  目的: 1 マニフェスト複数 overlay を取りこぼさないため（戻り値契約の変更要）。
- `Navigation nested graph expansion: Navigation のネストグラフ展開`
  目的: ネスト navigation 配下の destination を可視化するため（既存テストがフラットモデルを固定・要更新）。
