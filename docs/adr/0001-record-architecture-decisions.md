# ADR-0001: Record architecture decisions: 設計決定を ADR として記録する

- **Status**: Accepted
- **Date**: 2026-09-05
- **Deciders**: リポジトリオーナー + Claude Code セッション（ペルソナ探索の導入時）
- **Related**: `docs/adr/README.md`, `.claude/STEERING.md`

## Context: 背景

Juml は GUI の再設計（3 段メニュー → ドロップダウン）、テスト監査ラウンド、マルチエージェント
ワークフローの導入など、後から「なぜそうしたか」を問われる決定を短期間に重ねてきた。
決定の理由は PR 本文とチャットログに散らばっており、リポジトリ内に一次資料が無かった。
Claude Code のステアリング（`.claude/`）も、方針の根拠が無いと次のセッションで再議論しがちである。

## Decision: 決定

- `docs/adr/` に **Architecture Decision Record** を置く（Michael Nygard 形式のライト版）。
- 1 決定 = 1 ファイル、4 桁連番 + 英語ケバブケース。本文は日本語、タイトルは「英語: 日本語」併記。
- Status は `Proposed → Accepted → Deprecated / Superseded`。既存 ADR は書き換えず、新 ADR で上書きする。
- 対象: 覆すコストが高い決定、複数案から選んだ決定、`.claude/` の仕組みに関わる決定。
- Claude Code は、これらに該当する決定をしたとき ADR を提案・作成する（`CLAUDE.md` に明記）。

## Options considered: 検討した選択肢

| 案 | 概要 | 利点 | 欠点 |
|---|---|---|---|
| A（採用） | `docs/adr/` に Markdown の ADR | 軽量・diff で追える・PR と同じレビュー導線 | 書く習慣が必要 |
| B | GitHub Discussions / Wiki | 議論しやすい | リポジトリと乖離しやすく、Claude が読めない |
| C | `CHANGE.md` に混ぜる | 追加の仕組み不要 | 変更履歴と決定理由が混ざり、後から探せない |

## Consequences: 結果・影響

- **良くなること**: 決定の背景がリポジトリ内で辿れる。Claude Code のセッションをまたいでも方針が保たれる。
- **引き換えに受け入れること**: 決定ごとに短い文書を書くコスト。
- **フォローアップ**: 過去の主要決定（ADR-0004 など）を遡って記録する。
