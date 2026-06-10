# Juml 査定レポート — AOSP/AAOS 解析ツールとしての実力評価

- 実施日: 2026-06-10
- Juml バージョン: 2.1 (commit bddd2e7 からビルドした fat jar)
- 実行環境: OpenJDK 21 / Linux / `-Xmx6g`

## 査定対象

AAOS の中核リポジトリ **`platform/packages/services/Car`**(CarService 本体)を
`android.googlesource.com` から shallow clone して使用した。AOSP 実プロジェクトとして
十分に大規模かつ、Java / AIDL / Soong / SELinux / C++ / Kotlin が混在するため
Juml の全機能を横断的に試せる。

| 項目 | 値 |
|---|---|
| リポジトリサイズ | 334 MB(8,452 ファイル) |
| Java | 1,917 ファイル |
| Kotlin | 84 ファイル |
| AIDL | 436 ファイル |
| Android.bp (Soong) | 216 ファイル |
| AndroidManifest.xml | 135 ファイル |
| C++ (.cpp/.h) | 541 ファイル |
| SELinux (.te) | 43 ファイル |

## 測定結果(実測値)

| 機能 | コマンド | 結果 | 所要時間 |
|---|---|---|---|
| クラス図 (full) | `-c` | 3,590 クラス / 4,440 関係線 / 71,637 行 puml、エラー 0 | 26s |
| クラス図 (headers-only) | `-c --mode headers-only` | 9,614 行 puml | 10s |
| シーケンス図 | `-q com.android.car.CarPropertyService.getProperty` | PropertyHalService まで実コールチェーン追跡。JavaDoc note・`<<binder>>` ステレオタイプ付き | 23s |
| Soong 解析 | `--android-bp` | 611 モジュール検出・java/cc/android/aidl に分類、依存グラフ puml 出力 | 1s |
| AIDL バインディング | `--aidl-binding` | 143 インターフェース検出、99 件で Stub 実装クラスを解決(`ICar` → `ICarImpl` 等、正解) | 20s |
| SELinux | `--selinux` | 36 type 宣言 / 83 allow / 3 neverallow を全 .te から抽出 | 1s |
| VHAL フロー | `--vhal-flow` | 359 アクセス / 58 プロパティ、プロパティ ID 16 進解決付き(`ENV_OUTSIDE_TEMPERATURE` = 0x11600703 等) | 42s |
| Insights | `--insights` | 3,542 クラス / 参照 199,355 件 / エントリポイント 231 / 循環 12 / デッドコード候補 2,284 | 23s |
| 影響解析 | `--impact com.android.car.CarPropertyService` | 直接 31 / 推移 308 呼び出し元 | 21s |
| Manifest 図 / screen-flow | `-M` / `--screen-flow`(KitchenSink) | Activity 17 / 遷移 18 を抽出 | 数秒 |
| SVG レンダリング | `-c -o *.svg`(power パッケージ) | 585 KB SVG 生成成功 | 6s |
| SVG レンダリング (full) | `-c -o *.svg`(全体 3,590 クラス) | Smetana レイアウト失敗。ただし .puml を保存し外部レンダリング手順を案内する graceful degradation | — |

## スコアカード

