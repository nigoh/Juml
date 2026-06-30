// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.init;

import juml.core.formats.uml.JavaClassInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Android アプリケーションクラスで使われる DI フレームワークを検出する。
 *
 * <p>対象パターン:</p>
 * <ul>
 *   <li>{@code @HiltAndroidApp} — Hilt DI フレームワーク</li>
 *   <li>{@code startKoin { }} 呼び出し — Koin DI フレームワーク</li>
 *   <li>{@code DaggerXxxComponent.create()} 呼び出し — Dagger DI フレームワーク</li>
 * </ul>
 */
public final class AppInitAnnotationDetector {

    /** 検出された DI フレームワークの種類。 */
    public enum DiFramework {
        HILT("@HiltAndroidApp"),
        KOIN("startKoin { }"),
        DAGGER("Dagger*Component");

        public final String label;

        DiFramework(String label) {
            this.label = label;
        }
    }

    private static final Pattern HILT_APP = Pattern.compile("@HiltAndroidApp");
    private static final Pattern START_KOIN = Pattern.compile("\\bstartKoin\\s*\\{");
    private static final Pattern DAGGER_COMPONENT = Pattern.compile(
            "\\bDagger[A-Za-z0-9_]+Component\\s*\\.\\s*(?:create|builder)\\s*\\(");

    /**
     * Application サブクラスのアノテーションリストと、ソース内のメソッド呼び出しから
     * 使用 DI フレームワークを検出して返す。
     *
     * @param cls クラス情報
     * @param sourceSnippet クラス全体のソーステキスト (未入手なら "")
     */
    public List<DiFramework> detect(JavaClassInfo cls, String sourceSnippet) {
        List<DiFramework> found = new ArrayList<>();
        if (cls == null) {
            return found;
        }
        // アノテーションリストから Hilt を検出
        if (cls.getAnnotations() != null) {
            for (String ann : cls.getAnnotations()) {
                if (ann.contains("HiltAndroidApp")) {
                    if (!found.contains(DiFramework.HILT)) {
                        found.add(DiFramework.HILT);
                    }
                }
            }
        }
        // ソーステキストから検出
        if (sourceSnippet != null && !sourceSnippet.isEmpty()) {
            if (HILT_APP.matcher(sourceSnippet).find()
                    && !found.contains(DiFramework.HILT)) {
                found.add(DiFramework.HILT);
            }
            if (START_KOIN.matcher(sourceSnippet).find()) {
                found.add(DiFramework.KOIN);
            }
            if (DAGGER_COMPONENT.matcher(sourceSnippet).find()) {
                found.add(DiFramework.DAGGER);
            }
        }
        return found;
    }
}
