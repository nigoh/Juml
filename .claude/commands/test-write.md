---
description: Juml のテストを test-engineer サブエージェントに設計・実装させる（新規追加・フレーキー修正・カバレッジ補強）
allowed-tools: Read, Grep, Glob, Bash, Agent, Edit, Write
---

以下のタスクを **test-engineer サブエージェント** に依頼してください。

## 引数の解釈

`$ARGUMENTS` が指定されている場合:
- ファイルパス（`.java`）→ その main クラス/機能のテストを設計・実装
- 落ちている/不安定なテスト名 → フレーキー原因を切り分けて修正
- 機能キーワード（例: "タブの重複防止", "ダイアログの Esc 閉じ", "エクスポート異常系"）
  → その振る舞いを守るテストを追加
- 空欄 → 直近の変更（git diff）から、回帰防止に必要なテストを提案・実装

## 依頼内容

<task>
対象: $ARGUMENTS

Juml のテストエンジニアとして、以下を行ってください:

1. **対象把握**: 対象の main コードと、同じパッケージの近い既存テストを読み、命名・ヘルパー・
   待ち方・skip 規約を踏襲する。
2. **ケース設計**: ハッピーパス + 境界 + 異常系（空/null/0 件/巨大/不正）の表を立てる。
3. **実装**: 既存スタイルで実際にテストを書く。GUI テストは headless skip /
   EDT 包み（GuiActionRunner）/ `@After cleanUp()` / 期限付きポーリングを厳守。
   ファイル先頭の SPDX/Copyright ヘッダを忘れない。
4. **検証**: `./gradlew compileTestJava`（可能なら `--tests` で実行）。checkstyle 警告 0 を意識。
   headless/Playwright で skip される範囲は報告に明記する。

公開された振る舞いで検証し、内部フィールドのリフレクション依存を新規に増やさないこと
（`.claude/rules/gui-tab-architecture.md` の方針）。
</task>

## 出力フォーマット（CLAUDE.md 準拠）

```
## 変更サマリー
- <English title>: <日本語の要約>
  目的: <なぜ／何を保証するため>

## 追加・変更したテスト
- `<file>`: <カバーしたケース>

## 検証結果
- <compileTestJava / 実行 / skip された範囲>
```
