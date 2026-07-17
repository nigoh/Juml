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
 * {@link StateSketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文は状態遷移図の基本要素に限定する:
 * {@code state Name} 宣言、{@code from --> to [: label]} 遷移 (端点は状態名または
 * 初期/終了の擬似状態 {@code [*]})、レイアウト座標コメント ({@code '@pos Name x y})。
 * 複合状態 ({@code state X { … }}) や別名宣言などそれ以外の非空行は「未対応」として
 * 報告し、呼び出し側 (GUI デザイナー) は編集を無効化してテキストを壊さないようにする。</p>
 */
public final class StateSketchCodec {

    private static final Pattern POS = Pattern.compile(
            "^'@pos\\s+(\\S+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*$");
    private static final Pattern STATE_DECL = Pattern.compile(
            "^state\\s+([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern TRANSITION = Pattern.compile(
            "^(\\[\\*\\]|[A-Za-z_$][\\w$]*)\\s*-->\\s*"
                    + "(\\[\\*\\]|[A-Za-z_$][\\w$]*)(?:\\s*:\\s*(.*\\S))?\\s*$");

    /** 位置未指定状態を格子状に自動配置する際の間隔。 */
    private static final int GRID_X = 200;
    private static final int GRID_Y = 130;
    private static final int GRID_COLS = 3;
    private static final int GRID_MARGIN = 50;

    private StateSketchCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final StateSketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(StateSketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** PlantUML テキストを状態遷移図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        StateSketchModel model = new StateSketchModel();
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
                // '@pos 以外の一般コメントはモデル化できず、GUI 再生成で失われるため未対応。
                unsupported.add(line);
                continue;
            }
            Matcher decl = STATE_DECL.matcher(line);
            if (decl.matches()) {
                obtainState(model, decl.group(1));
                continue;
            }
            Matcher tr = TRANSITION.matcher(line);
            if (tr.matches()) {
                String from = tr.group(1);
                String to = tr.group(2);
                // 擬似状態 [*] は状態ノードにしない。実状態の端点は暗黙生成する。
                if (!StateTransition.PSEUDO.equals(from)) {
                    obtainState(model, from);
                }
                if (!StateTransition.PSEUDO.equals(to)) {
                    obtainState(model, to);
                }
                model.getTransitions().add(new StateTransition(from, to, tr.group(3)));
                continue;
            }
            // 複合状態 (state X { …) やその他の状態記法は往復できないため編集をロックする。
            unsupported.add(line);
        }
        applyPositions(model, positions);
        return new ParseResult(model, unsupported);
    }

    private static StateNode obtainState(StateSketchModel model, String name) {
        StateNode s = model.findState(name);
        if (s == null) {
            s = new StateNode(name, 0, 0);
            model.getStates().add(s);
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

    /** 明示座標を反映し、座標の無い状態は格子状に自動配置する。 */
    private static void applyPositions(StateSketchModel model, Map<String, int[]> positions) {
        int auto = 0;
        for (StateNode s : model.getStates()) {
            int[] p = positions.get(s.getName());
            if (p != null) {
                s.moveTo(p[0], p[1]);
            } else {
                s.moveTo(GRID_MARGIN + (auto % GRID_COLS) * GRID_X,
                        GRID_MARGIN + (auto / GRID_COLS) * GRID_Y);
                auto++;
            }
        }
    }

    /** モデルを PlantUML テキストへ書き出す (座標は {@code '@pos} コメントで保存)。 */
    public static String toPuml(StateSketchModel model) {
        StringBuilder sb = new StringBuilder("@startuml");
        if (!model.getDiagramName().isEmpty()) {
            sb.append(' ').append(model.getDiagramName());
        }
        sb.append('\n');
        for (StateNode s : model.getStates()) {
            sb.append("state ").append(s.getName()).append('\n');
        }
        if (!model.getTransitions().isEmpty()) {
            sb.append('\n');
            for (StateTransition t : model.getTransitions()) {
                sb.append(t.getFrom()).append(" --> ").append(t.getTo());
                if (t.getLabel() != null && !t.getLabel().isEmpty()) {
                    sb.append(" : ").append(t.getLabel());
                }
                sb.append('\n');
            }
        }
        if (!model.getStates().isEmpty()) {
            sb.append('\n');
            for (StateNode s : model.getStates()) {
                sb.append("'@pos ").append(s.getName()).append(' ')
                        .append(s.getX()).append(' ').append(s.getY()).append('\n');
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }
}
