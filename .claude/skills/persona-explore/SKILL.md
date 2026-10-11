---
name: persona-explore
description: ペルソナ・エージェント (初見ユーザー / パワーユーザー / Android 開発者 / AOSP 解析者) に Juml を実際に動かさせて、バグ・使い勝手・不足機能を集め、アプリの成長バックログに変換する手順集。「ペルソナで触って」「ランダムテスト」「使い勝手や不足機能を洗い出して」「アプリを育てたい/熟成させたい」で自動ロード。コスト階層 (opus/fable=司令塔, sonnet/haiku=ワーカー) と停止条件、探索ハーネス GuiMonkey の使い方を提供する。
---

# Juml ペルソナ探索スキル（実際に動かして育てる）

**決定の記録**: ADR-0002（ペルソナ駆動の探索テスト）/ ADR-0003（コスト階層オーケストレーション）。
ここは「どう回すか」の手順集。方針の理由は ADR を読む。

## いつ使うか

- 「もっと実際に動かしてバグを見つけたい」「操作性・機能不足を洗い出したい」
- 「ランダムテスト（monkey / fuzz）をしたい」
- 「アプリを成長・熟成させる仕組みで回したい」（1 回の監査ではなく **継続する入力源** が欲しい）

単一機能のバグ網羅は `/bug-hunt`、テストの穴は `/test-audit`。ここは **実操作ベースで
「人が困ること」を拾う** のが目的で、成果物は (a) 確定バグの修正 と (b) `docs/persona-backlog.md`。

## 登場人物とコスト階層（ADR-0003）

| 層 | 誰 | モデル | 何をする |
|---|---|---|---|
| 決定論 | `scripts/run-monkey.sh` + `GuiMonkey` | LLM なし | GUI を機械的に叩き JSON + PNG を吐く。最も安い |
| ワーカー | `persona-newcomer` / `persona-power-user` | **haiku** | ハーネス実行・レポート読解・スクショ観察・CLI 試行・所見の構造化 |
| ワーカー | `persona-android-dev` / `persona-aosp-analyst` | **sonnet** | 同上 + ドメイン知識で「図が正しいか」を判断 |
| 検証 / 集約 | ワークフロー内の verify / triage | **sonnet** (effort high / medium) | bug の反証、バックログ項目への集約 |
| 司令塔 | メインループ | **opus / fable** | 計画・既知除外・修正・テスト・commit・バックログ更新・ADR |

原則: **司令塔は生ログを読まない**（ワーカーが要約した構造化所見だけを読む）。
**修正と commit は司令塔が直列に**（並列書き込み禁止）。

## 探索ハーネス GuiMonkey（`src/test/java/juml/app/uml/GuiMonkey*.java`）

JUnit テストではない探索用 main。`gradle compileTestJava` でコンパイルされ、Xvfb 上で実行する。

```sh
# 一覧
java -cp build/libs/Juml.jar:build/classes/java/test juml.app.uml.GuiMonkey --list
# 実行 (スクリプト経由が楽: HOME 隔離 / タイムアウト / 要約 / .juml 後始末)
.claude/skills/persona-explore/scripts/run-monkey.sh <runName> <projectDir> \
    [--alt <dir>] [--scenarios S2,S3,S21] [--seed 7] [--fuzz 80] [--persona newcomer]
# 出力: $JUML_MONKEY_OUT(/tmp/juml-monkey)/<runName>/{report.json,stdout.log,stderr.log,shots/*.png}
```

| シナリオ | 内容 |
|---|---|
| S0 / S12 | 起動 / 終了（常時。ウィンドウ・スレッドのリーク検査） |
| S2, S9, S10, S11 | 図種ドロップダウン一巡 / 高速切替 / 全閉じ後 / 別ウィンドウ |
| S3, S18 | ツリー巡回（選択・ダブルクリック） / ツリーのキー操作 |
| S1, S5, S6 | メニュー全項目 / パレット全コマンド / ショートカット打鍵 |
| S13, S14, S15, S16 | プレースホルダ / 無効図種 / エディタタブ / エディタ打鍵 |
| S17 | プレビュー上のランダムなマウス操作 |
| S20 | Git ペイン |
| S8, S19, S7 | LaF・テーマ / ロード重ね / プロジェクト切替 |
| **S21** | **シード付きランダム操作（`--fuzz N`）**。同じ seed で同じ操作列 |

`report.json` の `findings[].kind`: `exception` / `uncaught@*` / `stderr` / `invariant` /
`edt-stall` / `leak-window` / `leak-thread` / `applog-ERROR|WARN` / `dialog`（自動で閉じた）/
`harness`（ハーネス側の限界）。`shot` があれば所見時のスクリーンショット名。

## 標準サイクル（`/persona-explore`）

停止条件を **先に** 決める（STEERING.md のループ設計）:

1. **準備**: `gradle jar compileTestJava`。`docs/persona-backlog.md` の既存タイトルを `known` に。
2. **Explore**（並列・ワーカー）: ペルソナごとにハーネス + CLI を実行 → 構造化所見。
   起動は `Workflow({ scriptPath: ".claude/workflows/persona-explore.js", args })`（`name` はセッション
   開始時に登録済みのものだけ）。agentType 未登録時は定義ファイル Read 方式へ自動フォールバック。
3. **Verify**（並列・sonnet）: `bug` を反証。迷ったら false。
4. **Triage**（sonnet 1 体）: `usability` / `missing-feature` をバックログ項目（受け入れ条件・工数付き）に集約。
5. **修正**（司令塔・直列）: `confirmed` を修正 + 回帰テスト → `xvfb-run -a gradle check jar`。
6. **バックログ更新**（司令塔）: `docs/persona-backlog.md` に追記。着手済みは状態を更新。
7. **停止判定**: 確定バグ 0 件のラウンドが 1 回続いたら停止。バックログは司令塔が
   「次にやること」として提示し、ユーザーが取捨選択する（自動では実装しない）。

## ペルソナを増やす

1. `.claude/agents/persona-<name>.md` を作る（frontmatter に `model: haiku|sonnet`、`tools: Read, Grep, Glob, Bash`）。
   本文は「誰か / 何を重視するか / 手順（ハーネスのシナリオと CLI の順）/ 出力ルール」。
2. `.claude/workflows/persona-explore.js` の `CATALOG` に project / alt / scenarios / cli / model を追加。
3. 必要なら GuiMonkey にシナリオを足す（`GuiMonkey.catalog()` と `steps()` の両方に登録）。

## 出力規約（CLAUDE.md 準拠）

```
## 変更サマリー
- <English title>: <日本語の要約>
  目的: <なぜ>

## ラウンド経過
- ラウンド <N> (seed <S>): 所見 <raw> → 重複/既知除外 <n> → bug 確定 <c> / 棄却 <r> / バックログ +<b>
  - <persona>: <総評 1 行>

## 次にやること（バックログ上位）
- <English title>: <日本語の要約>
  目的: <なぜ>
```
