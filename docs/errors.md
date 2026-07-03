# Juml エラーコード一覧 (Error Code Reference)

<!-- このファイルは `gradle generateErrorDocs` により `juml.util.ErrorCode` から自動生成されます。手で編集しないでください。 -->
<!-- Generated from juml.util.ErrorCode by `gradle generateErrorDocs`. Do not edit by hand. -->

ログ・画面に表示されるエラー ID (例: `UML-R001`) から原因と対処法を引くためのカタログです。アプリ内では「ヘルプ → エラーコード一覧」から同じ内容を参照できます。

ID の形式は `領域プレフィックス + 3 桁連番`、ERROR / WARN の別は ID に含めずログのレベル欄で表します。採番・追加のルールは `.claude/rules/error-logging.md` を参照してください。

## UML-R — UML 描画 / UML Rendering

### UML-R001

- **概要**: 生成された PlantUML の構文エラーで図を描画できませんでした
- **対処**: エラーカードの行番号と logs/render-failed-*.puml の該当行を確認してください。生成図（エディタ以外）でこのエラーが出る場合は Juml 側の生成不具合の可能性が高いため、ログビューアの詳細と ID を添えて報告してください。
- **Summary**: Rendering failed due to a syntax error in the generated PlantUML
- **Remedy**: Check the reported line in logs/render-failed-*.puml. For generated (non-editor) diagrams this usually indicates a Juml generator bug; report the ID together with the log details.

### UML-R002

- **概要**: レイアウトエンジンが図の描画に失敗しました
- **対処**: Graphviz が無効の場合は純 Java の Smetana レイアウトが使われ、大きな図で失敗しやすくなります。「図 → Graphviz (dot) を有効化…」で dot を有効にするか、パッケージ選択・プリセット・スコープ絞り込みで図を小さくしてください。
- **Summary**: The layout engine failed to render the diagram
- **Remedy**: With Graphviz disabled the pure-Java Smetana layout is used and large diagrams may fail. Enable Graphviz (dot) via the Diagram menu, or reduce the diagram with package selection, presets, or scope.

### UML-R003

- **概要**: メモリ不足により図の描画に失敗しました
- **対処**: 対象パッケージやスコープを絞って図を小さくするか、起動オプション -Xmx でヒープ最大量を増やして再試行してください。
- **Summary**: Rendering failed due to insufficient memory
- **Remedy**: Narrow the target package/scope to shrink the diagram, or increase the JVM heap with -Xmx and retry.

### UML-R004

- **概要**: 描画結果の処理中に予期しないエラーが発生しました
- **対処**: 再描画で解消しない場合は、ログビューアの詳細（スタックトレース）と ID を添えて報告してください。
- **Summary**: Unexpected error while handling the render result
- **Remedy**: If re-rendering does not help, report the ID with the stack trace shown in the log viewer.

### UML-R005

- **概要**: 失敗した PlantUML の保存に失敗しました
- **対処**: logs ディレクトリの空き容量と書き込み権限を確認してください。図の失敗原因自体は直前の UML-R 系エラーを参照してください。
- **Summary**: Failed to save the failing PlantUML dump
- **Remedy**: Check free space and write permission of the logs directory. See the preceding UML-R error for the render failure itself.

### UML-R006

- **概要**: PNG 描画が PlantUML のエラー画像を返しました
- **対処**: 同じ題材の SVG 描画が成功するか確認してください。SVG も失敗する場合は UML-R001 / UML-R002 の対処に従ってください。
- **Summary**: PNG rendering returned a PlantUML error image
- **Remedy**: Check whether SVG rendering of the same diagram succeeds. If SVG fails too, follow the remedies of UML-R001 / UML-R002.

### UML-R007

- **概要**: 図の描画に失敗しました（原因未分類）
- **対処**: ログビューアの詳細と logs/render-failed-*.puml を確認し、ID とあわせて報告してください。
- **Summary**: Diagram rendering failed (unclassified)
- **Remedy**: Check the log viewer details and logs/render-failed-*.puml, and report them with this ID.

### UML-R008

- **概要**: 設定された Graphviz (dot) が実行できません
- **対処**: GRAPHVIZ_DOT 設定・環境変数が実在する実行可能な dot を指しているか確認してください。「図 → Graphviz (dot) を有効化…」で再検出するか、設定を外して同梱 Smetana レイアウトに戻してください。
- **Summary**: The configured Graphviz (dot) binary cannot be executed
- **Remedy**: Check the GRAPHVIZ_DOT setting / environment variable points to an existing executable dot. Re-detect via Diagram > Enable Graphviz (dot)..., or remove the setting to fall back to the built-in Smetana layout.


## UML-E — UML エディタ / UML Editor

### UML-E001

