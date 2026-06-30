// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.refs;

import java.util.Objects;

/**
 * 逆参照インデックス {@link ReferenceIndex} のキー。
 *
 * <p>シンボル種別 ({@link Kind}) と完全修飾名で同一性を判定する。
 * {@link Kind#METHOD} のキーは {@link #getMember()} にメソッド単純名を保持する
 * (シグネチャを含めない簡易キー。同名オーバーロードはまとめて 1 エントリになる)。
 * {@link Kind#FIELD} も同様にフィールド名を保持する。</p>
 *
 * <p>シグネチャを含めた厳密マッチが必要な場合は別途キーを拡張する余地を残すため、
 * {@link #getSignature()} は将来用に保持しているが現状は常に null。</p>
 */
public final class ReferenceKey {

    /** シンボル種別。 */
    public enum Kind { CLASS, METHOD, FIELD }

    private final Kind kind;
    private final String ownerFqn;
    private final String member;
    private final String signature;

    private ReferenceKey(Kind kind, String ownerFqn, String member, String signature) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.ownerFqn = Objects.requireNonNull(ownerFqn, "ownerFqn");
        this.member = member;
        this.signature = signature;
    }

    /** クラスシンボルキー。 */
    public static ReferenceKey ofClass(String fqn) {
        return new ReferenceKey(Kind.CLASS, fqn, null, null);
    }

    /** メソッドシンボルキー (オーナーの FQN + メソッド単純名)。シグネチャ無し。 */
    public static ReferenceKey ofMethod(String ownerFqn, String methodName) {
        return new ReferenceKey(Kind.METHOD, ownerFqn, methodName, null);
    }

    /**
     * シグネチャ付きメソッドシンボルキー。{@code signature} を同一性に含めるので、
     * 同名オーバーロードは別エントリになる (シンボル解決で型が確定したときに使う)。
     */
    public static ReferenceKey ofMethod(String ownerFqn, String methodName, String signature) {
        return new ReferenceKey(Kind.METHOD, ownerFqn, methodName, signature);
    }

    /** フィールドシンボルキー (オーナーの FQN + フィールド名)。 */
    public static ReferenceKey ofField(String ownerFqn, String fieldName) {
        return new ReferenceKey(Kind.FIELD, ownerFqn, fieldName, null);
    }

    public Kind getKind() {
        return kind;
    }

    public String getOwnerFqn() {
        return ownerFqn;
    }

    /** メソッド名 / フィールド名。{@link Kind#CLASS} の場合は null。 */
    public String getMember() {
        return member;
    }

    /** シグネチャ。{@link #ofMethod(String, String, String)} で設定したときのみ非 null。 */
    public String getSignature() {
        return signature;
    }

    /** 文字列表現。例: {@code class:com.foo.Bar}, {@code method:com.foo.Bar#doIt}。 */
    @Override
    public String toString() {
        switch (kind) {
            case CLASS:
                return "class:" + ownerFqn;
            case METHOD:
                return "method:" + ownerFqn + "#" + (member == null ? "" : member);
            case FIELD:
                return "field:" + ownerFqn + "#" + (member == null ? "" : member);
            default:
                return kind + ":" + ownerFqn;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReferenceKey)) {
            return false;
        }
        ReferenceKey that = (ReferenceKey) o;
        return kind == that.kind
                && Objects.equals(ownerFqn, that.ownerFqn)
                && Objects.equals(member, that.member)
                && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, ownerFqn, member, signature);
    }
}
