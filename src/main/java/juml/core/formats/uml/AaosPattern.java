// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AAOS (Android Automotive OS) 固有のパターンを認識するユーティリティ。
 *
 * <p>クラスのカテゴリ (CarManager / CarService / AAOS Annotation / 他) や、
 * AAOS 固有のアノテーション ({@code @AddedIn}, {@code @AddedInOrBefore},
 * {@code @ApiRequirements}) を検出する。</p>
 */
public final class AaosPattern {

    /** AAOS API バージョニング・要件アノテーション。 */
    public static final Set<String> API_ANNOTATIONS;
    static {
        Set<String> s = new HashSet<>(Arrays.asList(
                "AddedIn", "AddedInOrBefore", "ApiRequirements",
                "RequiresPermission", "PermissionResult",
                "MinimumPlatformSdkVersion", "MinimumCarVersion"));
        API_ANNOTATIONS = Collections.unmodifiableSet(s);
    }

    /** AAOS の主要パッケージプレフィックス。 */
    public static final List<String> AAOS_PACKAGE_PREFIXES = Collections.unmodifiableList(
            Arrays.asList(
                    "android.car",
                    "com.android.car",
                    "com.google.android.car",
                    "android.automotive",
                    "com.android.internal.car"));

    private static final Pattern MANAGER_PATTERN = Pattern.compile("^Car[A-Z].*Manager$");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("^Car[A-Z].*Service$");
    private static final Pattern AIDL_PATTERN = Pattern.compile("^ICar[A-Z0-9].*$");
    /**
     * AIDL 生成された binder stub の継承パターン。{@code ICarFoo.Stub} や、
     * 多重ネストの {@code Outer.Inner.Stub} (任意のジェネリクス後置を含む) にも
     * マッチさせるため、最終セグメントが {@code Stub} で終わる修飾名を対象にする。
     */
    private static final Pattern STUB_SUPERCLASS_PATTERN =
            Pattern.compile("^.+\\.Stub(?:<[^>]*>)?$");
    /**
     * クラスの JavaDoc 内で hidden API を示すマーカー。{@code @hide} もしくは
     * {@code {@hide}} の前後に単語境界 (改行・空白・記号) があることを要求し、
     * 識別子内の {@code @hide} 等での誤検出を避ける。
     */
    private static final Pattern HIDE_JAVADOC_PATTERN =
            Pattern.compile("(?:^|[\\s*{(])@hide(?:$|[\\s*})])");

    /**
     * AAOS の {@code @ApiRequirements(minPlatformVersion=...)} などで現れる
     * {@code Car.PLATFORM_VERSION_TIRAMISU_0} 形式のシンボル定数から、
     * コードネーム部 (例: {@code TIRAMISU}) を取り出す正規表現。
     */
    private static final Pattern PLATFORM_VERSION_SYMBOL =
            Pattern.compile("PLATFORM_VERSION_([A-Z][A-Z0-9_]*?)(?:_\\d+)?$");

    /** Android プラットフォーム API 可視性マーカーの annotation 短名。 */
    private static final Set<String> SYSTEM_API_ANNOTATIONS;
    private static final Set<String> TEST_API_ANNOTATIONS;
    static {
        SYSTEM_API_ANNOTATIONS = Collections.unmodifiableSet(new HashSet<>(
                Arrays.asList("SystemApi")));
        TEST_API_ANNOTATIONS = Collections.unmodifiableSet(new HashSet<>(
                Arrays.asList("TestApi")));
    }

    /** クラスのカテゴリを推定する (見つからなければ null)。 */
    public static String categorize(JavaClassInfo info) {
        if (info == null || info.getSimpleName() == null) {
            return null;
        }
        String name = info.getSimpleName();
        boolean inAaosPkg = isInAaosPackage(info.getPackageName());
        if (info.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE) {
            return "AIDL";
        }
        if (MANAGER_PATTERN.matcher(name).matches() && inAaosPkg) {
            return "CarManager";
        }
        if (SERVICE_PATTERN.matcher(name).matches() && inAaosPkg) {
            return "CarService";
        }
        if (AIDL_PATTERN.matcher(name).matches()
                && info.getKind() == JavaClassInfo.Kind.INTERFACE) {
            return "ICarInterface";
        }
        if (inAaosPkg && hasAaosApiAnnotation(info)) {
            return "AaosApi";
        }
        return null;
    }