- **概要**: 編集中の PlantUML に構文エラーがあります
- **対処**: エディタ上で赤く強調された行を修正してください。PlantUML の診断メッセージはエラーカードとログに表示されます。
- **Summary**: The PlantUML you are editing has a syntax error
- **Remedy**: Fix the line highlighted in red in the editor. The PlantUML diagnostics are shown on the error card and in the log.

### UML-E002

- **概要**: 編集中の PlantUML のレイアウトに失敗しました
- **対処**: 図が大きすぎる可能性があります。Graphviz (dot) を有効化するか、図を分割してください。
- **Summary**: Layout failed for the PlantUML you are editing
- **Remedy**: The diagram may be too large. Enable Graphviz (dot) or split the diagram.

### UML-E003

- **概要**: PlantUML ファイルの保存に失敗しました
- **対処**: 保存先の書き込み権限・空き容量・ファイルが他アプリで開かれていないかを確認してください。
- **Summary**: Failed to save the PlantUML file
- **Remedy**: Check write permission, free space, and that the file is not locked by another application.

### UML-E004

- **概要**: PlantUML ファイルを開けませんでした
- **対処**: ファイルの存在・読み取り権限・文字コード (UTF-8) を確認してください。
- **Summary**: Failed to open the PlantUML file
- **Remedy**: Check that the file exists, is readable, and is encoded in UTF-8.


## DIAG — 図生成・タブ / Diagram Service

### DIAG-001

- **概要**: 図設定の読込に失敗したため既定値で継続します
- **対処**: 動作への影響は軽微です。頻発する場合は設定ファイルの破損が疑われるため、設定をやり直して保存し直してください。
- **Summary**: Failed to load diagram settings; continuing with defaults
- **Remedy**: Impact is minor. If it repeats, the settings may be corrupted - reconfigure and save again.

### DIAG-002

- **概要**: スコープフィルタのクラス名正規表現が不正です
- **対処**: スコープダイアログの正規表現を修正してください（例: java.util..* のようにメタ文字を正しく使う）。
- **Summary**: Invalid class-name regex in the scope filter
- **Remedy**: Fix the regular expression in the scope dialog (e.g. java.util..*).

### DIAG-003

- **概要**: Doxygen の実行または解析に失敗しました
- **対処**: Doxygen がインストールされ PATH にあるか、対象ディレクトリが読めるかを確認してください。詳細はログを参照してください。
- **Summary**: Doxygen execution or parsing failed
- **Remedy**: Check that Doxygen is installed and on PATH, and that the target directory is readable. See the log for details.


## PRJ — プロジェクト読込・解析 / Project Analysis

### PRJ-001

- **概要**: プロジェクトの解析に失敗しました
- **対処**: 対象フォルダの読み取り権限を確認してください。特定ファイル起因の場合はログに個別の PRJ 系エラーが残ります。解消しない場合は ID とログを添えて報告してください。
- **Summary**: Project analysis failed
- **Remedy**: Check read permission of the target folder. File-specific causes are logged as separate PRJ errors. Report the ID with the log if it persists.

### PRJ-002

- **概要**: アーカイブ (jar/zip 等) の読込に失敗しました
- **対処**: アーカイブが破損していないか、パスワード付きでないかを確認してください。
- **Summary**: Failed to read the archive (jar/zip)
- **Remedy**: Check that the archive is not corrupted or password-protected.

### PRJ-003

- **概要**: Android.bp の走査に失敗したため Soong モジュールなしで継続します
- **対処**: AOSP 解析で Soong 情報が必要な場合のみ対処が必要です。Android.bp の文法・読み取り権限を確認してください。
- **Summary**: Android.bp scan failed; continuing without Soong modules
- **Remedy**: Only relevant for AOSP analysis. Check Android.bp syntax and read permissions.

### PRJ-004

- **概要**: ソースファイルの読込に失敗しました
- **対処**: 該当ファイルの読み取り権限と文字コードを確認してください。当該ファイルはスキップされ、解析は継続します。
- **Summary**: Failed to read a source file
- **Remedy**: Check read permission and encoding of the file. The file is skipped and analysis continues.

### PRJ-005

- **概要**: Java ソースの解析でエラーが発生しました
- **対処**: 該当ファイルがコンパイル可能か確認してください。コンパイルできるのに失敗する場合は Juml パーサの未対応構文の可能性があるため、ID・ファイル・行番号を添えて報告してください。
- **Summary**: Java source parsing error
- **Remedy**: Check whether the file compiles. If it compiles but still fails, the Juml parser may not support the construct - report the ID, file, and line.

### PRJ-006

