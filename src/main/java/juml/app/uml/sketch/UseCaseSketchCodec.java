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
 * {@link UseCaseSketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文はユースケース図の基本要素に限定する:
 * {@code actor} / {@code usecase} 宣言 (素の id、または {@code "表示名" as id})、
 * 関係 {@link UseCaseRelation.Kind} 3 種 ({@code -->} / {@code ..>} / {@code --|>})、
 * レイアウト座標コメント ({@code '@pos id x y})。境界 ({@code rectangle X { … }})・
 * 向き指定・短縮記法 ({@code (…)} / {@code :…:})・一般コメントなどそれ以外の非空行は
 * 「未対応」として報告し、呼び出し側 (GUI デザイナー) は編集を無効化してテキストを守る。</p>
 */
public final class UseCaseSketchCodec {

    private static final Pattern POS = Pattern.compile(
            "^'@pos\\s+(\\S+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*$");
    private static final Pattern ACTOR_DECL = Pattern.compile(
            "^actor\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern ACTOR_ALIAS = Pattern.compile(
            "^actor\\s+\"([^\"]*)\"\\s+as\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern USECASE_DECL = Pattern.compile(
            "^usecase\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern USECASE_ALIAS = Pattern.compile(
            "^usecase\\s+\"([^\"]*)\"\\s+as\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern RELATION = Pattern.compile(
            "^([A-Za-z_$][\\w$]*)\\s*(-->|\\.\\.>|--\\|>)\\s*"
                    + "([A-Za-z_$][\\w$]*)(?:\\s*:\\s*(.*\\S))?\\s*$");

    private static final int GRID_X = 200;
    private static final int GRID_Y = 120;
    private static final int GRID_COLS = 3;
    private static final int GRID_MARGIN = 50;

    private UseCaseSketchCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final UseCaseSketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(UseCaseSketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** PlantUML テキストをユースケース図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        UseCaseSketchModel model = new UseCaseSketchModel();
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
                UseCaseRelation.Kind kind = UseCaseRelation.Kind.fromArrow(rel.group(2));
                obtainNode(model, rel.group(1));
                obtainNode(model, rel.group(3));
                model.getRelations().add(new UseCaseRelation(
                        rel.group(1), kind, rel.group(3), rel.group(4)));
                continue;
            }
            // 境界 (rectangle X {)・向き指定・短縮記法などは往復できないため編集をロックする。
            unsupported.add(line);
        }
        applyPositions(model, positions);
        return new ParseResult(model, unsupported);
    }

    /** actor/usecase 宣言 (素 / 別名) にマッチしたらノードを追加し true を返す。 */
    private static boolean matchDeclaration(UseCaseSketchModel model, String line) {
        Matcher aa = ACTOR_ALIAS.matcher(line);
        if (aa.matches()) {
            obtain(model, UseCaseNode.Kind.ACTOR, aa.group(2), aa.group(1));
            return true;
        }
        Matcher ad = ACTOR_DECL.matcher(line);
        if (ad.matches()) {
            obtain(model, UseCaseNode.Kind.ACTOR, ad.group(1), null);
            return true;
        }
        Matcher ua = USECASE_ALIAS.matcher(line);
        if (ua.matches()) {
            obtain(model, UseCaseNode.Kind.USECASE, ua.group(2), ua.group(1));
            return true;
        }
        Matcher ud = USECASE_DECL.matcher(line);
        if (ud.matches()) {
            obtain(model, UseCaseNode.Kind.USECASE, ud.group(1), null);
            return true;
        }
        return false;
    }

    private static void obtain(UseCaseSketchModel model, UseCaseNode.Kind kind,
                               String id, String label) {
        UseCaseNode n = model.findNode(id);
        if (n == null) {
            model.getNodes().add(new UseCaseNode(kind, id, label, 0, 0));
        } else {
            n.setKind(kind);
            if (label != null) {
                n.setLabel(label);
            }
        }
    }

    /** 関係の端点用: 未宣言なら既定でユースケースとして暗黙生成する。 */
    private static void obtainNode(UseCaseSketchModel model, String id) {
        if (model.findNode(id) == null) {
            model.getNodes().add(new UseCaseNode(UseCaseNode.Kind.USECASE, id, null, 0, 0));
        }
    }

    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void applyPositions(UseCaseSketchModel model, Map<String, int[]> positions) {
        int auto = 0;
        for (UseCaseNode n : model.getNodes()) {
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
    public static String toPuml(UseCaseSketchModel model) {
        StringBuilder sb = new StringBuilder("@startuml");
        if (!model.getDiagramName().isEmpty()) {
            sb.append(' ').append(model.getDiagramName());
        }
        sb.append('\n');
        for (UseCaseNode n : model.getNodes()) {
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
            for (UseCaseRelation r : model.getRelations()) {
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
            for (UseCaseNode n : model.getNodes()) {
                sb.append("'@pos ").append(n.getId()).append(' ')
                        .append(n.getX()).append(' ').append(n.getY()).append('\n');
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }
}
