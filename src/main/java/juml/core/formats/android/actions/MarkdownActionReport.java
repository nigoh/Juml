// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.actions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link UiActionEntry} のリストを Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (操作種別ごとの件数)</li>
 *   <li>Click Handlers — setOnClickListener / Compose onClick / XML android:onClick</li>
 *   <li>Other Handlers — onLongClick / onCheckedChanged / メニュー</li>
 * </ol>
 */
public final class MarkdownActionReport {

    private MarkdownActionReport() {
    }

    public static String render(List<UiActionEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# UI 操作マップ — UI Action Map\n\n");
        sb.append("ユーザーがどの部品を操作すると、どの処理が呼ばれるかの一覧です。\n\n");

        if (entries == null || entries.isEmpty()) {
            sb.append("UI 操作のハンドラが見つかりませんでした "
                    + "(no UI action handlers detected)。\n");
            return sb.toString();
        }

        // 種別ごとにカウント
        Map<UiActionEntry.ActionType, Integer> counts = new LinkedHashMap<>();
        for (UiActionEntry.ActionType t : UiActionEntry.ActionType.values()) {
            counts.put(t, 0);
        }
        for (UiActionEntry e : entries) {
            counts.merge(e.actionType, 1, Integer::sum);
        }

        sb.append("- 操作の総数 / Total handlers: ").append(entries.size()).append('\n');
        for (Map.Entry<UiActionEntry.ActionType, Integer> c : counts.entrySet()) {
            if (c.getValue() > 0) {
                sb.append("  - ").append(c.getKey().label).append(": ")
                        .append(c.getValue()).append('\n');
            }
        }
        sb.append('\n');

        // Click ハンドラ
        List<UiActionEntry> clicks = new ArrayList<>();
        List<UiActionEntry> others = new ArrayList<>();
        for (UiActionEntry e : entries) {
            if (e.actionType == UiActionEntry.ActionType.ON_CLICK
                    || e.actionType == UiActionEntry.ActionType.XML_ON_CLICK
                    || e.actionType == UiActionEntry.ActionType.COMPOSE_CLICK) {
                clicks.add(e);
            } else {
                others.add(e);
            }
        }

        if (!clicks.isEmpty()) {
            sb.append("## クリック操作 (Click Handlers)\n\n");
            sb.append("| 部品 / ID | 操作の種類 | 処理メソッド | ファイル | 行 |\n");
            sb.append("|---|---|---|---|---|\n");
            for (UiActionEntry e : clicks) {
                sb.append("| ").append(e.componentId.isEmpty() ? "—" : e.componentId)
                        .append(" | ").append(e.actionType.label)
                        .append(" | ").append(e.handler.isEmpty() ? "—" : "`" + e.handler + "`")
                        .append(" | ").append(e.shortFileName())
                        .append(" | ").append(e.line < 0 ? "—" : String.valueOf(e.line))
                        .append(" |\n");
            }
            sb.append('\n');
        }

        if (!others.isEmpty()) {
            sb.append("## その他の操作 (Other Handlers)\n\n");
            sb.append("| 部品 / ID | 操作の種類 | 処理メソッド | ファイル | 行 |\n");
            sb.append("|---|---|---|---|---|\n");
            for (UiActionEntry e : others) {
                sb.append("| ").append(e.componentId.isEmpty() ? "—" : e.componentId)
                        .append(" | ").append(e.actionType.label)
                        .append(" | ").append(e.handler.isEmpty() ? "—" : "`" + e.handler + "`")
                        .append(" | ").append(e.shortFileName())
                        .append(" | ").append(e.line < 0 ? "—" : String.valueOf(e.line))
                        .append(" |\n");
            }
            sb.append('\n');
        }

        return sb.toString();
    }
}
