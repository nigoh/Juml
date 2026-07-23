// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlSnippets} カタログの健全性を検証する純ロジックテスト (headless)。
 */
public class PumlSnippetsTest {

    @Test
    public void everyGroupHasSnippets() {
        for (PumlSnippets.Group g : PumlSnippets.Group.values()) {
            assertFalse(g + " のスニペットが空", PumlSnippets.forGroup(g).isEmpty());
        }
    }

    @Test
    public void everyLabelResolvesToNonRawKey() {
        for (PumlSnippets.Group g : PumlSnippets.Group.values()) {
            String gn = g.displayName();
            assertFalse(g + " のグループ名が未解決キー", gn.startsWith("puml.snip"));
        }
        for (PumlSnippets.Snippet s : PumlSnippets.all()) {
            String label = s.displayName();
            assertFalse("スニペットラベルが空/未解決: " + label,
                    label == null || label.isEmpty() || label.startsWith("puml.snip"));
            assertFalse("スニペット本文が空", s.body() == null || s.body().isEmpty());
        }
    }

    @Test
    public void everyLabelResolvesInEnglishToo() {
        // 既定 (ja) だけでなく en ロケールでもラベルが解決されること (en キー欠落の検出)。
        try {
            juml.util.Messages.setLanguage("en");
            for (PumlSnippets.Group g : PumlSnippets.Group.values()) {
                assertFalse(g + " のグループ名(en)が未解決キー",
                        g.displayName().startsWith("puml.snip"));
            }
            for (PumlSnippets.Snippet s : PumlSnippets.all()) {
                assertFalse("スニペットラベル(en)が未解決: " + s.displayName(),
                        s.displayName().startsWith("puml.snip"));
            }
        } finally {
            juml.util.Messages.setLanguage("ja");
        }
    }

    @Test
    public void coversAllMajorDiagramFamilies() {
        // 主要な PlantUML 図種ファミリーがスニペットグループとして揃っていること。
        java.util.EnumSet<PumlSnippets.Group> groups =
                java.util.EnumSet.allOf(PumlSnippets.Group.class);
        for (PumlSnippets.Group expected : new PumlSnippets.Group[] {
                PumlSnippets.Group.USECASE, PumlSnippets.Group.COMPONENT,
                PumlSnippets.Group.ER, PumlSnippets.Group.OBJECT,
                PumlSnippets.Group.DEPLOYMENT, PumlSnippets.Group.TIMING,
                PumlSnippets.Group.JSON, PumlSnippets.Group.YAML,
                PumlSnippets.Group.MINDMAP, PumlSnippets.Group.WBS,
                PumlSnippets.Group.GANTT, PumlSnippets.Group.SALT}) {
            assertTrue("スニペットグループが不足: " + expected, groups.contains(expected));
        }
    }

    @Test
    public void caretMarkerAppearsAtMostOncePerBody() {
        for (PumlSnippets.Snippet s : PumlSnippets.all()) {
            int first = s.body().indexOf(PumlSnippets.CARET);
            if (first >= 0) {
                int second = s.body().indexOf(PumlSnippets.CARET, first + 1);
                assertTrue("キャレットマーカーは 1 本文に最大 1 個: " + s.displayName(),
                        second < 0);
            }
        }
    }
}
