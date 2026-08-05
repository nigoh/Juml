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
 * {@link ErSketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文は ER (エンティティ関連) 図の基本要素に限定する:
 * {@code entity} 宣言 (素の alias・{@code "表示名" as alias}・列ブロック付き)、
 * 列行 ({@code * name : type} = 主キー / {@code name : type} = 一般列)、
 * PK ブロックと一般列を分ける区切り線 {@code --}、crow's-foot (IE) 記法の
 * リレーション ({@code left ||--o{ right : label} など)、{@code hide circle}
 * 指令、レイアウト座標コメント ({@code '@pos alias x y})。それ以外の非空行
 * (複合構文・一般コメント等) は「未対応」として報告し、呼び出し側 (GUI デザイナー) は
 * 編集を無効化してテキストを壊さないようにする。</p>
 */
public final class ErSketchCodec {

    /** 座標コメントの書式: {@code '@pos alias x y}。 */
    private static final Pattern POS = Pattern.compile(
            "^'@pos\\s+(\\S+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*$");
    /** {@code entity "表示名" as alias} (列ブロックの有無は末尾の {@code {} で判定)。 */
    private static final Pattern ENTITY_ALIAS = Pattern.compile(
            "^entity\\s+" + SketchLabelText.QUOTED_LABEL
                    + "\\s+as\\s+(" + SketchIdentifier.BARE + ")\\s*(\\{)?\\s*$");
    /** {@code entity alias} (素の識別子)。 */
    private static final Pattern ENTITY_PLAIN = Pattern.compile(
            "^entity\\s+(" + SketchIdentifier.BARE + ")\\s*(\\{)?\\s*$");
    /** 列行: 先頭 {@code *} で主キー、{@code : 型} は任意。 */
    private static final Pattern COLUMN = Pattern.compile(
            "^(\\*\\s*)?(" + SketchIdentifier.BARE + ")\\s*(?::\\s*(.*\\S))?\\s*$");
    /** PK ブロックと一般列を分ける区切り線 ({@code --} / {@code ==} / {@code __} / {@code ..})。 */

    /**
     * その行が PK ブロックの区切り線として読まれるか。
     *
     * <p>列名の検証 ({@code ErSketchDialogs}) からも使う。区切りトークンには {@code __} が
     * 含まれるため、識別子として妥当な {@code __} や {@code ____} も<b>区切り線として
     * 読み直されて列が消える</b>。判定の本体は {@link SketchBlockLine#isDivider} —
     * ER のコーデック・ダイアログとクラス図のコーデックが 1 つの実装を共有する。</p>
     */
    static boolean isDividerLine(String line) {
        return SketchBlockLine.isDivider(line);
    }
    /** crow's-foot リレーション。左右のカーディナリティトークンは他図種と衝突しない。 */
    private static final Pattern RELATION = Pattern.compile(
            "^(" + SketchIdentifier.BARE + ")\\s*(\\|\\||\\|o|\\}o|\\}\\|)--(\\|\\||o\\||o\\{|\\|\\{)"
                    + "\\s*(" + SketchIdentifier.BARE + ")(?:\\s*:\\s*(.*\\S))?\\s*$");

    /** 位置未指定エンティティを格子状に自動配置する際の間隔。 */
    private static final int GRID_X = 260;
    private static final int GRID_Y = 190;
    private static final int GRID_COLS = 3;
    private static final int GRID_MARGIN = 40;

    private ErSketchCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final ErSketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(ErSketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** PlantUML テキストを ER 図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        ErSketchModel model = new ErSketchModel();
        List<String> unsupported = new ArrayList<>();
        Map<String, int[]> positions = new HashMap<>();
        String[] lines = (text == null ? "" : text).split("\n", -1);
        // 複数の図が入ったファイルは編集をロックする (SketchMultiDiagram の javadoc 参照)。
        SketchMultiDiagram.reportExtraDiagrams(
                (text == null ? "" : text).split("\n", -1), "@startuml", unsupported);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            i++;
            if (line.startsWith("@startuml")) {
                String name = SketchMultiDiagram.parseDiagramName(line, "@startuml");
                if (!name.isEmpty()) {
                    model.setDiagramName(name);
                }
                continue;
            }
            if (line.isEmpty() || line.equals("@enduml") || line.equals("hide circle")) {
                // hide circle は ER 図の見た目指令。toPuml が常に再付与するため消費して無視する。
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
            int afterEntity = matchEntity(model, lines, i, line, unsupported);
            if (afterEntity >= 0) {
                i = afterEntity; // エンティティ宣言 (+ 列ブロック) を消費した位置まで進める。
                continue;
            }
            Matcher rel = RELATION.matcher(line);
            if (rel.matches()) {
                addRelation(model, rel);
                continue;
            }
            unsupported.add(line);
        }
        applyPositions(model, positions);
        return new ParseResult(model, unsupported);
    }

    /**
     * エンティティ宣言 (素 / 別名 / 列ブロック) にマッチしたら追加し、消費後の行番号
     * ({@code index} 以上) を返す。エンティティ宣言でなければ {@code -1} を返す。
     */
    private static int matchEntity(ErSketchModel model, String[] lines, int index,
                                   String line, List<String> unsupported) {
        Matcher alias = ENTITY_ALIAS.matcher(line);
        if (alias.matches()) {
            ErSketchModel.Entity e = obtain(model, alias.group(2),
                    SketchLabelText.fromCaptured(alias.group(1)));
            return alias.group(3) != null
                    ? readColumns(lines, index, e, unsupported) : index;
        }
        Matcher plain = ENTITY_PLAIN.matcher(line);
        if (plain.matches()) {
            ErSketchModel.Entity e = obtain(model, plain.group(1), null);
            return plain.group(2) != null
                    ? readColumns(lines, index, e, unsupported) : index;
        }
        return -1;
    }

    private static ErSketchModel.Entity obtain(ErSketchModel model, String alias, String label) {
        ErSketchModel.Entity e = model.findEntity(alias);
        if (e == null) {
            e = new ErSketchModel.Entity(alias, normalizeLabel(alias, label), 0, 0);
            model.getEntities().add(e);
        } else if (label != null) {
            e.setDisplayName(normalizeLabel(alias, label));
        }
        return e;
    }

    /** リレーション端点用: 未宣言なら暗黙のエンティティとして生成する。 */
    private static ErSketchModel.Entity obtainEndpoint(ErSketchModel model, String alias) {
        ErSketchModel.Entity e = model.findEntity(alias);
        if (e == null) {
            e = new ErSketchModel.Entity(alias, null, 0, 0);
            model.getEntities().add(e);
        }
        return e;
    }

    /** 表示名が alias と同じ (または空) なら null に畳んで冗長な {@code as} 出力を避ける。 */
    private static String normalizeLabel(String alias, String label) {
        if (label == null || label.isEmpty() || label.equals(alias)) {
            return null;
        }
        return label;
    }

    /**
     * {@code {} } ブロック内の列行を読み、閉じ括弧の次の行番号を返す。区切り線 {@code --} は
     * PK ブロックと一般列の仕切りとして消費し (toPuml が再生成)、列にできない行は
     * {@code unsupported} へ積んで編集をロックしテキストを保全する。
     */
    private static int readColumns(String[] lines, int start, ErSketchModel.Entity e,
                                   List<String> unsupported) {
        int i = start;
        int dividers = 0;
        int dividerAfter = -1;
        String dividerToken = null;
        while (i < lines.length) {
            String line = lines[i].trim();
            // 図境界ディレクティブはメンバーではない。閉じ括弧が欠けたまま @enduml/@startuml に
            // 達したら消費せずブロックを打ち切る (外側ループが処理する)。
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
            if (isDividerLine(line)) {
                // 区切り線はモデルに持たない — 書き出しは PK 列と一般列の境目に
                // 自動で "--" を 1 本引く。読んだものがそのまま出てくるときだけ捨ててよい。
                dividers++;
                dividerAfter = e.getColumns().size();
                dividerToken = line;
                continue;
            }
            Matcher col = COLUMN.matcher(line);
            if (col.matches()) {
                e.getColumns().add(new ErSketchModel.Column(
                        col.group(1) != null, col.group(2), col.group(3)));
            } else {
                unsupported.add(line);
            }
        }
        String lost = lostOnWriteBack(e, dividers, dividerAfter, dividerToken);
        if (lost != null) {
            // 読んだものが書き戻しで出てこないなら編集をロックして原文を守る
            // (他の codec が非対応行に対してしている判断と同じ)。積むのは<b>原文の行</b> —
            // 固定文字列 "--" を積むと、利用者のファイルに無い行がバナーに出る。
            unsupported.add(lost);
        }
        return i;
    }

    /**
     * 列ブロックを {@link #toPuml} がそのまま再現できないなら、その原因の行を返す。
     *
     * <p>書き出しは<b>PK 列 → {@code --} → 一般列</b>の順に固定されている。したがって
     * 再現できるのは「PK 列がすべて一般列より前にある」ことが前提で、そのうえで
     * 区切り線については「1 本だけ」「PK と一般列の境目にある」「トークンが {@code --}」の
     * 3 つが要る。</p>
     *
     * <p>ラウンド 20 は区切り線の<b>位置</b>だけを見ていた。同じ損失なのに、
     * トークンの違い ({@code ==} は二重線、{@code ..} は破線と、実測で別の線が描かれる) と
     * <b>区切り線を書いていない図の列並べ替え</b>が素通しのままだった — どちらも
     * 「読んだものが出てこない」という 1 つの規則の兄弟である。</p>
     */
    private static String lostOnWriteBack(ErSketchModel.Entity e, int dividers,
                                          int at, String token) {
        List<ErSketchModel.Column> cols = e.getColumns();
        int firstNonPk = -1;
        for (int k = 0; k < cols.size(); k++) {
            if (!cols.get(k).isPrimaryKey()) {
                if (firstNonPk < 0) {
                    firstNonPk = k;
                }
            } else if (firstNonPk >= 0) {
                // PK が一般列の後ろにある = 書き出しで前へ動く (区切り線の有無に関わらず)。
                return cols.get(k).getName();
            }
        }
        if (dividers == 0) {
            return null;
        }
        if (dividers > 1 || at != firstNonPk || firstNonPk <= 0) {
            return token;
        }
        return "--".equals(token) ? null : token;
    }

    private static void addRelation(ErSketchModel model, Matcher rel) {
        ErSketchModel.Cardinality leftCard = ErSketchModel.Cardinality.fromLeft(rel.group(2));
        ErSketchModel.Cardinality rightCard = ErSketchModel.Cardinality.fromRight(rel.group(3));
        obtainEndpoint(model, rel.group(1));
        obtainEndpoint(model, rel.group(4));
        model.getRelations().add(new ErSketchModel.Relation(
                rel.group(1), leftCard, rightCard, rel.group(4), rel.group(5)));
    }

    /** int 範囲外・不正な整数は null を返す安全パース ({@code '@pos} 座標のクラッシュ防止)。 */
    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 明示座標を反映し、座標の無いエンティティは格子状に自動配置する。 */
    private static void applyPositions(ErSketchModel model, Map<String, int[]> positions) {
        int auto = 0;
        for (ErSketchModel.Entity e : model.getEntities()) {
            int[] p = positions.get(e.getAlias());
            if (p != null) {
                e.moveTo(p[0], p[1]);
            } else {
                e.moveTo(GRID_MARGIN + (auto % GRID_COLS) * GRID_X,
                        GRID_MARGIN + (auto / GRID_COLS) * GRID_Y);
                auto++;
            }
        }
    }

    /** モデルを PlantUML テキストへ書き出す (座標は {@code '@pos} コメントで保存)。 */
    public static String toPuml(ErSketchModel model) {
        StringBuilder sb = new StringBuilder(
                SketchMultiDiagram.startLine("@startuml", model.getDiagramName()));
        sb.append('\n');
        // hide circle: エンティティを丸ではなく表として描かせる ER 図の定番指令。
        sb.append("hide circle\n");
        for (ErSketchModel.Entity e : model.getEntities()) {
            appendEntity(sb, e);
        }
        if (!model.getRelations().isEmpty()) {
            sb.append('\n');
            for (ErSketchModel.Relation r : model.getRelations()) {
                sb.append(r.getLeft()).append(' ').append(r.arrow())
                        .append(' ').append(r.getRight());
                if (r.getLabel() != null && !r.getLabel().isEmpty()) {
                    sb.append(" : ").append(r.getLabel());
                }
                sb.append('\n');
            }
        }
        if (!model.getEntities().isEmpty()) {
            sb.append('\n');
            for (ErSketchModel.Entity e : model.getEntities()) {
                sb.append("'@pos ").append(e.getAlias()).append(' ')
                        .append(e.getX()).append(' ').append(e.getY()).append('\n');
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }

    /** 1 エンティティ分の宣言 (+ 列ブロック) を書き出す。PK 列を上段、一般列を下段に置く。 */
    private static void appendEntity(StringBuilder sb, ErSketchModel.Entity e) {
        sb.append("entity ");
        if (e.getDisplayName() != null && !e.getDisplayName().isEmpty()
                && !e.getDisplayName().equals(e.getAlias())) {
            sb.append('"').append(SketchLabelText.forOutput(e.getDisplayName()))
                    .append("\" as ").append(e.getAlias());
        } else {
            sb.append(e.getAlias());
        }
        List<ErSketchModel.Column> pk = new ArrayList<>();
        List<ErSketchModel.Column> other = new ArrayList<>();
        for (ErSketchModel.Column c : e.getColumns()) {
            (c.isPrimaryKey() ? pk : other).add(c);
        }
        if (pk.isEmpty() && other.isEmpty()) {
            sb.append('\n');
            return;
        }
        sb.append(" {\n");
        for (ErSketchModel.Column c : pk) {
            appendColumn(sb, c, true);
        }
        if (!pk.isEmpty() && !other.isEmpty()) {
            sb.append("  --\n");
        }
        for (ErSketchModel.Column c : other) {
            appendColumn(sb, c, false);
        }
        sb.append("}\n");
    }

    private static void appendColumn(StringBuilder sb, ErSketchModel.Column c, boolean pk) {
        sb.append("  ");
        if (pk) {
            sb.append("* ");
        }
        sb.append(c.getName());
        if (!c.getType().isEmpty()) {
            sb.append(" : ").append(c.getType());
        }
        sb.append('\n');
    }
}
