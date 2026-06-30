// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import juml.core.formats.uml.DiagramStyle;
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.PlantUmlSequenceDiagram;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test plan: Escape キー + デフォルトボタンの全ダイアログ統一 (H-1 + H-2) の自動検証。
 *
 * <p>各ダイアログについて:
 * <ol>
 *   <li>Escape キーがルートペインに登録されているか</li>
 *   <li>{@code getRootPane().setDefaultButton(ok)} が設定されているか</li>
 * </ol>
 * を確認する。SequenceParticipantFilterDialog はボタン順 (OK → Cancel) も検証する。</p>
 *
 * <p>ヘッドレス環境では JDialog の生成に display が必要なため {@code Assume} でスキップ。
 * {@code xvfb-run -a ./gradlew test --tests "*.DialogKeyboardTest"} で実行すること。</p>
 */
public class DialogKeyboardTest {

    private final List<JDialog> toDispose = new ArrayList<>();

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境ではスキップ (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
    }

    @After
    public void cleanup() {
        GuiActionRunner.execute(() -> {
            for (JDialog dlg : toDispose) {
                if (dlg.isDisplayable()) {
                    dlg.dispose();
                }
            }
        });
        toDispose.clear();
    }

    // ---- DiagramScopeDialog ----

    @Test
    public void diagramScope_defaultButton_isOk() {
        DiagramScopeDialog dlg = track(GuiActionRunner.execute(() ->
                new DiagramScopeDialog(null,
                        Collections.emptyList(), Collections.emptyList(), null)));
        JButton btn = GuiActionRunner.execute(() -> dlg.getRootPane().getDefaultButton());
        assertNotNull("DiagramScopeDialog: default button should be set", btn);
        assertEquals("DiagramScopeDialog: default button should be OK", "OK", btn.getText());
    }

    @Test
    public void diagramScope_escape_isRegistered() {
        DiagramScopeDialog dlg = track(GuiActionRunner.execute(() ->
                new DiagramScopeDialog(null,
                        Collections.emptyList(), Collections.emptyList(), null)));
        assertEscapeRegistered("DiagramScopeDialog", dlg);
    }

    // ---- StyleSettingsDialog ----

    @Test
    public void styleSettings_defaultButton_isOk() throws Exception {
        StyleSettingsDialog dlg = createStyleSettings();
        JButton btn = GuiActionRunner.execute(() -> dlg.getRootPane().getDefaultButton());
        assertNotNull("StyleSettingsDialog: default button should be set", btn);
        assertEquals("StyleSettingsDialog: default button should be OK", "OK", btn.getText());
    }

    @Test
    public void styleSettings_escape_isRegistered() throws Exception {
        StyleSettingsDialog dlg = createStyleSettings();
        assertEscapeRegistered("StyleSettingsDialog", dlg);
    }

    // ---- SequenceParticipantFilterDialog ----

    @Test
    public void sequenceParticipantFilter_defaultButton_isOk() throws Exception {
        SequenceParticipantFilterDialog dlg = createParticipantFilter();
        JButton btn = GuiActionRunner.execute(() -> dlg.getRootPane().getDefaultButton());
        assertNotNull("SequenceParticipantFilterDialog: default button should be set", btn);
        assertEquals("SequenceParticipantFilterDialog: default button should be OK",
                "OK", btn.getText());
    }

    @Test
    public void sequenceParticipantFilter_escape_isRegistered() throws Exception {
        SequenceParticipantFilterDialog dlg = createParticipantFilter();
        assertEscapeRegistered("SequenceParticipantFilterDialog", dlg);
    }

    @Test
    public void sequenceParticipantFilter_buttonOrder_okBeforeCancel() throws Exception {
        SequenceParticipantFilterDialog dlg = createParticipantFilter();
        List<String> labels = GuiActionRunner.execute(() -> collectButtonLabels(dlg));
        int okIdx = labels.indexOf("OK");
        int cancelIdx = labels.indexOf("Cancel");
        assertTrue("SequenceParticipantFilterDialog: OK button should exist", okIdx >= 0);
        assertTrue("SequenceParticipantFilterDialog: Cancel button should exist", cancelIdx >= 0);
        assertTrue("SequenceParticipantFilterDialog: OK should precede Cancel",
                okIdx < cancelIdx);
    }

    // ---- EntitySearchDialog ----

    @Test
    public void entitySearch_defaultButton_isOk() {
        EntitySearchDialog dlg = track(GuiActionRunner.execute(() ->
                new EntitySearchDialog(null, Collections.emptyList())));
        JButton btn = GuiActionRunner.execute(() -> dlg.getRootPane().getDefaultButton());
        assertNotNull("EntitySearchDialog: default button should be set", btn);
        assertEquals("EntitySearchDialog: default button should be OK", "OK", btn.getText());
    }

    @Test
    public void entitySearch_escape_isRegistered() {
        EntitySearchDialog dlg = track(GuiActionRunner.execute(() ->
                new EntitySearchDialog(null, Collections.emptyList())));
        assertEscapeRegistered("EntitySearchDialog", dlg);
    }

    // ---- SequenceEntryDialog ----

