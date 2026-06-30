// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.refs;

import java.util.Objects;

/**
 * シンボルが「使われている場所」を表す。
 *
 * <p>{@link ReferenceIndex} で 1 つの {@link ReferenceKey} に対し複数の
 * {@code ReferenceSite} が紐付く。各 site は「どのクラスのどのメソッドが、どの種類で参照したか」
 * を持つ。</p>
 */
public final class ReferenceSite {

    /** 参照種別。 */
    public enum Kind {
        /** メソッド呼び出し {@code foo.bar()}。 */
        CALL,
        /** クラス継承 {@code extends X}。 */
        EXTENDS,
        /** インタフェース実装 {@code implements X}。 */
        IMPLEMENTS,
        /** フィールド型・メソッドの戻り型・引数型・throws 型などでの参照。 */
        TYPE_REFERENCE,
        /** アノテーション付与 {@code @X}。 */
        ANNOTATION,
        /** import 文での参照。 */
        IMPORT,
        /** 名前解決ヒューリスティック (.kt や曖昧解決) 由来。 */
        HEURISTIC
    }

    private final String callerFqn;
    private final String callerMethod;
    private final String file;
    private final int lineHint;
    private final Kind kind;

    public ReferenceSite(String callerFqn, String callerMethod,
                         String file, int lineHint, Kind kind) {
        this.callerFqn = callerFqn == null ? "" : callerFqn;
        this.callerMethod = callerMethod == null ? "" : callerMethod;
        this.file = file == null ? "" : file;
        this.lineHint = lineHint;
        this.kind = kind == null ? Kind.CALL : kind;
    }

    /** 参照元のクラス FQN。 */
    public String getCallerFqn() {
        return callerFqn;
    }

    /**
     * 参照元のメソッド単純名 (引数なし、括弧なし)。
     * クラスヘッダでの参照 (extends/implements/field/annotation) は空文字。
     */
    public String getCallerMethod() {
        return callerMethod;
    }

    /** 参照元のソースファイル (絶対 or 相対パス。空文字なら未取得)。 */
    public String getFile() {
        return file;
    }

    /** 行番号ヒント。0 以下なら未取得。 */
    public int getLineHint() {
        return lineHint;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReferenceSite)) {
            return false;
        }
        ReferenceSite that = (ReferenceSite) o;
        return lineHint == that.lineHint
                && Objects.equals(callerFqn, that.callerFqn)
                && Objects.equals(callerMethod, that.callerMethod)
                && Objects.equals(file, that.file)
                && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(callerFqn, callerMethod, file, lineHint, kind);
    }

    @Override
    public String toString() {
        return callerFqn + (callerMethod.isEmpty() ? "" : "." + callerMethod)
                + " (" + kind + (file.isEmpty() ? "" : " @ " + file) + ")";
    }
}
