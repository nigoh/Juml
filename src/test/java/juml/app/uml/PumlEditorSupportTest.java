// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlEditorSupport} のファイル判定・拡張子補完・UTF-8 読み書きを検証する。
 * (ダイアログ系メソッドは対象外。headless で動く)
 */
public class PumlEditorSupportTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void isPumlFile_recognizesKnownExtensions() {
        assertTrue(PumlEditorSupport.isPumlFile(new File("a.puml")));
        assertTrue(PumlEditorSupport.isPumlFile(new File("a.plantuml")));
        assertTrue(PumlEditorSupport.isPumlFile(new File("a.pu")));
        assertTrue("大文字拡張子も受け付けるべき",
                PumlEditorSupport.isPumlFile(new File("A.PUML")));
        assertFalse(PumlEditorSupport.isPumlFile(new File("a.java")));
        assertFalse(PumlEditorSupport.isPumlFile(new File("puml")));
        assertFalse(PumlEditorSupport.isPumlFile(null));
    }

    @Test
    public void ensurePumlExtension_appendsOnlyWhenMissing() {
        assertEquals("diagram.puml", PumlEditorSupport.ensurePumlExtension("diagram"));
        assertEquals("diagram.puml", PumlEditorSupport.ensurePumlExtension("diagram.puml"));
        assertEquals("diagram.plantuml",
                PumlEditorSupport.ensurePumlExtension("diagram.plantuml"));
    }

    @Test
    public void writeAndRead_roundTripsUtf8Text() throws Exception {
        // 親ディレクトリも自動生成される。ファイル名は CI のロケール (POSIX) でも
        // 扱えるよう ASCII とし、UTF-8 の検証は本文側で行う。
        File f = new File(tmp.getRoot(), "sub/dir/diagram.puml");
        String text = "@startuml\nclass 日本語クラス\n@enduml\n";
        PumlEditorSupport.write(f, text);
        assertTrue(f.isFile());
        assertEquals(text, PumlEditorSupport.read(f));
    }

    @Test
    public void write_nullText_writesEmptyFile() throws Exception {
        File f = new File(tmp.getRoot(), "empty.puml");
        PumlEditorSupport.write(f, null);
        assertEquals("", PumlEditorSupport.read(f));
    }
}
