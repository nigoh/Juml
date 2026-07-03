// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link GraphvizLocator} の dot バイナリ検出ロジックのテスト。
 */
public class GraphvizLocatorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private String savedProp;

    @Before
    public void setUp() {
        savedProp = System.getProperty(GraphvizLocator.PLANTUML_DOT_PROP);
        System.clearProperty(GraphvizLocator.PLANTUML_DOT_PROP);
        PlantUmlRenderer.setGraphvizAvailable(false);
    }

    @After
    public void tearDown() {
        if (savedProp != null) {
            System.setProperty(GraphvizLocator.PLANTUML_DOT_PROP, savedProp);
        } else {
            System.clearProperty(GraphvizLocator.PLANTUML_DOT_PROP);
        }
        PlantUmlRenderer.setGraphvizAvailable(false);
    }

    @Test
    public void findBundledDotReturnsNullWhenAbsent() {
        File result = GraphvizLocator.findBundledDot(tmp.getRoot());
        assertNull(result);
    }

    @Test
    public void findBundledDotFindsExecutable() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        String exe = os.contains("win") ? "dot.exe" : "dot";

        // プラットフォーム固有パスに実行可能ファイルを作成
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String archNorm = (arch.contains("aarch") || arch.contains("arm64")) ? "aarch64" : "amd64";
        String platform = os.contains("win") ? "windows-" + archNorm
                : os.contains("mac") ? "mac-" + archNorm : "linux-" + archNorm;

        File platformDir = new File(tmp.getRoot(), "graphviz" + File.separator + platform);
        assertTrue(platformDir.mkdirs());
        File dotFile = new File(platformDir, exe);
        assertTrue(dotFile.createNewFile());
        assertTrue(dotFile.setExecutable(true));

        File found = GraphvizLocator.findBundledDot(tmp.getRoot());
        assertNotNull(found);
        assertTrue(found.getAbsolutePath().contains("graphviz"));
    }

    @Test
    public void findBundledDotFallsBackToSimplePath() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        String exe = os.contains("win") ? "dot.exe" : "dot";

        File simpleDir = new File(tmp.getRoot(), "graphviz");
        assertTrue(simpleDir.mkdirs());
        File dotFile = new File(simpleDir, exe);
        assertTrue(dotFile.createNewFile());
        assertTrue(dotFile.setExecutable(true));

        File found = GraphvizLocator.findBundledDot(tmp.getRoot());
        assertNotNull(found);
    }

    @Test
    public void initWithBundledDotSetsGraphvizAvailable() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        String exe = os.contains("win") ? "dot.exe" : "dot";

        File simpleDir = new File(tmp.getRoot(), "graphviz");
        assertTrue(simpleDir.mkdirs());
        File dotFile = new File(simpleDir, exe);
        assertTrue(dotFile.createNewFile());
        assertTrue(dotFile.setExecutable(true));

        assertFalse(PlantUmlRenderer.isGraphvizAvailable());
        GraphvizLocator.init(tmp.getRoot());
        assertTrue(PlantUmlRenderer.isGraphvizAvailable());
        assertNotNull(System.getProperty(GraphvizLocator.PLANTUML_DOT_PROP));
    }

    @Test
    public void initWithNoDotDoesNotSetGraphvizAvailable() {
        GraphvizLocator.init(tmp.getRoot());
        // PATH に本物の dot がある場合はそちらで true になる可能性があるため、
        // PATH 検索結果に応じてアサートする
        boolean hasSystemDot = GraphvizLocator.findSystemDot() != null;
        assertTrue(PlantUmlRenderer.isGraphvizAvailable() == hasSystemDot);
    }

    @Test
    public void initWithNullJarDirStillChecksSysProp() throws IOException {
        // 実行可能な dot を指すプロパティは jarDir=null でも尊重される
        File dot = tmp.newFile("dot");
        if (!dot.setExecutable(true) && !dot.canExecute()) {
            return; // 実行ビットを立てられない環境ではスキップ相当
        }
        System.setProperty(GraphvizLocator.PLANTUML_DOT_PROP, dot.getAbsolutePath());
        GraphvizLocator.init(null);
        assertTrue(PlantUmlRenderer.isGraphvizAvailable());
    }

    @Test
    public void initIgnoresNonExecutableSysProp() {
        // 実在しない dot を指す設定は無視して他の検出手段へフォールバックする。
        // 実在チェックなしで有効化すると、レンダリング時に dot 起動が失敗して
        // 要素の無い SVG が「成功」として生成されてしまう (無音破損の回帰防止)。
        System.setProperty(GraphvizLocator.PLANTUML_DOT_PROP, "/fake/dot");
        GraphvizLocator.init(null);
        boolean hasSystemDot = GraphvizLocator.findSystemDot() != null;
        assertTrue(PlantUmlRenderer.isGraphvizAvailable() == hasSystemDot);
    }

    @Test
    public void useDotBinaryRejectsNullAndNonExecutable() throws IOException {
        assertFalse(GraphvizLocator.useDotBinary(null));
        assertFalse(PlantUmlRenderer.isGraphvizAvailable());

        File plain = tmp.newFile("not-dot.txt");
        plain.setExecutable(false);
        // 実行不能なファイルは拒否される（実行ビットを落とせない環境ではスキップ相当）。
        if (!plain.canExecute()) {
            assertFalse(GraphvizLocator.useDotBinary(plain));
            assertFalse(PlantUmlRenderer.isGraphvizAvailable());
        }
    }

    @Test
    public void useDotBinaryAcceptsExecutableAndSetsProperty() throws IOException {
        File dot = tmp.newFile("dot");
        assertTrue(dot.setExecutable(true));

        assertTrue(GraphvizLocator.useDotBinary(dot));
        assertTrue(PlantUmlRenderer.isGraphvizAvailable());
        assertEquals(dot.getAbsolutePath(),
                System.getProperty(GraphvizLocator.PLANTUML_DOT_PROP));
    }

    @Test
    public void redetectHonoursExecutableSystemProperty() throws IOException {
        File dot = tmp.newFile("dot");
        assertTrue(dot.setExecutable(true));
        System.setProperty(GraphvizLocator.PLANTUML_DOT_PROP, dot.getAbsolutePath());

        assertTrue(GraphvizLocator.redetect());
        assertTrue(PlantUmlRenderer.isGraphvizAvailable());
    }
}
