// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.integration;

import org.junit.Test;
import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.android.AndroidManifestParser;
import juml.core.formats.android.AndroidPermissionInfo;
import juml.core.formats.android.GradleDependency;
import juml.core.formats.android.GradleProjectInfo;
import juml.core.formats.android.GradleScriptParser;
import juml.core.formats.java.JavaLexer;
import juml.core.formats.java.JavaToken;
import juml.core.formats.uml.AidlParser;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.util.ErrorListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 実在する OSS プロジェクトのサンプルソースを入力として Juml の各パーサを動かす統合テスト。
 *
 * <p>サンプルは {@code src/test/resources/samples/} 配下に同梱しており、出典は同ディレクトリの
 * {@code README.md} を参照。すべて Apache License 2.0 ライセンス。</p>
 *
 * <ul>
 *   <li>{@code easypermissions/} - googlesamples/easypermissions (Google, Apache 2.0)</li>
 *   <li>{@code aidl/} - AOSP platform_development (Apache 2.0)</li>
 * </ul>
 *
 * <p>合成された Java スニペットでは現れにくい現実世界のパターン
 * (Apache ライセンスヘッダ、複数行コメント、ラムダ、ネスト型、Parcelable.Creator など)
 * を解析できることを保証する。</p>
 */
public class RealWorldSampleParseTest {

