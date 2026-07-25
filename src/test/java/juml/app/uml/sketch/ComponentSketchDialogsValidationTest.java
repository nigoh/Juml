// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ComponentSketchDialogs#isValidNewId(String, ComponentSketchModel, ComponentNode)}
 * の id 形式チェック・重複チェックを検証する (モーダルを介さない純ロジックテスト。headless 可)。
 */
public class ComponentSketchDialogsValidationTest {

    private ComponentSketchModel model;
    private ComponentNode alpha;
    private ComponentNode beta;

    @Before
    public void setUp() {
        model = new ComponentSketchModel();
        alpha = new ComponentNode(ComponentNode.Kind.COMPONENT, "Alpha", null, 0, 0);
        beta = new ComponentNode(ComponentNode.Kind.COMPONENT, "Beta", null, 100, 0);
        model.getNodes().add(alpha);
        model.getNodes().add(beta);
    }

    @Test
    public void isValidNewId_acceptsNewUniqueId() {
        assertTrue("未使用の妥当な id は受理されるはず",
                ComponentSketchDialogs.isValidNewId("Gamma", model, alpha));
    }

    @Test
    public void isValidNewId_rejectsIdUsedByAnotherNode() {
        assertFalse("既存の別要素と同じ id への変更は拒否されるはず",
                ComponentSketchDialogs.isValidNewId("Beta", model, alpha));
    }

    @Test
    public void isValidNewId_acceptsUnchangedOwnId() {
        // リネームせず自分自身の id をそのまま確定するケース (target == same)。
        assertTrue("自分自身と同じ id (リネームなし) は受理されるはず",
                ComponentSketchDialogs.isValidNewId("Alpha", model, alpha));
    }

    @Test
    public void isValidNewId_rejectsEmptyString() {
        assertFalse("空文字は id として不正なはず",
                ComponentSketchDialogs.isValidNewId("", model, alpha));
    }

    @Test
    public void isValidNewId_rejectsLeadingDigit() {
        assertFalse("先頭が数字の id は不正なはず (PlantUML 識別子の境界)",
                ComponentSketchDialogs.isValidNewId("1Gamma", model, alpha));
    }

    @Test
    public void isValidNewId_rejectsInternalSpace() {
        assertFalse("空白を含む id は不正なはず",
                ComponentSketchDialogs.isValidNewId("Al pha", model, alpha));
    }

    @Test
    public void isValidNewId_rejectsHyphen() {
        assertFalse("ハイフンを含む id は不正なはず (\\w に含まれない記号)",
                ComponentSketchDialogs.isValidNewId("Al-pha", model, alpha));
    }

    @Test
    public void isValidNewId_acceptsUnderscoreAndDollarPrefix() {
        assertTrue("先頭がアンダースコアの id は許容されるはず",
                ComponentSketchDialogs.isValidNewId("_Gamma", model, alpha));
        assertTrue("先頭がドル記号の id は許容されるはず",
                ComponentSketchDialogs.isValidNewId("$Gamma", model, alpha));
    }
}
