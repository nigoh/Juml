// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import juml.core.formats.java.AndroidProjectScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AAOS の VHAL (Vehicle Hardware Abstraction Layer) アクセスをソースから検出する。
 *
 * <p>対象 API は {@code android.car.hardware.property.CarPropertyManager} の
 * {@code getProperty} / {@code setProperty} / {@code registerCallback} /
 * {@code unregisterCallback}。受信側変数名 (フィールド) のヒューリスティック検出と
 * 第 1 引数のトークン抽出で「どこからどの Property を読み書き/購読しているか」を返す。</p>
 *
 * <p>呼び出し検出は字句レベルではなく正規表現で行う。ReferenceIndex は
 * 呼び出しの引数までは保持しないため、Property ID の特定にはソーステキストの
 * 再走査が必要なため。</p>
 *
 * <p>制約:</p>
 * <ul>
 *   <li>{@code carPropertyManager.getProperty(...)} 形式の単純な呼び出しのみ検出</li>
 *   <li>レシーバ名 (フィールド名) のヒューリスティックを使う: 名前に
 *       {@code propertyManager} / {@code carPropertyManager} / {@code mCarPropertyManager}
 *       が含まれる、または直前のフィールド宣言で型に {@code CarPropertyManager}
 *       が含まれることを文字列マッチで確認</li>
 *   <li>{@code Stream}/メソッドチェーンの中間呼び出しは取りこぼす可能性あり</li>
 * </ul>
 */
public final class VhalAnalyzer {

    /** メソッド名 → アクセス種別。 */
    private static final Map<String, VhalAccess.Kind> VHAL_METHODS;
    static {
        Map<String, VhalAccess.Kind> m = new LinkedHashMap<>();
        m.put("getProperty", VhalAccess.Kind.GET);
        m.put("getBooleanProperty", VhalAccess.Kind.GET);
        m.put("getIntProperty", VhalAccess.Kind.GET);
        m.put("getFloatProperty", VhalAccess.Kind.GET);
        m.put("getIntArrayProperty", VhalAccess.Kind.GET);
        m.put("setProperty", VhalAccess.Kind.SET);
        m.put("setBooleanProperty", VhalAccess.Kind.SET);
        m.put("setIntProperty", VhalAccess.Kind.SET);
        m.put("setFloatProperty", VhalAccess.Kind.SET);
        m.put("registerCallback", VhalAccess.Kind.SUBSCRIBE);
        m.put("subscribePropertyEvents", VhalAccess.Kind.SUBSCRIBE);
        m.put("unregisterCallback", VhalAccess.Kind.UNSUBSCRIBE);
        m.put("unsubscribePropertyEvents", VhalAccess.Kind.UNSUBSCRIBE);
        VHAL_METHODS = Collections.unmodifiableMap(m);
    }

