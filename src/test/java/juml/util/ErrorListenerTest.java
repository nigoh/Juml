// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * ErrorListener のユニットテスト。
 */
public class ErrorListenerTest {

    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    private PrintStream origErr;

    @Before
    public void hookStderr() {
        origErr = System.err;
        System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    @After
    public void restoreStderr() {
        System.setErr(origErr);
    }

    private String errText() {
        return errBuf.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void testSilentDoesNothing() {
        ErrorListener.silent().onError("foo.java", 1, "bad");
        assertEquals("", errText());
    }

    @Test
    public void testStderrFormatsWithSourceAndLine() {
        ErrorListener.stderr().onError("Foo.java", 42, "unexpected token");
        assertTrue(errText(), errText().contains("Foo.java:42: unexpected token"));
    }

    @Test
    public void testStderrFormatsWithSourceOnly() {
        ErrorListener.stderr().onError("Foo.java", -1, "io fail");
        assertTrue(errText(), errText().contains("Foo.java: io fail"));
        assertFalse(errText().contains(":-1"));
    }

    @Test
    public void testStderrFormatsWithLineOnly() {
        ErrorListener.stderr().onError(null, 5, "missing brace");
        assertTrue(errText(), errText().contains("line 5: missing brace"));
    }

    @Test
    public void testStderrFormatsWithMessageOnly() {
        ErrorListener.stderr().onError(null, -1, "summary line");
        assertTrue(errText(), errText().contains("summary line"));
    }

    @Test
    public void testCollectingAccumulates() {
        List<String> sink = new ArrayList<>();
        ErrorListener l = ErrorListener.collecting(sink);
        l.onError("A.java", 1, "a");
        l.onError("B.java", 2, "b");
        l.onError(null, -1, "summary");
        assertEquals(3, sink.size());
        assertEquals("A.java:1: a", sink.get(0));
        assertEquals("B.java:2: b", sink.get(1));
        assertEquals("summary", sink.get(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCollectingNullSink() {
        ErrorListener.collecting(null);
    }

    @Test
    public void testCustomLambda() {
        StringBuilder sb = new StringBuilder();
        ErrorListener custom = (code, src, ln, msg) -> sb.append(src).append('@').append(ln)
                .append(':').append(msg);
        custom.onError("X", 10, "y");
        assertEquals("X@10:y", sb.toString());
    }

    @Test
    public void testThreeArgOverloadDelegatesWithNone() {
        List<ErrorCode> codes = new ArrayList<>();
        ErrorListener l = (code, src, ln, msg) -> codes.add(code);
        l.onError("A.java", 1, "info");
        assertEquals(1, codes.size());
        assertEquals(ErrorCode.NONE, codes.get(0));
    }

    @Test
    public void testStderrPrependsErrorId() {
        ErrorListener.stderr().onError(ErrorCode.PRJ_005, "Foo.java", 42, "bad token");
        assertTrue(errText(), errText().contains("[PRJ-005] Foo.java:42: bad token"));
    }

    @Test
    public void testCollectingPrependsErrorId() {
        List<String> sink = new ArrayList<>();
        ErrorListener.collecting(sink).onError(ErrorCode.PRJ_006, "IFoo.aidl", 3, "bad aidl");
        assertEquals("[PRJ-006] IFoo.aidl:3: bad aidl", sink.get(0));
    }

    @Test
    public void testNoneCodeHasNoIdPrefix() {
        List<String> sink = new ArrayList<>();
        ErrorListener.collecting(sink).onError(ErrorCode.NONE, "A.java", 1, "note");
        assertEquals("A.java:1: note", sink.get(0));
    }
}
