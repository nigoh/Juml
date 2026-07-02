// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlSourcePanel} のスニペット挿入 (編集モード限定) を検証する GUI テスト。
 */
public class PumlSourcePanelSnippetTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Test
    public void insertSnippet_inEditableMode_insertsAtCaret() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText("@startuml\n@enduml\n");
            panel.insertSnippet("class Foo {\n}\n");
        });
        String text = GuiActionRunner.execute(panel::getText);
        assertTrue("スニペットが本文へ挿入されるはず: " + text, text.contains("class Foo {"));
    }

    @Test
    public void insertSnippet_whenReadOnly_isIgnored() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            // setEditable を呼ばない = リードオンリー。
            panel.setText("@startuml\n@enduml\n");
            panel.insertSnippet("class Foo {\n}\n");
        });
        String text = GuiActionRunner.execute(panel::getText);
        assertFalse("リードオンリーではスニペットを挿入しない", text.contains("class Foo {"));
    }
}
