// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.settings;

/**
 * SharedPreferences の単一の読み取り／書き込み操作を表す。
 */
public final class SharedPreferencesEntry {

    /** キー名 (文字列リテラル)。 */
    public final String key;
    /** 型 (String / Boolean / Int / Long / Float / StringSet)。 */
    public final String type;
    /** デフォルト値 (文字列リテラルのみ。不明なら "")。 */
    public final String defaultValue;
    /** SharedPreferences ストア名。getDefaultSharedPreferences 経由なら "(default)"。 */
    public final String storeName;
    /** true なら書き込み (put*)、false なら読み取り (get*)。 */
    public final boolean isWrite;
    /** 検出されたファイルパス。 */
    public final String file;
    /** 行番号 (-1 なら不明)。 */
    public final int line;

    public SharedPreferencesEntry(String key, String type, String defaultValue,
                                   String storeName, boolean isWrite,
                                   String file, int line) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.storeName = storeName != null ? storeName : "";
        this.isWrite = isWrite;
        this.file = file != null ? file : "";
        this.line = line;
    }

    /** ファイル名のみ (パスを除いた末尾コンポーネント) を返す。 */
    public String shortFileName() {
        if (file.isEmpty()) {
            return "";
        }
        int sep = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        return sep >= 0 ? file.substring(sep + 1) : file;
    }
}
