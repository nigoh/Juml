# Architecture Decision Records (ADR)

Juml の **設計上・運用上の重要な決定** を、決めた時点の背景と選択肢ごと残すための記録です。
「なぜこうなっているのか」を後から辿れるようにし、同じ議論を繰り返さないことが目的です。

## いつ書くか

- 後から覆すコストが高い決定（アーキテクチャ方針、外部ツール/モデルの使い分け、開発プロセス）
- 複数の選択肢を比較して選んだ決定（「なぜ B ではなく A か」を残す価値があるもの）
- Claude Code のステアリング（`.claude/`）の仕組みに関わる決定（エージェント構成・コスト方針など）

小さな実装判断はコミットメッセージや PR で足りる。迷ったら書く（短くてよい）。

## 書き方

1. `0000-template.md` をコピーし、次の番号（4 桁連番）+ 英語ケバブケースのファイル名にする。
   例: `0005-plantuml-renderer-fallback.md`
2. 本文は日本語でよい（タイトルは「英語: 日本語」の併記を推奨。CLAUDE.md の規約と同じ）。
3. `Status` は `Proposed` → `Accepted` → (`Deprecated` | `Superseded by ADR-xxxx`) と遷移させる。
   **既存 ADR は書き換えず**、覆すときは新しい ADR を書いて古い方を Superseded にする。
4. 下の索引に 1 行追加する。

## 索引

| ID | タイトル | Status | 日付 |
|---|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions: 設計決定を ADR として記録する | Accepted | 2026-09-05 |
| [0002](0002-persona-driven-exploratory-testing.md) | Persona-driven exploratory testing: ペルソナ・エージェントが実際にアプリを動かして育てる | Accepted | 2026-09-05 |
| [0003](0003-cost-tiered-agent-orchestration.md) | Cost-tiered agent orchestration: opus/fable を司令塔、sonnet/haiku をワーカーにする | Accepted | 2026-09-05 |
| [0004](0004-diagram-kind-dropdown-over-three-tier-menus.md) | Diagram-kind dropdown over three-tier menus: 3 段メニューを図種ドロップダウンに置き換える | Accepted | 2026-09-04 |
