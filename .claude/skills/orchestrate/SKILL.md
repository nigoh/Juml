---
name: orchestrate
description: Juml でマルチエージェント・オーケストレーション (Workflow) を回すための手順とパターン集。「オーケストレーションして」「バグゼロにして」「徹底的に/多角的に調べて」「並列で監査して」「ワークフローを使って」という依頼で自動ロード。名前付きワークフロー (bug-hunt / render-sweep) の使い分け、発見→検証→修正→再監査のサイクル設計、停止条件、既存サブエージェントとの併用マップを提供する。
---

# Juml オーケストレーション手順（/bug-hunt・/render-sweep・Workflow）

複数エージェントを**構造化して**使うための手順集。単発の調査は既存サブエージェント
(`java-analyst` / `gui-auditor` など) で足りる。ここに来るのは:

- **網羅したい** — 領域のバグ・穴を面で洗い出す（1 コンテキストでは読み切れない）
- **確信を持ちたい** — 見つけた指摘を敵対的に検証して偽陽性を落としてから直す
- **面で回帰検証したい** — 実プロジェクト × 図種オプションの直積でレンダリングを総ざらい

## ワークフロー・テンプレート（`.claude/workflows/`）

| ファイル | 用途 | 主なパラメータ | 返り値 |
|---|---|---|---|
| `bug-hunt.js` | 対象領域を観点別レンズで並列調査 → bug 判定を敵対的検証 | target, files[], tests[], lenses[], context | `{confirmed, rest, rejectedCount}` |
| `render-sweep.js` | プロジェクト群 × 図種構成で jar 描画を総ざらい | projects[], jar, outBase, configs[] | `{failures, totalRuns}` |

### 起動方法（重要 — インライン方式を使う）

**この環境では `Workflow({ name, args })` の `args` がスクリプトの `args` グローバルに
届かないことがある**（`args.xxx は必須` エラーで即失敗する）。確実なのは、対象値を
スクリプトに**直接埋め込むインライン方式**。テンプレートファイルの本体を土台に、
`target` / `files` などを実値で書いて `Workflow({ script })` で起動する:

```
Workflow({ script: `export const meta = { name: 'my-bug-hunt', description: '...', phases: [{title:'Find'},{title:'Verify'}] }
  const FILES = ['src/main/java/juml/app/uml/PumlSourcePanel.java', /* ... */]
  const TARGET = 'UML エディタ (PlantUML 編集タブ)'
  const LENSES = [ /* 正確性 / 状態 / EDT / I・O / UX の 5 レンズ */ ]
  // 以下 .claude/workflows/bug-hunt.js の phase('Find')〜return と同じ骨格 (agent/parallel で発見→検証)
` })
```

`.claude/workflows/bug-hunt.js` / `render-sweep.js` は **完成した骨格の参照実装**。
本体（スキーマ定義・レンズ・発見→dedup→検証→return）をコピーし、先頭の `args` 依存部を
実値の定数に置き換えるだけでよい。`{ name, args }` 形式は args が届いた場合のみ動く
（テンプレート側は args が文字列で来ても parse する防御を入れてある）。

## 標準サイクル（バグゼロ要求のとき）

停止条件を**先に**決める（`STEERING.md` のループ設計と同じ原則）:

1. **Scout（インライン）**: 対象のファイル一覧・行数・入口を自分で把握（wc -l / grep）。
   `bug-hunt` の `files` を確定させる。
2. **Round N — 発見+検証**: `bug-hunt` を起動。`confirmed`（確定 bug）と `rest`（ux/test-gap）を受け取る。
3. **修正（メインループ）**: confirmed を自分で修正。テストは `/test-write`（test-engineer）に
   委譲可。checkstyle (`maxWarnings=0`) を守る。
4. **検証**: `/juml-verify` 相当（`xvfb-run -a gradle check jar`）。レンダリング系の変更なら
   `render-sweep` も回す。
5. **Round N+1 — 再監査**: 修正済みコードに `bug-hunt` を再実行。
   **停止条件: confirmed が 1 ラウンド 0 件（枯れ）**。ux/test-gap はユーザーへ報告して判断を仰ぐ。

## パターン集

- **発見 → 敵対的検証**: ファインダーの指摘は必ず独立の検証者に「反証しろ」と渡す。
  迷ったら false（偽陽性を通すコスト > 見逃すコスト。見逃しは次ラウンドで拾える）。
- **loop-until-dry**: 「N 件見つけたら終わり」ではなく「新規 0 件が続いたら終わり」。
- **レンズの多様性**: 同じプロンプト × N ではなく、正確性 / 状態 / EDT / I・O / UX と
  **観点を変えて**並列化する（冗長性では拾えない失敗モードを拾う）。
- **検証の実レンダリング原則**: PlantUML 生成系の指摘・修正は文字列一致でなく
  `PlantUmlRenderer.renderSvg` の実レンダリングで検証する（過去に文字列一致テストが
  不正構文をそのまま期待していた実例あり）。

## 既存の仕組みとの使い分け

| 状況 | 使うもの |
|---|---|
| 1 ファイル・1 機能の設計相談 | `java-analyst`（`/java-analyze`） |
| GUI の使い勝手を 1 回監査 | `gui-auditor`（`/gui-audit`） |
| テストの穴を監査 → 補強 | `/test-audit`（ラウンド制） + `/test-write` |
| 領域のバグを網羅 → 確定 | **`bug-hunt` ワークフロー**（本スキル） |
| レンダリング回帰を面で検証 | **`render-sweep` ワークフロー**（本スキル） |
| 変更後の合格判定 | `/juml-verify`（CI と同一ゲート） |

## コストと安全

- ワークフローはトークンを大量に使う。**ユーザーが明示的に求めたときだけ**起動する
  （「オーケストレーションして」「徹底的に」「ワークフローで」等）。
- ファインダーの `effort` は high、スイープ実行系は low が目安。
- 修正はワークフロー内で行わない（並列書き込みの競合を避ける）。発見・検証だけを
  並列化し、**修正と commit はメインループが直列に**行う。
