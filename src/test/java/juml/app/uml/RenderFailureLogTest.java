// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertNull;

/**
 * {@link RenderFailureLog} のヘッドレス安全な動作を確認する。
 *
 * <p>Swing コンポーネントを使わないため headless 環境でも問題なく動く。
 * ファイル書き込み ({@code logs/render-failed-*.puml}) の検証は
 * リポジトリを汚さないよう省略し、最小ケース (puml=null) のみ検証する。</p>
 */
public class RenderFailureLogTest {

    @Test
    public void testDumpNullPumlReturnsNullWithoutThrowing() {
        // puml が null のとき、ファイルを書かずに null を返すことを確認する。
        // "生成前に失敗" ケース: 例外はどんな Throwable でもよい。
        File result = RenderFailureLog.dump("test-label", null,
                new RuntimeException("dummy error"));
        assertNull("dump() with null puml should return null (no file written)",
                result);
    }

    @Test
    public void testDumpNullPumlAndNullErrorReturnsNull() {
        // puml=null かつ error=null でも例外を投げずに null を返すことを確認する。
        File result = RenderFailureLog.dump("test-label", null, null);
        assertNull("dump() with null puml and null error should return null",
                result);
    }

    @Test
    public void testDumpEmptyPumlReturnsNull() {
        // puml が空文字列のときも null を返すことを確認する (空文字はファイル書き込み対象外)。
        File result = RenderFailureLog.dump("test-label", "",
                new RuntimeException("dummy error"));
        assertNull("dump() with empty puml should return null",
                result);
    }
}
