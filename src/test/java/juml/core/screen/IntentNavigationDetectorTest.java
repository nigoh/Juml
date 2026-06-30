// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.screen;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Intent ベース画面遷移検出のテスト。
 */
public class IntentNavigationDetectorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void detectsFragmentTransactionReplace() {
        String src = "package x; public class MainActivity {"
                + " void open() { getSupportFragmentManager().beginTransaction()"
                + " .replace(R.id.container, new ProfileFragment()).commit(); } }";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "MainActivity.java");
        boolean found = false;
        for (ScreenTransition t : hits) {
            if (t.getKind() == ScreenTransition.Kind.FRAGMENT_TXN
                    && "ProfileFragment".equals(t.getTargetSimpleName())) {
                found = true;
            }
        }
        assertTrue("replace(container, new XxxFragment()) を検出", found);
    }

    @Test
    public void resolvesPushVarFromFieldType() {
        String src = "package x; public class RootScreen {"
                + " private final SettingsScreen mSettings = new SettingsScreen(c);"
                + " void onClick() { getScreenManager().push(mSettings); } }";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "RootScreen.java");
        boolean found = false;
        for (ScreenTransition t : hits) {
            if (t.getKind() == ScreenTransition.Kind.SCREEN_PUSH
                    && "SettingsScreen".equals(t.getTargetSimpleName())) {
                found = true;
            }
        }
        assertTrue("push(field) を宣言型から解決", found);
    }

    @Test
    public void detectsJetpackNavByIdAndDirections() {
        String src = "package x; public class HomeFragment {"
                + " void go() {"
                + " findNavController().navigate(R.id.detailFragment);"
                + " findNavController().navigate(HomeFragmentDirections.actionHomeToSettings()); } }";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "HomeFragment.java");
        boolean byId = false;
        boolean directions = false;
        for (ScreenTransition t : hits) {
            if (t.getKind() != ScreenTransition.Kind.NAV_ACTION) {
                continue;
            }
            if ("detailFragment".equals(t.getTargetSimpleName())) {
                byId = true;
            }
            if ("actionHomeToSettings".equals(t.getTargetSimpleName())) {
                directions = true;
            }
        }
        assertTrue("navigate(R.id.x) を検出", byId);
        assertTrue("navigate(XxxDirections.actionY()) を検出", directions);
    }

    @Test
    public void resolvesNavActionToDestinationFragmentViaGraph() throws IOException {
        File java = new File(tmp.getRoot(), "app/src/main/java/x");
        File nav = new File(tmp.getRoot(), "app/src/main/res/navigation");
        assertTrue(java.mkdirs());
        assertTrue(nav.mkdirs());
        write(new File(java, "HomeFragment.java"),
                "package x; public class HomeFragment {"
                + " void go() { findNavController().navigate("
                + "HomeFragmentDirections.actionHomeToSettings()); } }");
        write(new File(nav, "nav_graph.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\""
                + " xmlns:app=\"http://schemas.android.com/apk/res-auto\""
                + " app:startDestination=\"@id/homeFragment\">"
                + "<fragment android:id=\"@+id/homeFragment\" android:name=\"x.HomeFragment\">"
                + "<action android:id=\"@+id/action_home_to_settings\""
                + " app:destination=\"@id/settingsFragment\"/></fragment>"
                + "<fragment android:id=\"@+id/settingsFragment\""
                + " android:name=\"x.SettingsFragment\"/></navigation>");
        List<ScreenTransition> hits = new IntentNavigationDetector().analyzeProject(tmp.getRoot());
        boolean resolved = false;
        for (ScreenTransition t : hits) {
            if (t.getKind() == ScreenTransition.Kind.NAV_ACTION
                    && "SettingsFragment".equals(t.getTargetSimpleName())) {
                resolved = true;
            }
        }
        assertTrue("Directions の action を遷移先 Fragment に解決", resolved);
    }

    private void write(File f, String content) throws IOException {
        try (java.io.Writer w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(f), java.nio.charset.StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    @Test
    public void detectsStartActivityWithNewIntent() {
        String src = "package com.x;\n"
                + "public class MainActivity {\n"
                + "  void onClick() {\n"
                + "    startActivity(new Intent(this, DetailActivity.class));\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "MainActivity.java");
        assertEquals(1, hits.size());
        ScreenTransition t = hits.get(0);
        assertEquals("com.x.MainActivity", t.getFromFqn());
        assertEquals("DetailActivity", t.getTargetSimpleName());
        assertEquals(ScreenTransition.Kind.START_ACTIVITY, t.getKind());
        assertEquals("onClick", t.getFromMethod());
    }

    @Test
    public void detectsSetClassPattern() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void go() {\n"
                + "    Intent i = new Intent();\n"
                + "    i.setClass(this, B.class);\n"
                + "    startActivity(i);\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        boolean foundSetClass = false;
        for (ScreenTransition t : hits) {
            if (t.getKind() == ScreenTransition.Kind.SET_CLASS
                    && "B".equals(t.getTargetSimpleName())) {
                foundSetClass = true;
            }
        }
        assertTrue("Must detect setClass transition", foundSetClass);
    }

    @Test
    public void detectsSetClassNameStringForm() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void go() {\n"
                + "    Intent i = new Intent();\n"
                + "    i.setClassName(this, \"com.x.B\");\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        assertEquals(1, hits.size());
        ScreenTransition t = hits.get(0);
        assertEquals("com.x.B", t.getTargetClassName());
        assertEquals("B", t.getTargetSimpleName());
        assertEquals(ScreenTransition.Kind.SET_CLASS, t.getKind());
    }

    @Test
    public void promotesToStartForResultWhenAdjacent() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void pick() {\n"
                + "    Intent intent = new Intent(this, PickerActivity.class);\n"
                + "    startActivityForResult(intent, 42);\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        assertEquals(1, hits.size());
        assertEquals(ScreenTransition.Kind.START_FOR_RESULT, hits.get(0).getKind());
    }

    @Test
    public void plantUmlOutputContainsNodes() {
        String src = "package com.x;\n"
                + "public class MainActivity {\n"
                + "  void onClick() {\n"
                + "    startActivity(new Intent(this, DetailActivity.class));\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "MainActivity.java");
        String puml = PlantUmlScreenFlowDiagram.render(hits);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("@enduml"));
        assertTrue(puml.contains("MainActivity"));
        assertTrue(puml.contains("DetailActivity"));
        // 生の enum 名ではなく素人向けの日本語ラベルと凡例が出る
        assertTrue(puml.contains("画面を開く"));
        assertTrue(puml.contains("画面遷移の種類"));
    }

    @Test
    public void markdownOutputListsActivities() {
        String src = "package com.x;\n"
                + "public class MainActivity {\n"
                + "  void onClick() {\n"
                + "    startActivity(new Intent(this, DetailActivity.class));\n"
                + "    startActivity(new Intent(this, SettingsActivity.class));\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "MainActivity.java");
        String md = MarkdownScreenFlowReport.render(hits);
        assertTrue(md.contains("Screen Flow Report"));
        assertTrue(md.contains("MainActivity"));
        assertTrue(md.contains("DetailActivity"));
        assertTrue(md.contains("SettingsActivity"));
        assertTrue(md.contains("Outgoing"));
    }

    @Test
    public void detectsScreenManagerPushNewScreen() {
        String src = "package com.x;\n"
                + "public class StartScreen {\n"
                + "  void onClickItem() {\n"
                + "    getScreenManager().push(new DetailScreen(getCarContext()));\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "StartScreen.java");
        assertEquals(1, hits.size());
        ScreenTransition t = hits.get(0);
        assertEquals("com.x.StartScreen", t.getFromFqn());
        assertEquals("DetailScreen", t.getTargetSimpleName());
        assertEquals(ScreenTransition.Kind.SCREEN_PUSH, t.getKind());
        assertEquals("onClickItem", t.getFromMethod());
    }

    @Test
    public void ignoresNonScreenCollectionPush() {
        // Stack/Deque 等の汎用 push(new X()) は画面遷移ではない。
        // 対象クラスが *Screen 命名でなければ SCREEN_PUSH として拾わないこと
        // (AntennaPod SyndHandler の defaultNamespaces.push(new Atom()) 誤検出の回帰)。
        String src = "package x; public class SyndHandler {\n"
                + "  SyndHandler() { state.defaultNamespaces.push(new Rss20()); }\n"
                + "  void f() { state.defaultNamespaces.push(new Atom()); }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "SyndHandler.java");
        for (ScreenTransition t : hits) {
            assertNotEquals("non-Screen push must not be SCREEN_PUSH: " + t.getTargetSimpleName(),
                    ScreenTransition.Kind.SCREEN_PUSH, t.getKind());
        }
    }

    @Test
    public void ignoresClassKeywordInsideJavadocComment() {
        // "Session class for the ... app." の "class for" を誤ってクラス名 "for" にしない
        String src = "package com.x;\n"
                + "/** Session class for the sample app. */\n"
                + "public class NavigationSession {\n"
                + "  void onStart() {\n"
                + "    startActivity(new Intent(this, NavigationService.class));\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "NavigationSession.java");
        assertEquals(1, hits.size());
        assertEquals("com.x.NavigationSession", hits.get(0).getFromFqn());
        assertEquals("NavigationSession", hits.get(0).getFromSimpleName());
    }

    @Test
    public void doesNotMatchLowercaseMethodCallInPush() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void go() { stack.push(buildItem()); }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        assertTrue("push(lowercaseCall()) は遷移として拾わない", hits.isEmpty());
    }

    @Test
    public void diagramUsesStateDiagramSafeArrows() {
        // <--> や ..> は PlantUML state 図では構文エラーになるため使わない
        java.util.List<ScreenTransition> ts = new java.util.ArrayList<>();
        ts.add(new ScreenTransition("p.A", "pick", "B", "A.java", 1,
                ScreenTransition.Kind.START_FOR_RESULT));
        ts.add(new ScreenTransition("p.A", "go", "C", "A.java", 2,
                ScreenTransition.Kind.SET_CLASS));
        String puml = PlantUmlScreenFlowDiagram.render(ts);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue("state 図で双方向矢印は不可", !puml.contains("<-->"));
        assertTrue("state 図で ..> 依存矢印は不可", !puml.contains("..>"));
        // 遷移ラベルは素人向けの日本語ラベルを使う
        assertTrue(puml.contains("結果を受け取る遷移"));
        assertTrue(puml.contains("遷移先を準備"));
    }

    @Test
    public void emptyReportRendersPlaceholder() {
        String md = MarkdownScreenFlowReport.render(new java.util.ArrayList<>());
        assertNotNull(md);
        assertTrue(md.contains("no screen transitions"));
    }

    @Test
    public void reportEnumeratesMultiStepRoutes() {
        String src = "package com.x;\n"
                + "public class StartScreen {\n"
                + "  void a() { getScreenManager().push(new MidScreen(c)); }\n"
                + "}\n"
                + "class MidScreen {\n"
                + "  void b() { getScreenManager().push(new EndScreen(c)); }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "StartScreen.java");
        // 同一ファイル内 2 クラスは primary class 起点になるため、ルート確認は
        // ビルダー直接でも行う
        List<List<String>> routes = ScreenRouteBuilder.routes(hits);
        assertNotNull(routes);
        String md = MarkdownScreenFlowReport.render(hits);
        assertTrue(md, md.contains("## Routes"));
    }

    @Test
    public void routeBuilderChainsTransitiveEdges() {
        java.util.List<ScreenTransition> ts = new java.util.ArrayList<>();
        ts.add(new ScreenTransition("p.A", "go", "B", "A.java", 1,
                ScreenTransition.Kind.SCREEN_PUSH));
        ts.add(new ScreenTransition("p.B", "go", "C", "B.java", 1,
                ScreenTransition.Kind.SCREEN_PUSH));
        List<List<String>> routes = ScreenRouteBuilder.routes(ts);
        boolean hasAbc = false;
        for (List<String> r : routes) {
            if (String.join("→", r).equals("A→B→C")) {
                hasAbc = true;
            }
        }
        assertTrue("A→B→C のルートが列挙される", hasAbc);
    }

    @Test
    public void multipleTransitionsAggregatedInDiagram() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void go1() { startActivity(new Intent(this, B.class)); }\n"
                + "  void go2() { startActivity(new Intent(this, B.class)); }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        assertEquals(2, hits.size());
        String puml = PlantUmlScreenFlowDiagram.render(hits);
        // x2 で集約されているはず
        assertTrue(puml.contains("x2"));
    }
}
