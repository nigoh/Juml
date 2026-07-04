// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;
import juml.app.uml.MenuBarBuilder.Callbacks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link AppCommands#from(Callbacks)} のテスト。
 *
 * <p>コマンドパレットとメニューは同じ {@link Callbacks} を単一ソースとして共有する。
 * ここでは GUI を一切起動せず純ロジックとして、(1) null アクションがコマンド化されない、
 * (2) 各コマンドのアクションが正しいコールバックへ結線され混線しない、
 * (3) ショートカット文字列が伝播する、(4) preset が非 CUSTOM の数だけ展開される、
 * を検証する。コマンド実行経路の回帰 (例: ボタンを押しても別の処理が走る) を守る。</p>
 */
public class AppCommandsTest {

    /** アクションが 1 つも設定されていなければコマンドは 0 件 (空状態)。 */
    @Test
    public void emptyCallbacksProduceNoCommands() {
        List<CommandPalette.Command> cmds = AppCommands.from(new Callbacks());
        assertTrue("null だらけの Callbacks からはコマンドが生成されないはず", cmds.isEmpty());
    }

    /** 設定されたアクションだけがコマンド化され、ラベルとショートカットが付く。 */
    @Test
    public void onlySetActionsBecomeCommandsWithLabelAndShortcut() {
        Callbacks cb = new Callbacks();
        cb.chooseProject = () -> { };
        List<CommandPalette.Command> cmds = AppCommands.from(cb);

        assertEquals("設定したのは 1 アクションだけ", 1, cmds.size());
        CommandPalette.Command c = cmds.get(0);
        assertNotNull("ラベルが解決されているはず", c.label);
        assertFalse("ラベルは空でないはず", c.label.isEmpty());
        assertNotNull("openProject はショートカット (Ctrl/⌘+O) を持つはず", c.shortcut);
        assertTrue("ショートカット表記に 'O' を含むはず: " + c.shortcut,
                c.shortcut.contains("O"));
    }

    /**
     * Save As (Export) のショートカット表記は Ctrl+Shift+S。
     * 実アクセラレータ (MenuBarBuilder) は Shift 付きなのに、以前はパレット表記が
     * Ctrl+S (PUML 保存用) と食い違っていた。
     */
    @Test
    public void saveAsShortcutIncludesShift() {
        Callbacks cb = new Callbacks();
        cb.chooseAndExport = () -> { };
        List<CommandPalette.Command> cmds = AppCommands.from(cb);
        assertEquals(1, cmds.size());
        CommandPalette.Command c = cmds.get(0);
        assertNotNull("Save As はショートカットを持つはず", c.shortcut);
        assertTrue("Save As のショートカット表記は Shift を含むはず: " + c.shortcut,
                c.shortcut.contains("Shift"));
        assertTrue("Save As のショートカット表記は S を含むはず: " + c.shortcut,
                c.shortcut.contains("S"));
    }

    /** 単一アクションの結線: そのコマンドを実行すると当該コールバックが呼ばれる。 */
    @Test
    public void actionWiringInvokesTheBackingCallback() {
        AtomicBoolean fired = new AtomicBoolean(false);
        Callbacks cb = new Callbacks();
        cb.toggleSidebar = () -> fired.set(true);

        List<CommandPalette.Command> cmds = AppCommands.from(cb);
        assertEquals(1, cmds.size());
        cmds.get(0).action.run();
        assertTrue("コマンド実行で toggleSidebar が呼ばれるはず", fired.get());
    }

