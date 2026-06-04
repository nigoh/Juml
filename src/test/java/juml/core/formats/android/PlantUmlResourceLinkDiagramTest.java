// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PlantUmlResourceLinkDiagram のユニットテスト。
 */
public class PlantUmlResourceLinkDiagramTest {

    private ResourceLinkAnalysis sampleModel() {
        ResourceLinkAnalysis model = new ResourceLinkAnalysis();
        model.getReferences().add(new ResourceReference(
                "MainActivity", ResourceReference.Kind.LAYOUT, "activity_main", true, "f"));
        model.getReferences().add(new ResourceReference(
                "MainActivity", ResourceReference.Kind.STRING, "app_name", false, "f"));
        model.getReferences().add(new ResourceReference(
                "Helper", ResourceReference.Kind.LAYOUT, "row_item", false, "f"));
        model.addLayoutStringRef("activity_main", "title_text");

        AndroidProjectAnalysis analysis = new AndroidProjectAnalysis();
        AndroidStringResources sr = new AndroidStringResources();
        sr.getStrings().put("app_name", "My App");
        sr.getStrings().put("title_text", "Welcome");
        List<AndroidStringResources> list = new ArrayList<>();
        list.add(sr);
        analysis.getStringResourcesByModule().put(":root", list);
        model.setAnalysis(analysis);
        return model;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullModel() {
        PlantUmlResourceLinkDiagram.generate(null);
    }

    @Test
    public void testEmptyModelProducesNote() {
        String puml = PlantUmlResourceLinkDiagram.generate(new ResourceLinkAnalysis());
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("No code↔resource links"));
    }

    @Test
    public void testNodesAndEdges() {
        String puml = PlantUmlResourceLinkDiagram.generate(sampleModel());
        assertTrue(puml.contains("@startuml"));
        assertTrue(puml.trim().endsWith("@enduml"));
        // ステレオタイプ別ノード
        assertTrue(puml.contains("<<class>>"));
        assertTrue(puml.contains("<<layout>>"));
        assertTrue(puml.contains("<<string>>"));
        // content binding は太線 binds
        assertTrue("content binding エッジ", puml.contains("=> ") && puml.contains(": binds"));
        // 通常 R.layout 参照
        assertTrue(puml.contains(": R.layout"));
        // R.string 参照
        assertTrue(puml.contains(": R.string"));
        // layout → @string 参照
        assertTrue(puml.contains(": @string"));
    }

    @Test
    public void testResolvedStringValuesShown() {
        String puml = PlantUmlResourceLinkDiagram.generate(sampleModel());
        // 文字列ノードには実文言が併記される
        assertTrue("app_name の実文言", puml.contains("My App"));
        assertTrue("@string 参照先の実文言", puml.contains("Welcome"));
    }

    @Test
    public void testLegendToggle() {
        PlantUmlResourceLinkDiagram.Options opts = new PlantUmlResourceLinkDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlResourceLinkDiagram.generate(sampleModel(), opts);
        assertFalse(puml.contains("legend top left"));
    }
}
