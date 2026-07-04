// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.funcdiff;

import juml.core.formats.uml.JavaMethodInfo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link MarkdownMethodDiffReport} の非公開メソッド {@code callLabel} の回帰テスト。
 *
 * <p>修正前は {@code base.replace("()", "(" + firstArg + ")")} で組み立てていたため、
 * receiver 文字列に {@code "()"} が含まれる (例: {@code getConfig().load(...)} のような
 * チェーン呼び出し) と全出現が置換され、receiver 側にまで第1引数が誤注入されてラベルが
 * 壊れていた (例: {@code getConfig(KEY).load(KEY)})。現在は呼び出し部だけを直接組み立てる
 * ため、receiver はそのまま・method 側にだけ引数が付くことを {@code callLabel} が唯一
 * 呼ばれる公開 API である {@link MarkdownMethodDiffReport#render} の出力経由で保証する。</p>
 */
public class MarkdownMethodDiffReportCallLabelTest {

    /** name のメソッドを 1 件の呼び出し (receiver/methodName/firstArgLabel) だけで作る。 */
    private static JavaMethodInfo methodWithSingleCall(
            String methodName, String receiver, String calleeName, String firstArgLabel) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(methodName);
        JavaMethodInfo.Call call = new JavaMethodInfo.Call(receiver, calleeName);
        call.setFirstArgLabel(firstArgLabel);
        m.getStatements().add(call);
        return m;
    }

    private static MethodDiffAnalyzer.MethodSpec spec(String file, String cls, String method) {
        return new MethodDiffAnalyzer.MethodSpec(file, cls, method);
    }

    // -------------------------------------------------------------------------
    // ハッピーパス兼バグ回帰: receiver に "()" を含むチェーン呼び出し
    // -------------------------------------------------------------------------

    @Test
    public void callLabel_receiverWithParens_doesNotInjectArgIntoReceiver() {
        JavaMethodInfo methodA = methodWithSingleCall("go", "getConfig()", "load", "KEY");
        JavaMethodInfo methodB = methodWithSingleCall("go", "getConfig()", "load", "KEY");

        MethodDiffAnalyzer.DiffResult result = MethodDiffAnalyzer.analyze(
                methodA, spec("A.java", "A", "go"),
                methodB, spec("B.java", "B", "go"));
        String md = MarkdownMethodDiffReport.render(result);

        assertTrue("method 側にだけ第1引数が付いた正しいラベルが出るはず: " + md,
                md.contains("getConfig().load(KEY)"));
        assertFalse("旧実装のバグ (receiver 側にも引数が注入される) が再発していないはず: " + md,
                md.contains("getConfig(KEY)"));
    }

    /**
     * receiver 内に "()" が複数回現れる、さらに壊れやすいチェーン呼び出しでも
     * method 側にしか引数が付かないことを確認する（境界: 複数出現）。
     */
    @Test
    public void callLabel_receiverWithMultipleParenPairs_onlyMethodGetsArg() {
        JavaMethodInfo methodA =
                methodWithSingleCall("go", "cache.get().getConfig()", "load", "X");
        JavaMethodInfo methodB =
                methodWithSingleCall("go", "cache.get().getConfig()", "load", "X");

        MethodDiffAnalyzer.DiffResult result = MethodDiffAnalyzer.analyze(
                methodA, spec("A.java", "A", "go"),
                methodB, spec("B.java", "B", "go"));
        String md = MarkdownMethodDiffReport.render(result);

        assertTrue("receiver 中の複数の () はそのまま維持されるはず: " + md,
                md.contains("cache.get().getConfig().load(X)"));
        assertFalse("receiver 中の最初の () に誤注入されていないはず: " + md,
                md.contains("cache.get(X)"));
        assertFalse("receiver 中の 2 番目の () にも誤注入されていないはず: " + md,
                md.contains("getConfig(X)"));
    }

    // -------------------------------------------------------------------------
    // 境界: 第1引数ラベルが無い（空文字扱い）呼び出し
    // -------------------------------------------------------------------------

    @Test
    public void callLabel_noFirstArgLabel_rendersEmptyParens() {
        JavaMethodInfo methodA = methodWithSingleCall("go", "svc()", "run", null);
        JavaMethodInfo methodB = methodWithSingleCall("go", "svc()", "run", null);

        MethodDiffAnalyzer.DiffResult result = MethodDiffAnalyzer.analyze(
                methodA, spec("A.java", "A", "go"),
                methodB, spec("B.java", "B", "go"));
        String md = MarkdownMethodDiffReport.render(result);

        assertTrue("第1引数が無ければ空括弧のはず: " + md,
                md.contains("svc().run()"));
    }

    // -------------------------------------------------------------------------
    // 異常系寄り: receiver が null (レシーバー省略呼び出し) でも "." が付与されない
    // -------------------------------------------------------------------------

    @Test
    public void callLabel_nullReceiver_omitsLeadingDot() {
        JavaMethodInfo methodA = methodWithSingleCall("go", null, "run", "ARG");
        JavaMethodInfo methodB = methodWithSingleCall("go", null, "run", "ARG");

        MethodDiffAnalyzer.DiffResult result = MethodDiffAnalyzer.analyze(
                methodA, spec("A.java", "A", "go"),
                methodB, spec("B.java", "B", "go"));
        String md = MarkdownMethodDiffReport.render(result);

        assertTrue("receiver が無ければ先頭ドット無しの呼び出しだけが出るはず: " + md,
                md.contains("| `run(ARG)` | `run(ARG)` |"));
    }

    /**
     * ONLY_A (B に無い呼び出し) の詳細セクションでも callLabel が使われる
     * ({@link MarkdownMethodDiffReport#render} 内 appendDiffDetail 経由)。
     * receiver に "()" を含む呼び出しが A だけに存在するケースで同様に検証する。
     */
    @Test
    public void callLabel_onlyAWithParenReceiver_detailSectionNotCorrupted() {
        JavaMethodInfo methodA = methodWithSingleCall("go", "getConfig()", "load", "KEY");
        JavaMethodInfo methodB = new JavaMethodInfo();
        methodB.setName("go");

        MethodDiffAnalyzer.DiffResult result = MethodDiffAnalyzer.analyze(
                methodA, spec("A.java", "A", "go"),
                methodB, spec("B.java", "B", "go"));
        String md = MarkdownMethodDiffReport.render(result);

        assertTrue("差分詳細の A のみセクションにも正しいラベルが出るはず: " + md,
                md.contains("getConfig().load(KEY)"));
        assertFalse("差分詳細でも receiver 側への誤注入が無いはず: " + md,
                md.contains("getConfig(KEY)"));
    }
}
