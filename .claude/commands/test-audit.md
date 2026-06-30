---
description: Juml のテストスイート（特に Swing GUI テスト）を gui-test-auditor で監査し、ラウンドを回して枯れるまで穴を埋める
allowed-tools: Read, Grep, Glob, Bash, Agent, Edit, Write
---

Juml のテスト監査を **ラウンドを回して「枯れる」まで** 実施してください。
監査は **gui-test-auditor**、テスト実装は **test-engineer** サブエージェントに委譲します。

## 引数の解釈

`$ARGUMENTS` が指定されている場合:
- 画面/機能キーワード（例: "タブ", "ダイアログ", "ツールバー", "コマンドパレット"）
  → その領域のテストに絞って監査
- ファイルパス（`src/main/java/juml/app/uml/*.java`）→ そのコンポーネントのテストカバレッジを監査
- "flaky" / "フレーキー" → フレーキー（不安定）リスク退治に focus
- 空欄 → GUI テスト全体を対象に第 1 ラウンドから回す

## 進め方（loop-until-dry）

<task>
対象: $ARGUMENTS

1. **第 1 ラウンド監査**: gui-test-auditor に、対象の GUI テストのカバレッジ・フレーキー・
   EDT 規律・フィクスチャ衛生・アサーション品質を `file:line` 付きで監査させる。
2. **修正**: 返ってきた Critical/High を中心に test-engineer でテストを追加 / フレーキーを解消。
   既存作法（headless skip / EDT 包み / cleanUp / 期限付きポーリング / checkstyle 警告 0）を厳守。
3. **検証**: `./gradlew compileTestJava`（可能なら `--tests` で実行）。headless/Playwright で
   skip される範囲は明記する。
4. **再監査**: 「既出指摘リスト」を渡して重複を除外し、次ラウンドを回す。
5. **枯れ判定**: 連続ラウンドで新規 Critical/High が 0 になったら停止。残った Low は
   一覧化して「次にやること」へ送る。

各ラウンドの新規指摘・対応・検証結果を簡潔に記録し、最後に総括する。
</task>

## 出力フォーマット（CLAUDE.md 準拠）

```
## 変更サマリー
- <English title>: <日本語の要約>
  目的: <なぜ／何を保証するため>

## ラウンド経過
- ラウンド 1: 新規 <件数>（C/H/M/L） → 対応 <概要> / 検証 <結果>
- ラウンド 2: ...
- 枯れ: ラウンド <N> で新規 Critical/High = 0

## 次にやること（残 Low）
- <英語タイトル>: <日本語の要約>
```