    /** 複数アクションが混線しない: 先頭コマンド実行は片方のみを発火させる。 */
    @Test
    public void distinctActionsDoNotCrossWire() {
        AtomicBoolean zoomInFired = new AtomicBoolean(false);
        AtomicBoolean zoomOutFired = new AtomicBoolean(false);
        Callbacks cb = new Callbacks();
        cb.zoomIn = () -> zoomInFired.set(true);
        cb.zoomOut = () -> zoomOutFired.set(true);

        List<CommandPalette.Command> cmds = AppCommands.from(cb);
        assertEquals("zoomIn / zoomOut の 2 コマンド", 2, cmds.size());

        // from() の生成順は zoomIn → zoomOut。先頭を実行すると片方だけが発火する。
        cmds.get(0).action.run();
        assertTrue("1 つ目の実行で zoomIn が発火するはず", zoomInFired.get());
        assertFalse("混線して zoomOut まで発火してはならない", zoomOutFired.get());

        cmds.get(1).action.run();
        assertTrue("2 つ目の実行で zoomOut が発火するはず", zoomOutFired.get());
    }

    /** navigateBack/Forward アクションがコマンドパレットに登録される。 */
    @Test
    public void navigateBackForwardRegisteredAsCommands() {
        AtomicBoolean backFired = new AtomicBoolean(false);
        AtomicBoolean fwdFired = new AtomicBoolean(false);
        Callbacks cb = new Callbacks();
        cb.navigateBack = () -> backFired.set(true);
        cb.navigateForward = () -> fwdFired.set(true);

        List<CommandPalette.Command> cmds = AppCommands.from(cb);
        assertEquals(2, cmds.size());
        cmds.get(0).action.run();
        assertTrue("navigateBack が発火するはず", backFired.get());
        cmds.get(1).action.run();
        assertTrue("navigateForward が発火するはず", fwdFired.get());
    }

    /** applyPreset Consumer は非 CUSTOM の preset 数だけコマンドに展開され、値が渡る。 */
    @Test
    public void presetConsumerExpandsToOneCommandPerNonCustomPreset() {
        List<DiagramPreset> received = new ArrayList<>();
        Callbacks cb = new Callbacks();
        cb.applyPreset = received::add;

        List<CommandPalette.Command> cmds = AppCommands.from(cb);

        Set<DiagramPreset> expected = EnumSet.allOf(DiagramPreset.class);
        expected.remove(DiagramPreset.CUSTOM);

        assertEquals("applyPreset だけ設定したので preset コマンドのみが並ぶ",
                expected.size(), cmds.size());

        for (CommandPalette.Command c : cmds) {
            c.action.run();
        }
        assertEquals("全 preset コマンドが accept を呼ぶはず", expected.size(), received.size());
        assertTrue("CUSTOM は preset コマンド化されない", expected.containsAll(received));
        assertTrue("非 CUSTOM の全 preset が網羅されるはず", received.containsAll(expected));
    }

    /**
     * 図種切替 Consumer は「メニューのラジオに出る図種」の数だけコマンドに展開され、
     * 正しい {@link DiagramKind} が渡る。メソッド系図種 (SEQUENCE/ACTIVITY/CALLGRAPH) は
     * メニューのラジオから外れているため、パレットにも出さない。
     */
    @Test
    public void diagramKindConsumerExpandsToOneCommandPerMenuKind() {
        List<DiagramKind> received = new ArrayList<>();
        Callbacks cb = new Callbacks();
        cb.selectDiagramKindFromMenu = received::add;

        List<CommandPalette.Command> cmds = AppCommands.from(cb);

        Set<DiagramKind> expected = EnumSet.allOf(DiagramKind.class);
        expected.removeAll(ToolBarBuilder.DIAGRAMS_METHOD);

        assertEquals("メニューに出る図種の数だけコマンドが並ぶ", expected.size(), cmds.size());

        for (CommandPalette.Command c : cmds) {
            c.action.run();
        }
        assertEquals("全図種コマンドが accept を呼ぶはず", expected.size(), received.size());
        assertTrue("メソッド系図種はコマンド化されない",
                java.util.Collections.disjoint(received, ToolBarBuilder.DIAGRAMS_METHOD));
        assertTrue("メニュー掲載の全図種が網羅されるはず", received.containsAll(expected));
    }
}
