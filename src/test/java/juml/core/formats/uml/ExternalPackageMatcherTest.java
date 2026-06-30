// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExternalPackageMatcherTest {

    @Test
    public void defaultPrefixesMatchJavaPkg() {
        assertTrue(ExternalPackageMatcher.isExternal("java.util", null));
        assertTrue(ExternalPackageMatcher.isExternal("java.util", Collections.emptySet()));
    }

    @Test
    public void defaultPrefixesMatchAndroidxAndKotlin() {
        assertTrue(ExternalPackageMatcher.isExternal("androidx.compose.ui", null));
        assertTrue(ExternalPackageMatcher.isExternal("kotlin.collections", null));
        assertTrue(ExternalPackageMatcher.isExternal("kotlinx.coroutines", null));
    }

    @Test
    public void projectPackageIsNotExternal() {
        assertFalse(ExternalPackageMatcher.isExternal("juml.app.uml", null));
        assertFalse(ExternalPackageMatcher.isExternal("com.example.foo", null));
    }

    @Test
    public void nullAndEmptyPkgReturnFalse() {
        assertFalse(ExternalPackageMatcher.isExternal(null, null));
        assertFalse(ExternalPackageMatcher.isExternal("", null));
    }

    @Test
    public void customPrefixesOverride() {
        Set<String> custom = new LinkedHashSet<>();
        custom.add("com.example.");
        assertTrue(ExternalPackageMatcher.isExternal("com.example.foo", custom));
        assertFalse(ExternalPackageMatcher.isExternal("java.util", custom));
    }

    @Test
    public void prefixMustEndWithDot() {
        Set<String> custom = new LinkedHashSet<>();
        custom.add("com.example.");
        // "com.examplefoo" は前方一致しない (prefix が "com.example." なので正常に "."
        // で区切られた次の要素を要求する)。
        assertFalse(ExternalPackageMatcher.isExternal("com.examplefoo", custom));
        assertTrue(ExternalPackageMatcher.isExternal("com.example", custom));
    }

    @Test
    public void defaultPrefixesIsImmutable() {
        try {
            ExternalPackageMatcher.DEFAULT_PREFIXES.add("foo.");
            // 不変セットなので UnsupportedOperationException が出るはず。
            // 出ない場合は何らかの理由で破られているので失敗扱い。
            assertFalse("DEFAULT_PREFIXES should be immutable", true);
        } catch (UnsupportedOperationException expected) {
            // OK
        }
    }
}
