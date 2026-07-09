---
description: 対象領域のバグを bug-hunt ワークフロー (観点別並列発見→敵対的検証) で洗い出し、確定バグを修正して枯れるまでサイクルを回す
allowed-tools: Read, Grep, Glob, Bash, Edit, Write, Workflow, Agent, Skill
---

`bug-hunt` ワークフロー（`.claude/workflows/bug-hunt.js`）を使って、対象領域のバグを
「発見 → 敵対的検証 → 修正 → 再監査」のサイクルで枯れるまで潰してください。
手順の詳細は `orchestrate` スキル（`.claude/skills/orchestrate/SKILL.md`）に従うこと。

## 引数の解釈

`$ARGUMENTS`:
- ディレクトリ/ファイルパス → その配下・そのファイル群を対象にする
- 機能キーワード（例: "UML エディタ", "検索バー", "エクスポート"）→ 関連ソースを
  Grep/Glob で特定してから対象にする
- 空欄 → ユーザーに対象を確認する（全リポジトリを 1 回で回さない）

## 実行手順

1. **Scout**: 対象ファイルを確定（`files[]` と関連 `tests[]` を列挙、行数を把握）
2. **Workflow 起動（インライン方式）**: `.claude/workflows/bug-hunt.js` の本体を土台に、
   `target` / `files` / `lenses` を実値で埋め込んだスクリプトを `Workflow({ script: ... })` で起動する。
   ※ この環境では `Workflow({ name, args })` の args がスクリプトへ届かず即失敗することがあるため、
   **インライン script を主経路にする**（詳細は `orchestrate` スキル）。
3. **修正**: 返ってきた `confirmed` をメインループで直列に修正。回帰テストを追加
   （GUI が重いものは `/test-write` に委譲可）
4. **検証**: `xvfb-run -a gradle check jar`（/juml-verify と同一ゲート）
5. **再監査**: 修正後に再度 bug-hunt を実行。**confirmed が 0 件になったら停止**
6. `rest`（ux / test-gap）は修正せず、最終サマリーでユーザーに報告して判断を仰ぐ

## 報告

CLAUDE.md のサマリー規約（英語: 日本語 + 目的）で、ラウンドごとの
確定/棄却件数と修正内容を報告すること。
