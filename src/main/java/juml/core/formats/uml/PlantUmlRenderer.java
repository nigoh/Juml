// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import juml.util.JapaneseFontSupport;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * PlantUML テキストを SVG 等の画像形式に変換するレンダラ。
 *
 * <p>同梱の PlantUML jar を使って描画するため、外部 graphviz/dot は不要。
 * Graphviz が必要な diagram (クラス図 / コンポーネント図) では Smetana レイアウトを
 * 自動指定するため、追加インストールなしで動作する。</p>
 *
 * <p>{@link #renderSvg(String, OutputStream)} は内部でレンダリング結果を一旦バッファし、
 * PlantUML がフォールバックで返す「An error has occured」SVG を検出した場合は
 * {@link PlantUmlRenderFailedException} を投げる。これにより呼び元 (CLI / GUI) は
 * 壊れた SVG を保存・表示することを避けられる。</p>
 */
public final class PlantUmlRenderer {

    /**
     * 現在のスタイル設定。{@link #injectLayout(String)} 経由で挿入される。
     * 起動時に設定ファイルから読み込んだ値で上書きされる想定。GUI から変更可能。
     */
    private static volatile DiagramStyle currentStyle = DiagramStyle.defaults();

    /**
     * スタイルでフォント未指定時に既定として補う日本語対応フォントファミリ名。
     * 実行環境に存在する日本語フォントを {@link JapaneseFontSupport} で解決した値を持つ
     * （見つからなければ空文字 = 補わない）。これにより図内の日本語が文字化け（豆腐 □）
     * しないようにする。ユーザがスタイルで明示的にフォントを指定した場合はそちらが優先される。
     */
    private static volatile String fallbackFontName = JapaneseFontSupport.defaultFontFamily();

    /**
     * verbose モード。true なら同梱 Smetana 由来の stderr (UNSURE_ABOUT 等) を素通し、
     * false なら {@link #renderSvg(String, OutputStream)} 実行中だけ捨てる。
     */
    private static volatile boolean verbose = false;

    /**
     * Graphviz dot が利用可能かどうか。{@link GraphvizLocator#init(java.io.File)} が
     * dot を発見した場合に true になる。true のとき {@link #injectLayout(String)} は
     * {@code !pragma layout smetana} を挿入しない。
     */
    private static volatile boolean graphvizAvailable = false;

    /**
     * テスト用のレンダラ差し替えフック。null でなければ {@link SourceStringReader#outputImage}
     * の代わりに使われる。本番経路では常に null。
     */
    private static volatile BiConsumer<String, OutputStream> rendererImplForTest;

    /** PlantUML フォールバック エラー SVG に必ず含まれるマーカー。 */
    private static final String[] ERROR_MARKERS = {
            // PlantUML のレイアウト/内部エラー画像のバナー文言。
            "An error has occured",
            "I love it when a plan comes together",
            // 構文エラー等の画像に必ず現れるエラー位置表記。生成 PlantUML が壊れた場合
            // (例: コメントに @startuml が含まれ別図と誤認される等) に出る。PlantUML
            // 内部の文言で正常な図のテキストには現れないため、誤検知なくエラー判定できる。
            "[From string (line "
    };

    /** PlantUML のキャンバスサイズ上限を制御するシステムプロパティ / 環境変数名。 */
    private static final String LIMIT_SIZE_PROP = "PLANTUML_LIMIT_SIZE";

    /** レンダリング中に捕捉する stderr の最大保持バイト数 (末尾のみ保持)。 */
    private static final int STDERR_CAPTURE_MAX = 64 * 1024;

    /**
     * PNG ラスタライズ時の 1 辺あたり最大ピクセル数の既定値。PlantUML 既定の 4096 では
     * 巨大なクラス図/シーケンス図が PNG エクスポートで切り詰められてしまうため、より大きく取る。
     * 16384²×4byte ≒ 1GB が上限の目安なので、実用上ほとんどの図を収めつつ極端な OOM を避ける値。
     */
    public static final int DEFAULT_IMAGE_LIMIT = 16384;

    private PlantUmlRenderer() {
    }

    /**
     * PlantUML のキャンバスサイズ上限 ({@code PLANTUML_LIMIT_SIZE}) を {@link #DEFAULT_IMAGE_LIMIT}
     * に引き上げる。ユーザが {@code -DPLANTUML_LIMIT_SIZE=...} または環境変数で明示指定済みの
     * 場合は尊重して何もしない。アプリ起動時 ({@code Main}) に一度だけ呼ぶ想定。
     */
    public static void configureImageLimit() {
        if (System.getProperty(LIMIT_SIZE_PROP) == null
                && System.getenv(LIMIT_SIZE_PROP) == null) {
            System.setProperty(LIMIT_SIZE_PROP, Integer.toString(DEFAULT_IMAGE_LIMIT));
        }
    }

    /**
     * 現在有効な PNG キャンバス上限 (1 辺あたりピクセル数) を返す。プロパティ → 環境変数の順で
     * 参照し、未設定・不正値なら {@link #DEFAULT_IMAGE_LIMIT}。
     */
    public static int imageLimit() {
        String v = System.getProperty(LIMIT_SIZE_PROP);
        if (v == null) {
            v = System.getenv(LIMIT_SIZE_PROP);
        }
        if (v != null) {
            try {
                int n = Integer.parseInt(v.trim());
                if (n > 0) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // 不正値は既定にフォールバック
            }
        }
        return DEFAULT_IMAGE_LIMIT;
    }

    /** 現在のスタイルを取得する。null 安全 (常に非 null を返す)。 */
    public static DiagramStyle getStyle() {
        DiagramStyle s = currentStyle;
        return s != null ? s : DiagramStyle.defaults();
    }

    /**
     * 現在のスタイルを更新する。以降の {@link #renderSvg} 系および
     * {@link #injectLayout(String)} 呼び出しに即時反映される。
     * null を渡すと既定スタイルにリセットする。
     */
    public static void setStyle(DiagramStyle style) {
        currentStyle = style != null ? style.copy() : DiagramStyle.defaults();
    }

    /**
     * verbose モードを設定する。CLI で {@code -v} が指定されたら true、それ以外は false。
     * false の間、{@link #renderSvg(String, OutputStream)} 実行中の {@link System#err}
     * は一時的に捨てられ、同梱 Smetana が直接 stderr へ書き込むデバッグ ログを抑制する。
     */
    public static void setVerbose(boolean v) {
        verbose = v;
    }

    /**
     * Graphviz dot の利用可否を設定する。{@link GraphvizLocator} が呼び出す想定。
     * テストから明示的に false を渡してデフォルト動作 (Smetana 挿入) を強制できる。
     */
    public static void setGraphvizAvailable(boolean available) {
        graphvizAvailable = available;
    }

    /** Graphviz dot が利用可能かどうか返す。 */
    public static boolean isGraphvizAvailable() {
        return graphvizAvailable;
    }

    /**
     * フォント未指定時に補う既定フォント名を返す。日本語対応フォントが見つからない
     * 環境では空文字。
     */
    public static String getFallbackFontName() {
        String s = fallbackFontName;
        return s != null ? s : "";
    }

    /**
     * フォント未指定時に補う既定フォント名を設定する。主にテストで決定的な挙動を
     * 得るために使用する。null は空文字（補わない）として扱う。
     */
    public static void setFallbackFontName(String name) {
        fallbackFontName = name != null ? name : "";
    }

    /**
     * テスト専用 ({@code @VisibleForTesting} 相当): {@link SourceStringReader} の代わりに
     * 使うレンダラ実装を差し込む。null を渡すと本番経路に戻る。本番コードから呼ばないこと。
     * <p>JUnit テストが他パッケージ ({@code juml}) からアクセスする必要があるため
     * {@code public} になっているが、設計上は package-private 想定。</p>
     */
    public static void setRendererImplForTest(BiConsumer<String, OutputStream> impl) {
        rendererImplForTest = impl;
    }

    /**
     * 与えられたバイト列が PlantUML のフォールバック エラー SVG かどうか判定する。
     * 先頭 8KB だけサンプリングするので巨大な正常 SVG でも安価。
     */
    static boolean isErrorSvg(byte[] svgBytes) {
        if (svgBytes == null || svgBytes.length == 0) {
            return true;
        }
        int len = Math.min(svgBytes.length, 8192);
        String head = new String(svgBytes, 0, len, StandardCharsets.UTF_8);
        for (String marker : ERROR_MARKERS) {
            if (head.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** PlantUML テキストを SVG として OutputStream に書き出す。
     *
     * <p>レンダリング結果が PlantUML のエラー画像にすり替わっていた場合は
     * {@link PlantUmlRenderFailedException} を投げる。失敗時はエラー SVG から
     * 抽出した診断 (行番号・エラーテキスト) と stderr の末尾を例外に添え、
     * {@link juml.util.AppLog} にも記録する。</p>
     */
    public static void renderSvg(String puml, OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("out is null");
        }
        // 既に SVG 文字列 (PlantUML 以外の図種が直接生成したもの) はそのまま書き出す。
        if (looksLikeSvg(puml)) {
            out.write(puml.getBytes(StandardCharsets.UTF_8));
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        // Smetana/PlantUML が stderr へ直接書くデバッグ出力を捕捉する。verbose なら
        // 元の stderr にも素通しし、非 verbose なら画面には出さない。いずれの場合も
        // 失敗時の診断用に末尾を保持する (従来は非 verbose で完全に捨てていた)。
        // Smetana は巨大な図で大量に出力するため、末尾のみ保持する有界バッファを使う。
        StderrTailBuffer errCapture = new StderrTailBuffer(STDERR_CAPTURE_MAX);
        PrintStream origErr = System.err;
        PrintStream capture = new PrintStream(errCapture, true, StandardCharsets.UTF_8) {
            @Override
            public void write(byte[] b, int off, int len) {
                super.write(b, off, len);
                if (verbose) {
                    origErr.write(b, off, len);
                    origErr.flush();
                }
            }
        };
        System.setErr(capture);
        try {
            BiConsumer<String, OutputStream> stub = rendererImplForTest;
            if (stub != null) {
                stub.accept(puml, buf);
            } else {
                SourceStringReader reader = new SourceStringReader(injectLayout(puml));
                reader.outputImage(buf, new FileFormatOption(FileFormat.SVG));
            }
        } finally {
            System.setErr(origErr);
            capture.close();
        }
        byte[] bytes = buf.toByteArray();
        if (isErrorSvg(bytes)) {
            throw buildRenderFailure(puml, bytes, errCapture);
        }
        out.write(bytes);
    }

    /**
     * エラー SVG・stderr 捕捉・軽量リンタの結果を突き合わせて、原因調査に十分な情報を
     * 持つ {@link PlantUmlRenderFailedException} を構築し、{@link juml.util.AppLog} に
     * 詳細を記録する。
     */
    private static PlantUmlRenderFailedException buildRenderFailure(
            String puml, byte[] errorSvg, StderrTailBuffer errCapture) {
        String detail = extractErrorDetail(errorSvg);
        int errorLine = extractErrorLine(detail);
        String stderrTail = tailOf(errCapture.tailString(), 2000);
        // 生成 PlantUML を軽量リンタにかけ、既知のゴミ／構文崩れが見つかれば添える。
        String hint = PlantUmlSyntaxChecker.summarize(puml);

        StringBuilder msg = new StringBuilder("PlantUML render failed");
        if (errorLine > 0) {
            msg.append(" at line ").append(errorLine).append(" of generated source");
        }
        msg.append('.');
        if (!detail.isEmpty()) {
            msg.append(" PlantUML says: ").append(detail);
        }
        if (!hint.isEmpty()) {
            msg.append(" Possible cause — ").append(hint);
        }
        msg.append(" (layout=").append(graphvizAvailable ? "graphviz" : "smetana").append(')');

        StringBuilder log = new StringBuilder(msg);
        log.append('\n');
        appendPumlExcerpt(log, puml, errorLine);
        if (!stderrTail.isEmpty()) {
            log.append("\n--- stderr tail ---\n").append(stderrTail);
        }
        juml.util.AppLog.error("PlantUmlRenderer", log.toString());

        return new PlantUmlRenderFailedException(msg.toString(), errorLine, detail, stderrTail);
    }

    /**
     * エラー SVG のテキストノードから診断メッセージを抽出する。PlantUML のエラー画像は
     * {@code [From string (line N) ]}・失敗した行の内容・{@code Syntax Error?} 等を
     * {@code <text>} 要素として含むため、それらを連結して返す (最大 500 文字)。
     */
    static String extractErrorDetail(byte[] svgBytes) {
        if (svgBytes == null || svgBytes.length == 0) {
            return "";
        }
        String svg = new String(svgBytes, 0, Math.min(svgBytes.length, 65536),
                StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<text[^>]*>([^<]+)</text>").matcher(svg);
        while (m.find() && sb.length() < 500) {
            String t = m.group(1).trim();
            if (t.isEmpty()) {
                continue;
            }
            // エラー画像のバナー文言 (ジョーク文含む) はノイズなので除外する
            if (t.startsWith("An error has occured")
                    || t.contains("plan comes together")
                    || t.startsWith("Diagram size")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(t);
        }
        return sb.toString();
    }

    /** 診断テキスト中の {@code [From string (line N)} から行番号を取り出す。無ければ -1。 */
    static int extractErrorLine(String detail) {
        if (detail == null || detail.isEmpty()) {
            return -1;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\[From string \\(line (\\d+)\\)").matcher(detail);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                // 桁あふれ等は不明扱い
            }
        }
        return -1;
    }

    /** 失敗行の前後数行を行番号付きでログへ添える。行番号不明なら先頭数行のみ。 */
    private static void appendPumlExcerpt(StringBuilder log, String puml, int errorLine) {
        String[] lines = puml.split("\n", -1);
        int center = errorLine > 0 ? errorLine : 1;
        int from = Math.max(1, center - 3);
        int to = Math.min(lines.length, center + 3);
        log.append("--- generated PlantUML (lines ").append(from).append('-').append(to)
                .append(" of ").append(lines.length).append(") ---");
        for (int i = from; i <= to; i++) {
            log.append('\n').append(i == errorLine ? ">" : " ")
                    .append(String.format("%4d| ", i)).append(lines[i - 1]);
        }
    }

    /** 文字列の末尾 {@code maxChars} 文字を返す (診断ログの肥大化防止)。 */
    private static String tailOf(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= maxChars ? t : "…" + t.substring(t.length() - maxChars);
    }

    /** レンダリング中の stderr 捕捉に使う「末尾 maxBytes だけ保持する」有界バッファ。 */
    private static final class StderrTailBuffer extends OutputStream {
        private final byte[] ring;
        private int pos;
        private long total;

        StderrTailBuffer(int maxBytes) {
            this.ring = new byte[maxBytes];
        }

        @Override
        public void write(int b) {
            ring[pos] = (byte) b;
            pos = (pos + 1) % ring.length;
            total++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            for (int i = 0; i < len; i++) {
                write(b[off + i]);
            }
        }

        /** 保持している末尾バイト列を UTF-8 文字列で返す。 */
        String tailString() {
            if (total <= 0) {
                return "";
            }
            if (total < ring.length) {
                return new String(ring, 0, (int) total, StandardCharsets.UTF_8);
            }
            byte[] linear = new byte[ring.length];
            System.arraycopy(ring, pos, linear, 0, ring.length - pos);
            System.arraycopy(ring, 0, linear, ring.length - pos, pos);
            return new String(linear, StandardCharsets.UTF_8);
        }
    }

    /**
     * 文字列が (PlantUML ではなく) 既製の SVG ドキュメントかどうかを判定する。
     * 先頭の空白・BOM・XML 宣言・コメントを読み飛ばし、最初の要素が {@code <svg} なら true。
     */
    public static boolean looksLikeSvg(String s) {
        if (s == null) {
            return false;
        }
        int i = 0;
        int n = s.length();
        if (n > 0 && s.charAt(0) == '﻿') {
            i = 1;
        }
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (s.startsWith("<?xml", i)) {
                int close = s.indexOf("?>", i);
                if (close < 0) {
                    return false;
                }
                i = close + 2;
                continue;
            }
            if (s.startsWith("<!--", i)) {
                int close = s.indexOf("-->", i);
                if (close < 0) {
                    return false;
                }
                i = close + 3;
                continue;
            }
            if (s.startsWith("<!DOCTYPE", i)) {
                int close = s.indexOf('>', i);
                if (close < 0) {
                    return false;
                }
                i = close + 1;
                continue;
            }
            return s.startsWith("<svg", i);
        }
        return false;
    }

    /** PlantUML テキストを SVG として指定ファイルに書き出す。
     *
     * <p>失敗時は壊れた 0 byte ファイルを残さないよう、ファイルを削除した上で
     * 例外を再 throw する。</p>
     */
    public static void renderSvg(String puml, File outFile) throws IOException {
        try (OutputStream os = new FileOutputStream(outFile)) {
            renderSvg(puml, os);
        } catch (IOException e) {
            // 中身が無効な状態のファイルを残さない
            if (outFile.exists() && !outFile.delete()) {
                outFile.deleteOnExit();
            }
            throw e;
        }
    }

    /**
     * PNG エクスポート用に {@code @startuml} 直後へ {@code scale max <maxPx>*<maxPx>} を挿入する。
     * これにより {@code PLANTUML_LIMIT_SIZE} を超える巨大な図は「切り詰め」ではなく「縮小」されて
     * キャンバスに収まり、PNG が途中で切れたり壊れたりするのを防ぐ。
     *
     * <p>{@code scale max} は図が指定サイズより大きいときだけ縮小し、小さい図は拡大しないため
     * 通常サイズの図には影響しない。既に {@code scale} 指定がある図や {@code @startuml} を含まない
     * 文字列はそのまま返す ({@code maxPx <= 0} も同様)。</p>
     */
    public static String injectScaleMax(String puml, int maxPx) {
        if (puml == null) {
            return null;
        }
        int idx = puml.indexOf("@startuml");
        if (idx < 0 || maxPx <= 0 || hasScaleDirective(puml)) {
            return puml;
        }
        String line = "scale max " + maxPx + "*" + maxPx + "\n";
        int nl = puml.indexOf('\n', idx);
        if (nl < 0) {
            return puml + "\n" + line;
        }
        return puml.substring(0, nl + 1) + line + puml.substring(nl + 1);
    }

    /** 行頭 (前後空白除去後) が {@code scale} で始まる行があるか。ユーザ指定の scale を尊重するため。 */
    private static boolean hasScaleDirective(String puml) {
        for (String raw : puml.split("\n", -1)) {
            String t = raw.trim();
            if (t.equals("scale") || t.startsWith("scale ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 渡された PlantUML が向き指定ディレクティブ ({@code left to right direction} /
     * {@code top to bottom direction}) を受け付ける図種かを判定する。
     *
     * <p>シーケンス図・アクティビティ図はこれらを受け付けず構文エラーになる。
     * Juml が生成するシーケンス図は必ず {@code participant} 宣言を、アクティビティ図は
     * 必ず {@code start}/{@code stop} を含むため、これらの図種専用キーワードが
     * 行頭に現れるかで判別する (クラス図/パッケージ図等はこれらを含まないので
     * 誤判定しない)。</p>
     *
     * @return 向き指定を出してよければ true (= sequence/activity 以外)
     */
    static boolean supportsDirection(String puml) {
        if (puml == null || puml.isEmpty()) {
            return true;
        }
        for (String line : puml.split("\n", -1)) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            // シーケンス図: participant 等の宣言キーワードや autonumber。
            // ({@code actor} は使用例図 (usecase) でも向き指定可で使われ誤判定の元になるが、
            //  Juml のシーケンス図は participant のみを出力するため対象に含めない。)
            if (t.startsWith("participant ") || t.startsWith("boundary ")
                    || t.startsWith("control ") || t.startsWith("entity ")
                    || t.startsWith("database ") || t.startsWith("collections ")
                    || t.startsWith("queue ")
                    || t.equals("autonumber") || t.startsWith("autonumber ")) {
                return false;
            }
            // アクティビティ図 (新記法): start/stop/end やアクションノード ":...;"
            if (t.equals("start") || t.equals("stop") || t.equals("end")
                    || t.startsWith("if (") || t.startsWith("repeat")
                    || t.startsWith("while (") || t.startsWith("fork")
                    || t.startsWith("split") || t.startsWith("partition ")) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code @startuml} の直後に {@code !pragma layout smetana} と、現在の
     * {@link #getStyle() スタイル} 由来の {@code !theme} / {@code skinparam} 行を挿入する。
     * Graphviz/dot 未インストール環境でもクラス図/コンポーネント図を描画できるようにする。
     * 既に {@code !pragma layout} 指定があれば layout 行は重複追加しない (スタイル行は追加する)。
     */
    public static String injectLayout(String puml) {
        return injectLayout(puml, getStyle());
    }

    /**
     * スタイルを明示指定する {@link #injectLayout(String)} のオーバーロード (テスト用に公開)。
     */
    public static String injectLayout(String puml, DiagramStyle style) {
        if (puml == null) {
            return null;
        }
        int idx = puml.indexOf("@startuml");
        if (idx < 0) {
            return puml;
        }
        // シーケンス図・アクティビティ図は向き指定 (left/top ... direction) を受け付けず
        // PlantUML 構文エラーになるため、これらでは向き指定を抑制する。
        boolean allowDirection = supportsDirection(puml);
        // ユーザがスタイルで明示的に向きを指定した (非 DEFAULT) 場合、図種ビルダが本体に
        // 直書きした向き指定 (top to bottom direction など) を取り除き、prelude 側の
        // 向き指定を最終的に有効化する。未指定 (DEFAULT) ならビルダの既定を尊重する。
        String working = puml;
        if (allowDirection && style != null
                && style.getDirection() != DiagramStyle.Direction.DEFAULT) {
            working = stripBodyDirectionLines(puml);
            idx = working.indexOf("@startuml");
        }
        boolean hasLayoutPragma = working.contains("!pragma layout");
        String prelude = style != null ? style.toPlantUmlPrelude(allowDirection) : "";
        // スタイルでフォント未指定なら、日本語対応の既定フォントを補って文字化けを防ぐ。
        // !theme 由来のフォント指定より後に置くことで、こちらが優先される。
        String fontFallback = "";
        boolean noExplicitFont = style == null || style.getFontName().isEmpty();
        if (noExplicitFont && !getFallbackFontName().isEmpty()) {
            fontFallback = "skinparam defaultFontName " + getFallbackFontName() + "\n";
        }
        String styleLines = prelude + fontFallback;
        if (hasLayoutPragma && styleLines.isEmpty()) {
            return working;
        }
        StringBuilder injected = new StringBuilder();
        if (!hasLayoutPragma && !graphvizAvailable) {
            injected.append("!pragma layout smetana\n");
        }
        injected.append(styleLines);
        if (injected.length() == 0) {
            return working;
        }
        int nl = working.indexOf('\n', idx);
        if (nl < 0) {
            return working + "\n" + injected.toString();
        }
        return working.substring(0, nl + 1)
                + injected.toString()
                + working.substring(nl + 1);
    }

    /**
     * 本体に直書きされた向き指定行 ({@code left to right direction} /
     * {@code top to bottom direction}) を取り除く。ユーザがスタイルで向きを明示した際に、
     * 図種ビルダの既定向きより prelude の向き指定を優先させるために使う。
     */
    private static String stripBodyDirectionLines(String puml) {
        String[] lines = puml.split("\n", -1);
        StringBuilder sb = new StringBuilder(puml.length());
        for (String line : lines) {
            String t = line.trim();
            if (t.equals("left to right direction")
                    || t.equals("top to bottom direction")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }
}
