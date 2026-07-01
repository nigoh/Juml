// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidPermissionInfo;
import juml.core.formats.uml.JavaClassInfo;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * {@link ProjectTreeCellRenderer} のアイコン割り当てを headless で検証する。
 *
 * <p>各 Entry 型を {@link DefaultMutableTreeNode} に詰め
 * {@link ProjectTreeCellRenderer#getTreeCellRendererComponent} を呼び出し、
 * 返る {@link JLabel} の {@link JLabel#getIcon()} が期待の {@link TreeNodeIcon}
 * と一致することを確認する。</p>
 *
 * <p>{@link JTree} の生成は {@link org.assertj.swing.edt.GuiActionRunner} で包む。
 * ヘッドレス環境では {@link org.junit.Assume} でスキップする。</p>
 */
public class ProjectTreeCellRendererTest {

    private JTree tree;
    private ProjectTreeCellRenderer renderer;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        GuiActionRunner.execute(() -> {
            tree = new JTree();
            renderer = new ProjectTreeCellRenderer();
        });
    }

    // -------------------------------------------------------------------------
    // 構造ノード
    // -------------------------------------------------------------------------

    @Test
    public void moduleEntry_rendersModuleIcon() {
        assertIconEquals("ModuleEntry は MODULE アイコンを割り当てるはず",
                TreeNodeIcon.MODULE, new ModuleEntry("core"));
    }

    @Test
    public void packageEntry_rendersPackageIcon() {
        assertIconEquals("PackageEntry は PACKAGE アイコンを割り当てるはず",
                TreeNodeIcon.PACKAGE,
                new PackageEntry("com.example", 3, java.util.Collections.emptyList()));
    }

    // -------------------------------------------------------------------------
    // Java 型ノード
    // -------------------------------------------------------------------------

    @Test
    public void classEntry_class_rendersClassIcon() {
        JavaClassInfo info = makeClass("com.example.Foo", JavaClassInfo.Kind.CLASS);
        assertIconEquals("CLASS 種別は CLASS アイコンになるはず",
                TreeNodeIcon.CLASS, new ClassEntry(info));
    }

    @Test
    public void classEntry_interface_rendersInterfaceIcon() {
        JavaClassInfo info = makeClass("com.example.IFoo", JavaClassInfo.Kind.INTERFACE);
        assertIconEquals("INTERFACE 種別は INTERFACE アイコンになるはず",
                TreeNodeIcon.INTERFACE, new ClassEntry(info));
    }

    @Test
    public void classEntry_enum_rendersEnumIcon() {
        JavaClassInfo info = makeClass("com.example.Status", JavaClassInfo.Kind.ENUM);
        assertIconEquals("ENUM 種別は ENUM アイコンになるはず",
                TreeNodeIcon.ENUM, new ClassEntry(info));
    }

    @Test
    public void classEntry_annotation_rendersAnnotationIcon() {
        JavaClassInfo info = makeClass("com.example.MyAnnotation",
                JavaClassInfo.Kind.ANNOTATION);
        assertIconEquals("ANNOTATION 種別は ANNOTATION アイコンになるはず",
                TreeNodeIcon.ANNOTATION, new ClassEntry(info));
    }

    @Test
    public void classEntry_aidlInterface_rendersAidlIcon() {
        JavaClassInfo info = makeClass("com.example.IMyAidl",
                JavaClassInfo.Kind.AIDL_INTERFACE);
        assertIconEquals("AIDL_INTERFACE 種別は AIDL アイコンになるはず",
                TreeNodeIcon.AIDL, new ClassEntry(info));
    }

    // -------------------------------------------------------------------------
    // メソッド / 図種ノード
    // -------------------------------------------------------------------------

    @Test
    public void methodEntry_rendersMethodIcon() {
        JavaClassInfo owner = makeClass("com.example.Bar", JavaClassInfo.Kind.CLASS);
        juml.core.formats.uml.JavaMethodInfo method =
                new juml.core.formats.uml.JavaMethodInfo();
        method.setName("doSomething");
        assertIconEquals("MethodEntry は METHOD アイコンになるはず",
                TreeNodeIcon.METHOD, new MethodEntry(owner, method));
    }

    @Test
    public void methodDiagramEntry_sequence_rendersSequenceIcon() {
        JavaClassInfo owner = makeClass("com.example.Baz", JavaClassInfo.Kind.CLASS);
        juml.core.formats.uml.JavaMethodInfo method =
                new juml.core.formats.uml.JavaMethodInfo();
        method.setName("run");
        assertIconEquals("DiagramKind.SEQUENCE は SEQUENCE アイコンになるはず",
                TreeNodeIcon.SEQUENCE,
                new MethodDiagramEntry(owner, method, DiagramKind.SEQUENCE));
    }

    @Test
    public void methodDiagramEntry_activity_rendersActivityIcon() {
        JavaClassInfo owner = makeClass("com.example.Qux", JavaClassInfo.Kind.CLASS);
        juml.core.formats.uml.JavaMethodInfo method =
                new juml.core.formats.uml.JavaMethodInfo();
        method.setName("execute");
        assertIconEquals("DiagramKind.ACTIVITY は ACTIVITY アイコンになるはず",
                TreeNodeIcon.ACTIVITY,
                new MethodDiagramEntry(owner, method, DiagramKind.ACTIVITY));
    }

    // -------------------------------------------------------------------------
    // Manifest 系ノード
    // -------------------------------------------------------------------------

    @Test
    public void manifestEntry_rendersManifestIcon() {
        juml.core.formats.android.AndroidManifestInfo manifest =
                new juml.core.formats.android.AndroidManifestInfo();
        assertIconEquals("ManifestEntry は MANIFEST アイコンになるはず",
                TreeNodeIcon.MANIFEST, new ManifestEntry(manifest));
    }

    @Test
    public void componentGroupEntry_rendersComponentGroupIcon() {
        assertIconEquals("ComponentGroupEntry は COMPONENT_GROUP アイコンになるはず",
                TreeNodeIcon.COMPONENT_GROUP, new ComponentGroupEntry("Activities", 2));
    }

    @Test
    public void componentEntry_activity_rendersActivityComponentIcon() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.example.MainActivity");
        assertIconEquals("Android Activity は COMPONENT_ACTIVITY アイコンになるはず",
                TreeNodeIcon.COMPONENT_ACTIVITY, new ComponentEntry(info));
    }

    @Test
    public void componentEntry_service_rendersServiceIcon() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.SERVICE, "com.example.MyService");
        assertIconEquals("Android Service は COMPONENT_SERVICE アイコンになるはず",
                TreeNodeIcon.COMPONENT_SERVICE, new ComponentEntry(info));
    }

    @Test
    public void componentEntry_receiver_rendersReceiverIcon() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.RECEIVER, "com.example.MyReceiver");
        assertIconEquals("Android Receiver は COMPONENT_RECEIVER アイコンになるはず",
                TreeNodeIcon.COMPONENT_RECEIVER, new ComponentEntry(info));
    }

    @Test
    public void componentEntry_provider_rendersProviderIcon() {
        AndroidComponentInfo info = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.PROVIDER, "com.example.MyProvider");
        assertIconEquals("Android Provider は COMPONENT_PROVIDER アイコンになるはず",
                TreeNodeIcon.COMPONENT_PROVIDER, new ComponentEntry(info));
    }

    @Test
    public void permissionEntry_rendersPermissionIcon() {
        AndroidPermissionInfo perm =
                new AndroidPermissionInfo("android.permission.INTERNET");
        assertIconEquals("PermissionEntry は PERMISSION アイコンになるはず",
                TreeNodeIcon.PERMISSION, new PermissionEntry(perm));
    }

    @Test
    public void featureEntry_rendersFeatureIcon() {
        assertIconEquals("FeatureEntry は FEATURE アイコンになるはず",
                TreeNodeIcon.FEATURE, new FeatureEntry("android.hardware.camera"));
    }

    // -------------------------------------------------------------------------
    // 異常系
    // -------------------------------------------------------------------------

    @Test
    public void nullUserObject_rendersWithoutException() {
        // userObject が null / 未知型でも例外が起きないこと。
        Icon icon = renderIconFor(null);
        // アイコンが設定されないか、何らかの既定値が入るが、クラッシュしないことが重要。
        // (assertNotNull は強制しない — null アイコンも正常なフォールバック)
    }

    @Test
    public void unknownUserObject_rendersWithoutException() {
        Icon icon = renderIconFor("unknownStringObject");
        // 未知型でクラッシュしないことを保証。
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    private void assertIconEquals(String message, Icon expected, Object userObject) {
        Icon actual = renderIconFor(userObject);
        assertNotNull("アイコンが null でないはず — " + message, actual);
        assertEquals(message, expected, actual);
    }

    private Icon renderIconFor(Object userObject) {
        return GuiActionRunner.execute(() -> {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(userObject);
            JLabel label = (JLabel) renderer.getTreeCellRendererComponent(
                    tree, node, false, false, true, 0, false);
            return label.getIcon();
        });
    }

    private static JavaClassInfo makeClass(String fqn, JavaClassInfo.Kind kind) {
        JavaClassInfo ci = new JavaClassInfo();
        int dot = fqn.lastIndexOf('.');
        if (dot >= 0) {
            ci.setPackageName(fqn.substring(0, dot));
            ci.setSimpleName(fqn.substring(dot + 1));
        } else {
            ci.setSimpleName(fqn);
        }
        ci.setKind(kind);
        return ci;
    }
}
