// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link JavaStructureExtractor} のフィールド初期化子 (匿名クラス / ラムダ) 抽出機能の
 * ユニットテスト。
 *
 * <p>{@link JavaFieldInfo#getInlineMethods()} に正しくメソッド本体が取り込まれ、
 * シーケンス図がフィールド経由のリスナー呼び出しを展開できることを担保する。</p>
 */
public class JavaStructureExtractorInlineTest {

    @Test
    public void testAnonymousClassListenerFieldCapturesBody() {
        String src = ""
                + "package com.x;\n"
                + "class Foo {\n"
                + "  private OnClickListener listener = new OnClickListener() {\n"
                + "    public void onClick(View v) { mService.start(); log.d(); }\n"
                + "  };\n"
                + "  private Object mService;\n"
                + "  private Object log;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        assertEquals(1, cs.size());
        JavaClassInfo c = cs.get(0);
        // 匿名クラスの内側を別 ClassInfo として results に混入させないこと
        JavaFieldInfo listenerField = findField(c, "listener");
        assertNotNull("listener field should be captured", listenerField);
        assertEquals(1, listenerField.getInlineMethods().size());
        JavaMethodInfo onClick = listenerField.getInlineMethods().get(0);
        assertEquals("onClick", onClick.getName());
        // 本体内に 2 つの呼びだしがあること (mService.start, log.d)
        List<JavaMethodInfo.Call> calls = onClick.getCalls();
        assertEquals(2, calls.size());
        assertEquals("start", calls.get(0).getMethodName());
        assertEquals("mService", calls.get(0).getReceiver());
        assertEquals("d", calls.get(1).getMethodName());
    }

    @Test
    public void testLocalVarInitializerCallsAreCaptured() {
        // String s = svc.getName(); のような最も一般的なローカル変数初期化子の
        // 呼び出しが、シーケンス図・コールグラフの元となる getCalls() に現れること。
        String src = ""
                + "class A {\n"
                + "  void run() {\n"
                + "    String s = svc.getName();\n"
                + "    int n = a.b().c();\n"
                + "    use(s);\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaMethodInfo run = cs.get(0).getMethods().stream()
                .filter(m -> "run".equals(m.getName())).findFirst().orElseThrow(AssertionError::new);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (JavaMethodInfo.Call call : run.getCalls()) {
            names.add(call.getMethodName());
        }
        // 初期化子の呼び出し (getName, b, c) と通常文の use が実行順で並ぶ
        assertEquals(java.util.Arrays.asList("getName", "b", "c", "use"), names);
    }

    @Test
    public void testLambdaRunnableResolvesToRun() {
        String src = ""
                + "class A {\n"
                + "  Runnable r = () -> { doX(); };\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo r = findField(cs.get(0), "r");
        assertNotNull(r);
        assertEquals(1, r.getInlineMethods().size());
        assertEquals("run", r.getInlineMethods().get(0).getName());
        assertEquals("doX", r.getInlineMethods().get(0).getCalls().get(0).getMethodName());
    }

    @Test
    public void testLambdaOnClickListenerResolvesToOnClick() {
        // フィールド型は `View.OnClickListener` のような dot 形式でも解決できること
        String src = ""
                + "class A {\n"
                + "  View.OnClickListener l = v -> log.d(\"x\");\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo l = findField(cs.get(0), "l");
        assertNotNull(l);
        assertEquals(1, l.getInlineMethods().size());
        assertEquals("onClick", l.getInlineMethods().get(0).getName());
        assertEquals("d", l.getInlineMethods().get(0).getCalls().get(0).getMethodName());
    }

    @Test
    public void testLambdaExpressionBodyDoesNotEatFieldTerminator() {
        // expression-bodied lambda の後に別フィールドが続くケースで、後続フィールドが
        // 正しくパースされること (`;` の読み損ねが起きないこと)。
        String src = ""
                + "class A {\n"
                + "  Runnable r = () -> doX();\n"
                + "  int next;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaClassInfo c = cs.get(0);
        // フィールドが 2 つともパースされていること
        assertEquals(2, c.getFields().size());
        JavaFieldInfo r = findField(c, "r");
        JavaFieldInfo next = findField(c, "next");
        assertNotNull(r);
        assertNotNull(next);
        assertEquals("int", next.getType());
    }

    @Test
    public void testLambdaBlockBodyDoesNotLeakStatements() {
        // 通常のフィールド (int x = 1;) は inlineMethods を持たないこと
        String src = ""
                + "class A {\n"
                + "  int x = 1;\n"
                + "  String s = \"hello\";\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaClassInfo c = cs.get(0);
        for (JavaFieldInfo f : c.getFields()) {
            assertTrue("plain field should not have inlineMethods: " + f.getName(),
                    f.getInlineMethods().isEmpty());
        }
    }

    @Test
    public void testUnknownSamFallbackToInlineMarker() {
        // 既知 SAM マップにない型のラムダは `<inline>` で fallback すること
        String src = ""
                + "class A {\n"
                + "  MyCustomFunctional fn = () -> compute();\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo fn = findField(cs.get(0), "fn");
        assertNotNull(fn);
        assertEquals(1, fn.getInlineMethods().size());
        assertEquals("<inline>", fn.getInlineMethods().get(0).getName());
    }

    @Test
    public void testAnonymousClassWithMultipleMethods() {
        String src = ""
                + "class A {\n"
                + "  Adapter ad = new Adapter() {\n"
                + "    public void onCreate() { setupA(); }\n"
                + "    public void onDestroy() { teardownB(); }\n"
                + "  };\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo ad = findField(cs.get(0), "ad");
        assertNotNull(ad);
        assertEquals(2, ad.getInlineMethods().size());
        assertEquals("onCreate", ad.getInlineMethods().get(0).getName());
        assertEquals("onDestroy", ad.getInlineMethods().get(1).getName());
    }

    @Test
    public void testPlainArrayInitializerDoesNotMisfire() {
        // 配列初期化子は inline 化しないこと
        String src = "class A { int[] xs = new int[]{1, 2, 3}; }";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo xs = findField(cs.get(0), "xs");
        assertNotNull(xs);
        assertTrue(xs.getInlineMethods().isEmpty());
        assertFalse(cs.get(0).getFields().isEmpty());
    }

    @Test
    public void testMethodReferenceFieldInitCaptured() {
        // メソッド参照 Foo::bar はフィールド初期化子として inlineMethods に取り込む
        String src = ""
                + "class A {\n"
                + "  Runnable r = Foo::bar;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo r = findField(cs.get(0), "r");
        assertNotNull(r);
        assertEquals(1, r.getInlineMethods().size());
        JavaMethodInfo m = r.getInlineMethods().get(0);
        assertEquals("run", m.getName());
        assertEquals(1, m.getCalls().size());
        assertEquals("bar", m.getCalls().get(0).getMethodName());
        assertEquals("Foo", m.getCalls().get(0).getReceiver());
    }

    @Test
    public void testInstanceMethodReferenceFieldInitCaptured() {
        // ドット付き receiver もサポート: a.b.c::method
        String src = ""
                + "class A {\n"
                + "  Runnable r = a.b.c::method;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo r = findField(cs.get(0), "r");
        assertNotNull(r);
        assertEquals(1, r.getInlineMethods().size());
        assertEquals("a.b.c", r.getInlineMethods().get(0).getCalls().get(0).getReceiver());
    }

    @Test
    public void testFqnTypeMethodReferenceFoldsToSimpleName() {
        // 型 FQN のメソッド参照 receiver は単純名に畳む (com.example.Mapper::convert → Mapper)
        String src = ""
                + "class A {\n"
                + "  Runnable r = com.example.Mapper::convert;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo r = findField(cs.get(0), "r");
        assertNotNull(r);
        assertEquals("Mapper", r.getInlineMethods().get(0).getCalls().get(0).getReceiver());
    }

    @Test
    public void testGenericMethodReferenceStripsTypeArguments() {
        // ジェネリクス付きメソッド参照 receiver は型引数を除去する (Foo<Bar>::baz → Foo)
        String src = ""
                + "class A {\n"
                + "  Runnable r = Foo<Bar>::baz;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo r = findField(cs.get(0), "r");
        assertNotNull(r);
        assertEquals("Foo", r.getInlineMethods().get(0).getCalls().get(0).getReceiver());
    }

    @Test
    public void testInstanceChainMethodReferenceKeptAsIs() {
        // 小文字終わりのインスタンス連鎖は従来どおり原文を維持する (a.b.c::method → a.b.c)
        String src = ""
                + "class A {\n"
                + "  Runnable r = a.b.c::method;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo r = findField(cs.get(0), "r");
        assertNotNull(r);
        assertEquals("a.b.c", r.getInlineMethods().get(0).getCalls().get(0).getReceiver());
    }

    @Test
    public void testUnknownListenerSuffixResolvedByNamingConvention() {
        // 未知でも *Listener なら "<stem>" を method 名として推定する
        String src = ""
                + "class A {\n"
                + "  MyCustomListener l = () -> doX();\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo l = findField(cs.get(0), "l");
        assertNotNull(l);
        assertEquals(1, l.getInlineMethods().size());
        // "MyCustomListener" → strip "Listener" → "MyCustom" → "myCustom"
        assertEquals("myCustom", l.getInlineMethods().get(0).getName());
    }

    @Test
    public void testUnknownHandlerSuffixResolvedByNamingConvention() {
        String src = ""
                + "class A {\n"
                + "  PrintHandler h = () -> doX();\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo h = findField(cs.get(0), "h");
        assertNotNull(h);
        assertEquals("print", h.getInlineMethods().get(0).getName());
    }

    @Test
    public void testConstructorFieldAssignmentCaptured() {
        // コンストラクタ内で this.listener = ... としているケースを
        // フィールドの inlineMethods に紐づける
        String src = ""
                + "class A {\n"
                + "  private OnClickListener listener;\n"
                + "  A() {\n"
                + "    this.listener = new OnClickListener() {\n"
                + "      public void onClick(View v) { mService.start(); }\n"
                + "    };\n"
                + "  }\n"
                + "  private IService mService;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo listener = findField(cs.get(0), "listener");
        assertNotNull(listener);
        assertEquals(1, listener.getInlineMethods().size());
        assertEquals("onClick", listener.getInlineMethods().get(0).getName());
    }

    @Test
    public void testConstructorFieldAssignmentWithLambdaCaptured() {
        // ラムダ代入も同様に拾うこと
        String src = ""
                + "class A {\n"
                + "  private Runnable r;\n"
                + "  A() { this.r = () -> doX(); }\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo r = findField(cs.get(0), "r");
        assertNotNull(r);
        assertEquals(1, r.getInlineMethods().size());
        assertEquals("run", r.getInlineMethods().get(0).getName());
        assertEquals("doX", r.getInlineMethods().get(0).getCalls().get(0).getMethodName());
    }

    @Test
    public void testFieldAssignmentAfterFieldDeclarationOrder() {
        // フィールドがコンストラクタの「後」に宣言されているケースでも、
        // 解決パスでマッチさせること
        String src = ""
                + "class A {\n"
                + "  A() { this.r = () -> doX(); }\n"
                + "  private Runnable r;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo r = findField(cs.get(0), "r");
        assertNotNull(r);
        assertEquals(1, r.getInlineMethods().size());
    }

    @Test
    public void testMethodInternalListenerRegistrationCaptured() {
        // setOnClickListener(new OnClickListener() {...}) の第 1 引数が
        // 親メソッド本体の Call.inlineMethods に取り込まれること
        String src = ""
                + "class A {\n"
                + "  void onCreate() {\n"
                + "    button.setOnClickListener(new OnClickListener() {\n"
                + "      public void onClick(View v) { mService.start(); }\n"
                + "    });\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaMethodInfo onCreate = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Call setListener = onCreate.getCalls().stream()
                .filter(c -> "setOnClickListener".equals(c.getMethodName()))
                .findFirst().orElse(null);
        assertNotNull(setListener);
        assertEquals(1, setListener.getInlineMethods().size());
        assertEquals("onClick", setListener.getInlineMethods().get(0).getName());
    }

    @Test
    public void testMethodInternalLambdaListenerCaptured() {
        // setOnClickListener(v -> doX()) のラムダ引数も同様
        String src = ""
                + "class A {\n"
                + "  void onCreate() {\n"
                + "    button.setOnClickListener(v -> doX());\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaMethodInfo onCreate = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Call setListener = onCreate.getCalls().stream()
                .filter(c -> "setOnClickListener".equals(c.getMethodName()))
                .findFirst().orElse(null);
        assertNotNull(setListener);
        assertEquals(1, setListener.getInlineMethods().size());
        JavaMethodInfo inline = setListener.getInlineMethods().get(0);
        assertEquals("doX", inline.getCalls().get(0).getMethodName());
    }

    @Test
    public void testMethodInternalAddListenerLambdaResolvesSamName() {
        // addOnLayoutChangeListener(v -> ...) のラムダは set 系と同様に
        // 接頭辞 add を剥がして OnLayoutChangeListener → onLayoutChange に解決される
        String src = ""
                + "class A {\n"
                + "  void onCreate() {\n"
                + "    view.addOnLayoutChangeListener(v -> doX());\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaMethodInfo onCreate = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Call addListener = onCreate.getCalls().stream()
                .filter(c -> "addOnLayoutChangeListener".equals(c.getMethodName()))
                .findFirst().orElse(null);
        assertNotNull(addListener);
        assertEquals(1, addListener.getInlineMethods().size());
        JavaMethodInfo inline = addListener.getInlineMethods().get(0);
        assertEquals("onLayoutChange", inline.getName());
        assertEquals("doX", inline.getCalls().get(0).getMethodName());
    }

    @Test
    public void testMethodInternalRegisterCallbackLambdaResolvesSamName() {
        // registerXxxCallback(c -> ...) も接頭辞 register を剥がして Callback サフィックスで解決
        String src = ""
                + "class A {\n"
                + "  void onCreate() {\n"
                + "    cm.registerNetworkCallback(c -> handle());\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaMethodInfo onCreate = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Call reg = onCreate.getCalls().stream()
                .filter(c -> "registerNetworkCallback".equals(c.getMethodName()))
                .findFirst().orElse(null);
        assertNotNull(reg);
        assertEquals(1, reg.getInlineMethods().size());
        // "NetworkCallback" → strip "Callback" → "Network" → "network"
        assertEquals("network", reg.getInlineMethods().get(0).getName());
    }

    @Test
    public void testAddListenerWithKnownSamMapResolvesToOnClick() {
        // 接頭辞を剥がした後の型名が SAM_FALLBACK にあるケース
        // (addOnClickListener → OnClickListener → onClick)
        String src = ""
                + "class A {\n"
                + "  void onCreate() {\n"
                + "    btn.addOnClickListener(v -> doX());\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaMethodInfo onCreate = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Call add = onCreate.getCalls().stream()
                .filter(c -> "addOnClickListener".equals(c.getMethodName()))
                .findFirst().orElse(null);
        assertNotNull(add);
        assertEquals("onClick", add.getInlineMethods().get(0).getName());
    }

    @Test
    public void testSecondArgAnonymousClassInlined() {
        // 第2引数の匿名クラス本体も call.getInlineMethods() に取り込む
        // (addTextChangedListener(null, new TextWatcher(){ afterTextChanged(){ refresh(); } }))
        String src = ""
                + "class A {\n"
                + "  void m(android.widget.EditText editor) {\n"
                + "    editor.addTextChangedListener(null, new android.text.TextWatcher() {\n"
                + "      public void afterTextChanged(android.text.Editable s) { refresh(); }\n"
                + "    });\n"
                + "  }\n"
                + "  void refresh() {}\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaMethodInfo m = cs.get(0).getMethods().stream()
                .filter(x -> "m".equals(x.getName())).findFirst().orElse(null);
        assertNotNull(m);
        JavaMethodInfo.Call call = m.getCalls().stream()
                .filter(c -> "addTextChangedListener".equals(c.getMethodName()))
                .findFirst().orElse(null);
        assertNotNull(call);
        assertFalse("2nd-arg anonymous body must be inlined into the call",
                call.getInlineMethods().isEmpty());
        assertEquals("afterTextChanged", call.getInlineMethods().get(0).getName());
    }

    private static JavaFieldInfo findField(JavaClassInfo c, String name) {
        for (JavaFieldInfo f : c.getFields()) {
            if (name.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }
}
