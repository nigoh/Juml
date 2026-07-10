Change log
=============

2.1
--------

* **アクティビティ図の「処理の書き漏れ」を解消: 代入文の欠落とコメント 2 種の取りこぼしを修正** (`JavaMethodInfo` / `StatementAdapter` / `JpComments` / `PlantUmlActivityDiagram` / GUI 設定系)
    * **背景**: メソッド本体の抽出が「呼び出し・宣言・制御ブロック」しか Statement 化しておらず、`total = 0;` `counter += 2;` `i++;` のような**代入・インクリメント文がアクティビティ図からまるごと欠落**していた。`while (j < 3) { j = j + 1; }` は本体が空のループに見え、代入の直前コメントだけが浮いて「コメントはあるのに処理がない」状態だった。
    * **代入文の Statement 化 (`JavaMethodInfo.Assignment`)**: `AssignExpr` (=, +=, ...) と文として現れた `i++`/`--i` を Assignment ノードとして保持し、アクティビティ図で `:total = 0;` のアクションノードとして描画。値式に含まれる呼び出しは従来どおり兄弟 Call に持ち上げるため、シーケンス図・コールグラフの挙動は不変。
    * **switch の case アーム内コメントの欠落を修正**: case アームの文列は BlockStmt を経由しないためコメント flush が働いていなかった。アーム直下のコメントを offset 順で note として出力する。
    * **波括弧なし単文ボディのコメント欠落を修正**: `else foo();` のような単文ボディに付いた前置コメントを、JavaParser が文へ帰属させたコメントから補完して note 出力する (`JpComments.attached`)。
    * **GUI トグル追加**: スタイル設定ダイアログのアクティビティ図セクションに「代入・インクリメント文を表示」を追加 (`activity.showAssignments`、既定 ON)。アプリ設定・プロジェクト単位設定の両方へ永続化。
    * テスト: `PlantUmlActivityDiagramTest` に代入描画・トグル OFF・呼び出しホイスト順序・case アームコメント・波括弧なしコメントの 5 ケースを追加。`SettingTest` / `ProjectSettingsPersistorTest` に新キーを追記。
    * 目的: 「アクティビティ図で処理が全部書かれていない / コメントが抜けている」という実利用の指摘を解消し、メソッド内の処理を漏れなく追える図にするため。

* **シーケンス図の展開深さとアクティビティ図の詳細表示を GUI 設定に追加** (`Setting` / `StyleSettingsDialog` / `DiagramService` / `ProjectSettingsPersistor` / `UmlMainFrame` / `messages*.properties`)
    * **背景**: 生成エンジン側 (`PlantUmlSequenceDiagram` / `PlantUmlActivityDiagram`) は呼び出しの再帰展開・分岐/ループ・ローカル変数・コールバック本体まで詳細に出力できるが、GUI からはシーケンス図の展開深さが 5 固定、アクティビティ図の詳細フラグは一切変更できなかった (CLI の `--seq-depth` のみ)。
    * スタイル設定ダイアログの「シーケンス図」セクションに **展開の深さ** スピナー (0-10、0 = 無制限) を追加。呼び出し先メソッドをどこまでシーケンスへ展開するかを GUI から調整できる (`sequence.maxDepth`、既定 5)。
    * **「アクティビティ図」セクションを新設**し、「ラムダ / コールバック本体を展開」「ローカル変数宣言を表示」「インラインコメントをノートとして表示」の 3 トグルを追加 (`activity.expandInlineCallbacks` / `activity.showLocalVars` / `activity.showInlineComments`、既定はすべて ON)。
    * 設定はアプリ設定 (Properties XML) とプロジェクト単位設定の両方へ永続化し、旧設定ファイルにキーが無い場合は従来と同じ既定値で読み込む (後方互換)。
    * テスト: `SettingTest` に既定値・クランプ・round-trip・旧ファイル互換の 4 ケース、`ProjectSettingsPersistorTest` に新キーの保存/復元 2 ケースを追加。リフレクションでダイアログを組む既存テスト 2 件を新シグネチャへ追従。
    * 目的: エンジンが持つ詳細化能力を GUI から使えるようにし、「処理を事細かく見たい」「大きすぎる図を浅く抑えたい」の両方向へ図の粒度を調整できるようにするため。

* **UML エディタ (Design タブ) をシーケンス図・アクティビティ図に対応** (`SketchPane` / `SketchDiagramType` / `SeqSketch*` / `ActivitySketch*` 新規 12 クラス)
    * **背景**: 自由編集エディタの Design サブタブ (GUI 図形デザイナー) はクラス図専用で、シーケンス図やアクティビティ図のテンプレートを開くと全行が「未対応」となり編集ロックされていた。
    * PlantUML テキストから図種 (クラス / シーケンス / アクティビティ) を自動判定 (`SketchDiagramType.detect`) し、対応するエディタ (ツールバー + キャンバス) へ自動で切り替える構造に再編。Undo/Redo (テキストスナップショット方式) とテキスト双方向同期は `SketchPane` で図種横断に一元管理。
    * **シーケンス図エディタ**: `participant` / `actor` 宣言、メッセージ 4 種 (`->` `->>` `-->` `-->>`)、`activate` / `deactivate` を GUI 編集。ライフライン・活性化バー・自己メッセージを描画し、メッセージの縦ドラッグ並べ替え、参加者の横ドラッグ並べ替え、2 クリックのメッセージ追加、ダブルクリック編集 (送信元/送信先/矢印/ラベル/activate 連動) に対応。
    * **アクティビティ図エディタ**: `start` / `stop` / `end`、1 行アクション (`:text;`)、`if/then/else/endif` (入れ子可) を GUI 編集。並び順から決定的に自動レイアウトするフローチャート描画 (分岐は左右ブランチ + 合流点)。ツールバー追加・右クリックメニュー (直後に追加 / then・else ブランチへ追加 / 上下移動 / 削除)・ダブルクリック編集に対応。
    * **ツール選択をアイコン付きコンボへ改善** (`SketchToolIcon`): 関係 7 種 / メッセージ 3 種のモードコンボに、線種・矢頭を描いた矢印プレビューアイコンを表示。PlantUML の矢印表記(`<|--` / `o--` / `->` 等) を文字で読まなくても形で選べるようにした (テーマ前景色に追従)。
    * 従来どおり、未対応構文 (ユースケース図、`while` / `fork`、コメント行等) を含むテキストは編集ロックしてユーザーのテキストを保全する (警告バナーは 3 図種共通の `SketchBanner` へ集約)。
    * テスト: `SeqSketchCodecTest` (13) / `ActivitySketchCodecTest` (12) / `SketchDiagramTypeTest` (8) を新設し、テンプレートの無損失 round-trip・編集ロック条件・モデル操作を固定。`SketchPaneTest` はシーケンス/アクティビティの編集可否・テキスト同期・Undo/Redo を検証する形へ拡張 (12 ケース)。
    * 目的: コードを読み込まなくても、動的な振る舞い (呼び出し順序・処理フロー) の図をゼロから GUI で描き起こせるようにし、UML エディタを「クラス図専用」から汎用のダイアグラムエディタへ育てるため。

* **クラス図レンダリング失敗を 2 件修正 (Android サンプルでの実地 e2e 検証由来)** (`KotlinLightScanner` / `PlantUmlClassRelations` / 各テスト)
    * **背景**: `android/architecture-components-samples` の全サブプロジェクトに対して全図種を一括描画したところ、2 種類のクラス図レンダリング失敗 (UML-R001 構文エラー) を検出した。
    * **バグ 1 — Kotlin コメント内の "class" 誤認 (`KotlinLightScanner`)**: KDoc/コメントや文字列リテラルに含まれる `class` という単語 (例: `/** This class holds the data ... : its name, a description, ... */`) を実クラス宣言と誤認し、"holds" という擬似クラスと、後続の英文をスーパータイプ名とする不正な関係行を生成していた。`skipNonCode` を使って非コード領域 (コメント/文字列/文字リテラル) をマスクし、クラスヘッダ検出から除外するよう修正。
    * **バグ 2 — 色付き継承/実装矢印の構文誤り (`PlantUmlClassRelations`)**: `--color-relations` / `--focus` (淡色化) 時の継承・実装エッジを `-[#color]<|--` / `-[#color]<|..` と生成していたが、同梱 PlantUML 1.2026.x では矢印ヘッド前の色ブラケットは構文エラーになる。正しい `<|-[#color]-` / `<|.[#color].` (色を矢印ヘッドと線の間に置く) へ修正。これにより全サンプルの `--color-relations` / `--jetpack` 系クラス図が描画不能だった問題を解消。
    * テスト: `KotlinLightScannerTest` にコメント/文字列内 `class` の誤認防止 2 ケース、`PlantUmlClassDiagramTest` に色付き/Focus エッジの**実レンダリング**回帰テスト 2 ケースを追加 (従来は文字列一致のみで不正構文を検出できず、既存アサートも誤形を期待していたため合わせて修正)。
    * 目的: 実プロジェクト (Kotlin/Java 混在の Android サンプル) でクラス図が黙って壊れる回帰を塞ぎ、色分け・Focus 表示を実使用可能にするため。

