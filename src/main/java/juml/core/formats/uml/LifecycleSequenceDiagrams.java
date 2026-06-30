// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Android ライフサイクルメソッド (Activity の onCreate 等) を起点に
 * PlantUML シーケンス図を一括生成するヘルパ。
 *
 * <p>CLI ({@code --all} / {@code --sequence-diagrams}) とエディタの両方から
 * 同じ起点規則を参照できるよう、コンポーネント種別ごとの起点候補と
 * 生成ロジックをここに集約する。</p>
 */
public final class LifecycleSequenceDiagrams {

    private static final Map<String, List<String>> ENTRY_BY_TYPE;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("Application", Arrays.asList(
                "onCreate", "onConfigurationChanged", "onLowMemory"));
        m.put("Activity", Arrays.asList(
                "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy"));
        m.put("Service", Arrays.asList(
                "onStartCommand", "onCreate", "onBind", "onDestroy"));
        m.put("BroadcastReceiver", Collections.singletonList("onReceive"));
        m.put("ContentProvider", Arrays.asList(
                "onCreate", "query", "insert", "update", "delete"));
        ENTRY_BY_TYPE = Collections.unmodifiableMap(m);
    }

    /**
     * ライフサイクルメソッドが「いつ呼ばれるか」の日本語説明。
     * 素人は onCreate / onResume 等の名前だけでは呼び出しタイミングが分からないため、
     * 図のタイトルに添えて理解を助ける。キーは {@code <コンポーネント種別>.<メソッド名>}。
     */
    private static final Map<String, String> WHEN_CALLED;

    static {
        Map<String, String> w = new LinkedHashMap<>();
        // Activity (画面)
        w.put("Activity.onCreate", "画面が最初に作られるとき");
        w.put("Activity.onStart", "画面が表示され始めるとき");
        w.put("Activity.onResume", "画面が操作可能になるとき");
        w.put("Activity.onPause", "画面が一部隠れる/離れる直前");
        w.put("Activity.onStop", "画面が見えなくなったとき");
        w.put("Activity.onDestroy", "画面が破棄されるとき");
        // Application (アプリ全体)
        w.put("Application.onCreate", "アプリ起動時の最初の初期化");
        w.put("Application.onConfigurationChanged", "画面回転など設定が変わったとき");
        w.put("Application.onLowMemory", "メモリ不足になったとき");
        // Service (裏で動く処理)
        w.put("Service.onStartCommand", "サービスが開始命令を受けたとき");
        w.put("Service.onCreate", "サービスが作られるとき");
        w.put("Service.onBind", "他から接続されるとき");
        w.put("Service.onDestroy", "サービスが破棄されるとき");
        // BroadcastReceiver (通知の受け取り)
        w.put("BroadcastReceiver.onReceive", "通知 (ブロードキャスト) を受け取ったとき");
        // ContentProvider (データ共有の窓口)
        w.put("ContentProvider.onCreate", "プロバイダが初期化されるとき");
        w.put("ContentProvider.query", "データの取得を求められたとき");
        w.put("ContentProvider.insert", "データの追加を求められたとき");
        w.put("ContentProvider.update", "データの更新を求められたとき");
        w.put("ContentProvider.delete", "データの削除を求められたとき");
        WHEN_CALLED = Collections.unmodifiableMap(w);
    }

    /**
     * ライフサイクル図のタイトルを「Class.method — いつ呼ばれるか」で組み立てる。
     * 説明が無いメソッドは {@code Class.method} のみを返す。
     */
    static String lifecycleTitle(String compType, String className, String methodName) {
        String base = className + "." + methodName;
        String when = WHEN_CALLED.get(compType + "." + methodName);
        return when == null ? base : base + " — " + when;
    }

    private LifecycleSequenceDiagrams() {
    }

    /** 生成された 1 本のシーケンス図 (起点クラス・メソッドと PlantUML テキスト)。 */
    public static final class Entry {
        public final String className;
        public final String methodName;
        public final String puml;

        Entry(String className, String methodName, String puml) {
            this.className = className;
            this.methodName = methodName;
            this.puml = puml;
        }

        /** ファイル名のベース ({@code Class.method})。拡張子は付与しない。 */
        public String baseName() {
            return className + "." + methodName;
        }
    }

    /**
     * クラス情報リストから Android ライフサイクル起点のシーケンス図をすべて生成して返す。
     * @param infos プロジェクトから抽出したクラス情報
     * @param opts シーケンス図生成オプション (null なら既定)
     * @return 生成された PlantUML シーケンス図のリスト (起点が見つからなければ空)
     */
    public static List<Entry> generateAll(List<JavaClassInfo> infos,
                                          PlantUmlSequenceDiagram.Options opts) {
        List<Entry> result = new ArrayList<>();
        if (infos == null || infos.isEmpty()) {
            return result;
        }
        PlantUmlSequenceDiagram.Options o = opts != null
                ? opts : new PlantUmlSequenceDiagram.Options();
        for (JavaClassInfo c : infos) {
            String compType = c.getAndroidComponentType();
            if (compType == null) {
                continue;
            }
            List<String> methodNames = ENTRY_BY_TYPE.get(compType);
            if (methodNames == null) {
                continue;
            }
            for (String mn : methodNames) {
                JavaMethodInfo m = findMethod(c, mn);
                if (m == null || m.getStatements().isEmpty()) {
                    continue;
                }
                // タイトルに「いつ呼ばれるか」を添える。呼び出し元の opts を汚さないよう
                // 元の title を退避し、生成後に必ず復元する。
                String savedTitle = o.title;
                o.title = lifecycleTitle(compType, c.getSimpleName(), m.getName());
                String puml;
                try {
                    puml = PlantUmlSequenceDiagram.generate(
                            infos, c.getSimpleName(), m.getName(), o);
                } finally {
                    o.title = savedTitle;
                }
                result.add(new Entry(c.getSimpleName(), m.getName(), puml));
            }
        }
        return result;
    }

    private static JavaMethodInfo findMethod(JavaClassInfo cls, String name) {
        for (JavaMethodInfo cand : cls.getMethods()) {
            if (name.equals(cand.getName()) && !cand.isAbstract()) {
                return cand;
            }
        }
        return null;
    }
}
