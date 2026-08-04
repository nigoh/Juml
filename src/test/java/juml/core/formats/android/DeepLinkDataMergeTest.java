// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 1 つの {@code <intent-filter>} 内の {@code <data>} を Android と同じ規則で束ねることの
 * 回帰テスト。
 *
 * <p>Android は同じ intent-filter 内の {@code <data>} を<b>属性ごとにプールして直積を取る</b>。
 * 要素単位では見ない。公式ドキュメントと App Links Assistant が出力する分割記法
 * (scheme / host / pathPrefix を別々の {@code <data>} に書く形) は 1 本の URI を意味するが、
 * 以前は 1 要素 = 1 URI と数えていたため、実在しない URI を 3 本でっち上げ、うち 2 本を
 * scheme 不明ゆえ App Links ではなく独自スキームへ誤分類し、本当の入口を 1 本も出して
 * いなかった。</p>
 */
public class DeepLinkDataMergeTest {

    /** 属性を 1 つだけ持つ {@code <data>} を作る。 */
    private static AndroidDataSpec data(String scheme, String host, String pathPrefix) {
        AndroidDataSpec d = new AndroidDataSpec();
        d.setScheme(scheme);
        d.setHost(host);
        d.setPathPrefix(pathPrefix);
        return d;
    }

    /** mimeType 付きの {@code <data>} を作る。 */
    private static AndroidDataSpec withMime(AndroidDataSpec d, String mimeType) {
        d.setMimeType(mimeType);
        return d;
    }

    private static AndroidProjectAnalysis analysisWith(AndroidDataSpec... specs) {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.x");
        AndroidComponentInfo entry = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.Entry");
        entry.setExported(true);
        AndroidIntentFilter f = new AndroidIntentFilter();
        f.setAutoVerify(true);
        f.getActions().add("android.intent.action.VIEW");
        f.getCategories().add("android.intent.category.DEFAULT");
        f.getCategories().add("android.intent.category.BROWSABLE");
        for (AndroidDataSpec d : specs) {
            f.getDataSpecs().add(d);
        }
        entry.getIntentFilters().add(f);
        m.getActivities().add(entry);
        a.getManifestsByModule().put("app", java.util.List.of(m));
        return a;
    }

    /** 図中の {@code rectangle "…"} ラベルを出現順に取り出す。 */
    private static java.util.List<String> uris(String puml) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("rectangle \"([^\"]+)\"").matcher(puml);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    public void splitDataElementsDescribeOneUriNotThree() {
        String puml = PlantUmlDeepLinkDiagram.generate(analysisWith(
                data("https", null, null),
                data(null, "example.com", null),
                data(null, null, "/item")), null);

        java.util.List<String> found = uris(puml);
        assertEquals("分割記法は 1 本の URI を意味すること: " + found, 1, found.size());
        assertEquals("https://example.com/item*", found.get(0));
    }

    @Test
    public void mergedUriIsClassifiedAsAnAppLinkNotACustomScheme() {
        String puml = PlantUmlDeepLinkDiagram.generate(analysisWith(
                data("https", null, null),
                data(null, "example.com", null),
                data(null, null, "/item")), null);

        // 判定は rectangle 行だけを見る。skinparam と legend は常に両方の
        // ステレオタイプに言及するので、本文全体を探すと必ず一致してしまう。
        String rect = puml.lines().filter(l -> l.contains("rectangle \""))
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue("https なので App Link として分類されること:\n" + rect,
                rect.contains("<<applink>>"));
        assertFalse("独自スキームの Deep Link に落とさないこと:\n" + rect,
                rect.contains("<<deeplink>>"));
    }

    @Test
    public void oneCompleteDataElementIsUnchanged() {
        // 非退行: 属性が揃った <data> 1 つはこれまでどおり 1 本。
        String puml = PlantUmlDeepLinkDiagram.generate(
                analysisWith(data("https", "example.com", "/item")), null);

        assertEquals(java.util.List.of("https://example.com/item*"), uris(puml));
    }

