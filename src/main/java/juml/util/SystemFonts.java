// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 実行環境にインストールされているフォントファミリ名を列挙するユーティリティ。
 *
 * <p>Style 設定ダイアログのフォント選択コンボボックスへ供給するために用いる。
 * 日本語（{@code skinparam defaultFontName} 経由で図に描画される文字）が
 * 文字化けしないよう、日本語表示可能なフォントを判定するヘルパも提供する。</p>
 *
 * <p>ヘッドレス環境などでフォント環境を取得できない場合は空配列を返し、例外は投げない。</p>
 */
public final class SystemFonts {

    private SystemFonts() {
    }

    /** 日本語表示可否の判定に使うサンプル文字（ひらがな・漢字・カタカナ）。 */
    private static final char[] JAPANESE_SAMPLE = {'あ', '日', 'ア'};

    private static volatile String[] cachedFamilies;

    /**
     * インストール済みフォントファミリ名を昇順（ロケール非依存）で返す。
     * 取得できない場合は空配列。結果はキャッシュされる。
     */
    public static String[] families() {
        String[] c = cachedFamilies;
        if (c != null) {
            return c.clone();
        }
        String[] resolved = resolve();
        cachedFamilies = resolved;
        return resolved.clone();
    }

    private static String[] resolve() {
        String[] families;
        try {
            families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
        } catch (Throwable t) {
            // ヘッドレス例外などフォント環境を取得できない場合は空配列。
            return new String[0];
        }
        if (families == null || families.length == 0) {
            return new String[0];
        }
        String[] copy = families.clone();
        Arrays.sort(copy, String.CASE_INSENSITIVE_ORDER);
        return copy;
    }

    /**
     * 指定フォントファミリが日本語（ひらがな・漢字・カタカナ）を表示できるか判定する。
     * 取得に失敗した場合は {@code false}。
     */
    public static boolean canDisplayJapanese(String family) {
        if (family == null || family.isEmpty()) {
            return false;
        }
        try {
            Font f = new Font(family, Font.PLAIN, 12);
            for (char c : JAPANESE_SAMPLE) {
                if (!f.canDisplay(c)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 日本語表示可能なフォントを先頭にまとめた一覧を返す。
     * （日本語フォント群 → その他フォント群、各群内は {@link #families()} の順）。
     * UI のフォント選択で日本語フォントを見つけやすくするために用いる。
     */
    public static List<String> familiesJapaneseFirst() {
        String[] all = families();
        List<String> japanese = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String fam : all) {
            if (canDisplayJapanese(fam)) {
                japanese.add(fam);
            } else {
                others.add(fam);
            }
        }
        List<String> result = new ArrayList<>(all.length);
        result.addAll(japanese);
        result.addAll(others);
        return result;
    }
}
