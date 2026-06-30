// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.formats.uml.PlantUmlClassDiagram;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link UmlOverrides} の CLI オプション反映テスト。
 *
 * <p>特に interactiveLinks (GUI プレビュー用 juml:// リンク) が CLI 出力に漏れない
 * 契約を守ること: preset (balanced 等) が GUI 用に ON にしても、CLI では
 * {@code --interactive-svg} 明示時のみ有効になる。</p>
 */
public class UmlOverridesInteractiveLinksTest {

    private static PlantUmlClassDiagram.Options apply(String... args) {
        CliOptions opts = new CliOptions();
        opts.parse(args);
        UmlOverrides ov = UmlOverrides.build(opts);
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        ov.applyTo(o);
        return o;
    }

    @Test
    public void defaultPresetDoesNotEnableInteractiveLinksForCli() {
        // 既定 (preset=balanced 相当) でも CLI 出力に juml:// リンクを出さない
        assertFalse(apply("-c").interactiveLinks);
    }

    @Test
    public void interactiveSvgFlagEnablesInteractiveLinks() {
        assertTrue(apply("-c", "--interactive-svg").interactiveLinks);
    }
}
