# Juml ステアリングマップ（Claude Code の操縦設計）

このドキュメントは、Juml リポジトリで Claude Code をどう「操縦（steering）」するかを、
Anthropic の記事
[Steering Claude Code: skills, hooks, rules, subagents and more](https://claude.com/ja/blog/steering-claude-code-skills-hooks-rules-subagents-and-more)
の **コンテキスト読み込みコスト 3 層モデル** に沿って整理したものです。

> **前提（重要）**: `.claude/agents` `.claude/commands` `.claude/skills` `.claude/rules` は
> Claude Code が **自動検出** します。`~/.claude/` へ手動コピーしたり、settings.json に
> 「登録」する必要はありません。Juml ディレクトリで Claude Code を起動すれば有効になります。

---

## 3 層モデルと Juml の対応

| 層 | 読み込みタイミング | コスト | Juml の中身 |
|---|---|---|---|
| **常時** | セッション開始時に常にロード | 高 | `CLAUDE.md`（全タスク共通の規約のみ） |
| **条件付き** | 該当ファイル編集時のみロード | 中 | `.claude/rules/*.md`（path-scoped） |
| **呼び出し時** | 明示呼び出し / 自動マッチ時のみ | 低 | `.claude/skills/`・`.claude/agents/`・`.claude/commands/` |
| **決定論** | イベント発火で必ず実行（コンテキスト外） | 極低 | `.claude/hooks/` + `settings.json` の `hooks` |

---

## 「意図 → 使う仕組み」早見表

| やりたいこと | 使う仕組み | 場所 |
|---|---|---|
| リポジトリ全体に常時効く規約 | CLAUDE.md (root) | `CLAUDE.md` |
| 特定ディレクトリだけの規約・設計方針 | path-scoped Rule | `.claude/rules/*.md`（`paths:`） |
| 再利用可能な手順 / チェックリスト / リファレンス | Skill | `.claude/skills/<name>/SKILL.md` |
| 隔離コンテキストでの調査・戦略設計（結果だけ親へ返す） | Subagent | `.claude/agents/*.md` |
| 名前付きで起動する定型タスク | Slash Command | `.claude/commands/*.md` |
| 「毎回 X したら必ず Y」「絶対 X するな」 | Hook（決定論） | `.claude/hooks/` + `settings.json` |
| 反復する安全な操作のプロンプト削減 | permissions.allow | `settings.json` |

---

## 現在の構成

### 常時層 — `CLAUDE.md`
全タスク共通の **サマリー出力規約**（英語: 日本語 + 目的）と日本語優先方針のみ。
領域固有の設計指針は Rules へ分離済み。

### 条件付き層 — `.claude/rules/`
- **`gui-tab-architecture.md`** … UML GUI（`src/main/java/juml/app/uml/**` ほか）編集時。
  VS Code 風タブ中心アーキ方針 / 責務分離 / フィールド配置・テスト方針。
- **`java-parsing-pipeline.md`** … Java 解析パイプライン（`src/main/java/juml/core/formats/{java,uml}/**`）
  編集時の軽量ガードレール。深い設計は `/java-analyze` へ委譲。

### 呼び出し層 — Skills / Subagents / Slash Commands
- **Skills**: `aosp-juml-analyzer`・`aaos-juml-analyzer`（各 SKILL.md + cheatsheet 群）。
  AOSP/AAOS の解析・図化手順とリファレンス。`gui-audit`（GUI ユーザビリティ監査手順）、
  `test-audit`（テスト監査をラウンドで回して枯らす手順）、
  `verify-recording`（実装確認の様子を GIF/webm で録画する手順 + ハーネス雛形）、
  `juml-verify`（実装変更を CI と同じ合格基準で自己検証するループ手順）。
- **Subagents**: `aosp-juml-explorer`・`aaos-juml-explorer`（戦略設計・読み取り専用）、
  `java-analyst`（Java 解析パイプラインの設計相談）、
  `gui-auditor`（Swing GUI のユーザビリティ監査・読み取り専用）、
  `gui-test-auditor`（GUI **テスト** の品質・カバレッジ監査・読み取り専用）、
  `test-engineer`（テストの設計・実装・フレーキー修正・書き込み可）、
  `verify-recorder`（実装確認の録画ハーネス作成・実行・パス返却・書き込み可）。
- **GUI 監査（使い勝手）**: `gui-audit` スキル + `gui-auditor` サブエージェント。
  「GUI が使いづらい」「導線を見直したい」ときに `/gui-audit <対象>` で起動。
  対象は `src/main/java/juml/app/uml/**`。VS Code タブ中心ゴール
  （`rules/gui-tab-architecture.md`）との整合も評価軸に含む。
- **テスト監査（守られ方）**: `test-audit` スキル + `gui-test-auditor`（監査）+ `test-engineer`（実装）。
  「テストが足りない」「GUI のテストを手厚くしたい」「フレーキーを潰したい」ときに
  `/test-audit <対象>` で **ラウンドを回して枯れるまで** 監査・補強する。テストは
  `/test-write <対象>` で `test-engineer` に直接書かせることもできる。
- **実装確認の録画（動いて見える）**: `verify-recording` スキル + `verify-recorder` サブエージェント。
  「実装確認の動画を撮りたい」「動いている様子を録画して」「PR 用の動作デモを作りたい」ときに
  `/verify-recording <対象>` で起動。**GUI 操作は Xvfb + アニメ GIF（方式 A）**、**図出力/Web は
  Playwright webm（方式 B）** で録る（同梱 ffmpeg は x11grab/gif 非対応のため方式 C は不可）。
  録画は人間レビュー用の補助成果物で、回帰保証は `/test-write` で別途テスト化する。
- **自己検証ループ（変更が本当に「良い状態」か）**: `juml-verify` スキル + `/juml-verify`。
  実装変更後に compile → checkstyle → test → jar → E2E スモークを **速い順** に回し、
  「全ゲート緑 or 3 ラウンド」で停止する。合格基準は CI（`build.yml` の `check jar`）と同一。
  headless で skip されたテストの明示（緑 ≠ GUI 検証済み）を義務付ける。
- **Slash Commands**: `/juml-explore`・`/juml-quick`・`/aosp-help`・`/aaos-help`・
  `/java-analyze`・`/java-diagram`・`/java-struct`・`/java-lex`・`/gui-audit`・
  `/test-audit`・`/test-write`・`/verify-recording`・`/juml-verify`・`/release`
  （詳細は `.claude/commands/README.md`）。

### 決定論層 — `.claude/hooks/` + `settings.json`
- **SessionStart** → `hooks/session-start.sh`: Java バージョンと `build/libs` の jar の
  有無・鮮度（src より古くないか）を確認してコンテキストへ通知（jar が無ければ
  `./gradlew jar` を案内、自動ビルドはしない）。
- **PreToolUse(Bash)** → `hooks/guard-git-push.sh`: 保護ブランチ(main/master)への push と
  force push（`=` 付き `--force-with-lease=<ref>` 形式を含む）を `exit 2` でブロック。
  作業ブランチへの push は妨げない。
- **Stop** → `hooks/quality-gate.sh`: `.java` / `build.gradle` / checkstyle 設定に
  **未検証の変更を残したままターンを終えようとしたら**、compile + checkstyle
  （CI の `check` の静的部分）を実行し、失敗していれば `exit 2` で停止をブロックして
  修正を続けさせる。同一変更状態の合格はハッシュ（`.git/juml-quality-gate.pass`）で
  記憶して再実行しない。`stop_hook_active` 継続中の再失敗はブロックせず警告のみ
  （無限ループ防止＝停止基準の明確化）。テストまで含むフル検証は `/juml-verify`。

---

## ループ設計（記事「Getting started with loops」の適用）

ループ =「停止条件が満たされるまで作業サイクルを繰り返す」仕組み。Juml では
**明確な停止条件** とセットで以下を運用する:

| ループ | トリガー | 停止条件 | 実体 |
|---|---|---|---|
| 自己検証ループ | 実装変更後（turn-based） | 全ゲート緑 or 3 ラウンド | `juml-verify` スキル |
| 品質ゲート | ターン終了（Stop イベント） | ゲート緑 or 1 継続で警告降格 | `hooks/quality-gate.sh` |
| テスト監査ループ | `/test-audit` | 新規 Critical/High が連続 1–2 ラウンド 0（枯れ） | `test-audit` スキル |

原則:
- **停止条件を先に決める**（早期終了と過剰反復の両方を防ぐ）。
- **決定論で済むものはスクリプト/Hook**（推論より安く確実）。ゲート判定は gradle、
  強制は Hook、判断が要る修正だけを Claude が担う。
- **skip の透明性**: headless で skip されたテストは「検証済み」と数えない。

---

## settings.json の permissions（任意・推奨）

反復する安全な操作の確認プロンプトを減らすため、以下を `settings.json` の
`permissions.allow` に追加できます（自分の環境のポリシーに合わせて取捨選択してください）。
権限を広げる変更のため、ここではドキュメントとして提示し、適用は各自の判断に委ねます。

```json
{
  "permissions": {
    "allow": [
      "Bash(./gradlew *)",
      "Bash(java -jar *)",
      "Bash(java -version)",
      "Bash(git status)",
      "Bash(git diff *)",
      "Bash(git log *)",
      "Bash(git branch *)",
      "Bash(find *)",
      "Bash(ls *)",
      "Bash(wc *)"
    ],
    "deny": [
      "Bash(git push --force *)",
      "Bash(git push -f *)"
    ]
  }
}
```

> 個人専用の差分は `.claude/settings.local.json`（git 管理外）に書けます。
> チーム共有の設定は `.claude/settings.json` に置きます。

---

## メンテナンス指針

- **CLAUDE.md は薄く保つ**（200 行未満目安）。特定ディレクトリにしか効かない指針は Rules へ。
- **手順は Skill、調査は Subagent、禁止/必須は Hook**。CLAUDE.md に手続きを詰め込まない。
- 新しい仕組みを足したら、この早見表と各 README を更新する。
