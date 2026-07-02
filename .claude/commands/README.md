# Juml Claude Code Slash Commands

Juml × AOSP/AAOS 解析の統合 slash command セット。
エージェント・スキルを活用した戦略設計から、クイック図化まで。

## コマンド一覧

### 1. `/juml-explore` — 統合解析コマンド 🎯

**用途**: AOSP/AAOS の戦略設計が必要なとき

AOSP または AAOS ソースツリーについて「理解したい」「分析したい」という**ゴール**を入力。
エージェント（`aosp-juml-explorer` / `aaos-juml-explorer`）が解析ロジックを設計し、
どこを見るか、どのオプション使うか、期待される出力は何かを日本語で提案します。

```
# AOSP: Soong について知りたい
/juml-explore aosp --build

# AAOS: CarService アーキテクチャを理解したい
/juml-explore aaos --carservice

# AAOS: CarPropertyManager のフロー
/juml-explore aaos --manager property
```

**特徴**:
- ✅ エージェント自動ロード
- ✅ 複雑な解析は戦略設計で対応
- ✅ 実行コマンドも提案
- ⏱ 時間がかかる可能性 (深く考える)

---

### 2. `/juml-quick` — クイック図化 ⚡

**用途**: とにかく図を出したいとき

パスと図種を指定して、Juml コマンドラインを即座に生成。
エージェント・スキルは使わず、直接実行コマンドのみ。

```
# クラス図
/juml-quick class ~/AOSP/frameworks/base/services/core

# 全種類一括出力
/juml-quick all ~/AOSP/packages/services/Car 8g

# Gradle 依存図
/juml-quick deps ~/AOSP/packages/services/Car
```

**特徴**:
- ✅ 高速 (1 秒以内に返答)
- ✅ コマンド確認→コピペで実行可能
- ⏱ 解析ロジック設計なし

---

### 3. `/aosp-help` — AOSP リファレンス 📚

**用途**: AOSP 知識を得たい、リファレンスを引きたい

Soong / partition / HAL / sepolicy の各トピック別クイックリファレンス。
スキル (`aosp-juml-analyzer`) のチートシートから関連セクションを引用。

```
# Soong とは？
/aosp-help build

# Treble partition 分離について詳しく知りたい
/aosp-help partition "Treble の利点"

# avc denied デバッグ方法
/aosp-help sepolicy "avc denied が出たときはどうする？"
```

**特徴**:
- ✅ 即時回答 (高速)
- ✅ リファレンス形式
- ✅ スキル自動ロード
- ⏱ 実行コマンドは含まない

---

### 4. `/aaos-help` — AAOS リファレンス 📚

**用途**: AAOS アーキテクチャを学びたい、リファレンスを引きたい

CarService / VHAL / UI / Security の各トピック別クイックリファレンス。
スキル (`aaos-juml-analyzer`) のチートシートから関連セクションを引用。

```
# CarPropertyManager のアーキテクチャ
/aaos-help carservice "CarPropertyManager"

# VHAL subscribe パターン
/aaos-help vhal "subscribe で値変更受け取り"

# MultiUser ロール分離
/aaos-help security "driver vs passenger の権限"
```

**特徴**:
- ✅ 即時回答 (高速)
- ✅ AAOS 特化リファレンス
- ✅ スキル自動ロード
- ⏱ AOSP 基礎知識も必要 (→ `/aosp-help` 併用)

---

### 5. Juml 開発系コマンド（解析エンジンの設計・検証） 🛠

Juml 自体の Java 解析パイプラインを設計・デバッグ・検証するためのコマンド群。
深い設計相談は `java-analyst` サブエージェントへ委譲します。

| コマンド | 用途 |
|---|---|
| `/java-analyze` | Java 解析パイプラインの設計相談・コード分析（`java-analyst` 駆動） |
| `/java-diagram` | PlantUML 図生成ロジックの分析と出力品質の改善提案 |
| `/java-struct <file.java>` | `JavaStructureExtractor` の構造抽出結果を検証 |
| `/java-lex <file.java>` | `JavaLexer` のトークン化を字句レベルで検査 |

> 関連: 解析パイプラインを編集すると `.claude/rules/java-parsing-pipeline.md` が自動ロードされます。

### 6. `/gui-audit` — GUI ユーザビリティ監査 🔍

**用途**: Swing デスクトップ GUI（`src/main/java/juml/app/uml/**`）が「使いづらい」とき

不満や対象画面を入力すると、読み取り専用の `gui-auditor` サブエージェントが
コードを根拠に UX 上の問題を `file:line` 付きで指摘し、改善案を提案します。

```
# 漠然とした不満 → 横断監査
/gui-audit GUI が全体的に使いづらい

# 特定領域に絞る
/gui-audit ツールバー
/gui-audit プロジェクト読込中にフリーズする

# コンポーネント指定
/gui-audit src/main/java/juml/app/uml/DiagramTabPane.java
```

