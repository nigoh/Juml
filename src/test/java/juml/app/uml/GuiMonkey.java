// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.SettingManager;

import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static juml.app.uml.GuiMonkeyRuntime.act;
import static juml.app.uml.GuiMonkeyRuntime.awaitTreeLoaded;
import static juml.app.uml.GuiMonkeyRuntime.callPrivate;
import static juml.app.uml.GuiMonkeyRuntime.checkInvariants;
import static juml.app.uml.GuiMonkeyRuntime.chooser;
import static juml.app.uml.GuiMonkeyRuntime.chooserEnabled;
import static juml.app.uml.GuiMonkeyRuntime.detached;
import static juml.app.uml.GuiMonkeyRuntime.frame;
import static juml.app.uml.GuiMonkeyRuntime.isJvmThread;
import static juml.app.uml.GuiMonkeyRuntime.log;
import static juml.app.uml.GuiMonkeyRuntime.onEdt;
import static juml.app.uml.GuiMonkeyRuntime.quiesce;
import static juml.app.uml.GuiMonkeyRuntime.record;
import static juml.app.uml.GuiMonkeyRuntime.tabPane;
import static juml.app.uml.GuiMonkeyRuntime.titleOf;

/**
 * 実起動の GUI を機械的に叩いて例外・エラーログ・同期不整合を採取する探索ハーネス (GUI monkey)。
 * 合否判定はしない。見つけたものを JSON で吐き、司令塔 (メインループ) やペルソナ・エージェントが triage する。
 *
 * <p>JUnit テストではない (＠Test を持たない) ため {@code gradle test} では実行されず、
 * {@code gradle compileTestJava} でコンパイルだけされる。実行は Xvfb 上で行う:</p>
 *
 * <pre>
 * xvfb-run -a java -cp build/libs/Juml.jar:build/classes/java/test juml.app.uml.GuiMonkey \
 *     --project &lt;dir&gt; [--alt &lt;dir&gt;] --out report.json \
 *     [--scenarios S2,S3,S21] [--seed 42] [--fuzz 80] [--shots &lt;dir&gt;] [--persona newcomer] [--list]
 * </pre>
 *
 * <ul>
 *   <li>{@code --scenarios}: 実行する決定的シナリオ (省略時は全部)。S0 (起動) と S12 (終了) は常に実行。</li>
 *   <li>{@code --fuzz N}: S21 (シード付きランダム操作) のステップ数。0 で無効 (既定 0)。</li>
 *   <li>{@code --seed}: S21 の乱数シード。同じシードで同じ操作列を再現できる。</li>
 *   <li>{@code --shots}: シナリオ境界と所見発生時に PNG スクリーンショットを保存するディレクトリ。</li>
 * </ul>
 *
 * <p>旧形式 {@code GuiMonkey <project> <alt> <out.json>} も受け付ける。</p>
 */
public final class GuiMonkey {

    /** シナリオ本体 (例外は採取側で捕まえる)。 */
    @FunctionalInterface
    interface Step {
        void run() throws Exception;
    }

    private static final int TREE_LOAD_TIMEOUT_MS = 45_000;

    private static File project;
    private static File alt;
    private static File out;
    private static String persona = "(none)";
    private static long seed = 42;
    private static int fuzzSteps;
    private static List<String> selected;

    private GuiMonkey() {
    }

    public static void main(String[] args) throws Exception {
        if (!parseArgs(args)) {
            System.exit(2);
        }
        long t0 = System.currentTimeMillis();
        List<String> ran = new ArrayList<>();
        GuiMonkeyRuntime.installHooks();
        try {
            SettingManager.getInstance();
        } catch (RuntimeException ex) {
            SettingManager.initialize();
        }
        onEdt(() -> PreferencesDialog.applyLookAndFeel("FLATLAF_LIGHT"));
        try {
            runAll(ran);
        } catch (Throwable t) {
            record("harness", "harness-crash", GuiMonkeyRuntime.stack(t));
        } finally {
            GuiMonkeyRuntime.writeReport(out, project, persona, seed, ran, System.currentTimeMillis() - t0);
        }
        System.exit(0);
    }

