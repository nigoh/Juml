// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;
import juml.util.ErrorListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * AndroidLayoutParser のユニットテスト。
 */
public class AndroidLayoutParserTest {

    private static final String NS = "xmlns:android=\"http://schemas.android.com/apk/res/android\"";

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        AndroidLayoutParser.parse(null);
    }

    @Test
    public void testEmpty() {
        List<String> errors = new ArrayList<>();
        AndroidLayoutInfo info = AndroidLayoutParser.parse(
                "", ErrorListener.collecting(errors));
        assertNotNull(info);
        assertNull("root should be null on empty input", info.getRoot());
        assertFalse("expected parse error log", errors.isEmpty());
    }

    @Test
    public void testDoctypeBlocked() {
        List<String> errors = new ArrayList<>();
        String xml = "<!DOCTYPE layout [<!ENTITY x \"y\">]>\n"
                + "<LinearLayout " + NS + "/>";
        AndroidLayoutInfo info = AndroidLayoutParser.parse(
                xml, ErrorListener.collecting(errors));
        assertNotNull(info);
        assertFalse("expected doctype block error", errors.isEmpty());
    }

    @Test
    public void testMalformedXml() {
        List<String> errors = new ArrayList<>();
        AndroidLayoutInfo info = AndroidLayoutParser.parse(
                "<LinearLayout " + NS + "><Button></LinearLayout>",
                ErrorListener.collecting(errors));
        assertNotNull(info);
        assertNull(info.getRoot());
        assertFalse(errors.isEmpty());
    }

    @Test
    public void testSimpleViewHierarchy() {
        String xml =
                "<LinearLayout " + NS + " android:id=\"@+id/root\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\""
                        + " android:orientation=\"vertical\">\n"
                        + "  <TextView android:id=\"@+id/title\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"wrap_content\""
                        + " android:text=\"Hello\"/>\n"
                        + "  <Button android:id=\"@+id/ok\""
                        + " android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\""
                        + " android:text=\"OK\"/>\n"
                        + "</LinearLayout>";
        AndroidLayoutInfo info = AndroidLayoutParser.parse(xml);
        LayoutViewNode root = info.getRoot();
        assertNotNull(root);
        assertEquals("LinearLayout", root.getTag());
        assertEquals("@+id/root", root.getId());
        assertEquals("root", root.shortId());
        assertEquals("match_parent", root.getWidth());
        assertEquals("vertical", root.getExtraAttributes().get("android:orientation"));
        assertEquals(2, root.getChildren().size());
        assertEquals(LayoutViewNode.Kind.VIEW_GROUP, root.classify());

        LayoutViewNode title = root.getChildren().get(0);
        assertEquals("TextView", title.getTag());
        assertEquals("Hello", title.getText());
        assertEquals(LayoutViewNode.Kind.VIEW, title.classify());

        LayoutViewNode ok = root.getChildren().get(1);
        assertEquals("Button", ok.getTag());
        assertEquals("OK", ok.getText());
    }

    @Test
    public void testToolsAttributeIsIgnored() {
        String xml =
                "<FrameLayout " + NS
                        + " xmlns:tools=\"http://schemas.android.com/tools\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\""
                        + " tools:context=\".MainActivity\"/>";
        AndroidLayoutInfo info = AndroidLayoutParser.parse(xml);
        LayoutViewNode root = info.getRoot();
        assertNotNull(root);
        assertFalse("tools: attributes must not be stored",
                root.getExtraAttributes().containsKey("tools:context"));
    }

    @Test
    public void testCustomNamespaceAttribute() {
        String xml =
                "<View " + NS + " xmlns:app=\"http://schemas.android.com/apk/res-auto\""
                        + " android:layout_width=\"0dp\""
                        + " android:layout_height=\"0dp\""
                        + " app:layout_constraintTop_toTopOf=\"parent\"/>";
        AndroidLayoutInfo info = AndroidLayoutParser.parse(xml);
        LayoutViewNode root = info.getRoot();
        assertNotNull(root);
        assertEquals("parent",
                root.getExtraAttributes().get("app:layout_constraintTop_toTopOf"));
    }

    @Test
    public void testReservedAndroidAttrsNotDuplicatedInExtra() {
        String xml =
                "<TextView " + NS + " android:id=\"@+id/x\""
                        + " android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\""
                        + " android:text=\"abc\"/>";
        LayoutViewNode root = AndroidLayoutParser.parse(xml).getRoot();
        assertNotNull(root);
        assertEquals("@+id/x", root.getId());
        assertEquals("abc", root.getText());
        // android:id/text/layout_width/layout_height は extraAttributes に重複格納しない
        assertFalse(root.getExtraAttributes().containsKey("android:id"));
        assertFalse(root.getExtraAttributes().containsKey("android:text"));
        assertFalse(root.getExtraAttributes().containsKey("android:layout_width"));
        assertFalse(root.getExtraAttributes().containsKey("android:layout_height"));
    }

    @Test
    public void testIncludeAndFragmentAndMerge() throws IOException {
        String xml = readSample("layout_with_include.xml");
        AndroidLayoutInfo info = AndroidLayoutParser.parse(xml);
        LayoutViewNode root = info.getRoot();
        assertNotNull(root);
        assertEquals("merge", root.getTag());
        assertEquals(LayoutViewNode.Kind.MERGE, root.classify());
        assertEquals(3, root.getChildren().size());

        LayoutViewNode header = root.getChildren().get(0);
        assertEquals(LayoutViewNode.Kind.INCLUDE, header.classify());
        assertEquals("@layout/common_header", header.getIncludeLayoutRef());
        // include の layout 属性は extraAttributes に重複格納しない
        assertFalse(header.getExtraAttributes().containsKey("layout"));

        LayoutViewNode frag = root.getChildren().get(1);
        assertEquals(LayoutViewNode.Kind.FRAGMENT, frag.classify());
        assertEquals("com.example.ContentFragment", frag.getFragmentClassName());

        LayoutViewNode footer = root.getChildren().get(2);
        assertEquals(LayoutViewNode.Kind.INCLUDE, footer.classify());
        assertEquals("@layout/common_footer", footer.getIncludeLayoutRef());
    }

    @Test
    public void testDataBindingRootIsUnwrapped() throws IOException {
        String xml = readSample("databinding_root.xml");
        AndroidLayoutInfo info = AndroidLayoutParser.parse(xml);
        LayoutViewNode root = info.getRoot();
        assertNotNull(root);
        // <layout> と <data> をスキップして FrameLayout がルートになる
        assertEquals("FrameLayout", root.getTag());
        assertEquals(1, root.getChildren().size());
        assertEquals("TextView", root.getChildren().get(0).getTag());
    }

    @Test
    public void testActivityMainSample() throws IOException {
        String xml = readSample("activity_main.xml");
        AndroidLayoutInfo info = AndroidLayoutParser.parse(xml);
        LayoutViewNode root = info.getRoot();
        assertNotNull(root);
        assertEquals("LinearLayout", root.getTag());
        assertEquals(2, root.getChildren().size());
        LayoutViewNode buttons = root.getChildren().get(1);
        assertEquals("LinearLayout", buttons.getTag());
        assertEquals(2, buttons.getChildren().size());
    }

    @Test
    public void testFragmentListSample() throws IOException {
        String xml = readSample("fragment_list.xml");
        AndroidLayoutInfo info = AndroidLayoutParser.parse(xml);
        LayoutViewNode root = info.getRoot();
        assertNotNull(root);
        assertEquals("androidx.constraintlayout.widget.ConstraintLayout", root.getTag());
        assertEquals("ConstraintLayout", root.shortTag());
        assertEquals(2, root.getChildren().size());
        LayoutViewNode list = root.getChildren().get(0);
        assertEquals("androidx.recyclerview.widget.RecyclerView", list.getTag());
        // app: 名前空間の制約属性が拾える
        assertTrue(list.getExtraAttributes().keySet().stream()
                .anyMatch(k -> k.startsWith("app:layout_constraint")));
    }

    private static String readSample(String name) throws IOException {
        try (InputStream in = AndroidLayoutParserTest.class
                .getResourceAsStream("/samples/layouts/" + name)) {
            if (in == null) {
                throw new IOException("resource not found: " + name);
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = in.read(tmp)) > 0) {
                buf.write(tmp, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
