// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jetpack Compose Navigation の遷移を Kotlin ソースから検出する。
 *
 * <p>検出パターン:</p>
 * <ul>
 *   <li>{@code NavHost(...) { ... }} ブロック内の {@code composable("route") { ... }}
 *       宣言 → "route" を画面ノードとして登録</li>
 *   <li>{@code navController.navigate("route")} 形式の呼び出し → 呼び出し元 Composable から
 *       "route" への遷移エッジを作成</li>
 *   <li>{@code popBackStack()} は遷移として記録しない (戻り操作)</li>
 * </ul>
 *
 * <p>呼び出し元の Composable 関数名は {@code @Composable fun Name(...)} を走査して特定。
 * navigate 呼び出しの位置を囲む最も内側の Composable 関数本体が caller となる。</p>
 *
 * <p>Intent ベース遷移検出 ({@link IntentNavigationDetector}) と独立に動作するので、
 * Compose と View ベース Activity 起動が混在するアプリでも両方の遷移をまとめて出せる。</p>
 */
public final class ComposeNavScanner {

    /** {@code composable("route") { ... }} - グループ 1: route 文字列。 */
    private static final Pattern COMPOSABLE_ROUTE = Pattern.compile(
            "\\bcomposable\\s*\\(\\s*(?:route\\s*=\\s*)?\"([^\"]+)\"");

    /** {@code navController.navigate("route")} - グループ 1: route 文字列。 */
    private static final Pattern NAVIGATE_CALL = Pattern.compile(
            "\\b(?:[A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.\\s*navigate\\s*\\(\\s*\"([^\"]+)\"");

    /**
     * {@code navigate(Screen.Detail.route)} 等の非文字列 route - グループ 1: route 式。
     * {@code R.id.*} (Jetpack Nav。IntentNavigationDetector が担当) と関数呼び出し形は除外する。
     */
    private static final Pattern NAVIGATE_NONSTRING = Pattern.compile(
            "\\bnavigate\\s*\\(\\s*(?!R\\.id)"
                    + "([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z0-9_$]+)*)\\s*(?=[,)])");

    /** {@code @Composable fun Name(...)} - グループ 1: composable 関数名。 */
    private static final Pattern COMPOSABLE_FUN = Pattern.compile(
            "@Composable[\\s\\n]+(?:public\\s+|private\\s+|internal\\s+)?fun\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");

    /** {@code NavHost(navController, startDestination = "home") { ... }} - グループ 1: 開始 route。 */
    private static final Pattern NAVHOST_START_DEST = Pattern.compile(
            "\\bNavHost\\s*\\([^)]*startDestination\\s*=\\s*\"([^\"]+)\"");

    /** Compose 遷移走査結果。 */
    public static final class Result {
        private final List<ScreenTransition> transitions = new ArrayList<>();
        private final List<String> routes = new ArrayList<>();
        private String startDestination = "";

        public List<ScreenTransition> getTransitions() { return transitions; }
        /** {@code composable("...")} で宣言された全 route。 */
        public List<String> getRoutes() { return routes; }
        /** {@code NavHost(startDestination = "...")} で指定された初期 route。 */
        public String getStartDestination() { return startDestination; }
    }

    /** 1 つの Kotlin ソースから Compose 遷移情報を抽出する。 */
    public Result analyzeSource(String src, String filePath) {
        Result r = new Result();
        if (src == null || src.isEmpty()) return r;

        // start destination
        Matcher nm = NAVHOST_START_DEST.matcher(src);
        if (nm.find()) {
            r.startDestination = nm.group(1);
        }

        // 全 composable("route") を登録
        Matcher cm = COMPOSABLE_ROUTE.matcher(src);
        while (cm.find()) {
            String route = cm.group(1);
            if (!r.routes.contains(route)) {
                r.routes.add(route);
            }
        }

        // Composable 関数の span を収集 (呼び出し元の判定用)
        List<int[]> funSpans = new ArrayList<>();
        List<String> funNames = new ArrayList<>();
        Matcher fm = COMPOSABLE_FUN.matcher(src);
        while (fm.find()) {
            int parenStart = src.indexOf('(', fm.end() - 1);
            if (parenStart < 0) continue;
            int parenEnd = matchParen(src, parenStart);
            int braceStart = src.indexOf('{', parenEnd);
            if (braceStart < 0) continue;
            int braceEnd = matchBrace(src, braceStart);
            if (braceEnd > braceStart) {
                funSpans.add(new int[]{braceStart, braceEnd});
                funNames.add(fm.group(1));
            }
        }

        // composable("route") { ... } の本体 span も追加 (composable ラムダ内の navigate 用)
        Map<int[], String> composableSpanRoute = new LinkedHashMap<>();
        Matcher cm2 = COMPOSABLE_ROUTE.matcher(src);
        while (cm2.find()) {
            String route = cm2.group(1);
            // composable( の開き括弧から括弧平衡で対応する閉じ括弧を求める。
            // arguments = listOf(navArgument(...) { ... }) のような入れ子括弧/ラムダを
            // 本体ブロックと誤認しないようにするため (単純な最初の ) 検索では不可)。
            int parenStart = src.indexOf('(', cm2.start());
            if (parenStart < 0) continue;
            int closeParen = matchParen(src, parenStart);
            int braceStart = src.indexOf('{', closeParen);
            if (braceStart < 0) continue;
            int braceEnd = matchBrace(src, braceStart);
            if (braceEnd > braceStart) {
                int[] span = new int[]{braceStart, braceEnd};
                composableSpanRoute.put(span, route);
            }
        }

        // 呼び出し元クラスの FQN を推定 (package + 最初に出る class/object)
        NavCtx ctx = new NavCtx(inferFqn(src), src, filePath,
                composableSpanRoute, funSpans, funNames);

        // navigate("route") (文字列) を走査
        Matcher gm = NAVIGATE_CALL.matcher(src);
        while (gm.find()) {
            emitNavigate(r, ctx, gm.group(1), gm.start());
        }
        // navigate(Screen.Detail.route) (非文字列) を走査
        Matcher nm2 = NAVIGATE_NONSTRING.matcher(src);
        while (nm2.find()) {
            emitNavigate(r, ctx, routeNameOf(nm2.group(1)), nm2.start());
        }
        return r;
    }

