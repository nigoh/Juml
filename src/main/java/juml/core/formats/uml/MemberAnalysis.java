// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 全クラスのメンバー (フィールド・メソッド・enum 定数) を 1 メンバー=1 行に整理し、
 * 「素のメンバー属性 + 構造把握 + 処理解析」のセルを計算する単一の解析器。
 *
 * <p>{@link ClassMemberReport} (CSV) と {@link MemberWorkbookExporter} (.xlsx) の双方が
 * ここで生成する {@link #headers()} と {@link #rows(List)} を共有する。列定義・凡例・数値判定は
 * すべて {@link Col} 列挙に集約する。</p>
 *
 * <p>処理解析メトリクス (分岐数・ループ数・呼び出し等) はメソッド本体の構造化文ツリー
 * ({@link JavaMethodInfo#getStatements()}) から計算するため、本体を解析する FULL パースでのみ実値になる。
 * {@code ioCategory}/{@code role} はヒューリスティックな推定、{@code usedBy} は解析対象集合内での
 * 被参照メソッド数 (fan-in) の近似で、オーバーロードや同名クラスは区別しない。</p>
 */
public final class MemberAnalysis {

    /** 出力列の定義。順序がそのまま CSV / Excel の列順になる (先頭 17 列は既存互換)。 */
    public enum Col {
        CLASS("class", false, "全行", "ExportController",
                "クラスの単純名 (FQN/URI は使わない)"),
        PACKAGE("package", false, "全行", "juml.app.uml",
                "パッケージ名"),
        KIND("kind", false, "全行", "class / interface / enum / record",
                "型種別 (class/interface/enum/@interface/aidl/record)"),
        MEMBER("member", false, "全行", "method / field / enum-constant / (none)",
                "メンバー種別。(none) はメンバーを持たないクラス"),
        VISIBILITY("visibility", false, "メンバー", "public / private",
                "可視性 (public/protected/package/private)"),
        NAME("name", false, "メンバー", "buildExportPopup",
                "メンバー名 (フィールド名/メソッド名/enum 定数名)"),
        TYPE("type", false, "フィールド/メソッド", "JPopupMenu",
                "フィールド宣言型 / メソッド戻り値型 (コンストラクタは空)"),
        PARAMS("params", false, "メソッド", "parent: Frame, state: DiagramState",
                "メソッド引数 (name: type, ...)"),
        MODIFIERS("modifiers", false, "メンバー", "static final",
                "修飾子 (static/final/abstract)"),
        ENCLOSING("enclosing", false, "全行", "Service (Inner の外側)",
                "外側クラスの単純名チェーン (ネスト時)。トップレベルは空"),
        EXTENDS("extends", false, "全行", "Base",
                "親クラスの単純名 (継承元)"),
        IMPLEMENTS("implements", false, "全行", "Runnable",
                "実装インタフェースの単純名 (; 連結)"),
        LINE("line", true, "メソッド", "30",
                "メソッド/コンストラクタの宣言開始行 (1始まり)"),
        ANNOTATIONS("annotations", false, "メンバー", "Override; Deprecated",
                "付与アノテーション (先頭 @ なし、; 連結)"),
        OVERRIDES("overrides", false, "メソッド", "yes / no",
                "@Override の有無 (注釈ベースの近似)"),
        CALLS("calls", true, "メソッド", "16",
                "外部呼び出し総数 (fan-out)。ネスト/インライン本体も平坦化して計上"),
        CALLEES("callees", false, "メソッド", "JFileChooser; UmlExporter",
                "呼び出し先クラス (解決済み owner 優先、distinct ; 連結)"),
        CALLED_METHODS("calledMethods", false, "メソッド", "isEmpty; showMessageDialog",
                "呼び出すメソッド名 (distinct ; 連結)"),
        SELF_CALLS("selfCalls", true, "メソッド", "2",
                "自クラスのメソッドを呼び出した回数"),
        BRANCHES("branches", true, "メソッド", "6",
                "分岐数 (if + else if + case)"),
        LOOPS("loops", true, "メソッド", "1",
                "ループ数 (for/foreach/while/do-while)"),
        SWITCHES("switches", true, "メソッド", "1",
                "switch ブロック数"),
        TRIES("tries", true, "メソッド", "1",
                "try ブロック数"),
        CATCHES("catches", true, "メソッド", "1",
                "catch 節数"),
        MAX_NESTING("maxNesting", true, "メソッド", "2",
                "制御ブロックの最大ネスト深さ (本体直下=0)"),
        RETURNS("returns", true, "メソッド", "2",
                "return 文の数 (脱出点)"),
        BODY_THROWS("bodyThrows", true, "メソッド", "0",
                "本体内の throw 文の数"),
        THROWS_DECLARED("throwsDeclared", true, "メソッド", "1",
                "throws 節で宣言された例外数"),
        RECURSIVE("recursive", false, "メソッド", "yes / no",
                "自己再帰呼び出しの有無"),
        IO_CATEGORY("ioCategory", false, "メソッド", "UI / DB / none",
                "副作用/IO 分類 (DB/NET/IO/UI/LOG/none、callee FQN 推定)"),
        ROLE("role", false, "メソッド", "factory / getter / ctor",
                "役割推定 (ctor/getter/setter/factory/handler/validator/other)"),
        USED_BY("usedBy", true, "メソッド", "3",
                "解析対象内での被参照メソッド数 (fan-in 近似)"),
        COMMENT("comment", false, "メソッド/フィールド", "合計を返す。",
                "宣言直前の Javadoc / 行コメントの本文 (記号は除去、改行は空白化)");

        private final String header;
        private final boolean numeric;
        private final String appliesTo;
        private final String example;
        private final String legend;

        Col(String header, boolean numeric, String appliesTo, String example, String legend) {
            this.header = header;
            this.numeric = numeric;
            this.appliesTo = appliesTo;
            this.example = example;
            this.legend = legend;
        }

        public String header() {
            return header;
        }

        public boolean numeric() {
            return numeric;
        }

        public String appliesTo() {
            return appliesTo;
        }

        public String example() {
            return example;
        }

        public String legend() {
            return legend;
        }
    }

    private MemberAnalysis() {
    }

    /** 列見出しを定義順で返す。 */
    public static List<String> headers() {
        List<String> out = new ArrayList<>();
        for (Col c : Col.values()) {
            out.add(c.header());
        }
        return out;
    }

    /** 凡例 (列見出し, 対象, 例, 説明) の行を返す。 */
    public static List<String[]> legend() {
        List<String[]> out = new ArrayList<>();
        for (Col c : Col.values()) {
            out.add(new String[] {c.header(), c.appliesTo(), c.example(), c.legend()});
        }
        return out;
    }

    /** 列が数値列かどうか (Excel で数値セルとして書く判定に使う)。 */
    public static boolean isNumeric(int columnIndex) {
        return Col.values()[columnIndex].numeric();
    }

    /**
     * 全クラスのメンバー行を計算して返す。各行は {@link Col} と同じ長さの文字列配列で、
     * 該当なし/値なしのセルは空文字。空セルの見せ方 (CSV は "-"、Excel は数値列空白) は呼び出し側に委ねる。
     */
    public static List<String[]> rows(List<JavaClassInfo> classes) {
        List<JavaClassInfo> sorted = sortedClasses(classes);
        Map<String, Set<String>> fanIn = buildFanIn(sorted);
        List<String[]> out = new ArrayList<>();
        for (JavaClassInfo c : sorted) {
            appendClass(out, c, fanIn);
        }
        return out;
    }

    private static List<JavaClassInfo> sortedClasses(List<JavaClassInfo> classes) {
        List<JavaClassInfo> sorted = new ArrayList<>();
        if (classes != null) {
            for (JavaClassInfo c : classes) {
                if (c != null && c.getKind() != JavaClassInfo.Kind.MODULE) {
                    sorted.add(c);
                }
            }
        }
        sorted.sort(Comparator
                .comparing((JavaClassInfo c) -> nz(c.getSimpleName()))
                .thenComparing(c -> nz(c.getPackageName())));
        return sorted;
    }

    /** クラス単位で 1 回だけ計算する、全行共通のクラス系列セル。 */
    private static final class ClassCells {
        private final String cls;
        private final String pkg;
        private final String kind;
        private final String enclosing;
        private final String ext;
        private final String impl;

        ClassCells(JavaClassInfo c) {
            cls = nz(c.getSimpleName());
            pkg = nz(c.getPackageName());
            kind = kindLabel(c.getKind());
            enclosing = simple(c.getEnclosingClass());
            ext = simple(c.getSuperClass());
            impl = joinSimpleNames(c.getInterfaces());
        }
    }

    private static void appendClass(List<String[]> out, JavaClassInfo c,
                                    Map<String, Set<String>> fanIn) {
        ClassCells cc = new ClassCells(c);
        boolean emitted = false;

        if (c.getKind() == JavaClassInfo.Kind.ENUM) {
            for (String constant : c.getEnumConstants()) {
                out.add(base(cc, "enum-constant", "public", nz(constant)));
                emitted = true;
            }
        }
        for (JavaFieldInfo f : c.getFields()) {
            String[] r = base(cc, "field", visibility(f.getVisibility()), nz(f.getName()));
            set(r, Col.TYPE, nz(f.getType()));
            set(r, Col.MODIFIERS, fieldModifiers(f));
            set(r, Col.ANNOTATIONS, joinAnnotations(f.getAnnotations()));
            set(r, Col.COMMENT, comment(f.getComment()));
            out.add(r);
            emitted = true;
        }
        for (JavaMethodInfo m : c.getMethods()) {
            out.add(methodRow(m, cc, fanIn));
            emitted = true;
        }
        if (!emitted) {
            out.add(base(cc, "(none)", "", ""));
        }
    }

    private static String[] methodRow(JavaMethodInfo m, ClassCells cc,
                                      Map<String, Set<String>> fanIn) {
        String cls = cc.cls;
        String[] r = base(cc, "method", visibility(m.getVisibility()), nz(m.getName()));
        set(r, Col.TYPE, m.isConstructor() ? "" : nz(m.getReturnType()));
        set(r, Col.PARAMS, params(m));
        set(r, Col.MODIFIERS, methodModifiers(m));
        set(r, Col.LINE, lineNo(m));
        set(r, Col.ANNOTATIONS, joinAnnotations(m.getAnnotations()));
        set(r, Col.OVERRIDES, overrides(m));

        List<JavaMethodInfo.Call> calls = m.getCalls();
        set(r, Col.CALLS, Integer.toString(calls.size()));
        set(r, Col.CALLEES, callees(m, cls));
        set(r, Col.CALLED_METHODS, calledMethods(m));
        set(r, Col.SELF_CALLS, Integer.toString(selfCalls(calls, cls)));

        ControlFlow cf = controlFlow(m);
        set(r, Col.BRANCHES, Integer.toString(cf.branches));
        set(r, Col.LOOPS, Integer.toString(cf.loops));
        set(r, Col.SWITCHES, Integer.toString(cf.switches));
        set(r, Col.TRIES, Integer.toString(cf.tries));
        set(r, Col.CATCHES, Integer.toString(cf.catches));
        set(r, Col.MAX_NESTING, Integer.toString(cf.maxNesting));
        set(r, Col.RETURNS, Integer.toString(cf.returns));
        set(r, Col.BODY_THROWS, Integer.toString(cf.bodyThrows));

        set(r, Col.THROWS_DECLARED, Integer.toString(m.getThrowsTypes().size()));
        set(r, Col.RECURSIVE, recursive(m, cls) ? "yes" : "no");
        set(r, Col.IO_CATEGORY, ioCategory(m));
        set(r, Col.ROLE, role(m));
        set(r, Col.USED_BY, Integer.toString(usedBy(fanIn, cls, nz(m.getName()))));
        set(r, Col.COMMENT, comment(m.getComment()));
        return r;
    }

    /** クラス系列セル (全行共通) を埋めた行を作る。 */
    private static String[] base(ClassCells cc, String member, String visibility, String name) {
        String[] r = blankRow();
        set(r, Col.CLASS, cc.cls);
        set(r, Col.PACKAGE, cc.pkg);
        set(r, Col.KIND, cc.kind);
        set(r, Col.MEMBER, member);
        set(r, Col.VISIBILITY, visibility);
        set(r, Col.NAME, name);
        set(r, Col.ENCLOSING, cc.enclosing);
        set(r, Col.EXTENDS, cc.ext);
        set(r, Col.IMPLEMENTS, cc.impl);
        return r;
    }

    private static String[] blankRow() {
        String[] r = new String[Col.values().length];
        for (int i = 0; i < r.length; i++) {
            r[i] = "";
        }
        return r;
    }

    private static void set(String[] row, Col col, String value) {
        row[col.ordinal()] = nz(value);
    }

    // --- 処理解析: 制御フロー ---

    /** 制御フロー集計の入れ物。 */
    private static final class ControlFlow {
        int branches;
        int loops;
        int switches;
        int tries;
        int catches;
        int returns;
        int bodyThrows;
        int maxNesting;
    }

    private static ControlFlow controlFlow(JavaMethodInfo m) {
        ControlFlow cf = new ControlFlow();
        walk(m.getStatements(), 0, cf);
        return cf;
    }

    private static void walk(List<JavaMethodInfo.Statement> stmts, int depth, ControlFlow cf) {
        for (JavaMethodInfo.Statement s : stmts) {
            if (s instanceof JavaMethodInfo.Return) {
                cf.returns++;
            } else if (s instanceof JavaMethodInfo.Throw) {
                cf.bodyThrows++;
            } else if (s instanceof JavaMethodInfo.Block) {
                walkBlock((JavaMethodInfo.Block) s, depth, cf);
            }
        }
    }

    private static void walkBlock(JavaMethodInfo.Block b, int depth, ControlFlow cf) {
        // 凡例は「本体直下=0」と規定するため、トップレベル (depth=0) の制御ブロックは
        // ネスト 0。子ブロックへは depth+1 で降りる。
        int d = depth;
        if (d > cf.maxNesting) {
            cf.maxNesting = d;
        }
        switch (b.getKind()) {
            case WHILE:
            case FOR:
            case DO_WHILE:
                cf.loops++;
                break;
            case SWITCH:
                cf.switches++;
                break;
            case TRY:
                cf.tries++;
                break;
            default:
                break;
        }
        for (JavaMethodInfo.Branch br : b.getBranches()) {
            String t = br.getType();
            if ("if".equals(t) || "else if".equals(t) || "case".equals(t)) {
                cf.branches++;
            } else if ("catch".equals(t)) {
                cf.catches++;
            }
            walk(br.getBody(), d + 1, cf);
        }
    }

    // --- 処理解析: 呼び出し ---

    private static String calledMethods(JavaMethodInfo m) {
        Set<String> names = new LinkedHashSet<>();
        for (JavaMethodInfo.Call call : m.getCalls()) {
            String n = nz(call.getMethodName()).trim();
            if (!n.isEmpty()) {
                names.add(n);
            }
        }
        return String.join("; ", names);
    }

    private static int selfCalls(List<JavaMethodInfo.Call> calls, String cls) {
        int n = 0;
        for (JavaMethodInfo.Call call : calls) {
            if (callOwner(call, cls).equals(cls)) {
                n++;
            }
        }
        return n;
    }

    private static boolean recursive(JavaMethodInfo m, String cls) {
        String name = nz(m.getName());
        for (JavaMethodInfo.Call call : m.getCalls()) {
            if (name.equals(nz(call.getMethodName())) && callOwner(call, cls).equals(cls)) {
                return true;
            }
        }
        return false;
    }

    /** 呼び出しの宛先クラスを単純名で返す。未解決で receiver も this/空なら呼び出し元クラス扱い。 */
    private static String callOwner(JavaMethodInfo.Call call, String callerClassSimple) {
        String r = simple(call.getResolvedOwnerFqn());
        if (!r.isEmpty()) {
            return r;
        }
        String rec = nz(call.getReceiver()).trim();
        if (rec.isEmpty() || "this".equals(rec) || "super".equals(rec)) {
            return callerClassSimple;
        }
        return simple(rec);
    }

    // --- 処理解析: fan-in (被参照) ---

    private static Map<String, Set<String>> buildFanIn(List<JavaClassInfo> classes) {
        Map<String, Set<String>> map = new HashMap<>();
        for (JavaClassInfo c : classes) {
            String cs = simple(c.getSimpleName());
            for (JavaMethodInfo m : c.getMethods()) {
                String callerId = cs + "#" + nz(m.getName()) + "@" + m.getStartLine();
                for (JavaMethodInfo.Call call : m.getCalls()) {
                    String key = callOwner(call, cs) + "#" + nz(call.getMethodName());
                    map.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(callerId);
                }
            }
        }
        return map;
    }

    private static int usedBy(Map<String, Set<String>> fanIn, String cls, String methodName) {
        Set<String> callers = fanIn.get(cls + "#" + methodName);
        return callers == null ? 0 : callers.size();
    }

    // --- 処理解析: 分類・役割の推定 (ヒューリスティック) ---

    private static String ioCategory(JavaMethodInfo m) {
        Set<String> cats = new LinkedHashSet<>();
        for (JavaMethodInfo.Call call : m.getCalls()) {
            String owner = nz(call.getResolvedOwnerFqn()).toLowerCase(Locale.ROOT);
            if (owner.isEmpty()) {
                continue;
            }
            classify(owner, cats);
        }
        return cats.isEmpty() ? "none" : String.join(";", cats);
    }

    private static void classify(String owner, Set<String> cats) {
        if (owner.contains("java.sql") || owner.contains("javax.sql")
                || owner.contains("sqlite") || owner.contains("android.database")
                || owner.contains(".dao") || owner.contains(".room")) {
            cats.add("DB");
        }
        if (owner.contains("java.net") || owner.contains("okhttp")
                || owner.contains("retrofit") || owner.contains("httpclient")
                || owner.contains("socket")) {
            cats.add("NET");
        }
        if (owner.contains("java.io") || owner.contains("java.nio.file")) {
            cats.add("IO");
        }
        if (owner.contains("android.widget") || owner.contains("android.view")
                || owner.contains("javax.swing") || owner.contains("java.awt")) {
            cats.add("UI");
        }
        if (owner.contains("android.util.log") || owner.contains("org.slf4j")
                || owner.contains("java.util.logging") || owner.contains("log4j")) {
            cats.add("LOG");
        }
    }

    private static String role(JavaMethodInfo m) {
        if (m.isConstructor()) {
            return "ctor";
        }
        String name = nz(m.getName());
        int params = m.getParameterTypes().size();
        String ret = nz(m.getReturnType());
        boolean voidRet = ret.isEmpty() || "void".equals(ret);
        if ((startsWith(name, "get") || startsWith(name, "is") || startsWith(name, "has"))
                && params == 0 && !voidRet) {
            return "getter";
        }
        if (startsWith(name, "set") && params == 1 && voidRet) {
            return "setter";
        }
        if (!voidRet && isFactoryName(name)) {
            return "factory";
        }
        if (startsWith(name, "on") || startsWith(name, "handle")) {
            return "handler";
        }
        if (startsWith(name, "validate") || startsWith(name, "check")
                || startsWith(name, "verify") || startsWith(name, "ensure")) {
            return "validator";
        }
        return "other";
    }

    private static boolean isFactoryName(String name) {
        return startsWith(name, "create") || startsWith(name, "build")
                || startsWith(name, "make") || startsWith(name, "new")
                || startsWith(name, "of") || startsWith(name, "from")
                || "getInstance".equals(name) || "valueOf".equals(name);
    }

    /** camelCase の語頭一致 (prefix の直後が大文字、もしくは完全一致)。 */
    private static boolean startsWith(String name, String prefix) {
        if (!name.startsWith(prefix)) {
            return false;
        }
        if (name.length() == prefix.length()) {
            return true;
        }
        return Character.isUpperCase(name.charAt(prefix.length()));
    }

    // --- 構造系セル ---

    private static String lineNo(JavaMethodInfo m) {
        int ln = m.getStartLine();
        return ln > 0 ? Integer.toString(ln) : "";
    }

    private static String overrides(JavaMethodInfo m) {
        for (String a : m.getAnnotations()) {
            String head = nz(a);
            int paren = head.indexOf('(');
            if (paren >= 0) {
                head = head.substring(0, paren);
            }
            if (simple(head).equals("Override")) {
                return "yes";
            }
        }
        return "no";
    }

    private static String callees(JavaMethodInfo m, String cls) {
        Set<String> seen = new LinkedHashSet<>();
        for (JavaMethodInfo.Call call : m.getCalls()) {
            String owner = nz(call.getResolvedOwnerFqn());
            if (owner.isEmpty()) {
                owner = nz(call.getReceiver());
            }
            String s = simple(owner);
            if (!s.isEmpty() && !"this".equals(s) && !"super".equals(s)) {
                seen.add(s);
            }
        }
        return String.join("; ", seen);
    }

    private static String params(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        List<String> types = m.getParameterTypes();
        List<String> names = m.getParameterNames();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String name = i < names.size() ? names.get(i) : "";
            if (name != null && !name.isEmpty()) {
                sb.append(name).append(": ");
            }
            sb.append(types.get(i) == null ? "?" : types.get(i));
        }
        return sb.toString();
    }

    private static String joinSimpleNames(List<String> names) {
        if (names == null) {
            return "";
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String n : names) {
            String s = simple(n);
            if (!s.isEmpty()) {
                seen.add(s);
            }
        }
        return String.join("; ", seen);
    }

    private static String joinAnnotations(List<String> annotations) {
        if (annotations == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String a : annotations) {
            String v = nz(a).trim();
            if (v.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(v);
        }
        return sb.toString();
    }

    private static String fieldModifiers(JavaFieldInfo f) {
        StringBuilder sb = new StringBuilder();
        if (f.isStatic()) {
            sb.append("static");
        }
        if (f.isFinal()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("final");
        }
        return sb.toString();
    }

    private static String methodModifiers(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        if (m.isStatic()) {
            sb.append("static");
        }
        if (m.isAbstract()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("abstract");
        }
        return sb.toString();
    }

    private static String visibility(Visibility v) {
        return (v == null ? Visibility.PACKAGE : v).name().toLowerCase(Locale.ROOT);
    }

    private static String kindLabel(JavaClassInfo.Kind k) {
        if (k == null) {
            return "class";
        }
        switch (k) {
            case INTERFACE:
                return "interface";
            case ENUM:
                return "enum";
            case ANNOTATION:
                return "@interface";
            case AIDL_INTERFACE:
                return "aidl";
            case RECORD:
                return "record";
            default:
                return "class";
        }
    }

    /** FQN / ジェネリクス付き型名から simple 名を取り出す (a.b.C&lt;D&gt; -&gt; C)。 */
    private static String simple(String name) {
        String v = nz(name).trim();
        int lt = v.indexOf('<');
        if (lt >= 0) {
            v = v.substring(0, lt);
        }
        int dot = v.lastIndexOf('.');
        if (dot >= 0) {
            v = v.substring(dot + 1);
        }
        return v;
    }

    /** 宣言直前コメント本文を 1 行に正規化する (記号は抽出時に除去済み、改行/連続空白は空白化)。 */
    private static String comment(String raw) {
        return nz(raw).replaceAll("\\s+", " ").trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
