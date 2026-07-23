// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link DeploySketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文は配置図の基本要素 (フラット構成) に限定する:
 * ノード宣言 8 種 ({@code node} / {@code artifact} / {@code database} / {@code cloud} /
 * {@code component} / {@code rectangle} / {@code folder} / {@code frame}) の素の id 形式
 * および {@code "表示名" as id} 形式、リンク 3 種 ({@code -->} / {@code ..>} / {@code --})
 * ({@code : label} 付き・自己リンク可)、レイアウト座標コメント ({@code '@pos id x y})。
 * 入れ子コンテナ ({@code node X { ... }})・引用符だけの宣言 ({@code node "..."})・
 * その他未対応構文は「未対応」として報告し編集をロックしテキストを守る。</p>
 */
public final class DeploySketchCodec {

    /** ノード宣言キーワードの選択肢 (正規表現片)。 */
    private static final String KW =
            "node|artifact|database|cloud|component|rectangle|folder|frame";

    private static final Pattern POS = Pattern.compile(
            "^'@pos\\s+(\\S+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*$");
    private static final Pattern DECL = Pattern.compile(
            "^(" + KW + ")\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern ALIAS = Pattern.compile(
            "^(" + KW + ")\\s+\"([^\"]*)\"\\s+as\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final String ENDPOINT = "[A-Za-z_$][\\w$]*";
    private static final Pattern RELATION = Pattern.compile(
            "^(" + ENDPOINT + ")\\s*(-->|\\.\\.>|--)\\s*(" + ENDPOINT + ")"
                    + "(?:\\s*:\\s*(.*\\S))?\\s*$");

    private static final int GRID_X = 200;
    private static final int GRID_Y = 120;
    private static final int GRID_COLS = 3;
    private static final int GRID_MARGIN = 50;

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

    /** PlantUML テキストを配置図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        DeploySketchModel model = new DeploySketchModel();
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
                DeployLink.Kind kind = DeployLink.Kind.fromArrow(rel.group(2));
                String from = rel.group(1);
                String to = rel.group(3);
                obtainNode(model, from);
                obtainNode(model, to);
                model.getLinks().add(new DeployLink(from, kind, to, rel.group(4)));
                continue;
            }
            // 入れ子コンテナ・引用符だけの宣言・他構文は往復できないため編集をロックする。
            unsupported.add(line);
        }
        applyPositions(model, positions);
        return new ParseResult(model, unsupported);
    }

    /** ノード宣言 (素 / 別名) にマッチしたらノードを追加し true を返す。 */
    private static boolean matchDeclaration(DeploySketchModel model, String line) {
        Matcher alias = ALIAS.matcher(line);
        if (alias.matches()) {
            obtain(model, DeployNode.Kind.fromKeyword(alias.group(1)),
                    alias.group(3), alias.group(2));
            return true;
        }
        Matcher decl = DECL.matcher(line);
        if (decl.matches()) {
            obtain(model, DeployNode.Kind.fromKeyword(decl.group(1)),
                    decl.group(2), null);
            return true;
        }
        return false;
    }

    private static void obtain(DeploySketchModel model, DeployNode.Kind kind,
                               String id, String label) {
        DeployNode n = model.findNode(id);
        if (n == null) {
            model.getNodes().add(new DeployNode(kind, id, label, 0, 0));
        } else {
            n.setKind(kind);
            if (label != null) {
                n.setLabel(label);
            }
        }
    }

    /** リンクの端点用: 未宣言なら既定でノードとして暗黙生成する。 */
    private static void obtainNode(DeploySketchModel model, String id) {
        if (model.findNode(id) == null) {
            model.getNodes().add(new DeployNode(DeployNode.Kind.NODE, id, null, 0, 0));
        }
    }

    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void applyPositions(DeploySketchModel model, Map<String, int[]> positions) {
        int auto = 0;
        for (DeployNode n : model.getNodes()) {
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
    public static String toPuml(DeploySketchModel model) {
        StringBuilder sb = new StringBuilder("@startuml");
        if (!model.getDiagramName().isEmpty()) {
            sb.append(' ').append(model.getDiagramName());
        }
        sb.append('\n');
        for (DeployNode n : model.getNodes()) {
            sb.append(n.getKind().keyword()).append(' ');
            if (n.getLabel() != null && !n.getLabel().isEmpty()
                    && !n.getLabel().equals(n.getId())) {
                sb.append('"').append(n.getLabel()).append("\" as ").append(n.getId());
            } else {
                sb.append(n.getId());
            }
            sb.append('\n');
        }
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
        if (!model.getNodes().isEmpty()) {
            sb.append('\n');
            for (DeployNode n : model.getNodes()) {
                sb.append("'@pos ").append(n.getId()).append(' ')
                        .append(n.getX()).append(' ').append(n.getY()).append('\n');
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }
}
