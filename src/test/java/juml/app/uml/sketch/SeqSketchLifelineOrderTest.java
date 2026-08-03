// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * シーケンス図のライフライン (参加者) の左右の並びが、キャンバスの並びと一致することの回帰テスト。
 *
 * <p>PlantUML は並びを「先頭の宣言行の順 → 残りはメッセージ初出順」で決める。
 * 以前は宣言行を持つ参加者だけを先頭にまとめて出していたため、</p>
 * <ul>
 *   <li>ソース途中の {@code participant C} が先頭へ繰り上がって、それより前に登場していた
 *       暗黙の参加者を追い越す (往復しただけで並びが変わる)</li>
 *   <li>暗黙の参加者しかいない図でキャンバス上の並べ替えを行っても宣言行が出ず、
 *       並べ替えが黙って失われる</li>
 * </ul>
 * <p>検証は生成テキストだけでなく、実際に描画した SVG のライフライン見出しの
 * <b>x 座標</b>で行う (テキスト一致だと PlantUML の実挙動を取り違えても気付けない)。</p>
 */
public class SeqSketchLifelineOrderTest {

    private static final Pattern TEXT_EL =
            Pattern.compile("<text[^>]*\\sx=\"([0-9.]+)\"[^>]*>([^<]*)</text>");

    /** 描画した SVG から、指定した名前が現れる x 座標の小さい順の並びを返す。 */
    private static List<String> renderedOrder(String puml, List<String> names) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, out);
        String svg = out.toString(StandardCharsets.UTF_8);
        List<double[]> hits = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Matcher m = TEXT_EL.matcher(svg);
        while (m.find()) {
            String label = m.group(2).trim();
            int idx = names.indexOf(label);
            if (idx >= 0 && !labels.contains(label)) {
                labels.add(label);
                hits.add(new double[] {Double.parseDouble(m.group(1)), idx});
            }
        }
        hits.sort((a, b) -> Double.compare(a[0], b[0]));
        List<String> order = new ArrayList<>();
        for (double[] h : hits) {
            order.add(names.get((int) h[1]));
        }
        return order;
    }

    @Test
    public void declarationInTheMiddleDoesNotJumpToTheFront() throws Exception {
        // A, B が先に登場し、C は途中で宣言される。修正前の出力は
        // "participant C" を先頭に置いたため C が最左になっていた。
        String src = "@startuml\nA -> B : m1\nparticipant C\nB -> C : m2\n@enduml\n";
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(src);
        assertTrue("全行モデル化できること: " + r.unsupportedLines, r.isFullySupported());
        List<String> names = List.of("A", "B", "C");
        assertEquals("モデルの並びは登場順", names,
                r.model.getParticipants().stream().map(SeqParticipant::getName).toList());

        String out = SeqSketchCodec.toPuml(r.model);
        assertEquals("描画されるライフライン順がモデル順と一致すること",
                names, renderedOrder(out, names));
        // 2 周目は固定点 (全員が declared になっても並びは変わらない)。
        assertEquals(out, SeqSketchCodec.toPuml(SeqSketchCodec.parse(out).model));
    }

    @Test
    public void reorderingImplicitParticipantsIsPreserved() throws Exception {
        // 暗黙の参加者だけの図。キャンバスで B を先頭へ動かした並びが描画に反映されること
        // (修正前は宣言行が 1 つも出ず、初出順のまま A, B に戻っていた)。
        SeqSketchCodec.ParseResult r =
                SeqSketchCodec.parse("@startuml\nA -> B : hi\n@enduml\n");
        SeqSketchModel model = r.model;
        model.moveParticipant(model.findParticipant("B"), 0);

        String out = SeqSketchCodec.toPuml(model);
        assertEquals("並べ替えが描画へ反映されること",
                List.of("B", "A"), renderedOrder(out, List.of("A", "B")));
        assertEquals("2 周目は固定点", out, SeqSketchCodec.toPuml(SeqSketchCodec.parse(out).model));
    }

    @Test
    public void alreadyConsistentDiagramsKeepTheirText() {
        // 推論順がモデル順と一致する図には宣言行を足さない (無用な差分を出さない)。
        for (String src : new String[] {
            "@startuml\nA -> B : hi\n@enduml\n",
            "@startuml\nparticipant A\nA -> A : self()\n@enduml\n",
            "@startuml\nactor U\nparticipant S\nU -> S : go\n@enduml\n"}) {
            SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(src);
            assertEquals("出力が変わらないこと", src, SeqSketchCodec.toPuml(r.model));
        }
    }
}
