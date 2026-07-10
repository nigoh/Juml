// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link SeqSketchModel} と PlantUML テキストの双方向変換。
 *
 * <p>対応構文はシーケンス図の基本要素に限定する:
 * {@code participant / actor} 宣言、{@link SeqItem.Arrow} の 4 種のメッセージ、
 * {@code activate / deactivate}。それ以外の非空行は「未対応」として報告し、
 * 呼び出し側 (GUI デザイナー) は編集を無効化してテキストを壊さないようにする。</p>
 *
 * <p>シーケンス図の横位置・縦位置はテキストの並び順で一意に決まるため、
 * クラス図と違い座標コメント ({@code '@pos}) は使わない。</p>
 */
public final class SeqSketchCodec {

    private static final Pattern DECL = Pattern.compile(
            "^(participant|actor)\\s+([A-Za-z_$][\\w$.]*)\\s*$");
    // 長い矢印表記を先に並べる (--> が -->> の前置と衝突しないように)。
    private static final Pattern MESSAGE = Pattern.compile(
            "^([A-Za-z_$][\\w$.]*)\\s*(-->>|-->|->>|->)"
                    + "\\s*([A-Za-z_$][\\w$.]*)(?:\\s*:\\s*(.*\\S))?\\s*$");
    private static final Pattern ACTIVATION = Pattern.compile(
            "^(activate|deactivate)\\s+([A-Za-z_$][\\w$.]*)\\s*$");

    private SeqSketchCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final SeqSketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(SeqSketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** PlantUML テキストをシーケンス図モデルへ解析する。 */
    public static ParseResult parse(String text) {
        SeqSketchModel model = new SeqSketchModel();
        List<String> unsupported = new ArrayList<>();
        for (String raw : (text == null ? "" : text).split("\n", -1)) {
            String line = raw.trim();
            if (line.startsWith("@startuml")) {
                // 図名 (出力名) を保全する (クラス図コーデックと同じ契約)。
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
            Matcher decl = DECL.matcher(line);
            if (decl.matches()) {
                SeqParticipant p = model.obtainParticipant(decl.group(2));
                p.setKind("actor".equals(decl.group(1))
                        ? SeqParticipant.Kind.ACTOR : SeqParticipant.Kind.PARTICIPANT);
                p.setDeclared(true);
                continue;
            }
            Matcher msg = MESSAGE.matcher(line);
            if (msg.matches()) {
                SeqItem.Arrow arrow = SeqItem.Arrow.fromPuml(msg.group(2));
                // 端点が未宣言なら PlantUML と同様に暗黙の参加者として扱う。
                model.obtainParticipant(msg.group(1));
                model.obtainParticipant(msg.group(3));
                model.getItems().add(SeqItem.message(
                        msg.group(1), arrow, msg.group(3), msg.group(4)));
                continue;
            }
            Matcher act = ACTIVATION.matcher(line);
            if (act.matches()) {
                model.obtainParticipant(act.group(2));
                model.getItems().add("activate".equals(act.group(1))
                        ? SeqItem.activate(act.group(2))
                        : SeqItem.deactivate(act.group(2)));
                continue;
            }
            unsupported.add(line);
        }
        return new ParseResult(model, unsupported);
    }

    /** モデルを PlantUML テキストへ書き出す。 */
    public static String toPuml(SeqSketchModel model) {
        StringBuilder sb = new StringBuilder("@startuml");
        if (!model.getDiagramName().isEmpty()) {
            sb.append(' ').append(model.getDiagramName());
        }
        sb.append('\n');
        for (SeqParticipant p : model.getParticipants()) {
            if (p.isDeclared()) {
                sb.append(p.getKind().keyword()).append(' ').append(p.getName()).append('\n');
            }
        }
        for (SeqItem m : model.getItems()) {
            switch (m.getKind()) {
                case MESSAGE:
                    sb.append(m.getFrom()).append(' ').append(m.getArrow().puml())
                            .append(' ').append(m.getTo());
                    if (m.getLabel() != null && !m.getLabel().isEmpty()) {
                        sb.append(" : ").append(m.getLabel());
                    }
                    sb.append('\n');
                    break;
                case ACTIVATE:
                    sb.append("activate ").append(m.getTarget()).append('\n');
                    break;
                default:
                    sb.append("deactivate ").append(m.getTarget()).append('\n');
                    break;
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }
}
