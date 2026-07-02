// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlSoongDependencyDiagram} のエイリアス衝突ハードニングを検証する。
 *
 * <p>モジュール名 "foo-bar" と "foo.bar" はどちらも素朴な置換では "m_foo_bar" になるが、
 * 新実装ではハッシュサフィックスを付けて一意化し、両者が異なる alias を持つことを保証する。</p>
 */
public class PlantUmlSoongDependencyDiagramAliasTest {

    private static List<AndroidBpModule> parse(String src) {
        return new AndroidBpParser().parseSource(src, "Android.bp");
    }

    @Test
    public void testFooBarAndFooDotBarHaveDifferentAliases() {
        // "foo-bar" と "foo.bar" が同一の PlantUML alias に潰れないことを確認する。
        // 旧実装では両者とも "m_foo_bar" になり、同一ノードへ合成されていた。
        String src = "cc_library { name: \"foo-bar\" }\n"
                + "cc_library { name: \"foo.bar\" }\n";
        List<AndroidBpModule> mods = parse(src);
        String puml = PlantUmlSoongDependencyDiagram.render(mods);
        // 両モジュールのコンポーネントラベルが出力に含まれること
        assertTrue("module label 'foo-bar' should appear: " + puml,
                puml.contains("foo-bar"));
        assertTrue("module label 'foo.bar' should appear: " + puml,
                puml.contains("foo.bar"));
        // "as m_foo_bar" という alias が 2 回出ていないこと (衝突していれば 2 回出る)。
        // 新実装ではハッシュサフィックスにより "as m_foo_bar_<hash1>" と "as m_foo_bar_<hash2>" になる。
        int firstOccurrence = puml.indexOf(" as m_foo_bar");
        int secondOccurrence = puml.indexOf(" as m_foo_bar", firstOccurrence + 1);
        // 2 つ目の alias は見つかるが、それぞれのサフィックス (直後の文字) が異なること
        assertTrue("both modules should generate m_foo_bar-prefixed aliases: " + puml,
                firstOccurrence >= 0 && secondOccurrence >= 0);
        // 直後の文字が同じなら alias が一致してしまっている (衝突)
        String afterFirst = puml.substring(firstOccurrence, Math.min(firstOccurrence + 20, puml.length()));
        String afterSecond = puml.substring(secondOccurrence, Math.min(secondOccurrence + 20, puml.length()));
        assertFalse("foo-bar and foo.bar must have DIFFERENT aliases (collision detected): "
                + afterFirst + " vs " + afterSecond,
                afterFirst.equals(afterSecond));
    }

    @Test
    public void testBothModuleLabelsAppearInOutput() {
        // 生成 PlantUML に両方のモジュール名がコンポーネントラベルとして出ることを確認する。
        String src = "cc_library { name: \"foo-bar\" }\n"
                + "cc_library { name: \"foo.bar\" }\n";
        String puml = PlantUmlSoongDependencyDiagram.render(parse(src));
        assertTrue("'foo-bar' label should appear in rendered output: " + puml,
                puml.contains("\"foo-bar"));
        assertTrue("'foo.bar' label should appear in rendered output: " + puml,
                puml.contains("\"foo.bar"));
    }

    @Test
    public void testSimpleModuleNameWithoutSpecialCharsHasNoHashSuffix() {
        // ハイフン・ドットを含まない通常の名前では alias にハッシュサフィックスが付かないことを確認する。
        // "foobar" → "m_foobar" (置換なし → ハッシュ不要)
        String src = "cc_library { name: \"foobar\" }\n";
        String puml = PlantUmlSoongDependencyDiagram.render(parse(src));
        // alias は "m_foobar" そのままになること
        assertTrue("plain module alias should be m_foobar: " + puml,
                puml.contains(" as m_foobar"));
    }
}
