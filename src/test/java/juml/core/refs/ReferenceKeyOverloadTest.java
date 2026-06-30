// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.refs;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * シグネチャ付き {@link ReferenceKey} (P3b) のオーバーロード区別と、
 * 名前単位での集約 ({@link ReferenceIndex#sitesByMember}) の検証。
 */
public class ReferenceKeyOverloadTest {

    private static ReferenceSite site(String callerMethod) {
        return new ReferenceSite("p.Caller", callerMethod, "Caller.java", 1,
                ReferenceSite.Kind.CALL);
    }

    @Test
    public void signatureMakesOverloadsDistinctKeys() {
        ReferenceKey kInt = ReferenceKey.ofMethod("p.X", "m", "m(int)");
        ReferenceKey kStr = ReferenceKey.ofMethod("p.X", "m", "m(java.lang.String)");
        assertEquals("同名でもシグネチャが違えば不一致", false, kInt.equals(kStr));
        assertEquals(false, kInt.hashCode() == kStr.hashCode());
        // シグネチャ無しキーとも別物
        assertEquals(false, kInt.equals(ReferenceKey.ofMethod("p.X", "m")));
    }

    @Test
    public void sitesByMemberAggregatesAcrossOverloads() {
        ReferenceIndex idx = new ReferenceIndex();
        idx.addReference(ReferenceKey.ofMethod("p.X", "m", "m(int)"), site("callA"));
        idx.addReference(ReferenceKey.ofMethod("p.X", "m", "m(java.lang.String)"),
                site("callB"));
        // オーバーロードごとに別エントリ
        assertEquals(2, idx.symbolCount());
        assertEquals(1, idx.sites(ReferenceKey.ofMethod("p.X", "m", "m(int)")).size());
        // 名前単位では両方集約される (既存の名前 lookup consumer が壊れない)
        List<ReferenceSite> all = idx.sitesByMember(ReferenceKey.Kind.METHOD, "p.X", "m");
        assertEquals(2, all.size());
    }

    @Test
    public void sitesByMemberMatchesSignaturelessKeyToo() {
        ReferenceIndex idx = new ReferenceIndex();
        idx.addReference(ReferenceKey.ofMethod("p.X", "m"), site("callA"));
        List<ReferenceSite> all = idx.sitesByMember(ReferenceKey.Kind.METHOD, "p.X", "m");
        assertTrue(!all.isEmpty());
        assertEquals(1, all.size());
    }
}
