// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.funcdiff;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 2つの {@link JavaMethodInfo} の呼び出し列を比較し、差分を解析する。
 *
 * <p>類似度の根拠として以下の3指標を計算する:</p>
 * <ul>
 *   <li>LCS Similarity = LCS_length / max(|A|, |B|)  — 順序保持の共通部分割合</li>
 *   <li>Edit Distance (Levenshtein) = min(insertions + deletions + substitutions)</li>
 *   <li>Jaccard = |A∩B| / |A∪B|  — 集合ベース（順序無視・重複排除）</li>
 * </ul>
 *
 * <p>各マッチペアには信頼度スコア (0.0〜1.0) を付与する:</p>
 * <pre>
 *   confidence = receiver_score * 0.4 + firstArg_score * 0.3 + position_score * 0.3
 * </pre>
 */
public final class MethodDiffAnalyzer {

    private MethodDiffAnalyzer() {
    }

    // -------------------------------------------------------------------------
    // データモデル
    // -------------------------------------------------------------------------

    /** 比較対象のメソッドを特定するための情報。 */
    public static final class MethodSpec {
        public final String filePath;
        public final String className;   // null = クラス名省略
        public final String methodName;

        public MethodSpec(String filePath, String className, String methodName) {
            this.filePath = filePath;
            this.className = className;
            this.methodName = methodName;
        }

        /** "ClassName.methodName" または "methodName" の形式で返す。 */
        public String label() {
            return className != null ? className + "." + methodName : methodName;
        }
    }

    /** マッチ結果の種別。 */
    public enum MatchKind {
        MATCH,    // メソッド名・receiver・firstArg がすべて一致
        PARTIAL,  // メソッド名は一致するが receiver または firstArg が異なる
        ONLY_A,   // A にのみ存在
        ONLY_B    // B にのみ存在
    }

    /** LCSアライン後の1行分の比較結果。 */
    public static final class DiffRow {
        public final MatchKind kind;
        public final JavaMethodInfo.Call callA;       // ONLY_B のとき null
        public final JavaMethodInfo.Call callB;       // ONLY_A のとき null
        public final double confidence;               // ONLY_A/ONLY_B は -1（N/A）
        public final String detail;                   // PARTIAL 時の差分説明

        DiffRow(MatchKind kind,
                JavaMethodInfo.Call callA, JavaMethodInfo.Call callB,
                double confidence, String detail) {
            this.kind = kind;
            this.callA = callA;
            this.callB = callB;
            this.confidence = confidence;
            this.detail = detail;
        }
    }

    /** 3指標の計算結果。 */
    public static final class SimilarityMetrics {
        /** LCS の長さ。 */
        public final int lcsLen;
        /** LCS_length / max(|A|, |B|)。 */
        public final double lcsSimilarity;
        /** Levenshtein 編集距離。 */
        public final int editDistance;
        /** 1 − editDistance / max(|A|, |B|)。 */
        public final double normalizedEditSimilarity;
        /** |A∩B| / |A∪B|（methodName 集合ベース）。 */
        public final double jaccard;

        SimilarityMetrics(int lcsLen, double lcsSimilarity,
                          int editDistance, double normalizedEditSimilarity,
                          double jaccard) {
            this.lcsLen = lcsLen;
            this.lcsSimilarity = lcsSimilarity;
            this.editDistance = editDistance;
            this.normalizedEditSimilarity = normalizedEditSimilarity;
            this.jaccard = jaccard;
        }
    }

    /** analyze() の戻り値。 */
    public static final class DiffResult {
        public final MethodSpec specA;
        public final MethodSpec specB;
        public final int totalCallsA;
        public final int totalCallsB;
        public final List<DiffRow> rows;
        public final SimilarityMetrics metrics;
        public final int matchCount;
        public final int partialCount;
        public final int onlyACount;
        public final int onlyBCount;
        /** マッチ/PARTIAL ペアの平均信頼度。ペアなしなら 0.0。 */
        public final double avgConfidence;

        DiffResult(MethodSpec specA, MethodSpec specB,
                   int totalCallsA, int totalCallsB,
                   List<DiffRow> rows, SimilarityMetrics metrics) {
            this.specA = specA;
            this.specB = specB;
            this.totalCallsA = totalCallsA;
            this.totalCallsB = totalCallsB;
            this.rows = rows;
            this.metrics = metrics;

            int mc = 0, pc = 0, oa = 0, ob = 0;
            double confSum = 0.0;
            int confCount = 0;
            for (DiffRow r : rows) {
                switch (r.kind) {
                    case MATCH:   mc++; confSum += r.confidence; confCount++; break;
                    case PARTIAL: pc++; confSum += r.confidence; confCount++; break;
                    case ONLY_A:  oa++; break;
                    case ONLY_B:  ob++; break;
                    default: break;
                }
            }
            this.matchCount = mc;
            this.partialCount = pc;
            this.onlyACount = oa;
            this.onlyBCount = ob;
            this.avgConfidence = confCount > 0 ? confSum / confCount : 0.0;
        }
    }

