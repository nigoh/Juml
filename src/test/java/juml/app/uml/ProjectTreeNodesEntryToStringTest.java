// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidIntentFilter;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.uml.JavaClassInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link ProjectTreeNodes} 内の各ノード値型 {@code toString()} を網羅する
 * (JTree の表示ラベル整形ロジック。純関数で headless でも安全)。
 * 手本: {@link SoongTreeTest} の {@code BpModuleEntry}/{@code BpFileEntry} テスト。
 */
public class ProjectTreeNodesEntryToStringTest {

    // -------------------------------------------------------------------------
    // ClassEntry: JavaClassInfo.Kind ごとのバッジ文字
    // -------------------------------------------------------------------------

    private static JavaClassInfo classInfo(String simpleName, JavaClassInfo.Kind kind) {
        JavaClassInfo ci = new JavaClassInfo();
        ci.setSimpleName(simpleName);
        ci.setKind(kind);
        return ci;
    }

    @Test
    public void classEntry_interface_showsIBadge() {
        ClassEntry e = new ClassEntry(classInfo("Runnable", JavaClassInfo.Kind.INTERFACE));
        assertEquals("[I] Runnable", e.toString());
    }

    @Test
    public void classEntry_enum_showsEBadge() {
        ClassEntry e = new ClassEntry(classInfo("Color", JavaClassInfo.Kind.ENUM));
        assertEquals("[E] Color", e.toString());
    }

    @Test
    public void classEntry_annotation_showsABadge() {
        ClassEntry e = new ClassEntry(classInfo("Override", JavaClassInfo.Kind.ANNOTATION));
        assertEquals("[A] Override", e.toString());
    }

    @Test
    public void classEntry_aidlInterface_showsAidlBadge() {
        ClassEntry e = new ClassEntry(classInfo("IFoo", JavaClassInfo.Kind.AIDL_INTERFACE));
        assertEquals("[AIDL] IFoo", e.toString());
    }

    @Test
    public void classEntry_defaultKind_showsCBadge() {
        // CLASS はもちろん、分岐で個別扱いされていない RECORD もデフォルト "C" に落ちる。
        ClassEntry classKind = new ClassEntry(classInfo("Foo", JavaClassInfo.Kind.CLASS));
        assertEquals("[C] Foo", classKind.toString());

        ClassEntry recordKind = new ClassEntry(classInfo("Point", JavaClassInfo.Kind.RECORD));
        assertEquals("[C] Point", recordKind.toString());
    }

    // -------------------------------------------------------------------------
    // ComponentEntry: launcher / exported / kindBadge
    // -------------------------------------------------------------------------

    private static AndroidIntentFilter launcherFilter() {
        AndroidIntentFilter f = new AndroidIntentFilter();
        f.getActions().add("android.intent.action.MAIN");
        f.getCategories().add("android.intent.category.LAUNCHER");
        return f;
    }

    @Test
    public void componentEntry_activity_showsActivityBadgeAndShortName() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.example.MainActivity");
        ComponentEntry e = new ComponentEntry(info);
        assertEquals("[A] MainActivity", e.toString());
    }

    @Test
    public void componentEntry_service_showsServiceBadge() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.SERVICE, "com.example.SyncService");
        assertEquals("[S] SyncService", new ComponentEntry(info).toString());
    }

    @Test
    public void componentEntry_receiver_showsReceiverBadge() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.RECEIVER, "com.example.BootReceiver");
        assertEquals("[R] BootReceiver", new ComponentEntry(info).toString());
    }

    @Test
    public void componentEntry_provider_showsProviderBadge() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.PROVIDER, "com.example.FileProvider");
        assertEquals("[P] FileProvider", new ComponentEntry(info).toString());
    }

    @Test
    public void componentEntry_launcherActivity_appendsLauncherMarker() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.example.MainActivity");
        info.getIntentFilters().add(launcherFilter());
        String s = new ComponentEntry(info).toString();
        assertTrue("ランチャー Activity には [launcher] が付くはず: " + s,
                s.contains("[launcher]"));
    }

    @Test
    public void componentEntry_exportedTrue_appendsExportedMarker() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.SERVICE, "com.example.RemoteService");
        info.setExported(Boolean.TRUE);
        String s = new ComponentEntry(info).toString();
        assertTrue("exported=true には [exported] が付くはず: " + s, s.contains("[exported]"));
    }

    @Test
    public void componentEntry_exportedFalseOrNull_omitsExportedMarker() {
        AndroidComponentInfo falseInfo = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.SERVICE, "com.example.LocalService");
        falseInfo.setExported(Boolean.FALSE);
        assertTrue("exported=false には [exported] を付けないはず",
                !new ComponentEntry(falseInfo).toString().contains("[exported]"));

        AndroidComponentInfo nullInfo = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.SERVICE, "com.example.UnknownService");
        assertTrue("exported 未設定 (null) には [exported] を付けないはず",
                !new ComponentEntry(nullInfo).toString().contains("[exported]"));
    }

    @Test
    public void componentEntry_unnamedComponent_showsPlaceholder() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.RECEIVER, "");
        assertEquals("[R] (unnamed)", new ComponentEntry(info).toString());
    }

    // -------------------------------------------------------------------------
    // ManifestEntry: sourceSet / packageName の有無
    // -------------------------------------------------------------------------

    @Test
    public void manifestEntry_defaultSourceSetAndNoPackage_showsSourceSetOnly() {
        AndroidManifestInfo info = new AndroidManifestInfo();
        String s = new ManifestEntry(info).toString();
        assertEquals("[manifest] AndroidManifest.xml (main)", s);
    }

    @Test
    public void manifestEntry_customSourceSetAndPackage_showsBoth() {
        AndroidManifestInfo info = new AndroidManifestInfo();
        info.setSourceSet("debug");
        info.setPackageName("com.example.debug");
        String s = new ManifestEntry(info).toString();
        assertEquals("[manifest] AndroidManifest.xml (debug) — com.example.debug", s);
    }
}
