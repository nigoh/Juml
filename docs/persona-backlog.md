# Persona backlog: ペルソナ探索で集まった成長バックログ

`persona-explore` ワークフロー（ADR-0002）がラウンドごとに積む **使い勝手 / 不足機能** の一覧。
確定バグはここに載せず即修正する（PR に記録）。ここにある項目は **ユーザーが取捨選択して**
実装を決める。司令塔（メインループ）は追記と状態更新だけを行う。

- 状態: `open`（未着手）/ `in-progress` / `done`（PR 番号）/ `rejected`（理由）
- 重要度: `high` = 目的を達成できない / `medium` = 回り道で達成 / `low` = 好み
- 工数: `S` = 1 ファイル数十行 / `M` = 数ファイル / `L` = 設計変更
- 既存タイトルはワークフローの `known` として渡し、重複を防ぐ。

## 項目

| ID | 種別 | 重要度 | 工数 | タイトル | ペルソナ | 受け入れ条件 | 状態 | 初出 |
|---|---|---|---|---|---|---|---|---|
| PB-001 | missing-feature | medium | M | シーケンス図の参加者数・生成サイズにガードが無い (Main.main で 63447×465157px)。参加者上限 + 「大規模な図」確認を CLASS/INHERITANCE 以外にも | aosp-analyst | 参加者が閾値を超えると note 付きで打ち切られ、GUI では確認ダイアログが出る | open | R1 |
| PB-002 | missing-feature | medium | M | `--focus` + `--hide-unlinked` でも全クラス (897) を PlantUML に渡すため巨大ツリーで smetana が落ちる。近傍だけを出力する `--focus-only` (または規模ガード) が欲しい | aosp-analyst | src/main/java 全体に `--focus X` を付けると近傍のみの図が描画され、UML-R002 にならない | open | R1 |
| PB-003 | usability | low | S | ペルソナ (haiku) は「バグが無い」と「使いやすい」を区別せず所見 0 で返した。ミッション (ゴール指向タスク) を渡して達成可否・手順数・摩擦を必ず返させる仕組みに変更 | newcomer, power-user | ラウンド 2 以降で各ペルソナが missions 配列を返す | done (本 PR) | R1 |
| PB-004 | missing-feature | medium | M | Deep Link 図 (-D) が GUI から開けず、--nav-graph との役割分担も伝わらない | android-dev | GUIの図種メニューにDeep Link相当の項目が追加され選択して図が描画できる。加えて、-D と --nav-graph の対象範囲の違い（Manifestの外部公開deep link / nav graph内のdeepLink宣言）がツールチップかヘルプに明記されている。 | open | R2 |
| PB-005 | missing-feature | medium | M | Jetpack (--jetpack) ステレオタイプ表示がGUIから使えない | android-dev | GUIのクラス図表示オプション（チェックボックスやトグル）から--jetpack相当を有効化でき、有効時にActivity/Fragment/ViewModelへステレオタイプが付いた図が表示される。 | open | R2 |
| PB-006 | missing-feature | low | M | 画面遷移/UI操作マップ/設定のMarkdownレポートがGUIから開けない | android-dev | GUIのメニューかInsightsパネルから画面遷移レポート/UI操作マップ/設定レポートのいずれかを開くと、CLI相当のMarkdownまたは同等表示が確認できる。 | open | R2 |
| PB-007 | missing-feature | low | S | AIDL サンプルにStub実装が無く --aidl-binding の主目的が同梱データで検証できない | aosp-analyst | src/test/resources/samples/aidl配下にIRemoteServiceのStub実装クラスを追加し、--aidl-bindingを実行すると'With implementations: 1'以上が返る。 | open | R2 |
| PB-008 | usability | low | S | 大規模プロジェクトのツリー読み込み中に進捗表示が無く待機の伝え方が不明確 | aosp-analyst | 1000クラス規模のプロジェクトを開いたとき、ツリー構築中であることを示すプログレスインジケータ（バー/スピナー等）がGUI上に表示される。 | open (要確認: ペルソナは表示の有無を未確認。ガラスペインの進捗表示が既にある可能性) | R2 |
| PB-009 | usability | low | S | 現在のズーム倍率が数値表示されず正確な倍率への到達が判定できない | power-user | 図表示エリアまたはステータスバーに現在のズーム倍率が%表示され、ズーム操作に応じてリアルタイムに更新される。 | rejected (ステータスバー右下に倍率 % 表示が既にある。司令塔がスクリーンショットで確認。ペルソナの見落とし) | R2 |

## ラウンド履歴

| ラウンド | 日付 | seed | 所見 (raw) | bug 確定 / 棄却 | バックログ追加 | 備考 |
|---|---|---|---|---|---|---|
| R1 | 2026-09-05 | 42 | 5 | 3 / 2 | 3 | 確定 3 のうち修正 2 (nav-graph の終了コード / Batik GVT 事前ウォームアップで EDT 停止解消)。「`]]` エスケープ」は反証で誤認と判明 (実体は 897 クラスの巨大図での smetana クラッシュ = 既知 UML-R002 → PB-002)。usability 所見 0 → ミッション方式を導入 (PB-003) |
| R2 | 2026-09-05 | 43 | 5 | 0 / 0 | 6 | 確定バグ 0 → 停止条件到達。ミッション 16 件中 未達成 4 件 (GUI に無い CLI 専用機能: Deep Link 図 / --jetpack / Markdown レポート、AIDL サンプルの Stub 実装欠如)。ミッション方式で usability / missing-feature が 6 件に増えた |