    // -------------------------------------------------------------------------
    // 公開 API
    // -------------------------------------------------------------------------

    /**
     * "filePath::ClassName.methodName" または "filePath::methodName" をパースする。
     * {@code ::} がない場合は filePath のみとして扱い、methodName を要求する形式でないと
     * {@link IllegalArgumentException} をスローする。
     */
    public static MethodSpec parseSpec(String spec) {
        if (spec == null || spec.isEmpty()) {
            throw new IllegalArgumentException("spec must not be empty");
        }
        int sep = spec.lastIndexOf("::");
        if (sep < 0) {
            throw new IllegalArgumentException(
                    "spec must be \"filePath::methodName\" or \"filePath::ClassName.methodName\": "
                            + spec);
        }
        String filePath = spec.substring(0, sep).trim();
        String methodPart = spec.substring(sep + 2).trim();
        if (methodPart.isEmpty()) {
            throw new IllegalArgumentException("methodName must not be empty in spec: " + spec);
        }

        // "ClassName.methodName" または "methodName" を判別する。
        // findMethod は className を単純名 (getSimpleName) と厳密一致で照合するため、
        // 修飾名やネストクラス (com.example.Foo.bar / Outer.Inner.method) が来ても
        // className は必ず「最後のドット前セグメントの単純名」(Foo / Inner) へ畳む。
        // 以前は maybeClass をそのまま className にしていたため、修飾名では
        // getSimpleName と一致せずメソッドが無言で見つからなかった。
        String className = null;
        String methodName = methodPart;
        int dot = methodPart.lastIndexOf('.');
        if (dot > 0 && dot < methodPart.length() - 1) {
            className = simpleName(methodPart.substring(0, dot));
            methodName = methodPart.substring(dot + 1);
        }
        return new MethodSpec(filePath, className, methodName);
    }

    /** 修飾名 (a.b.C) の最後のセグメント (C) を返す。ドットが無ければそのまま。 */
    private static String simpleName(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot >= 0 ? qualified.substring(dot + 1) : qualified;
    }

