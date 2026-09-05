---
description: ペルソナ・エージェントに Juml を実際に動かさせ (GUI monkey + CLI + ランダム操作)、確定バグは修正・成長バックログ (使い勝手/不足機能) は docs/persona-backlog.md に積む
allowed-tools: Read, Grep, Glob, Bash, Edit, Write, Workflow, Agent, Skill
---

`persona-explore` ワークフロー（`.claude/workflows/persona-explore.js`）を使って、
ペルソナ・エージェントが **実際にアプリを動かして** 集めた所見を「確定バグの修正」と
「成長バックログの更新」に変換してください。手順・コスト階層・停止条件は
`persona-explore` スキル（`.claude/skills/persona-explore/SKILL.md`）と ADR-0002 / ADR-0003 に従うこと。

## 引数の解釈

`$ARGUMENTS`:
- ペルソナ名（`newcomer` / `power-user` / `android-dev` / `aosp-analyst`、カンマ区切り）→ そのペルソナだけ起動
- `seed=N` / `fuzz=N` / `round=N` → ワークフローの既定値を上書き
- 空欄 → 4 ペルソナ全部、seed 42、fuzz 60 で 1 ラウンド

## 実行手順（司令塔 = このメインループ）

1. **前提**: `gradle jar compileTestJava` 済みか確認（SessionStart フックの報告、無ければ実行）。
   `docs/persona-backlog.md` の既存タイトルを `known` として渡す。
2. **Workflow 起動**: `Workflow({ scriptPath: ".claude/workflows/persona-explore.js", args: {...} })`
   （`name` 指定はセッション開始時に登録済みのワークフローにしか効かない）。args が届かない環境では
   `DEFAULTS` を実値に書き換えたインライン script で起動。ペルソナの agentType が未登録なら
   ワークフローが定義ファイル Read 方式へ自動でフォールバックする。
3. **確定バグの修正**: `confirmed` をメインループで直列に修正し、回帰テストを追加
   （GUI が重いものは `/test-write` に委譲可）。`xvfb-run -a gradle check jar` で検証。
4. **バックログ更新**: `backlog` を `docs/persona-backlog.md` に追記（既存 ID と重複させない。
   状態は `open`）。着手したものは `in-progress` / `done` に更新する。
5. **再ラウンド判定**: 確定バグが 1 件でもあれば seed を変えてもう 1 ラウンド。
   **確定バグ 0 件のラウンドが 1 回続いたら停止**（バックログは無限に増やさない）。

## 報告

CLAUDE.md のサマリー規約（英語: 日本語 + 目的）で、ラウンドごとの
確定 / 棄却 / バックログ追加件数と修正内容、ペルソナごとの総評を報告すること。