    @Test
    public void twoCompleteDataElementsCrossProductAsAndroidDoes() {
        // Android は要素をまたいで直積を取るので、完全な <data> 2 つは 4 本になる。
        // 公式ドキュメントも注意喚起している挙動で、こちらが正しい。
        String puml = PlantUmlDeepLinkDiagram.generate(analysisWith(
                data("https", "a.com", null),
                data("http", "b.com", null)), null);

        java.util.List<String> found = uris(puml);
        assertEquals("2 scheme x 2 host = 4 本: " + found, 4, found.size());
        assertTrue(found.contains("https://a.com"));
        assertTrue(found.contains("https://b.com"));
        assertTrue(found.contains("http://a.com"));
        assertTrue(found.contains("http://b.com"));
    }

    /**
     * 回帰: path 属性を複数の {@code <data>} で共有する www / non-www 記法で、
     * ノードが二重に出ないこと。
     *
     * <p>scheme と authority はプールに入れる時点で重複排除していたのに path だけ素通しで、
     * 共有された {@code pathPrefix} がプールへ 2 回入り直積が丸ごと 2 倍になっていた。</p>
     */
    @Test
    public void sharedPathAcrossDataElementsDoesNotDoubleTheNodes() {
        String puml = PlantUmlDeepLinkDiagram.generate(analysisWith(
                data("https", "example.com", "/promo"),
                data("https", "m.example.com", "/promo")), null);

        java.util.List<String> found = uris(puml);
        assertEquals("host 2 つぶんの 2 本だけになること: " + found, 2, found.size());
        assertTrue(found.contains("https://example.com/promo*"));
        assertTrue(found.contains("https://m.example.com/promo*"));
    }

    /**
     * 回帰: URI 属性と {@code mimeType} を併せ持つ {@code <data>} で MIME 制約が消えないこと。
     *
     * <p>直積へ移したとき merge() が mimeType を写していなかったため、
     * {@code content://media} が「あらゆる content://media を受ける」図になり、
     * 実際の {@code image/*} 限定が出力のどこにも残らなかった。</p>
     */
    @Test
    public void mimeTypeSurvivesTheMerge() {
        String puml = PlantUmlDeepLinkDiagram.generate(analysisWith(
                withMime(data("content", "media", null), "image/*")), null);

        java.util.List<String> found = uris(puml);
        assertEquals("ノードは 1 本: " + found, 1, found.size());
        assertTrue("URI が出ること: " + found, found.get(0).startsWith("content://media"));
        assertTrue("MIME 制約もラベルに残ること: " + found, found.get(0).contains("image/*"));
    }

    /**
     * 回帰: Markdown サマリーも図と同じ {@code <data>} 解釈を使うこと。
     *
     * <p>直積化は図 ({@code PlantUmlDeepLinkDiagram}) にだけ入れていたため、同じ manifest で
     * {@code -D} は正しい 1 本を出すのに {@code -m} / {@code --summary} / {@code -A} の
     * Markdown は<b>存在しない URI を 3 本</b>並べ、mimeType も落としていた。
     * 同じ入力で出力が食い違うのが一番たちが悪い。</p>
     */
    @Test
    public void markdownSummaryUsesTheSameDataInterpretation() {
        AndroidProjectAnalysis a = analysisWith(
                data("https", null, null),
                data(null, "example.com", null),
                data(null, null, "/item"));

        String md = TextSummaryReport.toMarkdown(a);

        assertTrue("本当の入口が出ること:\n" + md, md.contains("https://example.com/item*"));
        assertFalse("scheme だけの偽 URI を出さないこと:\n" + md, md.contains("`https://*`"));
        assertFalse("host だけの偽 URI を出さないこと:\n" + md, md.contains("`*://example.com`"));
    }