    private static String loadResource(String path) throws IOException {
        try (InputStream in =
                RealWorldSampleParseTest.class.getResourceAsStream(path)) {
            assertNotNull("test resource not found on classpath: " + path, in);
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static JavaClassInfo findClass(List<JavaClassInfo> classes, String simpleName) {
        for (JavaClassInfo c : classes) {
            if (simpleName.equals(c.getSimpleName())) {
                return c;
            }
        }
        return null;
    }

    private static JavaMethodInfo findMethod(JavaClassInfo c, String name) {
        for (JavaMethodInfo m : c.getMethods()) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    // ---- JavaLexer: 実ソースをトークン化して例外が出ないことを確認 ----------

    @Test
    public void testLexerHandlesMainActivity() throws IOException {
        String src = loadResource("/samples/easypermissions/MainActivity.java");
        List<JavaToken> toks = new JavaLexer(src).tokenize();
        assertFalse("expected at least one token", toks.isEmpty());
        assertEquals("last token must be EOF",
                JavaToken.Type.EOF, toks.get(toks.size() - 1).type);
        // Apache ライセンスヘッダ (ブロックコメント) で先頭が消費されるが、
        // 後続の package キーワードが識別子トークンとして拾われていること。
        boolean sawPackage = false;
        for (JavaToken t : toks) {
            if (t.type == JavaToken.Type.IDENT && "package".equals(t.text)) {
                sawPackage = true;
                break;
            }
        }
        assertTrue("expected 'package' identifier in token stream", sawPackage);
    }

    @Test
    public void testLexerHandlesEasyPermissionsLibrary() throws IOException {
        String src = loadResource("/samples/easypermissions/EasyPermissions.java");
        List<JavaToken> toks = new JavaLexer(src).tokenize();
        // 358 行のソースを最後まで読み切れること (途中で字句解析が止まらない)。
        assertTrue("token count looks too small: " + toks.size(), toks.size() > 200);
        assertEquals(JavaToken.Type.EOF, toks.get(toks.size() - 1).type);
    }

    // ---- JavaStructureExtractor: 実ソースのクラス構造を抽出 ------------------

    @Test
    public void testExtractMainActivity() throws IOException {
        String src = loadResource("/samples/easypermissions/MainActivity.java");
        List<String> errors = new ArrayList<>();
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                src, ErrorListener.collecting(errors));

        JavaClassInfo activity = findClass(classes, "MainActivity");
        assertNotNull("MainActivity class not extracted: errors=" + errors, activity);
        assertEquals("pub.devrel.easypermissions.sample", activity.getPackageName());
        assertEquals(JavaClassInfo.Kind.CLASS, activity.getKind());
        assertEquals("AppCompatActivity", activity.getSuperClass());

        // implements EasyPermissions.PermissionCallbacks, EasyPermissions.RationaleCallbacks
        // パーサは qualifier 付きでも単純名でも片方で記録する可能性があるため
        // "PermissionCallbacks" / "RationaleCallbacks" が末尾に含まれていればよい。
        boolean hasPerm = false;
        boolean hasRat = false;
        for (String iface : activity.getInterfaces()) {
            if (iface.endsWith("PermissionCallbacks")) {
                hasPerm = true;
            }
            if (iface.endsWith("RationaleCallbacks")) {
                hasRat = true;
            }
        }
        assertTrue("interfaces missing PermissionCallbacks: "
                + activity.getInterfaces(), hasPerm);
        assertTrue("interfaces missing RationaleCallbacks: "
                + activity.getInterfaces(), hasRat);

        // 既知のメソッドが正しく抽出されること。
        for (String name : new String[] {
                "onCreate", "cameraTask", "locationAndContactsTask",
                "onRequestPermissionsResult", "onPermissionsGranted",
                "onPermissionsDenied", "onActivityResult",
                "onRationaleAccepted", "onRationaleDenied"}) {
            assertNotNull("missing method " + name + " in MainActivity",
                    findMethod(activity, name));
        }

        // onCreate(Bundle) は protected
        JavaMethodInfo onCreate = findMethod(activity, "onCreate");
        assertEquals("void", onCreate.getReturnType());
    }

    @Test
    public void testExtractAnnotationDeclaration() throws IOException {
        String src = loadResource(
                "/samples/easypermissions/AfterPermissionGranted.java");
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        JavaClassInfo anno = findClass(classes, "AfterPermissionGranted");
        assertNotNull(anno);
        assertEquals(JavaClassInfo.Kind.ANNOTATION, anno.getKind());
        assertEquals("pub.devrel.easypermissions", anno.getPackageName());
        // value() メソッドが拾えていること (annotation の attribute)。
        assertNotNull("missing value() attribute", findMethod(anno, "value"));
    }

    @Test
    public void testExtractNestedInterfaces() throws IOException {
        String src = loadResource("/samples/easypermissions/EasyPermissions.java");
        List<String> errors = new ArrayList<>();
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                src, ErrorListener.collecting(errors));

        JavaClassInfo outer = findClass(classes, "EasyPermissions");
        assertNotNull(outer);
        assertEquals(JavaClassInfo.Kind.CLASS, outer.getKind());
        assertEquals("pub.devrel.easypermissions", outer.getPackageName());

        // 内部 interface が抽出され、enclosingClass に外側クラスが入ること。
        JavaClassInfo permCb = findClass(classes, "PermissionCallbacks");
        JavaClassInfo ratCb = findClass(classes, "RationaleCallbacks");
        assertNotNull("PermissionCallbacks not extracted", permCb);
        assertNotNull("RationaleCallbacks not extracted", ratCb);
        assertEquals(JavaClassInfo.Kind.INTERFACE, permCb.getKind());
        assertEquals(JavaClassInfo.Kind.INTERFACE, ratCb.getKind());
        assertEquals("EasyPermissions", permCb.getEnclosingClass());
        assertEquals("EasyPermissions", ratCb.getEnclosingClass());

        // EasyPermissions.hasPermissions / requestPermissions の存在を確認。
        assertNotNull(findMethod(outer, "hasPermissions"));
        assertNotNull(findMethod(outer, "requestPermissions"));
        assertNotNull(findMethod(outer, "onRequestPermissionsResult"));
        assertNotNull(findMethod(outer, "somePermissionPermanentlyDenied"));

        // PermissionCallbacks.onPermissionsGranted / onPermissionsDenied。
        assertNotNull(findMethod(permCb, "onPermissionsGranted"));
        assertNotNull(findMethod(permCb, "onPermissionsDenied"));
    }

    @Test
    public void testExtractParcelableWithNestedBuilder() throws IOException {
        String src = loadResource("/samples/easypermissions/AppSettingsDialog.java");
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);

        JavaClassInfo outer = findClass(classes, "AppSettingsDialog");
        assertNotNull(outer);
        assertEquals(JavaClassInfo.Kind.CLASS, outer.getKind());
        // implements Parcelable
        assertTrue("interfaces should contain Parcelable: "
                + outer.getInterfaces(), outer.getInterfaces().contains("Parcelable"));

        JavaClassInfo builder = findClass(classes, "Builder");
        assertNotNull("nested Builder class not extracted", builder);
        assertEquals(JavaClassInfo.Kind.CLASS, builder.getKind());
        assertEquals("AppSettingsDialog", builder.getEnclosingClass());
    }

