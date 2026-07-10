// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * シーケンス図の呼び出し引数表示 ({@code Options.showCallArguments}) のユニットテスト。
 *
 * <p>既定 OFF では従来どおり定数シンボル第 1 引数 (firstArgLabel) のみ、
 * ON では引数の元ソースを矢印ラベルに添える (長すぎる場合は切り詰め)。
 * 本体テストは {@code PlantUmlSequenceDiagramTest} (FileLength 対策で分離)。</p>
 */
public class PlantUmlSequenceCallArgumentsTest {

    /** 既定 (showCallArguments=false) では引数は出ず、従来どおりの () 表記のまま。 */
    @Test
    public void testCallArgumentsHiddenByDefault() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Helper helper;"
                        + " void run(String label) { helper.done(label, 3); } }");
        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", opts);
        assertTrue(puml, puml.contains("Helper.done()"));
        assertFalse(puml, puml.contains("Helper.done(label, 3)"));
    }

    /** showCallArguments=true で矢印ラベルに引数の元ソースが出る。 */
    @Test
    public void testCallArgumentsShownWhenEnabled() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Helper helper;"
                        + " void run(String label) { helper.done(label, 3); } }");
        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.includeLegend = false;
        opts.showCallArguments = true;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", opts);
        assertTrue(puml, puml.contains("Helper.done(label, 3)"));
    }

    /** showCallArguments=true でも長い引数はラベル保護のため "…" で切り詰められる。 */
    @Test
    public void testCallArgumentsTruncatedWhenTooLong() {
        String longArg = "aVeryVeryLongArgumentVariableNameThatKeepsGoingOnAndOn";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Helper helper;"
                        + " void run() { helper.done(" + longArg + "); } }");
        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.includeLegend = false;
        opts.showCallArguments = true;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", opts);
        assertTrue("切り詰め記号が出ること: " + puml, puml.contains("…"));
        assertFalse("引数全文は出ないこと: " + puml, puml.contains(longArg + ")"));
    }

    /** 既定 OFF でも定数シンボル第 1 引数 (firstArgLabel) の従来表示は維持される。 */
    @Test
    public void testConstantSymbolFirstArgStillShownByDefault() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { CarPropertyManager mgr;"
                        + " void run() {"
                        + " mgr.getProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET); } }");
        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", opts);
        assertTrue(puml,
                puml.contains("getProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET)"));
    }
}
