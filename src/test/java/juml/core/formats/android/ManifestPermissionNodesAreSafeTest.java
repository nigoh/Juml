// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Manifest 図の {@code uses-permission} が<b>素の {@code [name]} 短縮構文</b>を
 * やめたことの回帰テスト。
 *
 * <p>兄弟の {@link PlantUmlComponentDiagram} は同じ {@code AndroidPermissionInfo} を
 * 読むのに、まさにこの問題のために {@code [name]} をやめて引用符付き component 形式へ
 * 変えてあり、コメントで理由まで書いてある。規則が片方の経路にだけ適用されていた。</p>
 *
 * <p>失敗モードは 2 つ。(1) permission 名は AndroidManifest.xml の<b>属性値</b>なので
 * {@code &#10;} で本物の改行が、あるいは {@code ]} が入りうる。すると {@code [...]} が
 * 途中で閉じて<b>図が 1 枚も出ない</b>。(2) {@code [name]} は表示名であると同時に
 * <b>エイリアスでもある</b>ため、末尾セグメントが同じ 2 つの permission が同一エイリアスで
 * 二重宣言され、PlantUML が後勝ちで 1 個に畳んで片方が図から消える。</p>
 *
 * <p>判定は文字列一致ではなく<b>実レンダリング</b>で行う。</p>
 */
public class ManifestPermissionNodesAreSafeTest {

    private static AndroidProjectAnalysis withPermissions(String... names) {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.x");
        for (String n : names) {
            m.getPermissions().add(new AndroidPermissionInfo(n));
        }
        List<AndroidManifestInfo> ms = new ArrayList<>();
        ms.add(m);
        a.getManifestsByModule().put(":app", ms);
        return a;
    }

    private static String render(AndroidProjectAnalysis a) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(PlantUmlManifestDiagram.generate(a), out);
        return out.toString("UTF-8");
    }

    /** 改行入りの permission 名。属性値なので実際に起きる。 */
    @Test
    public void aNewlineInAPermissionNameStillRenders() throws IOException {
        String svg = render(withPermissions("com.x.permission.行1\n行2"));
        assertTrue("SVG が出力されること", svg.contains("<svg"));
    }

    /** {@code ]} を含む名前。素の {@code [name]} なら途中で閉じて構文エラーになる。 */
    @Test
    public void aBracketInAPermissionNameStillRenders() throws IOException {
        String svg = render(withPermissions("com.x.permission.FOO]BAR"));
        assertTrue("SVG が出力されること", svg.contains("<svg"));
    }

    /** 末尾セグメントが同じ 2 つの permission が、どちらも図に残ること。 */
    @Test
    public void twoPermissionsSharingTheirLastSegmentBothSurvive() throws IOException {
        AndroidProjectAnalysis a =
                withPermissions("android.permission.CAMERA", "com.x.permission.CAMERA");
        String puml = PlantUmlManifestDiagram.generate(a);
        assertEquals("生成テキストに permission ノードが 2 行出ること", 2,
                puml.lines().filter(l -> l.contains("<<permission>>")
                        && l.contains("CAMERA")).count());

        Matcher mt = Pattern.compile(">([^<>]*CAMERA[^<>]*)<").matcher(render(a));
        int nodes = 0;
        while (mt.find()) {
            nodes++;
        }
        assertEquals("描画後も CAMERA のノードが 2 個あること (後勝ちで畳まれない)", 2, nodes);
    }

    /** 非退行: 普通の名前は今までどおり 1 個ずつ描かれること。 */
    @Test
    public void ordinaryPermissionsRenderOneNodeEach() throws IOException {
        Matcher mt = Pattern.compile(">([^<>]*CAMERA[^<>]*)<").matcher(
                render(withPermissions("android.permission.INTERNET",
                        "android.permission.CAMERA")));
        int nodes = 0;
        while (mt.find()) {
            nodes++;
        }
        assertEquals("CAMERA は 1 個だけ", 1, nodes);
    }
}
