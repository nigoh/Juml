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
            assertTrue("message should mention PlantUML layout: " + msg,
                    msg.contains("PlantUML layout error"));
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
}
