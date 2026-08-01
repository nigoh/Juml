// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.io.File;

/**
 * ファイルパスに関するユーティリティクラス
 */
public class PathUtil {

    /**
     * 拡張子を変換したファイル名を取得する。
     * @param base ファイル名
     * @param ext 変換後の拡張子
     * @return 拡張子が変換されたファイル名
     */
    public static String extConvert(String base, String ext){
        if (ext != null) {
            ext = ext.replaceAll("[^a-zA-Z0-9]", "");
        }
        String ret;
        File f = new File(base);
        String fn = f.getName();
        int i = fn.lastIndexOf(".");
        if(i>0){
            ret = f.getPath().toString().substring(0, f.getPath().toString().length() - (fn.length() - i));
        }
        else{
            ret = base;
        }
        ret += "." + ext;

        return ret;
    }

    /**
     * プログラムが動作しているディレクトリを取得する
     * @return プログラムが動作しているディレクトリ
     */
    public static String getBasePath() {
        return new File(System.getProperty("user.dir")).getAbsolutePath();
    }

    /** ユーザー単位のデータ格納ディレクトリを差し替えるためのシステムプロパティ (テスト用)。 */
    public static final String USER_DATA_DIR_PROPERTY = "juml.userDataDir";

    /**
     * 設定・ログなど「起動場所に依存してはいけないもの」を置くユーザー単位の
     * ディレクトリ ({@code ~/.juml}、Windows は {@code %LOCALAPPDATA%/Juml})。
     *
     * <p>{@link #getBasePath()} (= {@code user.dir}) はユーザーが任意のフォルダから
     * {@code java -jar Juml.jar <path>} で起動するため一定しない。そこへ設定やログを置くと
     * 起動場所ごとに別ファイルになり、設定が毎回初期値へ戻ったうえ、行った先々に
     * {@code settings.xml} と {@code logs/} が散らばる。インストール先が書込不可なら
     * そもそも保存できない。解析キャッシュ・下書き・プロジェクト履歴は既にこの場所へ
     * 移してあるので、設定とログも揃える。</p>
     *
     * <p>テストは {@value #USER_DATA_DIR_PROPERTY} で差し替えられる。</p>
     */
    public static File getUserDataDir() {
        String override = System.getProperty(USER_DATA_DIR_PROPERTY);
        if (override != null && !override.isEmpty()) {
            return new File(override);
        }
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isEmpty()) {
                return new File(local, "Juml");
            }
        }
        return new File(System.getProperty("user.home", "."), ".juml");
    }
}
