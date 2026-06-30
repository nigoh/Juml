// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.core.formats.uml.PlantUmlRenderer;
import juml.util.ErrorListener;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlSmaliSequenceDiagram} と invoke 解析のユニットテスト。
 */
public class PlantUmlSmaliSequenceDiagramTest {

    private static ApkAnalysis decoded() throws IOException {
        return ApktoolDecodedAnalyzer.analyze(
                new File("src/test/resources/samples/apk/decoded"), ErrorListener.silent());
    }

    @Test
    public void invokesAreCollectedFromBody() throws IOException {
        ApkAnalysis a = decoded();
        SmaliClassInfo activity = a.getClasses().stream()
                .filter(c -> c.getClassName().equals("com.example.app.MainActivity"))
                .findFirst().orElseThrow();
        SmaliMethodInfo onClick = activity.getMethods().stream()
                .filter(m -> m.getName().equals("onClick")).findFirst().orElseThrow();
        boolean callsLoad = onClick.getInvokes().stream()
                .anyMatch(i -> i.getOwnerClass().equals("com.example.app.MainPresenter")
                        && i.getMethodName().equals("load"));
        assertTrue("onClick should invoke MainPresenter.load()", callsLoad);
    }

    @Test
    public void sequenceFollowsInScopeRecursion() throws IOException {
        String puml = PlantUmlSmaliSequenceDiagram.generate(decoded(),
                "com.example.app.MainActivity.onClick", null);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.trim().endsWith("@enduml"));
        // MainActivity -> MainPresenter.load(), then recurse: MainPresenter -> Repository.fetch()
        assertTrue(puml.contains("MainActivity"));
        assertTrue(puml.contains("MainPresenter"));
        assertTrue(puml.contains("load()"));
        assertTrue(puml.contains("fetch()"));
    }

    @Test
    public void frameworkCallsMarkedExternal() throws IOException {
        // MainPresenter.load() calls android.util.Log.d (framework) -> external participant
        String puml = PlantUmlSmaliSequenceDiagram.generate(decoded(),
                "com.example.app.MainPresenter.load", null);
        assertTrue(puml.contains("<<external>>"));
        assertTrue(puml.contains("Log"));
    }

    @Test
    public void simpleClassNameResolves() throws IOException {
        String puml = PlantUmlSmaliSequenceDiagram.generate(decoded(),
                "MainActivity.onClick", null);
        assertTrue(puml.contains("load()"));
    }

    @Test
    public void unresolvedEntryProducesNote() throws IOException {
        String puml = PlantUmlSmaliSequenceDiagram.generate(decoded(),
                "Nope.ghost", null);
        assertTrue(puml.contains("could not resolve entry"));
        assertTrue(puml.trim().endsWith("@enduml"));
    }

    @Test
    public void rendersToSvg() throws IOException {
        String puml = PlantUmlSmaliSequenceDiagram.generate(decoded(),
                "com.example.app.MainActivity.onClick", null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, bos);
        assertTrue(bos.toString(StandardCharsets.UTF_8).contains("<svg"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullAnalysisThrows() {
        PlantUmlSmaliSequenceDiagram.generate((ApkAnalysis) null, "X.y", null);
    }
}