    /** navigate 呼び出しの遷移元解決に必要なファイル単位の文脈。 */
    private static final class NavCtx {
        final String fqn;
        final String src;
        final String filePath;
        final Map<int[], String> composableSpanRoute;
        final List<int[]> funSpans;
        final List<String> funNames;

        NavCtx(String fqn, String src, String filePath,
               Map<int[], String> composableSpanRoute,
               List<int[]> funSpans, List<String> funNames) {
            this.fqn = fqn;
            this.src = src;
            this.filePath = filePath;
            this.composableSpanRoute = composableSpanRoute;
            this.funSpans = funSpans;
            this.funNames = funNames;
        }
    }

    /** route 式 ({@code Screen.Detail.route} 等) から表示用のroute名を取り出す。 */
    private static String routeNameOf(String expr) {
        String e = expr;
        // 末尾の .route / .name / .destination を取り除く
        for (String suffix : new String[]{".route", ".name", ".destination"}) {
            if (e.endsWith(suffix)) {
                e = e.substring(0, e.length() - suffix.length());
                break;
            }
        }
        int dot = e.lastIndexOf('.');
        return dot < 0 ? e : e.substring(dot + 1);
    }

    private static void emitNavigate(Result r, NavCtx ctx, String targetRoute, int start) {
        if (targetRoute == null || targetRoute.isEmpty()) {
            return;
        }
        int line = lineOf(ctx.src, start);
        // 呼び出し元の判定:
        // 1. composable("route") { ... } ブロック内なら、その route が "from"
        // 2. そうでなければ最も内側の @Composable fun 名を "from"
        String fromLabel = "";
        for (Map.Entry<int[], String> e : ctx.composableSpanRoute.entrySet()) {
            int[] sp = e.getKey();
            if (start >= sp[0] && start <= sp[1]) {
                fromLabel = e.getValue();
            }
        }
        if (fromLabel.isEmpty()) {
            int[] enclosing = innermostSpan(start, ctx.funSpans);
            if (enclosing != null) {
                int idx = ctx.funSpans.indexOf(enclosing);
                fromLabel = ctx.funNames.get(idx);
            }
        }
        if (fromLabel.isEmpty()) {
            fromLabel = "(unknown)";
        }
        r.transitions.add(new ScreenTransition(
                ctx.fqn, fromLabel, targetRoute,
                ctx.filePath, line, ScreenTransition.Kind.COMPOSE_NAVIGATE));
    }

    private static String inferFqn(String src) {
        Matcher pm = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;?\\s*$")
                .matcher(src);
        String pkg = pm.find() ? pm.group(1) : "";
        Matcher cm = Pattern.compile(
                "(?:public|protected|private|internal|final|abstract|open|sealed|data|inner)?"
                        + "\\s*(?:enum\\s+|annotation\\s+)?(?:class|interface|object)"
                        + "\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(src);
        String name = cm.find() ? cm.group(1) : "NavGraph";
        return pkg.isEmpty() ? name : pkg + "." + name;
    }

    private static int[] innermostSpan(int offset, List<int[]> spans) {
        int[] best = null;
        int bestLen = Integer.MAX_VALUE;
        for (int[] s : spans) {
            if (offset >= s[0] && offset <= s[1]) {
                int len = s[1] - s[0];
                if (len < bestLen) {
                    bestLen = len;
                    best = s;
                }
            }
        }
        return best;
    }

    private static int matchBrace(String src, int open) {
        return matchBalance(src, open, '{', '}');
    }

    private static int matchParen(String src, int open) {
        return matchBalance(src, open, '(', ')');
    }

    private static int matchBalance(String src, int open, char openCh, char closeCh) {
        if (open < 0 || open >= src.length() || src.charAt(open) != openCh) return open;
        int depth = 1;
        boolean inString = false;
        for (int i = open + 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < src.length()) { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == openCh) depth++;
            else if (c == closeCh) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return src.length();
    }

    private static int lineOf(String src, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }
}
