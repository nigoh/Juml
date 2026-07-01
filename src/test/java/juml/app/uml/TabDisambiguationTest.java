// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

import static org.junit.Assert.assertEquals;

/**
 * 同名タブの曖昧さ解消ロジック検証: {@link TreeNodeOpenRequest#disambiguator()} と
 * {@link DiagramTabHeader#updateTitle}。
 */
public class TabDisambiguationTest {

    private static JavaClassInfo cls(String pkg, String simple) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(simple);
        return c;
    }

    @Test
    public void classDisambiguatorIsPackage() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(cls("com.example.foo", "Bar"));
        assertEquals("com.example.foo", req.disambiguator());
    }

    @Test
    public void packageAndModuleDisambiguators() {
        assertEquals("com.a.b", TreeNodeOpenRequest.pkg("com.a.b").disambiguator());
        assertEquals("core", TreeNodeOpenRequest.module("core").disambiguator());
    }

    @Test
    public void noPackageYieldsEmptyDisambiguator() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(cls("", "Top"));
        assertEquals("", req.disambiguator());
    }

    @Test
    public void updateTitleReplacesHeaderLabel() {
        Component header = DiagramTabHeader.build("Bar", TreeNodeIcon.CLASS, "tip",
                () -> { }, e -> { }, () -> { }, null);
        DiagramTabHeader.updateTitle(header, "Bar  ·  com.example.foo");
        // ヘッダ内のタイトル JLabel が差し替わっている
        String found = null;
        for (Component c : ((JPanel) header).getComponents()) {
            if (c instanceof JLabel && "Bar  ·  com.example.foo".equals(((JLabel) c).getText())) {
                found = ((JLabel) c).getText();
            }
        }
        assertEquals("Bar  ·  com.example.foo", found);
    }
}
