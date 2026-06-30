---
name: test-audit
description: Juml のテストスイート（特に Swing GUI テスト）を「枯れるまでラウンドを回して」体系的に監査・補強する手順集。「テストが足りない」「GUI のテストを手厚くしたい」「フレーキーを潰したい」「カバレッジの穴を洗い出したい」という依頼で自動ロード。監査は gui-test-auditor、実装は test-engineer サブエージェントに委譲する。
---

# Juml テスト監査スキル（ラウンドを回して枯らす）

Juml のテスト、とりわけ **Swing GUI テスト** を、コードを根拠に体系的に監査し、
穴を埋めるための手順集です。深い監査は `gui-test-auditor`（読み取り専用）、
テスト実装は `test-engineer` サブエージェントに委譲します。

## いつ使うか

- 「GUI のテストを手厚くしたい」「テストの監査役がほしい」
- 「フレーキー（不安定）なテストを潰したい」「CI が緑なのに GUI が壊れる」
- リリース前のテストカバレッジ点検 / 回帰スイートの穴探し

## 中心思想: ラウンドを回して「枯れる」まで

1 回の監査では穴は出尽くさない。**監査 → 修正 → 再監査** を繰り返し、
**新規の重大指摘がゼロになるまで（＝枯れるまで）** 回す。これが本スキルの核。

```
ラウンド N:
  1. gui-test-auditor に監査を依頼（既出指摘リストを渡して重複を除外）
  2. 返ってきた指摘を重大度順に確定
  3. Critical/High を中心に test-engineer で修正（テスト追加・フレーキー解消）
  4. ./gradlew compileTestJava（+ 可能なら --tests で実行）で検証
  5. 「既出リスト」に今回の指摘を追記
  6. 監査が「枯れた(yes)」を返す or 新規 Critical/High が 0 → 停止
     そうでなければ N+1 へ
```

> **枯れの判定**: 連続 1〜2 ラウンドで新規 Critical/High がゼロになったら停止してよい。
> 残った Low は一覧化して「次にやること」へ送る（無限に磨かない）。

## テストスタック（前提）

| 種別 | フレームワーク | 場所 |
|---|---|---|
| 単体/統合 | JUnit 4.13.2 | `src/test/java/juml/**` |
| Swing GUI | AssertJ-Swing 3.17.1 | `juml/gui/**`, `juml/app/uml/**` |
| SVG スナップショット | Playwright 1.49.0 | `juml/playwright/**` |
| 静的検査 | Checkstyle (maxWarnings=0) | テストも警告 0 必須 |

- `./gradlew test` … headless では Swing/Robot/Playwright は **自動 skip**。
- `./gradlew compileTestJava` … テストのコンパイルのみ（書いた直後の最低検証）。
- `./gradlew test --tests 'juml.app.uml.LruTabPolicyTest'` … 個別実行。

> **重要な落とし穴**: headless CI は GUI テストを skip するため「緑 ≠ GUI 検証済み」。
> 監査ではこの skip による空洞化も穴として扱う。

## 監査の柱（gui-test-auditor の観点の要約）

1. **カバレッジの穴** … 無防備な GUI コンポーネント、未検証の状態遷移、欠けた異常系。
2. **フレーキー** … 固定 `Thread.sleep` 待ち、実行順/共有状態リーク、時刻/乱数/ロケール依存。
3. **EDT 規律** … Swing 生成・取得が `GuiActionRunner` で包まれているか。
4. **Robot/フィクスチャ衛生** … `@After cleanUp()`、headless ガードの網羅。
5. **アサーションの質** … 「例外が出ないだけ」の空振り、失敗メッセージ無し。
6. **構造・命名** … 1 テスト 1 関心、説明的な名前。
7. **skip の透明性** … 常時 skip で空洞化していないか。

## クイック・チェック（自分で軽く実態把握するとき）

```sh
# フレーキー候補: 非同期完了を固定 sleep で待っていないか
grep -rn "Thread.sleep" src/test/java/juml/gui src/test/java/juml/app/uml

# EDT 規律: Swing 生成/取得が GuiActionRunner で包まれているか
grep -rn "GuiActionRunner\|invokeAndWait" src/test/java/juml

# headless ガードの網羅（Robot を使うのに assumeFalse が無いテストは危険）
grep -rln "FrameFixture\|Robot" src/test/java/juml
grep -rln "assumeFalse\|isHeadless" src/test/java/juml

# フィクスチャ衛生: FrameFixture を作って cleanUp していないテスト
grep -rln "new FrameFixture" src/test/java/juml
grep -rln "cleanUp()" src/test/java/juml

# 無防備なコンポーネント探し: main にあってテスト名に出てこないクラス
ls src/main/java/juml/app/uml/*.java
ls src/test/java/juml/app/uml/*.java
```

## ワークフロー詳細

### ステップ 1: 範囲確定
ユーザーの関心（GUI 全体 / 特定コンポーネント / フレーキー退治）を確認。
漠然としていれば GUI 全体を対象に第 1 ラウンドを回す。

### ステップ 2: gui-test-auditor へ委譲（監査）
`/test-audit <対象>` でも可。**既出指摘リスト**（前ラウンドまでの指摘）を渡して
重複を抑える。エージェントは `file:line` 付きの新規指摘＋「枯れたか」の判定を返す。

### ステップ 3: test-engineer へ委譲（修正）
Critical/High を優先し、`test-engineer` にテスト追加・フレーキー解消を依頼。
既存作法（headless skip / EDT / cleanUp / 期限付きポーリング / checkstyle 警告 0）を厳守させる。

### ステップ 4: 検証
`./gradlew compileTestJava` で最低コンパイル、headless で動くものは `--tests` で実行。
GUI/Playwright が skip される場合は「何が skip されたか」を報告に明記する。

### ステップ 5: ラウンド継続判定
新規 Critical/High が残るなら次ラウンドへ。枯れたら Low をまとめて「次にやること」へ。

## 既存テストを作法の手本にする

新規テストの書き方は既存の良い実装に倣う:

- `src/test/java/juml/gui/UmlMainFrameSwingTest.java` — headless skip + 期限付きポーリング（`awaitLoadedTree`）+ EDT 包み + cleanUp の手本
- `src/test/java/juml/gui/UmlMainFrameTabLinkageIT.java` — タブ↔ツリー↔ツールバー連動
- `src/test/java/juml/gui/UmlMainFrameRightClickIT.java` — 右クリック導線
- `src/test/java/juml/app/uml/DialogKeyboardTest.java` — ダイアログのキーボード操作
- `src/test/java/juml/app/uml/{ToolBarBuilder,MenuBarBuilder,LruTabPolicy}Test.java` — 単体の手本

## 関連する仕組み

- **監査役**: `gui-test-auditor` サブエージェント（読み取り専用）
- **実装役**: `test-engineer` サブエージェント（テストを実際に書く）
- **UX 監査**: `gui-auditor`（使い勝手の監査。テスト監査とは別だが「重要 UX に
  テストが無い」観点で接続する）
- **アーキ方針**: `.claude/rules/gui-tab-architecture.md`（リフレクション依存より
  公開振る舞いで検証する方針）

## 出力規約（日本語サマリー / CLAUDE.md 準拠）

```
## 変更サマリー
- <English title>: <日本語の要約>
  目的: <なぜ／何を保証するため>

## ラウンド経過
- ラウンド <N>: 新規指摘 <件数>（Critical/High/Medium/Low） → 対応 <概要>

## 次にやること（残 Low / 別ラウンド）
- <英語タイトル>: <日本語の要約>
```
