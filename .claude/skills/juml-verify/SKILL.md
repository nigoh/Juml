---
name: juml-verify
description: Juml の実装変更を「良い状態」の測定可能な基準で自己検証するループ手順。コード変更後の検証、コミット前チェック、「動くことを確認して」「検証して」「ビルド通る?」という依頼で自動ロード。CI (xvfb-run ./gradlew check jar) と同じ合格基準をローカルで速い順に回し、失敗→修正→再検証を最大 3 ラウンドで収束させる。
---

# Juml 自己検証ループ（/juml-verify）

実装変更のあとに「終わった」と宣言する前へ挟む **検証ループ** の手順集。
記事 [Getting started with loops](https://claude.com/blog/getting-started-with-loops) の
turn-based loop + 検証スキルのパターンを Juml 向けに具体化したもの。
「良い状態」を測定可能な基準（下表）で定義し、**全ゲート緑 or 3 ラウンド** を停止条件とする。

## 合格基準（良い状態の定義 = CI と同一）

CI (`.github/workflows/build.yml`) は `xvfb-run -a ./gradlew --no-daemon check jar` を回す。
ローカル検証はこれを **速い順** に分割して早期に失敗を拾う:

| # | ゲート | コマンド | 合格基準 | 目安 |
|---|---|---|---|---|
| 1 | コンパイル | `./gradlew -q compileJava compileTestJava` | exit 0 | 速い |
| 2 | 静的検査 | `./gradlew -q checkstyleMain checkstyleTest` | **警告 0**（maxWarnings=0） | 速い |
| 3 | テスト | `./gradlew test`（GUI 変更時は `xvfb-run -a ./gradlew test`） | 失敗 0。**何が skip されたかを必ず報告** | 中 |
| 4 | 成果物 | `./gradlew jar` | `build/libs/Juml.jar` 生成 | 中 |
| 5 | E2E スモーク | 下記「スモーク検証」 | SVG が生成され `<svg` を含む | 速い |

> ゲート 1–2 は Stop hook（`.claude/hooks/quality-gate.sh`）が停止時に自動強制する。
> このスキルはその上位互換で、テスト・成果物・E2E まで含めたフル検証を行う。

> **環境フォールバック**: SessionStart hook が「wrapper 未展開 → システム gradle を
> 使え」と報告した環境（ネットワーク制限で wrapper の配布 zip を取得できない
> リモート環境など）では、上記の `./gradlew` をすべて `gradle` に読み替える。

## ループの回し方（停止条件つき）

```
ラウンド N (N ≤ 3):
  1. ゲートを 1 → 5 の順に実行。失敗したら以降のゲートへ進まず修正する
  2. 修正したら「失敗したゲート」から再開（前段はやり直さなくてよい）
  3. 全ゲート緑 → 停止（成功）。検証結果と skip 一覧を報告
  4. 3 ラウンド使い切っても赤 → 停止（未解決）。残る失敗・原因仮説・
     試したことを正直に報告する。黙って緑を装わない
```

- **早期終了の防止**: 「コンパイルが通った」だけで終わらない。変更の種類に応じた
  最低ライン（下記）まで到達してから報告する。
- **過剰反復の防止**: 同じ失敗に同じ修正を 2 回試さない。2 回目に同じ失敗なら
  仮説を変えるか、未解決として報告する。

## 変更の種類 → 最低限回すゲート

| 変更した場所 | 最低ライン | 追加で推奨 |
|---|---|---|
| `core/formats/**`（解析パイプライン） | 1–3 + 該当 `--tests` | ゲート 5 のスモーク。`/java-struct` `/java-lex` で段の確認 |
| `app/uml/**`（Swing GUI） | 1–3（**xvfb-run 必須**。headless の緑は GUI 未検証） | `/verify-recording` で動作を動画に残す |
| `app/cli/**` | 1–3 | ゲート 5（実際に CLI を叩く） |
| build.gradle / checkstyle 設定 | 1–4 | — |
| docs / .claude のみ | ゲート不要（該当ランタイムなし） | 参照リンク・整合の目視確認 |

個別テストの実行: `./gradlew test --tests 'juml.core.formats.uml.XxxTest'`

## スモーク検証（ゲート 5: 実際に図が出ることを確認する）

Juml 自身のソースを題材にした自己ホスティングのスモーク。解析→PlantUML→SVG の
パイプライン全体が生きていることを 1 コマンドで確認できる:

```sh
out=$(mktemp -d)
java -jar build/libs/Juml.jar -c -o "$out/class.svg" src/main/java/juml/util
test -s "$out/class.svg" && grep -q '<svg' "$out/class.svg" \
  && echo "SMOKE OK: $out/class.svg" || echo "SMOKE FAILED"
```

- 変更した機能に対応する CLI があるなら題材を差し替える
  （例: シーケンス図なら `-q Class.method`、一括なら `-A -o <dir>`）。
- GUI のみの機能で CLI から叩けない場合は、xvfb-run での GUI テスト実行
  （ゲート 3）と `/verify-recording` を代替スモークとする。

## 報告の作法（CLAUDE.md 準拠）

```
## 検証結果
- Gate 1-2 (compile/checkstyle): PASS
- Gate 3 (test): PASS — ただし headless のため Swing GUI テスト N 件 skip
- Gate 5 (smoke): PASS — build/... に SVG 生成を確認

## 変更サマリー
- <English title>: <日本語の要約>
  目的: <なぜ／何のためか>
```

**skip の透明性**が最重要: headless で GUI テストが skip された場合、
「テスト緑 = GUI 検証済み」ではないことを必ず明記する（`xvfb-run` が使える
環境なら使う。この方針は `test-audit` スキルと共通）。

## 関連する仕組み

- **Stop hook 品質ゲート**: `.claude/hooks/quality-gate.sh` — ゲート 1–2 を停止時に自動強制
- **テストの補強**: `/test-audit`（枯れるまでラウンド）/ `/test-write`（実装）
- **動作の可視化**: `/verify-recording`（GIF / webm 録画）
- **CI との対応**: `.github/workflows/build.yml`（`check jar` が本スキルの合格基準の出典）
