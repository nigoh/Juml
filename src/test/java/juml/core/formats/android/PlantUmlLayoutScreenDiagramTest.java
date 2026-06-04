// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * PlantUmlLayoutScreenDiagram (Salt ワイヤーフレーム) のユニットテスト。
 */
public class PlantUmlLayoutScreenDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullLayout() {
        PlantUmlLayoutScreenDiagram.generate(null);
    }

    @Test
    public void testSaltEnvelopeAndWidgets() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        String salt = PlantUmlLayoutScreenDiagram.generate(info);
        assertTrue("salt 開始", salt.startsWith("@startsalt"));
        assertTrue("salt 終了", salt.trim().endsWith("@endsalt"));
        // ボタンはワイヤーフレームのボタン表記に変換される
        assertTrue("OK ボタン", salt.contains("[ OK ]"));
        assertTrue("Cancel ボタン", salt.contains("[ Cancel ]"));
        // TextView の text はプレーンテキストとして出る
        assertTrue("TextView text", salt.contains("Hello World"));
        // 枠付きグリッド
        assertTrue("bordered group", salt.contains("{+"));
        // 既定タイトル
        assertTrue("title", salt.contains("Screen: activity_main.xml"));
    }

    @Test
    public void testHorizontalLinearLayoutJoinsChildren() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        String salt = PlantUmlLayoutScreenDiagram.generate(info);
        // 内側の横並び LinearLayout のボタンは | で連結される
        assertTrue("横並び結合", salt.contains("[ OK ] | [ Cancel ]"));
    }

    @Test
    public void testEditTextRendersAsField() {
        LayoutViewNode root = new LayoutViewNode("LinearLayout");
        LayoutViewNode edit = new LayoutViewNode("EditText");
        edit.setText("name");
        root.getChildren().add(edit);
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        info.setRoot(root);
        info.setFileName("form.xml");
        String salt = PlantUmlLayoutScreenDiagram.generate(info);
        assertTrue("入力欄表記", salt.contains("\"name\""));
    }

    @Test
    public void testStringResolverResolvesAtStringRef() {
        LayoutViewNode root = new LayoutViewNode("LinearLayout");
        LayoutViewNode tv = new LayoutViewNode("TextView");
        tv.setText("@string/greeting");
        root.getChildren().add(tv);
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        info.setRoot(root);
        info.setFileName("a.xml");

        PlantUmlLayoutScreenDiagram.Options opts = new PlantUmlLayoutScreenDiagram.Options();
        opts.stringResolver = ref -> "@string/greeting".equals(ref) ? "Hello!" : null;
        String salt = PlantUmlLayoutScreenDiagram.generate(info, opts);
        assertTrue("実文言へ解決される", salt.contains("Hello!"));
        assertFalse("参照のままにはしない", salt.contains("@string/greeting"));
    }

    @Test
    public void testMaxNodesTruncates() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        PlantUmlLayoutScreenDiagram.Options opts = new PlantUmlLayoutScreenDiagram.Options();
        opts.maxNodes = 2;
        String salt = PlantUmlLayoutScreenDiagram.generate(info, opts);
        assertTrue("truncated 表記", salt.contains("truncated (maxNodes=2)"));
    }

    @Test
    public void testSanitizeRemovesSaltSpecials() {
        assertEquals("a/b(c)", PlantUmlLayoutScreenDiagram.sanitize("a|b[c]", 0));
        assertEquals("x y", PlantUmlLayoutScreenDiagram.sanitize("x\n y", 0));
    }

    private static AndroidLayoutInfo parseSample(String name) throws IOException {
        AndroidLayoutInfo info = AndroidLayoutParser.parse(readSample(name));
        info.setFileName(name);
        return info;
    }

    private static String readSample(String name) throws IOException {
        try (InputStream in = PlantUmlLayoutScreenDiagramTest.class
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
