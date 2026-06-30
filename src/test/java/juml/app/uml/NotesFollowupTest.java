// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PlantUmlSvgRenderer.LinkArea;
import juml.util.Messages;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** #232 フォローアップ (ELEMENT アンカー / コマンドパレット i18n) の検証。 */
public class NotesFollowupTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void elementRectResolvesByFqn() {
        List<LinkArea> areas = Arrays.asList(
                new LinkArea("juml://class/com.x.Foo", 10, 20, 100, 50),
                new LinkArea("https://example.com", 0, 0, 1, 1));
        assertArrayEquals(new double[] {10, 20, 100, 50},
                DiagramTabInternals.elementRect(areas, "com.x.Foo"), 0.001);
        assertNull(DiagramTabInternals.elementRect(areas, "com.x.Missing"));
        assertNull(DiagramTabInternals.elementRect(null, "com.x.Foo"));
        assertNull(DiagramTabInternals.elementRect(areas, null));
    }

    @Test
    public void elementAnchorSurvivesJsonRoundTrip() {
        File root = tmp.getRoot();
        DiagramNote n = new DiagramNote(16, 0, 240, 150, "anchored note");
        n.setAnchor(DiagramNote.Anchor.ELEMENT);
        n.setTargetRef("com.x.Foo");
        new DiagramNotesStore(root).save("CLASS:all", Arrays.asList(n));

        DiagramNote loaded = new DiagramNotesStore(root).load("CLASS:all").get(0);
        assertEquals(DiagramNote.Anchor.ELEMENT, loaded.getAnchor());
        assertEquals("com.x.Foo", loaded.getTargetRef());
        assertEquals(16, loaded.getX(), 0.001);
    }

    @Test
    public void commandPaletteLabelsAreLocalized() throws Exception {
        // すべてのコールバックを非 null にして全コマンドを生成させ、ラベルが i18n 解決される
        // (キー漏れがあると Messages.get がキー文字列 "cmd.*" をそのまま返す) ことを検証する。
        MenuBarBuilder.Callbacks cb = new MenuBarBuilder.Callbacks();
        for (Field f : MenuBarBuilder.Callbacks.class.getFields()) {
            if (f.getType() == Runnable.class) {
                f.set(cb, (Runnable) () -> { });
            } else if (f.getType() == Consumer.class) {
                f.set(cb, (Consumer<Object>) o -> { });
            }
        }
        String original = Messages.getLanguage();
        try {
            for (String lang : new String[] {"en", "ja"}) {
                Messages.setLanguage(lang);
                List<CommandPalette.Command> cmds = AppCommands.from(cb);
                assertTrue(cmds.size() >= 30);
                for (CommandPalette.Command c : cmds) {
                    assertFalse("unlocalized command label (" + lang + "): " + c.label,
                            c.label.startsWith("cmd."));
                }
            }
        } finally {
            Messages.setLanguage(original);
        }
    }
}
