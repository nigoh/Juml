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
 * {@link SketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文はクラス図の基本要素に限定する:
 * {@code class / abstract class / interface / enum} 宣言 (メンバー付き)、
 * {@link SketchRelation.Kind} の 7 種の関係、レイアウト座標コメント
 * ({@code '@pos Name x y})。それ以外の非空行は「未対応」として報告し、
 * 呼び出し側 (GUI デザイナー) は編集を無効化してテキストを壊さないようにする。</p>
 */
public final class SketchPumlCodec {

    /** 座標コメントの書式: {@code '@pos Name x y}。 */
    private static final Pattern POS = Pattern.compile(
            "^'@pos\\s+(\\S+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*$");
    private static final Pattern CLASS_DECL = Pattern.compile(
            "^(abstract\\s+)?(class|interface|enum)\\s+([A-Za-z_$][\\w$.]*)\\s*(\\{)?\\s*$");
    // 長い矢印表記を先に並べる (-- が --> の前置と衝突しないように)。
    private static final Pattern RELATION = Pattern.compile(
            "^([A-Za-z_$][\\w$.]*)\\s*(<\\|--|<\\|\\.\\.|o--|\\*--|-->|\\.\\.>|--)"
                    + "\\s*([A-Za-z_$][\\w$.]*)(?:\\s*:\\s*(.*\\S))?\\s*$");

    /** 位置未指定クラスを格子状に自動配置する際の間隔。 */
    private static final int GRID_X = 240;
    private static final int GRID_Y = 170;
    private static final int GRID_COLS = 3;
    private static final int GRID_MARGIN = 40;

    private SketchPumlCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final SketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(SketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** PlantUML テキストをクラス図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        SketchModel model = new SketchModel();
        List<String> unsupported = new ArrayList<>();
        Map<String, int[]> positions = new HashMap<>();
        String[] lines = (text == null ? "" : text).split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            i++;
            if (line.isEmpty() || line.startsWith("@startuml") || line.equals("@enduml")) {
                continue;
            }
            Matcher pos = POS.matcher(line);
            if (pos.matches()) {
                Integer px = parseIntSafe(pos.group(2));
                Integer py = parseIntSafe(pos.group(3));
                if (px == null || py == null) {
                    // int 範囲外の座標はモデル化できない。未対応として編集をロックし、
                    // 例外で Design タブ切替を壊さずテキストを保全する。
                    unsupported.add(line);
                } else {
                    positions.put(pos.group(1), new int[]{px, py});
                }
                continue;
            }
            if (line.startsWith("'")) {
                // '@pos 以外の一般コメントはモデル化できず、GUI 編集で再生成すると失われる。
                // isFullySupported() は「編集してもテキストを失わない」ことを表すので、
                // コメントがあるときは未対応として扱い、デザイナー編集を無効化して
                // ユーザーのコメントを黙って消さないようにする。
                unsupported.add(line);
                continue;
            }
            Matcher decl = CLASS_DECL.matcher(line);
            if (decl.matches()) {
                SketchClass.Kind kind = kindOf(decl.group(1), decl.group(2));
                SketchClass c = obtainClass(model, decl.group(3));
                c.setKind(kind);
                if (decl.group(1) != null && !"class".equals(decl.group(2))) {
                    // 'abstract interface' / 'abstract enum' はモデルの種別 (INTERFACE/ENUM)
                    // で表現できず、GUI 再生成すると abstract 修飾子が黙って失われる。
                    // 未対応行として積み、デザイナー編集を無効化してテキストを保全する。
                    unsupported.add(line);
                }
                if (decl.group(4) != null) {
                    i = readMembers(lines, i, c, unsupported);
                }
                continue;
            }
            Matcher rel = RELATION.matcher(line);
            if (rel.matches()) {
                SketchRelation.Kind kind = SketchRelation.Kind.fromArrow(rel.group(2));
                // 関係の端点が未宣言なら PlantUML と同様に暗黙のクラスとして扱う。
                obtainClass(model, rel.group(1));
                obtainClass(model, rel.group(3));
                model.getRelations().add(new SketchRelation(
                        rel.group(1), kind, rel.group(3), rel.group(4)));
                continue;
            }
            unsupported.add(line);
        }
        applyPositions(model, positions);
        return new ParseResult(model, unsupported);
    }

    private static SketchClass.Kind kindOf(String abstractPrefix, String keyword) {
        if ("interface".equals(keyword)) {
            return SketchClass.Kind.INTERFACE;
        }
        if ("enum".equals(keyword)) {
            return SketchClass.Kind.ENUM;
        }
        return abstractPrefix != null ? SketchClass.Kind.ABSTRACT : SketchClass.Kind.CLASS;
    }

    private static SketchClass obtainClass(SketchModel model, String name) {
        SketchClass c = model.findClass(name);
        if (c == null) {
            c = new SketchClass(name, SketchClass.Kind.CLASS, 0, 0);
            model.getClasses().add(c);
        }
        return c;
    }

    /** PlantUML のクラス内区切り線 ({@code --} / {@code ==} / {@code __} / {@code ..})。 */
    private static final Pattern MEMBER_SEPARATOR = Pattern.compile("^(--|==|__|\\.\\.).*$");

    /**
     * {@code {} } ブロック内のメンバー行を読み、閉じ括弧の次の行番号を返す。
     *
     * <p>{@link #toPuml} は「全フィールド → 全メソッド」の順で再生成するため、原文が
     * メソッドとフィールドを交互に書いていたり区切り線 ({@code --} 等) を含むと、GUI 編集で
     * 並びが崩れる。そうした<em>往復で並びが変質する本体</em>は {@code unsupported} へ積んで
     * 編集をロックし、テキストの並びを保全する (isFullySupported()==true の「編集しても
     * テキストを失わない」契約を守る)。区切り線はフィールドとして誤分類されるため特に危険。</p>
     */
    private static int readMembers(String[] lines, int start, SketchClass c,
                                   List<String> unsupported) {
        int i = start;
        boolean sawMethod = false;
        boolean reorderRisk = false;
        while (i < lines.length) {
            String line = lines[i].trim();
            // 図境界ディレクティブはメンバーではない。閉じ括弧が欠けたまま
            // @enduml/@startuml に達したら、これらを吸い込んで破損させないよう
            // 消費せずにブロックを打ち切る (外側ループが処理する)。
            if (line.equals("@enduml") || line.startsWith("@startuml")) {
                break;
            }
            i++;
            if (line.equals("}")) {
                break;
            }
            if (line.isEmpty()) {
                continue;
            }
            // 括弧を含む行はメソッド、それ以外 (区切り線 -- を含む) はフィールド扱い。
            if (line.contains("(")) {
                c.getMethods().add(line);
                sawMethod = true;
            } else {
                // 区切り線、またはメソッドの後に来るフィールド (交互配置) は再生成で並びが崩れる。
                if (MEMBER_SEPARATOR.matcher(line).matches() || sawMethod) {
                    reorderRisk = true;
                }
                c.getFields().add(line);
            }
        }
        if (reorderRisk) {
            // 並びが往復で崩れる本体を含むクラスは編集不可にして原文を保全する。
            unsupported.add(c.getName() + " {…}");
        }
        return i;
    }

    /** int 範囲外・不正な整数は null を返す安全パース ({@code '@pos} 座標のクラッシュ防止)。 */
    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 明示座標を反映し、座標の無いクラスは格子状に自動配置する。 */
    private static void applyPositions(SketchModel model, Map<String, int[]> positions) {
        int auto = 0;
        for (SketchClass c : model.getClasses()) {
            int[] p = positions.get(c.getName());
            if (p != null) {
                c.moveTo(p[0], p[1]);
            } else {
                c.moveTo(GRID_MARGIN + (auto % GRID_COLS) * GRID_X,
                        GRID_MARGIN + (auto / GRID_COLS) * GRID_Y);
                auto++;
            }
        }
    }

    /** モデルを PlantUML テキストへ書き出す (座標は {@code '@pos} コメントで保存)。 */
    public static String toPuml(SketchModel model) {
        StringBuilder sb = new StringBuilder("@startuml\n");
        for (SketchClass c : model.getClasses()) {
            sb.append(c.getKind().keyword()).append(' ').append(c.getName());
            if (c.getFields().isEmpty() && c.getMethods().isEmpty()) {
                sb.append('\n');
            } else {
                sb.append(" {\n");
                for (String f : c.getFields()) {
                    sb.append("  ").append(f).append('\n');
                }
                for (String m : c.getMethods()) {
                    sb.append("  ").append(m).append('\n');
                }
                sb.append("}\n");
            }
        }
        if (!model.getRelations().isEmpty()) {
            sb.append('\n');
            for (SketchRelation r : model.getRelations()) {
                sb.append(r.getLeft()).append(' ').append(r.getKind().arrow())
                        .append(' ').append(r.getRight());
                if (r.getLabel() != null && !r.getLabel().isEmpty()) {
                    sb.append(" : ").append(r.getLabel());
                }
                sb.append('\n');
            }
        }
        if (!model.getClasses().isEmpty()) {
            sb.append('\n');
            for (SketchClass c : model.getClasses()) {
                sb.append("'@pos ").append(c.getName()).append(' ')
                        .append(c.getX()).append(' ').append(c.getY()).append('\n');
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }
}
