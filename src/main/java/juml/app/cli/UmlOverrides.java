// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.app.uml.DiagramPreset;
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.UmlGenerator;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CLI から指定された、UML 系出力で上書きするオプション値の束。
 *
 * <p>{@code -A}/{@code -c}/{@code -q} の経路から共通で使う。
 * クラス図向け (showComments / showFields / publicOnly 等) とシーケンス図向け (seqDepth) の
 * 双方を保持する。{@link DiagramPreset} を CLI {@code --preset} で適用してから、
 * 個別フラグ ({@code --no-fields} / {@code --public-only} 等) で上書きする 2 段方式。</p>
 */
public final class UmlOverrides {

    DiagramPreset preset = DiagramPreset.BALANCED;

    boolean showComments = true;
    boolean showAnnotations = true;
    boolean showEnumConstants = true;
    boolean showFinal = true;
    boolean showFields = true;
    boolean showMethods = true;
    boolean showInheritance = true;
    boolean showImplementations = true;
    boolean showUsageRelations = true;
    boolean publicOnly = false;
    boolean excludeExternal = false;
    boolean markExternalSupertypes = false;
    boolean colorCodeRelations = false;
    boolean hideEmptyMembers = false;
    boolean hideUnlinked = false;
    String focusClass = "";
    boolean visibilityIcons = true;
    boolean interactiveLinks = false;
    boolean jetpack = false;

    PlantUmlClassDiagram.CommentStyle commentStyle =
            PlantUmlClassDiagram.CommentStyle.INLINE;
    Integer commentMaxLengthOverride; // null = preset 既定をそのまま使う
    Set<String> hiddenAnnotationsOverride; // null = preset 既定をそのまま使う
    public Set<String> excludedPackages = new LinkedHashSet<>();
    /** クラス名 (単純名/完全修飾名) が一致したら除外する正規表現 (空で無効)。 */
    public String excludeNameRegex = "";
    public UmlGenerator.ParseMode parseMode = UmlGenerator.ParseMode.FULL;

    public Integer seqDepth;

    /** クラス図向け Options に値を反映する。 */
    public void applyTo(PlantUmlClassDiagram.Options o) {
        o.showComments = showComments;
        o.showAnnotations = showAnnotations;
        o.showEnumConstants = showEnumConstants;
        o.showFinal = showFinal;
        o.showFields = showFields;
        o.showMethods = showMethods;
        o.showInheritance = showInheritance;
        o.showImplementations = showImplementations;
        o.showUsageRelations = showUsageRelations;
        o.publicOnly = publicOnly;
        o.excludeExternalLibraries = excludeExternal;
        o.markExternalSupertypes = markExternalSupertypes;
        o.colorCodeRelations = colorCodeRelations;
        o.hideEmptyMembers = hideEmptyMembers;
        o.hideUnlinkedClasses = hideUnlinked;
        o.focusClass = focusClass == null ? "" : focusClass;
        o.visibilityIcons = visibilityIcons;
        o.commentStyle = commentStyle;
        if (commentMaxLengthOverride != null) {
            o.commentMaxLength = commentMaxLengthOverride;
        }
        if (hiddenAnnotationsOverride != null) {
            o.hiddenAnnotations = new HashSet<>(hiddenAnnotationsOverride);
        }
        o.interactiveLinks = interactiveLinks;
        if (o.jetpack != null) {
            o.jetpack.enabled = jetpack;
        }
    }

    /** シーケンス図向けにコメント関連オプションを適用する。 */
    public void applyTo(juml.core.formats.uml.PlantUmlSequenceDiagram.Options o) {
        o.showComments = showComments;
        o.commentStyle = commentStyle;
    }

    /**
     * パース済み CLI オプションから {@link UmlOverrides} を組み立てる。
     * 不正値の場合は {@code System.exit(1)} で終了して null を返す。
     */
    public static UmlOverrides build(CliOptions options) {
        UmlOverrides o = new UmlOverrides();
        if (!applyPreset(o, options)) {
            return null;
        }
        applyPresetDefaults(o);
        applyFlagOverrides(o, options);
        if (!applyCommentStyle(o, options)
                || !applySeqDepth(o, options)
                || !applyCommentMaxLength(o, options)) {
            return null;
        }
        applyHiddenAnnotations(o, options);
        applyExcludePackages(o, options);
        if (!applyRelation(o, options) || !applyMode(o, options)) {
            return null;
        }
        return o;
    }

