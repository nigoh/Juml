// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.regex.Pattern;

/**
 * GUI デザイナーが扱う図種。PlantUML テキストの内容から自動判定する。
 *
 * <p>まず {@code @startmindmap} で始まる図は無条件にマインドマップと確定する
 * ({@code @startuml} 前提の他 8 図種と衝突しない)。次に {@code usecase} / {@code component} キーワード (いずれも一意) があれば
 * ユースケース図 / コンポーネント図と確定する。次に一意マーカーを先取りで判定する:
 * {@code object 名前} 宣言 → オブジェクト図、ER 図固有マーカー (crow's-foot 関係演算子
 * {@code ||--o{} 等、または {@code entity "..." {} の列ブロック + {@code hide circle}) → ER 図、
 * {@code node} / {@code artifact} / {@code cloud} 宣言 → 配置図 ({@code entity} / {@code database}
 * はシーケンス図と共有するため単独では判定材料にしない)。無ければ行単位の先勝ちで:
 * アクティビティ図の構文 ({@code start} / {@code :action;} /
 * {@code if (...) then}) → 状態遷移図の構文 ({@code state X} / {@code [*] --> X}) →
 * クラス宣言 ({@code class} / {@code interface} / {@code enum}) → シーケンス図の構文
 * ({@code participant} / {@code A -> B} / {@code activate}) の順で調べ、どれにも該当
 * しなければ既定のクラス図として扱う (未対応構文はクラス図コーデックが編集ロックで保全する)。</p>
 */
public enum SketchDiagramType {

    /** クラス図 (既定)。 */
    CLASS,
    /** シーケンス図。 */
    SEQUENCE,
    /** アクティビティ図 (新形式構文)。 */
    ACTIVITY,
    /** 状態遷移図。 */
    STATE,
    /** ユースケース図。 */
    USECASE,
    /** コンポーネント図。 */
    COMPONENT,
    /** オブジェクト図。 */
    OBJECT,
    /** ER (エンティティ関連) 図。 */
    ER,
    /** 配置図 (デプロイ図)。 */
    DEPLOYMENT,
    /** マインドマップ ({@code @startmindmap})。 */
    MINDMAP;

    /**
     * 宣言キーワードに続く引数部。<b>矢印で始まらないこと</b>を要求する。
     *
     * <p>要求しないと、宣言キーワードと同綴りの識別子を左端に置いた<b>関連行</b>が
     * 宣言に見える: {@code usecase --> User} / {@code node --> Server} /
     * {@code component --> Api} はどれも「usecase/node/component という名前の要素からの
     * 関連」であって宣言ではない。誤認すると図種を取り違え、無関係な設計器が開く
     * ({@code Note --> Alice} で同じ事故を起こしたのと同じ形)。</p>
     */
    private static final String DECL_ARG = "\\s+(?![-<.])\\S.*$";

    /**
     * ユースケース図に固有の行。{@code usecase} キーワードは他図種と衝突しないため、
     * これが 1 行でもあればユースケース図と確定できる ({@code actor} はシーケンス図と
     * 共有するため単独では判定材料にしない)。
     */
    private static final Pattern USECASE_LINE = Pattern.compile("^usecase" + DECL_ARG);
    /**
     * コンポーネント図に固有の行。{@code component} キーワード、または単独の短縮形
     * {@code [Id]} (Id は識別子)。{@code [*]} は識別子でないので状態図と衝突しない。
     */
    private static final Pattern COMPONENT_LINE = Pattern.compile(
            "^(component" + DECL_ARG + "|\\[" + SketchIdentifier.BARE + "\\]\\s*)$");
    /**
     * オブジェクト図に固有の行。{@code object 名前} 宣言は他図種と衝突しないため、これが
     * 1 行でもあればオブジェクト図と確定できる。
     */
    private static final Pattern OBJECT_LINE = Pattern.compile(
            "^object\\s+" + SketchIdentifier.HEAD_ONLY + ".*$");
    /**
     * 配置図に固有の宣言行。{@code node} / {@code artifact} / {@code cloud} は他図種と
     * 衝突しないため、これらが 1 行でもあれば配置図と確定できる。{@code database} は
     * シーケンス図の参加者宣言と共有するため、単独では判定材料にしない。
     */
    private static final Pattern DEPLOYMENT_LINE = Pattern.compile(
            "^(node|artifact|cloud)" + DECL_ARG);