    /** パッケージ名が AAOS 関連プレフィックスにマッチするか。 */
    public static boolean isInAaosPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        for (String p : AAOS_PACKAGE_PREFIXES) {
            if (pkg.equals(p) || pkg.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }

    /** クラス/メソッド/フィールドのアノテーションリストに AAOS API アノテーションがあれば true。 */
    public static boolean hasAaosApiAnnotation(JavaClassInfo info) {
        if (info == null) {
            return false;
        }
        if (containsAaosApi(info.getAnnotations())) {
            return true;
        }
        for (JavaMethodInfo m : info.getMethods()) {
            if (containsAaosApi(m.getAnnotations())) {
                return true;
            }
        }
        for (JavaFieldInfo f : info.getFields()) {
            if (containsAaosApi(f.getAnnotations())) {
                return true;
            }
        }
        return false;
    }

    /** 単一アノテーション (例 "AddedIn(majorVersion=33)") から短い名前を取得し API マーカーか判定。 */
    public static boolean isAaosApiAnnotation(String annotation) {
        if (annotation == null || annotation.isEmpty()) {
            return false;
        }
        String name = annotation;
        int paren = name.indexOf('(');
        if (paren >= 0) {
            name = name.substring(0, paren);
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        return API_ANNOTATIONS.contains(name);
    }

    private static boolean containsAaosApi(List<String> annotations) {
        for (String a : annotations) {
            if (isAaosApiAnnotation(a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Android プラットフォーム API の可視性マーカーを 1 つだけ返す
     * (見つからなければ null)。
     *
     * <p>優先順位は {@code Hidden > SystemApi > TestApi}。これは
     * 「`@SystemApi` だが `@hide` JavaDoc 付き」のような併用パターンで、
     * より制限の強い hidden を優先表示するため。</p>
     *
     * <p>判定基準:</p>
     * <ul>
     *   <li>{@code Hidden}: クラスの JavaDoc に {@code @hide} を含む</li>
     *   <li>{@code SystemApi}: クラスのアノテーションリストに
     *       {@code @SystemApi} が含まれる (FQN や引数有りも考慮)</li>
     *   <li>{@code TestApi}: 同 {@code @TestApi}</li>
     * </ul>
     *
     * <p>これらは AAOS 専用ではなく Android プラットフォーム全般のマーカー
     * だが、AAOS の Stub / CarService / 内部 SDK で多用されるため本
     * パターン集に同居させる。</p>
     */
    public static String apiVisibilityStereotype(JavaClassInfo info) {
        if (info == null) {
            return null;
        }
        if (hasHideJavadoc(info)) {
            return "Hidden";
        }
        if (hasAnnotationShortName(info.getAnnotations(), SYSTEM_API_ANNOTATIONS)) {
            return "SystemApi";
        }
        if (hasAnnotationShortName(info.getAnnotations(), TEST_API_ANNOTATIONS)) {
            return "TestApi";
        }
        return null;
    }

    /**
     * このクラスが AIDL の生成 stub を継承して binder サービスを実装しているか。
     *
     * <p>典型的なパターン: {@code class CarFooService extends ICarFoo.Stub}。
     * シーケンス図でプロセス境界 ({@code binder} hop) を可視化する判定に使う。
     * superClass 名の末尾セグメントが {@code Stub} (任意のジェネリクス後置可)
     * で、かつ前段が 1 セグメント以上ある場合に true を返す。</p>
     */
    public static boolean isAidlBinderImpl(JavaClassInfo info) {
        if (info == null) {
            return false;
        }
        String sup = info.getSuperClass();
        if (sup == null || sup.isEmpty()) {
            return false;
        }
        return STUB_SUPERCLASS_PATTERN.matcher(sup.trim()).matches();
    }

    /** JavaDoc コメントに {@code @hide} マーカーが含まれているか。 */
    private static boolean hasHideJavadoc(JavaClassInfo info) {
        String c = info.getComment();
        if (c == null || c.isEmpty()) {
            return false;
        }
        return HIDE_JAVADOC_PATTERN.matcher(c).find();
    }

    /**
     * アノテーション短名 ({@code @Foo} の {@code Foo} 部) のいずれかが
     * {@code names} に含まれているか。FQN 形式 ({@code android.annotation.SystemApi})
     * や引数有り ({@code SystemApi(client=...)}) も短名比較で吸収する。
     */
    private static boolean hasAnnotationShortName(List<String> annotations,
                                                    Set<String> names) {
        if (annotations == null) {
            return false;
        }
        for (String a : annotations) {
            if (a == null || a.isEmpty()) {
                continue;
            }
            String s = a;
            int paren = s.indexOf('(');
            if (paren >= 0) {
                s = s.substring(0, paren);
            }
            int dot = s.lastIndexOf('.');
            if (dot >= 0) {
                s = s.substring(dot + 1);
            }
            if (names.contains(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * クラスの AAOS / Android API レベル要件を 1 つの短いバッジ文字列で返す
     * (見つからなければ null)。
     *
     * <p>サポートする annotation 形式:</p>
     * <ul>
     *   <li>{@code @AddedIn(majorVersion=33)} / {@code @AddedIn(33)} → {@code "API 33+"}</li>
     *   <li>{@code @AddedInOrBefore(majorVersion=33)} → {@code "API <=33"}</li>
     *   <li>{@code @MinimumPlatformSdkVersion(33)} → {@code "Plat 33+"}</li>
     *   <li>{@code @MinimumCarVersion(33)} → {@code "Car 33+"}</li>
     *   <li>{@code @ApiRequirements(minPlatformVersion=Car.PLATFORM_VERSION_TIRAMISU_0,
     *       minCarVersion=Car.PLATFORM_VERSION_TIRAMISU_0)} → {@code "Plat TIRAMISU+/Car TIRAMISU+"}</li>
     * </ul>
     *
     * <p>{@code @ApiRequirements} を最優先で評価し、それが無い場合に他の単軸
     * マーカーを annotation 出現順に走査する。複数 annotation の集約は行わず、
     * 最初に見つかった有効なバッジを返す (実プロジェクトでクラス宣言に
     * 複数の AAOS API marker が共存することはほとんど無いため)。</p>
     */
    public static String apiLevelBadge(JavaClassInfo info) {
        if (info == null) {
            return null;
        }
        // 最優先: @ApiRequirements (Plat+Car の組み合わせ表示)
        for (String ann : info.getAnnotations()) {
            if ("ApiRequirements".equals(annotationShortName(ann))) {
                String b = parseApiRequirementsBadge(ann);
                if (b != null) {
                    return b;
                }
            }
        }
        // 続いて単軸マーカー (AddedIn / MinimumCarVersion 等)
        for (String ann : info.getAnnotations()) {
            String b = parseSingleAxisBadge(ann);
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    /** annotation 文字列 ({@code "AddedIn(majorVersion=33)"}) から短名を取り出す。 */
    private static String annotationShortName(String ann) {
        if (ann == null || ann.isEmpty()) {
            return "";
        }
        String s = ann;
        int paren = s.indexOf('(');
        if (paren >= 0) {
            s = s.substring(0, paren);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        return s.trim();
    }

    /**
     * 単軸 API レベル marker ({@code @AddedIn} / {@code @AddedInOrBefore} /
     * {@code @MinimumPlatformSdkVersion} / {@code @MinimumCarVersion}) を
     * バッジ文字列に変換する。該当しなければ null。
     */
    private static String parseSingleAxisBadge(String ann) {
        String shortName = annotationShortName(ann);
        String args = extractArgs(ann);
        switch (shortName) {
            case "AddedIn": {
                String v = extractArgValue(args, "majorVersion");
                if (v == null) {
                    v = extractFirstPositionalValue(args);
                }
                return v == null ? null : "API " + normalizeVersionToken(v) + "+";
            }
            case "AddedInOrBefore": {
                String v = extractArgValue(args, "majorVersion");
                if (v == null) {
                    v = extractFirstPositionalValue(args);
                }
                return v == null ? null : "API <=" + normalizeVersionToken(v);
            }
            case "MinimumPlatformSdkVersion": {
                String v = extractFirstPositionalValue(args);
                if (v == null) {
                    v = extractArgValue(args, "value");
                }
                return v == null ? null : "Plat " + normalizeVersionToken(v) + "+";
            }
            case "MinimumCarVersion": {
                String v = extractFirstPositionalValue(args);
                if (v == null) {
                    v = extractArgValue(args, "value");
                }
                return v == null ? null : "Car " + normalizeVersionToken(v) + "+";
            }
            default:
                return null;
        }
    }

    /**
     * {@code @ApiRequirements(minPlatformVersion=..., minCarVersion=...)} から
     * {@code "Plat <ver>+/Car <ver>+"} 形式のバッジを構築する。両方欠落なら null。
     */
    private static String parseApiRequirementsBadge(String ann) {
        String args = extractArgs(ann);
        String plat = extractArgValue(args, "minPlatformVersion");
        String car = extractArgValue(args, "minCarVersion");
        if (plat == null && car == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (plat != null) {
            sb.append("Plat ").append(normalizeVersionToken(plat)).append('+');
        }
        if (car != null) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append("Car ").append(normalizeVersionToken(car)).append('+');
        }
        return sb.toString();
    }

    /**
     * バージョン引数の生文字列を表示用に整形する。
     * <ul>
     *   <li>純粋な数値 ({@code "33"}) はそのまま返す</li>
     *   <li>{@code Car.PLATFORM_VERSION_TIRAMISU_0} 等の定数はコードネーム
     *       ({@code TIRAMISU}) を抽出して返す</li>
     *   <li>それ以外は前後の引用符・空白を削っただけの値を返す</li>
     * </ul>
     */
    private static String normalizeVersionToken(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        // 引用符付きリテラル
        if (s.length() >= 2
                && (s.charAt(0) == '"' || s.charAt(0) == '\'')
                && s.charAt(s.length() - 1) == s.charAt(0)) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.isEmpty()) {
            return "";
        }
        // 純粋な数値はそのまま
        if (s.chars().allMatch(Character::isDigit)) {
            return s;
        }
        // PLATFORM_VERSION_<NAME>_<N> -> <NAME>
        java.util.regex.Matcher m = PLATFORM_VERSION_SYMBOL.matcher(s);
        if (m.find()) {
            return m.group(1);
        }
        return s;
    }

    /** annotation 文字列から括弧内 ({@code "(...)"}) を取り出す。引数なしなら空文字。 */
    private static String extractArgs(String ann) {
        if (ann == null) {
            return "";
        }
        int open = ann.indexOf('(');
        int close = ann.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return "";
        }
        return ann.substring(open + 1, close);
    }

    /**
     * {@code key=value} ペア (任意の空白許容) の {@code value} を取り出す。
     * 値はカンマ ({@code ,}) または閉じ括弧で終端する。ネストした括弧は深さ
     * を辿って終端を判定する (例: {@code arr={A, B}} を 1 つの値として扱う)。
     */
    private static String extractArgValue(String args, String key) {
        if (args == null || args.isEmpty() || key == null) {
            return null;
        }
        int n = args.length();
        int i = 0;
        while (i < n) {
            // key の境界一致を探す (識別子の中間に部分一致しないように)
            int hit = args.indexOf(key, i);
            if (hit < 0) {
                return null;
            }
            boolean leftBoundary = hit == 0
                    || !Character.isJavaIdentifierPart(args.charAt(hit - 1));
            int after = hit + key.length();
            if (!leftBoundary || after >= n) {
                i = hit + 1;
                continue;
            }
            int p = after;
            while (p < n && Character.isWhitespace(args.charAt(p))) {
                p++;
            }
            if (p >= n || args.charAt(p) != '=') {
                i = hit + 1;
                continue;
            }
            p++;
            while (p < n && Character.isWhitespace(args.charAt(p))) {
                p++;
            }
            // 値を読み込む: ',' または ')' またはネスト中の '}' / ']' の対応外で停止
            int start = p;
            int depth = 0;
            while (p < n) {
                char c = args.charAt(p);
                if (depth == 0 && c == ',') {
                    break;
                }
                if (c == '(' || c == '{' || c == '[') {
                    depth++;
                } else if (c == ')' || c == '}' || c == ']') {
                    if (depth == 0) {
                        break;
                    }
                    depth--;
                }
                p++;
            }
            return args.substring(start, p).trim();
        }
        return null;
    }

    /**
     * 位置引数の先頭 ({@code @AddedIn(33)} の {@code 33}) を取り出す。
     * {@code key=value} 形式が先頭にある場合は位置引数とみなさず null を返す。
     */
    private static String extractFirstPositionalValue(String args) {
        if (args == null) {
            return null;
        }
        String s = args.trim();
        if (s.isEmpty()) {
            return null;
        }
        // 先頭に key= があるか
        int i = 0;
        int n = s.length();
        while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) {
            i++;
        }
        int j = i;
        while (j < n && Character.isWhitespace(s.charAt(j))) {
            j++;
        }
        if (i > 0 && j < n && s.charAt(j) == '=') {
            return null; // named-arg only — no positional
        }
        // 最初の `,` 手前まで (深さを考慮)
        int depth = 0;
        int end = s.length();
        for (int k = 0; k < n; k++) {
            char c = s.charAt(k);
            if (depth == 0 && c == ',') {
                end = k;
                break;
            }
            if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                if (depth == 0) {
                    break;
                }
                depth--;
            }
        }
        return s.substring(0, end).trim();
    }

    private AaosPattern() {
    }
}
