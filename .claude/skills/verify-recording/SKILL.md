---
name: verify-recording
description: 実装確認（動作検証）の様子を「動画」で残すための手順集。Swing GUI の操作を Xvfb 上で動かして録画（アニメ GIF）したり、PlantUML 図出力(HTML/SVG)を Playwright で webm 録画する。「実装確認の動画を撮りたい」「動いている様子を録画して」「GUI 操作を動画で残したい」「PR に貼る動作デモを作りたい」という依頼で自動ロード。実作業は verify-recorder サブエージェントへ委譲できる。
---

# Juml 実装確認・録画スキル（動作の様子を動画で残す）

実装した機能が「実際に動いているか」を**静止画ではなく動画**で確認・記録するための
手順集です。テストの合否（`/test-audit` `/test-write`）や使い勝手の評価（`/gui-audit`）
とは別レイヤーで、「**この変更で画面/出力がこう動く**」を目で見える成果物として残します。

> 監査・実装は委譲: 重い録画ハーネスの作成・実行は `verify-recorder` サブエージェント
> （書き込み可）に任せられる。録画ファイルの**ユーザーへの提示は親（メイン）が
> `SendUserFile` で行う**（サブエージェントの戻り値はパス文字列のみ）。

## いつ使うか

- 「実装確認の動画を撮りたい」「動いている様子を録画して」
- 「GUI のこの操作（タブ開閉・ダイアログ・エクスポート）を動画で残したい」
- 「PR に貼る動作デモ（before/after）を作りたい」
- レビューア向けに「ビルドが通った」だけでなく「実際こう動く」を見せたい

## この環境で実際に使える録画方式（検証済みの前提）

| 方式 | 対象 | エンコード | 出力 | 可否 |
|---|---|---|---|---|
| **A. GUI フレーム採取 → GIF** | Swing 本体の操作 | 純 Java（`java.awt.Robot` + ImageIO） | アニメ GIF | ✅ 外部依存なし（推奨） |
| **B. Playwright 録画** | PlantUML 図出力(HTML/SVG)・Web | Chromium 内蔵 | webm | ✅ Chromium 同梱済み |
| C. ffmpeg x11grab | 画面そのもの | システム ffmpeg | mp4 等 | ⚠ 不可（下記注意） |

確認済みの環境事実（このリポジトリの remote 実行環境）:

- **Java 21**・**Xvfb / xvfb-run あり**（`$DISPLAY` は未設定 = headless）。
- **Playwright Chromium** は `/opt/pw-browsers` に同梱（`playwright install` 不要）。
- **ffmpeg はシステム PATH に無い**。Playwright 同梱の ffmpeg は**画面録画用 `x11grab`
  デバイスを持たず、GIF muxer も無い**（webm 専用の内蔵ビルド）。
  → **方式 C は素では不可**。どうしても画面そのものを mp4 で録るなら
  `apt-get install ffmpeg` が必要だが、network policy でブロックされ得る。まず A/B を使う。

> 結論: **Swing GUI の操作を録るなら方式 A（GIF）**、**図のレンダリング/Web を録るなら
> 方式 B（Playwright webm）** を既定とする。

## 方式 A: Swing GUI を Xvfb 上で動かしてアニメ GIF 化（推奨）

外部エンコーダ不要で完結する自前録画。流れは「Xvfb 起動 → GUI を EDT で表示 →
`java.awt.Robot` で一定間隔フレーム採取 → AssertJ-Swing/Robot で操作 → 停止して GIF 書き出し」。

ready-to-paste なハーネス（`ScreenRecorder` ＋ JUnit 録画テストの雛形）は
`recorder-snippets.md` を参照。要点だけ:

1. **jar/クラスの用意**: 既存 GUI テスト基盤（AssertJ-Swing）を再利用するのが安全。
   独立実行なら `./gradlew jar` 後に `java -jar build/libs/juml-*.jar`。
2. **Xvfb で表示先を用意**して gradle/java をラップ:
   ```sh
   xvfb-run -a -s "-screen 0 1280x900x24" \
     ./gradlew test --tests 'juml.gui.recording.FeatureRecordingIT'
   ```
3. **録画は別スレッド**で `Robot.createScreenCapture(rect)` を ~10fps（100ms 間隔）で回し、
   `BufferedImage` を貯めて最後に `ImageIO` の GIF writer でアニメ GIF 化。
4. **操作は EDT 規律を厳守**（`GuiActionRunner` 包み）。待ちは固定 `sleep` ではなく
   既存の期限付きポーリング（`awaitLoadedTree` 等）に倣う。
5. 出力は **`build/recordings/<feature>.gif`**。`@After` で必ず録画停止＆ウィンドウ `cleanUp()`。

> GIF はサイズが膨らみやすい。**対象ウィンドウ矩形だけを採取**し、長さは 5〜15 秒・
> 10fps 程度に抑える。色数の多い画面は GIF だと粗くなる点に留意（記録用途なら十分）。

## 方式 B: PlantUML 図出力 / Web を Playwright で webm 録画

