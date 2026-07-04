// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.refs;

import org.junit.Test;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;

import static org.junit.Assert.assertEquals;

/**
 * {@link NameResolver} の名前解決ルールの単体テスト。
 */
public class NameResolverTest {

    private static JavaClassInfo cls(String pkg, String name, String... imports) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        for (String i : imports) {
            c.getImports().add(i);
        }
        return c;
    }

    @Test
    public void importExactMatch() {
        ClassIndex idx = new ClassIndex();
        NameResolver r = new NameResolver(idx, null);
        JavaClassInfo owner = cls("com.foo", "Owner", "java.util.List");
        assertEquals("java.util.List", r.resolve("List", owner));
    }

    @Test
    public void samePackageWhenHeaderExists() {
        ClassIndex idx = new ClassIndex();
        idx.put(cls("com.foo", "Sibling"), null, null);
        NameResolver r = new NameResolver(idx, null);
        JavaClassInfo owner = cls("com.foo", "Owner");
        assertEquals("com.foo.Sibling", r.resolve("Sibling", owner));
    }

    @Test
    public void wildcardImport() {
        ClassIndex idx = new ClassIndex();
        idx.put(cls("java.util", "ArrayList"), null, null);
        NameResolver r = new NameResolver(idx, null);
        JavaClassInfo owner = cls("com.foo", "Owner", "java.util.*");
        assertEquals("java.util.ArrayList", r.resolve("ArrayList", owner));
    }

    @Test
    public void unresolvedReturnsInput() {
        ClassIndex idx = new ClassIndex();
        NameResolver r = new NameResolver(idx, null);
        JavaClassInfo owner = cls("com.foo", "Owner");
        assertEquals("UnknownClass", r.resolve("UnknownClass", owner));
    }

    @Test
    public void stripsGenericsAndArrays() {
        ClassIndex idx = new ClassIndex();
        NameResolver r = new NameResolver(idx, null);
        JavaClassInfo owner = cls("com.foo", "Owner", "java.util.List");
        assertEquals("java.util.List", r.resolve("List<String>[]", owner));
    }

    @Test
    public void nestedTypeSamePackageResolvesHeadToFqn() {
        // 同パッケージのネスト型参照 Config.Item は com.foo.Config.Item へ解決される
        // (以前は "Config.Item" のまま返り、参照インデックスに載らなかった)。
        ClassIndex idx = new ClassIndex();
        idx.put(cls("com.foo", "Config"), null, null);
        NameResolver r = new NameResolver(idx, null);
        JavaClassInfo owner = cls("com.foo", "Owner");
        assertEquals("com.foo.Config.Item", r.resolve("Config.Item", owner));
    }

    @Test
    public void nestedTypeViaImportResolvesHead() {
        ClassIndex idx = new ClassIndex();
        NameResolver r = new NameResolver(idx, null);
        JavaClassInfo owner = cls("com.foo", "Owner", "com.bar.Config");
        assertEquals("com.bar.Config.Item", r.resolve("Config.Item", owner));
    }

    @Test
    public void unresolvableDottedHeadIsPassedThrough() {
        // 先頭セグメントが解決できないドット名 (真の FQN 等) はそのまま返す。
        ClassIndex idx = new ClassIndex();
        NameResolver r = new NameResolver(idx, null);
        JavaClassInfo owner = cls("com.foo", "Owner");
        assertEquals("java.util.List", r.resolve("java.util.List", owner));
    }

    @Test
    public void fqnPassthrough() {
        ClassIndex idx = new ClassIndex();
        NameResolver r = new NameResolver(idx, null);
        JavaClassInfo owner = cls("com.foo", "Owner");
        assertEquals("com.bar.Already.Fqn", r.resolve("com.bar.Already.Fqn", owner));
    }
}
