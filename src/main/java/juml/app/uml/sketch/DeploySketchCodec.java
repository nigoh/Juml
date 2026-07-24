// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link DeploySketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文は配置図の要素一式: ノード宣言 8 種 ({@code node} / {@code artifact} /
 * {@code database} / {@code cloud} / {@code component} / {@code rectangle} /
 * {@code folder} / {@code frame}) の素の id 形式・{@code "表示名" as id} 形式・
 * エイリアス無しの引用符宣言 ({@code node "表示名"})、それらの
 * {@code node "X" as x { … }} 入れ子コンテナ (任意深さ)、リンク 3 種
 * ({@code -->} / {@code ..>} / {@code --}) ({@code : label} 付き・自己リンク可・
 * 端点はエイリアス/引用符ラベルのどちらでも可)、レイアウト座標コメント
 * ({@code '@pos id x y})。入れ子ノードの座標は親の内側原点からの相対値として
 * {@code '@pos} に保存する。手編集で負の相対座標が指定されても {@link #parse} 時に
 * 0 へクランプし (トップレベルの絶対座標は対象外)、負座標がモデルへ入らないようにする
 * (bug-hunt round6。{@link #applyPositions(List, Map, boolean)} 参照)。往復不能な構文
 * (未知キーワードのブロック等) だけ「未対応」として報告し編集をロックしてテキストを守る。</p>
 */
public final class DeploySketchCodec {

    /** ノード宣言キーワードの選択肢 (正規表現片)。 */
    private static final String KW =
            "node|artifact|database|cloud|component|rectangle|folder|frame";

    /** 別名 (id) として許される厳格な識別子。合成 id・リンク端点にも使う。 */
    private static final String BARE_ID = "[A-Za-z_$][\\w$]*";
    private static final Pattern BARE_ID_ONLY = Pattern.compile("^" + BARE_ID + "$");

    private static final Pattern POS = Pattern.compile(
            "^'@pos\\s+(\\S+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*$");
    private static final Pattern ALIAS = Pattern.compile(
            "^(" + KW + ")\\s+\"([^\"]*)\"\\s+as\\s+(" + BARE_ID + ")\\s*$");
    /** 引用符ラベルのみでエイリアス無し ({@code node "Web Server"})。id は合成する。 */
    private static final Pattern ANON_QUOTED = Pattern.compile(
            "^(" + KW + ")\\s+\"([^\"]*)\"\\s*$");
    /** 素の宣言。id は {@code .}/{@code -} も許す緩い文字集合で受理し、
     * 厳格な識別子でなければ (例: {@code app.war}) ラベルとして扱い id を合成する。 */
    private static final Pattern DECL_LOOSE = Pattern.compile(
            "^(" + KW + ")\\s+([A-Za-z_$][\\w$.-]*)\\s*$");
    /** リンク端点: 素の識別子、または引用符付きラベル参照。 */
    private static final String ENDPOINT = "(?:\"[^\"]*\"|" + BARE_ID + ")";
    private static final Pattern RELATION = Pattern.compile(
            "^(" + ENDPOINT + ")\\s*(-->|\\.\\.>|--)\\s*(" + ENDPOINT + ")"
                    + "(?:\\s*:\\s*(.*\\S))?\\s*$");

    private static final int GRID_X = 200;
    private static final int GRID_Y = 120;
    private static final int GRID_COLS = 3;
    private static final int GRID_MARGIN = 50;
    /** toPuml のブロック内インデント幅 (スペース 2 個/階層)。 */
    private static final String INDENT_UNIT = "  ";

    private DeploySketchCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final DeploySketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(DeploySketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** 未解決のまま保留したリンク 1 本分 (2 パス目でノード宣言確定後に解決する)。 */
    private record PendingRelation(String fromToken, DeployLink.Kind kind,
                                    String toToken, String label) {
    }

    /** 解析中の可変状態をまとめた作業用コンテキスト (parse 本体のメソッド長を抑える)。 */
    private static final class ParseCtx {
        final DeploySketchModel model = new DeploySketchModel();
        final List<String> unsupported = new ArrayList<>();
        final Map<String, int[]> positions = new HashMap<>();
        /** 引用符ラベル → id の逆引き (エイリアス無し宣言をリンク端点から解決するため)。 */
        final Map<String, String> labelToId = new HashMap<>();
        /** 現在の入れ子コンテナ (先頭が最も内側)。空ならトップレベル。 */
        final Deque<DeployNode> containerStack = new ArrayDeque<>();
        /** 未対応ブロックの中にいるときの残り深さ (0 ならブロック外)。 */
        int unknownBlockDepth;
        /**
         * 1 パス目で読んだリンクを保留するキュー。宣言 (ノード/入れ子) より前に
         * 書かれたリンクが labelToId 未登録のまま解決され、幽霊ノードの二重生成や
         * 入れ子子ノードのトップレベル残留を招くのを防ぐため、全宣言確定後の
         * 2 パス目でまとめて解決する。
         */
        final List<PendingRelation> pendingRelations = new ArrayList<>();
    }

    /** PlantUML テキストを配置図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        ParseCtx ctx = new ParseCtx();
        for (String raw : (text == null ? "" : text).split("\n", -1)) {
            parseLine(ctx, raw.trim());
        }
        // 2 パス目: 全ノード宣言 (入れ子含む) が確定した後にリンク端点を解決する。
        for (PendingRelation p : ctx.pendingRelations) {
            String from = resolveEndpoint(ctx, p.fromToken());
            String to = resolveEndpoint(ctx, p.toToken());
            ctx.model.getLinks().add(new DeployLink(from, p.kind(), to, p.label()));
        }
        applyPositions(ctx.model.getNodes(), ctx.positions);
        return new ParseResult(ctx.model, ctx.unsupported);
    }

    /** 1 行を解析してコンテキストへ反映する。 */
    private static void parseLine(ParseCtx ctx, String line) {
        if (line.startsWith("@startuml")) {
            String name = line.substring("@startuml".length()).trim();
            if (!name.isEmpty()) {
                ctx.model.setDiagramName(name);
            }
            return;
        }
        if (line.isEmpty() || line.equals("@enduml")) {
            return;
        }
        if (ctx.unknownBlockDepth > 0) {
            // 未対応ブロックの中: 中身をまるごと保全しつつ、対応する閉じ括弧まで深さを追う。
            ctx.unsupported.add(line);
            if (line.endsWith("{")) {
                ctx.unknownBlockDepth++;
            } else if (line.equals("}")) {
                ctx.unknownBlockDepth--;
            }
            return;
        }
        if (line.equals("}")) {
            if (!ctx.containerStack.isEmpty()) {
                ctx.containerStack.pop();
            } else {
                // 対応する開きの無い浮いた閉じ括弧: 往復できないため未対応として保全する。
                ctx.unsupported.add(line);
            }
            return;
        }
        Matcher pos = POS.matcher(line);
        if (pos.matches()) {
            parsePos(ctx, pos);
            return;
        }
        if (line.startsWith("'")) {
            ctx.unsupported.add(line);
            return;
        }
        boolean opensBlock = line.endsWith("{");
        String head = opensBlock ? line.substring(0, line.length() - 1).trim() : line;
        DeployNode parent = ctx.containerStack.peek();
        DeployNode declared = matchDeclarationHead(ctx, head, parent);
        if (declared != null) {
            if (opensBlock) {
                declared.setContainer(true);
                ctx.containerStack.push(declared);
            }
            return;
        }
        if (!opensBlock) {
            Matcher rel = RELATION.matcher(line);
            if (rel.matches()) {
                parseRelation(ctx, rel);
                return;
            }
        }
        // 未知のキーワード/構文は往復できないため編集をロックする。ブロックを開いていれば
        // 対応する閉じ括弧まで丸ごと未対応として保全する。
        ctx.unsupported.add(line);
        if (opensBlock) {
            ctx.unknownBlockDepth = 1;
        }
    }

    private static void parsePos(ParseCtx ctx, Matcher pos) {
        Integer px = parseIntSafe(pos.group(2));
        Integer py = parseIntSafe(pos.group(3));
        if (px == null || py == null) {
            ctx.unsupported.add(pos.group(0));
        } else {
            ctx.positions.put(pos.group(1), new int[]{px, py});
        }
    }

    private static void parseRelation(ParseCtx ctx, Matcher rel) {
        DeployLink.Kind kind = DeployLink.Kind.fromArrow(rel.group(2));
        // 端点解決は全ノード宣言が確定してから (2 パス目) 行う。ここでは保留するだけ。
        ctx.pendingRelations.add(new PendingRelation(rel.group(1), kind, rel.group(3), rel.group(4)));
    }

    /**
     * 宣言行の頭部 (末尾 {@code {} を除いた部分) をノード宣言としてマッチさせ、
     * モデルへ反映する。マッチしなければ null。
     */
    private static DeployNode matchDeclarationHead(ParseCtx ctx, String head, DeployNode parent) {
        Matcher alias = ALIAS.matcher(head);
        if (alias.matches()) {
            DeployNode.Kind kind = DeployNode.Kind.fromKeyword(alias.group(1));
            String label = alias.group(2);
            String id = alias.group(3);
            DeployNode n = declareNode(ctx.model, kind, id, label, parent);
            ctx.labelToId.put(label, id);
            return n;
        }
        Matcher anon = ANON_QUOTED.matcher(head);
        if (anon.matches()) {
            DeployNode.Kind kind = DeployNode.Kind.fromKeyword(anon.group(1));
            return declareAnon(ctx, kind, anon.group(2), parent);
        }
        Matcher decl = DECL_LOOSE.matcher(head);
        if (decl.matches()) {
            DeployNode.Kind kind = DeployNode.Kind.fromKeyword(decl.group(1));
            String token = decl.group(2);
            if (BARE_ID_ONLY.matcher(token).matches()) {
                return declareNode(ctx.model, kind, token, null, parent);
            }
            // '.' や '-' を含む素の語 (例: app.war) は id として合成し直し、元の表記は
            // 表示名として保つ (再生成時は引用符 + 明示エイリアス形式になる)。
            return declareAnon(ctx, kind, token, parent);
        }
        return null;
    }

    private static DeployNode declareAnon(ParseCtx ctx, DeployNode.Kind kind,
                                          String label, DeployNode parent) {
        String id = ctx.model.uniqueId(sanitizeBase(label));
        DeployNode n = new DeployNode(kind, id, label, 0, 0);
        if (parent != null) {
            ctx.model.addChild(parent, n);
        } else {
            ctx.model.getNodes().add(n);
        }
        ctx.labelToId.put(label, id);
        return n;
    }

    private static DeployNode declareNode(DeploySketchModel model, DeployNode.Kind kind,
                                          String id, String label, DeployNode parent) {
        DeployNode n = model.findNode(id);
        if (n == null) {
            n = new DeployNode(kind, id, label, 0, 0);
            if (parent != null) {
                model.addChild(parent, n);
            } else {
                model.getNodes().add(n);
            }
        } else {
            n.setKind(kind);
            if (label != null) {
                n.setLabel(label);
            }
        }
        return n;
    }

    /** リンク端点の記法 (素の id / 引用符ラベル) を id へ解決し、未宣言なら暗黙生成する。 */
    private static String resolveEndpoint(ParseCtx ctx, String token) {
        if (token.startsWith("\"")) {
            String label = token.substring(1, token.length() - 1);
            String id = ctx.labelToId.get(label);
            if (id == null) {
                id = ctx.model.uniqueId(sanitizeBase(label));
                ctx.model.getNodes().add(new DeployNode(DeployNode.Kind.NODE, id, label, 0, 0));
                ctx.labelToId.put(label, id);
            }
            return id;
        }
        if (ctx.model.findNode(token) == null) {
            ctx.model.getNodes().add(new DeployNode(DeployNode.Kind.NODE, token, null, 0, 0));
        }
        return token;
    }

    /** 表示名から合成 id のベースを作る (識別子として不正な文字を除去)。 */
    private static String sanitizeBase(String label) {
        String s = label.replaceAll("[^A-Za-z0-9_$]", "");
        if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) {
            s = "N" + s;
        }
        return s;
    }

    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 明示座標 ({@code '@pos}) を反映し、無指定のノードは兄弟単位 (同じ階層) で
     * 格子状に自動配置する。入れ子ノードの座標は親の内側原点からの相対値。
     */
    private static void applyPositions(List<DeployNode> siblings, Map<String, int[]> positions) {
        applyPositions(siblings, positions, false);
    }

    /**
     * {@code clampNonNegative} が true (= {@code siblings} が誰かの子、つまり相対座標) の
     * ときだけ、明示座標を非負へクランプして適用する。トップレベル (絶対座標) は従来どおり
     * そのまま反映する。
     *
     * <p>入れ子子ノードの相対座標は GUI ドラッグでは {@code Math.max(0, ..)} で常に非負に
     * 丸められるが、手編集テキストの {@code '@pos child -30 -20} (POS 正規表現 {@code -?\d+}
     * は負値も受理する) からは負の相対座標が到達しうる。負の相対座標がモデルへ入ると、枠拡張
     * (旧 minLeft/minTop) と contentOrigin/タイトル描画/子ドラッグの原点計算が食い違い、
     * 座標ジャンプ・タイトル重なり・往復での誤座標永続化を招く (bug-hunt round5/round6)。
     * ここで load 時に一度だけ 0 へクランプしてモデルへ確定させることで、以降のレイアウト・
     * 描画・ドラッグ・再シリアライズはすべて非負の一貫した値だけを見ればよくなり、これらの
     * バグの発生条件自体が消える (初回ロードで正規化・2 回目以降は固定点、という既存の
     * 往復流儀に沿う)。</p>
     */
    private static void applyPositions(List<DeployNode> siblings, Map<String, int[]> positions,
                                        boolean clampNonNegative) {
        int auto = 0;
        for (DeployNode n : siblings) {
            int[] p = positions.get(n.getId());
            if (p != null) {
                int x = clampNonNegative ? Math.max(0, p[0]) : p[0];
                int y = clampNonNegative ? Math.max(0, p[1]) : p[1];
                n.moveTo(x, y);
            } else {
                n.moveTo(GRID_MARGIN + (auto % GRID_COLS) * GRID_X,
                        GRID_MARGIN + (auto / GRID_COLS) * GRID_Y);
                auto++;
            }
            // 子は必ず親からの相対座標 (誰の子であっても非負へクランプする)。
            applyPositions(n.getChildren(), positions, true);
        }
    }

    /** モデルを PlantUML テキストへ書き出す (座標は {@code '@pos} コメントで保存)。 */
    public static String toPuml(DeploySketchModel model) {
        StringBuilder sb = new StringBuilder("@startuml");
        if (!model.getDiagramName().isEmpty()) {
            sb.append(' ').append(model.getDiagramName());
        }
        sb.append('\n');
        appendNodes(sb, model.getNodes(), 0);
        if (!model.getLinks().isEmpty()) {
            sb.append('\n');
            for (DeployLink l : model.getLinks()) {
                sb.append(l.getFrom()).append(' ').append(l.getKind().arrow())
                        .append(' ').append(l.getTo());
                if (l.getLabel() != null && !l.getLabel().isEmpty()) {
                    sb.append(" : ").append(l.getLabel());
                }
                sb.append('\n');
            }
        }
        List<DeployNode> all = model.allNodes();
        if (!all.isEmpty()) {
            sb.append('\n');
            for (DeployNode n : all) {
                sb.append("'@pos ").append(n.getId()).append(' ')
                        .append(n.getX()).append(' ').append(n.getY()).append('\n');
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }

    /** ノード宣言を (入れ子なら再帰的に) 書き出す。 */
    private static void appendNodes(StringBuilder sb, List<DeployNode> list, int indent) {
        String pad = INDENT_UNIT.repeat(indent);
        for (DeployNode n : list) {
            sb.append(pad).append(n.getKind().keyword()).append(' ');
            boolean hasLabel = n.getLabel() != null && !n.getLabel().isEmpty()
                    && !n.getLabel().equals(n.getId());
            if (hasLabel) {
                sb.append('"').append(n.getLabel()).append("\" as ").append(n.getId());
            } else {
                sb.append(n.getId());
            }
            if (n.isContainer()) {
                sb.append(" {\n");
                appendNodes(sb, n.getChildren(), indent + 1);
                sb.append(pad).append("}\n");
            } else {
                sb.append('\n');
            }
        }
    }
}
