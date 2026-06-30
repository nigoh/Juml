// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

/**
 * Phase 2.4 で導入した「呼び出し第 1 引数が定数シンボルなら
 * {@link JavaMethodInfo.Call#getFirstArgLabel()} に保持する」機能の検証。
 *
 * <p>AAOS の {@code CarPropertyManager.getProperty(VehiclePropertyIds.XXX)} のような
 * 「シンボル定数 1 つを渡す呼び出し」を、シーケンス図ラベルに引数まで載せて
 * 表示できるようにするための土台機能。</p>
 */
public class JavaStructureExtractorConstantArgTest {

    private static JavaMethodInfo.Call firstCall(List<JavaClassInfo> infos,
                                                   String methodName) {
        for (JavaClassInfo c : infos) {
            for (JavaMethodInfo m : c.getMethods()) {
                for (JavaMethodInfo.Call call : m.getCalls()) {
                    if (methodName.equals(call.getMethodName())) {
                        return call;
                    }
                }
            }
        }
        return null;
    }

    @Test
    public void testDottedConstantSymbolCaptured() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(CarPropertyManager m) {"
                        + "  m.getProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET);"
                        + "} }");
        JavaMethodInfo.Call call = firstCall(cs, "getProperty");
        assertNotNull(call);
        assertEquals("VehiclePropertyIds.HVAC_TEMPERATURE_SET",
                call.getFirstArgLabel());
    }

    @Test
    public void testBareConstantSymbolCaptured() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) { m.set(MAX_VALUE); } }");
        JavaMethodInfo.Call call = firstCall(cs, "set");
        assertNotNull(call);
        assertEquals("MAX_VALUE", call.getFirstArgLabel());
    }

    @Test
    public void testMultiSegmentConstantCaptured() {
        // Manifest.permission.READ_PHONE_STATE のような 3 段ドット
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(C c) {"
                        + "  c.check(Manifest.permission.READ_PHONE_STATE);"
                        + "} }");
        JavaMethodInfo.Call call = firstCall(cs, "check");
        assertNotNull(call);
        assertEquals("Manifest.permission.READ_PHONE_STATE",
                call.getFirstArgLabel());
    }

    @Test
    public void testLowercaseVariableNotCaptured() {
        // 小文字始まりはローカル変数等とみなして拾わない
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m, int x) { m.get(x); } }");
        JavaMethodInfo.Call call = firstCall(cs, "get");
        assertNotNull(call);
        assertNull(call.getFirstArgLabel());
    }

    @Test
    public void testNumberLiteralNotCaptured() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) { m.get(123); } }");
        JavaMethodInfo.Call call = firstCall(cs, "get");
        assertNotNull(call);
        assertNull(call.getFirstArgLabel());
    }

    @Test
    public void testStringLiteralNotCaptured() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) { m.get(\"key\"); } }");
        JavaMethodInfo.Call call = firstCall(cs, "get");
        assertNotNull(call);
        assertNull(call.getFirstArgLabel());
    }

    @Test
    public void testCamelCaseClassReferenceNotCaptured() {
        // CarPropertyManager.class のような型参照は定数ではない
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) { m.get(CarPropertyManager.class); } }");
        JavaMethodInfo.Call call = firstCall(cs, "get");
        assertNotNull(call);
        // 末尾 "class" は小文字始まりなので拒否
        assertNull(call.getFirstArgLabel());
    }

    @Test
    public void testNoArgsNotCaptured() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) { m.refresh(); } }");
        JavaMethodInfo.Call call = firstCall(cs, "refresh");
        assertNotNull(call);
        assertNull(call.getFirstArgLabel());
    }

    @Test
    public void testCompoundExpressionFirstArgNotCaptured() {
        // FOO + 1 や FOO.method() のような複合式は定数単独参照ではない
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) { m.set(FOO + 1); } }");
        JavaMethodInfo.Call call = firstCall(cs, "set");
        assertNotNull(call);
        assertNull(call.getFirstArgLabel());
    }

    @Test
    public void testConstantFirstArgWithFollowingArgs() {
        // FOO の直後がカンマで他の引数が続いていても、先頭 1 つだけを拾う
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) {"
                        + "  m.setValue(HVAC_TEMPERATURE_SET, 22.5f);"
                        + "} }");
        JavaMethodInfo.Call call = firstCall(cs, "setValue");
        assertNotNull(call);
        assertEquals("HVAC_TEMPERATURE_SET", call.getFirstArgLabel());
    }

    @Test
    public void testSingleLetterUpperCaseNotCaptured() {
        // T のような 1 文字は型パラメータの可能性が高く拒否
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) { m.set(T); } }");
        JavaMethodInfo.Call call = firstCall(cs, "set");
        assertNotNull(call);
        assertNull(call.getFirstArgLabel());
    }

    @Test
    public void testConstantWithDigits() {
        // VERSION_2_0 のような数字を含む定数も採用
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) { m.set(VERSION_2_0); } }");
        JavaMethodInfo.Call call = firstCall(cs, "set");
        assertNotNull(call);
        assertEquals("VERSION_2_0", call.getFirstArgLabel());
    }

    @Test
    public void testCallInsideArgsNotAffected() {
        // 別呼び出しが第 1 引数の場合は捕捉しない (定数では無いため)
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void run(M m) { m.set(other.compute()); } }");
        JavaMethodInfo.Call call = firstCall(cs, "set");
        assertNotNull(call);
        assertNull(call.getFirstArgLabel());
    }
}
