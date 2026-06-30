// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ソース上の継承チェーンを辿って Android コンポーネント種別を判定する。
 *
 * <p>AndroidManifest.xml の宣言にあるコンポーネントは
 * {@link juml.core.formats.android.AndroidManifestParser} で抽出できるが、
 * <strong>Fragment は通常 Manifest に書かれない</strong>ため、Manifest 経由では
 * 検出できない。本クラスは {@code extends androidx.fragment.app.Fragment} のような
 * スーパークラスチェーンを {@link ClassIndex} で辿り、知っている基底に到達したら
 * 種別を返す。</p>
 *
 * <p>判定対象の基底クラス (FQN マッチではなく単純名フォールバックも許す):</p>
 * <ul>
 *   <li>{@code android.app.Activity} → ACTIVITY</li>
 *   <li>{@code android.app.Service} → SERVICE</li>
 *   <li>{@code android.content.BroadcastReceiver} → RECEIVER</li>
 *   <li>{@code android.content.ContentProvider} → PROVIDER</li>
 *   <li>{@code android.app.Fragment} / {@code androidx.fragment.app.Fragment} → FRAGMENT</li>
 * </ul>
 *
 * <p>Stage A ヘッダしか持たない外部 JAR 由来のクラスを通っても継承チェーンを
 * 辿れる (super_class が DB / ClassIndex に登録されていれば追跡可能)。
 * 解決不能になった時点で「不明」を返す。</p>
 */
public final class AndroidSuperclassDetector {

    /** 判定種別 (Manifest の {@code AndroidComponentInfo.Kind} とは別 enum)。 */
    public enum ComponentKind {
        APPLICATION,
        ACTIVITY,
        SERVICE,
        RECEIVER,
        PROVIDER,
        FRAGMENT
    }

    /** 既知の基底クラス FQN/単純名 → 種別 のマップ。 */
    private static final Map<String, ComponentKind> KNOWN_BASES;

    static {
        Map<String, ComponentKind> m = new HashMap<>();
        // FQN マッチ
        m.put("android.app.Application", ComponentKind.APPLICATION);
        m.put("androidx.multidex.MultiDexApplication", ComponentKind.APPLICATION);
        m.put("android.app.Activity", ComponentKind.ACTIVITY);
        m.put("androidx.appcompat.app.AppCompatActivity", ComponentKind.ACTIVITY);
        m.put("androidx.fragment.app.FragmentActivity", ComponentKind.ACTIVITY);
        m.put("android.app.Service", ComponentKind.SERVICE);
        m.put("android.app.IntentService", ComponentKind.SERVICE);
        m.put("androidx.lifecycle.LifecycleService", ComponentKind.SERVICE);
        // フレームワーク/サポートライブラリの Service 派生基底 (外部 jar で chain が解決
        // できなくても entry point として扱えるよう明示登録する)
        m.put("androidx.core.app.JobIntentService", ComponentKind.SERVICE);
        m.put("android.app.job.JobService", ComponentKind.SERVICE);
        m.put("androidx.media.MediaBrowserServiceCompat", ComponentKind.SERVICE);
        m.put("android.service.media.MediaBrowserService", ComponentKind.SERVICE);
        m.put("android.service.notification.NotificationListenerService", ComponentKind.SERVICE);
        m.put("android.service.quicksettings.TileService", ComponentKind.SERVICE);
        m.put("android.inputmethodservice.InputMethodService", ComponentKind.SERVICE);
        m.put("android.accessibilityservice.AccessibilityService", ComponentKind.SERVICE);
        m.put("android.service.dreams.DreamService", ComponentKind.SERVICE);
        m.put("com.google.android.gms.wearable.WearableListenerService", ComponentKind.SERVICE);
        m.put("android.content.BroadcastReceiver", ComponentKind.RECEIVER);
        m.put("android.content.ContentProvider", ComponentKind.PROVIDER);
        m.put("android.app.Fragment", ComponentKind.FRAGMENT);
        m.put("androidx.fragment.app.Fragment", ComponentKind.FRAGMENT);
        m.put("androidx.fragment.app.DialogFragment", ComponentKind.FRAGMENT);
        // 単純名フォールバック (依存 JAR が走査対象外で super_class が単純名のみのケース)
        m.put("Application", ComponentKind.APPLICATION);
        m.put("MultiDexApplication", ComponentKind.APPLICATION);
        m.put("Activity", ComponentKind.ACTIVITY);
        m.put("AppCompatActivity", ComponentKind.ACTIVITY);
        m.put("FragmentActivity", ComponentKind.ACTIVITY);
        m.put("Service", ComponentKind.SERVICE);
        m.put("IntentService", ComponentKind.SERVICE);
        m.put("LifecycleService", ComponentKind.SERVICE);
        m.put("JobIntentService", ComponentKind.SERVICE);
        m.put("JobService", ComponentKind.SERVICE);
        m.put("MediaBrowserServiceCompat", ComponentKind.SERVICE);
        m.put("MediaBrowserService", ComponentKind.SERVICE);
        m.put("NotificationListenerService", ComponentKind.SERVICE);
        m.put("TileService", ComponentKind.SERVICE);
        m.put("InputMethodService", ComponentKind.SERVICE);
        m.put("AccessibilityService", ComponentKind.SERVICE);
        m.put("DreamService", ComponentKind.SERVICE);
        m.put("WearableListenerService", ComponentKind.SERVICE);
        m.put("BroadcastReceiver", ComponentKind.RECEIVER);
        m.put("ContentProvider", ComponentKind.PROVIDER);
        m.put("Fragment", ComponentKind.FRAGMENT);
        m.put("DialogFragment", ComponentKind.FRAGMENT);
        KNOWN_BASES = m;
    }