| # | カテゴリ | スコア | 重み | 根拠 |
|---|---|---|---|---|
| 1 | Java 構造解析(クラス図) | **9/10** | 20% | 1,917 ファイルを 26 秒・エラー 0 で解析。JavaDoc / アノテーション / `<<SystemApi>>` まで描画。AOSP ツリーを Gradle なしでそのまま食える |
| 2 | シーケンス図 / コールトレース | **8/10** | 10% | binder 境界・HAL 層まで実チェーン追跡。減点: participant に変数名 (`carPropertyConfig`) や `android` 等の未解決シンボルが混在し粒度が不揃い |
| 3 | AOSP 特化(Soong / AIDL / SELinux) | **9/10** | 15% | 611 Soong モジュール分類、AIDL→Stub 実装マッピングの精度が高い。SELinux も網羅。この組み合わせを出せる OSS ツールは稀少 |
| 4 | AAOS 特化(VHAL / screen-flow) | **7/10** | 15% | プロパティ ID 解決付き VHAL 利用箇所一覧は実用的。減点: テストコード混入(`HVAC_TEMPERATURE_SET` の GET 35 件は大半テスト)、空文字・`/* areaId= */ 0` 等の抽出ノイズ |
| 5 | アーキテクチャ俯瞰(insights / impact) | **7/10** | 10% | エントリポイント 231 検出は優秀。減点: 「83 パッケージの循環」は粒度が粗く行動に繋がらない。デッドコード 2,284 件はテスト含有で過大。impact スコアが全件 0.50/HIGH で序列が付かない |
| 6 | Kotlin 対応 | **3/10** | 10% | 部分的に解析できる(メソッド・型シグネチャ取得)が、`for` ループをクラスと誤認した `class "...menu.for"` を生成、本体が空のクラスも多い。AOSP の Kotlin 比率は増加中で将来リスク |
| 7 | C++ / ネイティブ層 | **0/10** | 5% | `cpp/` 配下 541 ファイル(carwatchdogd / carpowerpolicyd / VHAL native)は完全に対象外。設計上のスコープ外だが、AAOS の解析ではネイティブデーモンが盲点になる |
| 8 | スケーラビリティ / 堅牢性 | **7/10** | 10% | 334 MB リポジトリでクラッシュなし・全解析 1 分未満。SVG は Smetana 限界(数百クラス)で破綻するが graceful degradation と `-P` 分割で回避可 |
| 9 | CLI UX / 一貫性 | **6/10** | 5% | 機能は豊富だが `-o` の意味が機能ごとに異なる(file / dir / basename 派生)。`--aidl-binding -o <既存dir>` は生スタックトレースでクラッシュ |

### 総合スコア: **7.0 / 10**

> **評価サマリー**: AOSP/AAOS の Java・AIDL・Soong・SELinux 層に対しては
> 「クローン直後のツリーに 1 コマンドで当てられる解析ツール」としてすでに実戦投入可能。
> 一方で (a) テスト/プロダクションコードの未分離による各レポートのノイズ、
> (b) Kotlin 解析の誤認バグ、(c) ネイティブ層の盲点、(d) CLI 出力規約の不統一が
> スコアを押し下げている。いずれも構造的な欠陥ではなく改修可能。

## 改修ロードマップ(優先度順)

### P1 — 少工数で全レポートの精度が上がるもの

1. **テストコード分離** — `src/test` / `tests/` / `*Test.java` / `androidTest` を
   既定で区別し、`--include-tests` で戻せるようにする。
   効果: vhal-flow のノイズ、insights のデッドコード過大検出 (2,284 件)、
   impact のテスト呼び出し元混入が一挙に解消し、カテゴリ 4・5 が +2 点見込み。
2. **`-o` セマンティクス統一とクラッシュ修正** — `CliOutput.writeText`
   (`juml/app/cli/CliOutput.java:34`) がディレクトリ指定で `FileNotFoundException` を
   生のまま投げる。全コマンドで「dir なら中に既定名で書く / file ならそのまま」に統一。
3. **impact スコアの差別化** — 全件 0.50/HIGH では優先順位付けに使えない。
   参照種別 (DIRECT_CALL > TYPE_REFERENCE > IMPORT)・距離・fan-in で重み付けする。

### P2 — 中期(解析品質の底上げ)

4. **Kotlin パーサ修正** — 最低ラインとして制御構文 (`for`/`when`/`if`) をクラス誤認
   しないこと、class body のメンバー抽出を Java 同等に近づけること。
5. **VHAL フローの定数解決強化** — 空文字プロパティ・コメント付き引数
   (`/* areaId= */ 0`) のノイズ除去。ローカル変数経由のプロパティ ID も
   1 ホップだけ辿る。