    /** {@code --preset} を解釈する。不正値なら false。 */
    private static boolean applyPreset(UmlOverrides o, CliOptions options) {
        if (!options.preset.getArguments().isEmpty()) {
            String raw = options.preset.getArguments().getLast();
            DiagramPreset p = DiagramPreset.fromCli(raw);
            if (p == DiagramPreset.CUSTOM && !"custom".equalsIgnoreCase(raw)) {
                System.err.println("Invalid --preset value: " + raw
                        + " (expected: minimal | balanced | detailed)");
                System.exit(1);
                return false;
            }
            o.preset = p;
        }
        return true;
    }

    /** preset の既定値を各フィールドへ反映する。 */
    private static void applyPresetDefaults(UmlOverrides o) {
        PlantUmlClassDiagram.Options tmp = new PlantUmlClassDiagram.Options();
        o.preset.applyTo(tmp);
        o.showComments = tmp.showComments;
        o.showAnnotations = tmp.showAnnotations;
        o.showEnumConstants = tmp.showEnumConstants;
        o.showFinal = tmp.showFinal;
        o.showFields = tmp.showFields;
        o.showMethods = tmp.showMethods;
        o.showInheritance = tmp.showInheritance;
        o.showImplementations = tmp.showImplementations;
        o.showUsageRelations = tmp.showUsageRelations;
        o.publicOnly = tmp.publicOnly;
        o.excludeExternal = tmp.excludeExternalLibraries;
        o.commentStyle = tmp.commentStyle;
        // interactiveLinks は preset から引き継がない。GUI プレビュー用の juml:// リンクは
        // CLI 出力/保存図に出さない契約のため、CLI では --interactive-svg のみで有効化する
        // (applyFlagOverrides 参照)。preset 値を写すと balanced 等で既定 ON になってしまう。
        o.jetpack = tmp.jetpack != null && tmp.jetpack.enabled;
    }

    /** 個別の boolean フラグ ({@code --no-*} / {@code --public-only} 等) で上書きする。 */
    private static void applyFlagOverrides(UmlOverrides o, CliOptions options) {
        if (options.noComments.isSet()) {
            o.showComments = false;
        }
        if (options.noAnnotations.isSet()) {
            o.showAnnotations = false;
        }
        if (options.noEnumConstants.isSet()) {
            o.showEnumConstants = false;
        }
        if (options.noFinal.isSet()) {
            o.showFinal = false;
        }
        if (options.noFields.isSet()) {
            o.showFields = false;
        }
        if (options.noMethods.isSet()) {
            o.showMethods = false;
        }
        if (options.publicOnly.isSet()) {
            o.publicOnly = true;
        }
        if (options.excludeExternal.isSet()) {
            o.excludeExternal = true;
        }
        if (options.markExternalSupertypes.isSet()) {
            o.markExternalSupertypes = true;
        }
        if (options.colorRelations.isSet()) {
            o.colorCodeRelations = true;
        }
        if (options.hideEmptyMembers.isSet()) {
            o.hideEmptyMembers = true;
        }
        if (options.hideUnlinked.isSet()) {
            o.hideUnlinked = true;
        }
        if (!options.focus.getArguments().isEmpty()) {
            o.focusClass = options.focus.getArguments().getLast();
        }
        if (options.noVisibilityIcons.isSet()) {
            o.visibilityIcons = false;
        }
        if (options.interactiveSvg.isSet()) {
            o.interactiveLinks = true;
        }
        o.jetpack = o.jetpack || options.jetpack.isSet();
    }

    /** {@code --comment-style}。不正値なら false。 */
    private static boolean applyCommentStyle(UmlOverrides o, CliOptions options) {
        if (options.commentStyle.getArguments().isEmpty()) {
            return true;
        }
        String style = options.commentStyle.getArguments().getLast().toLowerCase(Locale.ROOT);
        if ("note".equals(style)) {
            o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        } else if ("inline".equals(style)) {
            o.commentStyle = PlantUmlClassDiagram.CommentStyle.INLINE;
        } else {
            System.err.println("Invalid --comment-style: " + style
                    + " (expected: inline | note)");
            System.exit(1);
            return false;
        }
        return true;
    }

