// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.Setting;
import juml.core.formats.uml.DiagramStyle;
import juml.core.formats.uml.PlantUmlRenderer;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * プロジェクト単位の設定（スタイル・シーケンス・クラス図オプション）の保存と復元。
 *
 * <p>保存・復元はベストエフォートで行い、失敗しても例外を伝播させない。</p>
 */
public final class ProjectSettingsPersistor {

    private final Supplier<Setting> settingSupplier;
    private final Runnable onStyleRestored;

    /**
     * @param settingSupplier  現在の {@link Setting} を返すサプライヤ（通常 {@code Main::getSetting}）
     * @param onStyleRestored  スタイル復元後に呼ぶコールバック（テーマメニューの再同期など）
     */
    public ProjectSettingsPersistor(Supplier<Setting> settingSupplier,
                                    Runnable onStyleRestored) {
        this.settingSupplier = settingSupplier;
        this.onStyleRestored = onStyleRestored;
    }

    /**
     * プロジェクトを「最近使用」に登録し、保存済み設定があれば復元する。
     * 未知プロジェクトや復元失敗は無視する（ベストエフォート）。
     */
    public void restoreAndPersist(File root) {
        if (root == null) {
            return;
        }
        try {
            juml.ProjectRepository repo = juml.ProjectRepository.getInstance();
            // アーカイブ (.jar/.aar/.class) は「最近使用したプロジェクト」に積まない。
            // 一覧側 (Welcome / Open Recent) はディレクトリ前提のため、積むと常に
            // "missing" 表示の死にエントリになり、実プロジェクトを枠から押し出す。
            if (root.isDirectory()) {
                repo.touch(root);
            }
            Map<String, String> saved = repo.loadSettings(root);
            if (saved.isEmpty()) {
                return;
            }
            Setting s = settingSupplier.get();
            if (s == null) {
                return;
            }
            DiagramStyle style = s.getStyle();
            if (saved.containsKey("style.theme")) style.setTheme(saved.get("style.theme"));
            if (saved.containsKey("style.backgroundColor"))
                style.setBackgroundColor(saved.get("style.backgroundColor"));
            if (saved.containsKey("style.fontName")) style.setFontName(saved.get("style.fontName"));
            if (saved.containsKey("style.fontSize"))
                style.setFontSize(parseIntOrZero(saved.get("style.fontSize")));
            if (saved.containsKey("style.direction")) {
                try {
                    style.setDirection(DiagramStyle.Direction.valueOf(saved.get("style.direction")));
                } catch (IllegalArgumentException ignored) {}
            }
            if (saved.containsKey("style.lineType")) {
                try {
                    style.setLineType(DiagramStyle.LineType.valueOf(saved.get("style.lineType")));
                } catch (IllegalArgumentException ignored) {}
            }
            if (saved.containsKey("style.shadowing")) {
                try {
                    style.setShadowing(DiagramStyle.Shadowing.valueOf(saved.get("style.shadowing")));
                } catch (IllegalArgumentException ignored) {}
            }
            if (saved.containsKey("style.nodeSep"))
                style.setNodeSep(parseIntOrZero(saved.get("style.nodeSep")));
            if (saved.containsKey("style.rankSep"))
                style.setRankSep(parseIntOrZero(saved.get("style.rankSep")));
            if (saved.containsKey("style.customSkinparam"))
                style.setCustomSkinparam(saved.get("style.customSkinparam"));
            s.setStyle(style);
            PlantUmlRenderer.setStyle(style);

            if (saved.containsKey("sequence.showComments"))
                s.setSequenceShowComments(Boolean.parseBoolean(saved.get("sequence.showComments")));
            if (saved.containsKey("sequence.commentStyle"))
                s.setSequenceCommentStyle(saved.get("sequence.commentStyle"));
            if (saved.containsKey("sequence.commentPlacement"))
                s.setSequenceCommentPlacement(saved.get("sequence.commentPlacement"));
            if (saved.containsKey("sequence.qualifyMethodNames"))
                s.setSequenceQualifyMethodNames(
                        Boolean.parseBoolean(saved.get("sequence.qualifyMethodNames")));
            if (saved.containsKey("sequence.maxDepth"))
                s.setSequenceMaxDepth(parseIntOrZero(saved.get("sequence.maxDepth")));
            if (saved.containsKey("sequence.showCallArguments"))
                s.setSequenceShowCallArguments(
                        Boolean.parseBoolean(saved.get("sequence.showCallArguments")));

            if (saved.containsKey("activity.expandInlineCallbacks"))
                s.setActivityExpandInlineCallbacks(
                        Boolean.parseBoolean(saved.get("activity.expandInlineCallbacks")));
            if (saved.containsKey("activity.showLocalVars"))
                s.setActivityShowLocalVars(
                        Boolean.parseBoolean(saved.get("activity.showLocalVars")));
            if (saved.containsKey("activity.showAssignments"))
                s.setActivityShowAssignments(
                        Boolean.parseBoolean(saved.get("activity.showAssignments")));
            if (saved.containsKey("activity.showCallArguments"))
                s.setActivityShowCallArguments(
                        Boolean.parseBoolean(saved.get("activity.showCallArguments")));
            if (saved.containsKey("activity.showInlineComments"))
                s.setActivityShowInlineComments(
                        Boolean.parseBoolean(saved.get("activity.showInlineComments")));

            if (saved.containsKey("classDiagram.lastPreset"))
                s.setClassDiagramLastPreset(saved.get("classDiagram.lastPreset"));
            if (saved.containsKey("classDiagram.showFields"))
                s.setClassDiagramShowFields(Boolean.parseBoolean(saved.get("classDiagram.showFields")));
            if (saved.containsKey("classDiagram.showMethods"))
                s.setClassDiagramShowMethods(
                        Boolean.parseBoolean(saved.get("classDiagram.showMethods")));
            if (saved.containsKey("classDiagram.showAnnotations"))
                s.setClassDiagramShowAnnotations(
                        Boolean.parseBoolean(saved.get("classDiagram.showAnnotations")));
            if (saved.containsKey("classDiagram.publicOnly"))
                s.setClassDiagramPublicOnly(
                        Boolean.parseBoolean(saved.get("classDiagram.publicOnly")));
            if (saved.containsKey("classDiagram.excludeExternal"))
                s.setClassDiagramExcludeExternal(
                        Boolean.parseBoolean(saved.get("classDiagram.excludeExternal")));
            if (saved.containsKey("classDiagram.markExternalSupertypes"))
                s.setClassDiagramMarkExternalSupertypes(
                        Boolean.parseBoolean(saved.get("classDiagram.markExternalSupertypes")));
            if (saved.containsKey("classDiagram.commentMaxLength"))
                s.setClassDiagramCommentMaxLength(
                        parseIntOrZero(saved.get("classDiagram.commentMaxLength")));
            if (saved.containsKey("classDiagram.hiddenAnnotations"))
                s.setClassDiagramHiddenAnnotations(saved.get("classDiagram.hiddenAnnotations"));

            onStyleRestored.run();
        } catch (RuntimeException ignored) {
            // 設定復元はベストエフォート
        }
    }

