// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** {@link DiagramNotesStore} の保存/復元検証 (.juml/notes.json 往復)。 */
public class DiagramNotesStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void savesAndLoadsNotesPerDiagram() throws Exception {
        File root = tmp.newFolder("project");
        DiagramNotesStore store = new DiagramNotesStore(root);

        DiagramNote a = new DiagramNote(10, 20, 200, 120, "# Heading\n- item");
        a.setColor("#D6F5C8");
        DiagramNote b = new DiagramNote(50, 60, 180, 90, "plain *note*");
        store.save("CLASS:com.x.Foo", Arrays.asList(a, b));

        assertTrue(new File(new File(root, ".juml"), "notes.json").isFile());

        // 別インスタンスで読み直して内容が一致すること
        DiagramNotesStore reloaded = new DiagramNotesStore(root);
        List<DiagramNote> notes = reloaded.load("CLASS:com.x.Foo");
        assertEquals(2, notes.size());
        assertEquals("# Heading\n- item", notes.get(0).getText());
        assertEquals("#D6F5C8", notes.get(0).getColor());
        assertEquals(10.0, notes.get(0).getX(), 0);
        assertEquals("plain *note*", notes.get(1).getText());
    }

    @Test
    public void lockedFlagSurvivesRoundTrip() throws Exception {
        File root = tmp.newFolder("locked");
        DiagramNote a = new DiagramNote(10, 20, 200, 120, "locked");
        a.setLocked(true);
        DiagramNote b = new DiagramNote(50, 60, 180, 90, "free");
        new DiagramNotesStore(root).save("k", Arrays.asList(a, b));

        List<DiagramNote> notes = new DiagramNotesStore(root).load("k");
        assertTrue(notes.get(0).isLocked());
        assertFalse(notes.get(1).isLocked());
    }

    @Test
    public void tagsSurviveRoundTrip() throws Exception {
        File root = tmp.newFolder("tags");
        DiagramNote a = new DiagramNote(0, 0, 100, 80, "x");
        a.setTags(Arrays.asList("todo", "perf"));
        new DiagramNotesStore(root).save("k", Arrays.asList(a));

        DiagramNote loaded = new DiagramNotesStore(root).load("k").get(0);
        assertEquals(Arrays.asList("todo", "perf"), loaded.getTags());
    }

    @Test
    public void connectorsSurviveRoundTrip() throws Exception {
        File root = tmp.newFolder("conn");
        DiagramNote a = new DiagramNote(0, 0, 100, 80, "a");
        DiagramNote b = new DiagramNote(200, 0, 100, 80, "b");
        DiagramConnector c = new DiagramConnector(a.getId(), b.getId());
        new DiagramNotesStore(root).save("k", Arrays.asList(a, b), Arrays.asList(c));

        DiagramNotesStore reloaded = new DiagramNotesStore(root);
        assertEquals(2, reloaded.load("k").size());
        List<DiagramConnector> conns = reloaded.loadConnectors("k");
        assertEquals(1, conns.size());
        assertEquals(a.getId(), conns.get(0).getFromId());
        assertEquals(b.getId(), conns.get(0).getToId());
    }

    @Test
    public void unknownDiagramReturnsEmpty() throws Exception {
        File root = tmp.newFolder("p2");
        DiagramNotesStore store = new DiagramNotesStore(root);
        assertTrue(store.load("nope").isEmpty());
    }

    @Test
    public void savingEmptyRemovesEntry() throws Exception {
        File root = tmp.newFolder("p3");
        DiagramNotesStore store = new DiagramNotesStore(root);
        store.save("k", Arrays.asList(new DiagramNote(0, 0, 100, 80, "x")));
        store.save("k", java.util.Collections.emptyList());
        assertTrue(new DiagramNotesStore(root).load("k").isEmpty());
    }

    @Test
    public void nullProjectRootIsNoOp() {
        DiagramNotesStore store = new DiagramNotesStore(null);
        store.save("k", Arrays.asList(new DiagramNote(0, 0, 10, 10, "x")));
        assertTrue(store.load("k").isEmpty());
    }

    /**
     * rename でキーを移すと、付箋・コネクタが新キーへ移り旧キーは空になる
     * (Save As のタブキー移行で付箋が旧キーに取り残されないため)。
     */
    @Test
    public void renameMovesNotesToNewKey() throws Exception {
        File root = tmp.newFolder("rename");
        DiagramNotesStore store = new DiagramNotesStore(root);
        DiagramNote a = new DiagramNote(1, 2, 100, 80, "note");
        store.save("PUML:untitled", Arrays.asList(a));

        assertTrue(store.rename("PUML:untitled", "PUML:/path/file.puml"));

        // 別インスタンスで読み直しても新キーで取れ、旧キーは空。
        DiagramNotesStore reloaded = new DiagramNotesStore(root);
        assertEquals(1, reloaded.load("PUML:/path/file.puml").size());
        assertTrue(reloaded.load("PUML:untitled").isEmpty());
    }

    /** rename の移行元が存在しない場合は何もせず true。 */
    @Test
    public void renameOfMissingKeyIsNoOp() throws Exception {
        File root = tmp.newFolder("rename2");
        DiagramNotesStore store = new DiagramNotesStore(root);
        assertTrue(store.rename("nope", "also-nope"));
        assertTrue(store.load("also-nope").isEmpty());
    }

    /**
     * 保存はアトミック (一時ファイル → rename) で行うため、書き込み後に
     * {@code .tmp} が残らない。
     */
    @Test
    public void saveLeavesNoTempFileBehind() throws Exception {
        File root = tmp.newFolder("atomic");
        new DiagramNotesStore(root).save("k",
                Arrays.asList(new DiagramNote(0, 0, 10, 10, "x")));
        File jumlDir = new File(root, ".juml");
        File tmpFile = new File(jumlDir, "notes.json.tmp");
        assertFalse("一時ファイルが残ってはいけない", tmpFile.exists());
        assertTrue(new File(jumlDir, "notes.json").isFile());
    }
}