    /**
     * {@code carPropertyManager.getProperty(...)} 形式を捕まえる正規表現。
     * グループ 1: receiver 名, グループ 2: メソッド名。引数列は {@code (} 以降を
     * 対応括弧まで手動走査で取り出す（{@code getProperty(getPropId(X), area)} のような
     * ネスト括弧で {@code [^)]*} だと途中で切れるため）。
     */
    private static final Pattern CALL_PATTERN = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.\\s*"
                    + "(getProperty|getBooleanProperty|getIntProperty|getFloatProperty|"
                    + "getIntArrayProperty|setProperty|setBooleanProperty|setIntProperty|"
                    + "setFloatProperty|registerCallback|subscribePropertyEvents|"
                    + "unregisterCallback|unsubscribePropertyEvents)\\s*\\(");

    /** {@code CarPropertyManager} 型のフィールドを検出するパターン。 */
    private static final Pattern FIELD_DECL_PATTERN = Pattern.compile(
            "(?:^|[\\s;{,])(?:private|protected|public)?\\s*(?:final|static)*\\s*"
                    + "CarPropertyManager\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    /**
     * Kotlin の {@code val/var name: CarPropertyManager} 型フィールドパターン。
     * {@code lateinit var}, アノテーション前置、private/protected/internal 修飾子も許容。
     */
    private static final Pattern KOTLIN_FIELD_DECL_PATTERN = Pattern.compile(
            "(?:^|[\\s;{,])(?:private|protected|public|internal)?\\s*"
                    + "(?:lateinit\\s+|const\\s+)?(?:val|var)\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*CarPropertyManager\\b");

    /** Kotlin の {@code fun name(...): ReturnType { ... }} 形式。 */
    private static final Pattern KOTLIN_FUN_DECL_PATTERN = Pattern.compile(
            "\\bfun\\s+(?:<[^>]+>\\s+)?"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\([^)]*\\)"
                    + "(?:\\s*:\\s*[^{=]+)?\\s*\\{");

    /** {@code CarPropertyManager.METHOD} のような static 呼出パターン (単語境界付き)。 */
    private static final Pattern STATIC_CALL_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_$])CarPropertyManager\\s*\\.\\s*("
                    + "getProperty|setProperty|registerCallback|unregisterCallback)\\s*\\(");

    /** メソッド名規則 (大まかな抽出): {@code [modifiers] returnType name(...)}。 */
    private static final Pattern METHOD_DECL_PATTERN = Pattern.compile(
            "(?:public|protected|private|static|final|synchronized|abstract|@\\w+\\s*)*\\s*"
                    + "[A-Za-z_$<][A-Za-z0-9_$<>,\\s\\[\\]\\?]*\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\([^)]*\\)\\s*(?:throws[^{]*)?\\{");

    /** プロジェクト内の Java / Kotlin ソースをスキャンして全 VHAL アクセスを返す。
     * テストソースは既定で除外する。 */
    public List<VhalAccess> analyzeProject(File projectRoot) throws IOException {
        return analyzeProject(projectRoot, false);
    }

    /** {@code includeTests} でテストソースを含めるかを制御できる版。 */
    public List<VhalAccess> analyzeProject(File projectRoot, boolean includeTests)
            throws IOException {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        opts.includeAidl = false;
        opts.includeKotlin = true;
        opts.includeTests = includeTests;
        List<File> files = AndroidProjectScanner.scan(projectRoot, opts);
        List<VhalAccess> all = new ArrayList<>();
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".java") && !name.endsWith(".kt")) {
                continue;
            }
            String src;
            try {
                src = AndroidProjectScanner.readFile(f);
            } catch (IOException ex) {
                continue;
            }
            all.addAll(analyzeSource(src, f.getPath()));
        }
        return all;
    }

    /** 単一ソース文字列から VHAL アクセスを抽出する (テスト用)。 */
    public List<VhalAccess> analyzeSource(String src, String filePath) {
        List<VhalAccess> out = new ArrayList<>();
        if (src == null || src.isEmpty()) {
            return out;
        }
        String packageName = readPackage(src);
        String className = readPrimaryClassName(src);
        String fqn = packageName.isEmpty() ? className
                : packageName + "." + className;

        // CarPropertyManager 型のフィールド名を収集 (Java + Kotlin)
        java.util.Set<String> mgrFields = new java.util.LinkedHashSet<>();
        Matcher fm = FIELD_DECL_PATTERN.matcher(src);
        while (fm.find()) {
            mgrFields.add(fm.group(1));
        }
        Matcher kfm = KOTLIN_FIELD_DECL_PATTERN.matcher(src);
        while (kfm.find()) {
            mgrFields.add(kfm.group(1));
        }

        // メソッド宣言の開始位置を収集 (Java と Kotlin 両方)
        List<int[]> methodSpans = new ArrayList<>();
        List<String> methodNames = new ArrayList<>();
        Matcher mm = METHOD_DECL_PATTERN.matcher(src);
        while (mm.find()) {
            int braceStart = mm.end() - 1;
            int braceEnd = findMatchingBrace(src, braceStart);
            if (braceEnd > braceStart) {
                methodSpans.add(new int[]{braceStart, braceEnd});
                methodNames.add(mm.group(1));
            }
        }
        Matcher km = KOTLIN_FUN_DECL_PATTERN.matcher(src);
        while (km.find()) {
            int braceStart = km.end() - 1;
            int braceEnd = findMatchingBrace(src, braceStart);
            if (braceEnd > braceStart) {
                methodSpans.add(new int[]{braceStart, braceEnd});
                methodNames.add(km.group(1));
            }
        }

        // 通常の receiver 呼び出し
        // receiver が "CarPropertyManager" の呼び出しは CALL_PATTERN と
        // STATIC_CALL_PATTERN の両方にマッチするため、CALL 側で処理済みの開始位置を
        // 記録し、STATIC 側で二重計上しないようにする。
        java.util.Set<Integer> handledStarts = new java.util.HashSet<>();
        Matcher m = CALL_PATTERN.matcher(src);
        while (m.find()) {
            String receiver = m.group(1);
            String method = m.group(2);
            // CarPropertyManager 型らしくない receiver はスキップ
            if (!looksLikeCarPropertyManager(receiver, mgrFields)) {
                continue;
            }
            VhalAccess.Kind kind = VHAL_METHODS.get(method);
            if (kind == null) {
                continue;
            }
            String args = balancedArgs(src, m.end() - 1);
            if (args == null) {
                continue;
            }
            int line = lineOf(src, m.start());
            String callerMethod = enclosingMethodName(m.start(), methodSpans, methodNames);
            out.add(buildAccess(fqn, callerMethod, filePath, line, kind, method, args));
            handledStarts.add(m.start());
        }

        // static 呼び出し
        Matcher s = STATIC_CALL_PATTERN.matcher(src);
        while (s.find()) {
            // CALL_PATTERN で既に計上済みの位置は二重計上しない。
            if (handledStarts.contains(s.start())) {
                continue;
            }
            String method = s.group(1);
            VhalAccess.Kind kind = VHAL_METHODS.get(method);
            if (kind == null) {
                continue;
            }
            String args = balancedArgs(src, s.end() - 1);
            if (args == null) {
                continue;
            }
            int line = lineOf(src, s.start());
            String callerMethod = enclosingMethodName(s.start(), methodSpans, methodNames);
            out.add(buildAccess(fqn, callerMethod, filePath, line, kind, method, args));
        }
        return out;
    }

    /**
     * 引数列から Property/Area トークンを取り出して {@link VhalAccess} を構築する。
     * {@code registerCallback(callback, propertyId, rate)} のように Property が
     * 第 1 引数でないメソッドもあるので、メソッド名で引数位置を切り替える。
     */
    private static VhalAccess buildAccess(String callerFqn, String callerMethod,
                                            String filePath, int line,
                                            VhalAccess.Kind kind, String method,
                                            String argsRaw) {
        String[] parts = splitArgs(argsRaw);
        int propIdx;
        int areaIdx;
        switch (method) {
            case "registerCallback":
            case "subscribePropertyEvents":
            case "unregisterCallback":
            case "unsubscribePropertyEvents":
                // 第 0 引数はコールバック、第 1 引数が Property
                propIdx = 1;
                areaIdx = 2;
                break;
            default:
                // getProperty / setProperty: 第 0 引数が Property、第 1 引数が Area
                propIdx = 0;
                areaIdx = 1;
                break;
        }
        String propertyToken = parts.length > propIdx ? parts[propIdx] : "";
        String areaToken = parts.length > areaIdx ? parts[areaIdx] : "";
        return new VhalAccess(callerFqn, callerMethod, filePath, line, kind,
                propertyToken, areaToken);
    }

    /** 名前ヒューリスティック: {@code CarPropertyManager} 型らしい receiver か判定。 */
    private static boolean looksLikeCarPropertyManager(String receiver,
                                                         java.util.Set<String> mgrFields) {
        if (mgrFields.contains(receiver)) {
            return true;
        }
        String lower = receiver.toLowerCase();
        return lower.contains("carpropertymanager") || lower.contains("propertymanager")
                || lower.equals("mcpm") || lower.equals("cpm");
    }

    private static String readPackage(String src) {
        // Java は ; で終わる、Kotlin は ; なし。両方を許容。
        Matcher m = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;?\\s*$",
                Pattern.MULTILINE).matcher(src);
        return m.find() ? m.group(1) : "";
    }

    private static String readPrimaryClassName(String src) {
        // Java: class/interface/enum, Kotlin: class/interface/object/enum class/data class
        Matcher m = Pattern.compile(
                "(?:public|protected|private|internal|final|abstract|open|sealed|data|inner)?"
                        + "\\s*(?:enum\\s+|annotation\\s+)?(?:class|interface|object|enum)"
                        + "\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(src);
        return m.find() ? m.group(1) : "Unknown";
    }

    private static int lineOf(String src, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < src.length(); i++) {
            if (src.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** offset の位置を囲むメソッドの名前を返す (見つからなければ空)。 */
    private static String enclosingMethodName(int offset, List<int[]> spans,
                                                List<String> names) {
        String found = "";
        for (int i = 0; i < spans.size(); i++) {
            int[] sp = spans.get(i);
            if (offset >= sp[0] && offset <= sp[1]) {
                found = names.get(i);
            }
        }
        return found;
    }

    /** 引数列 {@code "FOO, BAR, baz"} をネストした括弧を尊重して分割する。 */
    private static String[] splitArgs(String args) {
        if (args == null || args.isEmpty()) {
            return new String[0];
        }
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(' || c == '[' || c == '{' || c == '<') {
                depth++;
                cur.append(c);
            } else if (c == ')' || c == ']' || c == '}' || c == '>') {
                if (depth > 0) depth--;
                cur.append(c);
            } else if (c == ',' && depth == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString().trim());
        }
        return out.toArray(new String[0]);
    }

    /**
     * {@code openParen} 位置の {@code '('} に対応する閉じ {@code ')'} までの内側の引数文字列を返す。
     * 括弧のネスト ({@code getProperty(getPropId(X), area)}) を考慮する。対応する閉じ括弧が
     * 見つからない場合は {@code null}。
     */
    private static String balancedArgs(String src, int openParen) {
        if (openParen < 0 || openParen >= src.length() || src.charAt(openParen) != '(') {
            return null;
        }
        int depth = 1;
        for (int i = openParen + 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return src.substring(openParen + 1, i);
                }
            }
        }
        return null;
    }

    /** open 位置の {@code '{'} に対応する閉じ {@code '}'} のオフセットを返す。 */
    private static int findMatchingBrace(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '{') {
            return open;
        }
        int depth = 1;
        for (int i = open + 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return src.length();
    }
}