- **概要**: AIDL ソースが文法から逸脱しています
- **対処**: 該当 AIDL ファイルの該当行を確認してください。正しい AIDL で失敗する場合は ID を添えて報告してください。
- **Summary**: AIDL source deviates from the grammar
- **Remedy**: Check the reported line in the AIDL file. If valid AIDL fails, report it with this ID.

### PRJ-007

- **概要**: Android リソース XML の解析で警告が発生しました
- **対処**: 該当 XML の該当行を確認してください。解析は継続しており、多くの場合は無視できます。
- **Summary**: Warning while parsing an Android resource XML
- **Remedy**: Check the reported line. Parsing continues; this is usually ignorable.

### PRJ-008

- **概要**: Android リソース XML の解析に失敗しました
- **対処**: XML が整形式か（ルート要素・タグの閉じ忘れ）を確認してください。該当ファイルはスキップされます。
- **Summary**: Failed to parse an Android resource XML
- **Remedy**: Check that the XML is well-formed (root element, unclosed tags). The file is skipped.

### PRJ-009

- **概要**: Gradle / Version Catalog の解析に失敗しました
- **対処**: build.gradle / libs.versions.toml の文法を確認してください。プラグイン解決に影響する場合があります。
- **Summary**: Failed to parse Gradle / Version Catalog files
- **Remedy**: Check the syntax of build.gradle / libs.versions.toml. Plugin resolution may be affected.

### PRJ-010

- **概要**: Doxygen XML の解析に失敗しました
- **対処**: Doxygen の生成 XML が壊れていないか確認し、キャッシュを削除して再実行してください。
- **Summary**: Failed to parse Doxygen XML
- **Remedy**: Check the generated Doxygen XML and re-run after clearing the cache.

### PRJ-011

- **概要**: smali ファイルの解析に失敗しました
- **対処**: apktool の出力が完全か確認してください。該当ファイルはスキップされます。
- **Summary**: Failed to parse a smali file
- **Remedy**: Check that the apktool output is complete. The file is skipped.

### PRJ-012

- **概要**: 解析インデックスの更新に失敗しました
- **対処**: インデックス DB の書き込み権限・空き容量を確認してください。解消しない場合はインデックスを再構築してください。
- **Summary**: Failed to update the analysis index
- **Remedy**: Check write permission and free space for the index DB. Rebuild the index if it persists.


## ANA — 解析パネル / Analysis Panels

### ANA-001

- **概要**: 参照検索に失敗しました
- **対処**: 対象プロジェクトの解析が完了しているか確認し、再実行してください。解消しない場合は ID とログを添えて報告してください。
- **Summary**: Reference search failed
- **Remedy**: Make sure project analysis has finished, then retry. Report the ID with the log if it persists.

### ANA-002

- **概要**: メソッド差分の解析に失敗しました
- **対処**: 比較対象の 2 ファイルが読めるか確認してください。詳細はログを参照してください。
- **Summary**: Method diff analysis failed
- **Remedy**: Check that both files are readable. See the log for details.

### ANA-003

- **概要**: 差分レポートの保存に失敗しました
- **対処**: 保存先の書き込み権限・空き容量を確認してください。
- **Summary**: Failed to save the diff report
- **Remedy**: Check write permission and free space of the destination.

### ANA-004

- **概要**: 影響範囲の解析に失敗しました
- **対処**: 解析完了後に再実行してください。解消しない場合は ID とログを添えて報告してください。
- **Summary**: Impact analysis failed
- **Remedy**: Retry after analysis completes. Report the ID with the log if it persists.

### ANA-005

- **概要**: インサイトの解析に失敗しました
- **対処**: 解析完了後に再実行してください。解消しない場合は ID とログを添えて報告してください。
- **Summary**: Insights analysis failed
- **Remedy**: Retry after analysis completes. Report the ID with the log if it persists.

### ANA-006

- **概要**: インサイトレポートの保存に失敗しました
- **対処**: 保存先の書き込み権限・空き容量を確認してください。
- **Summary**: Failed to save the insights report
- **Remedy**: Check write permission and free space of the destination.


## CACHE — 解析キャッシュ / Analysis Cache

### CACHE-001

- **概要**: 解析キャッシュの読込に失敗しました
- **対処**: キャッシュは無視され再解析されます。頻発する場合はキャッシュディレクトリを削除してください。
- **Summary**: Failed to load the analysis cache
- **Remedy**: The cache is ignored and the project is re-analyzed. Delete the cache directory if this repeats.

### CACHE-002

- **概要**: 解析キャッシュの保存に失敗しました
- **対処**: キャッシュディレクトリの書き込み権限・空き容量を確認してください。
- **Summary**: Failed to save the analysis cache
- **Remedy**: Check write permission and free space of the cache directory.

### CACHE-003