**特徴**:
- ✅ EDT ブロッキング・tooltip 欠落・ショートカット未設定・空状態などを重点確認
- ✅ VS Code タブ中心アーキ（`.claude/rules/gui-tab-architecture.md`）との整合も評価
- ✅ コードは変更せず、重大度付きで指摘（実装は別途）
- 📚 手順は `gui-audit` スキル、深掘りは `gui-auditor` エージェント

> 関連: GUI を編集すると `.claude/rules/gui-tab-architecture.md` が自動ロードされます。

### 7. `/test-audit` — テスト監査（枯れるまでラウンドを回す） 🧪

**用途**: 「テストが足りない」「GUI のテストを手厚くしたい」「フレーキーを潰したい」とき

読み取り専用の `gui-test-auditor` サブエージェントが GUI テストのカバレッジの穴・
フレーキー（不安定）要因・EDT 規律・フィクスチャ衛生・アサーション品質を `file:line`
付きで監査し、`test-engineer` がテストを追加 / 修正します。**監査 → 修正 → 再監査** を
**新規の重大指摘がゼロになる（枯れる）まで** 繰り返すのが特徴。

```
# GUI テスト全体を第 1 ラウンドから
/test-audit

# 領域を絞る
/test-audit タブ
/test-audit コマンドパレット
/test-audit src/main/java/juml/app/uml/DiagramTabPane.java

# フレーキー退治に focus
/test-audit flaky
```

**特徴**:
- ✅ カバレッジの穴（無防備な GUI コンポーネント）・フレーキー・EDT 規律を重点監査
- ✅ headless CI の「緑 ≠ GUI 検証済み」を前提に skip 空洞化も穴として扱う
- ✅ ラウンドを回して枯れるまで補強（loop-until-dry）
- 📚 手順は `test-audit` スキル、監査は `gui-test-auditor`、実装は `test-engineer`

### 8. `/test-write` — テストの設計・実装 ✍️

**用途**: 特定のクラス/機能/落ちているテストに対して、テストを書いてほしいとき

`test-engineer` サブエージェントが、既存作法（headless skip / EDT 包み / cleanUp /
期限付きポーリング / checkstyle 警告 0）に沿って実際にテストコードを書きます。

```
/test-write src/main/java/juml/app/uml/CommandPalette.java
/test-write タブの重複防止
/test-write flaky: UmlMainFrameTabLinkageIT
```

> 関連: GUI/テストを編集すると `.claude/rules/gui-tab-architecture.md` が自動ロードされます。

### 9. `/verify-recording` — 実装確認の録画（動いて見える） 🎬

**用途**: 実装が「実際に動いている様子」を**静止画でなく動画**で残したいとき

`verify-recorder` サブエージェントが、対象シナリオに応じて録画ハーネスを作成・実行し、
生成物のパスを返します。**GUI 操作はアニメ GIF（方式 A・Xvfb 必須）**、**図出力(HTML/SVG)や
Web は Playwright webm（方式 B・headless 可）** で録画します。

```
# GUI 操作シナリオ → 方式 A（GIF）
/verify-recording タブを 3 つ開いて重複防止される様子
/verify-recording エクスポートのダイアログ操作

# 図出力 → 方式 B（webm）
/verify-recording シーケンス図の生成

# 直近の変更から価値の高いシナリオを 1 つ録る
/verify-recording
```

**特徴**:
- ✅ GUI は Xvfb + `java.awt.Robot` で採取 → 純 Java（ImageIO）で GIF 化（外部エンコーダ不要）
- ✅ 図/Web は Playwright `setRecordVideoDir` で webm 録画（Chromium 同梱）
- ⚠ システム ffmpeg は無く、同梱 ffmpeg は x11grab/gif 非対応 → 画面録画(方式 C)は使えない
- ✅ 生成物は `build/recordings/` に集約（git 管理外）。提示は親が `SendUserFile` で実施
- 📌 録画は人間レビュー用の補助。回帰保証は `/test-write` でテスト化
- 📚 手順は `verify-recording` スキル、実作業は `verify-recorder` エージェント

### 10. `/juml-verify` — 実装変更の自己検証ループ ✅

**用途**: コード変更後に「本当に良い状態か」を CI と同じ基準で確認したいとき

compile → checkstyle → test → jar → E2E スモークを**速い順**に回し、
失敗 → 修正 → 再検証を最大 3 ラウンドで収束させます。合格基準は
CI（`build.yml` の `check jar`）と同一。headless で skip されたテストは
「検証済み」と数えず、必ず件数を報告します。

```
# 変更内容から自動でゲート範囲を判定
/juml-verify

# 速攻フィードバック（compile + checkstyle のみ）
/juml-verify quick

# フル検証（全ゲート + 可能なら xvfb-run でテスト実行）
/juml-verify full

# 領域を指定
/juml-verify src/main/java/juml/core/formats/uml
```

**特徴**:
- ✅ 「全ゲート緑 or 3 ラウンド」の明確な停止条件（早期終了と過剰反復の両方を防ぐ）
- ✅ ゲート 1–2（compile/checkstyle）は Stop hook（`quality-gate.sh`）が停止時に自動強制
- ✅ E2E スモークは Juml 自身のソースを図化する自己ホスティング方式
- 📚 手順は `juml-verify` スキル

