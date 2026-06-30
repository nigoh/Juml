# コントリビューションガイド

Juml への貢献を歓迎します。バグ報告・機能提案・Pull Request はどなたでも歓迎します。
本プロジェクトは MIT ライセンス（[`LICENSE`](LICENSE)）の下で公開されています。提出された
コントリビューションは同ライセンスの下で配布されることに同意したものとみなします。

## バグ報告・機能提案

- まず既存の [Issues](https://github.com/nigoh/juml/issues) に重複が無いか確認してください。
- バグ報告は Issue テンプレート（Bug report）に沿って、再現手順・期待結果・実際の結果・
  環境（OS / Java バージョン）を記載してください。
- 機能提案は Feature request テンプレートに沿って、背景（なぜ必要か）と想定する挙動を記載してください。
- セキュリティ上の問題は公開 Issue ではなく [`SECURITY.md`](SECURITY.md) の手順で報告してください。

## 開発環境

- **Java 17** 以上（ビルドターゲットは Java 17）。
- 同梱の Gradle Wrapper を使うため、ローカルに Gradle のインストールは不要です。

```bash
./gradlew build        # コンパイル + テスト + Checkstyle
./gradlew test         # ユニットテスト
./gradlew check        # テスト + Checkstyle
./gradlew jar          # build/libs/Juml.jar (単体実行可能 fat jar) を生成
```

Swing GUI テスト（AssertJ-Swing）はディスプレイが必要です。ヘッドレス環境では
`DISPLAY` 未設定時に自動 skip されますが、実行したい場合は仮想ディスプレイでラップしてください。

```bash
xvfb-run -a ./gradlew check
```

## Pull Request の手順

1. リポジトリを fork し、作業用ブランチを切ります（例: `feature/xxx`、`fix/yyy`）。
2. 変更を加え、`./gradlew check` がローカルで通ることを確認します（Checkstyle は
   `maxWarnings = 0` の厳格設定です。警告ゼロが必須です）。
3. 新規の挙動にはできる範囲でテストを追加してください。
4. 新規の `.java` ファイルにはライセンスヘッダを付与してください:

   ```java
   // SPDX-License-Identifier: MIT
   // Copyright (c) 2015-2026 naou and contributors
   ```

5. PR テンプレートに沿って、変更の概要・目的（なぜ）・テスト方法を記載して PR を作成します。

## コーディング規約

- Checkstyle 設定（`config/checkstyle/checkstyle.xml`）に従ってください。
- 既存のコードスタイル・命名・パッケージ構成に合わせてください。
- UML GUI 周りは [`CLAUDE.md`](CLAUDE.md) の「VS Code 風タブ中心」アーキテクチャ方針に沿わせてください。
