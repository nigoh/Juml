---
paths:
  - "src/main/java/**"
  - "src/test/java/**"
  - "src/main/resources/messages*.properties"
  - "docs/errors.md"
---

# エラー ID / ロギング規約（error-logging）

> Java ソースを編集するときに読み込まれる path-scoped rule です。
> エラーを記録・通知するコードを書く／変えるときは必ずこの規約に従ってください。

## 基本原則

- **ERROR / WARN のログには必ずエラー ID（`ErrorCode`）を付ける。**
  `AppLog.error(ErrorCode, source, message[, t])` / `AppLog.warn(ErrorCode, ...)` を使う。
  ID なしオーバーロードは新規コードで使わない（stderr ティー等の基盤内部専用）。
- **`ErrorListener` への通知にも ID を付ける**: `onError(ErrorCode, source, line, message)`。
  進捗・情報通知（「wrote …」「skip: …」等、エラーでないもの）だけは
  3 引数オーバーロード（= `ErrorCode.NONE`、ID なし）を使う。
- **ドメイン例外は `JumlException`（unchecked）を継承**し、コンストラクタで
  `ErrorCode` を渡す。catch 側での分類には `JumlException.codeOf(t, fallback)` を使う。
- ID は **利用者が目視転記して対処法を引くための識別子**（クローズド環境運用）。
  発生原因が違うなら ID を分け、同じ原因なら呼び出し箇所が違っても同じ ID を使う。

## ID の体系

- 形式: `領域プレフィックス + 3 桁連番`（例: `UML-R001`, `PRJ-005`）。
- ERROR / WARN の別は ID に**含めない**（ログのレベル欄で表す）。
- 領域は 10 区分:
  `UML-R`（PlantUML 描画）/ `UML-E`（UML エディタ）/ `DIAG`（図生成・タブ）/
  `PRJ`（プロジェクト読込・解析）/ `ANA`（解析パネル）/ `CACHE`（解析キャッシュ）/
  `EXP`（エクスポート）/ `NOTE`（ノート）/ `CFG`（設定・永続化）/ `SYS`（基盤・未分類）。
- 採番単位はハイブリッド: 多発領域（UML-R / UML-E）は**原因単位**で丁寧に分類、
  その他は呼び出し箇所単位でよい。エディタ起因の描画失敗は UML-R でなく
  UML-E 系へ読み替える（`RenderFailureLog.classify` 参照）。
- 発生元不明の stderr 出力は `SYS-001`。頻出パターンが見つかったら専用 ID へ昇格させる。

## 新しい ID を追加する手順

1. `juml.util.ErrorCode` に enum 定数を追加（該当領域の末尾番号 +1。**欠番の再利用禁止**）。
2. `messages.properties`（英語）と `messages_ja.properties`（日本語、`\uXXXX`
   エスケープ）に `errcode.<ID>.summary` / `errcode.<ID>.remedy` を追加する。
   remedy は「利用者がその場でできる対処」を書く（開発者向けの内部説明にしない）。
3. `gradle generateErrorDocs` を実行して `docs/errors.md` を再生成しコミットする。
4. `gradle test --tests 'juml.util.ErrorCodeCatalogTest' --tests
   'juml.devtools.ErrorCatalogDocTest'` が通ることを確認する
   （ID 重複・形式逸脱・リソース欠落・docs 乖離を検出する）。

## 変更してはいけないこと

- 既存 ID の意味の変更・削除・再利用（利用現場の共通認識と docs の履歴が壊れる）。
  廃止したい場合は残したまま「非推奨」の旨を remedy に書く。
- ログ行フォーマット `日時 [LEVEL] [ID] [thread] message` の順序変更
  （ログビューア・現場の目視照合が依存）。
- `docs/errors.md` の手動編集（自動生成のため、次回生成で消える）。

## 表示・参照系の配線（変更時の確認先）

- タブ内エラーカード: `DiagramFailureMessage`（ID 見出し + `juml-errcode:` リンク）
- リファレンス UI: `ErrorReferenceDialog`（ヘルプメニュー / パレット / ログビューアから）
- ログビューア: `LogViewerDialog`（ID 列・絞り込み・行コピー・対処法ボタン）