### 11. `/release` — リリース自動化 🚀

バージョン更新・タグ付け・成果物生成などのリリース手順を実行します。

---

## 使い分けガイド

### 「○○ を理解したい」 → `/juml-explore`

```
Q: "CarService の起動シーケンスを理解したい"
→ /juml-explore aaos --carservice
↓ (エージェント考える)
A: "仮説 A: …, 仮説 B: … を検証するため、以下のステップを提案:
   1. CarService クラス図を生成 (-c で CarService.java)
   2. ライフサイクルシーケンス図 (-Q で onCreate)
   3. 期待される出力: CarLocalServices 登録順序が見える"
```

### 「とにかく図にして」 → `/juml-quick`

```
Q: "frameworks/base のクラス図"
→ /juml-quick class ~/AOSP/frameworks/base/services/core
↓ (即時返答)
A: "java -Xmx8g -jar ... -c -o class.svg ~/AOSP/... (コピペで OK)"
```

### 「仕組みを知りたい」 → `/aosp-help` or `/aaos-help`

```
Q: "Soong って何？"
→ /aosp-help build
↓ (スキル参照)
A: "[Soong は…] [Android.bp とは…] [Juml との連携は…]"
```

---

## ワークフロー例

### AAOS: CarService の Audio フロー理解

```
ユーザ: "/juml-explore aaos --carservice"
  ↓
エージェント: "CarAudioService と CarPropertyService の連携を確認するため、
  step 1: CarService クラス図 (-c)
  step 2: CarAudioManager.setVolume の呼び出しチェーン (-q)
  を提案"
  ↓
ユーザ: "コマンドコピペ + 実行"
  ↓
図が生成される
  ↓ (図から習得したら)
ユーザ: "/aaos-help carservice 'CarAudioService の仕組み'"
  ↓
スキル: "CarAudioService は…" (詳細リファレンス)
```

---

## エージェント・スキル リファレンス

| 要素 | 場所 | 用途 |
|---|---|---|
| **Agent: AOSP** | `.claude/agents/aosp-juml-explorer.md` | 汎用 AOSP 戦略設計 |
| **Agent: AAOS** | `.claude/agents/aaos-juml-explorer.md` | AAOS 戦略設計 |
| **Agent: Java** | `.claude/agents/java-analyst.md` | Juml の Java 解析パイプライン設計相談 |
| **Agent: GUI** | `.claude/agents/gui-auditor.md` | Swing GUI のユーザビリティ監査（読み取り専用） |
| **Agent: GUI Test** | `.claude/agents/gui-test-auditor.md` | GUI テストの品質・カバレッジ監査（読み取り専用） |
| **Agent: Test** | `.claude/agents/test-engineer.md` | テストの設計・実装・フレーキー修正（書き込み可） |
| **Agent: Recorder** | `.claude/agents/verify-recorder.md` | 実装確認の録画ハーネス作成・実行・パス返却（書き込み可） |
| **Skill: AOSP** | `.claude/skills/aosp-juml-analyzer/` | Soong / Partition / HAL / SELinux |
| **Skill: AAOS** | `.claude/skills/aaos-juml-analyzer/` | CarService / VHAL / UI / Security |
| **Skill: GUI** | `.claude/skills/gui-audit/` | GUI ユーザビリティ監査の手順・チェックリスト |
| **Skill: Test** | `.claude/skills/test-audit/` | テスト監査をラウンドで回して枯らす手順 |
| **Skill: Recording** | `.claude/skills/verify-recording/` | 実装確認を GIF/webm で録画する手順 + ハーネス雛形 |
| **Skill: Verify** | `.claude/skills/juml-verify/` | CI と同じ合格基準で回す自己検証ループの手順 |

> パスはプロジェクトルートからの相対です。`.claude/` 配下は Claude Code が自動検出するため、
> 手動コピーや登録は不要です（→ `.claude/STEERING.md`）。

---

## トラブルシューティング

### Q: Juml jar が見つからない

```sh
cd /home/user/Juml && ./gradlew jar
```

### Q: AOSP path が見つからない

コマンド実行時、エージェントが `~/AOSP/` を想定しています。
別の場所なら、slash command で full path 指定:

```
/juml-quick class /mnt/aosp-r/frameworks/base/services/core
```

### Q: 出力ファイル どこに生成される？

デフォルト: `/tmp/aosp-out/` または `/tmp/aaos-out/`

コマンド内で `-o` オプションで出力先変更可能。

### Q: OOM (Out of Memory)

メモリ指定を増やす:

```
/juml-quick class <path> 16g
```

---

## 出力規約

すべての応答は `/home/user/Juml/CLAUDE.md` に準拠:

```
## 変更サマリー
- **English phrase**: 日本語説明
  目的: なぜこれをしたか
```

---

**Created**: 2026-05-18  
**Last Updated**: 2026-07-02
