// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link PlantUmlRenderer} のエラー検出 + {@link PlantUmlRenderFailedException} 経路を検証する。
 *
 * <p>PlantUML 同梱 Smetana が落ちると「An error has occured」を含むフォールバック SVG が
 * 出力される。それをそのまま保存・表示しないよう、{@code isErrorSvg} で検出して
 * 例外に変換する仕組みを単体テストする。</p>
 */
public class PlantUmlRendererErrorDetectionTest {

    @After
    public void resetRendererImpl() {
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.setVerbose(false);
    }

    @Test
    public void testIsErrorSvgDetectsPlantUmlErrorMarker() {
        String body = "<?xml version=\"1.0\"?><svg><text>An error has occured</text></svg>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        assertTrue("error marker should be detected",
                PlantUmlRenderer.isErrorSvg(bytes));
    }

    @Test
    public void testIsErrorSvgDetectsATeamMarker() {
        String body = "<svg><text>I love it when a plan comes together</text></svg>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        assertTrue("PlantUML A-team marker should be detected",
                PlantUmlRenderer.isErrorSvg(bytes));
    }

    @Test
    public void testIsErrorSvgDetectsSyntaxErrorLocationMarker() {
        // 動的検証で発見: コメント中の @startuml が別図と誤認され、構文エラー画像が
        // exit 0 で素通りしていた。構文エラー画像に必ず現れる位置表記を検出する回帰。
        String body = "<svg><text>[From string (line 42) ]</text>"
                + "<text>Syntax Error? (Assumed diagram type: sequence)</text></svg>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        assertTrue("syntax error location marker should be detected",
                PlantUmlRenderer.isErrorSvg(bytes));
    }

    @Test
    public void testIsErrorSvgAcceptsValidSvg() {
        String body = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\">"
                + "<rect width=\"10\" height=\"10\"/></svg>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        assertFalse("valid SVG should not be detected as error",
                PlantUmlRenderer.isErrorSvg(bytes));
    }

    @Test
    public void testIsErrorSvgRejectsNullOrEmpty() {
        assertTrue(PlantUmlRenderer.isErrorSvg(null));
        assertTrue(PlantUmlRenderer.isErrorSvg(new byte[0]));
    }

    @Test
    public void testRenderSvgThrowsOnErrorMarkerViaStub() {
        // テスト用 DI フックを使って強制的にエラー SVG を返させ、例外型と
        // メッセージを確認する。本番の Smetana バグの再現は CI で困難なため、
        // ここではエラー判定ロジックのみを検証する。
        PlantUmlRenderer.setRendererImplForTest((puml, out) -> {
            byte[] err = "<svg><text>An error has occured</text></svg>"
                    .getBytes(StandardCharsets.UTF_8);
            try {
                out.write(err);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
        try {
            PlantUmlRenderer.renderSvg("@startuml\nclass X\n@enduml\n",
                    new ByteArrayOutputStream());
            fail("Expected PlantUmlRenderFailedException");
        } catch (PlantUmlRenderFailedException expected) {
            String msg = expected.getMessage();
            assertTrue("message should mention render failure: " + msg,
                    msg.contains("PlantUML render failed"));
            assertTrue("message should mention layout engine: " + msg,
                    msg.contains("layout="));
        } catch (IOException other) {
            fail("Unexpected IOException: " + other);
        }
    }

    @Test
    public void testRenderSvgFileDeletesOnFailure() throws IOException {
        PlantUmlRenderer.setRendererImplForTest((puml, out) -> {
            byte[] err = "<svg><text>An error has occured</text></svg>"
                    .getBytes(StandardCharsets.UTF_8);
            try {
                out.write(err);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
        Path tmp = Files.createTempFile("juml-fail", ".svg");
        File svg = tmp.toFile();
        try {
            PlantUmlRenderer.renderSvg("@startuml\nclass X\n@enduml\n", svg);
            fail("Expected PlantUmlRenderFailedException");
        } catch (PlantUmlRenderFailedException expected) {
            // 失敗時にゴミファイルが残らないこと
            assertFalse("0-byte svg should be deleted on failure",
                    svg.exists());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testRenderSvgSuccessWritesBytesUnchanged() throws IOException {
        // フィールド注入で「正常 SVG」を返すスタブを使い、out へバイトがそのまま流れることを確認
        byte[] validSvg = ("<?xml version=\"1.0\"?>"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                + "<rect width=\"10\" height=\"10\"/></svg>")
                .getBytes(StandardCharsets.UTF_8);
        PlantUmlRenderer.setRendererImplForTest((puml, out) -> {
            try {
                out.write(validSvg);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg("@startuml\nclass X\n@enduml\n", sink);
        assertEquals(new String(validSvg, StandardCharsets.UTF_8),
                new String(sink.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testVerboseSwitchDoesNotChangeOutput() throws IOException {
        // verbose ON/OFF のどちらでもバイト出力は変わらない (stderr 抑制有無のみ)
        byte[] validSvg = ("<svg xmlns=\"http://www.w3.org/2000/svg\"><rect/></svg>")
                .getBytes(StandardCharsets.UTF_8);
        PlantUmlRenderer.setRendererImplForTest((puml, out) -> {
            try {
                out.write(validSvg);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
        ByteArrayOutputStream a = new ByteArrayOutputStream();
        PlantUmlRenderer.setVerbose(false);
        PlantUmlRenderer.renderSvg("@startuml\n@enduml", a);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        PlantUmlRenderer.setVerbose(true);
        PlantUmlRenderer.renderSvg("@startuml\n@enduml", b);
        assertEquals(new String(a.toByteArray(), StandardCharsets.UTF_8),
                new String(b.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testStderrSuppressedWhenNotVerbose() throws IOException {
        // スタブが System.err に書いても、verbose=false なら呼び元の stderr に到達しないこと
        PrintStream origErr = System.err;
        ByteArrayOutputStream observed = new ByteArrayOutputStream();
        System.setErr(new PrintStream(observed, true, StandardCharsets.UTF_8));
        try {
            PlantUmlRenderer.setRendererImplForTest((puml, out) -> {
                System.err.println("UNSURE_ABOUT: should be suppressed");
                try {
                    out.write("<svg><rect/></svg>".getBytes(StandardCharsets.UTF_8));
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            });
            PlantUmlRenderer.setVerbose(false);
            PlantUmlRenderer.renderSvg("@startuml\nclass X\n@enduml\n",
                    new ByteArrayOutputStream());
        } finally {
            System.setErr(origErr);
        }
        String captured = new String(observed.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("UNSURE_ABOUT should be suppressed but saw: " + captured,
                captured.contains("UNSURE_ABOUT"));
    }

    @Test
    public void testStderrPassesThroughWhenVerbose() throws IOException {
        PrintStream origErr = System.err;
        ByteArrayOutputStream observed = new ByteArrayOutputStream();
        System.setErr(new PrintStream(observed, true, StandardCharsets.UTF_8));
        try {
            PlantUmlRenderer.setRendererImplForTest((puml, out) -> {
                System.err.println("UNSURE_ABOUT: visible in verbose");
                try {
                    out.write("<svg><rect/></svg>".getBytes(StandardCharsets.UTF_8));
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            });
            PlantUmlRenderer.setVerbose(true);
            PlantUmlRenderer.renderSvg("@startuml\nclass X\n@enduml\n",
                    new ByteArrayOutputStream());
        } finally {
            System.setErr(origErr);
        }
        String captured = new String(observed.toByteArray(), StandardCharsets.UTF_8);
        assertTrue("UNSURE_ABOUT should pass through in verbose, observed: " + captured,
                captured.contains("UNSURE_ABOUT"));
    }

    // ---- extractErrorDetail / extractErrorLine の単体テスト ----

    @Test
    public void testExtractErrorDetailIncludesLineMarkerAndContent() {
        // エラー SVG の <text> ノードから診断情報を取り出せることを確認する。
        // "[From string (line 7) ]" と "bad line content" が結果に含まれること。
        String svg = "<svg>"
                + "<text x=\"1\" y=\"2\">[From string (line 7) ]</text>"
                + "<text>bad line content</text>"
                + "<text>An error has occured...</text>"
                + "</svg>";
        byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);
        String detail = PlantUmlRenderer.extractErrorDetail(bytes);
        assertTrue("detail should contain line marker: " + detail,
                detail.contains("[From string (line 7)"));
        assertTrue("detail should contain error line content: " + detail,
                detail.contains("bad line content"));
    }

    @Test
    public void testExtractErrorDetailExcludesErrorBanner() {
        // "An error has occured" で始まるバナー文言はノイズとして除外されることを確認する。
        String svg = "<svg>"
                + "<text x=\"1\" y=\"2\">[From string (line 7) ]</text>"
                + "<text>bad line content</text>"
                + "<text>An error has occured...</text>"
                + "</svg>";
        byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);
        String detail = PlantUmlRenderer.extractErrorDetail(bytes);
        assertFalse("error banner 'An error has occured' should be excluded: " + detail,
                detail.contains("An error has occured"));
    }

    @Test
    public void testExtractErrorDetailDecodesHtmlEntities() {
        // PlantUML エラー SVG のテキストは HTML エンティティ符号化されている。
        // &#160; (nbsp) がそのまま "&#160;" と生表示されず、デコード (空白) されること。
        String svg = "<svg>"
                + "<text>[From string (line 3) ]</text>"
                + "<text>a &#160; b</text>"
                + "<text>x &lt;T&gt; &amp; y</text>"
                + "</svg>";
        String detail = PlantUmlRenderer.extractErrorDetail(svg.getBytes(StandardCharsets.UTF_8));
        assertFalse("生の &#160; が残ってはならない: " + detail, detail.contains("&#160;"));
        assertFalse("生の &lt; が残ってはならない: " + detail, detail.contains("&lt;"));
        assertTrue("< にデコードされるはず: " + detail, detail.contains("<T>"));
        assertTrue("& にデコードされるはず: " + detail, detail.contains("& y"));
    }

    @Test
    public void testExtractErrorDetailExcludesVersionNag() {
        // PlantUML のバージョン表記と「N 日前のバージョン」催促はノイズとして除外する。
        String svg = "<svg>"
                + "<text>[From string (line 5) ]</text>"
                + "<text>Syntax Error?</text>"
                + "<text>PlantUML 1.2026.2</text>"
                + "<text>&#160;</text>"
                + "<text>This version of PlantUML is 185 days old, so you should "
                + "download the latest version.</text>"
                + "</svg>";
        String detail = PlantUmlRenderer.extractErrorDetail(svg.getBytes(StandardCharsets.UTF_8));
        assertTrue("実際の診断は残すはず: " + detail, detail.contains("Syntax Error"));
        assertFalse("バージョン表記は除外: " + detail, detail.contains("PlantUML 1.2026"));
        assertFalse("更新催促は除外: " + detail, detail.contains("days old"));
        assertFalse("lone nbsp は空になり連結されない: " + detail, detail.contains("| |"));
    }

    @Test
    public void testExtractErrorDetailExcludesUpgradePrompts() {
        // 「consider upgrading from https://plantuml.com/download」等の更新催促も除外する。
        String svg = "<svg>"
                + "<text>[From string (line 5) ]</text>"
                + "<text>Syntax Error?</text>"
                + "<text>consider upgrading from https://plantuml.com/download</text>"
                + "</svg>";
        String detail = PlantUmlRenderer.extractErrorDetail(svg.getBytes(StandardCharsets.UTF_8));
        assertTrue("診断は残すはず: " + detail, detail.contains("Syntax Error"));
        assertFalse("更新催促 (consider upgrading) は除外: " + detail,
                detail.contains("consider upgrading"));
        assertFalse("plantuml.com/download は除外: " + detail,
                detail.contains("plantuml.com/download"));
    }

    @Test
    public void testDecodeEntitiesNumericAndNamed() {
        assertEquals("A B", PlantUmlRenderer.decodeEntities("A&#160;B").replace(' ', ' '));
        assertEquals("<a>", PlantUmlRenderer.decodeEntities("&lt;a&gt;"));
        assertEquals("m&m", PlantUmlRenderer.decodeEntities("m&amp;m"));
        assertEquals("no entities", PlantUmlRenderer.decodeEntities("no entities"));
        // 未知の名前付き参照は保全する。
        assertEquals("&foo;", PlantUmlRenderer.decodeEntities("&foo;"));
    }

    @Test
    public void testExtractErrorLineReturnsLineNumber() {
        // "[From string (line 7) ]" を含む detail 文字列から行番号 7 を返すことを確認する。
        String detail = "[From string (line 7) ] | bad line content";
        assertEquals("should extract line number 7",
                7, PlantUmlRenderer.extractErrorLine(detail));
    }

    @Test
    public void testExtractErrorLineReturnsMinusOneForNullOrEmpty() {
        // null および空文字のとき -1 を返すことを確認する。
        assertEquals("null detail should return -1",
                -1, PlantUmlRenderer.extractErrorLine(null));
        assertEquals("empty detail should return -1",
                -1, PlantUmlRenderer.extractErrorLine(""));
        assertEquals("detail without line marker should return -1",
                -1, PlantUmlRenderer.extractErrorLine("some unrelated text"));
    }

    @Test
    public void testRenderSvgThrowsWithCorrectErrorLineFromStub() {
        // スタブが構文エラー画像相当の SVG を返したとき、投げられた例外の
        // getErrorLine() == 3 であることを確認する。実際のエラー画像に合わせて
        // 位置マーカー + 併記文言 (Syntax Error?) の組み合わせにする
        // (マーカー単独では正常図のコメントと区別できないため判定されない)。
        String errorSvg = "<svg>"
                + "<text>[From string (line 3) ]</text>"
                + "<text>Syntax Error?</text>"
                + "<text>unexpected token</text>"
                + "</svg>";
        PlantUmlRenderer.setRendererImplForTest((puml, out) -> {
            try {
                out.write(errorSvg.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
        try {
            PlantUmlRenderer.renderSvg("@startuml\nclass X\n@enduml\n",
                    new ByteArrayOutputStream());
            fail("Expected PlantUmlRenderFailedException");
        } catch (PlantUmlRenderFailedException expected) {
            assertEquals("getErrorLine() should return 3",
                    3, expected.getErrorLine());
            assertTrue("getErrorDetail() should contain line marker: "
                    + expected.getErrorDetail(),
                    expected.getErrorDetail().contains("[From string (line 3)"));
            // getStderrTail() は空文字列か非 null であること
            assertFalse("getStderrTail() should not be null",
                    expected.getStderrTail() == null);
        } catch (IOException other) {
            fail("Unexpected IOException: " + other);
        }
    }

    // ── レイアウト pragma のディレクティブ判定 (contains 誤検知の回帰) ──

    @Test
    public void testHasLayoutPragmaDirectiveDetectsRealDirective() {
        assertTrue(PlantUmlRenderer.hasLayoutPragmaDirective(
                "@startuml\n!pragma layout smetana\n@enduml\n"));
        assertTrue("行頭の空白は許容",
                PlantUmlRenderer.hasLayoutPragmaDirective(
                        "@startuml\n  !pragma layout elk\n@enduml\n"));
    }

    @Test
    public void testHasLayoutPragmaDirectiveIgnoresEmbeddedText() {
        // 図に埋め込まれたコメント/定数値の "!pragma layout" 文字列 (例: Juml 自身の
        // ソースを図化した場合) をディレクティブと誤認しないこと。
        assertFalse(PlantUmlRenderer.hasLayoutPragmaDirective(
                "@startuml\nclass A {\n"
                        + "  .. @startuml の直後に !pragma layout smetana と、現在の ..\n"
                        + "}\n@enduml\n"));
    }

    @Test
    public void testInjectLayoutNotFooledByEmbeddedPragmaText() {
        PlantUmlRenderer.setGraphvizAvailable(false);
        String puml = "@startuml\nclass A {\n"
                + "  .. !pragma layout smetana の説明コメント ..\n}\n@enduml\n";
        String injected = PlantUmlRenderer.injectLayout(puml);
        assertTrue("埋め込みテキストに騙されず smetana を注入すること: " + injected,
                injected.startsWith("@startuml\n!pragma layout smetana\n"));
    }

    // ── レイアウトエンジンの致命的障害検出 (無音の壊れた SVG の回帰) ──

    @Test
    public void testFatalLayoutErrorCodeDetectsDotExecFailure() {
        String stderr = "java.io.IOException: Cannot run program \"/opt/local/bin/dot\":"
                + " Exec failed, error: 2 (No such file or directory)";
        assertEquals(juml.util.ErrorCode.UML_R008,
                PlantUmlRenderer.fatalLayoutErrorCode(stderr));
    }

    @Test
    public void testFatalLayoutErrorCodeDetectsSmetanaCrash() {
        assertEquals(juml.util.ErrorCode.UML_R002,
                PlantUmlRenderer.fatalLayoutErrorCode(
                        "java.lang.UnsupportedOperationException: xyz\n"
                                + "\tat smetana.core.Macro.UNSUPPORTED(Macro.java:161)"));
        assertEquals(juml.util.ErrorCode.UML_R002,
                PlantUmlRenderer.fatalLayoutErrorCode(
                        "java.lang.UnsupportedOperationException\n"
                                + "\tat gen.lib.common.ns__c.init_rank(ns__c.java:307)"));
    }

    @Test
    public void testFatalLayoutErrorCodeIgnoresBenignWarnings() {
        assertEquals(null, PlantUmlRenderer.fatalLayoutErrorCode(null));
        assertEquals(null, PlantUmlRenderer.fatalLayoutErrorCode(""));
        // Smetana が通常動作で出す警告はスタックトレースを伴わないため無害と判定する
        assertEquals(null, PlantUmlRenderer.fatalLayoutErrorCode(
                "UNSURE_ABOUT SPLINES\nUNSURE_ABOUT ratio"));
    }

    @Test
    public void testRenderSvgThrowsWhenDotExecFailsSilently() throws IOException {
        // PlantUML が dot 起動失敗を握りつぶして "正常な" SVG を返すケースを再現:
        // スタブが有効な SVG を書きつつ stderr に起動失敗を出力する。
        PlantUmlRenderer.setRendererImplForTest((puml, out) -> {
            try {
                out.write("<svg><g>legend only</g></svg>"
                        .getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.err.println(
                    "java.io.IOException: Cannot run program \"/opt/local/bin/dot\"");
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PlantUmlRenderer.renderSvg("@startuml\nclass X\n@enduml\n", out);
            fail("Expected PlantUmlRenderFailedException");
        } catch (PlantUmlRenderFailedException expected) {
            assertEquals(juml.util.ErrorCode.UML_R008, expected.getErrorCode());
            assertEquals("壊れた SVG を書き出していないこと", 0, out.size());
        }
    }

    @Test
    public void testRenderSvgThrowsWhenSmetanaCrashesSilently() throws IOException {
        PlantUmlRenderer.setRendererImplForTest((puml, out) -> {
            try {
                out.write("<svg><g>partial</g></svg>".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.err.println("java.lang.UnsupportedOperationException: 7sgp99x1l3");
            System.err.println("\tat smetana.core.Macro.UNSUPPORTED(Macro.java:161)");
        });
        try {
            PlantUmlRenderer.renderSvg("@startuml\nclass X\n@enduml\n",
                    new ByteArrayOutputStream());
            fail("Expected PlantUmlRenderFailedException");
        } catch (PlantUmlRenderFailedException expected) {
            assertEquals(juml.util.ErrorCode.UML_R002, expected.getErrorCode());
        }
    }

    // ── isErrorSvg の偽陽性防止 (図テキストにエラーマーカー風文字列がある場合) ──

    @Test
    public void testIsErrorSvgIgnoresSourceLineMarkerAlone() {
        // 正常な図のコメントに "[From string (line N)" が含まれるだけでは
        // エラーと判定しない (実例: extractErrorLine の Javadoc を含む図)
        String body = "<svg><text>[From string (line 42) を解釈する</text></svg>";
        assertFalse(PlantUmlRenderer.isErrorSvg(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testIsErrorSvgDetectsSourceLineMarkerWithCorroboration() {
        // 構文エラー画像はバージョンバナーを併記するため、組み合わせで検出できる
        String body = "<svg><text>This version of PlantUML is 166 days old</text>"
                + "<text>[From string (line 7) ]</text></svg>";
        assertTrue(PlantUmlRenderer.isErrorSvg(body.getBytes(StandardCharsets.UTF_8)));
        String syntax = "<svg><text>Syntax Error?</text>"
                + "<text>[From string (line 3) ]</text></svg>";
        assertTrue(PlantUmlRenderer.isErrorSvg(syntax.getBytes(StandardCharsets.UTF_8)));
    }
}
