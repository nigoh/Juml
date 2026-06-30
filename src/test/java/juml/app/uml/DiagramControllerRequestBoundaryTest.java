// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import java.util.EnumMap;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramController} の DiagramRequest ビルダ ({@code buildSequenceRequest} /
 * {@code buildActivityRequest} / {@code buildCallGraphRequest}) の境界ケーステスト。
 *
 * <p>既存 {@code DiagramControllerTest} はハッピーパス ("Foo.bar") と missing-dot ("Foobar")
 * のみで、空文字・末尾ドット・先頭ドット・多重ドット (最後のドットで分割)・hidden participant
 * の伝播という境界が無防備だった。ユーザ入力起点 (EntitySearchDialog 等) の不正値が
 * どの結果/例外になるかを仕様として固定する。</p>
 *
 * <p>注: {@code null} 入力は現状 {@code NullPointerException} になる (実装が null ガードを
 * 持たない)。これは契約として曖昧なため本テストでは pin せず、フォローアップとして
 * 「3 ビルダに null → IllegalArgumentException ガードを足すべき」を残す。</p>
 */
public class DiagramControllerRequestBoundaryTest {

    private DiagramState state;
    private DiagramController controller;

    @Before
    public void setUp() {
        state = new DiagramState();
        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        EnumMap<DiagramKind, JRadioButtonMenuItem> items = new EnumMap<>(DiagramKind.class);
        EnumMap<DiagramKind, JToggleButton> toggles = new EnumMap<>(DiagramKind.class);
        for (DiagramKind k : DiagramKind.values()) {
            items.put(k, new JRadioButtonMenuItem(k.name()));
            toggles.put(k, new JToggleButton(k.name()));
        }
        DiagramControllerDeps deps = new DiagramControllerDeps();
        deps.state = state;
        deps.cacheSupplier = () -> cache;
        deps.diagramItems = items;
        deps.diagramToggles = toggles;
        deps.treePanel = new ProjectTreePanel();
        deps.mainTabs = new JTabbedPane();
        deps.tabPane = null;
        deps.statusLabel = new JLabel();
        deps.parentFrame = null;
        deps.refreshDiagram = () -> { };
        deps.onKindChanged = kind -> { };
        controller = new DiagramController(deps);
    }

    /** 多重ドットは「最後のドット」で Class / method に分割する。 */
    @Test
    public void sequenceSplitsAtLastDot() {
        DiagramRequest req = controller.buildSequenceRequest("a.b.c");
        assertEquals("class は最後のドットまで", "a.b", req.getSequenceEntryClass());
        assertEquals("method は最後のドット以降", "c", req.getSequenceEntryMethod());
    }

    /** 末尾ドットは method が空文字の request を生む (現状仕様の固定)。 */
    @Test
    public void sequenceTrailingDotYieldsEmptyMethod() {
        DiagramRequest req = controller.buildSequenceRequest("Foo.");
        assertEquals("Foo", req.getSequenceEntryClass());
        assertEquals("", req.getSequenceEntryMethod());
    }

    /** 先頭ドットは class が空文字の request を生む。 */
    @Test
    public void sequenceLeadingDotYieldsEmptyClass() {
        DiagramRequest req = controller.buildSequenceRequest(".bar");
        assertEquals("", req.getSequenceEntryClass());
        assertEquals("bar", req.getSequenceEntryMethod());
    }

    /** 空文字はドットを含まないため IllegalArgumentException。 */
    @Test(expected = IllegalArgumentException.class)
    public void sequenceEmptyStringThrows() {
        controller.buildSequenceRequest("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void activityEmptyStringThrows() {
        controller.buildActivityRequest("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void callGraphEmptyStringThrows() {
        controller.buildCallGraphRequest("");
    }

    /** activity も最後のドットで分割する。 */
    @Test
    public void activitySplitsAtLastDot() {
        DiagramRequest req = controller.buildActivityRequest("pkg.Cls.method");
        assertEquals("pkg.Cls", req.getActivityEntryClass());
        assertEquals("method", req.getActivityEntryMethod());
    }

    /** state に hidden participant があれば sequence request に伝播する。 */
    @Test
    public void sequenceRequestCarriesHiddenParticipants() {
        state.sequenceHiddenParticipants.add("X");
        state.sequenceHiddenParticipants.add("Y");
        DiagramRequest req = controller.buildSequenceRequest("Foo.bar");
        assertTrue("hidden に X が伝播", req.getSequenceHiddenParticipants().contains("X"));
        assertTrue("hidden に Y が伝播", req.getSequenceHiddenParticipants().contains("Y"));
    }

    /** hidden が空なら request の hidden も空 (フィルタ無し)。DiagramRequest は null を空集合に正規化する。 */
    @Test
    public void sequenceRequestHasEmptyHiddenWhenStateEmpty() {
        DiagramRequest req = controller.buildSequenceRequest("Foo.bar");
        Set<String> hidden = req.getSequenceHiddenParticipants();
        assertTrue("hidden 未設定なら空 (フィルタ無し)", hidden == null || hidden.isEmpty());
    }
}