    /** アクティビティ図に固有の行 ({@code start} / {@code :action;} / {@code if} など)。 */
    private static final Pattern ACTIVITY_LINE = Pattern.compile(
            "^(start|stop|end|:.*;|if\\s*\\(.*|repeat\\b.*|while\\s*\\(.*|fork\\b.*)$");
    /**
     * 状態遷移図に固有の行。{@code state} 宣言、または初期/終了の擬似状態 {@code [*]} を
     * 端点に含む遷移。素の {@code A --> B} はクラス図の関連と曖昧なため判定材料にしない。
     */
    private static final Pattern STATE_LINE = Pattern.compile(
            "^(state\\s+" + SketchIdentifier.HEAD_ONLY + ".*|\\[\\*\\]\\s*-->.*|.*-->\\s*\\[\\*\\].*)$");
    /**
     * ER 図に固有の crow's-foot (IE) リレーション演算子 ({@code ||--o{} 等)。左右の
     * カーディナリティトークン ({@code |} / {@code o} / {@code {} / {@code }} の組) は
     * クラス図の関係表記 ({@code <|--} / {@code o--} / {@code *--} / {@code -->}) と
     * 一致しないため、これが 1 つでもあれば ER 図と確定できる。
     */
    private static final Pattern ER_RELATION = Pattern.compile(
            "(\\|\\||\\|o|\\}o|\\}\\|)--(\\|\\||o\\||o\\{|\\|\\{)");
    /** ER 図の列ブロックを開くエンティティ宣言 ({@code entity ... {})。 */
    private static final Pattern ER_ENTITY_BLOCK = Pattern.compile("^entity\\b.*\\{\\s*$");
    /** ER 図でエンティティを表として描かせる {@code hide circle} 指令 (ER コーデック専用)。 */
    private static final Pattern ER_HIDE_CIRCLE = Pattern.compile("^hide\\s+circle\\b.*$");
    /**
     * 座標コメント {@code '@pos id x y}。位置を持つ設計器 (クラス/コンポーネント/配置/ER/
     * オブジェクト/状態/ユースケース) だけが出力し、シーケンス図・アクティビティ図の
     * コーデックは決して出さない。したがってこの行があれば「シーケンス図ではない」と
     * 断定でき、{@code database} / {@code actor} のような共有キーワードの持ち主を絞り込める。
     */
    private static final Pattern POS_COMMENT = Pattern.compile("^'@pos\\s+\\S+\\s+-?\\d+\\s+-?\\d+\\s*$");
    /**
     * デザイナーが出したレイアウトコメント {@code '@pos <id> <x> <y>} か。
     *
     * <p>「編集を有効化」のコメント除去はこの行を対象外にする (消すと全ノードの配置が
     * リセットされる)。除去できないコメントが残る図は comment-only lock ではない。</p>
     */
    public static boolean isLayoutComment(String line) {
        return line != null && POS_COMMENT.matcher(line.trim()).matches();
    }

    /**
     * 「編集を有効化」で取り除いてよいコメント行か (レイアウトコメントは残す)。
     *
     * <p>ブロックコメント {@code /' … '/} も対象。ただし<b>その行だけで閉じている</b>
     * ものに限る — 解除は行単位で消すので、複数行に跨るブロックコメントの先頭行だけを
     * 消すとファイルが壊れる。</p>
     *
     * <p>{@link SketchBlockLine#isComment} がブロックコメントもロック対象にしたのに、
     * 解除側のこの判定は {@code '} しか見ていなかった。結果、ブロックコメントを含む図は
     * <b>読み取り専用のまま解除の手段が提示されない</b>状態になっていた —
     * ロックする規則と解除できる規則がずれると、利用者は出口の無い状態に置かれる。</p>
     */
    public static boolean isRemovableComment(String line) {
        String t = line == null ? "" : line.strip();
        if (t.startsWith("/'")) {
            return t.endsWith("'/") && t.length() >= 4;
        }
        return t.startsWith("'") && !isLayoutComment(t);
    }

