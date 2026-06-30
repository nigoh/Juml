# 技術調査メモ: Android 向け LSP と Juml でのコード表示／ジャンプ

> ステータス: **技術調査メモ（実装なし）**
> 対象ブランチ: `claude/android-lsp-support-qbvgry`
> 目的: 「Android に特化した LSP はあるか」「Juml で LSP 的なコード表示＆定義/参照ジャンプができるか」を整理し、
> 既存コード資産の棚卸しと段階的ロードマップを残す。

---

## 1. Android 向け LSP の現状

**結論: 「Android 専用」の単独 LSP は事実上存在しない。**
Android Studio 自体が IntelliJ プラットフォーム（LSP ではなく独自の PSI / インデックス基盤）で
できているため、Google は公式の "Android Language Server" を提供していない。
Android のコードを LSP で扱う場合は、**複数の言語サーバを組み合わせる**のが実情。

| 言語 / 対象 | 代表的な LSP | 備考 |
|---|---|---|
| Java | **Eclipse JDT Language Server (jdtls)** | VS Code の Red Hat Java 拡張の中身。Java の事実上の標準 |
| Kotlin | **kotlin-language-server (fwcd)** / JetBrains が公式 LSP を開発中 | fwcd 版は Gradle 連携・解決精度が弱め |
| XML (layout / manifest) | lemminx 等の汎用 XML LSP | Android リソース補完は弱い |
| AIDL / Gradle DSL | 専用 LSP はほぼ無い | |

→ 「Android に完全特化した LSP」を待つより、**jdtls + kotlin-language-server を束ねて
Android プロジェクトに向ける**のが現実解。

---

## 2. Juml に必要なもの — 2 つの道

### 道A: 自前の解析エンジンでジャンプを実装（LSP 不要・推奨）

Juml は既に `JavaParser + JavaSymbolSolver` で型・参照解決をしており、
ジャンプに必要な位置情報の **大半を保持済み**（下記「既存資産の棚卸し」参照）。
ここに「ソースコード閲覧パネル + クリックで定義/参照へ飛ぶ」を足せば、
外部プロセスなしで定義ジャンプが作れる。

- **長所**: 追加依存ゼロ／Juml の「ローカル完結・ネット通信や外部プロセスを行わない」設計思想
  （README・SECURITY.md）を堅持／fat jar 配布の手軽さを維持／既存の参照インデックス資産を再利用
  /「UML 図上のクラス → ソースの該当行へジャンプ」という Juml ならではの体験に繋げやすい
- **短所**: Kotlin は `KotlinLightScanner`（軽量スキャナ）止まりで完全な型解決は弱い
  → Kotlin の定義ジャンプはヒューリスティック精度になる

### 道B: 本物の LSP クライアントを内蔵（jdtls / kotlin-language-server を起動）

Juml を LSP クライアント化し、外部言語サーバを子プロセスで起動して
`textDocument/definition` 等を利用する。

- **長所**: 補完・ホバー・正確な Kotlin 解決まで "IDE 級"
- **短所**: 外部バイナリ依存／起動コスト／「ネット通信・外部プロセスを行わない」現行方針と衝突／
  fat jar 単体配布の手軽さが崩れる

### 推奨

**道A**。Juml の強み（軽量・ローカル完結・既存の参照インデックス資産）と最も整合し、
段階的に価値を出せる。将来 Kotlin 精度が必要になった時点で、道B を
「オプション機能（LSP が入っていれば使う）」として後付けするのが安全。

---

## 3. 既存コード資産の棚卸し

ジャンプ機能は「位置情報を持つ」「ソースを表示する」「クリックを位置に結びつける」の 3 要素。
現状を実コードで確認した結果は以下。

