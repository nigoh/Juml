---
name: gui-audit
description: Juml の Swing デスクトップ GUI（src/main/java/juml/app/uml/**）の使い勝手を体系的に監査する手順とチェックリスト。「GUI が使いづらい」「操作導線を見直したい」「ユーザビリティ／アクセシビリティを点検したい」という依頼があるとき自動ロード。実際の監査は gui-auditor サブエージェントへ委譲する。
---

# Juml GUI ユーザビリティ監査スキル

Juml の UML GUI（Swing デスクトップアプリ）の使い勝手を、**コードを根拠に**点検するための
手順集です。深い監査は `gui-auditor` サブエージェント（読み取り専用）に委譲します。

## いつ使うか

- ユーザーが「GUI が使いづらい」「分かりにくい」「操作が面倒」と言ったとき
- 新機能の UI を入れた後の UX レビュー
- リリース前のアクセシビリティ／キーボード操作チェック

## 監査対象マップ

| 体験領域 | 主なファイル | よくある問題 |
|---|---|---|
| 起動・初期表示 | `UmlApp.java` / `UmlMainFrame.java` / `SplashWindow.java` | 初期状態が空・何をすればいいか不明 |
| タブ操作 | `DiagramTabPane.java` / `LruTabPolicy.java` / `TabMemoryManager.java` | タブ増殖・重複・閉じにくい |
| サイドバー | `ProjectTreePanel.java` / `ProjectTreeCellRenderer.java` / `TreeIconLegendPanel.java` | アイコンの意味不明・導線が遠い |
| ツールバー | `ToolBarBuilder.java` | アイコンのみで tooltip 無し・活性制御漏れ |
| メニュー | `MenuBarBuilder.java` | ショートカット／ニーモニック未設定 |
| 長時間処理 | `ProjectLoader.java` / `LoadingGlassPane.java` / `LoadingGifs.java` | 進捗なし・キャンセル不可・フリーズ |
| ダイアログ | `*Dialog.java` / `DialogUtils.java` | 既定ボタン／Esc 未対応・検証メッセージが曖昧 |
| エラー表示 | `DiagramFailureMessage.java` | 原因も次の一手も分からないメッセージ |
| 設定の永続化 | `PreferencesDialog.java` / `ProjectSettingsPersistor.java` | サイズ／分割位置／最近の項目が保存されない |

## 北極星: VS Code 風 IDE 準拠

Juml GUI の長期ゴールは **「VS Code のような IDE の操作感」をベースラインにし、その上に
Juml 独自の図化操作・機能を積む** こと。監査・改善は常にこの二段構えで考える:

1. **まず IDE として自然か**（VS Code ユーザーの操作期待を裏切らないか）
2. **その上で Juml 独自の上乗せ**（図化に特化した便利機能）

VS Code パリティの主な観点（出発点・網羅ではない）:

| IDE 機能 | Juml で見るべき点 | 対応候補 |
|---|---|---|
| タブ操作 | ドラッグ並び替え・中クリック閉じ・`Ctrl+W`/`Ctrl+Tab` | `DiagramTabPane` |
| Quick Open (`Ctrl+P`) | クラス/メソッドへの高速ジャンプ | `EntitySearchDialog` |
| コマンドパレット (`Ctrl+Shift+P`) | 全機能の横断検索起動（現状ない可能性） | `MenuBarBuilder` |
| サイドバートグル (`Ctrl+B`) | ツリーの表示/非表示・折りたたみ | `ProjectTreePanel` |
| ステータスバー | 題材/図種/解析状態の常時表示 | `UmlMainFrame` |
| キーボード優先 | ほぼ全操作のショートカット網羅 | `MenuBarBuilder` |
| 分割ペイン | 図の並列比較 | `DiagramTabPane` / explore パネル |

> 欠落＝即欠陥ではない。「期待を裏切る箇所」を欠陥として、「あると IDE らしくなる新機能」を
> 上乗せ提案として切り分ける。

## 監査ワークフロー

### ステップ 1: 症状を画面コンポーネントに対応づける
ユーザーの「使いづらい」を具体的な画面に落とす。曖昧なら下記をヒアリングする:
- どの操作中か（起動直後／プロジェクト読込／図を開く／エクスポート…）
- 何が起きると困るか（固まる／反応がない／どこを押せばいいか分からない…）

### ステップ 2: gui-auditor へ委譲
範囲が決まったら `gui-auditor` サブエージェントに監査を依頼する
（`/gui-audit <対象>` でも可）。サブエージェントが `file:line` 付きで指摘を返す。

### ステップ 3: 優先度づけと実装方針
返ってきた指摘を重大度順に並べ、VS Code タブ中心アーキ（`.claude/rules/gui-tab-architecture.md`）
と責務分離に沿った最小変更で直す方針を立てる。

## クイック・チェックリスト（自分で軽く見るとき用）

監査の入口として `Grep` で次を確認すると早い。

### EDT ブロッキング（最優先）
```sh
# ボタン/メニューのハンドラで重処理を直接呼んでいないか
grep -rn "actionPerformed\|addActionListener" src/main/java/juml/app/uml
# 重処理はこちらに乗っているべき
grep -rn "SwingWorker\|invokeLater\|execute()" src/main/java/juml/app/uml
```
> `actionPerformed` 内で解析・図生成・ファイル I/O を同期実行していたら Critical 候補
> （UI フリーズ）。`ProjectLoader` のように `SwingWorker` 化すべき。

### tooltip / アクセシビリティ
```sh
# アイコンボタンに tooltip があるか
grep -rn "setToolTipText" src/main/java/juml/app/uml
# メニューのニーモニック／アクセラレータ
grep -rn "setMnemonic\|setAccelerator\|getRootPane().setDefaultButton" src/main/java/juml/app/uml
```
> ツールバーのアイコンボタン数に対して `setToolTipText` が極端に少なければ Medium。

### ダイアログのキーボード操作
```sh
grep -rn "setDefaultButton\|KeyStroke\|VK_ESCAPE\|WHEN_IN_FOCUSED_WINDOW" src/main/java/juml/app/uml
```
> 既存の良い実装例は `DialogKeyboardTest` / `DialogUtils` を参照（Esc クローズ等）。

### 破壊的操作の確認
```sh
grep -rn "showConfirmDialog\|JOptionPane" src/main/java/juml/app/uml
```
> 図/プロジェクトを閉じる・上書きする箇所に確認が無ければ要検討。

### 空状態の案内
```sh
grep -rn "isEmpty()\|getTabCount() == 0\|プロジェクト" src/main/java/juml/app/uml
```
> タブ 0 件・プロジェクト未読込時に「何をすればいいか」の案内があるか。

## 既存テストを根拠/回帰防止に使う

GUI の振る舞いを検証する既存テストは監査の参考になり、改善後の回帰防止にも使える:

- `src/test/java/juml/gui/UmlMainFrameSwingTest.java` — 基本構築
- `src/test/java/juml/gui/UmlMainFrameRightClickIT.java` — 右クリックメニュー導線
- `src/test/java/juml/gui/UmlMainFrameTabLinkageIT.java` — タブ↔ツリー↔ツールバー連動
- `src/test/java/juml/app/uml/DialogKeyboardTest.java` — ダイアログのキーボード操作
- `src/test/java/juml/app/uml/ToolBarBuilderTest.java` / `MenuBarBuilderTest.java`

改善を入れたら、対応するテストを更新／追加し、内部フィールドのリフレクション依存ではなく
公開された振る舞い（アクティブタブ API 等）で検証する（rule の方針に従う）。

## 出力規約（日本語サマリー）

監査結果の最終報告は CLAUDE.md の形式に従う:

```
## 変更サマリー
- <English title>: <日本語の要約>
  目的: <なぜ／何のためか>
```

例:
```
- Audited toolbar discoverability: ツールバーの発見可能性を監査
  目的: アイコンのみのボタンで機能が見つけられない問題を洗い出すため
```
