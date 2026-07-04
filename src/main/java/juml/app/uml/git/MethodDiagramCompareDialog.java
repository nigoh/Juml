// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.PlantUmlSvgRenderer;
import juml.app.uml.PlantUmlSvgRenderer.RenderedSvg;
import juml.app.uml.SvgPreviewPanel;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.util.Messages;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * git 履歴の 2 時点間で、あるメソッドの図 (アクティビティ / シーケンス) を左右に並べて
 * 比較するモードレスダイアログの共通土台。メソッド選択コンボ・左右レイアウト・変更検出・
 * 描画・書き出しまでを持ち、<b>どの図を生成するか</b>と<b>どう色付けするか</b>だけを
 * サブクラスに委ねる。
 *
 * <p>変更のあるメソッドを先頭に並べる判定は、生成した図テキストの差で行う (シグネチャが
 * 同じで本体だけ変わった関数も検出できる)。</p>
 */
abstract class MethodDiagramCompareDialog extends JDialog {

    /** 変更検出のために全メソッドの図を生成する上限 (超えたら検出を省く)。 */
    private static final int DETECT_LIMIT = 40;

    private final SvgPreviewPanel oldPanel = new SvgPreviewPanel();
    private final SvgPreviewPanel newPanel = new SvgPreviewPanel();
    private final JPanel oldHost = new JPanel(new BorderLayout());
    private final JPanel newHost = new JPanel(new BorderLayout());
    private final JComboBox<Entry> methodCombo = new JComboBox<>();

    private List<JavaClassInfo> oldClasses = List.of();
    private List<JavaClassInfo> newClasses = List.of();
    /** 書き出し用に保持する直近の描画結果 (片側 null 可)。 */
    private RenderedSvg lastOldSvg;
    private RenderedSvg lastNewSvg;
    /** 古いワーカー結果で新しい選択を上書きしないための世代。 */
    private int renderGen;

    /** コンボ 1 項目: 対象メソッドと変更有無。 */
    private static final class Entry {
        final String className;
        final String methodName;
        final boolean changed;

        Entry(String className, String methodName, boolean changed) {
            this.className = className;
            this.methodName = methodName;
            this.changed = changed;
        }

        @Override public String toString() {
            return (changed ? "● " : "　 ") + className + "." + methodName;
        }
    }

    MethodDiagramCompareDialog(Window owner, GitRepoService svc, String relPath,
                               String oldRev, String newRev, String titleKey) {
        super(owner, Messages.get(titleKey) + " - " + relPath, ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.add(new JLabel(Messages.get("git.actcmp.method")));
        methodCombo.setPrototypeDisplayValue(new Entry("SomeClassName", "someMethodName", true));
        bar.add(methodCombo);
        add(bar, BorderLayout.NORTH);

        oldHost.add(loading(), BorderLayout.CENTER);
        newHost.add(loading(), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titled(oldHost, Messages.get("git.diagcmp.old")),
                titled(newHost, Messages.get("git.diagcmp.new")));
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);
        add(DiagramExport.toolbar(this, DiagramExport.baseName(relPath),
                () -> lastOldSvg == null && lastNewSvg == null ? null
                        : DiagramExport.composite(lastOldSvg, lastNewSvg,
                                Messages.get("git.diagcmp.old"),
                                Messages.get("git.diagcmp.new"))),
                BorderLayout.SOUTH);
        setSize(1100, 720);
        setLocationRelativeTo(owner);

        methodCombo.addActionListener(e -> {
            Entry sel = (Entry) methodCombo.getSelectedItem();
            if (sel != null) {
                renderMethod(sel);
            }
        });
        startInit(svc, relPath, oldRev, newRev);
    }

    /** 対象メソッドの図 PlantUML を生成する (存在は呼び出し側が保証)。 */
    protected abstract String generateExisting(List<JavaClassInfo> classes,
                                               String className, String methodName);

    /** 旧/新 PlantUML から色付けした {@code [旧, 新]} を返す。 */
    protected abstract String[] colorizeDiagram(String oldPuml, String newPuml);

    /** 対象メソッドが存在すれば図を生成、無ければ空文字。 */
    private String diagramOf(List<JavaClassInfo> classes, String className,
                             String methodName) {
        return hasMethod(classes, className, methodName)
                ? generateExisting(classes, className, methodName) : "";
    }

    private static JLabel loading() {
        return new JLabel(Messages.get("git.umldiff.rendering"), SwingConstants.CENTER);
    }