    // ── 引数 ──────────────────────────────────────────────────
    private static boolean parseArgs(String[] args) {
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            boolean hasValue = i + 1 < args.length;
            switch (a) {
                case "--list":
                    for (Map.Entry<String, String> e : catalog().entrySet()) {
                        System.out.println(e.getKey() + "\t" + e.getValue());
                    }
                    return false;
                case "--project":
                    project = hasValue ? new File(args[++i]).getAbsoluteFile() : null;
                    break;
                case "--alt":
                    alt = hasValue ? new File(args[++i]).getAbsoluteFile() : null;
                    break;
                case "--out":
                    out = hasValue ? new File(args[++i]) : null;
                    break;
                case "--persona":
                    persona = hasValue ? args[++i] : persona;
                    break;
                case "--seed":
                    seed = hasValue ? Long.parseLong(args[++i]) : seed;
                    break;
                case "--fuzz":
                    fuzzSteps = hasValue ? Integer.parseInt(args[++i]) : fuzzSteps;
                    break;
                case "--shots":
                    GuiMonkeyRuntime.shotDir = hasValue ? new File(args[++i]) : null;
                    break;
                case "--scenarios":
                    selected = hasValue ? Arrays.asList(args[++i].split("\\s*,\\s*")) : null;
                    break;
                default:
                    positional.add(a);
            }
        }
        if (project == null && positional.size() >= 1) {
            project = new File(positional.get(0)).getAbsoluteFile();
        }
        if (alt == null && positional.size() >= 2) {
            alt = new File(positional.get(1)).getAbsoluteFile();
        }
        if (out == null && positional.size() >= 3) {
            out = new File(positional.get(2));
        }
        if (project == null || out == null) {
            System.err.println("usage: GuiMonkey --project <dir> --out <report.json> [--alt <dir>] [--scenarios S2,S3]"
                    + " [--seed N] [--fuzz N] [--shots <dir>] [--persona name] [--list]");
            return false;
        }
        if (alt == null) {
            alt = project;
        }
        if (GuiMonkeyRuntime.shotDir != null) {
            GuiMonkeyRuntime.shotDir.mkdirs();
        }
        return true;
    }

    /** シナリオ ID → 説明 (実行順)。S0 / S12 は常時実行のためここには含めない。 */
    static Map<String, String> catalog() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("S2", "図種ドロップダウンの全項目を順に選ぶ");
        m.put("S3", "プロジェクトツリーを展開し行を順に選択/ダブルクリック");
        m.put("S4", "メソッド系図種の切替バー (activity/callgraph/sequence)");
        m.put("S9", "図種を高速に連続切替 (競合検査)");
        m.put("S1", "メニューバーの全項目を叩く (終了/最近除く)");
        m.put("S5", "コマンドパレットの全コマンドを叩く");
        m.put("S6", "主要ショートカットを Robot で打鍵");
        m.put("S10", "全タブを閉じた後にドロップダウン/再オープン");
        m.put("S11", "別ウィンドウで開く → 全て閉じる");
        m.put("S13", "固定ユーティリティタブ選択時のプレースホルダ表示");
        m.put("S14", "無効化された図種をパレットから選ぶ");
        m.put("S15", "自由編集エディタタブの表示/ソース表示");
        m.put("S16", "エディタへ Robot で PlantUML を打鍵 + 編集系キー");
        m.put("S17", "プレビュー上のランダムなマウス操作");
        m.put("S18", "ツリーのキーボード操作");
        m.put("S20", "Git 連携ペイン (コミット/ファイル/ボタン/サブタブ)");
        m.put("S8", "Look&Feel とテーマの切替");
        m.put("S19", "ロード中に別プロジェクトを重ねてロード");
        m.put("S7", "別プロジェクトへ切替 → 図種一巡 → 元へ戻す");
        m.put("S21", "シード付きランダム操作 (--fuzz N で有効)");
        return m;
    }

    static Map<String, Step> steps() {
        Map<String, Step> m = new LinkedHashMap<>();
        m.put("S2", GuiMonkey::dropdownAllKinds);
        m.put("S3", () -> GuiMonkeyScenarios.treeWalk(160));
        m.put("S4", GuiMonkeyScenarios::methodKindBar);
        m.put("S9", GuiMonkey::rapidKindSwitch);
        m.put("S1", GuiMonkeyScenarios::menuWalk);
        m.put("S5", GuiMonkeyScenarios::paletteWalk);
        m.put("S6", GuiMonkeyScenarios::keyboardWalk);
        m.put("S10", GuiMonkey::closeAllThenDropdown);
        m.put("S11", GuiMonkey::detachedWindow);
        m.put("S13", GuiMonkeyScenarios::utilityTabPlaceholderWalk);
        m.put("S14", GuiMonkeyScenarios::paletteDisabledKindsWalk);
        m.put("S15", GuiMonkeyScenarios::editorTabWalk);
        m.put("S16", GuiMonkeyScenarios::editorTypingWalk);
        m.put("S17", () -> GuiMonkeyScenarios.previewMouseWalk(30));
        m.put("S18", GuiMonkeyScenarios::treeKeyWalk);
        m.put("S20", GuiMonkeyScenarios::gitPaneWalk);
        m.put("S8", GuiMonkey::lafAndTheme);
        m.put("S19", GuiMonkey::overlappingLoad);
        m.put("S7", GuiMonkey::projectSwitch);
        m.put("S21", () -> GuiMonkeyFuzz.fuzz(seed, fuzzSteps));
        return m;
    }

    // ── 実行順 ────────────────────────────────────────────────
    private static void runAll(List<String> ran) throws Exception {
        begin("S0-startup", ran);
        startup();
        for (Map.Entry<String, Step> e : steps().entrySet()) {
            String id = e.getKey();
            boolean wanted = selected == null || selected.contains(id);
            if ("S21".equals(id)) {
                wanted = fuzzSteps > 0 && (selected == null || selected.contains(id));
            }
            if (!wanted) {
                continue;
            }
            begin(id + "-" + catalog().get(id).replaceAll("\\s+", "-"), ran);
            try {
                e.getValue().run();
            } catch (Throwable t) {
                record("harness", "scenario-crash: " + GuiMonkeyRuntime.stack(t));
            }
            GuiMonkeyRuntime.shot(id + "-end");
        }
        begin("S12-shutdown", ran);
        shutdown();
    }

    private static void begin(String id, List<String> ran) {
        GuiMonkeyRuntime.scenario = id;
        ran.add(id);
        log("=== " + id + " ===");
    }

    // ── 起動 / 終了 ───────────────────────────────────────────
    private static void startup() throws Exception {
        onEdt(() -> {
            frame = new UmlMainFrame(project);
            frame.setSize(1400, 900);
            frame.setLocation(0, 0);
            frame.setVisible(true);
        });
        GuiMonkeyRuntime.bindFields();
        quiesce(3_000);
        checkInvariants("after-construct");
        boolean loaded = awaitTreeLoaded(TREE_LOAD_TIMEOUT_MS);
        if (!loaded) {
            record("load-timeout", "ツリーが 45 秒でロードされない: " + project);
        }
        quiesce(5_000);
        checkInvariants("after-load");
        if (!chooserEnabled()) {
            record("invariant", "ロード後も図種ボタンが無効のまま");
        }
        GuiMonkeyRuntime.shot("S0-loaded");
    }

    private static void shutdown() throws Exception {
        act("frame.dispose", () -> frame.dispose());
        Thread.sleep(1_500);
        List<String> leftover = onEdt(() -> {
            List<String> l = new ArrayList<>();
            for (Window w : Window.getWindows()) {
                if (w.isShowing()) {
                    l.add(w.getClass().getName() + " '" + titleOf(w) + "'");
                }
            }
            return l;
        });
        if (!leftover.isEmpty()) {
            record("leak-window", "dispose 後も表示中のウィンドウ: " + leftover);
        }
        List<String> threads = new ArrayList<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (!t.isDaemon() && t.isAlive() && !isJvmThread(t.getName())) {
                threads.add(t.getName());
            }
        }
        if (!threads.isEmpty()) {
            record("leak-thread", "dispose 後も生きている非デーモンスレッド: " + threads);
        }
    }

    // ── main 直下のシナリオ ───────────────────────────────────
    private static void dropdownAllKinds() throws Exception {
        for (DiagramKind k : DiagramKind.values()) {
            JMenuItem item = chooser.itemFor(k);
            if (item == null) {
                continue;
            }
            boolean enabled = onEdt(item::isEnabled);
            log("dropdown " + k + " enabled=" + enabled);
            if (!enabled) {
                continue;
            }
            act("dropdown:" + k, item::doClick);
            checkInvariants("dropdown:" + k);
        }
    }

    private static void rapidKindSwitch() throws Exception {
        DiagramKind[] cycle = { DiagramKind.CLASS, DiagramKind.PACKAGE, DiagramKind.INHERITANCE,
            DiagramKind.COMMON, DiagramKind.CYCLES };
        for (int i = 0; i < 15; i++) {
            JMenuItem it = chooser.itemFor(cycle[i % cycle.length]);
            if (it != null) {
                SwingUtilities.invokeLater(it::doClick);
            }
        }
        quiesce(20_000);
        checkInvariants("after-rapid-switch");
    }

    private static void closeAllThenDropdown() throws Exception {
        // 確認ダイアログは dialog-killer にキャンセルされるため、生のクローズ API を直接呼ぶ。
        act("closeAllTabs(raw)", () -> tabPane.closeAllTabs());
        quiesce(4_000);
        int before = onEdt(() -> tabPane.dynamicTabCount());
        JMenuItem cls = chooser.itemFor(DiagramKind.CLASS);
        if (cls != null && onEdt(cls::isEnabled)) {
            act("dropdown-after-close-all", cls::doClick);
            int after = onEdt(() -> tabPane.dynamicTabCount());
            if (after <= before) {
                record("invariant", "全タブを閉じた後に図種ドロップダウンで CLASS を選んでもタブが開かない (before="
                        + before + " after=" + after + ")");
            }
            checkInvariants("dropdown-after-close-all");
        }
        GuiMonkeyScenarios.runPalette("閉じたタブを開き直す", "reopen closed", "Reopen");
        quiesce(4_000);
        checkInvariants("after-reopen");
    }

    private static void detachedWindow() throws Exception {
        GuiMonkeyScenarios.runPalette("別ウィンドウで開く", "new window", "New Window");
        quiesce(4_000);
        int frames = onEdt(() -> {
            int n = 0;
            for (Window w : Window.getWindows()) {
                if (w instanceof Frame && w != frame && w.isShowing()) {
                    n++;
                }
            }
            return n;
        });
        log("detached frames showing=" + frames);
        checkInvariants("after-detach");
        if (detached != null) {
            act("detached.closeAll", () -> detached.closeAll());
        }
        checkInvariants("after-detach-close");
    }

    private static void lafAndTheme() throws Exception {
        act("laf:dark", () -> PreferencesDialog.applyLookAndFeelLive("FLATLAF_DARK"));
        checkInvariants("laf-dark");
        for (DiagramKind k : new DiagramKind[] { DiagramKind.PACKAGE, DiagramKind.CLASS }) {
            JMenuItem it = chooser.itemFor(k);
            if (it != null && onEdt(it::isEnabled)) {
                act("dropdown-dark:" + k, it::doClick);
                checkInvariants("dropdown-dark:" + k);
            }
        }
        for (String theme : new String[] { "plain", "cerulean", "hacker", "" }) {
            act("theme:" + (theme.isEmpty() ? "(none)" : theme),
                    () -> callPrivate(frame, "applyTheme", String.class, theme));
        }
        checkInvariants("after-theme");
        act("laf:light", () -> PreferencesDialog.applyLookAndFeelLive("FLATLAF_LIGHT"));
        act("laf:nimbus", () -> PreferencesDialog.applyLookAndFeelLive("NIMBUS"));
        act("laf:light-again", () -> PreferencesDialog.applyLookAndFeelLive("FLATLAF_LIGHT"));
        checkInvariants("laf-light");
    }

    private static void overlappingLoad() throws Exception {
        act("loadProject:alt(no-wait)", () -> callPrivate(frame, "loadProject", File.class, alt));
        quiesce(300);
        act("loadProject:orig(overlap)", () -> callPrivate(frame, "loadProject", File.class, project));
        awaitTreeLoaded(TREE_LOAD_TIMEOUT_MS);
        quiesce(5_000);
        checkInvariants("after-overlapping-load");
    }

    private static void projectSwitch() throws Exception {
        int tabsBefore = onEdt(() -> tabPane.dynamicTabCount());
        act("loadProject:alt", () -> callPrivate(frame, "loadProject", File.class, alt));
        boolean altLoaded = awaitTreeLoaded(TREE_LOAD_TIMEOUT_MS);
        quiesce(5_000);
        log("alt loaded=" + altLoaded + " tabsBefore=" + tabsBefore + " tabsAfter="
                + onEdt(() -> tabPane.dynamicTabCount()));
        checkInvariants("after-alt-load");
        if (altLoaded && !chooserEnabled()) {
            record("invariant", "別プロジェクトのロード後に図種ボタンが無効のまま");
        }
        // 別プロジェクトでも一通り図種を叩く
        for (DiagramKind k : DiagramKind.values()) {
            JMenuItem it = chooser.itemFor(k);
            if (it != null && onEdt(it::isEnabled)) {
                act("alt-dropdown:" + k, it::doClick);
                checkInvariants("alt-dropdown:" + k);
            }
        }
        GuiMonkeyScenarios.treeWalk(60);
        act("loadProject:back", () -> callPrivate(frame, "loadProject", File.class, project));
        awaitTreeLoaded(TREE_LOAD_TIMEOUT_MS);
        quiesce(5_000);
        checkInvariants("after-reload-original");
    }
}
