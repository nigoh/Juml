---
description: 実装変更を CI と同じ合格基準（compile / checkstyle / test / jar / E2E スモーク）で自己検証するループを回す
allowed-tools: Read, Grep, Glob, Bash
---

`juml-verify` スキル（`.claude/skills/juml-verify/SKILL.md`）の手順に従って、
現在の変更を検証してください。

## 引数の解釈

`$ARGUMENTS` が指定されている場合:
- ファイルパス / パッケージ（例: `src/main/java/juml/core/formats/uml`）
  → その領域の変更として「変更の種類 → 最低限回すゲート」表に従う
- `quick` → ゲート 1–2（compile + checkstyle）のみで即時フィードバック
- `full` → ゲート 1–5 すべて + 可能なら xvfb-run でのテスト実行
- 空欄 → `git status` / `git diff` から変更の種類を自分で判定し、
  該当する最低ラインのゲートを回す

## 進め方

対象: $ARGUMENTS

1. 変更ファイルを確認し、スキルの「変更の種類 → 最低限回すゲート」で範囲を決める
2. ゲートを速い順に実行。失敗 → 修正 → 失敗したゲートから再開（最大 3 ラウンド)
3. 全ゲート緑で停止し「検証結果」を報告。3 ラウンドで解決しなければ
   残る失敗と原因仮説を正直に報告する
4. headless で skip されたテストがあれば必ず件数と対象を明記する
