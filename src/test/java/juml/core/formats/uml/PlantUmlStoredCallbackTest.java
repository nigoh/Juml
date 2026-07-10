// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 変数・フィールドに「格納」されたコールバック (ラムダ / 匿名クラス) の
 * シーケンス図・アクティビティ図での可視化を検証するテスト。
 *
 * <ul>
 *   <li>ローカル変数コールバックは宣言位置ではなく実際の呼び出し位置で展開する
 *       (時系列を偽らない)</li>
 *   <li>アクティビティ図でもフィールド格納コールバックの本体を展開する
 *       (シーケンス図との非対称の解消)</li>
 *   <li>静的に解決できないコールバック (setter 経由等) は無言で消さず
 *       「未展開」note で可視化する</li>
 * </ul>
 */
public class PlantUmlStoredCallbackTest {

    // -------------------------------------------------------------------------
    // シーケンス図
    // -------------------------------------------------------------------------

    @Test
    public void seq_localVarLambda_expandsAtCallSiteNotDeclaration() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + " first.stepOne();"
                        + " Runnable x = () -> svc.work();"
                        + " second.stepTwo();"
                        + " x.run();"
                        + " third.stepThree(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("svc.work()"));
        // 展開位置は宣言位置 (stepOne と stepTwo の間) ではなく呼び出し位置
        // (stepTwo と stepThree の間) であること。
        int work = puml.indexOf("svc.work()");
        assertTrue("展開は stepTwo() より後のはず:\n" + puml,
                work > puml.indexOf("stepTwo()"));
        assertTrue("展開は stepThree() より前のはず:\n" + puml,
                work < puml.indexOf("stepThree()"));
    }

    @Test
    public void seq_localVarLambda_notInvokedDirectly_expandsAtDeclaration() {
        // 他メソッドへ渡すだけで直接呼ばれないローカルコールバックは
        // 従来どおり宣言位置で展開して取りこぼさない。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { Runnable x = () -> svc.work(); queue.submit(x); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("svc.work()"));
    }

    @Test
    public void seq_setterStoredCallback_showsUnresolvedNote() {
        // setter 経由で格納されたコールバックは静的に実体を解決できない。
        // 無言で消えるのではなく「未展開」note で可視化されること。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { private Runnable cb;"
                        + " void setCb(Runnable r) { this.cb = r; }"
                        + " void fire() { cb.run(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "fire", null);
        assertTrue("未解決コールバックの note が出るはず:\n" + puml,
                puml.contains("実行時に格納されるため未展開"));
    }

    @Test
    public void seq_unresolvedNote_canBeDisabled() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.showUnresolvedCallbackNote = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { private Runnable cb;"
                        + " void setCb(Runnable r) { this.cb = r; }"
                        + " void fire() { cb.run(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "fire", o);
        assertFalse(puml, puml.contains("未展開"));
    }

    @Test
    public void seq_ordinaryCall_doesNotGetFalsePositiveNote() {
        // 関数型らしくない通常のフィールド呼び出しにはノイズ note を出さない。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { private Service svc; void go() { svc.doWork(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "go", null);
        assertFalse(puml, puml.contains("未展開"));
    }

    @Test
    public void seq_projectInterfaceListenerField_showsUnresolvedNote() {
        // 解析済みインタフェース型のリスナーフィールド越しの呼び出し (実装は実行時注入)
        // も note で可視化されること。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface OnClickListener { void onClick(); }\n"
                        + "class Button { private OnClickListener listener;"
                        + " void click() { listener.onClick(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "Button", "click", null);
        assertTrue("リスナー呼び出しに note が出るはず:\n" + puml,
                puml.contains("実行時に格納されるため未展開"));
    }

    // -------------------------------------------------------------------------
    // アクティビティ図
    // -------------------------------------------------------------------------

    @Test
    public void act_fieldInitializerLambda_isExpanded() {
        // シーケンス図では従来から展開されるフィールド格納コールバックが、
        // アクティビティ図でも partition として展開されること (非対称の解消)。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Bar { Runnable r = () -> mWorker.execute();"
                        + " void kick() { r.run(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "Bar", "kick", null);
        assertTrue("フィールドのラムダ本体が展開されるはず:\n" + puml,
                puml.contains("mWorker.execute()"));
        assertTrue(puml, puml.contains("partition"));
    }

    @Test
    public void act_constructorAssignedListener_isExpanded() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Baz { private OnClickListener listener;\n"
                        + "  Baz() { this.listener = new OnClickListener() {"
                        + " public void onClick(View v) { mService.start(); } }; }\n"
                        + "  void register() { listener.onClick(null); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "Baz", "register", null);
        assertTrue("コンストラクタ代入のリスナー本体が展開されるはず:\n" + puml,
                puml.contains("mService.start()"));
    }

    @Test
    public void act_localVarLambda_expandsAtCallSite() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + " Runnable x = () -> svc.work();"
                        + " second.stepTwo();"
                        + " x.run(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("svc.work()"));
        assertTrue("展開は stepTwo() より後 (= 呼び出し位置) のはず:\n" + puml,
                puml.indexOf("svc.work()") > puml.indexOf("stepTwo()"));
    }

    @Test
    public void act_setterStoredCallback_showsUnresolvedNote() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { private Runnable cb;"
                        + " void setCb(Runnable r) { this.cb = r; }"
                        + " void fire() { cb.run(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "fire", null);
        assertTrue("未解決コールバックの note が出るはず:\n" + puml,
                puml.contains("実行時に格納されるため未展開"));
    }

    @Test
    public void act_expandDisabled_keepsPlainActionOnly() {
        PlantUmlActivityDiagram.Options o = new PlantUmlActivityDiagram.Options();
        o.expandInlineCallbacks = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Bar { Runnable r = () -> mWorker.execute();"
                        + " void kick() { r.run(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "Bar", "kick", o);
        assertFalse("展開オフでは partition を出さない:\n" + puml,
                puml.contains("mWorker.execute()"));
        assertTrue(puml, puml.contains(":r.run();"));
    }

    @Test
    public void act_recursiveStoredCallback_terminatesWithNote() {
        // 自己再帰するフィールドコールバック (cb の本体が cb.run() を呼ぶ) でも
        // 無限展開せずに recursive note で打ち切ること。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class R { Runnable cb = () -> cb.run(); void go() { cb.run(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "R", "go", null);
        assertTrue("再帰打ち切り note が出るはず:\n" + puml,
                puml.contains("recursive call"));
    }

    // -------------------------------------------------------------------------
    // 実レンダリング (note 入り出力が PlantUML 構文エラーにならないこと)
    // -------------------------------------------------------------------------

    @Test
    public void seq_unresolvedNoteDiagram_rendersWithoutSyntaxError() throws IOException {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { private Runnable cb;"
                        + " void setCb(Runnable r) { this.cb = r; }"
                        + " void fire() { cb.run(); } }");
        assertNoPlantUmlSyntaxError(
                PlantUmlSequenceDiagram.generate(infos, "A", "fire", null));
    }

    @Test
    public void act_storedCallbackDiagrams_renderWithoutSyntaxError() throws IOException {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Bar { Runnable r = () -> mWorker.execute();"
                        + " private Runnable cb;"
                        + " void setCb(Runnable x) { this.cb = x; }"
                        + " void kick() { r.run(); cb.run(); } }");
        assertNoPlantUmlSyntaxError(
                PlantUmlActivityDiagram.generate(infos, "Bar", "kick", null));
    }

    /** 生成 PlantUML を実レンダリングし、PlantUML の構文エラー画像にならないことを確認する。 */
    private static void assertNoPlantUmlSyntaxError(String puml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(puml, out);
        String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("PlantUML reported a syntax error for:\n" + puml,
                svg.contains("Syntax Error"));
    }
}
