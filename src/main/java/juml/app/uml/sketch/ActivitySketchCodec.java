// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link ActivitySketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文は新形式アクティビティ図の基本要素に限定する:
 * {@code start / stop / end}、1 行アクション ({@code :text;})、
 * {@code if (cond) then (label) ... else (label) ... endif} (入れ子可)。
 * それ以外の非空行 ({@code while / fork / partition} や複数行アクション等) は
 * 「未対応」として報告し、呼び出し側 (GUI デザイナー) は編集を無効化して
 * テキストを壊さないようにする。</p>
 *
 * <p>レイアウトは並び順から決定的に計算するため座標コメントは使わない。</p>
 */
public final class ActivitySketchCodec {

    private static final Pattern ACTION = Pattern.compile("^:(.*);$");
    private static final Pattern IF = Pattern.compile(
            "^if\\s*\\((.*)\\)\\s*then(?:\\s*\\((.*)\\))?$");
    private static final Pattern ELSE = Pattern.compile("^else(?:\\s*\\((.*)\\))?$");

    private ActivitySketchCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final ActivitySketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(ActivitySketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** PlantUML テキストをアクティビティ図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        ActivitySketchModel model = new ActivitySketchModel();
        List<String> unsupported = new ArrayList<>();
        String[] lines = (text == null ? "" : text).split("\n", -1);
        parseBlock(lines, 0, model.getNodes(), unsupported, model, false);
        return new ParseResult(model, unsupported);
    }

    /**
     * {@code start} 行目からノード列を読み進める。{@code insideIf} のときは
     * {@code else} / {@code endif} でブロックを終え、その行番号を呼び出し側へ返す。
     *
     * @return 次に読むべき行番号 (insideIf のときは else/endif の行番号)
     */
    private static int parseBlock(String[] lines, int start, List<ActivityNode> out,
                                  List<String> unsupported, ActivitySketchModel model,
                                  boolean insideIf) {
        int i = start;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (insideIf && (line.equals("endif") || ELSE.matcher(line).matches())) {
                return i;
            }
            i++;
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
            if (line.startsWith("'")) {
                // コメントはモデル化できず GUI 再生成で失われるため未対応扱い (テキスト保全)。
                unsupported.add(line);
                continue;
            }
            if (line.equals("start")) {
                out.add(ActivityNode.terminal(ActivityNode.Kind.START));
                continue;
            }
            if (line.equals("stop")) {
                out.add(ActivityNode.terminal(ActivityNode.Kind.STOP));
                continue;
            }
            if (line.equals("end")) {
                out.add(ActivityNode.terminal(ActivityNode.Kind.END));
                continue;
            }
            Matcher action = ACTION.matcher(line);
            if (action.matches()) {
                out.add(ActivityNode.action(action.group(1).trim()));
                continue;
            }
            Matcher ifm = IF.matcher(line);
            if (ifm.matches()) {
                ActivityNode branch = ActivityNode.branch(
                        ifm.group(1).trim(), trimOrNull(ifm.group(2)), null);
                out.add(branch);
                i = parseBlock(lines, i, branch.getThenBranch(), unsupported, model, true);
                if (i < lines.length) {
                    Matcher em = ELSE.matcher(lines[i].trim());
                    if (em.matches()) {
                        branch.setElseLabel(trimOrNull(em.group(1)));
                        i = parseBlock(lines, i + 1, branch.ensureElseBranch(),
                                unsupported, model, true);
                    }
                }
                if (i < lines.length && lines[i].trim().equals("endif")) {
                    i++;
                } else {
                    // 閉じられていない if は往復で構造が変わるため未対応として編集をロックする。
                    unsupported.add("if (" + branch.getCondition() + ") … endif?");
                }
                continue;
            }
            unsupported.add(line);
        }
        return i;
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** モデルを PlantUML テキストへ書き出す (ブランチは 2 スペース字下げ)。 */
    public static String toPuml(ActivitySketchModel model) {
        StringBuilder sb = new StringBuilder("@startuml");
        if (!model.getDiagramName().isEmpty()) {
            sb.append(' ').append(model.getDiagramName());
        }
        sb.append('\n');
        emit(sb, model.getNodes(), 0);
        sb.append("@enduml\n");
        return sb.toString();
    }

    private static void emit(StringBuilder sb, List<ActivityNode> nodes, int depth) {
        String indent = "  ".repeat(depth);
        for (ActivityNode n : nodes) {
            switch (n.getKind()) {
                case START:
                    sb.append(indent).append("start\n");
                    break;
                case STOP:
                    sb.append(indent).append("stop\n");
                    break;
                case END:
                    sb.append(indent).append("end\n");
                    break;
                case ACTION:
                    sb.append(indent).append(':').append(n.getText()).append(";\n");
                    break;
                default:
                    sb.append(indent).append("if (").append(n.getCondition()).append(") then");
                    if (n.getThenLabel() != null) {
                        sb.append(" (").append(n.getThenLabel()).append(')');
                    }
                    sb.append('\n');
                    emit(sb, n.getThenBranch(), depth + 1);
                    if (n.getElseBranch() != null) {
                        sb.append(indent).append("else");
                        if (n.getElseLabel() != null) {
                            sb.append(" (").append(n.getElseLabel()).append(')');
                        }
                        sb.append('\n');
                        emit(sb, n.getElseBranch(), depth + 1);
                    }
                    sb.append(indent).append("endif\n");
                    break;
            }
        }
    }
}