    // ---- AidlParser: AOSP の IRemoteService.aidl --------------------------

    @Test
    public void testParseAidlInterface() throws IOException {
        String src = loadResource("/samples/aidl/IRemoteService.aidl");
        List<JavaClassInfo> classes = AidlParser.parse(src);
        assertEquals(1, classes.size());
        JavaClassInfo iface = classes.get(0);
        assertEquals("com.example.android.apis.app", iface.getPackageName());
        assertEquals("IRemoteService", iface.getSimpleName());
        assertEquals(JavaClassInfo.Kind.AIDL_INTERFACE, iface.getKind());
        assertEquals(2, iface.getMethods().size());
        assertNotNull(findMethod(iface, "registerCallback"));
        assertNotNull(findMethod(iface, "unregisterCallback"));
    }

    // ---- AndroidManifestParser: 実マニフェスト ----------------------------

    @Test
    public void testParseAndroidManifest() throws IOException {
        String xml = loadResource("/samples/easypermissions/AndroidManifest.xml");
        List<String> errors = new ArrayList<>();
        AndroidManifestInfo info = AndroidManifestParser.parse(
                xml, ErrorListener.collecting(errors));
        assertTrue("manifest parse should be clean: " + errors, errors.isEmpty());
        assertEquals("pub.devrel.easypermissions.sample", info.getPackageName());
        // allowBackup="true" / supportsRtl="true" / theme="@style/AppTheme"
        assertEquals(Boolean.TRUE, info.getApplicationAllowBackup());
        assertEquals("@style/AppTheme", info.getApplicationTheme());
        // application.android:name は未指定 (label のみ)
        assertNull(info.getApplicationClass());

        // 1 Activity (MainActivity, ランチャー)
        assertEquals(1, info.getActivities().size());
        AndroidComponentInfo main = info.getActivities().get(0);
        // ".MainActivity" は package を補完して FQN になる。
        assertTrue("activity name should resolve to FQN: " + main.getName(),
                main.getName().endsWith(".MainActivity"));
        assertTrue("MainActivity should be a launcher (MAIN+LAUNCHER intent-filter)",
                main.isLauncher());

        // uses-permission が 5 件 (CAMERA / FINE_LOCATION / READ_CONTACTS /
        // READ_SMS / WRITE_EXTERNAL_STORAGE)
        List<String> names = new ArrayList<>();
        for (AndroidPermissionInfo p : info.getPermissions()) {
            names.add(p.getName());
        }
        assertEquals("expected 5 uses-permission entries: " + names, 5, names.size());
        assertTrue(names.toString(),
                names.contains("android.permission.CAMERA"));
        assertTrue(names.toString(),
                names.contains("android.permission.ACCESS_FINE_LOCATION"));
        assertTrue(names.toString(),
                names.contains("android.permission.READ_CONTACTS"));
        assertTrue(names.toString(),
                names.contains("android.permission.READ_SMS"));
        assertTrue(names.toString(),
                names.contains("android.permission.WRITE_EXTERNAL_STORAGE"));
    }

    // ---- GradleScriptParser: 実 build.gradle ------------------------------

