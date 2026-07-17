// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link ComponentSketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文はコンポーネント図の基本要素に限定する:
 * {@code component} / {@code interface} 宣言 (素の id・{@code "表示名" as id}・
 * コンポーネントの短縮形 {@code [id]})、関係 {@link ComponentRelation.Kind} 3 種
 * ({@code -->} / {@code ..>} / {@code --})、レイアウト座標コメント ({@code '@pos id x y})。
 * パッケージ境界・{@code database}/{@code node}/{@code cloud} など他要素・空白入りの
 * {@code [名前]}・一般コメントは「未対応」として報告し編集をロックしテキストを守る。</p>
 */
public final class ComponentSketchCodec {

    private static final Pattern POS = Pattern.compile(
            "^'@pos\\s+(\\S+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*$");
    private static final Pattern COMP_BRACKET = Pattern.compile(
            "^\\[([A-Za-z_$][\\w$]*)\\]\\s*$");
    private static final Pattern COMP_DECL = Pattern.compile(
            "^component\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern COMP_ALIAS = Pattern.compile(
            "^component\\s+\"([^\"]*)\"\\s+as\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern IFACE_DECL = Pattern.compile(
            "^interface\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern IFACE_ALIAS = Pattern.compile(
            "^interface\\s+\"([^\"]*)\"\\s+as\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final String ENDPOINT = "(\\[[A-Za-z_$][\\w$]*\\]|[A-Za-z_$][\\w$]*)";
    private static final Pattern RELATION = Pattern.compile(
            "^" + ENDPOINT + "\\s*(-->|\\.\\.>|--)\\s*" + ENDPOINT
                    + "(?:\\s*:\\s*(.*\\S))?\\s*$");

    private static final int GRID_X = 200;
    private static final int GRID_Y = 120;
    private static final int GRID_COLS = 3;
    private static final int GRID_MARGIN = 50;

    private ComponentSketchCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final ComponentSketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(ComponentSketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** PlantUML テキストをコンポーネント図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        ComponentSketchModel model = new ComponentSketchModel();
        List<String> unsupported = new ArrayList<>();
        Map<String, int[]> positions = new HashMap<>();
        for (String raw : (text == null ? "" : text).split("\n", -1)) {
            String line = raw.trim();
            if (line.startsWith("@startuml")) {
                String name = line.substring("@startuml".length()).trim();
                if (!name.isEmpty()) {
                    model.setDiagramName(name);
                }
                continue;
            }
            if (line.isEmpty() || line.equals("@enduml")) {
                continue;
            }
            Matcher pos = POS.matcher(line);
            if (pos.matches()) {
                Integer px = parseIntSafe(pos.group(2));
                Integer py = parseIntSafe(pos.group(3));
                if (px == null || py == null) {
                    unsupported.add(line);
                } else {
                    positions.put(pos.group(1), new int[]{px, py});
                }
                continue;
            }
            if (line.startsWith("'")) {
                unsupported.add(line);
                continue;
            }
            if (matchDeclaration(model, line)) {
                continue;
            }
            Matcher rel = RELATION.matcher(line);
            if (rel.matches()) {
                ComponentRelation.Kind kind = ComponentRelation.Kind.fromArrow(rel.group(2));
                String from = stripBrackets(rel.group(1));
                String to = stripBrackets(rel.group(3));
                obtainNode(model, from);
                obtainNode(model, to);
                model.getRelations().add(new ComponentRelation(from, kind, to, rel.group(4)));
                continue;
            }
            // パッケージ境界・他要素・空白入り [名前] などは往復できないため編集をロックする。
            unsupported.add(line);
        }
        applyPositions(model, positions);
        return new ParseResult(model, unsupported);
    }

    /** component/interface 宣言 (素 / 別名 / [id]) にマッチしたらノードを追加し true を返す。 */
    private static boolean matchDeclaration(ComponentSketchModel model, String line) {
        Matcher cb = COMP_BRACKET.matcher(line);
        if (cb.matches()) {
            obtain(model, ComponentNode.Kind.COMPONENT, cb.group(1), null);
            return true;
        }
        Matcher ca = COMP_ALIAS.matcher(line);
        if (ca.matches()) {
            obtain(model, ComponentNode.Kind.COMPONENT, ca.group(2), ca.group(1));
            return true;
        }
        Matcher cd = COMP_DECL.matcher(line);
        if (cd.matches()) {
            obtain(model, ComponentNode.Kind.COMPONENT, cd.group(1), null);
            return true;
        }
        Matcher ia = IFACE_ALIAS.matcher(line);
        if (ia.matches()) {
            obtain(model, ComponentNode.Kind.INTERFACE, ia.group(2), ia.group(1));
            return true;
        }
        Matcher id = IFACE_DECL.matcher(line);
        if (id.matches()) {
            obtain(model, ComponentNode.Kind.INTERFACE, id.group(1), null);
            return true;
        }
        return false;
    }

    private static void obtain(ComponentSketchModel model, ComponentNode.Kind kind,
                               String id, String label) {
        ComponentNode n = model.findNode(id);
        if (n == null) {
            model.getNodes().add(new ComponentNode(kind, id, label, 0, 0));
        } else {
            n.setKind(kind);
            if (label != null) {
                n.setLabel(label);
            }
        }
    }

    /** 関係の端点用: 未宣言なら既定でコンポーネントとして暗黙生成する。 */
    private static void obtainNode(ComponentSketchModel model, String id) {
        if (model.findNode(id) == null) {
            model.getNodes().add(new ComponentNode(
                    ComponentNode.Kind.COMPONENT, id, null, 0, 0));
        }
    }

    private static String stripBrackets(String s) {
        if (s.length() >= 2 && s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']') {
            return s.substring(1, s.length() - 1);
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

    private static void applyPositions(ComponentSketchModel model, Map<String, int[]> positions) {
        int auto = 0;
        for (ComponentNode n : model.getNodes()) {
            int[] p = positions.get(n.getId());
            if (p != null) {
                n.moveTo(p[0], p[1]);
            } else {
                n.moveTo(GRID_MARGIN + (auto % GRID_COLS) * GRID_X,
                        GRID_MARGIN + (auto / GRID_COLS) * GRID_Y);
                auto++;
            }
        }
    }

    /** モデルを PlantUML テキストへ書き出す (座標は {@code '@pos} コメントで保存)。 */
    public static String toPuml(ComponentSketchModel model) {
        StringBuilder sb = new StringBuilder("@startuml");
        if (!model.getDiagramName().isEmpty()) {
            sb.append(' ').append(model.getDiagramName());
        }
        sb.append('\n');
        for (ComponentNode n : model.getNodes()) {
            sb.append(n.getKind().keyword()).append(' ');
            if (n.getLabel() != null && !n.getLabel().isEmpty()
                    && !n.getLabel().equals(n.getId())) {
                sb.append('"').append(n.getLabel()).append("\" as ").append(n.getId());
            } else {
                sb.append(n.getId());
            }
            sb.append('\n');
        }
        if (!model.getRelations().isEmpty()) {
            sb.append('\n');
            for (ComponentRelation r : model.getRelations()) {
                sb.append(r.getFrom()).append(' ').append(r.getKind().arrow())
                        .append(' ').append(r.getTo());
                if (r.getLabel() != null && !r.getLabel().isEmpty()) {
                    sb.append(" : ").append(r.getLabel());
                }
                sb.append('\n');
            }
        }
        if (!model.getNodes().isEmpty()) {
            sb.append('\n');
            for (ComponentNode n : model.getNodes()) {
                sb.append("'@pos ").append(n.getId()).append(' ')
                        .append(n.getX()).append(' ').append(n.getY()).append('\n');
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }
}
