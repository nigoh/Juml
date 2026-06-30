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
 * AndroidNavigationGraphParser のユニットテスト。
 */
public class AndroidNavigationGraphParserTest {

    private static final String NS_ANDROID = "xmlns:android=\"http://schemas.android.com/apk/res/android\"";
    private static final String NS_APP = "xmlns:app=\"http://schemas.android.com/apk/res-auto\"";
    private static final String NS_TOOLS = "xmlns:tools=\"http://schemas.android.com/tools\"";
    private static final String ALL_NS = NS_ANDROID + " " + NS_APP + " " + NS_TOOLS;

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        AndroidNavigationGraphParser.parse(null);
    }

    @Test
    public void testEmpty() {
        List<String> errors = new ArrayList<>();
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(
                "", ErrorListener.collecting(errors));
        assertNotNull(info);
        assertTrue("destinations should be empty", info.getDestinations().isEmpty());
        assertFalse("expected parse error log", errors.isEmpty());
    }

    @Test
    public void testDoctypeBlocked() {
        List<String> errors = new ArrayList<>();
        String xml = "<!DOCTYPE nav [<!ENTITY x \"y\">]>\n"
                + "<navigation " + ALL_NS + " app:startDestination=\"@id/home\"/>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(
                xml, ErrorListener.collecting(errors));
        assertNotNull(info);
        assertFalse("expected doctype block error", errors.isEmpty());
    }


    @Test
    public void testMalformedXml() {
        List<String> errors = new ArrayList<>();
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(
                "<navigation " + ALL_NS + "><fragment></navigation>",
                ErrorListener.collecting(errors));
        assertNotNull(info);
        assertFalse(errors.isEmpty());
    }

    @Test
    public void testMinimalNavigation() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        assertNotNull(info);
        assertEquals("nav_graph", info.getGraphId());
        assertEquals("homeFragment", info.getStartDestination());
        assertTrue(info.getDestinations().isEmpty());
    }

    @Test
    public void testFragmentDestination() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "  <fragment\n"
                + "      android:id=\"@+id/homeFragment\"\n"
                + "      android:name=\"com.example.HomeFragment\"\n"
                + "      android:label=\"Home\"\n"
                + "      tools:layout=\"@layout/fragment_home\" />\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        assertEquals(1, info.getDestinations().size());

        NavigationDestination dest = info.getDestinations().get(0);
        assertEquals(NavigationDestination.Kind.FRAGMENT, dest.getKind());
        assertEquals("@+id/homeFragment", dest.getId());
        assertEquals("homeFragment", dest.getIdRef());
        assertEquals("com.example.HomeFragment", dest.getName());
        assertEquals("Home", dest.getLabel());
        assertEquals("@layout/fragment_home", dest.getToolsLayout());
    }

    @Test
    public void testActivityDestination() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/mainActivity\">\n"
                + "  <activity\n"
                + "      android:id=\"@+id/mainActivity\"\n"
                + "      android:name=\"com.example.MainActivity\"\n"
                + "      android:label=\"Main\" />\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        assertEquals(1, info.getDestinations().size());
        assertEquals(NavigationDestination.Kind.ACTIVITY, info.getDestinations().get(0).getKind());
    }

    @Test
    public void testDialogDestination() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "  <fragment android:id=\"@+id/homeFragment\" android:name=\"com.example.HomeFragment\" />\n"
                + "  <dialog android:id=\"@+id/confirmDialog\" android:name=\"com.example.ConfirmDialog\" android:label=\"Confirm\" />\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        assertEquals(2, info.getDestinations().size());
        assertEquals(NavigationDestination.Kind.DIALOG, info.getDestinations().get(1).getKind());
        assertEquals("Confirm", info.getDestinations().get(1).getLabel());
    }

    @Test
    public void testAction() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "  <fragment android:id=\"@+id/homeFragment\" android:name=\"com.example.HomeFragment\">\n"
                + "    <action android:id=\"@+id/action_home_to_detail\"\n"
                + "        app:destination=\"@id/detailFragment\" />\n"
                + "  </fragment>\n"
                + "  <fragment android:id=\"@+id/detailFragment\" android:name=\"com.example.DetailFragment\" />\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        NavigationDestination home = info.getDestinations().get(0);
        assertEquals(1, home.getActions().size());

        NavigationAction action = home.getActions().get(0);
        assertEquals("action_home_to_detail", action.getIdRef());
        assertEquals("detailFragment", action.getDestination());
        assertFalse(action.isGlobal());
    }

    @Test
    public void testGlobalAction() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "  <action android:id=\"@+id/action_global_home\"\n"
                + "      app:destination=\"@id/homeFragment\"\n"
                + "      app:popUpTo=\"@id/homeFragment\"\n"
                + "      app:popUpToInclusive=\"true\" />\n"
                + "  <fragment android:id=\"@+id/homeFragment\" android:name=\"com.example.HomeFragment\" />\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        assertEquals(1, info.getGlobalActions().size());

        NavigationAction ga = info.getGlobalActions().get(0);
        assertEquals("action_global_home", ga.getIdRef());
        assertEquals("homeFragment", ga.getDestination());
        assertEquals("homeFragment", ga.getPopUpTo());
        assertTrue(ga.isPopUpToInclusive());
        assertTrue(ga.isGlobal());
    }

    @Test
    public void testArgument() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "  <fragment android:id=\"@+id/homeFragment\" android:name=\"com.example.HomeFragment\">\n"
                + "    <argument android:name=\"userId\" app:argType=\"integer\" android:defaultValue=\"0\" />\n"
                + "    <argument android:name=\"token\" app:argType=\"string\" app:nullable=\"true\" />\n"
                + "  </fragment>\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        NavigationDestination dest = info.getDestinations().get(0);
        assertEquals(2, dest.getArguments().size());

        NavigationArgument userId = dest.getArguments().get(0);
        assertEquals("userId", userId.getName());
        assertEquals("integer", userId.getArgType());
        assertEquals("0", userId.getDefaultValue());
        assertFalse(userId.isNullable());

        NavigationArgument token = dest.getArguments().get(1);
        assertEquals("token", token.getName());
        assertEquals("string", token.getArgType());
        assertTrue(token.isNullable());
    }

    @Test
    public void testImplicitDeepLinkWithoutUri() {
        // uri を持たず action/mimeType だけの暗黙 deeplink も取りこぼさず記述子化する。
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "  <fragment android:id=\"@+id/homeFragment\" android:name=\"com.example.HomeFragment\">\n"
                + "    <deepLink app:action=\"android.intent.action.VIEW\" app:mimeType=\"image/*\" />\n"
                + "    <deepLink app:action=\"android.intent.action.SEND\" />\n"
                + "  </fragment>\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        NavigationDestination dest = info.getDestinations().get(0);
        assertEquals(2, dest.getDeepLinks().size());
        assertEquals("android.intent.action.VIEW (image/*)", dest.getDeepLinks().get(0));
        assertEquals("android.intent.action.SEND", dest.getDeepLinks().get(1));
    }

    @Test
    public void testDeepLink() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "  <fragment android:id=\"@+id/homeFragment\" android:name=\"com.example.HomeFragment\">\n"
                + "    <deepLink app:uri=\"https://www.example.com/home\" />\n"
                + "    <deepLink app:uri=\"example://home\" />\n"
                + "  </fragment>\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        NavigationDestination dest = info.getDestinations().get(0);
        assertEquals(2, dest.getDeepLinks().size());
        assertEquals("https://www.example.com/home", dest.getDeepLinks().get(0));
        assertEquals("example://home", dest.getDeepLinks().get(1));
    }

    @Test
    public void testNestedNavigation() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "  <fragment android:id=\"@+id/homeFragment\" android:name=\"com.example.HomeFragment\" />\n"
                + "  <navigation android:id=\"@+id/authGraph\" android:label=\"Auth\"\n"
                + "      app:startDestination=\"@id/loginFragment\">\n"
                + "    <fragment android:id=\"@+id/loginFragment\" android:name=\"com.example.LoginFragment\" />\n"
                + "  </navigation>\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        assertEquals(2, info.getDestinations().size());

        NavigationDestination nested = info.getDestinations().get(1);
        assertEquals(NavigationDestination.Kind.NAVIGATION, nested.getKind());
        assertEquals("authGraph", nested.getIdRef());
        assertEquals("loginFragment", nested.getStartDestination());
    }

    @Test
    public void testInclude() {
        String xml = "<navigation " + ALL_NS + "\n"
                + "  android:id=\"@+id/nav_graph\"\n"
                + "  app:startDestination=\"@id/homeFragment\">\n"
                + "  <fragment android:id=\"@+id/homeFragment\" android:name=\"com.example.HomeFragment\" />\n"
                + "  <include app:graph=\"@navigation/secondary_nav\" />\n"
                + "</navigation>";
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        assertEquals(2, info.getDestinations().size());

        NavigationDestination include = info.getDestinations().get(1);
        assertEquals(NavigationDestination.Kind.INCLUDE, include.getKind());
        assertEquals("secondary_nav", include.getIdRef());
    }

    @Test
    public void testSimpleNavSample() throws IOException {
        String xml = readSample("simple_nav.xml");
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        assertNotNull(info);
        assertEquals("nav_graph", info.getGraphId());
        assertEquals("homeFragment", info.getStartDestination());
        assertEquals(2, info.getDestinations().size());

        NavigationDestination home = info.getDestinations().get(0);
        assertEquals("homeFragment", home.getIdRef());
        assertEquals(1, home.getActions().size());
        assertEquals("detailFragment", home.getActions().get(0).getDestination());
    }

    @Test
    public void testFullNavSample() throws IOException {
        String xml = readSample("full_nav.xml");
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        assertNotNull(info);
        assertEquals("full_nav", info.getGraphId());
        assertEquals("homeFragment", info.getStartDestination());
        assertEquals(1, info.getGlobalActions().size());

        long fragCount = info.getDestinations().stream()
                .filter(d -> d.getKind() == NavigationDestination.Kind.FRAGMENT).count();
        long actCount = info.getDestinations().stream()
                .filter(d -> d.getKind() == NavigationDestination.Kind.ACTIVITY).count();
        long dlgCount = info.getDestinations().stream()
                .filter(d -> d.getKind() == NavigationDestination.Kind.DIALOG).count();
        long navCount = info.getDestinations().stream()
                .filter(d -> d.getKind() == NavigationDestination.Kind.NAVIGATION).count();
        long incCount = info.getDestinations().stream()
                .filter(d -> d.getKind() == NavigationDestination.Kind.INCLUDE).count();

        assertEquals(2, fragCount);
        assertEquals(1, actCount);
        assertEquals(1, dlgCount);
        assertEquals(1, navCount);
        assertEquals(1, incCount);
    }

    @Test
    public void testNormalizeRef() {
        assertNull(AndroidNavigationGraphParser.normalizeRef(null));
        assertNull(AndroidNavigationGraphParser.normalizeRef(""));
        assertEquals("foo", AndroidNavigationGraphParser.normalizeRef("@+id/foo"));
        assertEquals("foo", AndroidNavigationGraphParser.normalizeRef("@id/foo"));
        assertEquals("foo", AndroidNavigationGraphParser.normalizeRef("@navigation/foo"));
        assertEquals("foo", AndroidNavigationGraphParser.normalizeRef("foo"));
    }

    @Test
    public void testGetKey() {
        AndroidNavigationGraphInfo info = new AndroidNavigationGraphInfo();
        info.setModuleName("app");
        info.setSourceSet("main");
        info.setFileName("nav_graph.xml");
        assertEquals("app::main::nav_graph.xml", info.getKey());
    }

    @Test
    public void testDisplayName() {
        NavigationDestination dest = new NavigationDestination();
        dest.setLabel("Home Screen");
        assertEquals("Home Screen", dest.displayName());

        dest.setLabel(null);
        dest.setName("com.example.HomeFragment");
        assertEquals("HomeFragment", dest.displayName());

        dest.setName(null);
        dest.setIdRef("homeFragment");
        assertEquals("homeFragment", dest.displayName());
    }

    private static String readSample(String name) throws IOException {
        try (InputStream in = AndroidNavigationGraphParserTest.class
                .getResourceAsStream("/samples/navigation/" + name)) {
            assertNotNull("sample resource not found: " + name, in);
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
