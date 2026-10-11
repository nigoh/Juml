// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.AbstractButton;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.JTree;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static juml.app.uml.GuiMonkeyRuntime.act;
import static juml.app.uml.GuiMonkeyRuntime.button1;
import static juml.app.uml.GuiMonkeyRuntime.button3;
import static juml.app.uml.GuiMonkeyRuntime.checkInvariants;
import static juml.app.uml.GuiMonkeyRuntime.chooser;
import static juml.app.uml.GuiMonkeyRuntime.click;
import static juml.app.uml.GuiMonkeyRuntime.collectAll;
import static juml.app.uml.GuiMonkeyRuntime.diagramItems;
import static juml.app.uml.GuiMonkeyRuntime.ensureRobot;
import static juml.app.uml.GuiMonkeyRuntime.findAny;
import static juml.app.uml.GuiMonkeyRuntime.findShowing;
import static juml.app.uml.GuiMonkeyRuntime.frame;
import static juml.app.uml.GuiMonkeyRuntime.keyName;
import static juml.app.uml.GuiMonkeyRuntime.keys;
import static juml.app.uml.GuiMonkeyRuntime.killDialogs;
import static juml.app.uml.GuiMonkeyRuntime.log;
import static juml.app.uml.GuiMonkeyRuntime.mainTabs;
import static juml.app.uml.GuiMonkeyRuntime.onEdt;
import static juml.app.uml.GuiMonkeyRuntime.palette;
import static juml.app.uml.GuiMonkeyRuntime.quiesce;
import static juml.app.uml.GuiMonkeyRuntime.record;
import static juml.app.uml.GuiMonkeyRuntime.robot;
import static juml.app.uml.GuiMonkeyRuntime.stack;
import static juml.app.uml.GuiMonkeyRuntime.tabPane;
import static juml.app.uml.GuiMonkeyRuntime.tree;

/**
 * {@link GuiMonkey} の決定的シナリオ群 (S1〜S20)。各メソッドは 1 つの操作面を機械的に叩き、
 * 例外・不変条件違反は {@link GuiMonkeyRuntime#record} で採取する (合否判定はしない)。
 */
final class GuiMonkeyScenarios {

    private static final int[] KEY_SHIFT_F = { KeyEvent.VK_SHIFT, KeyEvent.VK_F };

    private GuiMonkeyScenarios() {
    }

    // ── S3: ツリー巡回 ─────────────────────────────────────────
    static void treeWalk(int maxRows) throws Exception {
        if (tree == null) {
            record("harness", "JTree が見つからない");
            return;
        }
        // 段階的に展開 (遅延ロードのノードは展開で読み込まれる)
        for (int pass = 0; pass < 6; pass++) {
            int rows = onEdt(tree::getRowCount);
            for (int r = 0; r < Math.min(rows, 400); r++) {
                final int row = r;
                onEdt(() -> {
                    if (tree.isCollapsed(row)) {
                        tree.expandRow(row);
                    }
                });
            }
            quiesce(1_500);
        }
        int rows = onEdt(tree::getRowCount);
        log("tree rows=" + rows);
        int limit = Math.min(rows, maxRows);
        for (int r = 0; r < limit; r++) {
            final int row = r;
            String label = onEdt(() -> String.valueOf(tree.getPathForRow(row) == null ? "?"
                    : tree.getPathForRow(row).getLastPathComponent()));
            act("tree-select[" + row + "]:" + label, () -> tree.setSelectionRow(row));
            if (r % 7 == 3) {
                // ダブルクリック相当も混ぜる (単一選択とは別経路の可能性)
                act("tree-dblclick[" + row + "]", () -> dblClickRow(row));
            }
            checkInvariants("tree-select[" + row + "]");
        }
    }

