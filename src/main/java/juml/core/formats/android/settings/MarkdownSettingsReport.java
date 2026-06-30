// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SettingsAnalysisResult} を Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (検出件数)</li>
 *   <li>SharedPreferences Keys — キー別の読み取り/書き込み一覧</li>
 *   <li>Preference XML Definitions — res/xml/ で定義されたキー一覧</li>
 * </ol>
 */
public final class MarkdownSettingsReport {

    private MarkdownSettingsReport() {
    }

    public static String render(SettingsAnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 設定の保存・定義レポート — Settings Report\n\n");
        sb.append("アプリが端末に保存する設定値 (SharedPreferences) と、"
                + "設定画面の項目定義 (Preference XML) の一覧です。\n\n");

        if (result.isEmpty()) {
            sb.append("設定の保存・定義が見つかりませんでした "
                    + "(no SharedPreferences / Preference XML detected)。\n");
            return sb.toString();
        }

        List<SharedPreferencesEntry> code = result.getCodeEntries();
        List<PreferenceXmlEntry> xml = result.getXmlEntries();

        sb.append("- 設定値の読み書き箇所 / SharedPreferences accesses: ")
                .append(code.size()).append('\n');
        sb.append("- 設定画面の項目数 / Preference XML keys: ").append(xml.size()).append('\n');
        sb.append('\n');

        if (!code.isEmpty()) {
            sb.append("## 保存される設定値 (SharedPreferences Keys)\n\n");

            // キー別にグループ化
            Map<String, List<SharedPreferencesEntry>> byKey = new LinkedHashMap<>();
            for (SharedPreferencesEntry e : code) {
                byKey.computeIfAbsent(e.key, k -> new ArrayList<>()).add(e);
            }

            sb.append("| キー | 型 | 初期値 | 読み込み箇所 | 書き込み箇所 |\n");
            sb.append("|---|---|---|---|---|\n");

            for (Map.Entry<String, List<SharedPreferencesEntry>> entry : byKey.entrySet()) {
                String key = entry.getKey();
                List<SharedPreferencesEntry> entries = entry.getValue();

                String type = "";
                String defVal = "";
                List<String> reads = new ArrayList<>();
                List<String> writes = new ArrayList<>();

                for (SharedPreferencesEntry e : entries) {
                    if (!e.type.isEmpty()) {
                        type = e.type;
                    }
                    if (!e.defaultValue.isEmpty() && !e.isWrite) {
                        defVal = e.defaultValue;
                    }
                    String loc = e.shortFileName() + ":" + e.line;
                    if (e.isWrite) {
                        writes.add(loc);
                    } else {
                        reads.add(loc);
                    }
                }

                sb.append("| `").append(key).append("` | ")
                        .append(type.isEmpty() ? "—" : type).append(" | ")
                        .append(defVal.isEmpty() ? "—" : "`" + defVal + "`").append(" | ")
                        .append(reads.isEmpty() ? "—" : String.join(", ", reads)).append(" | ")
                        .append(writes.isEmpty() ? "—" : String.join(", ", writes)).append(" |\n");
            }
            sb.append('\n');

            // ストア名の一覧
            List<String> storeNames = new ArrayList<>();
            for (SharedPreferencesEntry e : code) {
                if (!e.storeName.isEmpty() && !storeNames.contains(e.storeName)) {
                    storeNames.add(e.storeName);
                }
            }
            if (!storeNames.isEmpty()) {
                sb.append("### 保存先ファイル (SharedPreferences Stores)\n\n");
                sb.append("| 保存先名 | キー |\n");
                sb.append("|---|---|\n");
                for (String store : storeNames) {
                    List<String> keys = new ArrayList<>();
                    for (SharedPreferencesEntry e : code) {
                        if (store.equals(e.storeName) && !keys.contains(e.key)) {
                            keys.add(e.key);
                        }
                    }
                    Collections.sort(keys);
                    sb.append("| `").append(store).append("` | ")
                            .append(String.join(", ", keys)).append(" |\n");
                }
                sb.append('\n');
            }
        }

        if (!xml.isEmpty()) {
            sb.append("## 設定画面の項目定義 (Preference XML Definitions)\n\n");
            sb.append("| キー | 種類 | 初期値 | 表示名 | ファイル |\n");
            sb.append("|---|---|---|---|---|\n");
            for (PreferenceXmlEntry e : xml) {
                sb.append("| `").append(e.key).append("` | ")
                        .append(e.elementType.isEmpty() ? "—" : e.elementType).append(" | ")
                        .append(e.defaultValue.isEmpty() ? "—" : "`" + e.defaultValue + "`").append(" | ")
                        .append(e.title.isEmpty() ? "—" : e.title).append(" | ")
                        .append(e.shortFileName()).append(" |\n");
            }
            sb.append('\n');
        }

        return sb.toString();
    }
}
