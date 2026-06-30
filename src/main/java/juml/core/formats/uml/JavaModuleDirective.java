// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code module-info.java} に書かれた 1 つのディレクティブ。
 *
 * <p>JLS §7.7 で定義される下記 5 種を扱う:</p>
 * <ul>
 *   <li>{@code requires [transitive] [static] Mod;}</li>
 *   <li>{@code exports Pkg [to Mod1, Mod2];}</li>
 *   <li>{@code opens Pkg [to Mod1, Mod2];}</li>
 *   <li>{@code uses Service;}</li>
 *   <li>{@code provides Service with Impl1, Impl2;}</li>
 * </ul>
 *
 * <p>各ディレクティブ共通の構造として、主語となる名前 ({@link #getName()})、
 * 装飾子 ({@link #getModifiers()}、{@code requires} の {@code transitive} /
 * {@code static} など)、ターゲット ({@link #getTargets()}、{@code to ...} /
 * {@code with ...} の右側) を保持する。</p>
 */
public final class JavaModuleDirective {

    /** ディレクティブ種別。 */
    public enum Kind { REQUIRES, EXPORTS, OPENS, USES, PROVIDES }

    private final Kind kind;
    private final String name;
    private final List<String> modifiers;
    private final List<String> targets;

    public JavaModuleDirective(Kind kind, String name,
                                List<String> modifiers, List<String> targets) {
        if (kind == null) {
            throw new IllegalArgumentException("kind is null");
        }
        this.kind = kind;
        this.name = name == null ? "" : name;
        this.modifiers = modifiers == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(modifiers));
        this.targets = targets == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(targets));
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * ディレクティブの主語。
     * <ul>
     *   <li>{@link Kind#REQUIRES}: 必要モジュール名 (例: {@code java.sql})</li>
     *   <li>{@link Kind#EXPORTS} / {@link Kind#OPENS}: 公開対象パッケージ名</li>
     *   <li>{@link Kind#USES}: サービスインタフェースの完全修飾名</li>
     *   <li>{@link Kind#PROVIDES}: サービスインタフェースの完全修飾名</li>
     * </ul>
     */
    public String getName() {
        return name;
    }

    /**
     * 装飾子のリスト (不変)。
     * <ul>
     *   <li>{@link Kind#REQUIRES}: {@code "transitive"} / {@code "static"}</li>
     *   <li>その他のディレクティブでは空</li>
     * </ul>
     */
    public List<String> getModifiers() {
        return modifiers;
    }

    /**
     * ターゲットのリスト (不変)。
     * <ul>
     *   <li>{@link Kind#EXPORTS} / {@link Kind#OPENS}: {@code to} の右側のモジュール名</li>
     *   <li>{@link Kind#PROVIDES}: {@code with} の右側の実装クラス完全修飾名</li>
     *   <li>{@link Kind#REQUIRES} / {@link Kind#USES}: 常に空</li>
     * </ul>
     */
    public List<String> getTargets() {
        return targets;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(kind.name().toLowerCase());
        for (String m : modifiers) {
            sb.append(' ').append(m);
        }
        sb.append(' ').append(name);
        if (!targets.isEmpty()) {
            sb.append(kind == Kind.PROVIDES ? " with " : " to ");
            for (int i = 0; i < targets.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(targets.get(i));
            }
        }
        return sb.toString();
    }
}
