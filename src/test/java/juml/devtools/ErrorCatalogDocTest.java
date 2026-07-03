// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.devtools;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * {@code docs/errors.md} が {@link ErrorCatalogDoc} の生成結果と一致することを検証する。
 *
 * <p>エラーコードやメッセージを変更したのにドキュメントを再生成し忘れた場合、
 * このテストが CI で落ちる。修正は {@code gradle generateErrorDocs} を実行して
 * 生成結果をコミットするだけでよい。</p>
 */
public class ErrorCatalogDocTest {

    @Test
    public void docsErrorsMdIsUpToDate() throws IOException {
        Path doc = Path.of("docs/errors.md");
        assertTrue("docs/errors.md がありません。`gradle generateErrorDocs` を実行してください",
                Files.exists(doc));
        String onDisk = new String(Files.readAllBytes(doc), StandardCharsets.UTF_8);
        // OS 依存の改行差を吸収して比較する
        String expected = ErrorCatalogDoc.render().replace("\r\n", "\n");
        assertEquals("docs/errors.md が ErrorCode カタログと乖離しています。"
                        + "`gradle generateErrorDocs` で再生成してコミットしてください",
                expected, onDisk.replace("\r\n", "\n"));
    }

    @Test
    public void renderContainsEveryId() {
        String doc = ErrorCatalogDoc.render();
        for (juml.util.ErrorCode c : juml.util.ErrorCode.values()) {
            if (c.hasId()) {
                assertTrue("missing " + c.getId(), doc.contains("### " + c.getId()));
            }
        }
    }
}