    /** 配置図が出す {@code database} 宣言 (シーケンス図の参加者宣言と綴りを共有する)。 */
    private static final Pattern DATABASE_LINE = Pattern.compile("^database" + DECL_ARG);
    /** ユースケース図が出す {@code actor} 宣言 (シーケンス図の参加者宣言と綴りを共有する)。 */
    private static final Pattern ACTOR_LINE = Pattern.compile("^actor" + DECL_ARG);
    /** クラス図に固有の宣言行。 */
    private static final Pattern CLASS_LINE = Pattern.compile(
            "^(abstract\\s+)?(class|interface|enum)\\b.*$");
    /**
     * シーケンス図に固有の行。矢印は {@code ->} 系のみ ({@code -->} はクラス図の関連と
     * 曖昧なため判定材料にしない。PlantUML 自身も同様の曖昧さを他の行で解決している)。
     */
    private static final Pattern SEQUENCE_LINE = Pattern.compile(
            "^(participant\\b.*|actor\\b.*|boundary\\b.*|control\\b.*|entity\\b.*"
                    + "|database\\b.*|queue\\b.*|collections\\b.*"
                    + "|activate\\b.*|deactivate\\b.*|autonumber\\b.*"
                    + "|alt\\b.*|opt\\b.*|loop\\b.*|par\\b.*|group\\b.*"
                    + "|" + SketchIdentifier.DOTTED + "\\s*(->>?|-->>)\\s*" + SketchIdentifier.HEAD_ONLY + ".*)$");

    /**
     * ブロック本体 ({@code entity X { ... }} / {@code class A { ... }} の中身) を落として
     * 「トップレベル宣言だけ」を返す。
     *
     * <p>本体の行はメンバー名であって宣言ではない。素通しすると、たとえば ER 図の列名を
     * {@code node} にしただけで {@code DEPLOYMENT_LINE} に一致し、保存して開き直した瞬間に
     * <b>空で編集ロックされた配置図デザイナー</b>が出る (列名は自由入力なので普通に起きる)。
     * PlantUML 側の判定 ({@code PumlDiagramScan}) にも同じ穴があり、そちらは自由記述の
     * 除外で塞いだ。ここはブロック本体の除外で塞ぐ。</p>
     */
    private static String[] topLevelLines(String[] lines) {
        // note / legend / title / header の本文と /' ... '/ は利用者が書いた散文であって
        // 宣言ではない。素通しすると、note に「node Server」と 1 行書いた ER 図が
        // 配置図と判定され、空で編集ロックされた配置図デザイナーが開く。
        // PlantUML 側の判定と同じマスクを共有する (片方だけ直すと再発するため)。
        boolean[] isCode = juml.core.formats.uml.PumlDiagramScan.codeLineMask(lines);
        java.util.List<String> out = new java.util.ArrayList<>();
        int depth = 0;
        for (int i = 0; i < lines.length; i++) {
            // Juml 自身が出す座標コメント ('@pos …) は PlantUML にはコメントでも、
            // 設計器にとっては構造の一部 (どの設計器が書いた図かを示す)。落とすと
            // 「actor 1 つだけのユースケース図」がシーケンス図へ流れる。
            if (!isCode[i] && !isLayoutComment(lines[i])) {
                continue;
            }
            if (depth == 0) {
                out.add(lines[i]);
            }
            depth = updateDepth(lines[i].trim(), depth);
        }
        return out.toArray(new String[0]);
    }

