// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.Point;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/** {@link NotesSidePanel} がヘッドレスで構築でき、タグを追従することの検証。 */
public class NotesSidePanelTest {

    @Test
    public void buildsAndTracksTags() {
        SvgPreviewPanel preview = new SvgPreviewPanel();
        NotesSidePanel panel = new NotesSidePanel(preview);

        preview.notes().addNoteAt(new Point(10, 10), 1.0);
        preview.notes().getNotes().get(0).setTags(Arrays.asList("todo", "perf"));
        panel.refresh();

        assertTrue("一覧パネルがタグを拾うこと",
                panel.visibleTags().containsAll(Arrays.asList("perf", "todo")));
    }
}
