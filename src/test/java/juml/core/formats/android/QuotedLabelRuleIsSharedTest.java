// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * 引用符付きラベル {@code "…"} のエスケープ規則が<b>全生成器で同じ</b>ことの回帰テスト。
 *
 * <p>この規則は Android 系の 5 つの生成器に書き写されていて、うち 4 つは
 * {@code "} を {@code '} に置くだけだった。manifest 図だけが改行を畳み creole を
 * 無害化していたので、<b>同じ文字列でも図種によって描けたり全損したりした</b>。</p>
 *
 * <p>改行はラベルを途中で閉じるので PlantUML が構文エラーになり、図が 1 枚も出ない。
 * 実際に起きる入力である — {@code strings.xml} の {@code &#10;} も、
 * {@code android:text} の複数行も、Android では普通に書かれる。ここでは文字列一致では
 * なく<b>実レンダリング</b>で判定する (過去に文字列一致テストが不正構文をそのまま
 * 期待していた実例があるため)。</p>
 */
public class QuotedLabelRuleIsSharedTest {

    /** ラベルを壊しうるものを全部入れた文言。改行・引用符・creole タグ。 */
    private static final String HOSTILE = "行1\n行2 \"quoted\" <b>bold</b>\tタブ";

    /** 実際に描けること (描けなければ PlantUmlRenderFailedException が飛ぶ)。 */
    private static void assertRenders(String puml, String label) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, out);
        assertTrue(label + ": SVG が出力されること", out.size() > 0);
        assertTrue(label + ": ラベルが途中で閉じないこと (生の改行が残っていない)",
                quotedLabelsAreSingleLine(puml));
    }

    /** {@code "…"} の中に生の改行が残っていないか。 */
    private static boolean quotedLabelsAreSingleLine(String puml) {
        boolean inQuote = false;
        for (int i = 0; i < puml.length(); i++) {
            char c = puml.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (inQuote && (c == '\n' || c == '\r')) {
                return false;
            }
        }
        return true;
    }

    /** Navigation-graph 図: {@code android:label} と引数の既定値が manifest 由来。 */
    @Test
    public void navigationGraphLabelsSurviveHostileText() throws IOException {
        AndroidNavigationGraphInfo info = new AndroidNavigationGraphInfo();
        info.setFileName("nav_main.xml");
        info.setGraphId("nav_main");
        info.setStartDestination("@id/home");

        NavigationDestination home = new NavigationDestination();
        home.setKind(NavigationDestination.Kind.FRAGMENT);
        home.setId("home");
        home.setName("com.x.HomeFragment");
        home.setLabel(HOSTILE);
        info.getDestinations().add(home);

        NavigationDestination detail = new NavigationDestination();
        detail.setKind(NavigationDestination.Kind.FRAGMENT);
        detail.setId("detail");
        detail.setName("com.x.DetailFragment");
        detail.setLabel("詳細");
        info.getDestinations().add(detail);

        assertRenders(PlantUmlNavigationGraphDiagram.generate(info), "nav-graph");
    }

    /** Layout 図: {@code android:text} に XML の {@code &#10;} で本物の改行が入る。 */
    @Test
    public void layoutLabelsSurviveHostileText() throws IOException {
        AndroidLayoutInfo layout = new AndroidLayoutInfo();
        layout.setFileName("activity_main.xml");
        LayoutViewNode root = new LayoutViewNode("LinearLayout");
        root.setId("@+id/root");
        LayoutViewNode text = new LayoutViewNode("TextView");
        text.setId("@+id/message");
        text.setText(HOSTILE);
        text.setContentDescription(HOSTILE);
        root.getChildren().add(text);
        layout.setRoot(root);

        assertRenders(PlantUmlLayoutDiagram.generate(layout), "layout");
    }

    /** Resource-link 図: {@code strings.xml} の実文言に {@code <b>} や改行が入る。 */
    @Test
    public void resourceLinkLabelsSurviveHostileText() throws IOException {
        ResourceLinkAnalysis model = new ResourceLinkAnalysis();
        model.getReferences().add(new ResourceReference(
                "MainActivity", ResourceReference.Kind.STRING, "greeting", false, "f"));
        AndroidProjectAnalysis analysis = new AndroidProjectAnalysis();
        AndroidStringResources sr = new AndroidStringResources();
        sr.getStrings().put("greeting", HOSTILE);
        List<AndroidStringResources> list = new ArrayList<>();
        list.add(sr);
        analysis.getStringResourcesByModule().put(":root", list);
        model.setAnalysis(analysis);

        assertRenders(PlantUmlResourceLinkDiagram.generate(model), "resource-link");
    }

    /** Deep-link 図と Manifest 図: どちらも同じ manifest の値を読む。 */
    @Test
    public void manifestBackedLabelsSurviveHostileText() throws IOException {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.x");
        m.setApplicationLabel(HOSTILE);

        AndroidComponentInfo entry = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.WebEntry");
        entry.setExported(true);
        AndroidIntentFilter web = new AndroidIntentFilter();
        web.setAutoVerify(true);
        web.getActions().add("android.intent.action.VIEW");
        web.getCategories().add("android.intent.category.BROWSABLE");
        AndroidDataSpec ws = new AndroidDataSpec();
        ws.setScheme("https");
        ws.setHost("example.com");
        // deep-link 図のラベルは scheme/host/path から組む。XML 属性なので
        // &#10; で本物の改行が入りうる。
        ws.setPathPrefix("/share" + HOSTILE);
        web.getDataSpecs().add(ws);
        entry.getIntentFilters().add(web);
        m.getActivities().add(entry);

        List<AndroidManifestInfo> ms = new ArrayList<>();
        ms.add(m);
        a.getManifestsByModule().put(":app", ms);

        assertRenders(PlantUmlDeepLinkDiagram.generate(a), "deep-link");
        assertRenders(PlantUmlManifestDiagram.generate(a), "manifest");
    }
}
