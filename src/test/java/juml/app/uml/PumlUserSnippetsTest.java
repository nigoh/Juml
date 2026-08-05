// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ユーザー定義スニペットの保存・読み込みと、補完への合流を検証する
 * 純ロジックテスト (headless)。
 */
public class PumlUserSnippetsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @After
    public void restoreCompletionSource() {
        PumlCompletion.setUserSnippetsForTest(null);
    }

    private PumlUserSnippets store() throws Exception {
        return new PumlUserSnippets(new File(tmp.newFolder("data"), "snippets.json"));
    }

    private static PumlUserSnippets.Entry entry(String trigger, String body) {
        return new PumlUserSnippets.Entry(trigger, trigger, body);
    }

    // -------------------------------------------------------------------------
    // 保存と読み込み
    // -------------------------------------------------------------------------

    @Test
    public void savedSnippetsComeBackOnReload() throws Exception {
        PumlUserSnippets s = store();
        assertTrue(s.save(List.of(
                new PumlUserSnippets.Entry("hdr", "社内ヘッダ", "title ${1:name}\n"))));
        PumlUserSnippets reopened = new PumlUserSnippets(s.file());
        List<PumlUserSnippets.Entry> loaded = reopened.load();
        assertEquals(1, loaded.size());
        assertEquals("hdr", loaded.get(0).trigger());
        assertEquals("社内ヘッダ", loaded.get(0).label());
        assertEquals("title ${1:name}\n", loaded.get(0).body());
    }

    @Test
    public void addReplacesAnEntryWithTheSameTrigger() throws Exception {
        PumlUserSnippets s = store();
        s.add(entry("hdr", "first\n"));
        s.add(entry("HDR", "second\n"));
        assertEquals(1, s.load().size());
        assertEquals("second\n", s.load().get(0).body());
    }

    @Test
    public void entriesWithoutATriggerOrBodyAreDropped() throws Exception {
        // 引けないスニペットは一覧を汚すだけ。
        PumlUserSnippets s = store();
        s.save(List.of(entry("", "body\n"), entry("ok", ""), entry("good", "body\n")));
        assertEquals(1, s.load().size());
        assertEquals("good", s.load().get(0).trigger());
    }

    @Test
    public void missingFileLoadsAsEmpty() throws Exception {
        assertTrue(new PumlUserSnippets(
                new File(tmp.newFolder("empty"), "nope.json")).load().isEmpty());
    }

    @Test
    public void corruptFileLoadsAsEmptyInsteadOfThrowing() throws Exception {
        // 壊れた JSON で起動できなくなるより、追加分が見えないほうが害が小さい。
        File f = new File(tmp.newFolder("bad"), "snippets.json");
        Files.write(f.toPath(), "{ this is not json".getBytes(StandardCharsets.UTF_8));
        assertTrue(new PumlUserSnippets(f).load().isEmpty());
    }

    @Test
    public void savingCreatesTheDirectory() throws Exception {
        File f = new File(new File(tmp.newFolder("root"), "nested"), "snippets.json");
        assertTrue(new PumlUserSnippets(f).save(List.of(entry("a", "x\n"))));
        assertTrue(f.isFile());
    }

    @Test
    public void labelFallsBackToTheTrigger() {
        assertEquals("abc", new PumlUserSnippets.Entry("abc", "", "body").label());
        assertEquals("abc", new PumlUserSnippets.Entry("abc", null, "body").label());
    }

    @Test
    public void triggerIsNormalisedToSomethingCompletionCanFind() {
        // 空白や記号を含む語は補完の接頭辞として拾えない。
        assertEquals("myheader", PumlUserSnippets.normalizeTrigger(" My Header! "));
        assertEquals("@startx", PumlUserSnippets.normalizeTrigger("@startx"));
        assertEquals("", PumlUserSnippets.normalizeTrigger("   "));
        assertEquals("", PumlUserSnippets.normalizeTrigger(null));
    }

    // -------------------------------------------------------------------------
    // 補完への合流
    // -------------------------------------------------------------------------

    private static List<String> labelsAt(String doc, String typed) {
        String text = doc + typed;
        List<String> out = new ArrayList<>();
        for (PumlCompletionItem i : PumlCompletion.items(text, text.length(), false)) {
            out.add(i.label());
        }
        return out;
    }

    @Test
    public void userSnippetsAreOfferedByTheirTrigger() {
        PumlCompletion.setUserSnippetsForTest(
                () -> List.of(entry("hdrx", "title Mine\nskinparam monochrome true\n")));
        assertTrue(labelsAt("@startuml\n", "hd").contains("hdrx"));
    }

    @Test
    public void userSnippetsOutrankTheBundledOnes() {
        // わざわざ登録した型は、汎用の雛形より狙って呼ばれるはず。
        PumlCompletion.setUserSnippetsForTest(() -> List.of(entry("note", "my note\n")));
        String text = "@startuml\nno";
        List<PumlCompletionItem> items = PumlCompletion.items(text, text.length(), false);
        assertEquals(PumlCompletionItem.Kind.SNIPPET, items.get(0).kind());
        assertEquals("my note\n", items.get(0).insert());
    }

    @Test
    public void userSnippetsExpandTheirPlaceholders() {
        PumlCompletion.setUserSnippetsForTest(
                () -> List.of(entry("crew", "actor ${1:User}\nparticipant ${2:Api}\n")));
        String text = "@startuml\ncre";
        PumlCompletionItem item = PumlCompletion.items(text, text.length(), false).get(0);
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(item.insert());
        assertEquals("actor User\nparticipant Api\n", ex.text());
        assertEquals(2, ex.stops().size());
    }

    @Test
    public void userSnippetsStillOnlyAppearAtLineStart() {
        PumlCompletion.setUserSnippetsForTest(() -> List.of(entry("crew", "actor X\n")));
        assertFalse("行中にブロックを挿し込ませない",
                labelsAt("@startuml\nA -> B : ", "cre").contains("crew"));
    }

    @Test
    public void noUserSnippets_changesNothing() {
        PumlCompletion.setUserSnippetsForTest(List::of);
        assertTrue(labelsAt("@startuml\n", "al").contains("alt"));
    }
}