    static void dblClickRow(int row) {
        try {
            Rectangle b = tree.getRowBounds(row);
            if (b == null) {
                return;
            }
            MouseEvent press = new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                    b.x + 5, b.y + b.height / 2, 2, false, MouseEvent.BUTTON1);
            MouseEvent click = new MouseEvent(tree, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                    b.x + 5, b.y + b.height / 2, 2, false, MouseEvent.BUTTON1);
            tree.dispatchEvent(press);
            tree.dispatchEvent(click);
        } catch (RuntimeException ex) {
            record("exception", "dblClick dispatch: " + stack(ex));
        }
    }

    // ── S4: メソッド系図種の切替バー ───────────────────────────
    static void methodKindBar() throws Exception {
        DiagramKind active = onEdt(() -> tabPane.activeTabKind());
        log("active kind before method bar=" + active);
        if (active == null || !ToolBarBuilder.DIAGRAMS_METHOD.contains(active)) {
            // ツリーからメソッド行を探して選ぶ
            int rows = tree == null ? 0 : onEdt(tree::getRowCount);
            for (int r = 0; r < rows; r++) {
                final int row = r;
                String label = onEdt(() -> String.valueOf(tree.getPathForRow(row).getLastPathComponent()));
                if (label.contains("(") && label.contains(")")) {
                    act("select-method-row[" + row + "]:" + label, () -> tree.setSelectionRow(row));
                    active = onEdt(() -> tabPane.activeTabKind());
                    if (active != null && ToolBarBuilder.DIAGRAMS_METHOD.contains(active)) {
                        break;
                    }
                }
            }
        }
        active = onEdt(() -> tabPane.activeTabKind());
        if (active == null || !ToolBarBuilder.DIAGRAMS_METHOD.contains(active)) {
            log("no method tab available; skip method bar");
            return;
        }
        for (DiagramKind k : new DiagramKind[] { DiagramKind.ACTIVITY, DiagramKind.CALLGRAPH,
                DiagramKind.SEQUENCE, DiagramKind.ACTIVITY }) {
            act("switchActiveMethodKind:" + k, () -> tabPane.switchActiveMethodKind(k));
            checkInvariants("methodbar:" + k);
        }
        // メソッドタブ中に構造系図種をドロップダウンで選ぶ (題材引き継ぎ経路)
        JMenuItem it = chooser.itemFor(DiagramKind.CLASS);
        if (it != null && onEdt(it::isEnabled)) {
            act("dropdown-from-method-tab:CLASS", it::doClick);
            checkInvariants("dropdown-from-method-tab");
        }
    }

    // ── S1: メニューバー全項目 ─────────────────────────────────
    static void menuWalk() throws Exception {
        JMenuBar bar = onEdt(frame::getJMenuBar);
        if (bar == null) {
            return;
        }
        List<JMenuItem> items = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        onEdt(() -> {
            for (int i = 0; i < bar.getMenuCount(); i++) {
                collectMenuItems(bar.getMenu(i), bar.getMenu(i).getText(), items, paths);
            }
        });
        log("menu items=" + items.size());
        for (int i = 0; i < items.size(); i++) {
            JMenuItem item = items.get(i);
            String path = paths.get(i);
            if (isDangerous(path)) {
                continue;
            }
            boolean enabled = onEdt(item::isEnabled);
            if (!enabled) {
                log("menu skip (disabled): " + path);
                continue;
            }
            act("menu:" + path, item::doClick);
            checkInvariants("menu:" + path);
        }
    }

    /** 終了系・最近使ったプロジェクト (別プロジェクトへ飛ぶ) は機械的に叩かない。 */
    static boolean isDangerous(String label) {
        String low = label.toLowerCase();
        return low.contains("exit") || low.contains("終了") || low.contains("quit")
                || low.contains("recent") || low.contains("最近");
    }

    static void collectMenuItems(JMenu menu, String path, List<JMenuItem> items, List<String> paths) {
        for (Component c : menu.getMenuComponents()) {
            if (c instanceof JMenu) {
                JMenu sub = (JMenu) c;
                collectMenuItems(sub, path + " > " + sub.getText(), items, paths);
            } else if (c instanceof JMenuItem) {
                items.add((JMenuItem) c);
                paths.add(path + " > " + ((JMenuItem) c).getText());
            }
        }
    }

    // ── S5: コマンドパレット全コマンド ─────────────────────────
    static void paletteWalk() throws Exception {
        if (palette == null) {
            return;
        }
        log("palette commands=" + palette.size());
        for (CommandPalette.Command cmd : palette) {
            if (isDangerous(cmd.label)) {
                continue;
            }
            act("palette:" + cmd.label, cmd.action);
            checkInvariants("palette:" + cmd.label);
        }
    }

    static void runPalette(String... needles) throws Exception {
        if (palette == null) {
            return;
        }
        for (CommandPalette.Command cmd : palette) {
            String low = cmd.label.toLowerCase();
            for (String n : needles) {
                if (low.contains(n.toLowerCase())) {
                    act("palette:" + cmd.label, cmd.action);
                    return;
                }
            }
        }
        log("palette command not found for " + String.join("/", needles));
    }

    // ── S6: キーボードショートカット ───────────────────────────
    static final int[][] SHORTCUTS = {
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_1 },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_2 },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_EQUALS },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_MINUS },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_0 },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_0 },
        { KeyEvent.VK_F5 },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_B },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_B },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_F },
        { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_F },
        { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_P },
        { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_U },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_N },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_J },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_E },
        { KeyEvent.VK_ALT, KeyEvent.VK_LEFT },
        { KeyEvent.VK_ALT, KeyEvent.VK_RIGHT },
        { KeyEvent.VK_ALT, KeyEvent.VK_1 },
        { KeyEvent.VK_ALT, KeyEvent.VK_2 },
        { KeyEvent.VK_ALT, KeyEvent.VK_3 },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_W },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_T },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_M },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_COMMA },
        { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_L },
        { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_F1 },
        { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_N },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_W },
    };

    static void keyboardWalk() throws Exception {
        if (!ensureRobot()) {
            return;
        }
        onEdt(() -> {
            frame.toFront();
            frame.requestFocus();
        });
        quiesce(1_000);
        for (int[] combo : SHORTCUTS) {
            String name = keyName("key:", combo);
            act(name, () -> keys(combo));
            checkInvariants(name);
        }
    }

    // ── S13: 固定ユーティリティタブ選択時のプレースホルダ ──────
    /** 図タブ非アクティブ (固定ユーティリティタブ選択) ではドロップダウンがプレースホルダになる。 */
    static void utilityTabPlaceholderWalk() throws Exception {
        if (mainTabs == null) {
            return;
        }
        String none = Messages.get("toolbar.diagramKind.none");
        int fixed = onEdt(() -> mainTabs.getTabCount() - tabPane.dynamicTabCount());
        for (int i = 0; i < fixed; i++) {
            final int idx = i;
            act("select-utility-tab[" + i + "]", () -> mainTabs.setSelectedIndex(idx));
            checkPlaceholder("utility-tab[" + i + "]", none);
        }
        act("select-utility-tab[0]", () -> mainTabs.setSelectedIndex(0));
        for (DiagramKind k : new DiagramKind[] {DiagramKind.LAYOUT, DiagramKind.NAVIGATION, DiagramKind.CLASS}) {
            JMenuItem item = chooser.itemFor(k);
            if (item == null || !onEdt(item::isEnabled)) {
                continue;
            }
            int before = onEdt(() -> tabPane.dynamicTabCount());
            act("dropdown-inactive:" + k, item::doClick);
            int after = onEdt(() -> tabPane.dynamicTabCount());
            boolean active = onEdt(() -> tabPane.hasActiveTab());
            if (!active) {
                checkPlaceholder("dropdown-inactive-no-tab:" + k + "(" + before + "->" + after + ")", none);
            } else {
                checkInvariants("dropdown-inactive-opened:" + k);
            }
            act("select-utility-tab[0]", () -> mainTabs.setSelectedIndex(0));
        }
    }

    /** 図タブ非アクティブ時のドロップダウン/メニューラジオの表示が「図種なし」になっているか。 */
    static void checkPlaceholder(String where, String none) throws Exception {
        String[] r = onEdt(() -> {
            List<String> p = new ArrayList<>();
            if (tabPane.hasActiveTab()) {
                return new String[0];
            }
            String label = chooser.component().getText();
            if (!label.contains(none)) {
                p.add("図タブ非アクティブなのにドロップダウン='" + label + "' (期待: プレースホルダ '" + none + "')");
            }
            if (diagramItems != null) {
                for (Map.Entry<DiagramKind, JRadioButtonMenuItem> e : diagramItems.entrySet()) {
                    if (e.getValue().isSelected()) {
                        p.add("図タブ非アクティブなのにメニューラジオ " + e.getKey() + " が選択中");
                    }
                }
            }
            return p.toArray(new String[0]);
        });
        for (String p : r) {
            record("invariant", where + ": " + p);
        }
    }

    // ── S14: 無効化された図種をパレットから選ぶ ────────────────
    /** メニュー/ドロップダウンで無効化された図種をコマンドパレットから選んでも空図タブが開かない。 */
    static void paletteDisabledKindsWalk() throws Exception {
        if (palette == null) {
            return;
        }
        String prefix = Messages.get("cmd.diagram.switchTo");
        for (DiagramKind k : DiagramKind.values()) {
            JMenuItem item = chooser.itemFor(k);
            if (item == null || onEdt(item::isEnabled)) {
                continue;
            }
            String label = prefix + " " + k.getDisplayName();
            CommandPalette.Command found = null;
            for (CommandPalette.Command c : palette) {
                if (c.label.equals(label)) {
                    found = c;
                }
            }
            if (found == null) {
                log("no palette command for disabled kind " + k);
                continue;
            }
            int before = onEdt(() -> tabPane.dynamicTabCount());
            String labelBefore = onEdt(() -> chooser.component().getText());
            act("palette-disabled:" + k, found.action);
            int after = onEdt(() -> tabPane.dynamicTabCount());
            String labelAfter = onEdt(() -> chooser.component().getText());
            if (after != before) {
                record("invariant", "無効化された図種 " + k + " をパレットから選ぶとタブが開いた ("
                        + before + "->" + after + ")");
            }
            if (!labelAfter.equals(labelBefore)) {
                record("invariant", "無効化された図種 " + k + " をパレットから選ぶとドロップダウン表示が変わった ('"
                        + labelBefore + "'->'" + labelAfter + "')");
            }
            checkInvariants("palette-disabled:" + k);
        }
    }

    // ── S15: 自由編集エディタタブ ──────────────────────────────
    /** 自由編集エディタタブ: 図種なし表示 / ソース表示コマンドが例外にならない / 図タブへ戻れる。 */
    static void editorTabWalk() throws Exception {
        String none = Messages.get("toolbar.diagramKind.none");
        int before = onEdt(() -> tabPane.dynamicTabCount());
        runPalette(Messages.get("cmd.file.newUml").trim());
        int after = onEdt(() -> tabPane.dynamicTabCount());
        if (after <= before) {
            record("harness", "新規 UML 図タブが開かない (before=" + before + " after=" + after + ")");
            return;
        }
        boolean editor = onEdt(() -> tabPane.activeTabIsPumlEditor());
        if (!editor) {
            record("invariant", "新規 UML 図の後にアクティブタブがエディタでない");
        }
        checkPlaceholder("editor-tab-focused", none);
        runPalette(Messages.get("cmd.view.openSource"));
        checkPlaceholder("editor-tab-after-open-source", none);
        JMenuItem cls = chooser.itemFor(DiagramKind.CLASS);
        if (cls != null && onEdt(cls::isEnabled)) {
            act("dropdown-from-editor:CLASS", cls::doClick);
            checkInvariants("dropdown-from-editor");
        }
    }

    // ── S16: エディタへの Robot 入力 ───────────────────────────
    static final String[] EDITOR_SNIPPETS = {
        "\nclass Foo {\n +bar(): void\n}\n",
        "class Bar\nFoo --> Bar : uses\n",
        "interface Baz\nBar ..|> Baz\n",
        "note right of Foo : typed by monkey\n",
        "cla",
    };

    static final int[][] EDITOR_KEYS = {
        { KeyEvent.VK_CONTROL, KeyEvent.VK_Z }, { KeyEvent.VK_CONTROL, KeyEvent.VK_Y },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_A }, { KeyEvent.VK_CONTROL, KeyEvent.VK_C },
        { KeyEvent.VK_END }, { KeyEvent.VK_CONTROL, KeyEvent.VK_V },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_F }, { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_H }, { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_G }, { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_SLASH }, { KeyEvent.VK_CONTROL, KEY_SHIFT_F[0], KEY_SHIFT_F[1] },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_D }, { KeyEvent.VK_ALT, KeyEvent.VK_UP },
        { KeyEvent.VK_ALT, KeyEvent.VK_DOWN }, { KeyEvent.VK_TAB }, { KeyEvent.VK_SHIFT, KeyEvent.VK_TAB },
        { KeyEvent.VK_CONTROL, KeyEvent.VK_S }, { KeyEvent.VK_ESCAPE },
        { KeyEvent.VK_F5 },
    };

    /** エディタタブを開いて JTextPane にフォーカスを置く。見つからなければ null。 */
    static JTextPane focusEditor() throws Exception {
        runPalette(Messages.get("cmd.file.newUml").trim());
        JTextPane pane = onEdt(() -> findShowing(mainTabs.getSelectedComponent(), JTextPane.class));
        if (pane == null) {
            record("harness", "エディタの JTextPane が見つからない");
            return null;
        }
        onEdt(() -> {
            frame.toFront();
            pane.requestFocusInWindow();
            pane.setCaretPosition(pane.getDocument().getLength());
        });
        quiesce(500);
        return pane;
    }

    /** 自由編集エディタに Robot で PlantUML を打ち込み、補完/整形/Undo/検索/保存系のキーを叩く。 */
    static void editorTypingWalk() throws Exception {
        if (!ensureRobot() || focusEditor() == null) {
            return;
        }
        for (String sn : EDITOR_SNIPPETS) {
            act("type:" + sn.replace('\n', '|'), () -> GuiMonkeyRuntime.typeAscii(sn));
            act("key:Ctrl+Space", () -> keys(KeyEvent.VK_CONTROL, KeyEvent.VK_SPACE));
            act("key:Escape", () -> keys(KeyEvent.VK_ESCAPE));
            checkInvariants("editor-typing");
        }
        for (int[] combo : EDITOR_KEYS) {
            String name = keyName("editor-key:", combo);
            act(name, () -> keys(combo));
            checkInvariants(name);
        }
        for (String needle : new String[] {"cmd.view.zoomIn", "cmd.view.zoomOut", "cmd.view.zoomFit",
            "cmd.diagram.findInDiagram", "cmd.view.addNote", "cmd.view.notesPanel"}) {
            runPalette(Messages.get(needle));
            act("key:Escape", () -> keys(KeyEvent.VK_ESCAPE));
        }
        checkInvariants("after-editor-walk");
    }

    // ── S17: プレビュー上のマウス操作 ──────────────────────────
    /** アクティブ図のプレビュー上でクリック / 右クリック / ダブルクリック / ドラッグ / ホイールをランダムに行う。 */
    static void previewMouseWalk(int steps) throws Exception {
        if (!ensureRobot()) {
            return;
        }
        JMenuItem cls = chooser.itemFor(DiagramKind.CLASS);
        if (cls != null && onEdt(cls::isEnabled)) {
            act("dropdown:CLASS(for-mouse)", cls::doClick);
        }
        Random rnd = new Random(42);
        for (int i = 0; i < steps; i++) {
            if (!previewMouseStep(rnd, i)) {
                return;
            }
            if (i % 5 == 4) {
                checkInvariants("preview-mouse[" + i + "]");
            }
        }
        checkInvariants("after-preview-mouse");
    }

    /** プレビューへ 1 回のランダムなマウス操作を行う。プレビューが無ければ false。 */
    static boolean previewMouseStep(Random rnd, int i) throws Exception {
        SvgPreviewPanel pp = onEdt(() -> findShowing(mainTabs.getSelectedComponent(), SvgPreviewPanel.class));
        if (pp == null) {
            record("harness", "SvgPreviewPanel が見つからない (step " + i + ")");
            return false;
        }
        Rectangle r = onEdt(() -> {
            Point p = pp.getLocationOnScreen();
            return new Rectangle(p.x, p.y, pp.getWidth(), pp.getHeight());
        });
        if (r.width < 20 || r.height < 20) {
            return true;
        }
        final int fx = r.x + 5 + rnd.nextInt(Math.max(1, r.width - 10));
        final int fy = r.y + 5 + rnd.nextInt(Math.max(1, r.height - 10));
        final boolean up = rnd.nextBoolean();
        String at = "@" + fx + "," + fy;
        switch (rnd.nextInt(7)) {
            case 0:
                act("mouse:click" + at, () -> {
                    robot.mouseMove(fx, fy);
                    click(button1(), 1);
                });
                break;
            case 1:
                act("mouse:dblclick" + at, () -> {
                    robot.mouseMove(fx, fy);
                    click(button1(), 2);
                });
                break;
            case 2:
                act("mouse:rclick" + at, () -> {
                    robot.mouseMove(fx, fy);
                    click(button3(), 1);
                });
                act("key:Escape", () -> keys(KeyEvent.VK_ESCAPE));
                break;
            case 3:
                act("mouse:ctrl-click" + at, () -> {
                    robot.mouseMove(fx, fy);
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    click(button1(), 1);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                });
                break;
            case 4:
                act("mouse:drag" + at, () -> {
                    robot.mouseMove(fx, fy);
                    robot.mousePress(button1());
                    for (int d = 0; d < 8; d++) {
                        robot.mouseMove(fx + d * 12, fy + d * 7);
                    }
                    robot.mouseRelease(button1());
                });
                break;
            case 5:
                act("mouse:ctrl-wheel" + at, () -> {
                    robot.mouseMove(fx, fy);
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.mouseWheel(up ? 3 : -3);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                });
                break;
            default:
                act("mouse:wheel" + at, () -> {
                    robot.mouseMove(fx, fy);
                    robot.mouseWheel(up ? 5 : -5);
                });
                break;
        }
        killDialogs();
        return true;
    }

    // ── S18: ツリーのキーボード操作 ────────────────────────────
    static final int[][] TREE_KEYS = {
        { KeyEvent.VK_DOWN }, { KeyEvent.VK_DOWN }, { KeyEvent.VK_RIGHT }, { KeyEvent.VK_DOWN }, { KeyEvent.VK_ENTER },
        { KeyEvent.VK_DOWN }, { KeyEvent.VK_DOWN }, { KeyEvent.VK_ENTER }, { KeyEvent.VK_LEFT }, { KeyEvent.VK_UP },
        { KeyEvent.VK_END }, { KeyEvent.VK_ENTER }, { KeyEvent.VK_HOME }, { KeyEvent.VK_SHIFT, KeyEvent.VK_F10 },
        { KeyEvent.VK_ESCAPE }, { KeyEvent.VK_CONTEXT_MENU }, { KeyEvent.VK_ESCAPE }, { KeyEvent.VK_SPACE },
        { KeyEvent.VK_PAGE_DOWN }, { KeyEvent.VK_ENTER }, { KeyEvent.VK_PAGE_UP }, { KeyEvent.VK_F2 }, { KeyEvent.VK_ESCAPE },
    };

    /** ツリーをキーボードだけで移動・展開・オープンする。 */
    static void treeKeyWalk() throws Exception {
        if (!ensureRobot() || tree == null) {
            return;
        }
        onEdt(() -> {
            frame.toFront();
            tree.requestFocusInWindow();
            tree.setSelectionRow(0);
        });
        quiesce(500);
        for (int[] combo : TREE_KEYS) {
            String name = keyName("tree-key:", combo);
            onEdt(() -> {
                if (!tree.hasFocus()) {
                    tree.requestFocusInWindow();
                }
            });
            act(name, () -> keys(combo));
            killDialogs();
            checkInvariants(name);
        }
    }

    // ── S20: Git 連携ペイン ────────────────────────────────────
    /**
     * Git タブ: コミット表の行選択 → 変更ファイル選択 → 複数選択 → 連打+選択解除 (stale result 検査)
     * → ペイン内の全ボタン (diff モード切替 / 比較ダイアログ起動; ダイアログは killer が閉じる)
     * → サブタブ (branches / file history) の表を順に選択。
     */
    static void gitPaneWalk() throws Exception {
        if (mainTabs == null || frame == null) {
            return;
        }
        juml.app.uml.git.GitPanel gitPanel = findAny(frame, juml.app.uml.git.GitPanel.class);
        if (gitPanel == null) {
            log("git: GitPanel not found");
            return;
        }
        int idx = onEdt(() -> mainTabs.indexOfComponent(gitPanel));
        if (idx < 0) {
            log("git: GitPanel is not a main tab");
            return;
        }
        act("select-git-tab", () -> mainTabs.setSelectedIndex(idx));
        quiesce(10_000);
        JTable table = findAny(gitPanel, JTable.class);
        JList<?> files = findAny(gitPanel, JList.class);
        if (table == null || files == null) {
            record("harness", "git: commits table / files list not found (table=" + table + ", list=" + files + ")");
            return;
        }
        int rows = onEdt(table::getRowCount);
        log("git: commit rows=" + rows);
        if (rows == 0) {
            log("git: no commits (project is not inside a git repo?)");
            return;
        }
        for (int r = 0; r < Math.min(rows, 6); r++) {
            final int row = r;
            act("git-select-commit[" + r + "]", () -> table.setRowSelectionInterval(row, row));
            quiesce(4_000);
            int n = onEdt(() -> files.getModel().getSize());
            for (int i = 0; i < Math.min(n, 4); i++) {
                final int fi = i;
                act("git-select-file[" + r + "," + i + "]", () -> files.setSelectedIndex(fi));
            }
        }
        if (rows >= 2) {
            act("git-select-range[0..1]", () -> table.setRowSelectionInterval(0, 1));
            quiesce(4_000);
        }
        // 連打してから選択解除: 走行中ワーカーの古い結果がクリア済み一覧へ適用されないか。
        act("git-rapid-then-clear", () -> {
            table.setRowSelectionInterval(Math.min(2, rows - 1), Math.min(2, rows - 1));
            table.setRowSelectionInterval(0, 0);
            table.clearSelection();
        });
        quiesce(8_000);
        int leftover = onEdt(() -> files.getModel().getSize());
        if (leftover != 0) {
            record("invariant", "選択解除後も変更ファイル一覧に " + leftover + " 件残っている (stale worker result)");
        }
        act("git-select-commit[0]", () -> table.setRowSelectionInterval(0, 0));
        quiesce(4_000);
        if (onEdt(() -> files.getModel().getSize()) > 0) {
            act("git-select-file[0]", () -> files.setSelectedIndex(0));
        }
        List<AbstractButton> buttons = new ArrayList<>();
        collectAll(gitPanel, AbstractButton.class, buttons, 40);
        for (AbstractButton b : buttons) {
            String text = String.valueOf(b.getText()) + "/" + String.valueOf(b.getToolTipText());
            if (!onEdt(() -> b.isEnabled() && b.isShowing())) {
                continue;
            }
            act("git-button:" + text, b::doClick);
            quiesce(6_000);
        }
        gitSubTabs(gitPanel);
        checkInvariants("after-git-pane");
    }

    private static void gitSubTabs(Component gitPanel) throws Exception {
        JTabbedPane sub = findAny(gitPanel, JTabbedPane.class);
        if (sub == null) {
            return;
        }
        int tabs = onEdt(sub::getTabCount);
        for (int t = 0; t < tabs; t++) {
            final int ti = t;
            act("git-subtab[" + t + "]", () -> sub.setSelectedIndex(ti));
            quiesce(6_000);
            Component page = onEdt(() -> sub.getComponentAt(ti));
            List<JTable> tables = new ArrayList<>();
            collectAll(page, JTable.class, tables, 3);
            for (JTable tb : tables) {
                int n = onEdt(tb::getRowCount);
                for (int r = 0; r < Math.min(n, 3); r++) {
                    final int row = r;
                    act("git-subtab[" + t + "]-row[" + r + "]", () -> tb.setRowSelectionInterval(row, row));
                    quiesce(3_000);
                }
            }
            List<JTree> trees = new ArrayList<>();
            collectAll(page, JTree.class, trees, 2);
            for (JTree tr : trees) {
                int n = onEdt(tr::getRowCount);
                for (int r = 0; r < Math.min(n, 4); r++) {
                    final int row = r;
                    act("git-subtab[" + t + "]-tree[" + r + "]", () -> tr.setSelectionRow(row));
                    quiesce(2_000);
                }
            }
        }
        act("git-subtab[0]", () -> sub.setSelectedIndex(0));
    }
}
