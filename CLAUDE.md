# Juml — Claude 作業ガイド

このリポジトリで作業する際の応答ルールをまとめます。

## サマリー出力ルール

ターン終了時のサマリーや、まとまった変更内容を報告するときは、以下の形式で日本語化してください。

### 1. 「英語: 日本語」形式で記載する

技術用語や英語のキーワードはそのまま残し、コロンの後に日本語訳・補足を続けます。

例:

- `Refactored Gradle build script: Gradle ビルドスクリプトを整理した`
- `Added unit tests for editor: エディタのユニットテストを追加した`
- `Bumped Java target to 17: Java のターゲットを 17 に引き上げた`

### 2. 目的（Why）を簡易的に添える

「何をしたか」だけでなく、「なぜそれをしたか／何のためか」を 1 行程度で必ず添えます。

例:

```
- Switched to fat jar: 単一ファイル配布の fat jar に変更
  目的: ユーザーが依存解決なしに java -jar で実行できるようにするため
- Parse libs.versions.toml: Version Catalog (libs.versions.toml) を解釈
  目的: モダンな Android プロジェクトでもプラグイン解決が通るようにするため
```

### 3. 最終サマリーのテンプレート

```
## 変更サマリー
- <英語タイトル>: <日本語の要約>
  目的: <なぜ／何のためか>

## 次にやること（あれば）
- <英語タイトル>: <日本語の要約>
  目的: <なぜ／何のためか>
```

## その他の方針

- チャット応答内のコメントや見出しも、可能な限り日本語を優先する（コード内コメントは既存スタイルに合わせる）。
- コミットメッセージ・PR タイトル本体は英語のままでよい（CHANGE.md や README は日本語要約があると親切）。
- 専門用語・固有名詞（Gradle, PlantUML, fat jar など）は無理に訳さず原語のまま使う。

## 領域別ルール / ステアリング（読み込みコスト 3 層）

この `CLAUDE.md` は **全タスクで常時必要な規約だけ** を置く（記事「Steering Claude Code」の
常時ロード層）。特定ディレクトリにだけ効く条件付き指針は **path-scoped Rules** へ分離してあり、
該当ファイルを編集するときだけ自動で読み込まれる（中コスト層）。

- **UML GUI を編集するとき** → `.claude/rules/gui-tab-architecture.md`
  （VS Code 風タブ中心アーキ方針 / 責務分離 / テスト方針。対象: `src/main/java/juml/app/uml/**`）
- **Java 解析パイプラインを編集するとき** → `.claude/rules/java-parsing-pipeline.md`
  （lexer→structure→PlantUML の編集ガードレール。対象: `src/main/java/juml/core/formats/{java,uml}/**`）
- **エラーログ・例外を書く/変えるとき** → `.claude/rules/error-logging.md`
  （エラー ID (ErrorCode) の採番・記録規約 / docs/errors.md 自動生成。対象: `src/**/*.java`）

`.claude/` 全体の仕組み（Rules / Skills / Subagents / Slash Commands / Hooks / settings）の
使い分けマップは **`.claude/STEERING.md`** を参照。agents・commands・skills は Claude Code が
自動検出するため settings.json への登録は不要。
