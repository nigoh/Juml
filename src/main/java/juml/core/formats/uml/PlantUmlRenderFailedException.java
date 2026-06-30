// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.io.IOException;

/**
 * PlantUML のレンダリング失敗 (Smetana のレイアウト例外等) を表す例外。
 *
 * <p>同梱 PlantUML は内部のレイアウトエンジン (Smetana) が落ちた場合、
 * 例外を握り潰してフォールバックの「An error has occured」SVG を出力する。
 * その出力をそのまま保存するとユーザは壊れた SVG を有効なものと誤認するため、
 * Juml 側でフォールバック SVG を検出して本例外に変換する。</p>
 */
public final class PlantUmlRenderFailedException extends IOException {

    private static final long serialVersionUID = 1L;

    public PlantUmlRenderFailedException(String message) {
        super(message);
    }
}
