// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import com.github.javaparser.ast.Node;
import juml.core.formats.uml.JavaCommentScanner;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaParser ノードに既存パーサーと同じ規約でコメントを割り当てるヘルパ。
 *
 * <p>{@link JavaCommentScanner} で収集したコメント位置を使い、宣言直前のコメント
 * （連続行コメントは改行結合）と、メソッド本体内のコメント（各 1 件ずつ整形）を取り出す。
 * 整形は {@link JavaCommentScanner#cleanText} に委譲して出力を完全一致させる。</p>
 */
final class JpComments {

    private final String src;
    private final List<JavaCommentScanner.Comment> comments;
    private final int[] lineStarts;

    JpComments(String src) {
        this.src = src;
        this.comments = JavaCommentScanner.scan(src);
        this.lineStarts = buildLineStarts(src);
    }

    /** 宣言ノード直前のコメント（無ければ null）。 */
    String before(Node node) {
        if (comments.isEmpty()) {
            return null;
        }
        int pos = beginOffset(node);
        return pos < 0 ? null : JavaCommentScanner.findCommentBefore(src, comments, pos);
    }

    /** 本体ブロック内のコメントを出現順に整形して返す（連続行コメントは結合しない）。 */
    List<String> within(Node body) {
        List<String> out = new ArrayList<>();
        for (JavaCommentScanner.Comment c : commentsIn(body)) {
            String t = JavaCommentScanner.cleanText(c);
            if (t != null && !t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 本体ブロック内（範囲内）のコメントを出現順で返す。 */
    List<JavaCommentScanner.Comment> commentsIn(Node body) {
        List<JavaCommentScanner.Comment> out = new ArrayList<>();
        if (comments.isEmpty()) {
            return out;
        }
        int start = beginOffset(body);
        int end = endOffset(body);
        if (start < 0 || end < 0) {
            return out;
        }
        for (JavaCommentScanner.Comment c : comments) {
            if (c.start >= start && c.end <= end) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * JavaParser が文へ帰属させた前置コメントを {@link JavaCommentScanner#cleanText}
     * 整形で返す（無ければ null）。波括弧なしの単文ボディ（{@code else foo();} 等）は
     * BlockStmt を経由しないため offset ベースの flush では拾えず、この経路で補完する。
     * 整形を既存出力と完全一致させるため、スキャナ側の同位置コメントへ突き合わせる。
     */
    String attached(Node stmt) {
        com.github.javaparser.ast.comments.Comment c = stmt.getComment().orElse(null);
        if (c == null) {
            return null;
        }
        int cs = beginOffset(c);
        if (cs < 0) {
            return null;
        }
        for (JavaCommentScanner.Comment sc : comments) {
            if (sc.start == cs) {
                return JavaCommentScanner.cleanText(sc);
            }
        }
        return null;
    }

    /** ノードの元ソース文字列（pretty-print ではなく原文）。整形差異を避けるために使う。 */
    String raw(Node node) {
        int s = beginOffset(node);
        int e = endOffset(node);
        if (s < 0 || e < 0 || s > e || e > src.length()) {
            return node.toString();
        }
        return src.substring(s, e);
    }

    int beginOffset(Node node) {
        return node.getBegin().map(p -> offset(p.line, p.column)).orElse(-1);
    }

    int endOffset(Node node) {
        return node.getEnd().map(p -> offset(p.line, p.column + 1)).orElse(-1);
    }

    private int offset(int line, int column) {
        if (line < 1 || line > lineStarts.length) {
            return -1;
        }
        return lineStarts[line - 1] + (column - 1);
    }

    private static int[] buildLineStarts(String s) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        int[] arr = new int[starts.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = starts.get(i);
        }
        return arr;
    }
}
