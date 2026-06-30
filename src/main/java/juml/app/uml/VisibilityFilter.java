// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * クラス図の可視性フィルタ。
 * 生成側では {@code publicOnly} などのトグルに展開して反映する。
 */
public enum VisibilityFilter {
    /** 全可視性を表示 (デフォルト)。 */
    ALL,
    /** {@code public} 可視性のみを表示。 */
    PUBLIC_ONLY,
    /** {@code package}-private 以上 (public/protected/package) を表示。 */
    PACKAGE_AND_ABOVE
}
