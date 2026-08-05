// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JSpinner;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramScopeDialog} の OK 確定ロジック {@code buildScope()} のテスト。
 *
 * <p>{@code DialogKeyboardTest} は Escape/既定ボタンのみ。ここでは UI ウィジェット →
 * {@link DiagramScope} 写像の中核分岐を検証する: (1) 関係種別を全 off にすると
 * {@code allOf} へ復帰する救済、(2) 一部選択はその集合を保つ、(3) maxClasses spinner の反映、
 * (4) 選択した include パッケージの反映。</p>
 *
 * <p>不正 regex 経路 ({@code buildScope} が {@code JOptionPane.showMessageDialog} で
 * モーダル表示し null を返す) は、テストから呼ぶと EDT がブロックするため本テストでは
 * 扱わない (フォローアップ: regex 検証を非モーダルな純メソッドに切り出せば単体化可能)。</p>
 *
 * <p>{@link javax.swing.JDialog} 生成に display が要るためヘッドレスでは {@code Assume} で
 * skip ({@code DialogKeyboardTest} と同方針)。</p>
 */
public class DiagramScopeDialogResultTest {

    private DiagramScopeDialog dlg;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境ではスキップ (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
    }

    @After
    public void cleanup() {
        if (dlg != null) {
            GuiActionRunner.execute(() -> {
                if (dlg.isDisplayable()) {
                    dlg.dispose();
                }
            });
        }
    }

    private DiagramScopeDialog create(List<String> packages) {
        return create(packages, null);
    }

    private DiagramScopeDialog create(List<String> packages, DiagramScope initial) {
        return create(packages, initial, true);
    }

    private DiagramScopeDialog create(List<String> packages, DiagramScope initial,
                                      boolean carryOverShaping) {
        dlg = GuiActionRunner.execute(() -> new DiagramScopeDialog(
                null, packages, Collections.emptyList(), initial, carryOverShaping));
        return dlg;
    }

    private <T> T field(String name) throws Exception {
        Field f = DiagramScopeDialog.class.getDeclaredField(name);
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        T v = (T) f.get(dlg);
        return v;
    }