    /**
     * 回帰: 1 つの {@code <data>} に path 系属性を複数書いたら、その数だけ入口が出ること。
     *
     * <p>Android は {@code path} / {@code pathPrefix} / {@code pathPattern} …を属性ごとに
     * 独立したマッチャとして登録する。要素をまるごとプールへ入れていたため、表示側の
     * {@code effectivePath()} が<b>最初の 1 つ</b>だけを返し、残りの入口が図にもサマリーにも
     * 出てこなかった。同じ意味を要素 2 つに分けて書けば 2 本出るので、記法の書き方だけで
     * 報告件数が変わるという食い違いにもなっていた。</p>
     */
    @Test
    public void severalPathAttributesInOneDataElementAreSeveralEntryPoints() {
        AndroidDataSpec d = data("https", "a.com", "/pre");
        d.setPath("/exact");
        d.setPathPattern("/p.*t");

        java.util.List<String> found = uris(PlantUmlDeepLinkDiagram.generate(analysisWith(d), null));

        assertEquals("path / pathPrefix / pathPattern の 3 本が出ること: " + found, 3, found.size());
        assertTrue(found.contains("https://a.com/exact"));
        assertTrue(found.contains("https://a.com/pre*"));
        assertTrue(found.contains("https://a.com/p.*t"));
    }

    /** 回帰: 同じ入口を要素 1 つで書いても要素 2 つに分けて書いても結果が一致すること。 */
    @Test
    public void packingPathAttributesIntoOneElementDoesNotChangeTheResult() {
        AndroidDataSpec packed = data("https", "a.com", "/pre");
        packed.setPath("/exact");

        java.util.List<String> one = uris(
                PlantUmlDeepLinkDiagram.generate(analysisWith(packed), null));
        java.util.List<String> two = uris(PlantUmlDeepLinkDiagram.generate(analysisWith(
                data("https", "a.com", null), data("https", "a.com", "/pre")), null));
        // 2 要素版は path=/exact を持たないので、比較のため同じ内容で組み直す。
        AndroidDataSpec exact = data("https", "a.com", null);
        exact.setPath("/exact");
        java.util.List<String> split = uris(PlantUmlDeepLinkDiagram.generate(analysisWith(
                exact, data("https", "a.com", "/pre")), null));

        assertEquals("要素の詰め方で件数が変わらないこと: packed=" + one + " split=" + split,
                new java.util.HashSet<>(split), new java.util.HashSet<>(one));
        assertTrue("参考: host だけの分割版は 1 本のまま: " + two, two.size() >= 1);
    }

    /**
     * 回帰: 同じ {@code mimeType} の {@code <data>} が 2 つあっても MIME 専用ノードは 1 つ。
     *
     * <p>直積側はどのプールも重複排除していたのに、MIME 専用のフォールバックだけが生の
     * 要素列を回していた。manifest マージ (sourceSet / ライブラリ) で同じ要素が重なるのは
     * 日常的で、見分けの付かないノードが 2 つ並び、グループ見出しも「2 件」と嘘をついていた。</p>
     */
    @Test
    public void duplicateMimeOnlyDataElementsProduceOneNode() {
        AndroidDataSpec a = new AndroidDataSpec();
        a.setMimeType("image/*");
        AndroidDataSpec b = new AndroidDataSpec();
        b.setMimeType("image/*");

        String puml = PlantUmlDeepLinkDiagram.generate(analysisWith(a, b), null);

        java.util.List<String> found = uris(puml);
        assertEquals("同じ MIME 制約はノード 1 つ: " + found, 1, found.size());
        assertTrue(found.get(0).contains("image/*"));
        assertTrue("グループ見出しも 1 件と数えること:\n" + puml, puml.contains("MIME-only (1)"));
    }

    /** 非退行: 異なる mimeType が 2 つあれば 2 ノードのまま。 */
    @Test
    public void distinctMimeOnlyDataElementsStillProduceTwoNodes() {
        AndroidDataSpec a = new AndroidDataSpec();
        a.setMimeType("image/*");
        AndroidDataSpec b = new AndroidDataSpec();
        b.setMimeType("video/*");

        java.util.List<String> found = uris(
                PlantUmlDeepLinkDiagram.generate(analysisWith(a, b), null));

        assertEquals("別々の MIME は 2 ノード: " + found, 2, found.size());
    }

    /** 回帰: サマリーでも mimeType 制約が落ちないこと。 */
    @Test
    public void markdownSummaryKeepsTheMimeConstraint() {
        AndroidProjectAnalysis a = analysisWith(withMime(data("content", "media", null), "image/*"));

        String md = TextSummaryReport.toMarkdown(a);

        assertTrue("URI が出ること:\n" + md, md.contains("content://media"));
        assertTrue("MIME 制約も出ること:\n" + md, md.contains("image/*"));
    }
}