* **UML 描画失敗の詳細出力を強化: レンダリングエンジンの生 stderr を画面・報告テキストへ露出** (`DiagramFailureMessage` / `DiagramTabPane` / `messages*.properties`)
    * **背景**: `PlantUmlRenderFailedException` はレンダリング中に PlantUML/Smetana が stderr へ出した診断 (レイアウトエンジンの内部エラーやスタックトレース) を `stderrTail` として保持していたが、これは `logs/juml.log` にしか出ておらず、失敗カード・「エラー詳細をコピー」の報告テキストには失敗メッセージ 1 行しか載っていなかった。
    * 失敗カードに **「技術的な詳細 (レンダリングエンジンの出力)」** セクションを追加し、stderr 末尾 (最大 800 文字、超過時は末尾を残す) を等幅フォントで直接表示するようにした。
    * 「エラー詳細をコピー」で得られる報告テキストにも stderr 末尾を全文添え、ログファイルを別途添付しなくても報告が自己完結するようにした。
    * `DiagramFailureMessage.fullReason` を原因チェーン展開に対応させ、ラップされた例外 (`A ← B ← C`) でも根本原因メッセージが見えるようにした (最大 5 段)。
    * テスト: `DiagramFailureMessageTest` に `engineOutput` の抽出・切り詰め・非対象例外、失敗カードへの露出、原因チェーン展開の 7 ケースを追加。
    * 目的: 図の描画に失敗したとき「なぜ失敗したか」の根本原因を、ログファイルを開かずに画面と報告テキストからそのまま把握・共有できるようにするため。

* **リポジトリ同梱 JAR/AAR の読み込みに対応** (`GradleScriptParser` / `GradleDependency` / `DependencyJarIndex` / `UmlGenerator` / `SupertypeClassifier` / `PlantUmlClassDiagram` / `UmlCommands` / `DiagramService`)
    * **背景**: 依存 JAR の解決先が `~/.gradle/caches` と `~/.m2` に限られており、プロジェクト内に同梱された `libs/*.jar` (`files('...')` / `fileTree(dir: '...')` 宣言) が一切読み込まれず、継承先クラスが裸ノードのまま図に出ていた。
    * `files('libs/a.jar')` / `fileTree(dir: 'libs', include: [...])` (Groovy/kts の `mapOf("dir" to ...)` 方言含む) を依存として解析し、各モジュールの宣言パス + 慣習の `<module>/libs/` を `DependencyJarIndex` へ索引する。宣言された実体が無い場合は `<<missing>>` として記録。
    * prefix 集合 (`android.*` 等) で判定できない社内ライブラリのパッケージでも、依存インデックスに実在する FQN は `<<external>>` として補完ノード表示するよう分類を拡張 (CLI `-c` / GUI クラス図の両方)。
    * 目的: リポジトリに JAR を同梱するオフライン環境・社内ライブラリ構成でも、外部クラスの継承関係とメンバが図で確認できるようにするため。

* **同梱バイナリ (dot / doxygen) と jar 位置の解決を強化** (`GraphvizLocator` / `DoxygenLocator` / `Main`)
    * 同梱バイナリの探索基点を jar 隣接だけでなく `<jarDir>/bundle/` → カレントディレクトリ → `<cwd>/bundle/` へ拡張し、リポジトリから直接実行 (`build/libs/Juml.jar`) しても `bundle/graphviz` 等を検出できるようにした。jar 位置が取れない環境 (jpackage 等) でも cwd 基点の探索は継続する。
    * `-Djuml.home=<dir>` / `JUML_HOME` 環境変数によるフォールバックを追加し、解決できない場合は黙って機能低下せず AppLog に記録する。
    * 目的: 配布 zip 以外の起動形態でも同梱バイナリが「静かに見つからない」状態をなくすため。

* **図・逆参照からのソースジャンプを一気通貫で整備** (`DiagramTabPane` / `DiagramTabSupport` / `JavaSourcePanel` / `ReverseReferencePanel` / `UmlMainFrame` / `JavaClassInfo` / `TypeDeclAdapter`)
    * 図上のクラスリンクを **Ctrl(⌘)+クリック**、または右クリック →「ソースを開く」で定義ソースへ直接ジャンプできるようにした。メソッドリンクのポップアップにも「ソースを開く」を追加。
    * References (逆参照) タブの行を **ダブルクリック / Enter** で、参照箇所の `file:line` へジャンプする導線を追加 (ソート後の行でも正しい参照先に飛ぶ)。
    * 解析時にクラス宣言行 (`JavaClassInfo.startLine`) を記録し、ソースビューの着地行はインデックスの宣言行 (クラス/メソッド) を優先、無ければ従来のヒューリスティック探索へフォールバックする方式に変更 (オーバーロード誤爆の低減)。
    * **バグ修正**: インデックスに無いクラスのメソッドリンクをクリックすると、スタブの `qualifiedName` が単純名だけになりタブキーが別クラスと衝突して空図が開くことがあった問題を修正 (パッケージ名を FQN から復元)。
    * 目的: 「UML 図 → ソース定義 → 参照箇所」の往復を IDE のようにワンアクションで行えるようにするため。

* **AOSP 解析の CLI 未結線を解消: `--vintf` / `--android-mk` / `--partitions` を新設 + 走査除外の強化** (`AospCommands` / `CliDispatcher` / `CliOptions` / `core/aosp` 新規 6 クラス)
    * `--vintf`: VINTF manifest (`manifest*.xml` / `compatibility_matrix*.xml`) を走査し、HAL 宣言の Markdown レポートと、matrix の要求と device manifest の宣言を突き合わせる PlantUML 図 (必須 HAL の欠落は赤背景) を出力。
    * `--android-mk`: 実装済みだった `AndroidMkParser` を結線し、legacy Make モジュールを `--android-bp` と同じ体裁 (モジュール一覧 + 依存グラフ) で図化。
    * `--partitions`: `Android.bp` の partition 属性 (`vendor` / `product_specific` / `system_ext_specific` 等) を集計し、partition ごとの内訳と **partition 跨ぎ依存** を Markdown + PlantUML で可視化。
    * `Android.bp` / `Android.mk` 走査の除外に `prebuilts` / `.repo` / `out-soong` 等を追加し、AOSP フルツリー指定時のノイズと性能劣化を防止 (`AospScanExcludes` で `AndroidProjectScanner` の既定除外と整合)。
    * 目的: 実装済みのまま到達不能だった VINTF / Android.mk 解析を CLI から使えるようにし、Treble 境界 (partition) の把握まで Juml で完結させるため。

* **git 履歴の UML 構造 Diff を追加: コミット間のクラス構造差分を UML クラス図で比較** (`ClassStructureDiff` / `PlantUmlStructureDiffDiagram` 新設 (`juml.core.structdiff`)、`GitRepoService` / `GitFileHistoryPane` / `GitUmlDiffDialog` 新設 / `messages*.properties`)
    * **背景**: git の差分は普段コード (unified diff) で見るが、「クラス構造がどう変わったか」は行 diff からは読み取りづらい。差分そのものを UML で見られるようにする。
    * **中核 (`juml.core.structdiff`)**: 新旧 2 バージョンの解析結果 (`List<JavaClassInfo>`) を宣言単位で突き合わせる `ClassStructureDiff` (クラス = 完全修飾名、フィールド/enum 定数 = 名前、メソッド = 名前 + 引数型でオーバーロード対応。extends/implements/modifiers/型パラメータのヘッダ変化も検出)。結果を GitHub diff 風配色の PlantUML クラス図にする `PlantUmlStructureDiffDiagram` (追加 = 緑 `<<added>>`、削除 = 赤 `<<removed>>` + 打ち消し線、変更 = 黄 `<<modified>>` で旧宣言を打ち消し線併記。凡例付き、不変クラスは既定で非表示)。
    * **git 連携**: `GitRepoService` に指定 rev 時点の blob を読む `fileContentAt()` と第 1 親を返す `parentOf()` を追加 (従来どおり読み取り専用)。
    * **GUI**: File History サブタブに「UML Diff」ボタンを追加。履歴からコミットを 1 件選択 = 親コミットと比較、2 件選択 = コミット間比較。解析 → 差分計算 → PlantUML 生成 → SVG 描画を SwingWorker で背景実行し、モードレスダイアログに図 + PlantUML テキストのタブで表示 (描画失敗時もテキストは参照可能)。
    * テスト: `ClassStructureDiffTest` (15 ケース) / `PlantUmlStructureDiffDiagramTest` (14 ケース、同梱 PlantUML での実レンダリング確認含む) / `GitRepoServiceTest` に blob 読み取り・親解決の 3 ケースを追加。さらに結合レベルとして、実 git リポジトリで blob 取得 → 解析 → diff → PlantUML → Batik SVG の全連鎖をヘッドレスで通す `GitUmlDiffPipelineE2ETest` (親比較/2 コミット直接比較/初回コミット/存在しないパスの 4 ケース) と、ダイアログ実物を Xvfb 上で完走させる `GitUmlDiffDialogSwingTest` (3 ケース、リフレクション不使用) を追加。
    * 目的: コードレビューやリファクタリング確認で「このコミットで公開 API・クラス構造がどう変わったか」を図でひと目で把握できるようにするため。

* **PlantUML 1.2026.x のエスケープ回帰を修正 (HTML エンティティ → チルダエスケープ) + 未エスケープ箇所の一掃 + alias 衝突解消** (`PlantUmlCommentFormatter` / `PlantUmlCallGraphDiagram` / `PlantUmlActivityDiagram` / `PlantUmlModuleDiagram` / `PlantUmlSequenceDiagram` / `PlantUmlClassDiagram` / aosp・aaos の `PlantUml*Diagram` 4 種)
    * **背景**: 「UML が描画されない / おかしい」報告の調査で、同梱 PlantUML 1.2026.x が `&lt;` 等の **HTML エンティティを解釈せずそのまま表示する** ことを実測で確認 (`&lt;init&gt;` が文字どおり画面に出る回帰)。一方、生の `<b>` のような既知タグは書式として解釈されテキストが欠落する。
    * **修正 (中核)**: `escapeHtml` (`& < >` → エンティティ) を `escapeText` (`<` → `~<` の creole チルダエスケープ) に置き換え。全コンテキスト (メンバ行 / note / ラベル / title / WBS / legend) で元の文字どおり表示されることをレンダリング実測で確認。`>` と `&` は生のままで安全のため変換しない。
    * **未エスケープ箇所の修正**: コールグラフ (WBS) のクラス名/メソッド名 (完全に未エスケープだった)、アクティビティ図の action/条件/partition ラベルと title、モジュール図の title、シーケンス図の loop/recursive call ラベル。クラス図の `package "..."` も `quoteId` に統一。
    * **alias 衝突解消**: aosp/aaos 図の `alias()` が `foo-bar` と `foo.bar` を同じ `m_foo_bar` に潰して別ノードを合成していた問題を、置換発生時のみ元名ハッシュを付与して一意化。
    * テスト: 既存テストを新仕様へ更新 + `PlantUmlCallGraphDiagramEscapeTest` / `PlantUmlSoongDependencyDiagramAliasTest` 新設。`checkstyle` (maxWarnings=0) 通過。
    * 目的: エスケープ起因の表示崩れ・テキスト欠落・ノード合成をなくし、「図が描画されない/おかしい」原因を根本から潰すため。

