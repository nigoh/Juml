// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

/**
 * 値を必要とするオプションに値が与えられなかった場合に発生する例外。
 * エラー ID は {@link ErrorCode#SYS_005}。JumlException 継承 (unchecked) だが、
 * 呼び出し側が扱いやすいよう throws 宣言は従来どおり残してよい。
 */
public class MissingArgumentException extends JumlException {

    private static final long serialVersionUID = 1L;

    private final String option;

    public MissingArgumentException(String option) {
        super(ErrorCode.SYS_005, String.format("Missing value for option: %s", option));
        this.option = option;
    }

    public String getOption() {
        return option;
    }
}