| 要素 | 既存資産 | 状態 |
|---|---|---|
| 逆参照（誰が使っているか） | `juml.core.refs.ReferenceIndex` / `ReferenceKey` / `ReferenceSite` | ✅ 完成。`ReferenceSite` は `file` + `lineHint` を保持し、**参照箇所の位置情報あり** |
| 逆参照 GUI / CLI | `app.uml.explore.ReverseReferencePanel`（`--ref-find` の GUI 版） | ✅ 表で参照箇所を一覧表示。**ジャンプ先候補のUIが既にある** |
| 名前解決（単純名→FQN） | `juml.core.refs.NameResolver` | ✅ import / 同パッケージ / JAR 索引で解決 |
| メソッド定義の位置 | `JavaMethodInfo.getStartLine()`（`MemberAdapter` が `getBegin().line` を設定） | ✅ **メソッド定義行を保持** → メソッドジャンプ可能 |
| クラス定義の位置 | `JavaClassInfo.getSourceFile()` あり／**クラス宣言行は無し** | ⚠️ ファイルは分かるが行が無い → 当面はファイル先頭着地。`classStartLine` 追加が望ましい（小改修） |
| ソースコード閲覧 | `app.uml.PumlSourcePanel` は **PlantUML テキスト専用**のリードオンリー表示 | ❌ 実コード閲覧パネルは無い → 新規実装が必要 |
| GUI タブ基盤 | `DiagramTabPane` / `DiagramController`（VS Code 風タブ中心アーキ、`.claude/rules/gui-tab-architecture.md`） | ✅ ソースタブを足す土台あり |

**要点**: 「位置情報」と「逆参照 UI」はほぼ揃っている。**最大の不足は実ソースコード閲覧パネル**で、
次にクラス宣言行（`classStartLine`）の補完。これらが埋まれば道A の定義/参照ジャンプは成立する。

---

## 4. 段階的ロードマップ（道A 前提）

各ステップ単独でユーザ価値が出る粒度に分割。

### フェーズ 1: ソースコード閲覧パネル（基盤）
- 新規 `SourceCodePanel`（仮）を追加。等幅・行番号付き・シンタックスハイライト（最小は無しでも可）。
- `DiagramTabPane` に「Source」タブ種別を追加し、`file:line` を受け取って該当行へスクロール＆ハイライト。
- 目的: 以降のジャンプ先を「表示する」器を用意するため。

### フェーズ 2: 参照箇所へのジャンプ（既存資産の最短活用）
- `ReverseReferencePanel` の行ダブルクリック → `ReferenceSite.getFile()` + `getLineHint()` で
  フェーズ1のパネルを開く。
- 目的: 既に位置情報を持つ逆参照を、最小コストで "クリックして飛べる" 体験にするため。

### フェーズ 3: 定義へのジャンプ（Go to Definition）
- メソッド: `JavaMethodInfo.getStartLine()` をそのまま利用。
- クラス: `JavaClassInfo` に `classStartLine` を追加（`MemberAdapter` の `TypeDeclaration.getBegin().line`）。
- UML クラス図・メンバ一覧（`MemberListPanel` / `MethodListPanel`）から定義ジャンプ。
- 目的: 「図 → 定義ソース」という Juml 独自の往復導線を作るため。

### フェーズ 4: ソース内ハイパーリンク（任意・発展）
- 表示中ソースの識別子クリック → `NameResolver` + `ReferenceIndex` で定義/参照へ。
- 目的: IDE 的な相互ジャンプ。SymbolSolver の解決コストとキャッシュ設計が要検討。

### フェーズ 5（任意）: LSP クライアント（道B のオプション化）
- jdtls / kotlin-language-server が環境にあれば検出して利用、無ければ道A にフォールバック。
- Kotlin の正確な定義ジャンプ・補完・ホバーが必要になった場合のみ。
- 目的: 設計思想（ローカル完結）を壊さずに IDE 級精度を「使えるなら使う」形で提供するため。

---

## 5. 既知の論点・リスク

- **設計思想との整合**: 道B（外部プロセス起動）は SECURITY.md の「外部プロセス起動・ネット通信を行わない」
  方針と衝突する。採るならオプション扱い＋明示同意が前提。
- **Kotlin 精度**: 道A の Kotlin は軽量スキャナ依存。定義ジャンプは "それっぽい候補" 止まりになり得る。
- **パフォーマンス**: ソース内ハイパーリンク（フェーズ4）は SymbolSolver の都度解決が重い。
  既存の `ReferenceIndex` / SQLite キャッシュ（`~/.juml/cache/<hash>/index.db`）への寄せが必要。
- **大規模図/大規模ソース**: 既存の「巨大な図が描画できないときの対処」と同様、表示側のスケール対策が要る。

---

## 6. 次アクション（実装着手時）
1. フェーズ1 `SourceCodePanel` + Source タブの最小実装。
2. フェーズ2 で `ReverseReferencePanel` からのダブルクリックジャンプを配線（既存資産のみで完結）。
3. `JavaClassInfo.classStartLine` を追加してクラス定義ジャンプを開通。

> 本メモは実装を含まない調査結果。着手の可否・順序はオーナー判断に委ねる。
