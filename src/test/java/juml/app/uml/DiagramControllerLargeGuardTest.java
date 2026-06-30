// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramController#isWholeProjectDiagramLarge(DiagramKind, int)} の
 * 事前ガード判定ロジックを検証する。
 *
 * <p>全体 Class / Inheritance 図のみ、クラス数がしきい値を超えたときにガード
 * (スコープ提案ダイアログ) を出す。それ以外の図種やしきい値以下では出さない。</p>
 */
public class DiagramControllerLargeGuardTest {

    @Test
    public void largeClassDiagramTriggersGuard() {
        assertTrue(DiagramController.isWholeProjectDiagramLarge(DiagramKind.CLASS, 41));
    }

    @Test
    public void largeInheritanceDiagramTriggersGuard() {
        assertTrue(DiagramController.isWholeProjectDiagramLarge(DiagramKind.INHERITANCE, 1000));
    }

    @Test
    public void smallClassDiagramDoesNotTrigger() {
        assertFalse(DiagramController.isWholeProjectDiagramLarge(DiagramKind.CLASS, 40));
        assertFalse(DiagramController.isWholeProjectDiagramLarge(DiagramKind.CLASS, 0));
    }

    @Test
    public void nonStructuralKindsNeverTrigger() {
        // パッケージ図やコンポーネント図などは巨大でもこのガードの対象外。
        assertFalse(DiagramController.isWholeProjectDiagramLarge(DiagramKind.PACKAGE, 100000));
        assertFalse(DiagramController.isWholeProjectDiagramLarge(DiagramKind.COMPONENT, 100000));
        assertFalse(DiagramController.isWholeProjectDiagramLarge(DiagramKind.MANIFEST, 100000));
    }
}
