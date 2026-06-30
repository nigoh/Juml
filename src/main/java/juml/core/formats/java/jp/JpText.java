// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import juml.core.formats.uml.Visibility;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaParser ノードから既存モデル用の文字列・可視性へ変換する小さなヘルパ群。
 *
 * <p>修飾子・アノテーションは既存パーサーと同じ表現（アノテーションは先頭 {@code @} を除いた
 * {@code "Foo"} / {@code "Foo(args)"}）に整える。</p>
 */
final class JpText {

    private JpText() {
    }

    /** 修飾子キーワード（{@code public} / {@code static} / {@code sealed} 等）のリスト。 */
    static List<String> modifiers(NodeWithModifiers<?> node) {
        List<String> out = new ArrayList<>();
        node.getModifiers().forEach(m -> out.add(m.getKeyword().asString()));
        return out;
    }

    /** 修飾子から可視性を判定する。 */
    static Visibility visibility(NodeWithModifiers<?> node) {
        return Visibility.fromModifiers(modifiers(node));
    }

    /**
     * 型表記から最外殻の型単純名を取り出す。
     * 例: {@code Action.Builder} → {@code Action}、{@code com.x.ScreenManager} → {@code ScreenManager}。
     * ジェネリクス・配列・空白は落とす。
     */
    static String outer(String type) {
        String t = type;
        int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt);
        }
        t = t.replace("[]", "").trim();
        for (String s : t.split("\\.")) {
            if (!s.isEmpty() && Character.isUpperCase(s.charAt(0))) {
                return s;
            }
        }
        int dot = t.lastIndexOf('.');
        return dot < 0 ? t : t.substring(dot + 1);
    }

    /** アノテーションを先頭 {@code @} を除いた文字列リストで返す。 */
    static List<String> annotations(NodeWithAnnotations<?> node) {
        List<String> out = new ArrayList<>();
        for (AnnotationExpr a : node.getAnnotations()) {
            String s = a.toString();
            out.add(s.startsWith("@") ? s.substring(1) : s);
        }
        return out;
    }
}
