// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.AppLog;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * {@link GuiMonkey} の実行基盤: 共有状態 / EDT 静穏待ち / ダイアログ自動クローズ /
 * 例外・エラーログ・stderr の捕捉 / 不変条件検査 / スクリーンショット / JSON レポート。
 *
 * <p>テストではなく探索ハーネスのため、{@link UmlMainFrame} の内部フィールドへリフレクションで
 * 触る（公開振る舞いで検証する回帰テストとは役割が異なる。確定した問題は別途テスト化する）。</p>
 */
final class GuiMonkeyRuntime {

    // ── 採取した所見 ──────────────────────────────────────────
    static final List<String[]> FINDINGS = Collections.synchronizedList(new ArrayList<>());
    static final Set<String> SEEN = Collections.synchronizedSet(new LinkedHashSet<>());
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    static final List<String> SHOTS = Collections.synchronizedList(new ArrayList<>());
    static volatile String scenario = "startup";

    /** 所見発生時に撮る自動スクリーンショットの上限 (ログ系の大量所見で溢れさせない)。 */
    private static final int MAX_FINDING_SHOTS = 30;

    // ── 共有状態 (bindFields で束縛) ──────────────────────────
    static UmlMainFrame frame;
    static DiagramTabPane tabPane;
    static DiagramController controller;
    static DiagramKindChooser chooser;
    static EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    static List<CommandPalette.Command> palette;
    static JTabbedPane mainTabs;
    static JTree tree;
    static DetachedDiagramWindows detached;
    static Robot robot;
    static int actionCount;
    static File shotDir;
    private static int shotSeq;
    private static int findingShots;

    private GuiMonkeyRuntime() {
    }

