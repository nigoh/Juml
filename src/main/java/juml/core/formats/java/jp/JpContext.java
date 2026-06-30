// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import juml.core.formats.uml.JavaClassInfo;

import java.util.List;

/**
 * 1 つのコンパイル単位を解析する間の共有コンテキスト。
 *
 * <p>パッケージ名・import 一覧と、ローカルクラス/ネスト型を「別 top-level エントリ」として
 * 追記するための出力リストを保持する。{@code headersOnly} が true のときはメソッド本体の
 * statement tree を構築しない（軽量モード）。</p>
 */
final class JpContext {

    final String packageName;
    final List<String> imports;
    final List<JavaClassInfo> out;
    final boolean headersOnly;
    final JpComments comments;
    /** シンボル解決 (Call.resolvedOwnerFqn の設定) を行うか。solver がある FULL 解析時のみ true。 */
    final boolean resolve;
    /** ローカルクラスの enclosingClass に使う、現在解析中の型の単純名チェーン。 */
    String currentEnclosing;

    JpContext(String packageName, List<String> imports, List<JavaClassInfo> out,
              boolean headersOnly, JpComments comments, boolean resolve) {
        this.packageName = packageName;
        this.imports = imports;
        this.out = out;
        this.headersOnly = headersOnly;
        this.comments = comments;
        this.resolve = resolve;
    }
}
