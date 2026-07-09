---
description: render-sweep ワークフローで実プロジェクト群 × 図種オプション群の描画を総ざらいし、失敗があれば原因を特定して修正する
allowed-tools: Read, Grep, Glob, Bash, Edit, Write, Workflow, Agent, Skill
---

`render-sweep` ワークフロー（`.claude/workflows/render-sweep.js`）を使って、
Juml.jar のレンダリングを実プロジェクトの面で回帰検証してください。
手順の詳細は `orchestrate` スキル（`.claude/skills/orchestrate/SKILL.md`）に従うこと。

## 引数の解釈

`$ARGUMENTS`:
- ディレクトリパス（複数可）→ そのプロジェクト群を入力にする
- GitHub URL → scratchpad に shallow clone してから入力にする
  （例: `android/architecture-components-samples` はサブプロジェクトが 15 個ある良い題材）
- 空欄 → ユーザーに入力プロジェクトを確認する

## 実行手順

1. **前提**: `gradle jar` で jar が最新であることを確認（SessionStart フックの報告も参照）
2. **入力確定**: プロジェクトディレクトリ群を列挙（マルチプロジェクトはサブディレクトリ単位）
3. **Workflow 起動（インライン方式）**: `.claude/workflows/render-sweep.js` の本体を土台に、
   `projects` を実値で埋め込んだスクリプトを `Workflow({ script: ... })` で起動する
   （この環境では `Workflow({ name, args })` の args が届かないことがあるため。詳細は `orchestrate` スキル）
4. **失敗ゼロならそのまま報告**。失敗があれば:
   - 生成 PlantUML を `-o x.puml` で取り出し、失敗行 (`[From string (line N)]`) を特定
   - 原因を解析パイプライン（lexer → structure → PlantUML 生成 → レンダラ）のどの段か
     切り分けて修正（`.claude/rules/java-parsing-pipeline.md` のガードレール遵守）
   - **実レンダリング**の回帰テストを追加（文字列一致だけのテストは不可）
   - `xvfb-run -a gradle check jar` → 再スイープで失敗ゼロを確認

## 報告

CLAUDE.md のサマリー規約で、実行数 / 失敗数 / 修正内容を報告すること。