- **概要**: 旧形式キャッシュの退避に失敗しました
- **対処**: キャッシュディレクトリ内の旧形式 (legacy) ファイルを手動で削除してください。
- **Summary**: Failed to archive the legacy cache
- **Remedy**: Manually delete the legacy files in the cache directory.


## EXP — エクスポート / Export

### EXP-001

- **概要**: 図のエクスポートに失敗しました
- **対処**: 出力先の書き込み権限・空き容量・拡張子を確認してください。
- **Summary**: Diagram export failed
- **Remedy**: Check write permission, free space, and the file extension of the destination.

### EXP-002

- **概要**: クリップボードへのコピーに失敗しました
- **対処**: 他のアプリケーションがクリップボードを占有していないか確認し、再試行してください。
- **Summary**: Copy to clipboard failed
- **Remedy**: Check that no other application is holding the clipboard, then retry.

### EXP-003

- **概要**: 一覧のエクスポートに失敗しました
- **対処**: 出力先の書き込み権限・空き容量を確認してください。
- **Summary**: List export failed
- **Remedy**: Check write permission and free space of the destination.

### EXP-004

- **概要**: メンバー一覧ワークブックの出力に失敗しました
- **対処**: 出力先の書き込み権限・空き容量と、ファイルが他アプリで開かれていないかを確認してください。
- **Summary**: Member workbook export failed
- **Remedy**: Check write permission / free space and that the file is not open in another application.

### EXP-005

- **概要**: フォルダ別クラス図の一括出力に失敗しました
- **対処**: 出力先の書き込み権限を確認してください。個別の図の描画失敗は UML-R 系エラーの対処を参照してください。
- **Summary**: Per-folder class diagram export failed
- **Remedy**: Check write permission of the output directory. For individual render failures see the UML-R remedies.

### EXP-006

- **概要**: PNG のバックグラウンド出力に失敗しました
- **対処**: 出力先の書き込み権限を確認してください。描画自体の失敗は UML-R 系エラーの対処（図を小さくする / Graphviz 有効化）を参照してください。
- **Summary**: Background PNG export failed
- **Remedy**: Check write permission of the destination. For render failures see the UML-R remedies (shrink the diagram / enable Graphviz).


## NOTE — ノート / Notes

### NOTE-001

- **概要**: ノート付き PNG の出力に失敗しました
- **対処**: 出力先の書き込み権限を確認してください。図自体の描画失敗は UML-R 系エラーの対処を参照してください。
- **Summary**: PNG export with notes failed
- **Remedy**: Check write permission of the destination. For render failures see the UML-R remedies.


## CFG — 設定・永続化 / Settings & Persistence

### CFG-001

- **概要**: 設定の保存に失敗しました
- **対処**: 設定ディレクトリの書き込み権限・空き容量を確認してください。
- **Summary**: Failed to save settings
- **Remedy**: Check write permission and free space of the settings directory.

### CFG-002

- **概要**: プロジェクト履歴データベースの初期化に失敗しました
- **対処**: ベースディレクトリの書き込み権限を確認してください。履歴機能なしで継続します。
- **Summary**: Failed to initialize the project history database
- **Remedy**: Check write permission of the base directory. The app continues without history.

### CFG-003

- **概要**: プロジェクト履歴データベースの操作に失敗しました
- **対処**: 頻発する場合は DB ファイル破損の可能性があります。DB ファイルを削除すると再作成されます（履歴は失われます）。
- **Summary**: Project history database operation failed
- **Remedy**: If this repeats the DB file may be corrupted. Deleting it recreates the DB (history is lost).


## SYS — 基盤・その他 / System

### SYS-001

- **概要**: 標準エラー出力を検出しました（未分類）
- **対処**: 内容を確認し、問題がある場合は前後のログとあわせて報告してください。頻出するパターンは専用 ID への昇格候補です。
- **Summary**: Captured stderr output (unclassified)
- **Remedy**: Review the content; report it with surrounding log entries if problematic. Frequent patterns are candidates for dedicated IDs.

### SYS-002

- **概要**: 未捕捉の例外が発生しました
- **対処**: アプリの動作が不安定な場合は再起動してください。ID とスタックトレースを添えて報告してください。
- **Summary**: Uncaught exception
- **Remedy**: Restart the app if it becomes unstable. Report the ID with the stack trace.

### SYS-003

- **概要**: 外部アプリケーションの起動に失敗しました
- **対処**: OS のファイル関連付けを確認してください。表示されたパスを手動で開くことで代替できます。
- **Summary**: Failed to open with an external application
- **Remedy**: Check the OS file association. As a workaround, open the shown path manually.

### SYS-004

- **概要**: 不明なコマンドラインオプションです
- **対処**: --help でオプション一覧を確認してください。
- **Summary**: Unknown command-line option
- **Remedy**: Run with --help to list the available options.