* **図テキストの "..." 省略を全廃し、既定で全文表示に変更** (`PlantUmlClassDiagram` / `PlantUmlSequenceDiagram` / `PlantUmlActivityDiagram` / `PlantUmlLayoutDiagram` / `PlantUmlLayoutScreenDiagram` / `PlantUmlResourceLinkDiagram` / `Setting` / `DiagramPreset`)
    * `commentMaxLength` の既定を 60/80 → **0 (無制限)** に変更 (クラス図/シーケンス図/アクティビティ図、BALANCED プリセット、`Setting` 既定)。Android 系図のハードコード上限 (30/40/28) も 0 = 無制限に。アクティビティ図の条件ラベル固定 80 文字も撤廃。
    * **設定移行**: 旧バージョンが既定値のまま永続化した `classDiagram.commentMaxLength=60` は、未移行の設定ファイルに限り 0 へ自動移行 (`.migrated` マーカーで冪等化。60 以外のユーザ指定値と移行後の明示的な 60 は尊重)。
    * 上限を指定したい場合は従来どおりスタイル設定のスピナー / `--comment-max-length` / DETAILED プリセット (NOTE 折り返し 200) で調整可能。
    * テスト: 既定値変更へのテスト追従 + アクティビティ図の全文表示/明示指定時の切り詰めテストを追加。
    * 目的: 長いコメント・定数値・ラベルが `...`/`…` で切れて情報が失われるのをやめ、図で全文を確認できるようにするため。

* **クラス図の定数 (static final) 表示を改善: グルーピング + 値の全文併記** (`PlantUmlClassDiagram`)
    * `static final` 定数を通常フィールドの前にまとめ、間に `..` 区切り線を挿入 (enum 定数の区切りと同じ流儀)。`Options.groupConstants` (既定 true) で無効化可能。
    * 定数の初期化値は 40 文字打ち切りを廃止して全文併記 (改行を含む値は 1 行に畳む)。
    * テスト: グルーピング有効/無効・境界 (定数のみ)・長文値・空白正規化の 5 ケースを追加。
    * 目的: クラス図で定数定義とその値をひと目で確認できるようにするため。

* **UML 描画失敗の診断ログを拡充 (原因の可視化 + 失敗 PlantUML の自動保存)** (`PlantUmlRenderer` / `PlantUmlRenderFailedException` / `GraphvizLocator` / `PlantUmlImageRenderer` / `RenderFailureLog` 新設、`DiagramTabPane` / `DiagramFailureMessage` / `DiagramService` / `messages*.properties`)
    * **背景**: 描画失敗時に「なぜ失敗したか」が分からず報告もしづらかった。render パスは `AppLog` に一切記録しておらず、PlantUML 自身のエラー行情報も捨てていた。
    * **エラー診断の構造化**: エラー SVG から `[From string (line N)]` の行番号と失敗行テキストを抽出し、`PlantUmlRenderFailedException` に `getErrorLine()` / `getErrorDetail()` / `getStderrTail()` として保持。メッセージにも「何行目で・PlantUML が何と言ったか・レイアウトエンジンはどちらか」を含め、ログには失敗行前後の生成 PlantUML 抜粋を添える。
    * **stderr の保全**: 非 verbose で完全に捨てていた Smetana の stderr 出力を末尾 64KB の有界リングバッファで捕捉し、失敗時に例外とログへ添付。
    * **AppLog への配線**: 描画失敗 (`PlantUmlRenderer` + `DiagramTabPane` の握りつぶし 2 箇所)、Graphviz dot の解決結果 (どの経路で見つけたか / Smetana フォールバック)、`DiagramService` の設定取得失敗 3 箇所を `AppLog` へ記録。
    * **失敗 PlantUML の自動保存**: GUI で描画に失敗すると生成 PlantUML を `logs/render-failed-<timestamp>.puml` へ保存 (最大 20 件でローテーション)。失敗カードに保存先とログ (Help → エラーログ / logs/juml.log) への案内を表示。
    * **PNG 経路のエラー検出**: PNG エクスポートが PlantUML のエラー画像 (description が `(Error)`) を正常出力として保存していた穴を塞ぎ、例外化してダイアログ + ログで通知。
    * テスト: 診断抽出 (`extractErrorDetail` / `extractErrorLine`)・例外への行番号伝播・`RenderFailureLog` の null 安全のテストを追加。
    * 目的: 描画失敗の原因をログと保存された .puml からそのまま報告できるようにするため。

* **アプリのエラーログ機能を追加 (ログビューア + ファイル出力 + 例外捕捉)** (`AppLog` / `LogViewerDialog` 新設、`Main` / `MenuBarBuilder` / `AppCommands` / `UmlMainFrame` / `ProjectLoader` / `ExportController` / `messages*.properties` / `AppLogTest` / `LogViewerDialogSwingTest`)
    * **背景**: アプリが大規模化したのに対し、エラーや警告を後から確認する手段が `System.err` への出力しか無く、GUI 利用者は問題発生時に原因を追えなかった。
    * **中核 (`juml.util.AppLog`)**: 外部ライブラリに依存しない軽量ロギング基盤。(1) メモリ上のリングバッファ (最新 5000 件) をビューアへ供給、(2) `<basePath>/logs/juml.log` へ追記 (2MB で 1 世代ローテーション)、(3) リスナーでビューアへライブ配信。`init()` で **未捕捉例外ハンドラ** と **`System.err` のティー** を設置するため、既存の `System.err.println` 出力・スタックトレース・別スレッドの未捕捉例外も追加実装なしで取り込む。本クラスは `System.err`/`System.out` へは書かず、ティーとの無限再帰を避ける。コンソール出力の退行を防ぐため、ティー前の元 `System.err` を保持して未捕捉例外はコンソールにも 1 度だけ出す。
    * **ビューア (`LogViewerDialog`)**: 図操作を妨げないモードレスダイアログ。時刻 / レベル / スレッド / メッセージのテーブル、レベル絞り込み (All/INFO/WARN/ERROR)、行選択でスタックトレース表示、全コピー / ファイル保存 / ログファイルを開く / クリア、自動スクロールに対応。ERROR は赤・WARN は橙で色分け。`Help > エラーログ...` (Ctrl+Shift+L) とコマンドパレットから開ける。単一インスタンス管理で、各エントリの連番を使い生成直後の二重表示を防ぐ。
    * **既存箇所への配線（網羅）**: `Main.main` 冒頭で `AppLog.init()`。今まで握りつぶし / ダイアログ表示 / ステータス表示のみで**スタックトレースを捨てていた失敗箇所**を洗い出し、`AppLog.error/warn` を追加した。対象は GUI の各エクスポート (`ExportController` 4箇所 / `NoteExport` PNG / `PngBackgroundExporter` / `PerFolderExporter` / `DiagramTabSupport`)、プロジェクト/アーカイブ読込 (`ProjectLoader` 解析失敗・Android.bp 走査失敗・アーカイブ読込失敗)、解析パネル群 (`ImpactExplorerPanel` / `InsightsPanel` / `ReverseReferencePanel` / `FuncDiffPanel` の解析・レポート保存失敗)、Doxygen 実行失敗 (`DoxygenResultCache`)、外部アプリ起動失敗 (`JavaSourcePanel`)、スコープ正規表現エラー (`DiagramScopeDialog`)。あわせてインフラ層の `System.err.println` 出力 (`ProjectRepository` 7箇所 / `SettingManager` 2箇所 / `DiskAnalysisCache` / `IndexDatabase`) を、適切なレベル + スタックトレース付きの `AppLog` 呼び出しへ置き換え、`System.err` ティーとの二重記録も解消した。CLI コマンドの `stderr` 出力 (ユーザ向け正規出力) と `ErrorListener.stderr()` ファクトリはそのまま残す (後者はティー経由で取り込まれる)。
    * テスト: `AppLogTest` (記録・リングバッファ・レベルしきい値・リスナー・連番の単調増加・スタックトレース詳細を 10 ケース) と `LogViewerDialogSwingTest` (ヘッドレススキップ。既存ログ表示・ライブ追記)。`checkstyle` (maxWarnings=0) 通過。
    * 目的: 大規模化したアプリで、エラー・警告を GUI からその場で確認でき、再起動後もログファイルで原因調査できるようにするため。

