// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * クラス図の関連線の種別。
 *
 * <p>{@link DiagramScope} から関連線フィルタとして使い、生成側
 * ({@code PlantUmlClassDiagram.Options}) では {@code showInheritance} /
 * {@code showImplementations} / {@code showUsageRelations} の 3 トグルに展開して反映する。</p>
 */
public enum RelationKind {
    /** {@code extends} による継承。 */
    INHERITANCE,
    /** {@code implements} によるインタフェース実装。 */
    IMPLEMENTATION,
    /** フィールド型・パラメータ型などからの利用関係。 */
    USAGE
}
