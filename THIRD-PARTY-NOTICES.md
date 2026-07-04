# Third-Party Notices

Juml 本体は MIT ライセンス（ルートの `LICENSE` 参照）で配布されます。
配布される fat jar (`Juml.jar`) には以下の第三者ライブラリが同梱されています。
各ライブラリは下記の各ライセンスの条件に従って再配布されており、それぞれの著作権は
各権利者に帰属します。

| ライブラリ | 用途 | ライセンス | 入手元 |
|---|---|---|---|
| FlatLaf (`com.formdev:flatlaf`) | Swing の Look & Feel（Light/Dark テーマ） | Apache License 2.0 | https://www.formdev.com/flatlaf/ |
| PlantUML (`net.sourceforge.plantuml:plantuml-mit`) | UML 図の SVG 生成 | MIT | https://plantuml.com/ |
| Apache Batik (`org.apache.xmlgraphics:batik-*`) | SVG レンダリング / Swing 描画 | Apache License 2.0 | https://xmlgraphics.apache.org/batik/ |
| JFontChooser (`com.rover12421.opensource:JFontChooser`) | フォント選択ダイアログ | Apache License 2.0 | https://github.com/dheid/fontchooser |
| ASM (`org.ow2.asm:asm`) | バイトコードからのクラスヘッダ抽出 | BSD 3-Clause | https://asm.ow2.io/ |
| SQLite JDBC (`org.xerial:sqlite-jdbc`) | 解析インデックスの永続化 | Apache License 2.0 | https://github.com/xerial/sqlite-jdbc |
| JavaParser + Symbol Solver (`com.github.javaparser:javaparser-symbol-solver-core`) | Java ソースの解析 / 型解決 | Apache License 2.0（LGPL-3.0 とのデュアルライセンスのうち Apache-2.0 を選択） | https://javaparser.org/ |
| Apache POI (`org.apache.poi:poi-ooxml`) | `.xlsx` 出力 | Apache License 2.0 | https://poi.apache.org/ |
| Eclipse JGit (`org.eclipse.jgit:org.eclipse.jgit`) | git リポジトリの閲覧（履歴 / diff / blame）※閲覧のみ | Eclipse Distribution License 1.0（BSD 3-Clause 相当） | https://www.eclipse.org/jgit/ |
| Apktool (`org.apktool:apktool-lib`) | APK の逆コンパイル（smali / AndroidManifest 抽出） | Apache License 2.0 | https://apktool.org/ |
| smali / baksmali / dexlib2 (`com.android.tools.smali:*`) | Dalvik バイトコードの逆アセンブル（Apktool が使用） | Apache License 2.0 / BSD 3-Clause | https://github.com/google/smali |
| Guava (`com.google.guava:guava`) | Apktool の推移的依存 | Apache License 2.0 | https://github.com/google/guava |
| BIZ UDPGothic (`BIZUDPGothic-Regular.ttf`) | UML 図の既定日本語フォント（見やすさ重視のユニバーサルデザイン書体）。jar 内 `fonts/` に同梱し起動時に登録する | SIL Open Font License 1.1（ライセンス全文は jar 内 `fonts/OFL.txt`） | https://github.com/googlefonts/morisawa-biz-ud-gothic |

上記の推移的依存（`batik-anim` / `batik-bridge` / `batik-gvt` / `batik-css`、
`poi` / `xmlbeans` / `commons-compress` 等、`javaparser-core`、`brut.j.*` /
`smali-util` / `smali-dexlib2` ほか）も同一のライセンス系統で配布されます。
JGit は `com.googlecode.javaewah:JavaEWAH`（Apache License 2.0）と
`org.slf4j:slf4j-api`（MIT）を推移取得し、これらも fat jar に同梱されます。

## 注記

- **PlantUML について**: PlantUML は同一機能を複数のライセンスの別アーティファクト
  （GPL 版 `plantuml`、MIT 版 `plantuml-mit`、Apache 版 `plantuml-asl` など）で配布
  しています。Juml は fat jar 全体を MIT 互換に保つため **MIT 版 (`plantuml-mit`)**
  を採用しています。MIT 版は ditaa 連携など一部機能を含みませんが、Juml が生成する
  クラス図 / シーケンス図 / パッケージ図はすべて生成可能です。
- Apache License 2.0 で配布されるライブラリの `NOTICE` ファイル相当の表記は、各
  ライブラリの配布物（jar 内 `META-INF/`）に含まれています。
- ライセンス全文は各プロジェクトの配布物および上記 URL を参照してください。

## テスト専用依存（fat jar には含まれません）

| ライブラリ | ライセンス |
|---|---|
| JUnit 4 (`junit:junit`) | Eclipse Public License 1.0 |
| Playwright (`com.microsoft.playwright:playwright`) | Apache License 2.0 |
| AssertJ-Swing (`org.assertj:assertj-swing-junit`) | Apache License 2.0 |