* **付箋・検索ハイライト・ミニマップ・ラバーバンドがスクロールで図とずれる不具合を修正** (`SvgPreviewPanel` / `DiagramSearchLayer` / `SvgPreviewPanelScrollPaintTest` 新設)
    * **症状**: 図をスクロールすると付箋メモが画面に貼り付いたまま図と一緒に動かず (= 図とずれて見える)、右下に固定されるはずのミニマップ (全体俯瞰) がスクロールに合わせて流れてしまっていた。検索ハイライト枠・ラバーバンド選択も同様にずれていた。
    * **原因**: `SvgPreviewPanel.paintComponent` がオーバーレイ (付箋/検索/ミニマップ/ラバーバンド) を描く直前に `g2.setTransform(new AffineTransform())` で変換を identity へリセットしていた。`JViewport` はスクロール時に Graphics へ平行移動 (スクロールオフセット) を入れて描画するが、identity に戻すとそのオフセットが消えてしまう。スクロール位置 0 では平行移動が 0 なので顕在化せず、スクロールしたときだけ崩れていた。`DiagramSearchLayer.paint` も同様に identity へ戻していた。
    * **修正**: 描画開始時の「パネル基準変換 (スクロール込み)」を `baseTransform` として控え、identity リセットの代わりにこれへ戻すよう変更。`DiagramSearchLayer` 側の identity リセットは撤去し、呼び出し元が設定したパネル基準変換のまま描く。これで付箋は図に貼り付いて一緒にスクロールし、ミニマップは隅に固定されたままになる。
    * **付箋の座標系について**: 付箋の位置・サイズは元々 UML 全体の描画範囲 (SVG/図座標) を基準に保持しており (ELEMENT アンカーは対象要素相対、FREE は図全体に対する絶対位置)、`zoom` 倍してパネル座標へ写像している。設計自体は妥当で「実装の限界」ではなく、本件は描画時の変換リセットという単一バグだった。
    * テスト: `SvgPreviewPanelScrollPaintTest` でスクロール込みの `Graphics2D` へ `paintComponent` し、付箋が「図座標 − スクロール量」の位置に描かれること (= 図に追従) を画素で検証。`checkstyle` 通過。
    * 目的: スクロール時に付箋・ミニマップ等が図と正しく連動するようにし、見た目のずれを解消するため。

* **Doxygen 連携 R5: 出力キャッシュ + 同梱バイナリ配線 + リリース手順/ライセンス明記** (`DoxygenCache` 新設、`DoxygenRunner` / `DoxygenResultCache` / `build.gradle` / `README` / `DoxygenCacheTest`)
    * **出力キャッシュ**: doxygen の実行は重いので、対象プロジェクトの `*.java` に変更が無ければ前回生成した XML を再利用し、`DoxygenXmlParser` で再パースするだけにする。配置は既存規約に合わせ `~/.juml/cache/doxygen/<projectHash>/`、鮮度判定は「`*.java` の件数 + 最終更新最大値 + doxygen パス」の署名で行う (`build`/`test`/`generated`/`.gradle` 等は走査から除外)。`DoxygenRunner#runCached` を新設し、`DoxygenResultCache` 経由で全 Doxygen タブが利用する。
    * **同梱バイナリ配線**: `makeZip` の `from 'bundle'` により `bundle/doxygen/<platform>/doxygen[.exe]` は配布 ZIP へ自動同梱され、`DoxygenLocator` が jar 隣接 `doxygen/<platform>/` から検出する (Graphviz と同経路)。`copySystemDoxygen` Gradle タスク (システム導入済み doxygen を `bundle/doxygen/` へ配置する動作確認用) を追加。
    * **リリース手順/ライセンス**: doxygen は GPLv2 のため、外部プロセスとして呼ぶ「単なる集積」ではあるが、バイナリ再配布時はライセンス全文同梱・ソース入手手段提示など GPL の義務を満たす必要がある旨を `build.gradle` に明記。バイナリ自体はリポジトリにコミットせず配布物作成時に投入する運用とする。
    * テスト: `DoxygenCacheTest` (短ハッシュの安定性・署名の差分検出・`build`/`test` 除外・stamp/index による鮮度判定)。`checkstyle` 通過。
    * 目的: doxygen の重い再実行を避けて再表示を高速化し、同梱・配布の道筋とライセンス上の注意を整備するため。

* **Doxygen 連携 R4: 継承/参照メタ + グループ階層** (`DoxygenGroupsPanel` / `DoxGroup` 新設、`DoxygenXmlParser` / `DoxModel` / `DoxCompound` / `DoxMember` / `DoxygenPanel` / `UmlMainFrame`)
    * Doxygen 連携の最終コンテンツ 2 点を追加。**継承/参照メタ**は compound の `<basecompoundref>` / `<derivedcompoundref>` (基底型/派生型) とメンバーの `<references>` / `<referencedby>` (参照先/参照元) を取り込み、Doxygen タブの詳細ペインに「Base types / Known subtypes / References / Referenced by」として表示する。`referencedby` の重複名は除去する。
    * **グループ階層**は `index.xml` の `kind="group"` と `group___*.xml` (`<title>` / `<innerclass>` / `<innergroup>`) を取り込み、独立の「Groups」タブにグループ → 下位グループ・所属型のツリーを描く。トップレベルは「どのグループの下位にもならないもの」を判定し、循環参照は visited ガードで防ぐ。
    * `DoxygenResultCache` を引き続き共有し、Doxygen / TODO / Groups の 3 タブが 1 回の doxygen 実行結果を使い回す。UI 配線: 固定ユーティリティタブを 9→10 本に拡張 (`fixedSuffix`)。
    * テスト: `DoxygenXmlParserTest` に継承メタ・参照メタ (重複除去)・グループ階層 (タイトル/innerclass/innergroup) の検証を追加。`checkstyle` 通過。
    * 目的: ロードマップ上のコンテンツ 4 点 (API / TODO / グループ / 継承・参照) を出し切り、Doxygen 連携を一通り「枯らす」ため。

* **Doxygen 連携 R3: @todo/@bug/@deprecated を横断集約する独立 TODO タブ** (`DoxygenTodoPanel` / `DoxygenResultCache` / `DoxXrefItem` 新設、`DoxygenXmlParser` / `DoxModel` / `DoxygenPanel` / `UmlMainFrame`)
    * doxygen XML の `<xrefsect>` (`@todo` / `@bug` / `@deprecated`) をプロジェクト横断で集約し、種別フィルタ付きのテーブル (種別 / 箇所 / 内容) として独立の「TODO」解析タブに表示する第 3 ラウンド。発生箇所はクラス由来なら `Class`、メンバー由来なら `Class.member` を付与。
    * **二重起動の防止**: `DoxygenResultCache` (監視可能なホルダ兼共有ランナー) を新設し、Doxygen タブと TODO タブで 1 回の doxygen 解析結果 (`DoxModel`) を共有する。どちらのタブの「Run Doxygen」からでも実行でき、結果は両タブへ同時反映される。
    * `DoxygenXmlParser`: compound 本体とメンバー各々の `<detaileddescription>` 内 `<xrefsect>` を走査して `DoxXrefItem` に変換。xref のテキストは API リファレンスの散文に混ざらないよう `proseText` の除外対象に追加。
    * UI 配線: 固定ユーティリティタブを 8→9 本に拡張 (`fixedSuffix`)。テスト: `DoxygenXmlParserTest` に xref 集約 (クラス/メンバー由来の location・種別・散文非混入) を追加。`checkstyle` 通過。
    * 目的: 既存 Juml に無い「ドキュメント横断の宿題リスト」を提供し、doc コメントの `@todo`/`@bug`/`@deprecated` を一望できるようにするため。

* **Doxygen 連携 R2: API リファレンス詳細を整形表示** (`DoxygenPanel` / `DoxygenXmlParser` / `DoxMember` / `DoxCompound` / `DoxParam` 新設)
    * MVP (検出→実行→ツリー表示) に続く第 2 ラウンド。Doxygen タブを「左ツリー + 右詳細ペイン」の `JSplitPane` に拡張し、選択した型/メンバーの **API リファレンス** (brief/detailed・`@param`/`@return`/`@throws`・戻り値型) を自前の簡易 HTML (`JEditorPane`) で整形表示する。doxygen 生成 HTML は使わない方針を維持。
    * `DoxygenXmlParser`: `<detaileddescription>` を解析し、構造化要素 (`parameterlist` / `simplesect`) を除いた散文・`@param` (`kind="param"`)・`@throws` (`kind="exception"`)・`@return` (`simplesect kind="return"`)・メンバー型 (`<type>`) を抽出。`DoxMember#setDetail` / `DoxCompound#setDetailed` で後付けする (index.xml 縮退時は brief まで)。
    * テスト: `DoxygenXmlParserTest` に型・detailed・`@param`/`@return`/`@throws` 抽出の検証を追加。`checkstyleMain` / `checkstyleTest` 通過。
    * 目的: Doxygen 連携の中核価値である「ソースコメント由来の整形 API ドキュメント」を Juml 内で読めるようにするため。

* **Doxygen 連携の MVP: Java プロジェクトを doxygen で解析し XML をネイティブ表示** (`juml.core.formats.doxygen.*` 新設 / `DoxygenPanel` / `UmlMainFrame` / `Main`)
    * 外部バイナリ doxygen を `GENERATE_XML=YES` で起動し、生成 XML をパースして「Doxygen」解析タブにクラス/インタフェース/列挙 → メンバーのツリーとして表示する第 1 ラウンド (MVP)。HTML レンダラは使わない方針。
    * `DoxygenLocator`: `GraphvizLocator` と同方式で doxygen を検出 (環境変数 `DOXYGEN_BINARY` → 同梱 `bundle/doxygen/<platform>/` → PATH)。未検出時はパネルから実行ファイルを手動選択できる。
    * `DoxygenRunner`: Java 向け最小 Doxyfile (`OPTIMIZE_OUTPUT_JAVA` / `FILE_PATTERNS=*.java` / `HAVE_DOT=NO` / build・test 除外) を一時生成し `ProcessBuilder` で起動。`DoxygenXmlParser`: `index.xml` + compound XML を DOM (外部エンティティ無効化) でパースし、名前・brief・メンバーを抽出。
    * GUI は既存タブ中心アーキに忠実に解析タブとして 1 本追加 (`fixedSuffix` 7→8)。`SwingWorker` でバックグラウンド実行。
    * テスト: `DoxygenXmlParserTest` (compound/メンバー/brief 抽出・compound ファイル欠落時の index 縮退・index.xml 欠落例外・Doxyfile 生成内容) を追加。
    * 目的: Java のソースコメント由来の API ドキュメントを Juml 内で見られるようにする足場を作るため (本ラウンドは検出→実行→ツリー表示まで。API リファレンス詳細 / TODO・Bug・Deprecated 集約 / グループ階層 / 同梱バイナリ配線は後続ラウンドで拡張予定)。

