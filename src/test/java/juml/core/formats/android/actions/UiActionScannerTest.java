// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.actions;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * UiActionScanner のユニットテスト。
 */
public class UiActionScannerTest {

    private final UiActionScanner scanner = new UiActionScanner();

    @Test
    public void detectsSetOnClickListener() {
        String src = "public class MainActivity {\n"
                + "  void setup() {\n"
                + "    btnLogin.setOnClickListener(v -> login());\n"
                + "  }\n"
                + "}\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "MainActivity.java");
        boolean found = false;
        for (UiActionEntry e : entries) {
            if (e.actionType == UiActionEntry.ActionType.ON_CLICK) {
                found = true;
                assertEquals(3, e.line);
            }
        }
        assertTrue("Must detect setOnClickListener", found);
    }

    @Test
    public void detectsKotlinTrailingLambdaHandlers() {
        // Kotlin の SAM 変換 + trailing lambda (括弧なし {}) を取りこぼさない
        String src = "class MyFragment {\n"
                + "  fun setup() {\n"
                + "    btnSave.setOnClickListener { save() }\n"
                + "    switchDark.setOnCheckedChangeListener { _, b -> set(b) }\n"
                + "  }\n"
                + "}\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "MyFragment.kt");
        assertTrue("trailing-lambda onClick", entries.stream()
                .anyMatch(e -> e.actionType == UiActionEntry.ActionType.ON_CLICK
                        && "btnSave".equals(e.componentId)));
        assertTrue("trailing-lambda onCheckedChanged", entries.stream()
                .anyMatch(e -> e.actionType == UiActionEntry.ActionType.ON_CHECKED_CHANGED));
    }

    @Test
    public void doesNotDetectHandlerInLineComment() {
        // 行コメント中の擬似コードは誤検出しない ({} を許容したことの副作用を防ぐ)
        String src = "class A {\n"
                + "  fun f() {\n"
                + "    // btnLogin.setOnClickListener { login() }\n"
                + "  }\n"
                + "}\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "A.kt");
        assertTrue("commented handler must be ignored", entries.isEmpty());
    }

    @Test
    public void capturesLambdaHandlerTarget() {
        // setOnClickListener(v -> cameraTask()) のラムダ呼び出し先メソッドをハンドラ名に反映する
        String src = "public class MainActivity {\n"
                + "  void setup() {\n"
                + "    findViewById(R.id.btn).setOnClickListener(v -> cameraTask());\n"
                + "  }\n"
                + "}\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "MainActivity.java");
        assertTrue("handler should name the lambda target method", entries.stream()
                .anyMatch(e -> e.actionType == UiActionEntry.ActionType.ON_CLICK
                        && "MainActivity#cameraTask".equals(e.handler)));
    }

    @Test
    public void capturesMethodReferenceHandlerTarget() {
        // setOnClickListener(this::onSave) のメソッド参照先もハンドラ名に反映する
        String src = "public class A {\n"
                + "  void f() { btn.setOnClickListener(this::onSave); }\n"
                + "}\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "A.java");
        assertTrue("handler should name the method reference target", entries.stream()
                .anyMatch(e -> "A#onSave".equals(e.handler)));
    }

    @Test
    public void detectsSetOnLongClickListener() {
        String src = "view.setOnLongClickListener(v -> true);\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "A.java");
        boolean found = entries.stream()
                .anyMatch(e -> e.actionType == UiActionEntry.ActionType.ON_LONG_CLICK);
        assertTrue(found);
    }

    @Test
    public void detectsOnOptionsItemSelected() {
        String src = "public class Frag {\n"
                + "  public boolean onOptionsItemSelected(MenuItem item) {\n"
                + "    return true;\n"
                + "  }\n"
                + "}\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "Frag.java");
        boolean found = entries.stream()
                .anyMatch(e -> e.actionType == UiActionEntry.ActionType.MENU_ITEM);
        assertTrue(found);
    }

    @Test
    public void detectsComposeClick() {
        String src = "Button(\n"
                + "  onClick = { viewModel.submit() }\n"
                + ")\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "Screen.kt");
        boolean found = entries.stream()
                .anyMatch(e -> e.actionType == UiActionEntry.ActionType.COMPOSE_CLICK);
        assertTrue(found);
    }

    @Test
    public void detectsXmlOnClickAttribute() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <Button\n"
                + "        android:id=\"@+id/btn_save\"\n"
                + "        android:onClick=\"onSaveClicked\" />\n"
                + "</LinearLayout>\n";
        List<UiActionEntry> entries = scanner.analyzeLayoutXml(xml, "activity_main.xml");
        assertEquals(1, entries.size());
        UiActionEntry e = entries.get(0);
        assertEquals(UiActionEntry.ActionType.XML_ON_CLICK, e.actionType);
        assertEquals("onSaveClicked", e.handler);
        assertEquals("btn_save", e.componentId);
    }

    @Test
    public void normalizesDataBindingOnClickLambda() {
        // android:onClick="@{() -> vm.onSave()}" のデータバインディング式から呼び出し先を抽出する
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "  <Button android:id=\"@+id/save_btn\"\n"
                + "      android:onClick=\"@{() -> vm.onSave()}\" />\n"
                + "</layout>\n";
        List<UiActionEntry> entries = scanner.analyzeLayoutXml(xml, "activity.xml");
        assertEquals(1, entries.size());
        assertEquals("vm.onSave", entries.get(0).handler);
    }

    @Test
    public void normalizesDataBindingOnClickMethodReference() {
        // android:onClick="@{vm::onSave}" のメソッド参照も receiver.method 形式に正規化する
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "  <Button android:onClick=\"@{vm::onSave}\" />\n"
                + "</layout>\n";
        List<UiActionEntry> entries = scanner.analyzeLayoutXml(xml, "a.xml");
        assertEquals(1, entries.size());
        assertEquals("vm.onSave", entries.get(0).handler);
    }

    @Test
    public void emptySourceReturnsEmptyList() {
        List<UiActionEntry> entries = scanner.analyzeSource("", "A.java");
        assertTrue(entries.isEmpty());
    }
}
