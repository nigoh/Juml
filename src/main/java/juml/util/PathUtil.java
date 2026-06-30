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
}