* **クラス図の型解決を O(参照数 × クラス数) → O(1) 化 (`KnownTypeIndex` 新設)** (`PlantUmlClassRelations` / `PlantUmlClassDiagram` / `PlantUmlClassLegend` / `PlantUmlCommonClassesDiagram` / `PlantUmlPackageDiagram`)
    * 利用関係 (フィールド/戻り値/引数の型→ユーザ定義型) の解決で、型参照ごとに既知クラス名集合を `for (k : known) if (k.endsWith("." + name))` と**全走査**していた。大きなクラス図では O(参照数 × クラス数) になり図生成が重かった。
    * 既知クラスの「ドット区切り接尾辞 → FQN」マップを 1 度だけ構築する `KnownTypeIndex` を新設し、照合を O(1) 化。各図生成器 (クラス図/凡例/共通クラス図/パッケージ図) はループ前に索引を 1 度構築して使い回すよう変更。
    * 併せて**出力の決定性を向上**: 旧実装は同じ単純名を持つ複数 FQN のうち `HashSet` 反復順 (実質不定) の最初を返していたが、索引は**辞書順最小**を選ぶ。一意なケースの出力は不変で、曖昧なケースのみ決定的になる (再現性向上)。
    * テスト: `KnownTypeIndexTest` を追加 (完全一致 / 単純名・多段接尾辞解決 / ジェネリクス展開 / タイブレークの決定性 / Set オーバーロード同値)。`core.formats` 全 1163 件・`checkstyle` 通過。
    * 目的: 大きなクラス図の生成時間を縮め、かつ図出力を実行間で安定させるため。

* **追加のパフォーマンス改善 (付箋 / 図生成 / パース / 一覧)** (`DiagramNotesLayer` / `PlantUmlPackageDiagram` / `StatementAdapter` / `FilteredListPanel`)
    * **付箋 Markdown の再描画キャッシュ (`DiagramNotesLayer`)**: これまで付箋を 1 枚描くたびに `MarkdownRenderer.toHtml()` を呼び、パン/ズーム/操作のたびに本文を再パースしていた。本文文字列をキーに変換済み HTML を上限付き LRU でキャッシュし、編集時だけ作り直すようにした。付箋を多く貼った大きな図でも操作が軽くなる。
    * **パッケージ図解決の O(n)→O(1) 化 (`PlantUmlPackageDiagram`)**: 型参照→パッケージ解決のたびに既知クラス名集合 (`knownNames`) 全体を `stream().anyMatch(endsWith)` で線形走査していた。既知クラスの「単純名」集合を事前構築し `contains` で判定するよう変更 (旧条件と同値)。大きな図ほど図生成が速くなる。
    * **AST 二重走査の解消 (`StatementAdapter`)**: switch 式初期化子の検出で `findAll(SwitchExpr.class)` を「有無チェック」と「反復」で 2 回呼んでいた (各回が AST 全走査)。1 回に集約。インライン (lambda 等) の一般ケースでは従来どおり呼ばないよう分岐順も保持。
    * **一覧フィルタのデバウンス (`FilteredListPanel`)**: メンバー/メソッド/関数一覧の絞り込みが 1 打鍵ごとに全文再フィルタ + `setText` していた。最後の打鍵から 160ms 後に 1 度だけ適用するデバウンスを追加し、数千行の一覧でも入力がカクつかないようにした (プログラムからの `setText` は即時のまま)。
    * 目的: 描画本体 (バックバッファ) に続き、図生成・解析・対話一覧の各経路でも大規模プロジェクト時の重さを取り除くため。テスト: `core.formats` / `app.uml` (計 1379 件) が全て成功することを確認。

* **大きな図のスムーズ化: 描画バックバッファ (ラスタライズキャッシュ)** (`SvgPreviewPanel`)
    * これまでプレビューは再描画 (パン / 付箋ドラッグ / ラバーバンド選択 / ホバー由来の `repaint()`) のたびに Batik の `svgNode.paint()` を呼び、GVT ツリー全体を毎フレーム走査・描画していた。巨大な図では 1 フレームが重く、操作がカクついていた。
    * 対策として「可視領域 (ビューポート) + マージン (384px)」分だけを 1 度だけ `BufferedImage` にラスタライズしてキャッシュ (`backBuffer`) し、以降の再描画は `drawImage` (ブリット) で済ませるようにした。ズーム変更・図の差し替え時のみ再ラスタライズする。パンでマージン外へ出たときも、その時点の可視領域を中心に作り直す。
    * 計測 (182×20143 の大きな図, 1200×900 ビューポート): 操作中フレームが **約 31.2ms → 0.46ms (≒68倍)** に短縮。図を貼り付くように追従させるオーバーレイ (付箋 / 選択矩形) は従来どおりパネル座標へ描画。
    * テスト: 既存の `SvgPreviewPanelTest` / `app.uml` パッケージ (223 件) が全て通ることを確認 (状態管理・ヒットテストは不変)。
    * 目的: 大きな図を開いてもパン・ズーム・付箋操作・テキスト選択を滑らかに保つため。

* **メモリ使用量の削減: 図タブの描画解放 + 数の LRU 上限** (`DiagramTabPane` / `TabMemoryManager` / `LruTabPolicy`)
    * 各図タブは描画済み SVG (Batik のベクタ木) を保持するため、タブを多数開くとメモリが累積して重くなっていた。対策を 2 段で実施:
        1. **描画解放 (主対策)**: 直近 **4 件** (`-Djuml.renderedTabs=N`) のタブだけ描画を保持し、それ以前の古いタブは SVG (ベクタ木) を解放する。再フォーカス時に PlantUML テキストから再描画する (テキストは軽量なので残す)。これで開いているタブ数によらず常駐する描画は数枚に収まる。
        2. **数の上限**: 同時に開ける図タブ数の上限 (既定 **20**、`-Djuml.maxDiagramTabs=N`) を超えたら、アクティブ以外で最も古い未使用タブを閉じる。閉じたタブはツリーから再度開ける。
    * メモリ抑制の状態・判定を `TabMemoryManager` と純粋関数 `LruTabPolicy` に切り出し、ユニットテスト (`LruTabPolicyTest`) を追加 (Swing から独立して検証可能)。タブのクローズは既存の `closeTab()` を再利用。クローズ起因の選択変更による再入はフラグでガード。
    * 併せて `DiagramTabPane` の肥大化 (checkstyle FileLength) を解消するため、失敗メッセージ生成 (`DiagramFailureMessage`)・ツールチップ/アイコン/リンク解析 (`DiagramTabSupport`) を別クラスへ抽出。
    * 目的: 図タブを多数開いてもメモリ常駐量を一定に保ち、対話操作を軽く保つため。

* **メモリ使用量の削減: ClassIndex 詳細キャッシュの LRU 上限化 + 起動時の文字列重複排除** (`ClassIndex` / `bundle/Juml.sh`)
    * `ClassIndex.detailedCache` は Stage B 詳細パース結果を**無制限に蓄積**しており、長時間ナビゲート (シーケンス図/メンバー一覧など) するとプロジェクト全体の詳細情報がメモリに滞留して重くなっていた。アクセス順 LRU (既定上限 4096 件、システムプロパティ `juml.detailCacheSize` で調整可・0 以下で無制限) に置き換え、上限超過時に古いものから退避する。純粋なキャッシュ (ミス時は再パースで正しく復元) のため挙動は不変、メモリ上限だけが付く。
    * 起動スクリプト `bundle/Juml.sh` に G1 の文字列重複排除 (`-XX:+UseStringDeduplication`) を既定追加。Juml は型名/FQN/PlantUML 断片など重複文字列を大量に保持するためヒープ削減に効く (CPU コストは僅少)。`JUML_JAVA_OPTS` 環境変数で上書き可能 (例: `JUML_JAVA_OPTS="-Xmx2g"`)。
    * テスト: `ClassIndexTest` に「上限超過で古いエントリが退避され、退避後も再パースで正しく復元される」ケースを追加。
    * 目的: 大規模プロジェクトを長時間操作したときのメモリ肥大・GC スラッシングによる重さを抑えるため。

* **Android ブループリント (Android.bp / Soong) 解析の成熟化 (7 ラウンド)** (`juml.core.aosp.AndroidBp*` / `SoongGraphAnalysis` / `MarkdownSoongReport` / `PlantUmlSoongDependencyDiagram`)
    * これまで `--android-bp` は `name` / `srcs` / 依存名を 1 バケットに集約して図示するだけだった。実プロジェクト (AOSP/AAOS) の Blueprint を実用的に読み解けるよう、段階的に機能を拡張した。
    * **Round 1: 依存種別の保持** — `static_libs` / `shared_libs` / `header_libs` / `libs` / `defaults` / `required` 等を種別ごとに保持 (`depsByKind`)。結合ビュー (`getDeps`) は後方互換で維持。`defaults` 継承時も元の種別を保つ。
    * **Round 2: スカラ属性・パーティション抽出** — `sdk_version` / `package_name` / `stem` 等の文字列と `vendor` / `product_specific` / `system_ext_specific` 等の真偽フラグを抽出し、配置 (system / vendor / product / system_ext / odm / recovery / ramdisk) を推定。ネストブロック (target/arch) 内の同名キーには引っ張られない。
    * **Round 3: Soong 変数・連結の解決** — トップレベル変数代入 (`foo = [...]`, `foo += [...]`) を先読みし、`srcs: base + ["x.cpp"]` のような変数参照・`+` 連結を展開。実 Android.bp は変数多用のため src/dep の取りこぼしが激減。
    * **Round 4: 逆依存・循環依存解析** — `SoongGraphAnalysis` を新設。ローカルモジュール間の依存グラフから被依存ランキング (影響の大きいモジュール) を算出し、Tarjan SCC (反復実装) で循環依存を検出。
    * **Round 5: 依存図の描き分け** — 矢印を依存種別で色・線種分け (shared=青/static=緑/header=紫破線/defaults=灰点線/required=橙点線)、循環メンバを赤背景で強調、凡例を追加。生成 PlantUML が構文エラー 0 件で描画できることを実レンダリングで確認。
    * **Round 6: レポート拡充** — モジュール種別ヒストグラム、配置 (パーティション) 別集計、テストモジュール数、各表に Partition / SDK 列とテスト印 (⚙) を追加。Treble の system↔vendor 境界を一目で把握。
    * **Round 7: AIDL インタフェース詳細** — `aidl_interface` の `stability` (vintf 等) / `unstable` / `versions` 数 / `backend { java/cpp/ndk/rust }` の有効言語を抽出し、レポートに AIDL インタフェース表を追加。Binder/VINTF 境界の解析に直結。
    * 合成 AOSP ツリー (vehicle aidl_interface + Car android_app + framework java_library + 変数/defaults/循環) で `--android-bp` を実行し、変数展開・配置判定・AIDL backend・循環検出 (`CarService ↔ car-frameworks-service`) が正しく出力され、PlantUML が構文エラー 0 件で描画できることを確認。
    * 目的: Android のビルド定義 (ブループリント) を、ソースを開かずともモジュール構成・依存・配置・IPC 境界の観点で俯瞰できるようにし、AOSP/AAOS のような巨大ツリーの全体把握を支援するため。

