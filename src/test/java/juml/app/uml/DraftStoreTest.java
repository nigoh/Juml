// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DraftStore} の下書き保存/復元/削除 (純 IO, headless)。
 *
 * <p>自動保存のスナップショットがメタ情報 (タブキー・保存先・ラベル) 込みで
 * round-trip し、正常保存/辞退で確実に消えることを固定する。</p>
 */
public class DraftStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String TEXT = "@startuml\nclass A\n@enduml\n";

    @Test
    public void save_thenLoadAll_roundTripsTextAndMeta() throws Exception {
        DraftStore store = new DraftStore(tmp.newFolder("drafts"));
        File target = new File("/tmp/example.puml");
        store.save("PUML:/tmp/example.puml", TEXT, target, "example.puml");

        List<DraftStore.Draft> all = store.loadAll();
        assertEquals(1, all.size());
        DraftStore.Draft d = all.get(0);
        assertEquals("PUML:/tmp/example.puml", d.tabKey);
        assertEquals(TEXT, d.text);
        assertEquals(target.getAbsolutePath(), d.file.getAbsolutePath());
        assertEquals("example.puml", d.label);
    }

    @Test
    public void save_untitledDraft_hasNullFile() throws Exception {
        DraftStore store = new DraftStore(tmp.newFolder("drafts"));
        store.save("PUML:untitled-1", TEXT, null, "Untitled-1.puml");
        List<DraftStore.Draft> all = store.loadAll();
        assertEquals(1, all.size());
        assertNull("Untitled の下書きは保存先なし (null) のはず", all.get(0).file);
    }

    @Test
    public void save_overwritesSameKey() throws Exception {
        DraftStore store = new DraftStore(tmp.newFolder("drafts"));
        store.save("PUML:untitled-1", "old", null, "a");
        store.save("PUML:untitled-1", "new", null, "a");
        List<DraftStore.Draft> all = store.loadAll();
        assertEquals("同一キーは上書きされ 1 件のままのはず", 1, all.size());
        assertEquals("new", all.get(0).text);
    }

    @Test
    public void delete_removesOnlyThatKey() throws Exception {
        DraftStore store = new DraftStore(tmp.newFolder("drafts"));
        store.save("k1", "t1", null, "a");
        store.save("k2", "t2", null, "b");
        store.delete("k1");
        List<DraftStore.Draft> all = store.loadAll();
        assertEquals(1, all.size());
        assertEquals("k2", all.get(0).tabKey);
    }

    @Test
    public void deleteAll_leavesNothing() throws Exception {
        DraftStore store = new DraftStore(tmp.newFolder("drafts"));
        store.save("k1", "t1", null, "a");
        store.save("k2", "t2", null, "b");
        store.deleteAll();
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    public void loadAll_onMissingDirectoryReturnsEmpty() {
        DraftStore store = new DraftStore(new File(tmp.getRoot(), "no-such-dir"));
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    public void japaneseTextSurvivesRoundTrip() throws Exception {
        DraftStore store = new DraftStore(tmp.newFolder("drafts"));
        String text = "@startuml\nclass クラスA\n@enduml\n";
        store.save("k", text, null, "日本語.puml");
        assertEquals(text, store.loadAll().get(0).text);
    }
}
