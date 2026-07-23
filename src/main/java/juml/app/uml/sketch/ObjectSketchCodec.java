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
 * {@link ObjectSketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文はオブジェクト図の基本要素に限定する:
 * {@code object 名前} 宣言 (任意の {@code <<ステレオタイプ>>})、属性
 * (ブロック形式 {@code object X { name = value }} と コロン形式 {@code X : name = value}
 * の双方を読む)、リンク {@link ObjectLink.Kind} 3 種 ({@code -->} / {@code --} / {@code ..>})、
 * レイアウト座標コメント ({@code '@pos 名前 x y})。それ以外の非空行 (一般コメント含む) は
 * 「未対応」として報告し、呼び出し側 (GUI デザイナー) は編集を無効化してテキストを壊さない
 * ようにする。再生成は常にコロン形式へ正規化する (2 回目以降の往復は固定点になる)。</p>
 */
public final class ObjectSketchCodec {

    /** 座標コメントの書式: {@code '@pos Name x y}。 */
    private static final Pattern POS = Pattern.compile(
            "^'@pos\\s+(\\S+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*$");
    /** {@code object 名前 [<<ステレオタイプ>>] [{]}。 */
    private static final Pattern OBJECT_DECL = Pattern.compile(
            "^object\\s+([A-Za-z_$][\\w$.]*)\\s*(?:<<\\s*(.*?)\\s*>>)?\\s*(\\{)?\\s*$");
    /** コロン形式の属性: {@code 名前 : 属性}。 */
    private static final Pattern ATTR = Pattern.compile(
            "^([A-Za-z_$][\\w$.]*)\\s*:\\s*(.*\\S)\\s*$");
    // 長い矢印表記を先に並べる (-- が --> の前置と衝突しないように)。
    private static final Pattern LINK = Pattern.compile(
            "^([A-Za-z_$][\\w$.]*)\\s*(-->|\\.\\.>|--)\\s*([A-Za-z_$][\\w$.]*)"
                    + "(?:\\s*:\\s*(.*\\S))?\\s*$");

    /** 位置未指定オブジェクトを格子状に自動配置する際の間隔。 */
    private static final int GRID_X = 220;
    private static final int GRID_Y = 150;
    private static final int GRID_COLS = 3;
    private static final int GRID_MARGIN = 40;

    private ObjectSketchCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final ObjectSketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(ObjectSketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** PlantUML テキストをオブジェクト図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        ObjectSketchModel model = new ObjectSketchModel();
        List<String> unsupported = new ArrayList<>();
        Map<String, int[]> positions = new HashMap<>();
        String[] lines = (text == null ? "" : text).split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            i++;
            if (line.startsWith("@startuml")) {
                // @startuml に付いた図名 (出力名) を保全する。捨てると GUI 編集の再生成で
                // ユーザーが書いた名前が黙って失われる。
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
                // '@pos 以外の一般コメントはモデル化できず、GUI 編集で再生成すると失われる。
                // 未対応として扱い、デザイナー編集を無効化してコメントを黙って消さない。
                unsupported.add(line);
                continue;
            }
            Matcher decl = OBJECT_DECL.matcher(line);
            if (decl.matches()) {
                ObjectInstance o = obtainObject(model, decl.group(1));
                String stereo = decl.group(2);
                if (stereo != null && !stereo.isEmpty()) {
                    o.setStereotype(stereo);
                }
                if (decl.group(3) != null) {
                    i = readAttributes(lines, i, o);
                }
                continue;
            }
            Matcher link = LINK.matcher(line);
            if (link.matches()) {
                ObjectLink.Kind kind = ObjectLink.Kind.fromArrow(link.group(2));
                obtainObject(model, link.group(1));
                obtainObject(model, link.group(3));
                model.getLinks().add(new ObjectLink(
                        link.group(1), kind, link.group(3), link.group(4)));
                continue;
            }
            Matcher attr = ATTR.matcher(line);
            if (attr.matches()) {
                obtainObject(model, attr.group(1)).getAttributes().add(attr.group(2));
                continue;
            }
            unsupported.add(line);
        }
        applyPositions(model, positions);
        return new ParseResult(model, unsupported);
    }

    /**
     * {@code {} } ブロック内の属性行を読み、閉じ括弧の次の行番号を返す。
     *
     * <p>属性は単一リストで保持し並べ替えないため、ブロック内の非空行はすべて属性として
     * 取り込む (クラス図のような field/method 再分類による並び崩れは起こらない)。閉じ括弧が
     * 無いまま {@code @enduml} / {@code @startuml} / 次の {@code object} に達したら、それらを
     * 吸い込んで破損させないよう消費せずに打ち切る (外側ループが処理する)。</p>
     */
    private static int readAttributes(String[] lines, int start, ObjectInstance o) {
        int i = start;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.equals("@enduml") || line.startsWith("@startuml")
                    || line.startsWith("object ")) {
                break;
            }
            i++;
            if (line.equals("}")) {
                break;
            }
            if (line.isEmpty()) {
                continue;
            }
            o.getAttributes().add(line);
        }
        return i;
    }

    private static ObjectInstance obtainObject(ObjectSketchModel model, String name) {
        ObjectInstance o = model.findObject(name);
        if (o == null) {
            o = new ObjectInstance(name, null, 0, 0);
            model.getObjects().add(o);
        }
        return o;
    }

    /** int 範囲外・不正な整数は null を返す安全パース ({@code '@pos} 座標のクラッシュ防止)。 */
    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 明示座標を反映し、座標の無いオブジェクトは格子状に自動配置する。 */
    private static void applyPositions(ObjectSketchModel model, Map<String, int[]> positions) {
        int auto = 0;
        for (ObjectInstance o : model.getObjects()) {
            int[] p = positions.get(o.getName());
            if (p != null) {
                o.moveTo(p[0], p[1]);
            } else {
                o.moveTo(GRID_MARGIN + (auto % GRID_COLS) * GRID_X,
                        GRID_MARGIN + (auto / GRID_COLS) * GRID_Y);
                auto++;
            }
        }
    }

    /** モデルを PlantUML テキストへ書き出す (座標は {@code '@pos} コメントで保存)。 */
    public static String toPuml(ObjectSketchModel model) {
        StringBuilder sb = new StringBuilder("@startuml");
        if (!model.getDiagramName().isEmpty()) {
            sb.append(' ').append(model.getDiagramName());
        }
        sb.append('\n');
        for (ObjectInstance o : model.getObjects()) {
            sb.append("object ").append(o.getName());
            if (o.getStereotype() != null && !o.getStereotype().isEmpty()) {
                sb.append(" <<").append(o.getStereotype()).append(">>");
            }
            sb.append('\n');
            for (String a : o.getAttributes()) {
                sb.append(o.getName()).append(" : ").append(a).append('\n');
            }
        }
        if (!model.getLinks().isEmpty()) {
            sb.append('\n');
            for (ObjectLink l : model.getLinks()) {
                sb.append(l.getLeft()).append(' ').append(l.getKind().arrow())
                        .append(' ').append(l.getRight());
                if (l.getLabel() != null && !l.getLabel().isEmpty()) {
                    sb.append(" : ").append(l.getLabel());
                }
                sb.append('\n');
            }
        }
        if (!model.getObjects().isEmpty()) {
            sb.append('\n');
            for (ObjectInstance o : model.getObjects()) {
                sb.append("'@pos ").append(o.getName()).append(' ')
                        .append(o.getX()).append(' ').append(o.getY()).append('\n');
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }
}