* **Android プロジェクト/Manifest サマリーの見出し・専門用語を日本語併記** (`TextSummaryReport`)
    * `--summary` / `-m` のテキストサマリーで、`# Android Project Summary` / `## Modules` / `## Components` / `## Permissions` / `## Features` などの英語見出しに日本語補足を併記 (例: 「## Components (アプリの部品: 画面・サービス等)」)。`Foreground Service Types` セクションに「通知を出しながら裏で動き続ける処理」という前提説明を追加し、表ヘッダも日本語併記。`Predictive Back` に「戻る操作のプレビュー表示」を補足。
    * 実サンプルで `--summary` を出力し、全見出しが日本語併記になることを確認 (テストの英語部分文字列アサーションは併記で維持)。
    * 目的: アプリ概要レポート (最初に生成されやすい) を、Android 用語を知らない人でも見出しから内容を把握できるようにするため。

* **ライフサイクル・シーケンス図のタイトルに「いつ呼ばれるか」を併記** (`LifecycleSequenceDiagrams`)
    * `--all` / シーケンス図一括出力で生成される Android ライフサイクル図 (onCreate / onResume / onStartCommand / onReceive / query 等) のタイトルが `Class.method` のみで、素人は「そのメソッドがいつ呼ばれる処理か」が分からなかった。Activity/Application/Service/BroadcastReceiver/ContentProvider 各種別ごとに呼び出しタイミングの日本語説明を持たせ、タイトルを「MainActivity.onCreate — 画面が最初に作られるとき」のように出力 (呼び出し元の Options は退避・復元して非破壊)。
    * 実サンプルで `--all` を実行し、`sequence-diagrams/MainActivity.onCreate.puml` のタイトルに説明が付くことを確認。
    * 目的: 「処理の流れ」図でも、Android のライフサイクルを知らない人が各図の意味（発生タイミング）を掴めるようにするため。

* **操作種別ラベルと設定レポート表ヘッダを素人向け日本語化 (実生成物で検証)** (`UiActionEntry.ActionType` / `MarkdownSettingsReport`)
    * UI 操作種別を `onClick` / `android:onClick` / `onLongClick` 等の生 API 名から「タップ (onClick)」「長押し (onLongClick)」「ON/OFF 切替 (onCheckedChanged)」「メニュー選択」へ。設定レポートの残る英語表ヘッダ (Key/Type/Default/Read At/Written At, Store Name, Preference XML 表) を日本語化。
    * サンプル Android プロジェクト (Manifest + Activity/Service/Receiver/Provider + 権限 + Deep Link + Layout + Navigation + Preferences) で全 Android 図種・レポートを実出力し、凡例・ラベル・見出しが素人向けに表示されることを SVG/Markdown レベルで確認 (PlantUML 構文エラー 0 件)。
    * 目的: レポートの「操作の種類」列を、API 名を知らない人でも一目で理解できるようにするため。

* **UI 操作一覧・設定レポートの見出しを日本語併記に** (`MarkdownActionReport` / `MarkdownSettingsReport`)
    * `# UI Action Map` / `## Click Handlers` / `# Settings Report` / `## SharedPreferences Keys` など英語のみの見出し・空状態メッセージを日本語併記 (例: 「UI 操作マップ」「クリック操作」「設定の保存・定義レポート」) にし、各レポート冒頭に「何の一覧か」の 1 行説明を追加。テーブル見出しも日本語化。
    * 目的: Android 固有の解析レポート (操作と処理の対応 / 設定の保存先) を、素人が見出しだけで内容を把握できるようにするため。

* **GUI「ヘルプ > 使い方」に「どの図を見ればいい?」図種ガイドを追加** (`MenuBarBuilder.showUsageDialog`)
    * 図種が多く、初心者は「目的に対してどの図を選べばよいか」が分からなかった。Usage ダイアログに用途別ガイド (全体把握=Component/Manifest、画面の流れ=Screen Flow/Navigation、構造=Class/Inheritance/Package、処理の流れ=Sequence/Activity/Call Graph、見た目=Layout、重要クラス=Common/Cycles 等) を追加。
    * 目的: 初心者が「やりたいこと」から逆引きで図を選べるようにするため。

* **画面遷移図のタイトル日本語化 + 「遷移なし」時の案内表示** (`PlantUmlScreenFlowDiagram` / `MarkdownScreenFlowReport`)
    * タイトルを `Screen Flow (Intent + Screen.push)` から「画面遷移図 (Screen Flow)」へ。遷移が 1 件も無いとき、図が真っ白・レポートが英語一行で素人が戸惑っていたため、「画面遷移が見つかりませんでした。startActivity / Fragment 切替 / Navigation 等が対象に含まれているか確認してください」という案内を図とレポート双方に表示するようにした。
    * 目的: 画面遷移図 (遷移図) を開いたときに、空でも英語でも迷わず次の行動が分かるようにするため。

* **全図種のツールチップに「何の役に立つ図か」を追記** (`messages*.properties` / `ToolBarBuilderTest`)
    * これまで CLASS / PACKAGE / COMPONENT / MANIFEST / SCREEN_FLOW / BUILD_NINJA / INTERMEDIATES など多くの図種でツールチップ補足が無く、素人は「どの図を見ればよいか」の手掛かりが無かった。全 20 図種に用途説明を ja/en で追加 (例: 画面遷移図=「どの画面からどの画面へ移動するかの遷移図」、Manifest 図=「AndroidManifest に書かれたアプリ構成の一覧」)。
    * テスト: `ToolBarBuilderTest` を「全図種が先頭スペース付きの非空ツールチップを返す」検証に更新。
    * 目的: 図種が多くて選びにくい問題を解消し、初心者が目的から図を選べるようにするため。

* **GUI のプロジェクト解析失敗ダイアログを i18n 化 + 原因ヒントを表示** (`ProjectLoader` / `messages*.properties`)
    * これまで解析失敗時は英語固定で `Failed to analyze project: ...` / タイトル `Error` を出すだけで、素人は「何が悪いのか」「次に何をすべきか」が分からなかった。`dialog.analyzeFailed.title` / `dialog.analyzeFailed.message` を追加し、「プロジェクトのルート (build.gradle がある階層) を指定しているか」「読み取り権限があるか」という確認ポイントを日本語/英語で表示するようにした。
    * 目的: 解析が失敗したときに、初心者が自力で原因を切り分けられるようにするため。

* **レイアウト図・Deep Link 図の凡例を素人向けに書き換え** (`PlantUmlLayoutDiagram` / `PlantUmlDeepLinkDiagram`)
    * レイアウト図の `ViewGroup` / `View` / `MP=match_parent` / `WC=wrap_content` を、Android を知らない人向けに「他の部品を中に並べる入れ物」「親いっぱいに広げる」等の言葉で説明。
    * Deep Link 図に「Deep Link = 特定の URL をタップするとアプリの画面が開く仕組み」という前提説明を追加し、applink/deeplink/mime/autoVerify を平易に解説。
    * 目的: ビュー階層・Deep Link という Android 特有概念を、前提知識なしでも図の凡例だけで理解できるようにするため。

* **Android 各図の凡例を「何をするものか」付きで素人向けに書き換え** (`PlantUmlNavigationGraphDiagram` / `PlantUmlComponentDiagram` / `PlantUmlManifestDiagram`)
    * これまで凡例は `Fragment 画面` / `Service` / `BroadcastReceiver` / `ContentProvider` のように Android 用語をそのまま並べるだけで、素人には各部品が何をするのか分からなかった。各項目に 1 行説明を追加 (例: `Service: 画面を持たず裏で動く処理`、`ContentProvider: 他アプリとデータを共有する窓口`、`exported=true: 他アプリから呼び出せる=外部公開`)。
    * Navigation 図 (Jetpack Navigation) の凡例に「四角=1つの画面 / 矢印=画面移動」「[*] の矢印=起動時に最初に出る画面」や、色分け (fragment=青/activity=黄/dialog=緑…) の意味を明記。
    * 目的: コンポーネント図・Manifest 図・Navigation 図を、Android の専門用語を知らない人でも凡例だけで読み解けるようにするため。

* **画面遷移図・レポートを素人向けに改善 (遷移ラベルの日本語化 + 凡例追加)** (`ScreenTransition.Kind` / `PlantUmlScreenFlowDiagram` / `MarkdownScreenFlowReport`)
    * 画面遷移図 (`--screen-flow`) の矢印ラベルが `START_FOR_RESULT` / `SCREEN_PUSH` などの生の enum 名で、プログラム・Android 素人には何の遷移か全く伝わらなかった。`ScreenTransition.Kind` に `jpLabel()` (例: 「結果を受け取る遷移」) と `jpDescription()` (例: 「開いた画面から結果を返してもらう (startActivityForResult)」) を追加し、図とレポートで日本語ラベルを使うようにした。
    * PlantUML 図と Markdown レポートの双方に、**実際に図中へ現れた遷移種別だけ**を「日本語ラベル: 意味」で並べる凡例を追加。素人がその場で矢印の意味を確認できる。
    * テスト: `IntentNavigationDetectorTest` の生 enum 名アサーションを日本語ラベル + 凡例の検証に更新。
    * 目的: Android 素人が画面遷移図を見たときに、専門用語 (startActivityForResult 等) を知らなくても「どんな遷移か」を読み取れるようにするため。

* **Apktool で逆コンパイルした APK の図化** (`juml.core.formats.android.apk` パッケージ新規 + CLI `--apk*`)
    * `apktool d app.apk` の出力ディレクトリ (`apktool.yml` / `AndroidManifest.xml` / `smali*/`) をそのまま解析対象にできる。ソースコードが手元に無い APK でも、smali (逆アセンブル済み Dalvik バイトコード) のクラスヘッダから **クラス図** を復元できるのが狙い。Apktool 自体の実行・ネットワークアクセスは一切せず、ローカルファイルを読むだけ (Juml の安全方針を維持)。
    * **smali パーサ** (`SmaliParser` / `SmaliClassInfo` / `SmaliTypeDescriptor`): `.class`/`.super`/`.implements`/`.field`/`.method` のシグネチャのみを抽出 (命令本体は ASM ヘッダ抽出と同様に読み飛ばし)。型記述子 (`Lcom/Foo;` → `com.Foo`, `[I` → `int[]`, メソッド記述子) を復号し、`<init>` はコンストラクタとして単純名表示、synthetic/bridge は既定で除外。
    * **apktool.yml パーサ** (`ApktoolYmlParser`): YAML 依存を足さずインデントベースの軽量パーサで versionCode/Name・min/targetSdk・isFrameworkApk・doNotCompress・usesFramework を抽出 (fat jar を肥大化させない)。
    * **解析器** (`ApktoolDecodedAnalyzer` / `ApkAnalysis`): 逆コンパイルディレクトリを検出し、apktool.yml + AndroidManifest.xml を既存パーサで解析、multidex (`smali`, `smali_classes2`, …) を横断して全 `.smali` を収集。パッケージプレフィックス絞り込み・synthetic 取り込み・クラス数上限 (巨大 APK の安全弁) をオプション化。
    * **出力**: `PlantUmlSmaliClassDiagram` (パッケージ別グループ + extends/implements、スコープ外スーパータイプは `<<external>>` ノードで文脈保持) と `ApkSummaryReport` (apktool.yml メタ + manifest 構成 + smali 統計の Markdown)。
    * **Apktool 同梱で `.apk` を直接入力可能** (`ApktoolDecoder` + `apktool-lib` 依存追加): 事前に `apktool d` を実行しなくても、`--apk*` モードに `.apk` を渡すと同梱 Apktool が **JVM 内のライブラリ呼び出し** として逆コンパイル (smali + テキスト manifest を一時ディレクトリへ抽出) してから解析する。`brut.androlib.ApkDecoder` を直接呼ぶだけで外部プロセス (aapt 等) 起動・ネットワークアクセスは無し (decode は純 Java、DEX は実行せず baksmali するだけ)。展開済みディレクトリを渡す従来経路もそのまま使える。`build.gradle` に `google()` リポジトリ (smali/baksmali は Google Maven 配布) と Apache-2.0 の `apktool-lib` を追加し、`THIRD-PARTY-NOTICES.md` を更新。
    * **smali を「見る」3 モード追加**:
        * `--apk-decode`: `.apk` を `-o` ディレクトリへ逆コンパイルするだけ (smali/ + AndroidManifest.xml + apktool.yml)。図は作らず、smali ファイルをそのまま閲覧できる。`--apk` 全部出しも `.apk` 入力時は展開結果を `<-o>/decoded/` に残すようにした。
        * `--apk-smali`: クラスごとの smali 構造 (継承/実装・フィールド・メソッドシグネチャ) を読みやすい Markdown で出力 (`SmaliStructureReport`)。生 smali より構造を掴みやすく grep もしやすい。
        * `--apk-sequence Class.method`: **smali のメソッド本体 (`invoke-*` 命令) を辿ってシーケンス図を生成** (`PlantUmlSmaliSequenceDiagram`)。エントリから深さ制限付きでスコープ内クラスへ再帰し、フレームワーク呼び出しは `<<external>>` の葉として描く。サイクル防止・メッセージ数上限つき。「APK の処理フローを UML 化」する用途に対応。`SmaliParser` を拡張し、メソッド本体の呼び出し命令 (`SmaliInvoke`) を出現順に収集するようにした。
    * **CLI**: `--apk-summary` (Markdown) / `--apk-class-diagram` (クラス図) / `--apk` (全成果物を `-o` ディレクトリへ: apk-summary.md + apk-class-diagram.svg + manifest-diagram.svg) / `--apk-smali` (クラス別構造) / `--apk-sequence Class.method` (シーケンス図) / `--apk-decode` (逆コンパイルのみ) / `--apk-package PREFIX` (smali クラスをパッケージで絞って可読性を確保)。入力は `.apk` ファイルでも逆コンパイル済みディレクトリでも可。
    * テスト: `SmaliTypeDescriptorTest` / `SmaliParserTest` / `ApktoolYmlParserTest` / `ApktoolDecodedAnalyzerTest` / `PlantUmlSmaliClassDiagramTest` (SVG レンダリングのスモーク含む) / `ApkSummaryReportTest` / `ApkCommandsTest` / `ApktoolDecoderTest` (入力検証 + Apktool 配線の確認、実 APK の e2e は `sample.apk` を置いたときだけ走る gated テスト) を追加。逆コンパイル済みフィクスチャ (`src/test/resources/samples/apk/decoded`) で end-to-end を固定。
    * 目的: リバースエンジニアリングや他社 APK の構造把握で、ソース無しでも Juml のクラス図・サマリーを使えるようにするため。

* **継承元が標準/外部ライブラリかを図に表示 + .jar/.aar/.class の直接解析** (`SupertypeClassifier` / `ArchiveClassReader` 新規, `PlantUmlClassDiagram` / `UmlGenerator` 拡張)
    * **継承元の標準ライブラリ判定 (機能A)**: `extends`/`implements` 先がプロジェクト外で、JDK 標準 (`java.*`/`javax.*`) か外部ライブラリ (`android.*`/`kotlin.*` 等) と判定できる場合、クラス図に `<<standard>>` (水色) / `<<external>>` (小麦色) のステレオタイプ付きノードを補完描画して継承線を接続する。判定は既存の `ExternalPackageMatcher` を流用し、単純名は `NameResolver` で FQN 解決 (プロジェクト解析時)。CLI `--mark-external-supertypes`、GUI は Style 設定ダイアログの「Mark external supertypes」チェックボックスで切替。既定 OFF で後方互換。
    * **.jar/.aar/.class の直接解析 (機能B)**: Gradle キャッシュ経由ではなく、任意パスのコンパイル済みバイトコード (`.jar` / `.aar` 内 `classes.jar` / 単一 `.class` / それらを含むフォルダ) を解析対象として読み込みクラス図を生成できる。`ExternalClassReader` (ASM, `SKIP_CODE`) を再利用しヘッダのみ抽出。CLI は入力パスの拡張子で自動判定 (`-c foo.jar`)、GUI は File メニュー「Open JAR/AAR/Class...」。
    * テスト: `SupertypeClassifierTest` (8) / `PlantUmlClassDiagramExternalSupertypeTest` (6) / `ArchiveClassReaderTest` (6) を追加。CLI スモークで .jar 直接解析と `--mark-external-supertypes` の標準/外部マーキングを実機確認。
    * 目的: 「継承しているクラスが標準ライブラリか？」を一目で分かるようにし、ソースが手元に無いライブラリ (jar/aar) もそのまま図化できるようにするため。

* **テストコードを既定で解析対象から除外 (`--include-tests` で復帰)** (`AndroidProjectScanner.isTestDir` / `isTestFileName` 拡張)
    * これまで除外対象は Gradle 規約の `src/test` / `src/androidTest` のみで、AOSP 流レイアウト (`tests/`, `hostsidetests/`, `carservice_unit_test/`, `CarLibUnitTest/` 等) やテストディレクトリ外の `*Test.java` は素通りしていた。ディレクトリ名の単語分割判定 (camelCase / snake_case 境界で `test`/`tests` 語を検出) とファイル名接尾辞判定 (`*Test` / `*Tests` / `*TestCase` / `*_test`) に拡張し、`AndroidProjectScanner` を使う全解析 (クラス図 / insights / impact / vhal-flow / screen-flow / settings / action-map 等) で既定除外されるようにした。CLI には `--include-tests` を追加し、従来どおりテスト込みで解析することもできる。
    * AAOS 実リポジトリ (`packages/services/Car`) での効果: `--vhal-flow` のアクセス検出 359 → 33 件 (テスト用 `CUSTOM_*`/`BOOLEAN_PROP` ノイズが全消滅)、`--impact CarPropertyService` の直接参照元 31 → 19 件 (テストクラス混入解消)、`--insights` のエントリポイント 231 → 45 件。
    * テスト: `AndroidProjectScannerTest` に AOSP レイアウト除外 / 部分文字列 (`latest`, `Contest.java`) の誤検出防止 / `--include-tests` 復帰の 4 ケースを追加。
    * 目的: 大規模 AOSP/AAOS ソースに対するレポート類が「プロダクションコードの実態」を映すようにし、デッドコード・影響解析の偽陽性を減らすため。

* **`--impact` のスコアを参照種別・参照箇所数で差別化** (`ImpactAnalyzer` / `ImpactGraph` / `MarkdownImpactReport`)
    * 従来は層数のみの単純減衰 (`1/(layer+1)`) で layer 1 が全件 0.50 / HIGH になり優先順位付けに使えなかった。スコアを「最強参照種別の重み (CALL/EXTENDS/IMPLEMENTS=1.0, TYPE_REFERENCE=0.7, ANNOTATION=0.5, IMPORT=0.4) × 層減衰 (層ごとに半減) × 件数ボーナス (+5%/箇所, 最大 +30%)」に変更し、同一呼び出し元の複数参照は 1 ノードに集約して Reason に `DIRECT_CALL x5` のように件数を併記する。リスクしきい値も HIGH ≥0.8 / MEDIUM ≥0.45 に再調整し、Markdown レポートは各層内をスコア降順で並べる。
    * テスト: `ImpactAnalyzerTest` に CALL vs IMPORT の序列 / 層減衰 / 件数併記の 3 ケースを追加。
    * 目的: 影響解析を「参照の一覧」から「どこから確認すべきかが分かる優先順位付きレポート」に格上げするため。

* **`-o` の解釈を全 CLI コマンドで統一 (既存ディレクトリ指定対応)** (`CliOutput.writeText` / `writeUmlOutput` / `writeImpactOutput` にデフォルト名引数を追加)
    * `--aidl-binding -o <既存dir>` 等で生の `FileNotFoundException` スタックトレースを吐いて落ちていた問題を修正。`-o` が既存ディレクトリの場合はコマンド固有の既定ファイル名 (`aidl-binding.md`, `selinux.md`, `vhal-flow.md`+`.puml`, `class-diagram.svg` 等) をその中に書き出す。既定名を持たない低レベル `writeText` には出力先の指定方法を案内する明確な `IOException` を実装。
    * テスト: 新規 `CliOutputTest` (9 ケース: ディレクトリ補完 / 明確なエラー / 拡張子なし sibling 出力 / svg レンダリング)。
    * 目的: 初見ユーザーが最初に踏む `-o` の地雷を除去し、コマンドごとに挙動が違う混乱をなくすため。

* **Insights を GUI に統合: 循環依存図 + Insights パネル** (`DiagramKind.CYCLES` / `InsightsPanel` 新規)
    * **循環依存図 (図種 `Cycles`)**: ツールバー / Diagram メニューから「循環」を選ぶと、パッケージ間の循環依存 (Tarjan SCC) を赤太線・赤背景でハイライトした図をタブとして開ける。`DiagramService` の図種ディスパッチに統合し、逆参照インデックスは描画時に構築 (`InsightsAnalyzer.analyzeBuildingIndex` 新規、Impact パネル初回実行と同等コスト)。
    * **Insights パネル (固定タブ)**: Impact / References / Func Diff と並ぶ固定ユーティリティタブ「Insights」を追加。「Analyze」で CLI `--insights` と同内容の Markdown レポート (エントリポイント / ホットスポット / 循環 / デッドコード候補 / 推定レイヤ) を表示し、「Save Report...」で .md 保存できる。逆参照インデックスは `ReferenceIndexCache` 経由で Impact / References パネルと共有。
    * 配線: `ToolBarBuilder` (短ラベル「循環」+ ツールチップ + DIAGRAMS_MODULE/PACKAGE セット) / `DiagramController.iconForKind` / `messages(.ja).properties` の i18n キー / `UmlMainFrame` の固定タブ (fixedSuffix 6→7)。`DiagramService` のルート必須例外 (RESOURCE_LINK/SOONG) は case 統合で整理。
    * テスト: `DiagramServiceTest` に CYCLES 2 ケース追加。Xvfb 上で GUI を実起動し、循環図のレンダリングと Insights パネルの Analyze 実行 (Juml 自身: 446 クラス / 循環 3 件) をスクリーンショットで確認。
    * 目的: CLI を使わなくても、プロジェクトを開いたまま「どこから読むか・どこが危ないか」を GUI で確認できるようにするため (第 1 弾 CLI `--insights` の GUI 化)。

* **アーキテクチャ俯瞰レポート `--insights` を追加** (`juml.core.insights` パッケージ新規: `InsightsAnalyzer` / `InsightsModel` / `MarkdownInsightsReport` / `PlantUmlPackageCycleDiagram`)
    * 未知のコードベースを読み始める「最初の 1 時間」を支援する解析モード。`java -jar Juml.jar --insights <projectDir> [-o out]` で Markdown レポート + パッケージ循環図 (PlantUML) を一括出力する (出力規約は `--impact` と同じ: `.md` / `.puml` / `.svg` / 拡張子なしで両方)。
    * 既存の逆参照インデックス (`ReferenceIndex`) / `ClassIndex` / `AndroidSuperclassDetector` を再利用し、以下を 1 回の走査ベースで集計:
        1. **エントリポイント**: `public static main` + Android コンポーネント (Manifest 宣言 / 継承チェーン検出の両方)
        2. **ホットスポット**: fan-in (参照元クラス数) / fan-out (参照先クラス数) 上位 20 件と主な参照元
        3. **パッケージ循環依存**: Tarjan SCC (非再帰実装) でサイズ 2 以上の強連結成分を列挙し、循環エッジを赤太線でハイライトした図も出力 (孤立ノードは Smetana qsort 例外回避のため描かない)
        4. **デッドコード候補**: 参照ゼロの public クラス/メソッド。`@Override` / `main` / `on*` コールバック / コンストラクタ / エントリポイント / `*Test` を除外し、Reason・Confidence を併記 (DI / リフレクション / AIDL / XML 参照は静的に見えないため免責文つき)
        5. **推定レイヤ**: パッケージ名セグメント辞書 (ui / viewmodel / domain / repository / util 等) による best-effort 分類
    * core は CLI 非依存 (`juml.core.impact` と同じ 4 点セット構成) とし、将来 GUI の図種としても追加できる構造にした。`AnalysisCommands.buildReferenceIndex` はパース結果を共有できるようリファクタリング。
    * テスト: 新規 `InsightsAnalyzerTest` (10) / `MarkdownInsightsReportTest` (7) / `PlantUmlPackageCycleDiagramTest` (5)。Juml 自身への適用で 445 クラス / 循環 3 件 / ホットスポット表を確認、循環図の SVG レンダリングも検証。
    * 目的: 図を 1 枚ずつ開く前に「どこから読むべきか・どこが危ないか」を 1 ファイルで把握できるようにし、ソース解析の初動を速くするため。

* **Gradle 依存図のスコープ別矢印** (`PlantUmlGradleDependencyGraph.arrowForScope`)
    * これまで test 系以外すべて `-->` だった矢印を、`api` 系 → `-[bold]->` (推移的に公開される依存)、`compileOnly` / `runtimeOnly` → `..>` (弱い依存) にスタイル分けし、凡例にも説明を追加した。`implementation` は従来どおり `-->`。
    * テスト: `PlantUmlGradleDependencyGraphTest` に 3 ケース追加。
    * 目的: モジュール境界設計で重要な「api か implementation か」を図から直接読み取れるようにするため。

* **GradleScriptParser の `ksp` 直書き依存の脱落を修正**
    * `ksp(libs.x)` (Version Catalog 経由) は取れていたが、`ksp("group:artifact:ver")` / `ksp(project(":x"))` の直書きが `DEP_NOTATION` / `DEP_PROJECT` 正規表現に `ksp` が無く脱落していた実バグを修正。
    * テスト: `GradleScriptParserTest.testKspDependencies` 追加。
    * 目的: KSP (Room / Hilt 等) を使うモダンな Kotlin プロジェクトでも依存グラフが欠けないようにするため。

* **リソース紐づけ図に style/テーマを追加** (`RESOURCE_LINK` 拡張 / `StyleResourceParser` / `AndroidStyleResources` 新規)
    * 既存のコード↔リソース紐づけ図 (`RESOURCE_LINK`) が `R.layout` / `R.string` / `R.id` までだったのに対し、**スタイル/テーマ** を解析対象に追加した。
    * **`styles.xml` / `themes.xml` パーサ** (`StyleResourceParser`): `<style name parent>` と配下の `<item>` を抽出。親は明示 `parent="@style/Foo"` を優先し、無ければ Android の暗黙ドット継承 (`AppTheme.Dialog` → 親 `AppTheme`) で補完。`AndroidProjectScanner.includeValues` 経由で `<string>` と同じ values XML から両方を取り込む (`AndroidProjectAnalyzer.parseValuesResources`)。
    * **抽出元** を 3 経路に拡張:
        1. コード: `R.style.*` / `setTheme(R.style.X)` (`R_REF` に `style` 追加、`ResourceReference.Kind.STYLE`)
        2. レイアウト XML: `style="@style/X"` / `android:theme="@style/X"` (属性値から `@style/` 参照を収集)
        3. Manifest: `<application android:theme="@style/X">` を Application クラス → style として記録
    * **図** に紫の `<<style>>` ノードを追加: クラス⋯>style (`R.style` / `theme`)、レイアウト⋯>style (`style`)、そして **style ⋯|> 親style (`extends`)** の継承チェーンを再帰的に描画 (`AndroidProjectAnalysis.resolveStyleParent`)。凡例も更新。
    * テスト: 新規 `StyleResourceParserTest` (7 ケース) ＋ `ResourceLinkAnalyzerTest` / `PlantUmlResourceLinkDiagramTest` に style ケースを追加。既存テスト全 PASS / checkstyle clean。
    * 目的: 「どのクラス/レイアウト/Manifest がどのスタイル・テーマに紐づき、そのテーマがどう継承されているか」まで 1 枚で追えるようにするため。

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
