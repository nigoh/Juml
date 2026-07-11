# Juml Claude Code Slash Commands

よく使うコマンドだけを厳選した**常設セット（5 個）**です。
特化コマンドは `.claude/commands-archive/` に退避してあり、必要になったら戻して使います。

## 常設コマンド

| コマンド | 用途 | 委譲先 |
|---|---|---|
| `/propose <課題・対象>` | ソース修正が要るとき、現行ソース基準の**修正案をアーティファクトで提示 → 選択 → 忠実に実装**する | `artifact-design` / `juml-verify` |
| `/juml-verify [quick\|full\|<path>]` | 変更を CI と同じ基準（compile→checkstyle→test→jar→E2E）で自己検証 | `juml-verify` スキル |
| `/bug-hunt <領域>` | 観点別並列でバグを洗い出し、敵対的検証で確定分だけ修正（枯れるまで） | `bug-hunt` ワークフロー / `orchestrate` |
| `/test-write <対象>` | 既存作法に沿ってテストを設計・実装（新規 / フレーキー修正 / 穴埋め） | `test-engineer` エージェント |
| `/release <version>` | バージョン更新・タグ付け・成果物生成 | — |

### `/propose` — 修正案ドリブンの変更フロー（今回追加）

「行き当たりばったりで直す」のではなく、**必ず案を並べて選んでから実装**する型。

1. 現行ソースを読む（`file:line` で根拠を取る）
2. 修正案を 2〜4 個、標準フォーマット v1 でアーティファクト化 → **ここで停止**
3. 選択を受けてから、選ばれたデザインだけを**忠実に**実装
4. `/juml-verify` 基準で検証して報告

**Golden rule**: アーティファクト先出し・選択前にソースを触らない・選ばれた案から逸脱しない。
各案には必ず「現状 (Before)」を含める。フォーマットの詳細は `propose.md` を参照。

## 退避コマンド（`.claude/commands-archive/`）

特化・低頻度のため常設から外したもの。**削除ではなく移動**なので、戻せばそのまま復活します。

```sh
# 使いたいコマンドを常設に戻す（例: gui-audit）
git mv .claude/commands-archive/gui-audit.md .claude/commands/
```

| 退避コマンド | 用途 |
|---|---|
| `/gui-audit` | Swing GUI（`src/main/java/juml/app/uml/**`）の使い勝手監査（`gui-auditor`） |
| `/test-audit` | テストスイートを枯れるまで監査（`gui-test-auditor` → `test-engineer`） |
| `/render-sweep` | 実プロジェクト群 × 図種で描画の回帰総ざらい |
| `/verify-recording` | 実装確認を GIF / webm で録画（`verify-recorder`） |
| `/java-analyze` | Java 解析パイプラインの設計相談（`java-analyst`） |
| `/java-diagram` | PlantUML 図生成ロジックの分析・改善提案 |
| `/java-lex <file>` | `JavaLexer` のトークン化を字句レベルで検査 |
| `/java-struct <file>` | `JavaStructureExtractor` の構造抽出結果を検証 |
| `/juml-explore <aosp\|aaos ...>` | AOSP/AAOS の解析戦略設計（`aosp/aaos-juml-explorer`） |
| `/juml-quick <種別> <path>` | AOSP/AAOS を直接 Juml 実行するコマンドを即生成 |
| `/aosp-help <topic>` | AOSP × Juml クイックリファレンス（`aosp-juml-analyzer`） |
| `/aaos-help <topic>` | AAOS × Juml クイックリファレンス（`aaos-juml-analyzer`） |

## エージェント・スキル

コマンドは薄い入口で、実作業はエージェント／スキル／ワークフローへ委譲します。
一覧と使い分けは `.claude/STEERING.md` を参照。`.claude/` 配下は Claude Code が
自動検出するため、手動登録は不要です（退避コマンドはフォルダ外なので検出されません）。

## 出力規約

すべての応答は `CLAUDE.md` に準拠（`英語: 日本語` ＋ 目的）。

---

**Last Updated**: 2026-07-11
