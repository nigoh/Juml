// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JMenuItem;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static juml.app.uml.GuiMonkeyRuntime.act;
import static juml.app.uml.GuiMonkeyRuntime.checkInvariants;
import static juml.app.uml.GuiMonkeyRuntime.chooser;
import static juml.app.uml.GuiMonkeyRuntime.ensureRobot;
import static juml.app.uml.GuiMonkeyRuntime.frame;
import static juml.app.uml.GuiMonkeyRuntime.keyName;
import static juml.app.uml.GuiMonkeyRuntime.keys;
import static juml.app.uml.GuiMonkeyRuntime.killDialogs;
import static juml.app.uml.GuiMonkeyRuntime.log;
import static juml.app.uml.GuiMonkeyRuntime.mainTabs;
import static juml.app.uml.GuiMonkeyRuntime.onEdt;
import static juml.app.uml.GuiMonkeyRuntime.palette;
import static juml.app.uml.GuiMonkeyRuntime.tabPane;
import static juml.app.uml.GuiMonkeyRuntime.tree;

/**
 * S21: シード付きランダム操作 (fuzz)。決定的シナリオが辿らない操作の「組み合わせ」を、
 * ドロップダウン / パレット / ツリー / ショートカット / プレビュー / タブ切替 / エディタ入力 の
 * プールから乱択して連打する。同じシードなら同じ操作列になるため、所見は
 * {@code --seed} で再現できる。
 */
final class GuiMonkeyFuzz {

    /** 終了系を含まない、繰り返し押しても安全なショートカット群。 */
    private static final int[][] SAFE_KEYS = {
        { KeyEvent.VK_CONTROL, KeyEvent.VK_EQUALS }, { KeyEvent.VK_CONTROL, KeyEvent.VK_MINUS },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_0 }, { KeyEvent.VK_F5 }, { KeyEvent.VK_CONTROL, KeyEvent.VK_B },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_F }, { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_P }, { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_ALT, KeyEvent.VK_LEFT }, { KeyEvent.VK_ALT, KeyEvent.VK_RIGHT },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_W }, { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_T },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_1 }, { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_2 },
        { KeyEvent.VK_ALT, KeyEvent.VK_1 }, { KeyEvent.VK_ALT, KeyEvent.VK_2 }, { KeyEvent.VK_ALT, KeyEvent.VK_3 },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_M },
    };

    private GuiMonkeyFuzz() {
    }

    static void fuzz(long seed, int steps) throws Exception {
        Random rnd = new Random(seed);
        boolean hasRobot = ensureRobot();
        List<DiagramKind> kinds = new ArrayList<>();
        for (DiagramKind k : DiagramKind.values()) {
            if (chooser.itemFor(k) != null) {
                kinds.add(k);
            }
        }
        List<CommandPalette.Command> commands = new ArrayList<>();
        if (palette != null) {
            for (CommandPalette.Command c : palette) {
                if (!GuiMonkeyScenarios.isDangerous(c.label)) {
                    commands.add(c);
                }
            }
        }
        log("fuzz seed=" + seed + " steps=" + steps + " kinds=" + kinds.size() + " commands=" + commands.size());
        for (int i = 0; i < steps; i++) {
            int op = rnd.nextInt(10);
            String tag = "fuzz[" + i + "]";
            switch (op) {
                case 0:
                    dropdown(kinds.get(rnd.nextInt(kinds.size())), tag);
                    break;
                case 1:
                    if (!commands.isEmpty()) {
                        CommandPalette.Command c = commands.get(rnd.nextInt(commands.size()));
                        act(tag + " palette:" + c.label, c.action);
                    }
                    break;
                case 2:
                    treeRow(rnd, tag);
                    break;
                case 3:
                    if (hasRobot) {
                        int[] combo = SAFE_KEYS[rnd.nextInt(SAFE_KEYS.length)];
                        onEdt(() -> frame.toFront());
                        act(keyName(tag + " key:", combo), () -> keys(combo));
                    }
                    break;
                case 4:
                    if (hasRobot) {
                        GuiMonkeyScenarios.previewMouseStep(rnd, i);
                    }
                    break;
                case 5:
                    if (mainTabs != null) {
                        int n = onEdt(mainTabs::getTabCount);
                        if (n > 0) {
                            final int idx = rnd.nextInt(n);
                            act(tag + " main-tab[" + idx + "]", () -> mainTabs.setSelectedIndex(idx));
                        }
                    }
                    break;
                case 6:
                    DiagramKind[] method = ToolBarBuilder.DIAGRAMS_METHOD.toArray(new DiagramKind[0]);
                    final DiagramKind mk = method[rnd.nextInt(method.length)];
                    act(tag + " method-kind:" + mk, () -> tabPane.switchActiveMethodKind(mk));
                    break;
                case 7:
                    GuiMonkeyScenarios.runPalette(Messages.get("cmd.view.zoomIn"), Messages.get("cmd.view.zoomFit"));
                    break;
                case 8:
                    if (hasRobot && onEdt(() -> tabPane.activeTabIsPumlEditor())) {
                        String sn = GuiMonkeyScenarios.EDITOR_SNIPPETS[rnd.nextInt(GuiMonkeyScenarios.EDITOR_SNIPPETS.length)];
                        act(tag + " type:" + sn.replace('\n', '|'), () -> GuiMonkeyRuntime.typeAscii(sn));
                        act(tag + " key:Escape", () -> keys(KeyEvent.VK_ESCAPE));
                    } else {
                        GuiMonkeyScenarios.runPalette(Messages.get("cmd.file.newUml").trim());
                    }
                    break;
                default:
                    if (tree != null) {
                        final int rows = onEdt(tree::getRowCount);
                        if (rows > 0) {
                            final int row = rnd.nextInt(rows);
                            act(tag + " tree-expand[" + row + "]", () -> tree.expandRow(row));
                        }
                    }
                    break;
            }
            killDialogs();
            if (i % 5 == 4) {
                checkInvariants(tag);
            }
        }
        checkInvariants("after-fuzz");
    }

    private static void dropdown(DiagramKind k, String tag) throws Exception {
        JMenuItem item = chooser.itemFor(k);
        if (item != null && onEdt(item::isEnabled)) {
            act(tag + " dropdown:" + k, item::doClick);
            checkInvariants(tag + " dropdown:" + k);
        }
    }

    private static void treeRow(Random rnd, String tag) throws Exception {
        if (tree == null) {
            return;
        }
        int rows = onEdt(tree::getRowCount);
        if (rows == 0) {
            return;
        }
        final int row = rnd.nextInt(rows);
        if (rnd.nextInt(3) == 0) {
            act(tag + " tree-dblclick[" + row + "]", () -> GuiMonkeyScenarios.dblClickRow(row));
        } else {
            act(tag + " tree-select[" + row + "]", () -> tree.setSelectionRow(row));
        }
        checkInvariants(tag + " tree[" + row + "]");
    }
}
