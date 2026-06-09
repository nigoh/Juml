Juml 2.0 — Java + Android + Gradle UML Tool
================================================

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

概要
------------------------------------------------
Juml は **Java / Android (Gradle + AndroidManifest.xml + AIDL) プロジェクトから
UML 図を生成する Swing ベース + CLI のツール**です。同梱の PlantUML (Smetana レイアウト) で
描画するため、Graphviz / PlantUML の追加インストールは不要です。

* **生成できる図**: クラス図 / パッケージ図 / シーケンス図 / アクティビティ図 / 共通クラス図 /
  コンポーネント図 / Manifest 図 / Gradle 依存図 / Soong 図 (Android.bp) など全 15 種
* **動作環境**: Java 17 以上 (JRE / JDK)。Windows / macOS / Linux で動作確認
* **GUI と CLI の両対応**: 対話的にプレビューする GUI と、図を一括書き出す CLI を同梱

> 1.7 までは PAD (Problem Analysis Diagram) ツールでしたが、2.0 で
> Java + Android + Gradle 特化の UML ツールに完全転換しました。旧 PAD / SPD 機能は廃止されています。

クイックスタート
------------------------------------------------

```sh
# GUI を起動 (引数にプロジェクトを渡すと起動時に解析)
java -jar Juml.jar ~/AndroidStudioProjects/MyApp

# CLI でクラス図を SVG 出力
java -jar Juml.jar -c -o class.svg ~/AndroidStudioProjects/MyApp

# 全成果物を一括出力
java -jar Juml.jar --all -o ./out ~/AndroidStudioProjects/MyApp

# アーキテクチャ俯瞰レポート (エントリポイント / ホットスポット /
# パッケージ循環依存 / デッドコード候補 / 推定レイヤ) + 循環図を出力
java -jar Juml.jar --insights -o ./insights ~/AndroidStudioProjects/MyApp
```

GUI の操作、CLI の全オプション、各図種の詳細、ビルド方法、既知の制約などは
下記ドキュメントにまとめています。

ドキュメント
------------------------------------------------
詳細なドキュメントは [`docs/`](docs/) に **HTML** で置いており、**GitHub Pages で <https://nigoh.github.io/Juml/> に公開されます**。

* [ドキュメントトップ](https://nigoh.github.io/Juml/) — 概要・GUI マニュアル・開発者リファレンスへの入口
* [プロジェクト概要](docs/project-summary.html) — 何ができるか・構成・ビルド・主要依存ライブラリ
* [操作マニュアル](docs/operation-manual.html) — **入手・インストール (exe / jar / ビルド) から GUI・CLI の実運用までを一気通貫**で解説（少しコーディングできる人向け）
* [GUI 操作マニュアル](docs/gui-manual.html) — 起動・ツールバー・各図種の操作・スタイル設定・ショートカット・**巨大な図が描画できないときの対処**
* [開発者リファレンス](docs/developer-reference.html) — CLI 全フラグ・図種カタログ・GUI 内部・解析エンジン・**解析対象と既知の制約**
* [Claude Code 連携](docs/claude-code.html) — `.claude/` 配下のエージェント・スキル・スラッシュコマンド (AOSP / AAOS 解析・Java 解析エンジン) のカタログと使い方
* [Claude エージェント / スキル導入手順](.claude/SETUP_CLAUDE_AGENTS_SKILLS.md) — 上記の Claude Code エージェント・スキルをユーザホーム `~/.claude/` に配置する手順

オープンソースについて
------------------------------------------------
本プロジェクトは **MIT ライセンスで公開されているオープンソースソフトウェア (OSS)** です。
ソースコードは GitHub ([nigoh/juml](https://github.com/nigoh/juml)) で公開しており、
誰でも自由に利用・改変・再配布できます。バグ報告や Pull Request も歓迎します。

セキュリティ
------------------------------------------------

### 設計上の安全対策

Juml は**ローカルプロジェクトのソースコードを読み取るだけ**のツールであり、
ネットワーク通信・外部サービスへの送信・コード実行は一切行いません。
入力となる Java / AIDL / AndroidManifest.xml / Gradle ファイルはすべてローカルディスク上にあります。

主なセキュリティ対策:

| 対象 | 対策 |
|---|---|
| XML パース (AndroidManifest / Preferences / Layout XML) | XXE (外部エンティティ参照) を全パーサで無効化。`disallow-doctype-decl` / `external-general-entities` / `external-parameter-entities` / `setXIncludeAware(false)` / `setExpandEntityReferences(false)` を設定 |
| PlantUML カスタム skinparam | `!include` / `!pragma` 等のプリプロセッサ系ディレクティブをフィルタしてからレンダラに渡す |
| DB 保存済みの相対パス解決 | `getCanonicalFile()` でプロジェクトルート外への逸脱 (パストラバーサル) を検出・拒否 |
| GUI の HTML 表示 | プロジェクト名・パスを Swing HTML に埋め込む前に `&amp;` / `&lt;` / `&gt;` をエスケープ |
| SQL | すべての動的値は `PreparedStatement` のバインドパラメータ (`?`) 経由 |
| プロセス起動 | `Runtime.exec()` / `ProcessBuilder` を Juml 本体から直接呼び出さない。Graphviz 実行は PlantUML ライブラリに委譲 |
| デシリアライズ | `ObjectInputStream` / `readObject()` を一切使用しない。設定は Properties XML、索引は SQLite で永続化 |
| ハードコード秘密情報 | API キー・パスワード・トークンをコード内に埋め込まない |

### ユーザーへの注意事項

* **信頼できないプロジェクトを開く場合**: 悪意ある XML ファイルや AIDL ファイルが含まれる可能性があります。上記の対策を施していますが、未知の脆弱性の可能性はゼロではありません。
* **Custom Skinparam**: Style Settings の「Custom Skinparam」フィールドは PlantUML の `skinparam` 命令のみを受け付けます。ファイル読み込み系のディレクティブ (`!include` 等) は自動的に除去されます。
* **依存ライブラリ**: 同梱の PlantUML / Apache Batik 等に既知 CVE が公開された場合は、最新の fat jar をビルドし直してください (`./gradlew jar`)。

### 脆弱性の報告

セキュリティ上の問題を発見した場合は、公開 Issue ではなく
**GitHub の [Security Advisories](https://github.com/nigoh/juml/security/advisories/new)**
からご報告ください。

ライセンス
------------------------------------------------
Juml は **オープンソースソフトウェア** として MIT ライセンスのもとで公開されています。

    Copyright (c) 2015-2026 naou and contributors

    Released under the MIT license (http://opensource.org/licenses/mit-license)

MIT ライセンスは [OSI (Open Source Initiative)](https://opensource.org/licenses/MIT) が
承認したオープンソースライセンスで、商用・非商用を問わず自由に利用・改変・再配布が可能です
(著作権表示とライセンス文の保持が条件)。ライセンス全文はリポジトリ直下の
[`LICENSE`](LICENSE) を参照してください。

同梱の PlantUML は MIT 版アーティファクト (`plantuml-mit`) を使用しており、配布される
fat jar 全体が MIT 互換です。fat jar に同梱される第三者ライブラリとそのライセンスの
一覧は [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) を参照してください。

リンク
------------------------------------------------
* GitHub [https://github.com/nigoh/juml](https://github.com/nigoh/juml)
* ドキュメント [https://nigoh.github.io/Juml/](https://nigoh.github.io/Juml/)
</content>
</invoke>