    @Test
    public void sequenceEntry_defaultButton_isOk() {
        SequenceEntryDialog dlg = track(GuiActionRunner.execute(() ->
                new SequenceEntryDialog(null, Collections.emptyList())));
        JButton btn = GuiActionRunner.execute(() -> dlg.getRootPane().getDefaultButton());
        assertNotNull("SequenceEntryDialog: default button should be set", btn);
        assertEquals("SequenceEntryDialog: default button should be OK", "OK", btn.getText());
    }

    @Test
    public void sequenceEntry_escape_isRegistered() {
        SequenceEntryDialog dlg = track(GuiActionRunner.execute(() ->
                new SequenceEntryDialog(null, Collections.emptyList())));
        assertEscapeRegistered("SequenceEntryDialog", dlg);
    }

    // ---- LayoutFileChooserDialog ----

    @Test
    public void layoutChooser_defaultButton_isOk() {
        LayoutFileChooserDialog dlg = track(GuiActionRunner.execute(() ->
                new LayoutFileChooserDialog(null, Collections.emptyList())));
        JButton btn = GuiActionRunner.execute(() -> dlg.getRootPane().getDefaultButton());
        assertNotNull("LayoutFileChooserDialog: default button should be set", btn);
        assertEquals("LayoutFileChooserDialog: default button should be OK", "OK", btn.getText());
    }

    @Test
    public void layoutChooser_escape_isRegistered() {
        LayoutFileChooserDialog dlg = track(GuiActionRunner.execute(() ->
                new LayoutFileChooserDialog(null, Collections.emptyList())));
        assertEscapeRegistered("LayoutFileChooserDialog", dlg);
    }

    // ---- PreferencesDialog ----

    @Test
    public void preferences_defaultButton_isOk() throws Exception {
        PreferencesDialog dlg = createPreferences();
        JButton btn = GuiActionRunner.execute(() -> dlg.getRootPane().getDefaultButton());
        assertNotNull("PreferencesDialog: default button should be set", btn);
        assertEquals("PreferencesDialog: default button should be OK", "OK", btn.getText());
    }

    @Test
    public void preferences_escape_isRegistered() throws Exception {
        PreferencesDialog dlg = createPreferences();
        assertEscapeRegistered("PreferencesDialog", dlg);
    }

    // ---- helpers ----

    private <T extends JDialog> T track(T dlg) {
        toDispose.add(dlg);
        return dlg;
    }

    private void assertEscapeRegistered(String name, JDialog dlg) {
        KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        Object actionKey = GuiActionRunner.execute(() ->
                dlg.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(escape));
        assertNotNull(name + ": Escape key should be registered in root pane input map", actionKey);
    }

    private static List<String> collectButtonLabels(Container root) {
        List<String> labels = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JButton) {
                labels.add(((JButton) c).getText());
            }
            if (c instanceof Container) {
                labels.addAll(collectButtonLabels((Container) c));
            }
        }
        return labels;
    }

    /** StyleSettingsDialog のプライベートコンストラクタをリフレクションで呼ぶ。 */
    private StyleSettingsDialog createStyleSettings() throws Exception {
        Constructor<StyleSettingsDialog> ctor = StyleSettingsDialog.class.getDeclaredConstructor(
                Window.class, DiagramStyle.class, boolean.class,
                PlantUmlClassDiagram.CommentStyle.class,
                PlantUmlSequenceDiagram.CommentPlacement.class,
                boolean.class, StyleSettingsDialog.ClassDiagramPrefs.class, int.class);
        ctor.setAccessible(true);
        StyleSettingsDialog dlg = GuiActionRunner.execute(() -> {
            try {
                return ctor.newInstance(
                        null, DiagramStyle.defaults(), true,
                        PlantUmlClassDiagram.CommentStyle.INLINE,
                        PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE,
                        true, StyleSettingsDialog.ClassDiagramPrefs.defaults(), 3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return track(dlg);
    }

    /** PreferencesDialog のプライベートコンストラクタをリフレクションで呼ぶ。 */
    private PreferencesDialog createPreferences() throws Exception {
        Constructor<PreferencesDialog> ctor = PreferencesDialog.class.getDeclaredConstructor(
                java.awt.Frame.class, String.class, boolean.class, String.class, String.class);
        ctor.setAccessible(true);
        PreferencesDialog dlg = GuiActionRunner.execute(() -> {
            try {
                return ctor.newInstance(null, "SYSTEM", true, "ja", "AUTO");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return track(dlg);
    }

    /** SequenceParticipantFilterDialog のプライベートコンストラクタをリフレクションで呼ぶ。 */
    private SequenceParticipantFilterDialog createParticipantFilter() throws Exception {
        Constructor<SequenceParticipantFilterDialog> ctor =
                SequenceParticipantFilterDialog.class.getDeclaredConstructor(
                        Window.class, String.class, Set.class, Set.class);
        ctor.setAccessible(true);
        Set<String> participants = new LinkedHashSet<>(Arrays.asList("A", "B", "C"));
        SequenceParticipantFilterDialog dlg = GuiActionRunner.execute(() -> {
            try {
                return ctor.newInstance(null, "Foo.bar", participants, Collections.emptySet());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return track(dlg);
    }
}
