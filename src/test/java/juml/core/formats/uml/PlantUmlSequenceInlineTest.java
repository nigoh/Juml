// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * フィールド初期化子 (匿名クラス / ラムダ) およびメソッド引数に直接渡された
 * コールバック/リスナー本体がシーケンス図に展開されることを確認するテスト。
 */
public class PlantUmlSequenceInlineTest {

    @Test
    public void testFieldListenerBodyIsExpandedInSequenceDiagram() {
        String src = ""
                + "class Foo {\n"
                + "  private OnClickListener listener = new OnClickListener() {\n"
                + "    public void onClick(View v) { mService.start(); }\n"
                + "  };\n"
                + "  private IService mService;\n"
                + "  void register() { listener.onClick(null); }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Foo", "register", null);
        // フィールド型に解決された participant がアクティベートされ、本体内の
        // mService.start() が展開されていること (mService のフィールド型 IService に解決)
        assertTrue("should activate listener (field type): \n" + diagram,
                diagram.contains("activate OnClickListener"));
        assertTrue("should call IService.start in inline body: \n" + diagram,
                diagram.contains(" -> IService: IService.start()"));
    }

    @Test
    public void testLambdaListenerBodyIsExpanded() {
        String src = ""
                + "class Bar {\n"
                + "  Runnable r = () -> mWorker.execute();\n"
                + "  private Executor mWorker;\n"
                + "  void kick() { r.run(); }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Bar", "kick", null);
        assertTrue("lambda body should expand: \n" + diagram,
                diagram.contains(" -> Executor: Executor.execute()"));
        assertTrue("Runnable participant should be activated: \n" + diagram,
                diagram.contains("activate Runnable"));
    }

    @Test
    public void testConstructorAssignmentListenerExpanded() {
        // コンストラクタ内で代入したリスナーも、後で listener.onClick() を呼んだ際に
        // シーケンス図で展開できること
        String src = ""
                + "class Baz {\n"
                + "  private OnClickListener listener;\n"
                + "  Baz() {\n"
                + "    this.listener = new OnClickListener() {\n"
                + "      public void onClick(View v) { mService.start(); }\n"
                + "    };\n"
                + "  }\n"
                + "  private IService mService;\n"
                + "  void register() { listener.onClick(null); }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Baz", "register", null);
        assertTrue("constructor-assigned listener should expand: \n" + diagram,
                diagram.contains(" -> IService: IService.start()"));
    }

    @Test
    public void testFieldMethodReferenceExpanded() {
        // メソッド参照によるフィールド初期化も展開できること
        String src = ""
                + "class Qux {\n"
                + "  Runnable r = Worker::tick;\n"
                + "  void kick() { r.run(); }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Qux", "kick", null);
        assertTrue("method reference body should expand: \n" + diagram,
                diagram.contains("Worker.tick()"));
    }

    // ── 引数渡しコールバック (call.getInlineMethods) の展開テスト ──────────────────

    @Test
    public void testAnonymousClassArgExpandedWithOwnerClass() {
        // button.setOnClickListener(new OnClickListener() { onClick(){...} }) の形で
        // 引数に渡した匿名クラスの本体がシーケンス図に展開され、
        // 参加者名が「定義元クラス$メソッド名」になること
        String src = ""
                + "class MyActivity {\n"
                + "  private IService mService;\n"
                + "  void onCreate() {\n"
                + "    button.setOnClickListener(new OnClickListener() {\n"
                + "      public void onClick(View v) { mService.start(); }\n"
                + "    });\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "MyActivity", "onCreate", null);
        // 定義元クラス$コールバックメソッド名 の参加者が生成されること
        assertTrue("inline participant should be MyActivity$onClick: \n" + diagram,
                diagram.contains("\"MyActivity$onClick\""));
        // <<inline>> ステレオタイプが付くこと
        assertTrue("inline stereotype should appear: \n" + diagram,
                diagram.contains("<<inline>>"));
        // コールバック本体内の mService.start() が展開されること
        assertTrue("callback body should be expanded: \n" + diagram,
                diagram.contains("IService") && diagram.contains("start()"));
    }

    @Test
    public void testLambdaArgExpandedWithOwnerClass() {
        // setOnClickListener(v -> mService.start()) のラムダ引数が展開されること
        String src = ""
                + "class MyFragment {\n"
                + "  private IService mService;\n"
                + "  void onViewCreated() {\n"
                + "    btn.setOnClickListener(v -> mService.start());\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "MyFragment", "onViewCreated", null);
        assertTrue("inline participant should contain MyFragment$: \n" + diagram,
                diagram.contains("\"MyFragment$"));
        assertTrue("<<inline>> stereotype should appear: \n" + diagram,
                diagram.contains("<<inline>>"));
        assertTrue("lambda body mService.start() should be expanded: \n" + diagram,
                diagram.contains("start()"));
    }

    // ── forEach / stream チェーン ───────────────────────────────────────────────

    @Test
    public void testForEachLambdaWrappedInLoop() {
        // list.forEach(item -> ...) が loop ブロックで囲まれること
        String src = ""
                + "class Processor {\n"
                + "  private IService mService;\n"
                + "  void process() {\n"
                + "    items.forEach(item -> mService.handle(item));\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Processor", "process", null);
        assertTrue("forEach should be wrapped in loop block: \n" + diagram,
                diagram.contains("loop forEach"));
        assertTrue("loop block should be closed: \n" + diagram,
                diagram.contains("loop forEach\n") && diagram.contains("end\n"));
    }

    @Test
    public void testForEachParticipantUsesCallingMethodName() {
        // forEach の参加者名が OwnerClass$forEach になること (汎用 SAM 名 accept は使わない)
        String src = ""
                + "class Handler {\n"
                + "  private IService mService;\n"
                + "  void run() {\n"
                + "    list.forEach(item -> mService.exec(item));\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Handler", "run", null);
        assertTrue("participant should be Handler$forEach not Handler$accept: \n" + diagram,
                diagram.contains("\"Handler$forEach\""));
        assertFalse("generic SAM name 'accept' should not appear as participant: \n" + diagram,
                diagram.contains("Handler$accept"));
    }

    @Test
    public void testStreamIntermediateOpsSupressed() {
        // stream() や filter() (コールバックなし) はシーケンス図から除外されること
        String src = ""
                + "class Worker {\n"
                + "  private IService mService;\n"
                + "  void work() {\n"
                + "    list.stream().sorted().forEach(item -> mService.handle(item));\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Worker", "work", null);
        // stream() と sorted() は矢印として出ないこと
        assertFalse("stream() should be suppressed: \n" + diagram,
                diagram.contains(": stream()") || diagram.contains(": list.stream()"));
        assertFalse("sorted() should be suppressed: \n" + diagram,
                diagram.contains(": sorted()"));
        // forEach のコールバック本体は展開されること
        assertTrue("forEach body should be expanded: \n" + diagram,
                diagram.contains("loop forEach"));
    }

    @Test
    public void testStreamFilterWithLambdaNotSuppressed() {
        // filter(lambda) のようにコールバックがある場合は除外しないこと
        String src = ""
                + "class Checker {\n"
                + "  private IService mService;\n"
                + "  void check() {\n"
                + "    list.stream().filter(item -> mService.isValid(item));\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Checker", "check", null);
        // filter のコールバック展開で <<inline>> 参加者が生成されること
        assertTrue("filter lambda should be expanded: \n" + diagram,
                diagram.contains("\"Checker$filter\""));
    }

    @Test
    public void testInlineParticipantShowsOwnerClass() {
        // 参加者名に「定義元クラス$」プレフィックスがつくことを確認する
        String src = ""
                + "class Presenter {\n"
                + "  private IRepo mRepo;\n"
                + "  void load() {\n"
                + "    executor.execute(new Runnable() {\n"
                + "      public void run() { mRepo.fetch(); }\n"
                + "    });\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Presenter", "load", null);
        assertTrue("participant should start with Presenter$: \n" + diagram,
                diagram.contains("\"Presenter$run\"") || diagram.contains("\"Presenter$"));
        // 凡例に <<inline>> の説明が出ること
        assertTrue("legend should mention <<inline>>: \n" + diagram,
                diagram.contains("<<inline>>"));
    }

    // ── CompoundButton.OnCheckedChangeListener ────────────────────────────────

    @Test
    public void testSetOnCheckedChangeListenerLambdaExpanded() {
        // setOnCheckedChangeListener(lambda) の SAM メソッド名が onCheckedChanged に解決され
        // 参加者名が OwnerClass$onCheckedChanged になること
        String src = ""
                + "class SettingsFragment {\n"
                + "  private IService mService;\n"
                + "  void onViewCreated() {\n"
                + "    toggle.setOnCheckedChangeListener((b, checked) -> mService.update(checked));\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "SettingsFragment", "onViewCreated", null);
        assertTrue("participant should be SettingsFragment$onCheckedChanged: \n" + diagram,
                diagram.contains("\"SettingsFragment$onCheckedChanged\""));
        assertTrue("<<inline>> stereotype should appear: \n" + diagram,
                diagram.contains("<<inline>>"));
        assertTrue("callback body mService.update() should be expanded: \n" + diagram,
                diagram.contains("update("));
    }

    @Test
    public void testSetOnCheckedChangeListenerAnonymousClassExpanded() {
        // setOnCheckedChangeListener(new OnCheckedChangeListener(){...}) の匿名クラスが展開されること
        String src = ""
                + "class SwitchActivity {\n"
                + "  private IService mService;\n"
                + "  void onCreate() {\n"
                + "    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {\n"
                + "      public void onCheckedChanged(CompoundButton b, boolean checked) {\n"
                + "        mService.toggle(checked);\n"
                + "      }\n"
                + "    });\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "SwitchActivity", "onCreate", null);
        assertTrue("inline participant should contain SwitchActivity$: \n" + diagram,
                diagram.contains("\"SwitchActivity$"));
        assertTrue("<<inline>> stereotype should appear: \n" + diagram,
                diagram.contains("<<inline>>"));
        assertTrue("callback body mService.toggle() should be expanded: \n" + diagram,
                diagram.contains("toggle("));
    }
}