    /** {@code --seq-depth}。不正値なら false。 */
    private static boolean applySeqDepth(UmlOverrides o, CliOptions options) {
        if (options.seqDepth.getArguments().isEmpty()) {
            return true;
        }
        String raw = options.seqDepth.getArguments().getLast();
        try {
            o.seqDepth = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            System.err.println("Invalid --seq-depth value: " + raw);
            System.exit(1);
            return false;
        }
        return true;
    }

    /** {@code --comment-max-length}。不正値なら false。 */
    private static boolean applyCommentMaxLength(UmlOverrides o, CliOptions options) {
        if (options.commentMaxLength.getArguments().isEmpty()) {
            return true;
        }
        String raw = options.commentMaxLength.getArguments().getLast();
        try {
            o.commentMaxLengthOverride = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            System.err.println("Invalid --comment-max-length value: " + raw);
            System.exit(1);
            return false;
        }
        return true;
    }

    /** {@code --hidden-annotations} (CSV)。 */
    private static void applyHiddenAnnotations(UmlOverrides o, CliOptions options) {
        if (options.hiddenAnnotations.getArguments().isEmpty()) {
            return;
        }
        String csv = options.hiddenAnnotations.getArguments().getLast();
        Set<String> set = new LinkedHashSet<>();
        for (String tok : csv.split(",")) {
            String t = tok.trim();
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        o.hiddenAnnotationsOverride = set;
    }

    /** {@code --exclude-package} (複数指定可) と {@code --exclude-name-regex}。 */
    private static void applyExcludePackages(UmlOverrides o, CliOptions options) {
        List<String> argList = options.excludePackage.getArguments();
        for (String pkg : argList) {
            if (pkg != null && !pkg.isEmpty()) {
                o.excludedPackages.add(pkg);
            }
        }
        if (!options.excludeNameRegex.getArguments().isEmpty()) {
            o.excludeNameRegex = options.excludeNameRegex.getArguments().getLast();
        }
    }

    /** {@code --relation} (CSV)。不正トークンなら false。 */
    private static boolean applyRelation(UmlOverrides o, CliOptions options) {
        if (options.relation.getArguments().isEmpty()) {
            return true;
        }
        String csv = options.relation.getArguments().getLast();
        // 個別フラグで指定された場合は preset の relation 既定を上書きする
        boolean inherit = false;
        boolean impl = false;
        boolean use = false;
        for (String tok : csv.split(",")) {
            String t = tok.trim().toLowerCase(Locale.ROOT);
            switch (t) {
                case "inherit":
                case "inheritance":
                case "extends":
                    inherit = true;
                    break;
                case "impl":
                case "implementation":
                case "implements":
                    impl = true;
                    break;
                case "use":
                case "usage":
                case "uses":
                    use = true;
                    break;
                case "":
                    break;
                default:
                    System.err.println("Invalid --relation token: " + tok
                            + " (expected: inherit | impl | use)");
                    System.exit(1);
                    return false;
            }
        }
        o.showInheritance = inherit;
        o.showImplementations = impl;
        o.showUsageRelations = use;
        return true;
    }

    /** {@code --mode}。不正値なら false。 */
    private static boolean applyMode(UmlOverrides o, CliOptions options) {
        if (options.mode.getArguments().isEmpty()) {
            return true;
        }
        String m = options.mode.getArguments().getLast().toLowerCase(Locale.ROOT);
        if ("headers-only".equals(m) || "headers".equals(m)) {
            o.parseMode = UmlGenerator.ParseMode.HEADERS_ONLY;
        } else if ("full".equals(m)) {
            o.parseMode = UmlGenerator.ParseMode.FULL;
        } else {
            System.err.println("Invalid --mode value: " + m
                    + " (expected: headers-only | full)");
            System.exit(1);
            return false;
        }
        return true;
    }
}
