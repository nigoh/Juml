<!--
  Juml への Pull Request ありがとうございます！
  下記の各セクションを埋めてください。該当しない項目は「N/A」と記載するか削除して構いません。
  ドラフトのうちは Draft PR として作成し、レビュー準備が整ったら Ready for review にしてください。
-->

## 概要 (What)

<!-- この PR で何を変更したかを 1〜3 行で簡潔に -->

## 目的・背景 (Why)

<!--
  なぜこの変更が必要か / どの課題を解決するか。
  関連 Issue があれば必ずリンクしてください（例: Closes #123, Refs #456）。
-->

- 関連 Issue:

## 変更の種類 (Type of change)

<!-- 該当するものに [x] を付けてください（複数可） -->

- [ ] バグ修正 (Bug fix) — 既存挙動を壊さない修正
- [ ] 新機能 (Feature) — 新しい挙動の追加
- [ ] 破壊的変更 (Breaking change) — 既存の挙動・互換性に影響する変更
- [ ] リファクタリング (Refactor) — 挙動を変えない内部改善
- [ ] ドキュメント (Docs) — README / CHANGE.md / コメント等の更新
- [ ] ビルド・CI (Build/CI) — Gradle / GitHub Actions 等の変更
- [ ] その他 (Other):

## 変更内容の詳細

<!--
  主要な変更点を箇条書きで。どのクラス / パッケージ / 図種に影響するかが分かると親切です。
  例:
  - DiagramTabPane: アクティブタブの状態同期を修正
  - SequenceDiagramRenderer: try/catch を group として描画するよう改善
-->

-

## 影響範囲 (Affected diagrams / modules)

<!-- 該当する図種・機能に [x] を付けてください（該当する場合） -->

- [ ] クラス図 / 共通クラス図
- [ ] パッケージ図
- [ ] シーケンス図
- [ ] コンポーネント図 / Manifest 図
- [ ] Gradle 依存図 / Soong 図 (Android.bp)
- [ ] UML GUI (UmlMainFrame / DiagramTabPane など)
- [ ] Java 解析エンジン (Lexer / StructureExtractor)
- [ ] ビルド / CI / 配布 (fat jar など)
- [ ] その他:

## テスト方法 (How to test)

<!--
  どのように動作確認したか。再現手順・確認したコマンド・対象の入力プロジェクトなど。
  Swing GUI を伴う場合はスクリーンショットや GIF があると理想的です。
-->

```bash
./gradlew check
```

## チェックリスト

- [ ] `./gradlew check` がローカルで通ること（Checkstyle 警告ゼロ / `maxWarnings = 0`）
- [ ] 新規の挙動・バグ修正にテストを追加した（該当する場合）
- [ ] 新規 `.java` ファイルにライセンスヘッダを付与した（該当する場合）
- [ ] 破壊的変更がある場合、README / CHANGE.md など関連ドキュメントを更新した
- [ ] UML GUI の変更は [`CLAUDE.md`](../CLAUDE.md) の「VS Code 風タブ中心」方針に沿っている
- [ ] コミットを論理単位でまとめ、不要なデバッグコード・コメントアウトを残していない

## スクリーンショット / 出力例 (任意)

<!-- UI 変更や図の出力が変わる場合は Before / After を貼ってください -->

| Before | After |
| ------ | ----- |
|        |       |

## 補足・レビュアーへの注意点 (任意)

<!-- 設計上のトレードオフ、未対応の TODO、特に見てほしい箇所など -->
