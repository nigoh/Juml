// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link OptionParser} の引数解析を検証する。値必須オプションの値欠落検出
 * ({@code -o} が末尾 / 次が別フラグ) と {@code --opt=val} / {@code -x=val} 形式のサポートを
 * 中心に、以前の「無言で握り潰し標準出力へダンプ」する挙動が直っていることを確認する。
 */
public class OptionParserTest {

    private static Option out() {
        return new Option("o", "output", true);
    }

    private static Option flag() {
        return new Option("c", "class-diagram", false);
    }

    @Test
    public void spaceSeparatedValue_isParsed() throws Exception {
        Option out = out();
        new OptionParser(new Option[]{out, flag()}).parse(new String[]{"-o", "a.svg", "proj"});
        assertTrue(out.isSet());
        assertEquals("a.svg", out.getArguments().getLast());
    }

    @Test
    public void longEqualsValue_isParsed() throws Exception {
        Option out = out();
        new OptionParser(new Option[]{out, flag()}).parse(new String[]{"--output=a.svg"});
        assertTrue("--opt=val 形式で値が取れるはず", out.isSet());
        assertEquals("a.svg", out.getArguments().getLast());
    }

    @Test
    public void shortEqualsValue_isParsed() throws Exception {
        Option out = out();
        new OptionParser(new Option[]{out, flag()}).parse(new String[]{"-o=a.svg"});
        assertTrue("-x=val 形式で値が取れるはず", out.isSet());
        assertEquals("a.svg", out.getArguments().getLast());
    }

    @Test
    public void valueRequiringOption_atEnd_throwsMissingArgument() {
        Option out = out();
        try {
            new OptionParser(new Option[]{out, flag()}).parse(new String[]{"proj", "-o"});
            fail("末尾の値欠落は MissingArgumentException になるはず");
        } catch (MissingArgumentException ex) {
            assertEquals("--output", ex.getOption());
            assertEquals(ErrorCode.SYS_005, ex.getErrorCode());
        } catch (UnknownOptionException ex) {
            fail("UnknownOptionException ではなく MissingArgumentException のはず");
        }
        assertFalse("値が付かなかったので set されないはず", out.isSet());
    }

    @Test
    public void valueRequiringOption_followedByKnownFlag_throwsMissingArgument() {
        Option out = out();
        Option cls = flag();
        try {
            new OptionParser(new Option[]{out, cls}).parse(new String[]{"-o", "-c", "proj"});
            fail("次が登録済みフラグなら値欠落として弾くはず");
        } catch (MissingArgumentException ex) {
            assertEquals("--output", ex.getOption());
        } catch (UnknownOptionException ex) {
            fail("MissingArgumentException のはず");
        }
        // 別フラグを値として飲み込んでいない (= -c は値化されていない)。
        assertFalse(out.isSet());
    }

    @Test
    public void negativeNumberValue_isAcceptedNotTreatedAsOption() throws Exception {
        // "-1" は登録オプションではないので、値として受け付ける (誤って値欠落にしない)。
        Option depth = new Option(null, "seq-depth", true);
        new OptionParser(new Option[]{depth, flag()}).parse(new String[]{"--seq-depth", "-1"});
        assertTrue(depth.isSet());
        assertEquals("-1", depth.getArguments().getLast());
    }

    @Test
    public void doubleDash_stopsOptionParsing() throws Exception {
        Option cls = flag();
        OptionParser p = new OptionParser(new Option[]{out(), cls});
        p.parse(new String[]{"--", "-c", "-o"});
        assertFalse("-- 以降はオプション扱いしない", cls.isSet());
        assertEquals(java.util.Arrays.asList("-c", "-o"), p.getArguments());
    }

    @Test
    public void unknownOption_throwsUnknownOption() {
        try {
            new OptionParser(new Option[]{out()}).parse(new String[]{"--nope"});
            fail("未知オプションは UnknownOptionException のはず");
        } catch (UnknownOptionException ex) {
            assertEquals("--nope", ex.getOption());
        } catch (MissingArgumentException ex) {
            fail("UnknownOptionException のはず");
        }
    }
}
