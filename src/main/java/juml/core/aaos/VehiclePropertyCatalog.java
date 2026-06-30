// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import juml.core.formats.java.AndroidProjectScanner;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code android.car.VehiclePropertyIds} 風の定数定義から
 * 「Property 名 → 整数 ID」のマッピングを構築するカタログ。
 *
 * <p>プロジェクトに {@code VehiclePropertyIds.java} が含まれる場合は
 * そこから定数を抽出する。AAOS 本体外のアプリ層プロジェクトでは見つからないので、
 * 解析時には欠落しても許容する。</p>
 *
 * <p>抽出パターン: {@code public static final int FOO = 0x12345678;} と
 * {@code public static final int FOO = 1234;} の 2 形式。</p>
 */
public final class VehiclePropertyCatalog {

    private static final Pattern CONST_PATTERN = Pattern.compile(
            "public\\s+static\\s+final\\s+int\\s+"
                    + "([A-Z][A-Z0-9_]+)\\s*=\\s*(0x[0-9A-Fa-f_]+|-?\\d[\\d_]*)\\s*;");

    /** Property 名 → ID (10進)。 */
    private final Map<String, Long> nameToId = new LinkedHashMap<>();

    /**
     * プロジェクトを走査し {@code VehiclePropertyIds*.java} があれば定数を取り込む。
     * 取り込めなかった場合でも空のカタログを返す (例外は投げない)。
     */
    public static VehiclePropertyCatalog scanProject(File projectRoot) {
        VehiclePropertyCatalog cat = new VehiclePropertyCatalog();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return cat;
        }
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        opts.includeAidl = false;
        List<File> files = AndroidProjectScanner.scan(projectRoot, opts);
        for (File f : files) {
            String name = f.getName();
            if (!name.equals("VehiclePropertyIds.java")
                    && !name.endsWith("VehiclePropertyIds.java")) {
                continue;
            }
            try {
                String src = AndroidProjectScanner.readFile(f);
                cat.loadFromSource(src);
            } catch (IOException ex) {
                // skip unreadable
            }
        }
        return cat;
    }

    /** 既知のプロパティ定数を 1 件追加する (テストや手動補完用)。 */
    public void put(String name, long id) {
        if (name != null && !name.isEmpty()) {
            nameToId.put(name, id);
        }
    }

    /** Java ソース文字列から定数を取り込む。 */
    public void loadFromSource(String src) {
        if (src == null || src.isEmpty()) {
            return;
        }
        Matcher m = CONST_PATTERN.matcher(src);
        while (m.find()) {
            String name = m.group(1);
            String value = m.group(2).replace("_", "");
            long id;
            try {
                if (value.startsWith("0x") || value.startsWith("0X")) {
                    id = Long.parseLong(value.substring(2), 16);
                } else {
                    id = Long.parseLong(value);
                }
            } catch (NumberFormatException ex) {
                continue;
            }
            nameToId.put(name, id);
        }
    }

    /** Property 名から ID を引く。未知なら空。 */
    public Optional<Long> idOf(String propertyName) {
        if (propertyName == null) {
            return Optional.empty();
        }
        // "VehiclePropertyIds.HVAC_FAN_SPEED" のような形式も末尾を見て検索
        String name = propertyName;
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        Long v = nameToId.get(name);
        return v == null ? Optional.empty() : Optional.of(v);
    }

    /** 既知 Property の総数。 */
    public int size() {
        return nameToId.size();
    }

    /** 読み取り専用ビュー。 */
    public Map<String, Long> asMap() {
        return Collections.unmodifiableMap(nameToId);
    }
}
