// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployNode;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link DeploySketchDialogs#isValidNewId(String, DeploySketchModel, DeployNode)}
 * の id 形式チェック・重複チェックを検証する (モーダルを介さない純ロジックテスト。headless 可)。
 */
public class DeploySketchDialogsValidationTest {

    private DeploySketchModel model;
    private DeployNode srv;
    private DeployNode db;

    @Before
    public void setUp() {
        model = new DeploySketchModel();
        srv = new DeployNode(DeployNode.Kind.NODE, "Srv", null, 0, 0);
        db = new DeployNode(DeployNode.Kind.DATABASE, "Db", null, 100, 0);
        model.getNodes().add(srv);
        model.getNodes().add(db);
    }

    @Test
    public void isValidNewId_acceptsNewUniqueId() {
        assertTrue("未使用の妥当な id は受理されるはず",
                DeploySketchDialogs.isValidNewId("Cdn", model, srv));
    }

    @Test
    public void isValidNewId_rejectsIdUsedByAnotherNode() {
        assertFalse("既存の別ノードと同じ id への変更は拒否されるはず",
                DeploySketchDialogs.isValidNewId("Db", model, srv));
    }

    @Test
    public void isValidNewId_acceptsUnchangedOwnId() {
        assertTrue("自分自身と同じ id (リネームなし) は受理されるはず",
                DeploySketchDialogs.isValidNewId("Srv", model, srv));
    }

    @Test
    public void isValidNewId_rejectsEmptyString() {
        assertFalse("空文字は id として不正なはず",
                DeploySketchDialogs.isValidNewId("", model, srv));
    }

    @Test
    public void isValidNewId_rejectsLeadingDigit() {
        assertFalse("先頭が数字の id は不正なはず (PlantUML 識別子の境界)",
                DeploySketchDialogs.isValidNewId("1Srv", model, srv));
    }

    @Test
    public void isValidNewId_rejectsInternalSpace() {
        assertFalse("空白を含む id は不正なはず",
                DeploySketchDialogs.isValidNewId("Sr v", model, srv));
    }

    @Test
    public void isValidNewId_rejectsHyphen() {
        assertFalse("ハイフンを含む id は不正なはず (\\w に含まれない記号)",
                DeploySketchDialogs.isValidNewId("Sr-v", model, srv));
    }

    @Test
    public void isValidNewId_duplicateCheckCoversNestedChildren() {
        // findNode は入れ子の子ノードも含めて探索するため、コンテナ配下の id との
        // 重複も拒否されるはず (DeploySketchModel の id 名前空間はグローバル)。
        DeployNode child = new DeployNode(DeployNode.Kind.ARTIFACT, "Child", null, 0, 0);
        model.addChild(srv, child);
        assertFalse("コンテナ配下の子ノードと同じ id への変更も拒否されるはず",
                DeploySketchDialogs.isValidNewId("Child", model, db));
    }
}
