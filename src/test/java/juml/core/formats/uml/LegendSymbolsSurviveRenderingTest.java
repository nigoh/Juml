// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidDataSpec;
import juml.core.formats.android.AndroidIntentFilter;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.android.AndroidProjectAnalysis;
import juml.core.formats.android.PlantUmlDeepLinkDiagram;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * 凡例に書いた記号が<b>実際に描かれる</b>ことを、同梱 PlantUML で描画して確かめる。
 *
 * <p>文字列一致では守れない。{@code legend} の中は creole が効き、行頭の {@code #} は
 * 番号付きリストとして消費されるので、PUML に {@code "# protected"} と書いてあっても
 * 図には {@code "1."} と {@code "protected"} しか出ない — <b>説明対象の記号だけが落ちる</b>。
 * 同じことが可視性凡例と Deep Link 図の凡例の 2 か所で起きていた (同一規則の兄弟経路)。</p>
 *
 * <p>「どの文字が creole のマークアップか」を数え上げても必ず取りこぼすので、ここでは
 * <b>描画結果のテキストに記号が現れること</b>だけを言明する。逃がし方を変えても、
 * 新しい記号を凡例に足しても、この言明は同じ形で効く。</p>
 */
public class LegendSymbolsSurviveRenderingTest {

    private static final Pattern TEXT_NODE = Pattern.compile("<text[^>]*>([^<]*)</text>");

    /** PUML を描画し、SVG のテキストノードを連結して返す。 */
    private static String renderedText(String puml) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, bo);
        Matcher m = TEXT_NODE.matcher(bo.toString("UTF-8"));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            sb.append(m.group(1)).append('\n');
        }
        return sb.toString();
    }

    @Test
    public void theVisibilitySymbolsAreAllDrawn() throws Exception {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showVisibility = true;
        o.visibilityIcons = false;   // 記号表示モード (CLI の可視性アイコン無効指定)
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("p");
        c.setSimpleName("A");
        JavaFieldInfo f = new JavaFieldInfo();
        f.setName("x");
        f.setType("int");
        f.setVisibility(Visibility.PROTECTED);
        c.getFields().add(f);

        String text = renderedText(PlantUmlClassDiagram.generate(List.of(c), o));
        for (String symbol : new String[]{"+ public", "- private", "# protected",
                "~ package-private"}) {
            assertTrue("凡例に " + symbol + " が描かれること。実際の描画テキスト:\n" + text,
                    text.contains(symbol));
        }
    }

    @Test
    public void theDeepLinkLegendKeepsItsColourSymbol() throws Exception {
        AndroidProjectAnalysis analysis = new AndroidProjectAnalysis();
        AndroidManifestInfo manifest = new AndroidManifestInfo();
        manifest.setPackageName("com.x");
        AndroidComponentInfo entry = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.Entry");
        entry.setExported(true);
        AndroidIntentFilter filter = new AndroidIntentFilter();
        filter.getActions().add("android.intent.action.VIEW");
        filter.getCategories().add("android.intent.category.BROWSABLE");
        AndroidDataSpec data = new AndroidDataSpec();
        data.setScheme("https");
        data.setHost("example.com");
        filter.getDataSpecs().add(data);
        entry.getIntentFilters().add(filter);
        manifest.getActivities().add(entry);
        analysis.getManifestsByModule().put("app", List.of(manifest));

        String text = renderedText(PlantUmlDeepLinkDiagram.generate(analysis));
        assertTrue("凡例の #LightYellow が記号ごと描かれること。実際の描画テキスト:\n" + text,
                text.contains("#LightYellow"));
    }

    /** Manifest 図の凡例も同じ規則を通ること (3 本目の兄弟経路)。 */
    @Test
    public void theManifestLegendKeepsItsColourSymbol() throws Exception {
        String text = renderedText(
                juml.core.formats.android.PlantUmlManifestDiagram.generate(exportedActivity()));
        assertTrue("凡例の #LightYellow が記号ごと描かれること。実際の描画テキスト:\n" + text,
                text.contains("#LightYellow"));
    }

    /** Android コンポーネント図の凡例も同じ規則を通ること (4 本目)。 */
    @Test
    public void theComponentLegendKeepsItsColourSymbol() throws Exception {
        String text = renderedText(
                juml.core.formats.android.PlantUmlComponentDiagram.generate(exportedActivity()));
        assertTrue("凡例の #LightYellow が記号ごと描かれること。実際の描画テキスト:\n" + text,
                text.contains("#LightYellow"));
    }

    /**
     * コールグラフ (WBS) の凡例も同じ規則を通ること (5 本目)。
     *
     * <p>ここは色トークンが変数なので、先頭の空白を置いても記号は creole に食われる
     * (PlantUML は行頭の空白を落としてから creole を見る)。</p>
     */
    @Test
    public void theCallGraphLegendKeepsBothColourSymbols() throws Exception {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("p");
        c.setSimpleName("A");
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName("run");
        m.setReturnType("void");
        c.getMethods().add(m);
        String text = renderedText(
                PlantUmlCallGraphDiagram.generate(List.of(c), "A", "run", null));
        assertTrue("起点色が記号ごと描かれること。実際の描画テキスト:\n" + text,
                text.contains("#LightSkyBlue"));
        assertTrue("プロジェクト内クラス色が記号ごと描かれること。実際の描画テキスト:\n" + text,
                text.contains("#LightYellow"));
    }

    /** exported=true の Activity を 1 つ持つ最小の解析結果。 */
    private static AndroidProjectAnalysis exportedActivity() {
        AndroidProjectAnalysis analysis = new AndroidProjectAnalysis();
        AndroidManifestInfo manifest = new AndroidManifestInfo();
        manifest.setPackageName("com.x");
        AndroidComponentInfo entry = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.Entry");
        entry.setExported(true);
        AndroidIntentFilter filter = new AndroidIntentFilter();
        filter.getActions().add("android.intent.action.VIEW");
        filter.getCategories().add("android.intent.category.BROWSABLE");
        AndroidDataSpec data = new AndroidDataSpec();
        data.setScheme("https");
        data.setHost("example.com");
        filter.getDataSpecs().add(data);
        entry.getIntentFilters().add(filter);
        manifest.getActivities().add(entry);
        analysis.getManifestsByModule().put("app", List.of(manifest));
        return analysis;
    }
}
