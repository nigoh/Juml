// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.UmlGenerator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * スコープの署名 ({@link DiagramScope#signature()}) と、それを使うタブキー
 * ({@code DiagramController#scopeKey}) が<b>全フィルタ条件</b>を区別することの回帰テスト。
 *
 * <p>以前 {@code scopeKey} は一部の項目だけを連結しており、除外クラス正規表現・
 * include/exclude アノテーション・seed・除外 FQN・外部パッケージ接頭辞・プリセット・
 * フォーカスクラスだけが違うスコープが同じキーになっていた。同じキーは既存タブへの
 * フォーカスに解決されるため、<b>スコープを変えたのに図が変わらない</b>状態になる。</p>
 */
public class DiagramScopeSignatureTest {

    /** 各フィルタ項目を 1 つだけ変える差分。全項目がキーに効くことを確かめる。 */
    private static List<Object[]> mutations() {
        List<Object[]> out = new ArrayList<>();
        out.add(new Object[] {"includePackage", (UnaryOperator<DiagramScope.Builder>)
            b -> b.includePackage("com.a")});
        out.add(new Object[] {"includeModule", (UnaryOperator<DiagramScope.Builder>)
            b -> b.includeModule("app")});
        out.add(new Object[] {"excludePackage", (UnaryOperator<DiagramScope.Builder>)
            b -> b.excludePackage("com.b")});
        out.add(new Object[] {"excludeClass", (UnaryOperator<DiagramScope.Builder>)
            b -> b.excludeClass("com.a.Foo")});
        out.add(new Object[] {"excludeExternalLibraries", (UnaryOperator<DiagramScope.Builder>)
            b -> b.excludeExternalLibraries(true)});
        out.add(new Object[] {"externalPackagePrefixes", (UnaryOperator<DiagramScope.Builder>)
            b -> b.externalPackagePrefixes(Set.of("io.x"))});
        out.add(new Object[] {"classNameRegex", (UnaryOperator<DiagramScope.Builder>)
            b -> b.classNameRegex(".*Impl")});
        out.add(new Object[] {"excludeClassNameRegex", (UnaryOperator<DiagramScope.Builder>)
            b -> b.excludeClassNameRegex(".*Test")});
        out.add(new Object[] {"includeAnnotation", (UnaryOperator<DiagramScope.Builder>)
            b -> b.includeAnnotation("Entity")});
        out.add(new Object[] {"excludeAnnotation", (UnaryOperator<DiagramScope.Builder>)
            b -> b.excludeAnnotation("Deprecated")});
        out.add(new Object[] {"seed", (UnaryOperator<DiagramScope.Builder>)
            b -> b.seed("com.a.Seed")});
        out.add(new Object[] {"neighborHops", (UnaryOperator<DiagramScope.Builder>)
            b -> b.neighborHops(2)});
        out.add(new Object[] {"maxClasses", (UnaryOperator<DiagramScope.Builder>)
            b -> b.maxClasses(50)});
        out.add(new Object[] {"relationKinds", (UnaryOperator<DiagramScope.Builder>)
            b -> b.relationKinds(EnumSet.of(RelationKind.INHERITANCE))});
        out.add(new Object[] {"visibilityFilter", (UnaryOperator<DiagramScope.Builder>)
            b -> b.visibilityFilter(VisibilityFilter.PUBLIC_ONLY)});
        out.add(new Object[] {"parseMode", (UnaryOperator<DiagramScope.Builder>)
            b -> b.parseMode(UmlGenerator.ParseMode.HEADERS_ONLY)});
        out.add(new Object[] {"preset", (UnaryOperator<DiagramScope.Builder>)
            b -> b.preset(DiagramPreset.MINIMAL)});
        out.add(new Object[] {"focusClass", (UnaryOperator<DiagramScope.Builder>)
            b -> b.focusClass("com.a.Focus")});
        return out;
    }

    @SuppressWarnings("unchecked")
    private static DiagramScope mutated(Object[] m) {
        return ((UnaryOperator<DiagramScope.Builder>) m[1])
                .apply(DiagramScope.builder()).build();
    }

    @Test
    public void everyFilterFieldChangesTheSignature() {
        String base = DiagramScope.builder().build().signature();
        for (Object[] m : mutations()) {
            assertNotEquals(m[0] + " が署名に反映されること", base, mutated(m).signature());
        }
    }

    @Test
    public void everyFilterFieldChangesTheTabKey() {
        // 署名が変わってもタブキーへ届いていなければ意味がない (キー衝突 = タブ再利用)。
        String base = DiagramController.scopeKey(DiagramScope.builder().build());
        for (Object[] m : mutations()) {
            assertNotEquals(m[0] + " がタブキーに反映されること",
                    base, DiagramController.scopeKey(mutated(m)));
        }
    }

    @Test
    public void sameScopeContentGivesSameKey() {
        // 同一内容のスコープは 1 タブに集約する (これが崩れるとタブが際限なく増える)。
        DiagramScope a = DiagramScope.builder()
                .includePackage("com.a").excludeClassNameRegex(".*Test").maxClasses(20).build();
        DiagramScope b = DiagramScope.builder()
                .includePackage("com.a").excludeClassNameRegex(".*Test").maxClasses(20).build();
        assertEquals(a.signature(), b.signature());
        assertEquals(DiagramController.scopeKey(a), DiagramController.scopeKey(b));
    }

    @Test
    public void nullScopeHasEmptyKey() {
        assertEquals("", DiagramController.scopeKey(null));
    }
}