    @Test
    public void testParseAppBuildGradle() throws IOException {
        String script = loadResource("/samples/easypermissions/app-build.gradle");
        List<String> errors = new ArrayList<>();
        GradleProjectInfo info = GradleScriptParser.parse(
                script, "build.gradle", ErrorListener.collecting(errors));

        // apply plugin: 'com.android.application'
        assertTrue("plugins should contain com.android.application: "
                + info.getPlugins(),
                info.getPlugins().contains("com.android.application"));
        assertTrue("isAndroidApplication() should be true",
                info.isAndroidApplication());
        assertFalse("isAndroidLibrary() should be false",
                info.isAndroidLibrary());

        // android { compileSdkVersion 30 }
        assertEquals(Integer.valueOf(30), info.getCompileSdk());

        // defaultConfig
        assertEquals("pub.devrel.easypermissions.sample", info.getApplicationId());
        assertEquals(Integer.valueOf(14), info.getMinSdk());
        assertEquals(Integer.valueOf(30), info.getTargetSdk());
        assertEquals(Integer.valueOf(1), info.getVersionCode());
        assertEquals("1.0", info.getVersionName());

        // buildTypes.release
        assertNotNull(info.getBuildTypes().get("release"));
        assertEquals(Boolean.TRUE,
                info.getBuildTypes().get("release").getMinifyEnabled());

        // dependencies: implementation 'androidx.appcompat:appcompat:1.1.0',
        // implementation "androidx.annotation:annotation:1.1.0",
        // implementation project(':easypermissions')
        boolean hasAppcompat = false;
        boolean hasAnnotation = false;
        boolean hasProjectDep = false;
        for (GradleDependency d : info.getDependencies()) {
            if ("androidx.appcompat".equals(d.getGroup())
                    && "appcompat".equals(d.getName())
                    && "1.1.0".equals(d.getVersion())) {
                hasAppcompat = true;
            }
            if ("androidx.annotation".equals(d.getGroup())
                    && "annotation".equals(d.getName())
                    && "1.1.0".equals(d.getVersion())) {
                hasAnnotation = true;
            }
            if (d.isModuleReference()
                    && "easypermissions".equals(d.getModuleRef())) {
                hasProjectDep = true;
            }
        }
        assertTrue("appcompat 1.1.0 dep not found in " + info.getDependencies(),
                hasAppcompat);
        assertTrue("annotation 1.1.0 dep not found in " + info.getDependencies(),
                hasAnnotation);
        assertTrue("project(':easypermissions') not found in "
                + info.getDependencies(), hasProjectDep);
    }

    // ---- PlantUmlClassDiagram: 実ソースから .puml を生成 ------------------

    @Test
    public void testGenerateClassDiagramFromRealSources() throws IOException {
        // 複数ファイルをまとめて 1 つの図にする。
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(JavaStructureExtractor.extract(
                loadResource("/samples/easypermissions/MainActivity.java")));
        all.addAll(JavaStructureExtractor.extract(
                loadResource("/samples/easypermissions/EasyPermissions.java")));
        all.addAll(JavaStructureExtractor.extract(
                loadResource(
                        "/samples/easypermissions/AfterPermissionGranted.java")));
        // AIDL も同じ図に乗せる。
        all.addAll(AidlParser.parse(
                loadResource("/samples/aidl/IRemoteService.aidl")));

        String puml = PlantUmlClassDiagram.generate(all);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));

        // 主要クラス・パッケージが図に現れていること。
        assertTrue("MainActivity not in diagram",
                puml.contains("MainActivity"));
        assertTrue("EasyPermissions not in diagram",
                puml.contains("EasyPermissions"));
        assertTrue("AfterPermissionGranted not in diagram",
                puml.contains("AfterPermissionGranted"));
        assertTrue("IRemoteService not in diagram",
                puml.contains("IRemoteService"));
        assertTrue("sample package not in diagram",
                puml.contains("pub.devrel.easypermissions.sample"));
    }
}