    /**
     * クラスリストから spec に合致する {@link JavaMethodInfo} を返す。
     * 見つからない場合は null を返す。
     * 同名メソッドが複数あれば最初に見つかったものを返す。
     */
    public static JavaMethodInfo findMethod(List<JavaClassInfo> classes, MethodSpec spec) {
        for (JavaClassInfo ci : classes) {
            if (spec.className != null
                    && !ci.getSimpleName().equals(spec.className)) {
                continue;
            }
            for (JavaMethodInfo m : ci.getMethods()) {
                if (m.getName().equals(spec.methodName)) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * 2つのメソッドを比較して {@link DiffResult} を返す。
     * どちらかが null のときも空の DiffResult を返す。
     */
    public static DiffResult analyze(JavaMethodInfo methodA, MethodSpec specA,
                                     JavaMethodInfo methodB, MethodSpec specB) {
        List<JavaMethodInfo.Call> callsA =
                methodA != null ? methodA.getCalls() : new ArrayList<>();
        List<JavaMethodInfo.Call> callsB =
                methodB != null ? methodB.getCalls() : new ArrayList<>();

        int lenA = callsA.size();
        int lenB = callsB.size();
        int maxLen = Math.max(lenA, lenB);

        // 3指標を計算
        int lcsLen = computeLcsLen(callsA, callsB);
        int editDist = computeLevenshtein(callsA, callsB);
        double jaccard = computeJaccard(callsA, callsB);

        double lcsSim = maxLen == 0 ? 1.0 : (double) lcsLen / maxLen;
        double normEdit = maxLen == 0 ? 1.0 : 1.0 - (double) editDist / maxLen;

        SimilarityMetrics metrics = new SimilarityMetrics(
                lcsLen, lcsSim, editDist, normEdit, jaccard);

        // LCS アラインによる差分行を生成
        List<DiffRow> rows = lcsAlign(callsA, callsB, lenA, lenB);

        return new DiffResult(specA, specB, lenA, lenB, rows, metrics);
    }

    // -------------------------------------------------------------------------
    // 内部実装
    // -------------------------------------------------------------------------

    /** LCS の長さのみを計算する（O(N*M) DP）。 */
    static int computeLcsLen(List<JavaMethodInfo.Call> a, List<JavaMethodInfo.Call> b) {
        int n = a.size(), m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (sameMethodName(a.get(i - 1), b.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[n][m];
    }

    /**
     * Levenshtein 編集距離を計算する（O(N*M) DP）。
     * 挿入/削除/置換それぞれコスト1。
     */
    static int computeLevenshtein(List<JavaMethodInfo.Call> a, List<JavaMethodInfo.Call> b) {
        int n = a.size(), m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (sameMethodName(a.get(i - 1), b.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                                   Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[n][m];
    }

    /** Jaccard 係数を計算する（methodName 集合ベース）。 */
    static double computeJaccard(List<JavaMethodInfo.Call> a, List<JavaMethodInfo.Call> b) {
        Set<String> setA = new HashSet<>();
        for (JavaMethodInfo.Call c : a) setA.add(c.getMethodName());
        Set<String> setB = new HashSet<>();
        for (JavaMethodInfo.Call c : b) setB.add(c.getMethodName());

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        if (union.isEmpty()) return 1.0;

        Set<String> inter = new HashSet<>(setA);
        inter.retainAll(setB);
        return (double) inter.size() / union.size();
    }

    /**
     * LCS DP テーブルを構築してバックトラックし、
     * {@link DiffRow} のリストを生成する。
     */
    private static List<DiffRow> lcsAlign(List<JavaMethodInfo.Call> callsA,
                                           List<JavaMethodInfo.Call> callsB,
                                           int lenA, int lenB) {
        int n = lenA, m = lenB;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (sameMethodName(callsA.get(i - 1), callsB.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // バックトラック
        List<DiffRow> rows = new ArrayList<>();
        int i = n, j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0
                    && sameMethodName(callsA.get(i - 1), callsB.get(j - 1))) {
                JavaMethodInfo.Call ca = callsA.get(i - 1);
                JavaMethodInfo.Call cb = callsB.get(j - 1);
                StringBuilder detail = new StringBuilder();
                MatchKind kind = compareCall(ca, cb, detail);
                double conf = computeConfidence(ca, cb, i - 1, j - 1, n, m);
                rows.add(new DiffRow(kind, ca, cb, conf,
                        detail.length() > 0 ? detail.toString() : null));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                rows.add(new DiffRow(MatchKind.ONLY_B, null, callsB.get(j - 1), -1.0, null));
                j--;
            } else {
                rows.add(new DiffRow(MatchKind.ONLY_A, callsA.get(i - 1), null, -1.0, null));
                i--;
            }
        }

        // バックトラックの結果は逆順
        List<DiffRow> result = new ArrayList<>(rows.size());
        for (int k = rows.size() - 1; k >= 0; k--) {
            result.add(rows.get(k));
        }
        return result;
    }

    /**
     * 2つのCallが MATCH か PARTIAL かを判定する。
     * 前提: methodName は一致済み。
     */
    private static MatchKind compareCall(JavaMethodInfo.Call a, JavaMethodInfo.Call b,
                                          StringBuilder detail) {
        boolean receiverOk = normalizeReceiver(a.getReceiver())
                .equals(normalizeReceiver(b.getReceiver()));
        boolean firstArgOk = Objects.equals(a.getFirstArgLabel(), b.getFirstArgLabel());

        if (receiverOk && firstArgOk) {
            return MatchKind.MATCH;
        }
        // PARTIAL: 差分の説明を構築
        if (!receiverOk) {
            detail.append("レシーバー: ")
                  .append(nullStr(a.getReceiver()))
                  .append(" → ")
                  .append(nullStr(b.getReceiver()));
        }
        if (!firstArgOk) {
            if (detail.length() > 0) detail.append(" / ");
            detail.append("第1引数: ")
                  .append(nullStr(a.getFirstArgLabel()))
                  .append(" → ")
                  .append(nullStr(b.getFirstArgLabel()));
        }
        return MatchKind.PARTIAL;
    }

    /**
     * マッチペアの信頼度スコアを計算する。
     * <pre>
     *   confidence = receiver_score * 0.4 + firstArg_score * 0.3 + position_score * 0.3
     * </pre>
     */
    private static double computeConfidence(JavaMethodInfo.Call ca, JavaMethodInfo.Call cb,
                                             int idxA, int idxB, int lenA, int lenB) {
        // receiver_score
        double receiverScore = normalizeReceiver(ca.getReceiver())
                .equals(normalizeReceiver(cb.getReceiver())) ? 1.0 : 0.0;

        // firstArg_score
        String fa = ca.getFirstArgLabel();
        String fb = cb.getFirstArgLabel();
        double firstArgScore;
        if (Objects.equals(fa, fb)) {
            firstArgScore = 1.0;
        } else if (fa == null || fb == null) {
            firstArgScore = 0.5;
        } else {
            firstArgScore = 0.0;
        }

        // position_score
        double posA = lenA > 1 ? (double) idxA / (lenA - 1) : 0.0;
        double posB = lenB > 1 ? (double) idxB / (lenB - 1) : 0.0;
        double posScore = 1.0 - Math.abs(posA - posB);

        return receiverScore * 0.4 + firstArgScore * 0.3 + posScore * 0.3;
    }

    private static boolean sameMethodName(JavaMethodInfo.Call a, JavaMethodInfo.Call b) {
        return Objects.equals(a.getMethodName(), b.getMethodName());
    }

    private static String normalizeReceiver(String r) {
        if (r == null || r.equals("this")) return "";
        return r;
    }

    private static String nullStr(String s) {
        return s != null ? s : "(null)";
    }
}