    /** 現在のスタイル・シーケンス・クラス図設定をプロジェクト固有設定として保存する。 */
    public void saveCurrentProjectSettings(File currentProjectRoot) {
        if (currentProjectRoot == null) {
            return;
        }
        try {
            Setting s = settingSupplier.get();
            if (s == null) {
                return;
            }
            DiagramStyle style = PlantUmlRenderer.getStyle();
            Map<String, String> m = new LinkedHashMap<>();
            m.put("style.theme", style.getTheme());
            m.put("style.backgroundColor", style.getBackgroundColor());
            m.put("style.fontName", style.getFontName());
            m.put("style.fontSize", Integer.toString(style.getFontSize()));
            m.put("style.direction", style.getDirection().name());
            m.put("style.lineType", style.getLineType().name());
            m.put("style.shadowing", style.getShadowing().name());
            m.put("style.nodeSep", Integer.toString(style.getNodeSep()));
            m.put("style.rankSep", Integer.toString(style.getRankSep()));
            m.put("style.customSkinparam", style.getCustomSkinparam());
            m.put("sequence.showComments", Boolean.toString(s.isSequenceShowComments()));
            m.put("sequence.commentStyle", s.getSequenceCommentStyle());
            m.put("sequence.commentPlacement", s.getSequenceCommentPlacement());
            m.put("sequence.qualifyMethodNames",
                    Boolean.toString(s.isSequenceQualifyMethodNames()));
            m.put("sequence.maxDepth", Integer.toString(s.getSequenceMaxDepth()));
            m.put("sequence.showCallArguments",
                    Boolean.toString(s.isSequenceShowCallArguments()));
            m.put("activity.expandInlineCallbacks",
                    Boolean.toString(s.isActivityExpandInlineCallbacks()));
            m.put("activity.showLocalVars",
                    Boolean.toString(s.isActivityShowLocalVars()));
            m.put("activity.showAssignments",
                    Boolean.toString(s.isActivityShowAssignments()));
            m.put("activity.showCallArguments",
                    Boolean.toString(s.isActivityShowCallArguments()));
            m.put("activity.showInlineComments",
                    Boolean.toString(s.isActivityShowInlineComments()));
            m.put("classDiagram.lastPreset", s.getClassDiagramLastPreset());
            m.put("classDiagram.showFields", Boolean.toString(s.isClassDiagramShowFields()));
            m.put("classDiagram.showMethods", Boolean.toString(s.isClassDiagramShowMethods()));
            m.put("classDiagram.showAnnotations",
                    Boolean.toString(s.isClassDiagramShowAnnotations()));
            m.put("classDiagram.publicOnly", Boolean.toString(s.isClassDiagramPublicOnly()));
            m.put("classDiagram.excludeExternal",
                    Boolean.toString(s.isClassDiagramExcludeExternal()));
            m.put("classDiagram.markExternalSupertypes",
                    Boolean.toString(s.isClassDiagramMarkExternalSupertypes()));
            m.put("classDiagram.commentMaxLength",
                    Integer.toString(s.getClassDiagramCommentMaxLength()));
            m.put("classDiagram.hiddenAnnotations", s.getClassDiagramHiddenAnnotations());
            juml.ProjectRepository.getInstance().saveSettings(currentProjectRoot, m);
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート
        }
    }

    private static int parseIntOrZero(String v) {
        if (v == null) return 0;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 0; }
    }
}
