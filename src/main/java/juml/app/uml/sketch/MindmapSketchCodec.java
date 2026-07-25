// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link MindmapSketchModel} と PlantUML マインドマップテキストの双方向変換。
 *
 * <p>対応構文 (V1) はプレーンなマインドマップの基本要素に限定する:
 * {@code @startmindmap[ 名前]} … {@code @endmindmap}、記号の連続 + テキストの
 * ノード行 ({@code <単一種の記号列> テキスト})。深さは記号の連続長で表し
 * 「直前ノードの深さ +1」までしか受理しない (中間深さへの跳躍は PlantUML でも
 * {@code Bad indentation} になるため常に未対応)。記号は左右を表す:
 * {@code +}=右固定・{@code -}=左固定・{@code *}=未指定 (祖先から継承)。単一ルート。</p>
 *
 * <p>次のものは往復できないため「未対応」として報告し、呼び出し側 (GUI デザイナー) は
 * 編集を無効化してテキストを壊さないようにする: 複数行ノード ({@code *:...;})、
 * {@code left/right side} などのディレクティブ、{@code skinparam/title/caption/hide}
 * 等の装飾、{@code '} コメント、記号の混在 ({@code *-*})、深さ跳躍、2 本目以降の
 * 深さ 1 行 (単一ルート制約)。</p>
 *
 * <p><b>side 継承の正規化 (最重要):</b> PlantUML では記号ファミリの不整合 (例:
 * {@code - Left} の子に生の {@code *}) が構文エラー ({@code error42L}) になる。そこで
 * {@link #toPuml} は各ノードの<b>実効 side</b> (祖先から継承した最初の非 AUTO) を
 * 計算してから記号を選ぶ。これにより {@code -} の枝の途中に AUTO の子があっても出力は
 * {@code -} 系で一貫し、構文エラーを避けられる。生の {@code *} を持つモデルは
 * 「初回ロードで正規化・2 回目以降は固定点」になる ({@link DeploySketchCodec} の
 * 負座標クランプと同じ往復流儀)。</p>
 */
public final class MindmapSketchCodec {

    /** 記号の連続 + テキスト ({@code ** Design} 等)。記号は 1 種でなければ未対応にする。 */
    private static final Pattern NODE_LINE = Pattern.compile("^([*+\\-]+)\\s+(.+)$");

    private MindmapSketchCodec() {
    }

    /** {@link #parse(String)} の結果 (モデル + 未対応行の一覧)。 */
    public static final class ParseResult {
        public final MindmapSketchModel model;
        /** モデル化できなかった非空行 (これが空のときだけ GUI 編集を許可する)。 */
        public final List<String> unsupportedLines;

        ParseResult(MindmapSketchModel model, List<String> unsupportedLines) {
            this.model = model;
            this.unsupportedLines = unsupportedLines;
        }

        /** すべての行をモデル化できたか (= GUI 編集してもテキストを失わないか)。 */
        public boolean isFullySupported() {
            return unsupportedLines.isEmpty();
        }
    }

    /** スタックに積む「深さ + ノード」のフレーム。 */
    private record Frame(int depth, MindmapNode node) {
    }

    /** PlantUML テキストをマインドマップモデルへ解析する (スタック式インデントパーサ)。 */
    public static ParseResult parse(String text) {
        MindmapSketchModel model = new MindmapSketchModel();
        List<String> unsupported = new ArrayList<>();
        Deque<Frame> stack = new ArrayDeque<>();
        for (String raw : (text == null ? "" : text).split("\n", -1)) {
            parseLine(raw.trim(), model, unsupported, stack);
        }
        return new ParseResult(model, unsupported);
    }

    private static void parseLine(String line, MindmapSketchModel model,
                                  List<String> unsupported, Deque<Frame> stack) {
        if (line.startsWith("@startmindmap")) {
            String name = line.substring("@startmindmap".length()).trim();
            if (!name.isEmpty()) {
                model.setDiagramName(name);
            }
            return;
        }
        if (line.isEmpty() || line.equals("@endmindmap")) {
            return;
        }
        Matcher m = NODE_LINE.matcher(line);
        if (!m.matches()) {
            // コメント・装飾・複数行ノード・ディレクティブ等はモデル化できず往復で失われる。
            unsupported.add(line);
            return;
        }
        String symbols = m.group(1);
        MindmapNode.Side side = singleFamilySide(symbols);
        if (side == null) {
            // 記号の混在 (例: *-*) はファミリ不整合で PlantUML が壊れるため未対応。
            unsupported.add(line);
            return;
        }
        MindmapNode node = new MindmapNode(m.group(2).trim());
        node.setOwnSide(side);
        int depth = symbols.length();
        if (stack.isEmpty()) {
            if (model.getRoot() != null) {
                // 2 本目の深さ 1 行 (フォレスト) は単一ルート制約で未対応。
                unsupported.add(line);
                return;
            }
            // 最初のノードは記号数に関わらず深さ 1 として扱う。
            model.setRoot(node);
            stack.push(new Frame(1, node));
            return;
        }
        while (!stack.isEmpty() && stack.peek().depth() >= depth) {
            stack.pop();
        }
        if (stack.isEmpty() || depth != stack.peek().depth() + 1) {
            // 跳躍 (直前深さ +1 を超える) や 2 本目ルートへの逆戻りは往復不能。
            unsupported.add(line);
            return;
        }
        model.addChild(stack.peek().node(), node);
        stack.push(new Frame(depth, node));
    }

    /** 記号列が 1 種類だけなら対応する {@link MindmapNode.Side} を、混在なら null を返す。 */
    private static MindmapNode.Side singleFamilySide(String symbols) {
        char first = symbols.charAt(0);
        for (int i = 1; i < symbols.length(); i++) {
            if (symbols.charAt(i) != first) {
                return null;
            }
        }
        return MindmapNode.Side.fromSymbol(first);
    }

    /**
     * モデルを PlantUML テキストへ書き出す。各ノードは<b>実効 side</b> (祖先継承) で
     * 記号を選ぶため、生の {@code *} を持つ子も親の {@code -} / {@code +} 系へ正規化される
     * (記号ファミリ不整合 = 実機の {@code error42L} を避ける必須ロジック)。
     */
    public static String toPuml(MindmapSketchModel model) {
        StringBuilder sb = new StringBuilder("@startmindmap");
        if (!model.getDiagramName().isEmpty()) {
            sb.append(' ').append(model.getDiagramName());
        }
        sb.append('\n');
        if (model.getRoot() != null) {
            emit(sb, model.getRoot(), 1);
        }
        sb.append("@endmindmap\n");
        return sb.toString();
    }

    private static void emit(StringBuilder sb, MindmapNode n, int depth) {
        char sym = effectiveSide(n).symbol();
        sb.append(String.valueOf(sym).repeat(depth)).append(' ').append(n.getText()).append('\n');
        for (MindmapNode c : n.getChildren()) {
            emit(sb, c, depth + 1);
        }
    }

    /** {@code n} を含め祖先方向へ辿り、最初の非 AUTO を実効 side とする (無ければ AUTO)。 */
    private static MindmapNode.Side effectiveSide(MindmapNode n) {
        for (MindmapNode cur = n; cur != null; cur = cur.getParent()) {
            if (cur.getOwnSide() != MindmapNode.Side.AUTO) {
                return cur.getOwnSide();
            }
        }
        return MindmapNode.Side.AUTO;
    }
}