    private static JPanel titled(JPanel host, String title) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel header = new JLabel(title, SwingConstants.CENTER);
        header.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));
        p.add(header, BorderLayout.NORTH);
        p.add(host, BorderLayout.CENTER);
        return p;
    }

    /** 旧/新ソースを解析し、メソッド一覧 (変更を先頭) を組んでコンボへ流す。 */
    private void startInit(GitRepoService svc, String relPath, String oldRev, String newRev) {
        new SwingWorker<List<Entry>, Void>() {
            @Override protected List<Entry> doInBackground() throws Exception {
                String base = oldRev != null ? oldRev : svc.parentOf(newRev);
                oldClasses = parseQuietly(base != null
                        ? svc.fileContentAt(base, relPath) : null);
                newClasses = parseQuietly(svc.fileContentAt(newRev, relPath));
                return buildEntries(oldClasses, newClasses);
            }

            @Override protected void done() {
                try {
                    List<Entry> entries = get();
                    if (entries.isEmpty()) {
                        showNote(oldHost, Messages.get("git.actcmp.noMethods"));
                        showNote(newHost, Messages.get("git.actcmp.noMethods"));
                        return;
                    }
                    methodCombo.setModel(new DefaultComboBoxModel<>(
                            entries.toArray(new Entry[0])));
                    methodCombo.setSelectedIndex(0); // buildEntries は変更を先頭に並べる
                } catch (Exception ex) {
                    showNote(oldHost, errText(ex));
                    showNote(newHost, errText(ex));
                }
            }
        }.execute();
    }

    /** 選択メソッドの旧/新図を生成・色付けし、左右に描画する。 */
    private void renderMethod(Entry entry) {
        oldHost.removeAll();
        oldHost.add(loading(), BorderLayout.CENTER);
        newHost.removeAll();
        newHost.add(loading(), BorderLayout.CENTER);
        oldHost.revalidate();
        newHost.revalidate();
        final int gen = ++renderGen;
        new SwingWorker<RenderedSvg[], Void>() {
            @Override protected RenderedSvg[] doInBackground() {
                String oldD = diagramOf(oldClasses, entry.className, entry.methodName);
                String newD = diagramOf(newClasses, entry.className, entry.methodName);
                String[] colored = colorizeDiagram(oldD, newD);
                return new RenderedSvg[]{
                        renderQuietly(colored[0]), renderQuietly(colored[1])};
            }

            @Override protected void done() {
                if (gen != renderGen) {
                    return;
                }
                try {
                    RenderedSvg[] svg = get();
                    lastOldSvg = svg[0];
                    lastNewSvg = svg[1];
                    showSvg(oldHost, oldPanel, svg[0]);
                    showSvg(newHost, newPanel, svg[1]);
                } catch (Exception ex) {
                    showNote(oldHost, errText(ex));
                    showNote(newHost, errText(ex));
                }
            }
        }.execute();
    }

    /** 変更のあるメソッドを先頭に並べたエントリ一覧を作る。 */
    private List<Entry> buildEntries(List<JavaClassInfo> oldC, List<JavaClassInfo> newC) {
        Set<String> keys = new LinkedHashSet<>();
        List<String[]> pairs = new ArrayList<>(); // [className, methodName]
        collect(newC, keys, pairs);
        collect(oldC, keys, pairs);
        boolean detect = pairs.size() <= DETECT_LIMIT;

        List<Entry> changed = new ArrayList<>();
        List<Entry> same = new ArrayList<>();
        for (String[] p : pairs) {
            boolean diff = detect
                    && !diagramOf(oldC, p[0], p[1]).equals(diagramOf(newC, p[0], p[1]));
            Entry e = new Entry(p[0], p[1], diff);
            (diff ? changed : same).add(e);
        }
        changed.addAll(same);
        return changed;
    }

    private static void collect(List<JavaClassInfo> classes, Set<String> keys,
                                List<String[]> pairs) {
        for (JavaClassInfo c : classes) {
            for (JavaMethodInfo m : c.getMethods()) {
                String key = c.getSimpleName() + "#" + m.getName();
                if (keys.add(key)) {
                    pairs.add(new String[]{c.getSimpleName(), m.getName()});
                }
            }
        }
    }

    private static boolean hasMethod(List<JavaClassInfo> classes, String className,
                                     String methodName) {
        for (JavaClassInfo c : classes) {
            if (c.getSimpleName().equals(className)) {
                for (JavaMethodInfo m : c.getMethods()) {
                    if (m.getName().equals(methodName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static RenderedSvg renderQuietly(String puml) {
        if (puml == null || puml.isEmpty()) {
            return null; // その版にメソッドが無い
        }
        try {
            return PlantUmlSvgRenderer.render(puml);
        } catch (Exception ex) {
            return null;
        }
    }

    private static void showSvg(JPanel host, SvgPreviewPanel panel, RenderedSvg svg) {
        host.removeAll();
        if (svg != null) {
            panel.setSvgGraphicsNode(svg.getRoot(), svg.getWidth(), svg.getHeight());
            host.add(new JScrollPane(panel), BorderLayout.CENTER);
        } else {
            host.add(new JLabel(Messages.get("git.actcmp.absent"), SwingConstants.CENTER),
                    BorderLayout.CENTER);
        }
        host.revalidate();
        host.repaint();
    }

    private static void showNote(JPanel host, String text) {
        host.removeAll();
        host.add(new JLabel(text, SwingConstants.CENTER), BorderLayout.CENTER);
        host.revalidate();
        host.repaint();
    }

    private static String errText(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private static List<JavaClassInfo> parseQuietly(String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        try {
            return JavaStructureExtractor.extract(source);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