既存の Playwright スクショ IT（`src/test/java/juml/playwright/*ScreenshotIT.java`）の
延長。`screenshot()` を**録画コンテキスト**に置き換える:

```java
// 録画ディレクトリ付き context を作ると close 時に .webm が flush される
BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
        .setViewportSize(1000, 700)
        .setRecordVideoDir(Paths.get("build/recordings"))
        .setRecordVideoSize(1000, 700));
Page page = ctx.newPage();
page.navigate(html.toURI().toString());   // 生成 SVG を HTML ラップしたもの
page.waitForLoadState();
// …ズーム/スクロール/差し替えなど「動き」を作る操作…
Path video = page.video().path();         // 取得は close 前に予約
ctx.close();                              // ← ここで webm 書き出し完了
System.out.println("video: " + video);
```

- `setRecordVideoDir` を付けた context の `close()` で初めて webm が確定する（重要）。
- headless でも録画できる（Chromium の内蔵レコーダ）。`Playwright.create()` 失敗時は
  既存テスト同様 `Assume.assumeNoException` で skip。
- 「図が切り替わる」「ステップごとに差し替わる」様子を録るとレビューで効く。

詳細な雛形は `recorder-snippets.md` を参照。

## ワークフロー

### ステップ 1: 何を録るか確定
- 対象（GUI 操作か／図出力か）と「確認したい振る舞い」を 1 行で言語化する。
  例: 「タブを 3 つ開いて重複が抑止されること」「シーケンス図が onCheckedChanged を
  含んで描画されること」。曖昧なら GUI 全体ではなく**1 シナリオに絞る**。

### ステップ 2: 方式を選ぶ
- Swing 本体の操作 → **方式 A（GIF）**。図/HTML/Web のレンダリング → **方式 B（webm）**。

### ステップ 3: ハーネス作成（verify-recorder へ委譲可）
- `recorder-snippets.md` の雛形を `src/test/java/juml/gui/recording/`（A）または
  `src/test/java/juml/playwright/`（B）に置き、対象シナリオを記述。
- 既存作法を厳守: headless/Playwright skip ガード・EDT 包み・`cleanUp()`・
  期限付きポーリング・SPDX/Copyright ヘッダ・checkstyle 警告 0。

### ステップ 4: 録画実行
```sh
# 方式 A（Xvfb 必須）
xvfb-run -a -s "-screen 0 1280x900x24" ./gradlew test --tests 'juml.gui.recording.*'
# 方式 B（headless 可）
./gradlew test --tests 'juml.playwright.*Recording*'
```
- 生成物は `build/recordings/` に集約。skip された場合は**何が skip されたか**を必ず報告。

### ステップ 5: ユーザーへ提示
- 親（メイン）が `SendUserFile` で `build/recordings/<name>.{gif,webm}` を渡す。
  キャプションに「何の動作を録ったか／確認できること」を 1 行添える。
- PR に貼る場合は GitHub にアップロードした上で本文へ埋め込む（動画/GIF は
  ドラッグ&ドロップ or リリースアセット経由）。

## 落とし穴チェック

- **`$DISPLAY` 無しで方式 A を直叩きしない** → 必ず `xvfb-run` でラップ（さもなくば
  `HeadlessException`）。既存 GUI テストの headless skip と同じ理由。
- **方式 B は `ctx.close()` まで webm が出ない** → `path()` を close 前に予約し、close 後に存在確認。
- **GIF の肥大** → 矩形を絞る・10fps・短尺。`build/` は git 管理外（成果物はコミットしない）。
- **同梱 ffmpeg に x11grab/gif は無い** → 方式 C を当てにしない。A/B で代替する。
- **録画はテスト合否の代替ではない** → 「動いて見える」≠「回帰を守る」。恒久検証は
  `/test-write` で回帰テスト化する（録画は人間レビュー用の補助成果物）。

## 関連する仕組み

- **実作業役**: `verify-recorder` サブエージェント（ハーネス作成・録画実行・パス返却）
- **回帰テスト化**: `test-engineer`（`/test-write`）— 録画で確認した振る舞いをテストに昇格
- **使い勝手の評価**: `gui-auditor`（`/gui-audit`）— 「録ってみたら使いにくい」を監査へ接続
- **作法**: `.claude/rules/gui-tab-architecture.md`（EDT 規律・公開振る舞いで検証）
- **既存 Playwright 例**: `src/test/java/juml/playwright/CompoundButtonSequenceScreenshotIT.java`

## 出力規約（日本語サマリー / CLAUDE.md 準拠）

```
## 変更サマリー
- <English title>: <日本語の要約>
  目的: <なぜ／何を確認するため>

## 録画した内容
- 方式: <A: GIF / B: webm>
- シナリオ: <録画で確認できる振る舞い>
- 成果物: `build/recordings/<name>.{gif,webm}`（skip された場合はその旨）

## 次にやること（あれば）
- <英語タイトル>: <日本語の要約>（例: 録画した振る舞いを /test-write で回帰テスト化）
```