    // ── 実行基盤 ──────────────────────────────────────────────
    /** アクションを EDT に投げ、静穏 (EDT 応答・ダイアログ無し・描画中無し) まで待つ。 */
    static void act(String name, Runnable r) throws Exception {
        actionCount++;
        log("ACT " + name);
        final String sc = scenario + " / " + name;
        SwingUtilities.invokeLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                record(sc, "exception", stack(t));
            }
        });
        quiesce(8_000);
    }

    static void quiesce(long maxMs) throws Exception {
        long deadline = System.currentTimeMillis() + maxMs;
        int calm = 0;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(120);
            killDialogs();
            boolean idle = edtIdle(6_000);
            boolean busy = idle && isBusy();
            if (idle && !busy) {
                calm++;
                if (calm >= 2) {
                    return;
                }
            } else {
                calm = 0;
            }
        }
    }

    private static boolean edtIdle(long timeoutMs) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(latch::countDown);
        boolean ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (!ok) {
            record(scenario, "edt-stall", "EDT が " + timeoutMs + "ms 応答しない (モーダル以外でブロック?)\n"
                    + edtStack());
        }
        return ok;
    }

    static boolean isBusy() throws Exception {
        return onEdt(() -> {
            if (frame.getGlassPane() != null && frame.getGlassPane().isVisible()) {
                return true;
            }
            if (mainTabs != null) {
                Component sel = mainTabs.getSelectedComponent();
                if (sel != null && hasShowingProgressBar(sel)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean hasShowingProgressBar(Component c) {
        if (c instanceof JProgressBar && c.isShowing() && c.getWidth() > 0) {
            return true;
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                if (hasShowingProgressBar(k)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void killDialogs() throws Exception {
        List<String> killed = onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (Window w : Window.getWindows()) {
                if (w == frame || !w.isShowing()) {
                    continue;
                }
                if (w instanceof Dialog) {
                    l.add(w.getClass().getSimpleName() + " '" + titleOf(w) + "'"
                            + (w instanceof JDialog && ((JDialog) w).isModal() ? " (modal)" : ""));
                    w.dispose();
                }
            }
            return l;
        });
        for (String k : killed) {
            log("dialog closed: " + k);
            record(scenario, "dialog", k);
        }
    }

    static boolean awaitTreeLoaded(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            killDialogs();
            if (tree == null) {
                bindFields();
            }
            if (tree != null) {
                int rows = onEdt(tree::getRowCount);
                boolean busy = isBusy();
                if (rows >= 2 && !busy) {
                    return true;
                }
            }
            Thread.sleep(200);
        }
        return false;
    }

    static void bindFields() throws Exception {
        tabPane = get(frame, "tabPane");
        controller = get(frame, "controller");
        chooser = get(frame, "diagramKindChooser");
        diagramItems = get(frame, "diagramItems");
        palette = get(frame, "paletteCommands");
        mainTabs = get(frame, "mainTabs");
        detached = get(frame, "detachedWindows");
        Object treePanel = get(frame, "treePanel");
        tree = treePanel == null ? null : get(treePanel, "tree");
        if (tabPane == null || controller == null || chooser == null) {
            record(scenario, "harness", "フィールド取得失敗 tabPane=" + tabPane + " controller=" + controller
                    + " chooser=" + chooser);
        }
    }

    static void installHooks() {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> record(scenario, "uncaught@" + t.getName(), stack(e)));
        AppLog.init();
        AppLog.addListener(entry -> {
            try {
                Object level = get(entry, "level");
                if (level != null && ("WARN".equals(level.toString()) || "ERROR".equals(level.toString()))) {
                    Object code = get(entry, "code");
                    Object msg = get(entry, "message");
                    Object detail = get(entry, "detail");
                    record(scenario, "applog-" + level, "[" + code + "] " + msg
                            + (detail != null ? "\n" + head(String.valueOf(detail), 12) : ""));
                }
            } catch (Exception ex) {
                record(scenario, "harness", "AppLog listener: " + ex);
            }
        });
        // stderr のスタックトレースも拾う (Swing 内部 / SLF4J 以外)
        PrintStream origErr = System.err;
        System.setErr(new PrintStream(new StderrSniffer(origErr), true));
    }

    /** stderr を素通ししつつ、例外ブロックらしい行の塊を所見として採取する。 */
    private static final class StderrSniffer extends OutputStream {
        private final PrintStream origErr;
        private final StringBuilder line = new StringBuilder();
        private final List<String> block = new ArrayList<>();

        StderrSniffer(PrintStream origErr) {
            this.origErr = origErr;
        }

        @Override
        public void write(int b) {
            origErr.write(b);
            if (b == '\n') {
                onLine(line.toString());
                line.setLength(0);
            } else {
                line.append((char) b);
            }
        }

        private void onLine(String s) {
            boolean stackish = s.startsWith("\tat ") || s.startsWith("Caused by") || s.contains("Exception")
                    || s.contains("Error:") || s.endsWith("Error");
            if (stackish && !s.startsWith("SLF4J")) {
                block.add(s);
            } else if (!block.isEmpty()) {
                flushBlock();
            }
            if (block.size() > 14) {
                flushBlock();
            }
        }

        private void flushBlock() {
            if (!block.isEmpty() && (block.get(0).contains("Exception") || block.get(0).contains("Error"))) {
                record(scenario, "stderr", String.join("\n", block));
            }
            block.clear();
        }
    }

    // ── 不変条件 ──────────────────────────────────────────────
    /** ドロップダウン表示 / メニューラジオ / controller.currentKind がアクティブタブの図種と一致しているか。 */
    static void checkInvariants(String where) throws Exception {
        try {
            String[] r = onEdt(() -> {
                List<String> problems = new ArrayList<>();
                boolean hasActive = tabPane.hasActiveTab();
                DiagramKind active = tabPane.activeTabKind();
                String label = chooser.component().getText();
                if (hasActive && active != null) {
                    String want = ToolBarBuilder.toolbarLabel(active);
                    if (!label.contains(want)) {
                        problems.add("図種ボタン='" + label + "' だがアクティブタブの図種=" + active
                                + " (期待ラベル '" + want + "')");
                    }
                    JRadioButtonMenuItem radio = diagramItems == null ? null : diagramItems.get(active);
                    if (radio != null && !radio.isSelected()) {
                        problems.add("メニューラジオ " + active + " が未選択 (アクティブタブは " + active + ")");
                    }
                    if (radio == null && diagramItems != null) {
                        for (Map.Entry<DiagramKind, JRadioButtonMenuItem> e : diagramItems.entrySet()) {
                            if (e.getValue().isSelected()) {
                                problems.add("アクティブタブ " + active + " はラジオ無しだが " + e.getKey()
                                        + " のラジオが選択されたまま");
                            }
                        }
                    }
                    if (controller.currentKind != active) {
                        problems.add("controller.currentKind=" + controller.currentKind
                                + " ≠ activeTabKind=" + active);
                    }
                }
                return problems.toArray(new String[0]);
            });
            for (String p : r) {
                record(scenario, "invariant", where + ": " + p);
            }
        } catch (Throwable t) {
            record(scenario, "exception", "checkInvariants(" + where + "): " + stack(t));
        }
    }

    static boolean chooserEnabled() throws Exception {
        return onEdt(() -> chooser.component().isEnabled());
    }

    // ── コンポーネント探索 / Robot 入力 ────────────────────────
    static <T extends Component> T findShowing(Component root, Class<T> type) {
        if (root == null || !root.isShowing()) {
            return null;
        }
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container) {
            for (Component c : ((Container) root).getComponents()) {
                T t = findShowing(c, type);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    static <T extends Component> T findAny(Component root, Class<T> type) {
        List<T> all = new ArrayList<>();
        collectAll(root, type, all, 1);
        return all.isEmpty() ? null : all.get(0);
    }

    static <T extends Component> void collectAll(Component root, Class<T> type, List<T> out, int limit) {
        if (root == null || out.size() >= limit) {
            return;
        }
        if (type.isInstance(root)) {
            out.add(type.cast(root));
        }
        if (root instanceof Container) {
            for (Component c : ((Container) root).getComponents()) {
                collectAll(c, type, out, limit);
            }
        }
    }

    static boolean ensureRobot() {
        if (robot == null) {
            try {
                robot = new Robot();
                robot.setAutoDelay(25);
            } catch (Exception ex) {
                record(scenario, "harness", "Robot 生成失敗: " + ex);
                return false;
            }
        }
        return true;
    }

    /** ASCII の PlantUML 断片を US 配列想定で Robot 打鍵する (対応外の文字は読み飛ばす)。 */
    static void typeAscii(String text) {
        for (char ch : text.toCharArray()) {
            int code;
            boolean shift = false;
            if (ch == '\n') {
                code = KeyEvent.VK_ENTER;
            } else if (ch == ' ') {
                code = KeyEvent.VK_SPACE;
            } else if (ch == '-') {
                code = KeyEvent.VK_MINUS;
            } else if (ch == '>') {
                code = KeyEvent.VK_PERIOD;
                shift = true;
            } else if (ch == '<') {
                code = KeyEvent.VK_COMMA;
                shift = true;
            } else if (ch == '+') {
                code = KeyEvent.VK_EQUALS;
                shift = true;
            } else if (ch == ':') {
                code = KeyEvent.VK_SEMICOLON;
                shift = true;
            } else if (ch == '(') {
                code = KeyEvent.VK_9;
                shift = true;
            } else if (ch == ')') {
                code = KeyEvent.VK_0;
                shift = true;
            } else if (ch == '{') {
                code = KeyEvent.VK_OPEN_BRACKET;
                shift = true;
            } else if (ch == '}') {
                code = KeyEvent.VK_CLOSE_BRACKET;
                shift = true;
            } else if (ch == '"') {
                code = KeyEvent.VK_QUOTE;
                shift = true;
            } else if (Character.isLetterOrDigit(ch)) {
                code = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(ch));
                shift = Character.isUpperCase(ch);
            } else {
                continue;
            }
            if (shift) {
                robot.keyPress(KeyEvent.VK_SHIFT);
            }
            robot.keyPress(code);
            robot.keyRelease(code);
            if (shift) {
                robot.keyRelease(KeyEvent.VK_SHIFT);
            }
        }
    }

    static void keys(int... combo) {
        for (int k : combo) {
            robot.keyPress(k);
        }
        for (int i = combo.length - 1; i >= 0; i--) {
            robot.keyRelease(combo[i]);
        }
    }

    static String keyName(String prefix, int[] combo) {
        StringBuilder name = new StringBuilder(prefix);
        for (int k : combo) {
            name.append(KeyEvent.getKeyText(k)).append('+');
        }
        return name.toString();
    }

    static void click(int mask, int times) {
        for (int i = 0; i < times; i++) {
            robot.mousePress(mask);
            robot.mouseRelease(mask);
        }
    }

    static int button1() {
        return InputEvent.BUTTON1_DOWN_MASK;
    }

    static int button3() {
        return InputEvent.BUTTON3_DOWN_MASK;
    }

    // ── 所見・ログ・スクリーンショット ─────────────────────────
    static void record(String kind, String message) {
        record(scenario, kind, message);
    }

    static void record(String sc, String kind, String message) {
        String key = kind + "|" + firstLine(message);
        if (SEEN.add(key)) {
            String shot = null;
            if (!kind.startsWith("applog") && !"dialog".equals(kind) && findingShots < MAX_FINDING_SHOTS) {
                findingShots++;
                shot = shot("finding-" + kind);
            }
            FINDINGS.add(new String[] { sc, kind, message, shot });
            System.out.println("[FINDING] " + sc + " | " + kind + " | " + firstLine(message));
        }
    }

    static void log(String s) {
        LOG.add(s);
        System.out.println("[monkey] " + s);
    }

    /** {@code --shots} 指定時のみ、メインフレームの矩形を PNG に保存してファイル名を返す。 */
    static String shot(String tag) {
        if (shotDir == null || frame == null || !ensureRobot()) {
            return null;
        }
        try {
            Rectangle b = onEdt(() -> frame.isShowing() ? frame.getBounds() : null);
            if (b == null || b.width <= 0 || b.height <= 0) {
                return null;
            }
            BufferedImage img = robot.createScreenCapture(b);
            String name = String.format("%03d-%s.png", ++shotSeq, tag.replaceAll("[^A-Za-z0-9_.-]+", "_"));
            ImageIO.write(img, "png", new File(shotDir, name));
            SHOTS.add(name);
            log("shot " + name);
            return name;
        } catch (Exception ex) {
            log("shot failed: " + ex);
            return null;
        }
    }

    static <T> T onEdt(Supplier<T> s) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return s.get();
        }
        final Object[] box = new Object[1];
        final Throwable[] err = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                box[0] = s.get();
            } catch (Throwable t) {
                err[0] = t;
            }
        });
        if (err[0] != null) {
            throw new Exception(err[0]);
        }
        @SuppressWarnings("unchecked")
        T t = (T) box[0];
        return t;
    }

    static void onEdt(Runnable r) throws Exception {
        onEdt(() -> {
            r.run();
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    static <T> T get(Object target, String field) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    static void callPrivate(Object target, String name, Class<?> type, Object arg) {
        try {
            Method m = target.getClass().getDeclaredMethod(name, type);
            m.setAccessible(true);
            m.invoke(target, arg);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(ite.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String titleOf(Window w) {
        if (w instanceof Dialog) {
            return ((Dialog) w).getTitle();
        }
        if (w instanceof Frame) {
            return ((Frame) w).getTitle();
        }
        return "";
    }

    static boolean isJvmThread(String n) {
        return n.startsWith("AWT-") || n.equals("main") || n.equals("DestroyJavaVM") || n.equals("Reference Handler")
                || n.equals("Finalizer") || n.equals("Signal Dispatcher") || n.equals("Common-Cleaner")
                || n.equals("Notification Thread") || n.equals("Attach Listener") || n.startsWith("Java2D")
                || n.equals("TimerQueue") || n.startsWith("process reaper") || n.startsWith("Monitor Ctrl-Break");
    }

    private static String edtStack() {
        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
            if (e.getKey().getName().startsWith("AWT-EventQueue")) {
                StringBuilder sb = new StringBuilder();
                int n = 0;
                for (StackTraceElement el : e.getValue()) {
                    sb.append("    at ").append(el).append('\n');
                    if (++n >= 25) {
                        break;
                    }
                }
                return sb.toString();
            }
        }
        return "(EDT stack unavailable)";
    }

    static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return head(sw.toString(), 18);
    }

    private static String head(String s, int lines) {
        String[] arr = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines, arr.length); i++) {
            sb.append(arr[i]).append('\n');
        }
        return sb.toString().trim();
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    // ── JSON レポート ─────────────────────────────────────────
    static void writeReport(File out, File project, String persona, long seed, List<String> scenarios,
            long elapsedMs) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            w.write("{\n  \"project\": " + json(project.getPath()) + ",\n");
            w.write("  \"persona\": " + json(persona) + ",\n  \"seed\": " + seed + ",\n");
            w.write("  \"scenarios\": " + jsonArray(scenarios) + ",\n");
            w.write("  \"shots\": " + jsonArray(new ArrayList<>(SHOTS)) + ",\n");
            w.write("  \"elapsedMs\": " + elapsedMs + ",\n  \"actions\": " + actionCount + ",\n");
            w.write("  \"findings\": [\n");
            synchronized (FINDINGS) {
                for (int i = 0; i < FINDINGS.size(); i++) {
                    String[] f = FINDINGS.get(i);
                    w.write("    {\"scenario\": " + json(f[0]) + ", \"kind\": " + json(f[1])
                            + ", \"message\": " + json(f[2])
                            + (f[3] != null ? ", \"shot\": " + json(f[3]) : "")
                            + "}" + (i + 1 < FINDINGS.size() ? "," : "") + "\n");
                }
            }
            w.write("  ],\n  \"log\": [\n");
            synchronized (LOG) {
                for (int i = 0; i < LOG.size(); i++) {
                    w.write("    " + json(LOG.get(i)) + (i + 1 < LOG.size() ? "," : "") + "\n");
                }
            }
            w.write("  ]\n}\n");
        } catch (Exception ex) {
            System.out.println("report write failed: " + ex);
        }
        System.out.println("=== GuiMonkey done: project=" + project.getName() + " persona=" + persona
                + " seed=" + seed + " actions=" + actionCount + " findings=" + FINDINGS.size()
                + " elapsed=" + (elapsedMs / 1000) + "s ===");
    }

    private static String jsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append(i > 0 ? ", " : "").append(json(items.get(i)));
        }
        return sb.append(']').toString();
    }

    static String json(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
