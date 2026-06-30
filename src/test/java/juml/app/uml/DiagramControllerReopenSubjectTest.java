// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * {@link DiagramController#reopenRequestFor(TreeNodeOpenRequest, DiagramKind)} の
 * 「フォーカス題材を引き継いで図を開き直す」判定を検証する。
 *
 * <p>回帰の主眼: 単体クラス (スコープ付き) のタブを見ている最中に Class を選んでも、
 * プロジェクト全体図へフォールバックして「図が大きい」大規模ガードが出ないこと
 * (= その題材のクラス図を開き直すリクエストが返ること)。</p>
 */
public class DiagramControllerReopenSubjectTest {

    private static JavaClassInfo classInfo(String fqn) {
        JavaClassInfo ci = new JavaClassInfo();
        int dot = fqn.lastIndexOf('.');
        if (dot >= 0) {
            ci.setPackageName(fqn.substring(0, dot));
            ci.setSimpleName(fqn.substring(dot + 1));
        } else {
            ci.setSimpleName(fqn);
        }
        return ci;
    }

    private static JavaMethodInfo method(String name) {
        JavaMethodInfo mi = new JavaMethodInfo();
        mi.setName(name);
        return mi;
    }

    // --- クラス題材: Class を選んだら全体図ではなくその単体クラス図を開き直す ---------

    @Test
    public void classSubjectReopensSameClassForClassKind() {
        JavaClassInfo ci = classInfo("com.example.Foo");
        TreeNodeOpenRequest focused = TreeNodeOpenRequest.classNode(ci);

        TreeNodeOpenRequest reopen =
                DiagramController.reopenRequestFor(focused, DiagramKind.CLASS);

        // null でなければ全体図 (openProjectWide) へは行かない = 大規模ガードも出ない。
        assertEquals(TreeNodeOpenRequest.Target.CLASS, reopen.target);
        assertEquals("CLASS:com.example.Foo", reopen.tabKey());
    }

    @Test
    public void classSubjectDoesNotHijackNonClassKinds() {
        // 単体クラスタブから Package を選んだら、その題材ではなく従来どおり
        // 全体パッケージ図 (openKindAsTab) へ委譲したいので null を返す。
        TreeNodeOpenRequest focused = TreeNodeOpenRequest.classNode(classInfo("com.example.Foo"));
        assertNull(DiagramController.reopenRequestFor(focused, DiagramKind.PACKAGE));
        assertNull(DiagramController.reopenRequestFor(focused, DiagramKind.INHERITANCE));
    }

    // --- パッケージ / モジュール題材も Class はスコープを維持 -----------------------

    @Test
    public void packageSubjectReopensSamePackageForClassKind() {
        TreeNodeOpenRequest focused = TreeNodeOpenRequest.pkg("com.example.svc");
        TreeNodeOpenRequest reopen =
                DiagramController.reopenRequestFor(focused, DiagramKind.CLASS);
        assertEquals(TreeNodeOpenRequest.Target.PACKAGE, reopen.target);
        assertEquals("PKG:com.example.svc", reopen.tabKey());
    }

    @Test
    public void moduleSubjectReopensSameModuleForClassKind() {
        TreeNodeOpenRequest focused = TreeNodeOpenRequest.module("app");
        TreeNodeOpenRequest reopen =
                DiagramController.reopenRequestFor(focused, DiagramKind.CLASS);
        assertEquals(TreeNodeOpenRequest.Target.MODULE, reopen.target);
        assertEquals("MOD:app", reopen.tabKey());
    }

    // --- メソッド題材は既存どおり method 系図種のみ引き継ぐ -------------------------

    @Test
    public void methodSubjectReopensForMethodKinds() {
        TreeNodeOpenRequest focused =
                TreeNodeOpenRequest.method(classInfo("com.example.Foo"), method("bar"),
                        DiagramKind.SEQUENCE);
        TreeNodeOpenRequest reopen =
                DiagramController.reopenRequestFor(focused, DiagramKind.ACTIVITY);
        assertEquals(TreeNodeOpenRequest.Target.METHOD, reopen.target);
        assertEquals(DiagramKind.ACTIVITY, reopen.kind);
    }

    @Test
    public void methodSubjectDoesNotHijackClassKind() {
        // メソッドタブから Class を選んだら全体クラス図 (従来動作) へ委譲。
        TreeNodeOpenRequest focused =
                TreeNodeOpenRequest.method(classInfo("com.example.Foo"), method("bar"),
                        DiagramKind.SEQUENCE);
        assertNull(DiagramController.reopenRequestFor(focused, DiagramKind.CLASS));
    }

    // --- 題材なし (全体図タブ / null) は引き継がない -------------------------------

    @Test
    public void nullFocusReturnsNull() {
        assertNull(DiagramController.reopenRequestFor(null, DiagramKind.CLASS));
    }
}
