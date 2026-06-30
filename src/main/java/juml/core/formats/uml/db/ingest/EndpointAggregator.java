// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.ingest;

import juml.core.aaos.AidlBinding;
import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.dao.EndpointsDao;
import juml.core.screen.ScreenTransition;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Intent / Manifest / AIDL の各経路から得られた外部境界情報を
 * {@code external_endpoints} に集約する。
 *
 * <p>このテーブルは PR7 の「外部境界俯瞰図」と PR8 の影響分析の入力。
 * 1 つの境界 = 1 行 で正規化し、{@code source_kind} で出所を区別する。</p>
 */
public final class EndpointAggregator {

    private EndpointAggregator() {
    }

    /** Manifest 宣言 (Activity/Service/Receiver/Provider) を境界として登録。 */
    public static int ingestManifest(Connection conn, AndroidManifestInfo manifest, Long fileId)
            throws SQLException {
        if (manifest == null) {
            return 0;
        }
        int count = 0;
        count += writeComponents(conn, EndpointsDao.KIND_MANIFEST_ACTIVITY,
                manifest.getActivities(), fileId);
        count += writeComponents(conn, EndpointsDao.KIND_MANIFEST_SERVICE,
                manifest.getServices(), fileId);
        count += writeComponents(conn, EndpointsDao.KIND_MANIFEST_RECEIVER,
                manifest.getReceivers(), fileId);
        count += writeComponents(conn, EndpointsDao.KIND_MANIFEST_PROVIDER,
                manifest.getProviders(), fileId);
        return count;
    }

    /** {@link ScreenTransition} (Intent ベースの遷移) を境界として登録。 */
    public static int ingestIntentTransitions(Connection conn,
            Collection<ScreenTransition> transitions) throws SQLException {
        if (transitions == null || transitions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ScreenTransition t : transitions) {
            if (t == null || t.getTargetClassName() == null
                    || t.getTargetClassName().isEmpty()) {
                continue;
            }
            String kind = kindOf(t.getKind());
            EndpointsDao.insert(conn, kind,
                    t.getFromFqn(), t.getFromMethod(),
                    t.getTargetClassName(), null, null, t.getLineHint());
            count++;
        }
        return count;
    }

    /** AIDL interface 自体 + Stub binding を境界として登録。 */
    public static int ingestAidl(Connection conn, Collection<JavaClassInfo> aidlClasses,
            Map<String, List<AidlBinding>> bindings) throws SQLException {
        int count = 0;
        if (aidlClasses != null) {
            for (JavaClassInfo c : aidlClasses) {
                if (c == null || c.getKind() != JavaClassInfo.Kind.AIDL_INTERFACE) {
                    continue;
                }
                EndpointsDao.insert(conn, EndpointsDao.KIND_AIDL_INTERFACE,
                        null, null, c.getQualifiedName(), null, null, -1);
                count++;
            }
        }
        if (bindings != null) {
            for (Map.Entry<String, List<AidlBinding>> e : bindings.entrySet()) {
                List<AidlBinding> list = e.getValue();
                if (list == null) {
                    continue;
                }
                for (AidlBinding b : list) {
                    EndpointsDao.insert(conn, EndpointsDao.KIND_AIDL_BINDING,
                            b.getImplementationFqn(), null,
                            b.getAidlInterfaceFqn(),
                            attributes("impl_file", b.getImplementationFile()),
                            null, -1);
                    count++;
                }
            }
        }
        return count;
    }

    // ---- internals ----

    private static int writeComponents(Connection conn, String kind,
            List<AndroidComponentInfo> components, Long fileId) throws SQLException {
        if (components == null || components.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (AndroidComponentInfo c : components) {
            if (c == null || c.getName() == null || c.getName().isEmpty()) {
                continue;
            }
            EndpointsDao.insert(conn, kind, null, null, c.getName(),
                    componentAttributes(c), fileId, -1);
            count++;
        }
        return count;
    }

    private static String componentAttributes(AndroidComponentInfo c) {
        StringBuilder sb = new StringBuilder();
        appendAttr(sb, "exported", String.valueOf(c.getExported()));
        if (c.getPermission() != null && !c.getPermission().isEmpty()) {
            appendAttr(sb, "permission", c.getPermission());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String attributes(String key, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendAttr(sb, key, value);
        return sb.toString();
    }

    private static void appendAttr(StringBuilder sb, String key, String value) {
        if (sb.length() > 0) {
            sb.append(';');
        }
        sb.append(key).append('=').append(value);
    }

    private static String kindOf(ScreenTransition.Kind k) {
        if (k == null) {
            return EndpointsDao.KIND_INTENT_OTHER;
        }
        switch (k) {
            case START_ACTIVITY:
                return EndpointsDao.KIND_INTENT_START_ACTIVITY;
            case START_FOR_RESULT:
                return EndpointsDao.KIND_INTENT_START_FOR_RESULT;
            case SET_CLASS:
                return EndpointsDao.KIND_INTENT_SET_CLASS;
            default:
                return EndpointsDao.KIND_INTENT_OTHER;
        }
    }
}
