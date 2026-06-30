// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SELinux policy ({@code *.te}) の宣言・ルール 1 件分。
 *
 * <p>抽出対象:</p>
 * <ul>
 *   <li>{@link Kind#TYPE_DECL}: {@code type foo_t, attr1, attr2;}</li>
 *   <li>{@link Kind#TYPE_ATTRIBUTE}: {@code typeattribute foo_t coredomain;}</li>
 *   <li>{@link Kind#ALLOW}: {@code allow X Y:CLASS { ops };}</li>
 *   <li>{@link Kind#NEVERALLOW}: {@code neverallow X Y:CLASS { ops };}</li>
 *   <li>{@link Kind#DONTAUDIT}: {@code dontaudit X Y:CLASS { ops };}</li>
 *   <li>{@link Kind#AUDITALLOW}: {@code auditallow X Y:CLASS { ops };}</li>
 * </ul>
 */
public final class SelinuxRule {

    public enum Kind {
        TYPE_DECL, TYPE_ATTRIBUTE,
        ALLOW, NEVERALLOW, DONTAUDIT, AUDITALLOW
    }

    private final Kind kind;
    private final String subject;
    private final String target;
    private final String objectClass;
    private final List<String> permissions;
    private final boolean complement;
    private final List<String> attributes;
    private final String file;
    private final int line;

    private SelinuxRule(Builder b) {
        this.kind = b.kind;
        this.subject = nz(b.subject);
        this.target = nz(b.target);
        this.objectClass = nz(b.objectClass);
        this.permissions = Collections.unmodifiableList(new ArrayList<>(b.permissions));
        this.complement = b.complement;
        this.attributes = Collections.unmodifiableList(new ArrayList<>(b.attributes));
        this.file = nz(b.file);
        this.line = b.line;
    }

    public Kind getKind() { return kind; }
    /** {@code TYPE_DECL} ではタイプ名、その他のルールではサブジェクトドメイン。 */
    public String getSubject() { return subject; }
    /** allow/neverallow 系のターゲット。{@code TYPE_DECL} では空文字。 */
    public String getTarget() { return target; }
    /** allow {@code X Y:CLASS { ... }} の {@code CLASS} 部。 */
    public String getObjectClass() { return objectClass; }
    /** allow 系の {@code { permissions }}。{@code TYPE_DECL} では空。 */
    public List<String> getPermissions() { return permissions; }
    /**
     * permission 集合が補集合 ({@code ~{ ... }}) かどうか。
     * {@code true} のとき {@link #getPermissions()} は「これら以外を許可」の意味になる。
     */
    public boolean isComplement() { return complement; }
    /** {@code TYPE_DECL} の {@code attr1, attr2}, または {@code typeattribute} 引数。 */
    public List<String> getAttributes() { return attributes; }
    public String getFile() { return file; }
    public int getLine() { return line; }

    private static String nz(String s) { return s == null ? "" : s; }

    public static Builder builder(Kind kind) { return new Builder(kind); }

    public static final class Builder {
        private final Kind kind;
        private String subject;
        private String target;
        private String objectClass;
        private final List<String> permissions = new ArrayList<>();
        private boolean complement;
        private final List<String> attributes = new ArrayList<>();
        private String file;
        private int line;

        Builder(Kind kind) { this.kind = kind; }
        public Builder subject(String s) { this.subject = s; return this; }
        public Builder target(String s) { this.target = s; return this; }
        public Builder objectClass(String s) { this.objectClass = s; return this; }
        public Builder addPermission(String s) {
            if (s != null && !s.isEmpty()) permissions.add(s); return this;
        }
        public Builder complement(boolean c) { this.complement = c; return this; }
        public Builder addAttribute(String s) {
            if (s != null && !s.isEmpty()) attributes.add(s); return this;
        }
        public Builder file(String s) { this.file = s; return this; }
        public Builder line(int n) { this.line = n; return this; }
        public SelinuxRule build() { return new SelinuxRule(this); }
    }

    @Override
    public String toString() {
        switch (kind) {
            case TYPE_DECL:
                return "type " + subject
                        + (attributes.isEmpty() ? "" : ", " + String.join(", ", attributes));
            case TYPE_ATTRIBUTE:
                return "typeattribute " + subject + " "
                        + String.join(", ", attributes);
            default:
                return kind.name().toLowerCase() + " " + subject + " "
                        + target + ":" + objectClass + (complement ? " ~{ " : " { ")
                        + String.join(" ", permissions) + " }";
        }
    }
}
