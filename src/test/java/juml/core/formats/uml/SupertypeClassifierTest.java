// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * {@link SupertypeClassifier} の分類ロジックのテスト。
 */
public class SupertypeClassifierTest {

    private static JavaClassInfo owner() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.app");
        c.setSimpleName("Owner");
        return c;
    }

    @Test
    public void standardJdkPackageIsStandard() {
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                "java.util.AbstractList", owner(), null, new HashSet<>(), null, true);
        assertEquals(SupertypeClassifier.Kind.STANDARD, r.kind);
        assertEquals("java.util.AbstractList", r.fqn);
    }

    @Test
    public void androidPackageIsExternal() {
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                "android.app.Activity", owner(), null, new HashSet<>(), null, true);
        assertEquals(SupertypeClassifier.Kind.EXTERNAL, r.kind);
    }

    @Test
    public void distinguishOffMakesJdkExternal() {
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                "java.util.AbstractList", owner(), null, new HashSet<>(), null, false);
        assertEquals(SupertypeClassifier.Kind.EXTERNAL, r.kind);
    }

    @Test
    public void knownProjectClassIsProject() {
        Set<String> known = new HashSet<>();
        known.add("com.app.Base");
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                "com.app.Base", owner(), null, known, null, true);
        assertEquals(SupertypeClassifier.Kind.PROJECT, r.kind);
    }

    @Test
    public void dependencyClassPredicatePromotesUnknownToExternal() {
        // prefix 集合に無いパッケージ (社内ライブラリ / リポジトリ同梱 JAR) でも、
        // 依存インデックスに実在するなら EXTERNAL と判定する
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                "com.example.Foo", owner(), null, new HashSet<>(), null, true,
                fqn -> "com.example.Foo".equals(fqn));
        assertEquals(SupertypeClassifier.Kind.EXTERNAL, r.kind);
        // 実在しない FQN は従来通り UNKNOWN
        SupertypeClassifier.Result r2 = SupertypeClassifier.classify(
                "com.example.Ghost", owner(), null, new HashSet<>(), null, true,
                fqn -> "com.example.Foo".equals(fqn));
        assertEquals(SupertypeClassifier.Kind.UNKNOWN, r2.kind);
    }

    @Test
    public void simpleNameWithoutResolverIsUnknown() {
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                "Activity", owner(), null, new HashSet<>(), null, true);
        assertEquals(SupertypeClassifier.Kind.UNKNOWN, r.kind);
    }

    @Test
    public void resolverResolvesSimpleNameToFqn() {
        TypeRefResolver resolver = (ref, o) ->
                "Activity".equals(ref) ? "android.app.Activity" : ref;
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                "Activity", owner(), resolver, new HashSet<>(), null, true);
        assertEquals(SupertypeClassifier.Kind.EXTERNAL, r.kind);
        assertEquals("android.app.Activity", r.fqn);
    }

    @Test
    public void genericsAreStripped() {
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                "java.util.ArrayList<String>", owner(), null, new HashSet<>(), null, true);
        assertEquals(SupertypeClassifier.Kind.STANDARD, r.kind);
        assertEquals("java.util.ArrayList", r.fqn);
    }

    @Test
    public void unknownProjectLocalPackageIsUnknown() {
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                "com.other.Thing", owner(), null, new HashSet<>(), null, true);
        assertEquals(SupertypeClassifier.Kind.UNKNOWN, r.kind);
    }
}
