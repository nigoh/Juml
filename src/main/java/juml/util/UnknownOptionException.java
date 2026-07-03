// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

/**
 * 不明なオプションが指定された場合に発生する例外。
 * エラー ID は {@link ErrorCode#SYS_004}。JumlException 継承 (unchecked) だが、
 * 呼び出し側が扱いやすいよう throws 宣言は従来どおり残してよい。
 */
public class UnknownOptionException extends JumlException {

    private static final long serialVersionUID = 1L;

    private final String option;

    public UnknownOptionException(String option){
        super(ErrorCode.SYS_004, String.format("Unknown option: %s", option));
        this.option = option;
    }

    public String getOption(){
        return option;
    }
}
