// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import juml.util.ErrorListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link DoxygenXmlParser} のユニットテスト。
 *
 * <p>この環境には doxygen バイナリが無いため実起動は検証できないが、doxygen が出力する
 * XML の形に合わせたフィクスチャをパースして、ツリー表示用モデルが正しく組み立つことを確認する。</p>
 */
public class DoxygenXmlParserTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private File writeXmlDir(String indexXml, String... compoundFiles) throws IOException {
        File xmlDir = tmp.newFolder("xml");
        Files.writeString(new File(xmlDir, "index.xml").toPath(), indexXml, StandardCharsets.UTF_8);
        for (int i = 0; i + 1 < compoundFiles.length; i += 2) {
            Files.writeString(new File(xmlDir, compoundFiles[i]).toPath(),
                    compoundFiles[i + 1], StandardCharsets.UTF_8);
        }
        return xmlDir;
    }

    @Test
    public void parsesCompoundWithMembersAndBrief() throws IOException {
        String index =
                "<doxygenindex version=\"1.9.1\">"
                + "  <compound refid=\"classFoo\" kind=\"class\"><name>com.example.Foo</name>"
                + "    <member refid=\"classFoo_1aaa\" kind=\"function\"><name>doWork</name></member>"
                + "  </compound>"
                + "  <compound refid=\"namespacecom\" kind=\"namespace\"><name>com</name></compound>"
                + "</doxygenindex>";
        String fooXml =
                "<doxygen version=\"1.9.1\">"
                + "  <compounddef id=\"classFoo\" kind=\"class\">"
                + "    <compoundname>com.example.Foo</compoundname>"
                + "    <briefdescription><para>A demo class.</para></briefdescription>"
                + "    <detaileddescription><para>Detailed class prose.</para></detaileddescription>"
                + "    <sectiondef kind=\"public-func\">"
                + "      <memberdef kind=\"function\" id=\"classFoo_1aaa\">"
                + "        <type>void</type>"
                + "        <name>doWork</name>"
                + "        <argsstring>(int n)</argsstring>"
                + "        <briefdescription><para>Does work.</para></briefdescription>"
                + "        <detaileddescription><para>Runs the work."
                + "          <parameterlist kind=\"param\">"
                + "            <parameteritem>"
                + "              <parameternamelist><parametername>n</parametername></parameternamelist>"
                + "              <parameterdescription><para>the count</para></parameterdescription>"
                + "            </parameteritem>"
                + "          </parameterlist>"
                + "          <simplesect kind=\"return\"><para>nothing useful</para></simplesect>"
                + "          <parameterlist kind=\"exception\">"
                + "            <parameteritem>"
                + "              <parameternamelist><parametername>IOException</parametername></parameternamelist>"
                + "              <parameterdescription><para>on failure</para></parameterdescription>"
                + "            </parameteritem>"
                + "          </parameterlist>"
                + "        </para></detaileddescription>"
                + "      </memberdef>"
                + "    </sectiondef>"
                + "  </compounddef>"
                + "</doxygen>";
        File xmlDir = writeXmlDir(index, "classFoo.xml", fooXml);

        DoxModel model = DoxygenXmlParser.parse(xmlDir, ErrorListener.silent());

        // namespace は除外され、class のみ残る。
        List<DoxCompound> compounds = model.getCompounds();
        assertEquals(1, compounds.size());
        DoxCompound foo = compounds.get(0);
        assertEquals("class", foo.getKind());
        assertEquals("com.example.Foo", foo.getName());
        assertEquals("Foo", foo.simpleName());
        assertEquals("A demo class.", foo.getBrief());
        assertEquals("Detailed class prose.", foo.getDetailed());

        assertEquals(1, foo.getMembers().size());
        DoxMember m = foo.getMembers().get(0);
        assertEquals("function", m.getKind());
        assertEquals("doWork", m.getName());
        assertEquals("(int n)", m.getArgs());
        assertEquals("Does work.", m.getBrief());
        assertEquals("doWork(int n)", m.displayLabel());

        // R2: 型・detailed・@param・@return・@throws を抽出する。
        assertEquals("void", m.getType());
        assertEquals("Runs the work.", m.getDetailed());
        assertEquals(1, m.getParams().size());
        assertEquals("n", m.getParams().get(0).getName());
        assertEquals("the count", m.getParams().get(0).getDescription());
        assertEquals("nothing useful", m.getReturns());
        assertEquals(1, m.getThrows().size());
        assertEquals("IOException", m.getThrows().get(0).getName());
        assertEquals("on failure", m.getThrows().get(0).getDescription());
    }

    @Test
    public void collectsXrefItemsAcrossCompoundAndMembers() throws IOException {
        String index =
                "<doxygenindex version=\"1.9.1\">"
                + "  <compound refid=\"classBaz\" kind=\"class\"><name>Baz</name></compound>"
                + "</doxygenindex>";
        String bazXml =
                "<doxygen version=\"1.9.1\">"
                + "  <compounddef id=\"classBaz\" kind=\"class\">"
                + "    <compoundname>Baz</compoundname>"
                + "    <detaileddescription><para>Class prose."
                + "      <xrefsect id=\"deprecated_1\"><xreftitle>Deprecated</xreftitle>"
                + "        <xrefdescription><para>use Qux instead</para></xrefdescription></xrefsect>"
                + "    </para></detaileddescription>"
                + "    <sectiondef kind=\"public-func\">"
                + "      <memberdef kind=\"function\" id=\"classBaz_1m\">"
                + "        <name>run</name><argsstring>()</argsstring>"
                + "        <detaileddescription><para>Runs."
                + "          <xrefsect id=\"todo_1\"><xreftitle>Todo</xreftitle>"
                + "            <xrefdescription><para>handle nulls</para></xrefdescription></xrefsect>"
                + "          <xrefsect id=\"bug_1\"><xreftitle>Bug</xreftitle>"
                + "            <xrefdescription><para>off by one</para></xrefdescription></xrefsect>"
                + "        </para></detaileddescription>"
                + "      </memberdef>"
                + "    </sectiondef>"
                + "  </compounddef>"
                + "</doxygen>";
        File xmlDir = writeXmlDir(index, "classBaz.xml", bazXml);

        DoxModel model = DoxygenXmlParser.parse(xmlDir, ErrorListener.silent());

        java.util.List<DoxXrefItem> items = model.getXrefItems();
        assertEquals(3, items.size());
        // クラス由来 (Deprecated) は location がクラス名、メンバー由来は Class.member。
        DoxXrefItem deprecated = items.stream()
                .filter(x -> x.getKind() == DoxXrefItem.Kind.DEPRECATED).findFirst().orElseThrow();
        assertEquals("Baz", deprecated.getLocation());
        assertEquals("use Qux instead", deprecated.getDescription());
        DoxXrefItem todo = items.stream()
                .filter(x -> x.getKind() == DoxXrefItem.Kind.TODO).findFirst().orElseThrow();
        assertEquals("Baz.run", todo.getLocation());
        assertEquals("handle nulls", todo.getDescription());
        assertTrue(items.stream().anyMatch(x -> x.getKind() == DoxXrefItem.Kind.BUG));

        // xref のテキストは散文に混ざらない。
        assertEquals("Class prose.", model.getCompounds().get(0).getDetailed());
        assertEquals("Runs.", model.getCompounds().get(0).getMembers().get(0).getDetailed());
    }

    @Test
    public void parsesInheritanceAndReferenceMeta() throws IOException {
        String index =
                "<doxygenindex version=\"1.9.1\">"
                + "  <compound refid=\"classSub\" kind=\"class\"><name>Sub</name></compound>"
                + "</doxygenindex>";
        String subXml =
                "<doxygen version=\"1.9.1\">"
                + "  <compounddef id=\"classSub\" kind=\"class\">"
                + "    <compoundname>Sub</compoundname>"
                + "    <basecompoundref prot=\"public\">Base</basecompoundref>"
                + "    <basecompoundref prot=\"public\">Iface</basecompoundref>"
                + "    <derivedcompoundref prot=\"public\">Leaf</derivedcompoundref>"
                + "    <sectiondef kind=\"public-func\">"
                + "      <memberdef kind=\"function\" id=\"classSub_1m\">"
                + "        <name>go</name><argsstring>()</argsstring>"
                + "        <references refid=\"a\">helper</references>"
                + "        <referencedby refid=\"b\">caller</referencedby>"
                + "        <referencedby refid=\"b\">caller</referencedby>"
                + "      </memberdef>"
                + "    </sectiondef>"
                + "  </compounddef>"
                + "</doxygen>";
        File xmlDir = writeXmlDir(index, "classSub.xml", subXml);

        DoxModel model = DoxygenXmlParser.parse(xmlDir, ErrorListener.silent());
        DoxCompound sub = model.getCompounds().get(0);
        assertEquals(List.of("Base", "Iface"), sub.getBaseTypes());
        assertEquals(List.of("Leaf"), sub.getDerivedTypes());

        DoxMember go = sub.getMembers().get(0);
        assertEquals(List.of("helper"), go.getReferences());
        // referencedby の重複は除去される。
        assertEquals(List.of("caller"), go.getReferencedBy());
    }

    @Test
    public void parsesGroupHierarchy() throws IOException {
        String index =
                "<doxygenindex version=\"1.9.1\">"
                + "  <compound refid=\"group__parent\" kind=\"group\"><name>parent</name></compound>"
                + "  <compound refid=\"group__child\" kind=\"group\"><name>child</name></compound>"
                + "</doxygenindex>";
        String parentXml =
                "<doxygen version=\"1.9.1\">"
                + "  <compounddef id=\"group__parent\" kind=\"group\">"
                + "    <compoundname>parent</compoundname><title>Parent Group</title>"
                + "    <innerclass refid=\"classA\">com.example.A</innerclass>"
                + "    <innergroup refid=\"group__child\">child</innergroup>"
                + "  </compounddef>"
                + "</doxygen>";
        String childXml =
                "<doxygen version=\"1.9.1\">"
                + "  <compounddef id=\"group__child\" kind=\"group\">"
                + "    <compoundname>child</compoundname><title>Child Group</title>"
                + "    <innerclass refid=\"classB\">com.example.B</innerclass>"
                + "  </compounddef>"
                + "</doxygen>";
        File xmlDir = writeXmlDir(index,
                "group__parent.xml", parentXml, "group__child.xml", childXml);

        DoxModel model = DoxygenXmlParser.parse(xmlDir, ErrorListener.silent());
        assertEquals(2, model.getGroups().size());
        DoxGroup parent = model.getGroups().stream()
                .filter(g -> g.getId().equals("parent")).findFirst().orElseThrow();
        assertEquals("Parent Group", parent.getTitle());
        assertEquals(List.of("com.example.A"), parent.getInnerClassNames());
        assertEquals(List.of("child"), parent.getInnerGroupIds());
    }

    @Test
    public void fallsBackToIndexWhenCompoundFileMissing() throws IOException {
        String index =
                "<doxygenindex version=\"1.9.1\">"
                + "  <compound refid=\"classBar\" kind=\"class\"><name>Bar</name>"
                + "    <member refid=\"classBar_1bbb\" kind=\"variable\"><name>count</name></member>"
                + "  </compound>"
                + "</doxygenindex>";
        File xmlDir = writeXmlDir(index); // classBar.xml は意図的に書かない

        DoxModel model = DoxygenXmlParser.parse(xmlDir, ErrorListener.silent());

        assertEquals(1, model.getCompounds().size());
        DoxCompound bar = model.getCompounds().get(0);
        assertEquals("Bar", bar.getName());
        assertEquals("", bar.getBrief());
        assertEquals(1, bar.getMembers().size());
        assertEquals("count", bar.getMembers().get(0).getName());
    }

    @Test
    public void missingIndexThrows() throws IOException {
        File empty = tmp.newFolder("empty");
        assertThrows(IOException.class, () -> DoxygenXmlParser.parse(empty, ErrorListener.silent()));
    }

    @Test
    public void doxyfileContainsXmlAndJavaSettings() {
        String doxyfile = DoxygenRunner.buildDoxyfile(tmp.getRoot(), tmp.getRoot());
        assertTrue(doxyfile.contains("GENERATE_XML = YES"));
        assertTrue(doxyfile.contains("GENERATE_HTML = NO"));
        assertTrue(doxyfile.contains("OPTIMIZE_OUTPUT_JAVA = YES"));
        assertTrue(doxyfile.contains("FILE_PATTERNS = *.java"));
        assertTrue(doxyfile.contains(tmp.getRoot().getAbsolutePath()));
    }
}