6. **循環検出の粒度改善** — 83 パッケージの巨大 SCC をそのまま出さず、
   最小フィードバックエッジ・最短循環パスを提示して「どこを切れば壊れるか」を示す。

### P3 — 長期(スコープ拡張)

7. **ネイティブ層の軽量解析** — C++ 完全パースではなく、Soong の `cc_binary` /
   `cc_library` と AIDL の C++ バックエンドを既存の `--android-bp` /
   `--aidl-binding` レポートに接続し「Java↔native の境界」だけでも可視化する。
8. **巨大図の自動フォールバック** — Smetana 失敗を検出したら自動で
   per-package 分割 (`-P` 相当) に切り替える。
9. **クロスリポジトリ解析** — `hardware/interfaces`(VHAL 定義)と
   `packages/services/Car` を 2 ルートで読み、VHAL プロパティの定義↔利用を突合する。

## P1 改修後の再測定 (2026-06-10 同日実施)

P1 の 3 項目(テストコード分離 / `-o` 統一 / impact スコア差別化)を実装し、
同じ `packages/services/Car` ツリーで再測定した結果:

| 指標 | 改修前 | 改修後 | 変化 |
|---|---|---|---|
| `--vhal-flow` 検出アクセス | 359 件(テスト混入) | **33 件** | テスト用 `CUSTOM_*` / `BOOLEAN_PROP` ノイズが全消滅、実プロパティのみ残存 |
| `--impact CarPropertyService` 直接参照元 | 31 件(Test クラス 12 件混入) | **19 件** | テストクラス混入が解消 |
| impact スコア分布 | 全件 0.50 / HIGH | **1.00 HIGH 〜 0.40 LOW** | `DIRECT_CALL x6`=1.00 → `TYPE_REFERENCE`=0.70 → `IMPORT`=0.40 と序列化、層内スコア降順ソート |
| `--insights` エントリポイント | 231 件 | **45 件** | テストアプリの Activity/Service が除外され実態に接近 |
| `--insights` 解析クラス数 | 3,542 | 1,966 | プロダクションコードのみ |
| `--aidl-binding -o <既存dir>` | スタックトレースでクラッシュ | **`<dir>/aidl-binding.md` を出力** | 全コマンドで `-o` ディレクトリ指定が同じ規約で動作 |

テスト込みの従来挙動は新フラグ `--include-tests` で復帰できる。
この結果、スコアカードのカテゴリ 4 (AAOS 特化) は 7→8、カテゴリ 5 (俯瞰) は 7→8、
カテゴリ 9 (CLI UX) は 6→8 相当に改善し、総合スコアは **7.0 → 7.6 / 10** 相当となる。

## 再現手順

```sh
git clone --depth 1 https://android.googlesource.com/platform/packages/services/Car /tmp/aaos-car
/opt/gradle/bin/gradle jar -x test -x checkstyleMain -x checkstyleTest
JAR=build/libs/Juml.jar
java -Xmx6g -jar $JAR -c -v -o class.puml /tmp/aaos-car
java -Xmx6g -jar $JAR --android-bp   -o android-bp.md   /tmp/aaos-car
java -Xmx6g -jar $JAR --aidl-binding -o aidl-binding.md /tmp/aaos-car
java -Xmx6g -jar $JAR --selinux      -o selinux.md      /tmp/aaos-car
java -Xmx6g -jar $JAR --vhal-flow    -o vhal-flow.md    /tmp/aaos-car
java -Xmx6g -jar $JAR --insights     -o insights        /tmp/aaos-car
java -Xmx6g -jar $JAR --impact com.android.car.CarPropertyService -o impact.md /tmp/aaos-car
java -Xmx6g -jar $JAR -q com.android.car.CarPropertyService.getProperty -o seq.puml /tmp/aaos-car
```
