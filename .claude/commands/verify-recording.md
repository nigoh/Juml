---
description: 実装確認（動作検証）の様子を動画で残す。GUI 操作はアニメ GIF、図出力(HTML/SVG)は Playwright webm で録画し、verify-recorder エージェントが実作業を行う
allowed-tools: Read, Grep, Glob, Bash, Agent, Edit, Write
---

以下のタスクを **verify-recorder エージェント** に依頼してください。手順とスニペットは
`.claude/skills/verify-recording/SKILL.md` と `recorder-snippets.md` に従います。

## 引数の解釈

`$ARGUMENTS` が指定されている場合:
- GUI 操作シナリオ（例: "タブを 3 つ開いて重複防止", "プロジェクト読込中の進捗", "エクスポートのダイアログ"）
  → **方式 A（Xvfb + アニメ GIF）** で録画
- 図/出力キーワード（例: "シーケンス図の生成", "クラス図のズーム"）
  → **方式 B（Playwright webm）** で録画
- ファイルパス（`src/main/java/juml/app/uml/*.java` 等）→ その機能の動作シナリオを 1 つ録る
- 空欄 → 直近の変更（git diff）から「動いて見える」と価値の高いシナリオを 1 つ提案して録る

## 依頼内容

<task>
対象: $ARGUMENTS

Juml の実装確認・録画エンジニアとして、以下を行ってください:

1. **シナリオ確定**: 「何の動作を・どう録れば確認になるか」を 1 行で定義。GUI 操作なら
   方式 A（Xvfb + GIF）、図出力/Web なら方式 B（Playwright webm）を選ぶ。
2. **作法確認**: `src/test/java/juml/gui/UmlMainFrameSwingTest.java`（EDT 包み・期限付き
   ポーリング・cleanUp）と `src/test/java/juml/playwright/*ScreenshotIT.java` を参考にする。
3. **ハーネス作成**: `recorder-snippets.md` の雛形を `juml/gui/recording/`（A）/
   `juml/playwright/`（B）に置き、対象シナリオを実装。SPDX ヘッダ・headless/Playwright
   skip ガード・EDT 包み・cleanUp・checkstyle 警告 0 を厳守。
4. **録画実行**:
   - A: `xvfb-run -a -s "-screen 0 1280x900x24" ./gradlew test --tests 'juml.gui.recording.*'`
   - B: `./gradlew test --tests 'juml.playwright.*Recording*'`
5. **生成物確認**: `build/recordings/<name>.{gif,webm}` が存在し size>0 か確認。skip された
   範囲は明記。**生成物の絶対パスを必ず返す**（親が SendUserFile で提示する）。

注意:
- 方式 A は必ず `xvfb-run` でラップ（headless 直叩きは HeadlessException）。
- 方式 B は `ctx.close()` で webm が確定する（`page.video().path()` は close 前に予約）。
- システム ffmpeg は無く同梱 ffmpeg は x11grab/gif 非対応 → 方式 C は使わない。
- 録画は人間レビュー用の補助成果物。回帰保証が要るなら `/test-write` を次アクションに。
- 生成物はコミットしない（`build/` は git 管理外）。
</task>

## 録画後の提示（親が実施）

verify-recorder が返した絶対パスを、メイン側で `SendUserFile` を使ってユーザーに渡して
ください。キャプションに「何の動作を録ったか／確認できること」を 1 行添えます。

## 出力フォーマット（CLAUDE.md 準拠）

```
## 変更サマリー
- <English title>: <日本語の要約>
  目的: <なぜ／何を確認するため>

## 録画した内容
- 方式: <A: GIF / B: webm>
- シナリオ: <録画で確認できる振る舞い>
- 成果物: `build/recordings/<name>.{gif,webm}`（skip 時はその旨）

## 次にやること（あれば）
- <録画した振る舞いを /test-write で回帰テスト化 など>
```
