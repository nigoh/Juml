// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SELinux policy パーサのテスト。
 */
public class SelinuxPolicyParserTest {

    @Test
    public void parsesTypeDeclaration() {
        String src = "type foo_t, domain;\n"
                + "type bar_t, domain, coredomain;\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(2, rules.size());
        assertEquals(SelinuxRule.Kind.TYPE_DECL, rules.get(0).getKind());
        assertEquals("foo_t", rules.get(0).getSubject());
        assertTrue(rules.get(0).getAttributes().contains("domain"));
        assertEquals("bar_t", rules.get(1).getSubject());
        assertEquals(2, rules.get(1).getAttributes().size());
    }

    @Test
    public void parsesAllowRule() {
        String src = "allow foo_t bar_t:file { read write };\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(1, rules.size());
        SelinuxRule r = rules.get(0);
        assertEquals(SelinuxRule.Kind.ALLOW, r.getKind());
        assertEquals("foo_t", r.getSubject());
        assertEquals("bar_t", r.getTarget());
        assertEquals("file", r.getObjectClass());
        assertTrue(r.getPermissions().contains("read"));
        assertTrue(r.getPermissions().contains("write"));
    }

    @Test
    public void parsesSinglePermissionWithoutBraces() {
        String src = "allow foo_t bar_t:file read;\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(1, rules.size());
        assertEquals(1, rules.get(0).getPermissions().size());
        assertEquals("read", rules.get(0).getPermissions().get(0));
    }

    @Test
    public void parsesNeverallow() {
        String src = "neverallow foo_t bar_t:file write;\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(1, rules.size());
        assertEquals(SelinuxRule.Kind.NEVERALLOW, rules.get(0).getKind());
    }

    @Test
    public void parsesComplementPermission() {
        // 補集合 permission `~{ ... }` をルールとして取りこぼさず、complement フラグを立てる
        String src = "allow foo_t bar_t:file ~{ append };\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(1, rules.size());
        SelinuxRule r = rules.get(0);
        assertEquals(SelinuxRule.Kind.ALLOW, r.getKind());
        assertEquals("foo_t", r.getSubject());
        assertEquals("bar_t", r.getTarget());
        assertEquals("file", r.getObjectClass());
        assertTrue(r.isComplement());
        assertTrue(r.getPermissions().contains("append"));
    }

    @Test
    public void parsesWildcardTarget() {
        // ワイルドカード target `*` を取りこぼさない
        String src = "allow appdomain *:process fork;\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(1, rules.size());
        SelinuxRule r = rules.get(0);
        assertEquals("appdomain", r.getSubject());
        assertEquals("*", r.getTarget());
        assertEquals("process", r.getObjectClass());
        assertTrue(r.getPermissions().contains("fork"));
    }

    @Test
    public void parsesWildcardSinglePermission() {
        // 単一 permission の `*`（全許可）も拾う
        String src = "allow foo_t self:capability *;\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(1, rules.size());
        SelinuxRule r = rules.get(0);
        assertEquals("self", r.getTarget());
        assertEquals(1, r.getPermissions().size());
        assertEquals("*", r.getPermissions().get(0));
        // 単一 permission は補集合ではない
        assertTrue(!r.isComplement());
    }

    @Test
    public void plainAllowRuleIsNotComplement() {
        // 通常の `{ ... }` permission は complement=false のまま
        String src = "allow foo_t bar_t:file { read write };\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(1, rules.size());
        assertTrue(!rules.get(0).isComplement());
    }

    @Test
    public void strayQuoteDoesNotRevivePhantomCommentedRule() {
        // 対になっていない裸の " のある行の後、コメントアウトされた allow が
        // 文字列扱いの巻き込みで生きたルールに化けないこと（改行で文字列を閉じる）。
        String src = "type foo\"_t;\n"
                + "# allow evil_t target_t:file write;\n"
                + "allow good_t bar_t:file read;\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        // 生きている allow は good_t の 1 件だけ（evil_t は出ない）
        long allowCount = rules.stream()
                .filter(r -> r.getKind() == SelinuxRule.Kind.ALLOW).count();
        assertEquals(1, allowCount);
        SelinuxRule allow = rules.stream()
                .filter(r -> r.getKind() == SelinuxRule.Kind.ALLOW).findFirst().orElse(null);
        assertNotNull(allow);
        assertEquals("good_t", allow.getSubject());
    }

    @Test
    public void parsesTypeAttribute() {
        String src = "typeattribute foo_t coredomain;\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(1, rules.size());
        assertEquals(SelinuxRule.Kind.TYPE_ATTRIBUTE, rules.get(0).getKind());
        assertEquals("foo_t", rules.get(0).getSubject());
        assertTrue(rules.get(0).getAttributes().contains("coredomain"));
    }

    @Test
    public void ignoresComments() {
        String src = "# top-level comment\n"
                + "type foo_t, domain; # inline comment\n"
                + "/* block comment\n"
                + " spans multiple lines */\n"
                + "allow foo_t bar_t:file read;\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        assertEquals(2, rules.size());
        assertEquals("foo_t", rules.get(0).getSubject());
        assertEquals(SelinuxRule.Kind.ALLOW, rules.get(1).getKind());
    }

    @Test
    public void markdownReportContainsSections() {
        String src = "type foo_t, domain;\n"
                + "allow foo_t bar_t:file { read write };\n"
                + "neverallow foo_t baz_t:file write;\n";
        List<SelinuxRule> rules = new SelinuxPolicyParser().parseSource(src, "foo.te");
        String md = MarkdownSelinuxReport.render(rules);
        assertTrue(md.contains("SELinux Policy Report"));
        assertTrue(md.contains("Domain / Type Declarations"));
        assertTrue(md.contains("Allow Rules"));
        assertTrue(md.contains("Neverallow"));
        assertTrue(md.contains("foo_t"));
        assertTrue(md.contains("bar_t"));
    }

    @Test
    public void emptyPolicyRendersPlaceholder() {
        String md = MarkdownSelinuxReport.render(new java.util.ArrayList<>());
        assertNotNull(md);
        assertTrue(md.contains("no SELinux policy"));
    }

    @Test
    public void stripCommentsPreservesLength() {
        String src = "abc # comment\ndef /* block */ ghi";
        String stripped = SelinuxPolicyParser.stripComments(src);
        assertEquals(src.length(), stripped.length());
    }
}
