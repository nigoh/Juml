// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

/**
 * 型参照文字列 (単純名もしくは FQN) を、指定クラスのコンテキストで完全修飾名へ解決する
 * 抽象。{@code juml.core.refs.NameResolver} を {@code uml} パッケージから直接参照すると
 * 循環依存になるため、呼び出し側 (CLI/GUI) がラムダで橋渡しする。
 *
 * <p>解決できない場合は入力をそのまま返してよい (継承先の標準/外部判定では FQN 化できない
 * 単純名は {@code UNKNOWN} として扱われる)。</p>
 */
@FunctionalInterface
public interface TypeRefResolver {

    /**
     * {@code typeRef} を {@code owner} の import/package コンテキストで FQN に解決する。
     *
     * @param typeRef 単純名または FQN (ジェネリクスは含まないことを期待)
     * @param owner   解決の起点となるクラス
     * @return 解決済み FQN。解決失敗時は {@code typeRef} をそのまま返してよい
     */
    String resolveFqn(String typeRef, JavaClassInfo owner);
}
