// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.ErrorCode;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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
                new RuntimeException("dummy error"), false);
        assertNull("dump() with null puml should return null (no file written)",
                result);
    }

    @Test
    public void testDumpNullPumlAndNullErrorReturnsNull() {
        // puml=null かつ error=null でも例外を投げずに null を返すことを確認する。
        File result = RenderFailureLog.dump("test-label", null, null, false);
        assertNull("dump() with null puml and null error should return null",
                result);
    }

    @Test
    public void testDumpEmptyPumlReturnsNull() {
        // puml が空文字列のときも null を返すことを確認する (空文字はファイル書き込み対象外)。
        File result = RenderFailureLog.dump("test-label", "",
                new RuntimeException("dummy error"), false);
        assertNull("dump() with empty puml should return null",
                result);
    }

    @Test
    public void testDumpEditorTabDoesNotWriteFile() {
        // エディタタブ (editor=true) は編集途中テキストの失敗が頻発するため、
        // ファイル保存を抑制する (AppLog への記録のみ)。非 null の puml でも null を返す。
        File result = RenderFailureLog.dump("editor-tab",
                "@startuml\nclass {\n@enduml", new RuntimeException("syntax"), true);
        assertNull("editor タブの dump はファイルを書かず null を返すべき", result);
    }

    // ── classify: 失敗原因 → エラー ID の対応 ─────────────────────────

    @Test
    public void testClassifySyntaxErrorMapsToRenderOrEditorCode() {
        Throwable syntax = new juml.core.formats.uml.PlantUmlRenderFailedException(
                ErrorCode.UML_R001, "syntax", 3, "Syntax Error?", "");
        assertSame(ErrorCode.UML_R001, RenderFailureLog.classify(syntax, false));
        // エディタタブでは編集内容起因なので UML-E 系へ読み替える
        assertSame(ErrorCode.UML_E001, RenderFailureLog.classify(syntax, true));
    }

    @Test
    public void testClassifyLayoutErrorMapsToRenderOrEditorCode() {
        Throwable layout = new juml.core.formats.uml.PlantUmlRenderFailedException(
                ErrorCode.UML_R002, "layout", -1, "", "");
        assertSame(ErrorCode.UML_R002, RenderFailureLog.classify(layout, false));
        assertSame(ErrorCode.UML_E002, RenderFailureLog.classify(layout, true));
    }

    @Test
    public void testClassifyOutOfMemory() {
        assertSame(ErrorCode.UML_R003,
                RenderFailureLog.classify(new OutOfMemoryError("heap"), false));
        // 原因チェーンの奥の OOM も拾う
        assertSame(ErrorCode.UML_R003, RenderFailureLog.classify(
                new RuntimeException("wrap", new OutOfMemoryError("heap")), false));
    }

    @Test
    public void testClassifyUnknownFallsBackToR007() {
        assertSame(ErrorCode.UML_R007,
                RenderFailureLog.classify(new RuntimeException("x"), false));
        assertSame(ErrorCode.UML_R007, RenderFailureLog.classify(null, false));
    }
}
