// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.ExternalPackageMatcher;
import juml.core.formats.uml.UmlGenerator;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * クラス図 / パッケージ図の表示範囲を絞り込むためのスコープ指定 (不変)。
 *
 * <p>適用順序:</p>
 * <ol>
 *   <li>{@link #includedModules} に該当するクラスのみ (空ならフィルタなし)</li>
 *   <li>{@link #includedPackages} に該当するクラスのみ (前方一致 + 完全一致、空ならフィルタなし)</li>
 *   <li>{@link #excludedPackages} に該当するクラスを除外 (前方一致 + 完全一致、空なら除外なし)</li>
 *   <li>{@link #excludeExternalLibraries} が true なら、外部 JAR 由来クラスや
 *       {@code java.*}/{@code android.*}/{@code kotlin.*} 等を除外</li>
 *   <li>{@link #classNameRegex} に simpleName/qualifiedName のいずれかが一致するクラスのみ
 *       (null ならフィルタなし)</li>
 *   <li>{@link #seedQualifiedNames} のクラスを起点に、継承/実装/フィールド型を辿って
 *       {@link #neighborHops} 以内のクラスのみ (空ならフィルタなし)</li>
 *   <li>{@link #maxClasses} を超えるクラスは末尾から切り捨て、警告フッタを出す
 *       (0 以下なら無制限)</li>
 * </ol>
 *
 * <p>関連線フィルタ ({@link #relationKinds})・可視性フィルタ ({@link #visibilityFilter})・
 * パースモード ({@link #parseMode}) はクラスのリスト絞り込みではなく、生成側の
 * {@code PlantUmlClassDiagram.Options} に転写して適用される。</p>
 */
public final class DiagramScope {

    /** 何も絞り込まない既定スコープ。 */
    public static final DiagramScope ALL = new Builder().build();

    private final Set<String> includedPackages;
    private final Set<String> includedModules;
    private final Set<String> excludedPackages;
    private final boolean excludeExternalLibraries;
    private final Set<String> externalPackagePrefixes;
    private final Pattern classNameRegex;
    private final Set<String> seedQualifiedNames;
    private final int neighborHops;
    private final int maxClasses;
    private final EnumSet<RelationKind> relationKinds;
    private final VisibilityFilter visibilityFilter;
    private final UmlGenerator.ParseMode parseMode;
    private final DiagramPreset preset;
    /** フォーカス強調モードの焦点クラス FQN（空で無効）。フィルタではなく描画強調にのみ効く。 */
    private final String focusClass;

    private DiagramScope(Builder b) {
        this.includedPackages = Collections.unmodifiableSet(new LinkedHashSet<>(b.includedPackages));
        this.includedModules = Collections.unmodifiableSet(new LinkedHashSet<>(b.includedModules));
        this.excludedPackages = Collections.unmodifiableSet(new LinkedHashSet<>(b.excludedPackages));
        this.excludeExternalLibraries = b.excludeExternalLibraries;
        this.externalPackagePrefixes = (b.externalPackagePrefixes == null
                || b.externalPackagePrefixes.isEmpty())
                ? ExternalPackageMatcher.DEFAULT_PREFIXES
                : Collections.unmodifiableSet(new LinkedHashSet<>(b.externalPackagePrefixes));
        this.classNameRegex = b.classNameRegex;
        this.seedQualifiedNames = Collections.unmodifiableSet(
                new LinkedHashSet<>(b.seedQualifiedNames));
        this.neighborHops = Math.max(0, b.neighborHops);
        this.maxClasses = b.maxClasses;
        this.relationKinds = (b.relationKinds == null || b.relationKinds.isEmpty())
                ? EnumSet.allOf(RelationKind.class)
                : EnumSet.copyOf(b.relationKinds);
        this.visibilityFilter = b.visibilityFilter == null ? VisibilityFilter.ALL : b.visibilityFilter;
        this.parseMode = b.parseMode;
        this.preset = b.preset == null ? DiagramPreset.CUSTOM : b.preset;
        this.focusClass = b.focusClass == null ? "" : b.focusClass;
    }

    public Set<String> getIncludedPackages() {
        return includedPackages;
    }

    public Set<String> getIncludedModules() {
        return includedModules;
    }

    public Set<String> getExcludedPackages() {
        return excludedPackages;
    }

    public boolean isExcludeExternalLibraries() {
        return excludeExternalLibraries;
    }

    public Set<String> getExternalPackagePrefixes() {
        return externalPackagePrefixes;
    }

    public Pattern getClassNameRegex() {
        return classNameRegex;
    }

    public Set<String> getSeedQualifiedNames() {
        return seedQualifiedNames;
    }

    public int getNeighborHops() {
        return neighborHops;
    }

    public int getMaxClasses() {
        return maxClasses;
    }

    public EnumSet<RelationKind> getRelationKinds() {
        return EnumSet.copyOf(relationKinds);
    }

    public VisibilityFilter getVisibilityFilter() {
        return visibilityFilter;
    }

    /** {@link UmlGenerator.ParseMode} 指定 (null なら呼び出し側の既定に従う)。 */
    public UmlGenerator.ParseMode getParseMode() {
        return parseMode;
    }

    public DiagramPreset getPreset() {
        return preset;
    }

    /** フォーカス強調モードの焦点クラス FQN（空で無効）。 */
    public String getFocusClass() {
        return focusClass;
    }

    /** 何のフィルタも持たない (= 全件通過) ならば true。 */
    public boolean isEmpty() {
        return includedPackages.isEmpty()
                && includedModules.isEmpty()
                && excludedPackages.isEmpty()
                && !excludeExternalLibraries
                && classNameRegex == null
                && seedQualifiedNames.isEmpty()
                && maxClasses <= 0
                && relationKinds.containsAll(EnumSet.allOf(RelationKind.class))
                && visibilityFilter == VisibilityFilter.ALL
                && parseMode == null;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 既存スコープの値を引き継いだビルダを返す。プリセット適用や履歴 push に使う。 */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.includedPackages.addAll(includedPackages);
        b.includedModules.addAll(includedModules);
        b.excludedPackages.addAll(excludedPackages);
        b.excludeExternalLibraries = excludeExternalLibraries;
        b.externalPackagePrefixes.addAll(externalPackagePrefixes);
        b.classNameRegex = classNameRegex;
        b.seedQualifiedNames.addAll(seedQualifiedNames);
        b.neighborHops = neighborHops;
        b.maxClasses = maxClasses;
        b.relationKinds = EnumSet.copyOf(relationKinds);
        b.visibilityFilter = visibilityFilter;
        b.parseMode = parseMode;
        b.preset = preset;
        b.focusClass = focusClass;
        return b;
    }

    /** ビルダ。 */
    public static final class Builder {
        private final Set<String> includedPackages = new LinkedHashSet<>();
        private final Set<String> includedModules = new LinkedHashSet<>();
        private final Set<String> excludedPackages = new LinkedHashSet<>();
        private boolean excludeExternalLibraries;
        private final Set<String> externalPackagePrefixes = new LinkedHashSet<>();
        private Pattern classNameRegex;
        private final Set<String> seedQualifiedNames = new LinkedHashSet<>();
        private int neighborHops;
        private int maxClasses;
        private EnumSet<RelationKind> relationKinds;
        private VisibilityFilter visibilityFilter;
        private UmlGenerator.ParseMode parseMode;
        private DiagramPreset preset;
        private String focusClass = "";

        public Builder includePackage(String pkg) {
            if (pkg != null && !pkg.isEmpty()) {
                includedPackages.add(pkg);
            }
            return this;
        }

        public Builder includePackages(Set<String> pkgs) {
            if (pkgs != null) {
                for (String p : pkgs) {
                    includePackage(p);
                }
            }
            return this;
        }

        public Builder includeModule(String module) {
            if (module != null && !module.isEmpty()) {
                includedModules.add(module);
            }
            return this;
        }

        public Builder includeModules(Set<String> mods) {
            if (mods != null) {
                for (String m : mods) {
                    includeModule(m);
                }
            }
            return this;
        }

        public Builder excludePackage(String pkg) {
            if (pkg != null && !pkg.isEmpty()) {
                excludedPackages.add(pkg);
            }
            return this;
        }

        public Builder excludePackages(Set<String> pkgs) {
            if (pkgs != null) {
                for (String p : pkgs) {
                    excludePackage(p);
                }
            }
            return this;
        }

        public Builder excludeExternalLibraries(boolean v) {
            this.excludeExternalLibraries = v;
            return this;
        }

        public Builder externalPackagePrefixes(Set<String> prefixes) {
            this.externalPackagePrefixes.clear();
            if (prefixes != null) {
                for (String p : prefixes) {
                    if (p != null && !p.isEmpty()) {
                        this.externalPackagePrefixes.add(p);
                    }
                }
            }
            return this;
        }

        public Builder classNameRegex(Pattern p) {
            this.classNameRegex = p;
            return this;
        }

        public Builder classNameRegex(String regex) {
            this.classNameRegex = (regex == null || regex.isEmpty())
                    ? null : Pattern.compile(regex);
            return this;
        }

        public Builder seed(String qualifiedName) {
            if (qualifiedName != null && !qualifiedName.isEmpty()) {
                seedQualifiedNames.add(qualifiedName);
            }
            return this;
        }

        public Builder seeds(Set<String> qns) {
            if (qns != null) {
                for (String q : qns) {
                    seed(q);
                }
            }
            return this;
        }

        public Builder neighborHops(int hops) {
            this.neighborHops = hops;
            return this;
        }

        public Builder maxClasses(int max) {
            this.maxClasses = max;
            return this;
        }

        public Builder relationKinds(EnumSet<RelationKind> kinds) {
            this.relationKinds = (kinds == null || kinds.isEmpty())
                    ? null : EnumSet.copyOf(kinds);
            return this;
        }

        public Builder visibilityFilter(VisibilityFilter v) {
            this.visibilityFilter = v;
            return this;
        }

        public Builder parseMode(UmlGenerator.ParseMode mode) {
            this.parseMode = mode;
            return this;
        }

        public Builder preset(DiagramPreset p) {
            this.preset = p;
            return this;
        }

        /** フォーカス強調モードの焦点クラス FQN を設定する（空/null で無効）。 */
        public Builder focusClass(String fqn) {
            this.focusClass = fqn == null ? "" : fqn;
            return this;
        }

        public DiagramScope build() {
            return new DiagramScope(this);
        }
    }
}