    private static final int MAX_DEPTH = 16;

    private AndroidSuperclassDetector() {
    }

    /**
     * {@link ClassIndex} に登録された全クラスを走査して、Android コンポーネント
     * 種別が判定できる qn の {@code (qn → kind)} マップを返す (qn 順)。
     *
     * <p>Manifest にも登場するクラスは戻り値に含まれる。出所の区別 (manifest 由来か
     * superclass 由来か) は呼び出し側 (ComponentIngestor) が担当する。</p>
     */
    public static Map<String, ComponentKind> detect(ClassIndex index) {
        Map<String, ComponentKind> out = new LinkedHashMap<>();
        if (index == null) {
            return out;
        }
        for (String qn : index.qualifiedNames()) {
            ComponentKind kind = kindOf(qn, index);
            if (kind != null) {
                out.put(qn, kind);
            }
        }
        return out;
    }

    /** 単一クラスの判定。{@code qn} が {@link ClassIndex} に無い場合は null。 */
    public static ComponentKind kindOf(String qn, ClassIndex index) {
        if (qn == null || qn.isEmpty() || index == null) {
            return null;
        }
        java.util.Set<String> visited = new java.util.HashSet<>();
        return walk(qn, index, visited, 0);
    }

    private static ComponentKind walk(String qn, ClassIndex index, Set<String> visited, int depth) {
        if (qn == null || qn.isEmpty() || depth >= MAX_DEPTH || !visited.add(qn)) {
            return null;
        }
        ComponentKind direct = KNOWN_BASES.get(qn);
        if (direct != null) {
            return direct;
        }
        JavaClassInfo info = index.header(qn).orElse(null);
        if (info == null) {
            // header が無くとも単純名でヒットする可能性がある (typeref のみのケース)
            String simple = simpleNameOf(qn);
            return simple == null ? null : KNOWN_BASES.get(simple);
        }
        String superClass = info.getSuperClass();
        return walk(superClass, index, visited, depth + 1);
    }

    private static String simpleNameOf(String qn) {
        if (qn == null) {
            return null;
        }
        int dot = qn.lastIndexOf('.');
        return dot < 0 ? qn : qn.substring(dot + 1);
    }
}