    /**
     * 行内の波括弧で深さを更新する (宣言行の末尾 {@code &#123;} はその行を宣言として残す)。
     *
     * <p>コメント ({@code '} 以降) と引用ラベルの中の波括弧は数えない。数えてしまうと
     * コメントに波括弧を 1 つ書いただけで以降の全行が判定から消え、図種を見失う。</p>
     */
    private static int updateDepth(String line, int depth) {
        int d = depth;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (inQuotes) {
                continue;
            } else if (c == '\'') {
                break; // 行コメント。以降は本文ではない。
            } else if (c == '{') {
                d++;
            } else if (c == '}' && d > 0) {
                d--;
            }
        }
        return d;
    }

    /**
     * そのコーデックがこのテキストを<b>丸ごと</b>扱えるか (未対応行ゼロか)。
     *
     * <p>コーデックは自分が読める構文を正確に知っている。行の見た目を正規表現で
     * 推測するより確実で、しかも設計器の出力は必ず自分のコーデックで往復できるため、
     * 「保存 → 開き直すと別の設計器が開く」という事故が原理的に起きない。</p>
     */
    private static boolean fullySupportedBy(SketchDiagramType type, String text) {
        try {
            switch (type) {
                case CLASS:      return SketchPumlCodec.parse(text).isFullySupported();
                case SEQUENCE:   return SeqSketchCodec.parse(text).isFullySupported();
                case ACTIVITY:   return ActivitySketchCodec.parse(text).isFullySupported();
                case STATE:      return StateSketchCodec.parse(text).isFullySupported();
                case USECASE:    return UseCaseSketchCodec.parse(text).isFullySupported();
                case COMPONENT:  return ComponentSketchCodec.parse(text).isFullySupported();
                case OBJECT:     return ObjectSketchCodec.parse(text).isFullySupported();
                case ER:         return ErSketchCodec.parse(text).isFullySupported();
                case DEPLOYMENT: return DeploySketchCodec.parse(text).isFullySupported();
                case MINDMAP:    return MindmapSketchCodec.parse(text).isFullySupported();
                default:         return false;
            }
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * そのコーデックがこのテキストから<b>要素を 1 つも作れなかった</b>か。
     *
     * <p>「未対応行があって編集ロック」と「そもそも自分の図ではない」を区別するために使う。
     * 前者はその設計器で開くのが正しい (利用者はロック解除できる) が、後者は
     * <b>空の設計器</b>が開いて図に一切触れなくなる。後者のときだけ他コーデックを探す。</p>
     */
    private static boolean recognisedNothing(SketchDiagramType type, String text) {
        try {
            switch (type) {
                case CLASS:      return SketchPumlCodec.parse(text).model.getClasses().isEmpty();
                case SEQUENCE:   return SeqSketchCodec.parse(text).model.getParticipants().isEmpty();
                case ACTIVITY:   return ActivitySketchCodec.parse(text).model.getNodes().isEmpty();
                case STATE:      return StateSketchCodec.parse(text).model.getStates().isEmpty();
                case USECASE:    return UseCaseSketchCodec.parse(text).model.getNodes().isEmpty();
                case COMPONENT:  return ComponentSketchCodec.parse(text).model.getNodes().isEmpty();
                case OBJECT:     return ObjectSketchCodec.parse(text).model.getObjects().isEmpty();
                case ER:         return ErSketchCodec.parse(text).model.getEntities().isEmpty();
                case DEPLOYMENT: return DeploySketchCodec.parse(text).model.getNodes().isEmpty();
                case MINDMAP:    return MindmapSketchCodec.parse(text).model.getRoot() == null;
                default:         return true;
            }
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /**
     * 「完全に扱えるコーデック」を探す順序。
     *
     * <p>空図のようにどれでも扱えるテキストがあるので順序は必要だが、下の行走査より
     * ずっと安定している。マインドマップは開始ディレクティブが違うので最優先、
     * 続いて固有構文を持つものから並べ、最後に受理範囲の広いクラス図を置く。</p>
     */
    private static final SketchDiagramType[] SUPPORT_PROBE_ORDER = {
        MINDMAP, ER, DEPLOYMENT, USECASE, COMPONENT, OBJECT, STATE, ACTIVITY, SEQUENCE, CLASS,
    };

    /**
     * PlantUML テキストから図種を判定する。
     *
     * <p>まず<b>各コーデックに実際に読ませて</b>、丸ごと扱えるものがあればそれを採る。
     * どれも扱えないテキスト (手書きの未対応構文など) だけ、下の行走査で
     * 「どの設計器を表示ロックで見せるか」を決める。行走査は綴りが同じだけの識別子で
     * 誤判定しやすく (列名を {@code node} にした ER 図が配置図になる等)、監査で何度も
     * 実害が出たため、判定の主役から降ろしてある。</p>
     */
    public static SketchDiagramType detect(String text) {
        String source = text == null ? "" : text;
        SketchDiagramType scanned = detectByScanning(source);
        // 走査の答えが実際に成り立つなら、それを採る (曖昧なテキストの優先順位は
        // 走査側で丁寧に決めてあるので、能力だけで上書きしない)。
        if (fullySupportedBy(scanned, source)) {
            // コーデックが丸ごと読めたなら、そのまま採る。ここで PlantUML の読みを
            // 被せてはいけない: PlantUML は曖昧な断片を既定でシーケンス図と読むので、
            // 素の "A --> B" だけのクラス図がシーケンス図に化ける。
            return scanned;
        }
        // 走査の答えを丸ごとは読めないなら、丸ごと読めるコーデックを先に探す。
        // 走査の答えが部分的に読めるだけで打ち切ると、<b>本当にその図種のコーデックが
        // 完全に読める場合でも取り逃がす</b>: たとえば配置図の出力
        // (component ブロックの中に artifact を入れ子にした形) は、トップレベルに
        // node/artifact が現れないためコンポーネント図と走査され、コンポーネント設計器が
        // 一部だけ認識して編集ロックで開いていた — 配置図コーデックなら全部読めるのに。
        // 候補は PlantUML 自身の解釈が許す図種に絞る。絞らずに「最初に丸ごと読めたもの」を
        // 採ると、<b>読めるだけの無関係な設計器</b>が横取りする: ユースケースコーデックは
        // actor 宣言と --> 関連を読めてしまうので、クラス図 (Dog --|> Animal) も
        // シーケンス図 (actor cloud / cloud --> User) もユースケース設計器へ流れ、しかも
        // 編集可能で開くので最初の操作で元の図が書き潰される。
        java.util.Set<SketchDiagramType> allowed = parsedKindTypes(source);
        for (SketchDiagramType candidate : SUPPORT_PROBE_ORDER) {
            if ((allowed == null || allowed.contains(candidate))
                    && fullySupportedBy(candidate, source)) {
                return candidate;
            }
        }
        // ここまで来たら、どのコーデックもこのテキストを丸ごとは読めない (手書きの未対応
        // 構文が混じっている等)。走査の答えを PlantUML 自身の解釈で裏取りしてから、
        // 表示ロックで見せる設計器を決める。
        //
        // 以前ここには「走査が要素を 1 つでも認識していれば走査の答えを採る」ための分岐が
        // あったが、両分岐が同じ式を返す死んだコードになっていた (全体を読めるコーデックを
        // 探す処理を上へ移したときの取り残し)。
        return parserCheckedFallback(source, scanned);
    }

    /**
     * PlantUML 自身の解釈が許す設計器 (実機で各設計器の出力を解析して確定した対応)。
     *
     * <p>クラス図・オブジェクト図・ER 図は PlantUML から見ればどれも {@code ClassDiagram}、
     * ユースケース図・コンポーネント図・配置図はどれも {@code DescriptionDiagram} なので、
     * この対応だけでは設計器を 1 つに決められない。決められるのは<b>明らかな間違いの否定</b>で、
     * それがここでの役目。</p>
     */
    private static final java.util.Map<String, java.util.Set<SketchDiagramType>> PARSED_KIND_TYPES =
            java.util.Map.of(
                "SequenceDiagram", java.util.Set.of(SEQUENCE),
                "ActivityDiagram3", java.util.Set.of(ACTIVITY),
                "StateDiagram", java.util.Set.of(STATE),
                "MindMapDiagram", java.util.Set.of(MINDMAP),
                // 実測: コンポーネント図でも配置図でも、要素が interface だけ /
                // 中身の無い rectangle だけになると PlantUML は ClassDiagram と読む。
                // この集合は「明らかに違う設計器を否定する」ための拒否権であって選定では
                // ないので、実際に起こり得る対応を漏らさず入れる。選定は
                // fullySupportedBy が行う (各コーデックは自分の構文しか丸ごと読めない)。
                "ClassDiagram", java.util.Set.of(CLASS, OBJECT, ER, COMPONENT, DEPLOYMENT),
                "DescriptionDiagram", java.util.Set.of(USECASE, COMPONENT, DEPLOYMENT));

    /** 解釈が許す設計器のうち、どれとも決められないときに出す代表。 */
    private static final java.util.Map<String, SketchDiagramType> PARSED_KIND_FALLBACK =
            java.util.Map.of(
                "SequenceDiagram", SEQUENCE,
                "ActivityDiagram3", ACTIVITY,
                "StateDiagram", STATE,
                "MindMapDiagram", MINDMAP,
                "ClassDiagram", CLASS,
                "DescriptionDiagram", COMPONENT);

    /**
     * 最後の手段として、行走査の答えを PlantUML 自身の解釈で裏取りする。
     *
     * <p>行走査は自由記述や綴りを共有する識別子で誤判定する。塞いでも塞いでも別の形が
     * 出てくる ({@code note across} / {@code Legend --> Done} / {@code Title --> Footer} …)
     * ので、<b>正規表現を完璧にすることを当てにしない</b>。PlantUML が「これはシーケンス図だ」
     * と言っているのに走査が配置図と答えたなら、走査の方が間違っている。</p>
     *
     * <p>ただし効かせるのは<b>コーデックが丸ごとは読めなかったとき</b>だけ。読めたものに
     * まで被せると逆効果になる: PlantUML は曖昧な断片 (素の {@code A --> B} など) を
     * 既定でシーケンス図と読むため、クラス図がシーケンス図に化ける。裏取りが意味を持つのは
     * 「コーデックの答えが当てにならない」と分かっている場面に限られる。</p>
     */
    /** 座標を持たない設計器 (これらのコーデックは {@code '@pos} を決して出さない)。 */
    private static final java.util.Set<SketchDiagramType> POSITIONLESS_DESIGNERS =
            java.util.Set.of(SEQUENCE, ACTIVITY);

    /**
     * 座標コメントを持つ図から、座標を出さない設計器を候補から外す。
     *
     * <p>{@code '@pos} を書くのは位置を持つ設計器だけで、シーケンス図・アクティビティ図の
     * コーデックは決して出さない。したがってこの行がある図を PlantUML が
     * {@code SequenceDiagram} と読んでも、それは<b>曖昧な断片を既定でシーケンスと読む</b>
     * 動作であって、どの設計器が書いたかの証拠にはならない。除外しないと、未対応行が
     * 1 行混じっただけで配置図・ユースケース図・ER 図がシーケンス設計器へ飛ぶ。</p>
     *
     * <p>除外の結果が空になったら制約なしとして扱う (呼び出し側が走査の答えを保つ)。</p>
     */
    private static java.util.Set<SketchDiagramType> withoutPositionlessDesigners(
            java.util.Set<SketchDiagramType> allowed, String source) {
        if (allowed == null || !hasLayoutComment(source)) {
            return allowed;
        }
        java.util.Set<SketchDiagramType> kept = new java.util.HashSet<>(allowed);
        kept.removeAll(POSITIONLESS_DESIGNERS);
        return kept;
    }

    /** Juml の設計器が書いた図か (座標コメントを 1 行でも含むか)。 */
    private static boolean hasLayoutComment(String text) {
        for (String line : (text == null ? "" : text).split("\n", -1)) {
            if (isLayoutComment(line)) {
                return true;
            }
        }
        return false;
    }

    /** PlantUML 自身の解釈が許す設計器 (解釈できなければ null = 制約なし)。 */
    private static java.util.Set<SketchDiagramType> parsedKindTypes(String source) {
        return PARSED_KIND_TYPES.get(juml.core.formats.uml.PumlDiagramScan.parsedKind(source));
    }

    private static SketchDiagramType parserCheckedFallback(String source,
                                                           SketchDiagramType guess) {
        String kind = juml.core.formats.uml.PumlDiagramScan.parsedKind(source);
        java.util.Set<SketchDiagramType> allowed = withoutPositionlessDesigners(
                PARSED_KIND_TYPES.get(kind), source);
        if (allowed == null || allowed.isEmpty() || allowed.contains(guess)) {
            // 解釈できない図 (構文エラー・未対応記法) は走査の答えのまま扱う。
            return guess;
        }
        for (SketchDiagramType candidate : SUPPORT_PROBE_ORDER) {
            if (allowed.contains(candidate) && fullySupportedBy(candidate, source)) {
                return candidate;
            }
        }
        // 丸ごと読めるものが無ければ、せめて<b>要素を認識できる</b>設計器を選ぶ。
        // 代表値をそのまま返すと、要素を 1 つも読めない設計器が空のキャンバスで開き、
        // 図に一切触れなくなる (使用例図 "actor User / User --> (Login)" に対する
        // コンポーネント設計器がこれ)。未対応行があってロック、は許せるが、
        // 何も見えないのは許せない。
        for (SketchDiagramType candidate : SUPPORT_PROBE_ORDER) {
            if (allowed.contains(candidate) && !recognisedNothing(candidate, source)) {
                return candidate;
            }
        }
        return PARSED_KIND_FALLBACK.get(kind);
    }

    /** どのコーデックも完全には扱えないテキスト向けの行走査 (表示ロックする設計器を選ぶ)。 */
    private static SketchDiagramType detectByScanning(String text) {
        String[] lines = topLevelLines((text == null ? "" : text).split("\n", -1));
        // @startmindmap で始まる図はマインドマップと確定する (@startuml 前提の他図種と衝突なし)。
        for (String raw : lines) {
            if (raw.trim().startsWith("@startmindmap")) {
                return MINDMAP;
            }
        }
        // usecase キーワードは他図種と衝突しないため、1 行でもあればユースケース図と確定する
        // (actor はシーケンス図と共有するため、行順に依らずここで先取りして判定する)。
        for (String raw : lines) {
            if (USECASE_LINE.matcher(raw.trim()).matches()) {
                return USECASE;
            }
        }
        // node / artifact / cloud は配置図だけが出す宣言。配置図はコンポーネントノードも
        // 持てる (Kind.COMPONENT) ため、component より先に判定しないと「node と component が
        // 混在する配置図」がコンポーネント図へ流れてしまう。コンポーネント図コーデックは
        // component / interface しか扱わないので、この順序は安全。
        for (String raw : lines) {
            if (DEPLOYMENT_LINE.matcher(raw.trim()).matches()) {
                return DEPLOYMENT;
            }
        }
        // component キーワード / [Id] も他図種と衝突しないため先取りで判定する。
        for (String raw : lines) {
            if (COMPONENT_LINE.matcher(raw.trim()).matches()) {
                return COMPONENT;
            }
        }
        // object キーワードも他図種と衝突しないため先取りで判定する。
        for (String raw : lines) {
            if (OBJECT_LINE.matcher(raw.trim()).matches()) {
                return OBJECT;
            }
        }
        // ER 図固有マーカー: crow's-foot 演算子 (単独で確定)、または entity 列ブロック +
        // hide circle の同時出現。entity 単独はシーケンス図と共有するため判定材料にしない。
        boolean entityBlock = false;
        boolean hideCircle = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (ER_RELATION.matcher(line).find()) {
                return ER;
            }
            entityBlock = entityBlock || ER_ENTITY_BLOCK.matcher(line).matches();
            hideCircle = hideCircle || ER_HIDE_CIRCLE.matcher(line).matches();
        }
        // hide circle は ER コーデックだけが出す指令なので、列を持たない (= entity 行が
        // ブロックを開かない) エンティティしか残っていなくても ER 図と確定できる。
        // これを見ないと `hide circle` + `entity A` がシーケンス図の参加者宣言に吸われる。
        if (hideCircle) {
            return ER;
        }
        if (entityBlock) {
            return ER;
        }
        // 座標コメントがあれば「位置を持つ設計器の出力」= シーケンス図ではないと断定できる。
        // これを使って、綴りを共有するキーワードしか残っていない図の持ち主を決める
        // (例: 配置図で database ノードだけ、ユースケース図でアクターだけ残した状態)。
        boolean hasPos = false;
        for (String raw : lines) {
            if (POS_COMMENT.matcher(raw.trim()).matches()) {
                hasPos = true;
                break;
            }
        }
        if (hasPos) {
            for (String raw : lines) {
                String line = raw.trim();
                if (DATABASE_LINE.matcher(line).matches()) {
                    return DEPLOYMENT;
                }
                if (ACTOR_LINE.matcher(line).matches()) {
                    return USECASE;
                }
            }
        }
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("@") || line.startsWith("'")) {
                continue;
            }
            if (ACTIVITY_LINE.matcher(line).matches()) {
                return ACTIVITY;
            }
            if (STATE_LINE.matcher(line).matches()) {
                return STATE;
            }
            if (CLASS_LINE.matcher(line).matches()) {
                return CLASS;
            }
            if (SEQUENCE_LINE.matcher(line).matches()) {
                return SEQUENCE;
            }
        }
        return CLASS;
    }
}
