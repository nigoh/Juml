---
paths:
  - "src/main/java/juml/app/uml/**"
  - "src/test/java/juml/gui/**"
  - "src/test/java/juml/app/uml/**"
---

# UmlMainFrame アーキテクチャ方針（VS Code 風タブ中心）

> このルールは UML GUI（`src/main/java/juml/app/uml/` とその関連テスト）を
> 編集するときだけ読み込まれる path-scoped rule です。
> 全タスク共通の規約は `CLAUDE.md` を参照してください。

Juml の UML GUI は **VS Code のエディタのようなタブ中心モデル** を目標とする。
特別扱いの「Home タブ」は段階的に廃止し、すべての図を対等な **タブ（= エディタ）**
として扱う。新規実装は常にこの方針に沿わせること。

## 目標とする UI モデル

- **タブ = エディタ**: 1 つの図（題材 + 図種）が 1 タブ。動的タブは `DiagramTabPane`
  が一元管理する。同じ題材・図種のタブは重複生成せず既存タブにフォーカスする。
- **左ペイン（ProjectTreePanel）= サイドバー / ナビゲータ**: ノードの選択・ダブルクリックで
  タブを開く / フォーカスする。
- **ツールバー / Diagram メニュー = アクティブタブへの操作**: 図種切替などは
  「いまフォーカスしているタブ」に作用する（メソッドタブなら同じ `Class.method` の
  別図種をタブとして開く、など）。
- **アクティブタブ ↔ サイドバー ↔ ツールバーは常に連動**: フォーカス中タブの題材を
  ツリーでハイライトし、図種をツールバー / メニューへ反映し、ステータスバーを更新する。
- 共有 `previewPanel` に依存した単一の「Home ビュー」は **廃止対象**。残っている間も
  新規ロジックを Home 前提で書かない。

## 責務の分離原則

- `DiagramState`: 状態の保持のみ（副作用なし）。タブ固有の状態はタブ側に持たせ、
  グローバル状態は最小化する。
- `DiagramController`: 状態遷移と UI 同期（アクティブタブを起点に考える）
- `DiagramTabPane`: タブ（エディタ）のライフサイクル・描画・フォーカス通知
- `MenuBarBuilder` / `ToolBarBuilder`: UI 構築のみ（状態変更なし）
- `ProjectLoader`: SwingWorker ライフサイクルのみ
- `UmlMainFrame`: 上記の配線のみ

## フィールド配置 / テストのルール（旧「移動禁止」ルールの置き換え）

- 以前は `cache` / `previewPanel` / `status` / `currentKind` を `UmlMainFrame` から
  移動禁止としていたが、**この制約は撤廃する**。VS Code 化に向けて Home 専用の
  `previewPanel` / `currentKind` は段階的に除去・再配置してよい。
- 内部フィールドをリフレクションで覗く既存テスト（`UmlMainFrameRightClickIT` /
  `UmlMainFrameSwingTest` など）は、Home 前提から **「アクティブタブ」前提** へ移行する。
  フィールド名や構造を変える場合は当該テストも同時に更新し、壊れたまま放置しない。
- 新規テストは内部フィールドのリフレクションに依存せず、`DiagramTabPane` の
  アクティブタブ API など公開された振る舞い経由で検証することを推奨する。

## 新機能追加時のルール

- 状態の追加 → タブ固有なら当該タブ、グローバルなら `DiagramState`
- 図の切り替え / タブ操作ロジック → `DiagramController` または `DiagramTabPane`
- メニュー項目追加 → `MenuBarBuilder.Callbacks`
- 「Home タブ前提」の分岐を新たに増やさない。常にアクティブタブに対して動くように書く。
