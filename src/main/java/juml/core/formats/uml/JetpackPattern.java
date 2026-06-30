// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Android Jetpack 関連クラスを認識し、PlantUML 用ステレオタイプを返すユーティリティ。
 *
 * <p>判定はソースから取れる情報 (superClass / interfaces / クラスアノテーション /
 * コンストラクタアノテーション) だけで行う。import 解決はしないため、
 * extends の比較は単純名末尾一致 ({@code endsWith("." + base) || equals(base)}) で
 * 拾う。</p>
 */
public final class JetpackPattern {

    /** Fragment 系の継承元クラス名 (単純名)。優先順位は配列順 (上が優先)。 */
    private static final List<String> FRAGMENT_BASES = Collections.unmodifiableList(
            Arrays.asList(
                    "NavHostFragment",
                    "BottomSheetDialogFragment",
                    "DialogFragment",
                    "Fragment"));

    /** ViewModel 系の継承元クラス名 (単純名)。 */
    private static final List<String> VIEWMODEL_BASES = Collections.unmodifiableList(
            Arrays.asList(
                    "AndroidViewModel",
                    "ViewModel"));

    /** Hilt / Dagger の認識対象アノテーション (短縮名)。 */
    private static final Set<String> HILT_ENTRY_POINT = unmodifiableSetOf("AndroidEntryPoint");
    private static final Set<String> HILT_VIEWMODEL = unmodifiableSetOf("HiltViewModel");
    private static final Set<String> HILT_ANDROID_APP = unmodifiableSetOf("HiltAndroidApp");
    private static final String MODULE_ANN = "Module";
    private static final String INSTALL_IN_ANN = "InstallIn";
    private static final String INJECT_ANN = "Inject";

    private JetpackPattern() {
    }

    /**
     * クラスから Jetpack 系ステレオタイプ名のリストを抽出する。
     * 該当なしの場合は空リスト。重複は内部で除去するため呼び出し側で気にしなくてよい。
     */
    public static List<String> classify(JavaClassInfo info) {
        if (info == null) {
            return Collections.emptyList();
        }
        // LinkedHashSet で順序を保ちつつ重複を除去
        Set<String> out = new java.util.LinkedHashSet<>();

        String fragmentBase = matchSuperBase(info.getSuperClass(), FRAGMENT_BASES);
        if (fragmentBase != null) {
            out.add(fragmentBase);
        }

        String viewModelBase = matchSuperBase(info.getSuperClass(), VIEWMODEL_BASES);
        if (viewModelBase != null) {
            out.add(viewModelBase);
        }

        Set<String> classAnns = shortAnnotationNames(info.getAnnotations());
        if (containsAny(classAnns, HILT_ANDROID_APP)) {
            out.add("HiltAndroidApp");
        }
        if (containsAny(classAnns, HILT_ENTRY_POINT)) {
            out.add("AndroidEntryPoint");
        }
        if (containsAny(classAnns, HILT_VIEWMODEL)) {
            out.add("HiltViewModel");
        }
        if (classAnns.contains(MODULE_ANN)) {
            if (classAnns.contains(INSTALL_IN_ANN)) {
                out.add("HiltModule");
            } else {
                out.add("DaggerModule");
            }
        }
        if (hasInjectConstructor(info)) {
            out.add("Injectable");
        }

        return new ArrayList<>(out);
    }

    /** {@code "Foo"} / {@code "com.x.Foo"} / {@code "Foo<T>"} から単純名を取り出す。 */
    static String simpleName(String type) {
        if (type == null) {
            return "";
        }
        String s = type.trim();
        int lt = s.indexOf('<');
        if (lt >= 0) {
            s = s.substring(0, lt).trim();
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        return s;
    }

    /** アノテーション文字列 (例 {@code "InstallIn(SingletonComponent.class)"}) から名前部だけ取り出す。 */
    static String annotationShortName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("@")) {
            s = s.substring(1);
        }
        int paren = s.indexOf('(');
        if (paren >= 0) {
            s = s.substring(0, paren);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        return s;
    }

    private static String matchSuperBase(String superClass, List<String> bases) {
        if (superClass == null || superClass.isEmpty()) {
            return null;
        }
        String s = simpleName(superClass);
        for (String b : bases) {
            if (b.equals(s)) {
                return b;
            }
        }
        return null;
    }

    private static Set<String> shortAnnotationNames(List<String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> s = new HashSet<>();
        for (String a : annotations) {
            String n = annotationShortName(a);
            if (!n.isEmpty()) {
                s.add(n);
            }
        }
        return s;
    }

    private static boolean containsAny(Set<String> haystack, Set<String> needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInjectConstructor(JavaClassInfo info) {
        for (JavaMethodInfo m : info.getMethods()) {
            if (!m.isConstructor()) {
                continue;
            }
            for (String a : m.getAnnotations()) {
                if (INJECT_ANN.equals(annotationShortName(a))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> unmodifiableSetOf(String... values) {
        Set<String> s = new HashSet<>(Arrays.asList(values));
        return Collections.unmodifiableSet(s);
    }
}