    private DiagramScope buildScope() throws Exception {
        Method m = DiagramScopeDialog.class.getDeclaredMethod("buildScope");
        m.setAccessible(true);
        return GuiActionRunner.execute(() -> {
            try {
                return (DiagramScope) m.invoke(dlg);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void setChecks(boolean inh, boolean impl, boolean usage) throws Exception {
        JCheckBox inheritance = field("inheritanceCheckbox");
        JCheckBox implementation = field("implementationCheckbox");
        JCheckBox use = field("usageCheckbox");
        GuiActionRunner.execute(() -> {
            inheritance.setSelected(inh);
            implementation.setSelected(impl);
            use.setSelected(usage);
            return null;
        });
    }

    /** 関係種別を全部 off にすると、意味が無いので all on へ救済される。 */
    @Test
    public void allRelationsOffFallsBackToAll() throws Exception {
        create(Collections.emptyList());
        setChecks(false, false, false);
        DiagramScope scope = buildScope();
        assertEquals("全 off は allOf に復帰", EnumSet.allOf(RelationKind.class),
                scope.getRelationKinds());
    }

    /** 一部だけ選択した関係種別はその集合が保たれる。 */
    @Test
    public void selectedRelationsArePreserved() throws Exception {
        create(Collections.emptyList());
        setChecks(true, false, false);
        DiagramScope scope = buildScope();
        assertEquals("inheritance のみ選択", EnumSet.of(RelationKind.INHERITANCE),
                scope.getRelationKinds());
    }

    /** maxClasses spinner の値が scope に反映される。 */
    @Test
    public void maxClassesSpinnerReflected() throws Exception {
        create(Collections.emptyList());
        JSpinner spinner = field("maxClassesSpinner");
        GuiActionRunner.execute(() -> {
            spinner.setValue(250);
            return null;
        });
        assertEquals("maxClasses が反映される", 250, buildScope().getMaxClasses());
    }

    /** packageList で選択した include パッケージが scope に反映される。 */
    @Test
    public void selectedIncludePackagesReflected() throws Exception {
        create(Arrays.asList("com.a", "com.b", "com.c"));
        JList<String> list = field("packageList");
        GuiActionRunner.execute(() -> {
            list.setSelectedIndices(new int[] {0, 2}); // com.a, com.c
            return null;
        });
        DiagramScope scope = buildScope();
        assertTrue("com.a が include に入る", scope.getIncludedPackages().contains("com.a"));
        assertTrue("com.c が include に入る", scope.getIncludedPackages().contains("com.c"));
        assertTrue("未選択の com.b は入らない", !scope.getIncludedPackages().contains("com.b"));
    }

    /**
     * 回帰: ダイアログにウィジェットが無い設定 (起点・強調・個別の非表示) を
     * OK が消さないこと。以前はまっさらなビルダから組み直していたため、可視性を
     * 変えただけで「このクラスを強調」も 1-hop 図の起点も失われていた。
     */
    @Test
    public void okKeepsShapingTheDialogDoesNotShow() throws Exception {
        DiagramScope initial = DiagramScope.builder()
                .seed("com.example.Order")
                .focusClass("com.example.Order")
                .excludeClass("com.example.Noise")
                .neighborHops(1)
                .build();
        create(Collections.emptyList(), initial);

        DiagramScope scope = buildScope();

        assertEquals("起点が保たれること",
                Collections.singleton("com.example.Order"), scope.getSeedQualifiedNames());
        assertEquals("強調クラスが保たれること",
                "com.example.Order", scope.getFocusClass());
        assertTrue("個別に隠したクラスが保たれること",
                scope.getExcludedQualifiedNames().contains("com.example.Noise"));
    }

    /** 元スコープが無い (新規) ときは何も引き継がず、空のまま組み上がること。 */
    @Test
    public void okWithoutInitialScopeLeavesShapingEmpty() throws Exception {
        create(Collections.emptyList());

        DiagramScope scope = buildScope();

        assertTrue(scope.getSeedQualifiedNames().isEmpty());
        assertTrue(scope.getFocusClass().isEmpty());
        assertTrue(scope.getExcludedQualifiedNames().isEmpty());
    }

    /**
     * 回帰: 新しい図のスコープを選ばせる導線 ({@code promptForScope}) では引き継がないこと。
     *
     * <p>そこでの {@code initial} は「最後にアクティブだった別のタブ」の写しでしかない。
     * 引き継ぐと前の図の起点が新しい図へ紛れ込み、起点 BFS が母集合を絞り切って<b>空の図</b>に
     * なるうえ、起点を消す UI はどこにも無い。</p>
     *
     * <p>逆に、可視性・最大クラス数・近傍ホップ数などは<b>ウィジェットとして見えている</b>ので、
     * 別タブの値で初期化されること自体は利便であり退行ではない。ここで固定するのは
     * 「画面に出ていない整形だけが漏れない」ことに限る。</p>
     */
    @Test
    public void newDiagramScopeDoesNotInheritTheOtherTabsShaping() throws Exception {
        DiagramScope otherTab = DiagramScope.builder()
                .seed("com.example.Order")
                .focusClass("com.example.Order")
                .excludeClass("com.example.Noise")
                .neighborHops(1)
                .build();
        create(Collections.emptyList(), otherTab, false);

        DiagramScope scope = buildScope();

        assertTrue("別タブの起点を引き継がないこと", scope.getSeedQualifiedNames().isEmpty());
        assertTrue("別タブの強調クラスを引き継がないこと", scope.getFocusClass().isEmpty());
        assertTrue("別タブの個別非表示を引き継がないこと",
                scope.getExcludedQualifiedNames().isEmpty());
    }

    /**
     * 回帰: 何も選ばずに OK を押した結果は「空スコープ」= キャンセル相当であること。
     *
     * <p>{@code isEmpty()} が {@code parseMode == null} を要求する一方、{@code buildScope} は
     * FULL / HEADERS_ONLY のどちらかを<b>必ず</b>設定するため、このダイアログの結果は
     * 常に空でなくなっていた。そのため大規模プロジェクトのガードで「スコープを選ぶ」を選び、
     * 何も選ばずに OK を押しても null にならず、<b>ガードが防ごうとした全体描画がそのまま
     * 走る</b>うえ、空でないスコープなのでタブキーにハッシュが付いて既存の全体図タブと
     * 重複していた。ラウンド 12 でこれを「別事象」として見送ったのは誤りだった。</p>
     */
    @Test
    public void okWithNothingSelectedYieldsAnEmptyScope() throws Exception {
        create(Collections.emptyList());

        DiagramScope scope = buildScope();

        assertTrue("既定の FULL は絞り込みではないので空スコープであること: "
                + scope.signature(), scope.isEmpty());
    }

    /**
     * 回帰: 引き継いだ整形しか持たない結果を「空」と判定しないこと。
     *
     * <p>呼び出し側 ({@code DiagramEntryDialogs}) は {@code picked.isEmpty()} なら結果を
     * {@code null} へ潰す。{@code focusClass} を {@code isEmpty()} に数えていなかったため、
     * せっかく {@code carryOverUnshownSettings} で運んだ強調クラスが、そのすぐあとに
     * 捨てられていた — 強調したクラスがあるタブでスコープダイアログを開いて何も変えずに
     * OK を押すだけで強調が外れる。引き継ぎを入れた変更自体が無効化されていた。</p>
     */
    @Test
    public void aCarriedOverFocusClassMakesTheResultNonEmpty() throws Exception {
        create(Collections.emptyList(),
                DiagramScope.builder().focusClass("com.example.Order").build());

        DiagramScope scope = buildScope();

        assertEquals("com.example.Order", scope.getFocusClass());
        assertFalse("強調クラスだけでも空スコープ (=キャンセル相当) にしないこと: "
                + scope.signature(), scope.isEmpty());
    }

    /** 非退行: HEADERS_ONLY を選んだら「絞っている」= 空ではないこと。 */
    @Test
    public void headersOnlyIsNotAnEmptyScope() throws Exception {
        create(Collections.emptyList());
        javax.swing.JRadioButton headers = field("parseModeHeaders");
        GuiActionRunner.execute(() -> {
            headers.setSelected(true);
            return null;
        });

        assertFalse("HEADERS_ONLY は絞り込みなので空ではないこと", buildScope().isEmpty());
    }
}
