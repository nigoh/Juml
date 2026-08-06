// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * スタイル名の照合で {@code .} と {@code _} を同一視することの回帰テスト。
 *
 * <p>aapt は {@code R.style.*} を生成するときスタイル名のドットをアンダースコアへ変える。
 * XML 側は {@code Theme.MyApp.Dialog}、コード側は必ず {@code Theme_MyApp_Dialog} になる。
 * 素の文字列比較で引いていたので、<b>同じスタイルが 2 つの別ノードとして描かれ、
 * 継承チェーンがそこで切れる</b>。ドット継承は Android の標準的な書き方なので、
 * {@code Theme.AppCompat.*} を使う通常のアプリで必ず起きる。</p>
 */
public class StyleNameMatchingTest {

    /** {@code Theme.MyApp} → {@code Theme.AppCompat.Light} を持つ解析結果。 */
    private static AndroidProjectAnalysis analysisWithDottedStyles() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidStyleResources sr = new AndroidStyleResources();

        AndroidStyleResources.StyleDef base = new AndroidStyleResources.StyleDef("Theme.MyApp");
        base.setParent("Theme.AppCompat.Light");
        sr.getStyles().put("Theme.MyApp", base);

        AndroidStyleResources.StyleDef dialog =
                new AndroidStyleResources.StyleDef("Theme.MyApp.Dialog");
        dialog.setParent("Theme.MyApp");
        sr.getStyles().put("Theme.MyApp.Dialog", dialog);

        List<AndroidStyleResources> list = new ArrayList<>();
        list.add(sr);
        a.getStyleResourcesByModule().put(":app", list);
        return a;
    }

    /** 正規形はドットをアンダースコアへ寄せること (参照の書式によらず同じ答え)。 */
    @Test
    public void canonicalFormFoldsDotsIntoUnderscores() {
        assertEquals("Theme_MyApp_Dialog",
                AndroidProjectAnalysis.canonicalStyleName("@style/Theme.MyApp.Dialog"));
        assertEquals("Theme_MyApp_Dialog",
                AndroidProjectAnalysis.canonicalStyleName("R.style.Theme_MyApp_Dialog"));
        assertNull(AndroidProjectAnalysis.canonicalStyleName(null));
        assertNull(AndroidProjectAnalysis.canonicalStyleName(""));
    }

    /** コード側の {@code R.style.Theme_MyApp_Dialog} が XML 側の定義に届くこと。 */
    @Test
    public void aCodeSideReferenceResolvesToTheXmlDefinition() {
        AndroidProjectAnalysis a = analysisWithDottedStyles();

        AndroidStyleResources.StyleDef def = a.findStyle("R.style.Theme_MyApp_Dialog");
        assertNotNull("アンダースコア形の参照が解決すること", def);
        assertEquals("Theme.MyApp.Dialog", def.getName());
        assertEquals("Theme.MyApp", a.resolveStyleParent("R.style.Theme_MyApp_Dialog"));
    }

    /** 非退行: XML 側の書式 ({@code @style/…}) は従来どおり素の名前で引けること。 */
    @Test
    public void anXmlReferenceStillResolvesExactly() {
        AndroidProjectAnalysis a = analysisWithDottedStyles();

        assertEquals("Theme.AppCompat.Light", a.resolveStyleParent("@style/Theme.MyApp"));
        assertEquals("Theme.MyApp", a.resolveStyleParent("@style/Theme.MyApp.Dialog"));
    }

    /** 非退行: 定義の無い名前は null のままであること (正規形でも作り出さない)。 */
    @Test
    public void anUndefinedStyleStaysUnresolved() {
        AndroidProjectAnalysis a = analysisWithDottedStyles();

        assertNull(a.findStyle("R.style.Theme_Other"));
        assertNull(a.findStyle("@style/Nope"));
    }
}
