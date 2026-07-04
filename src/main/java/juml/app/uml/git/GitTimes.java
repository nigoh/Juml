// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import java.util.Date;

/** git 表示向けの相対日時整形ユーティリティ。 */
final class GitTimes {

    private static final long[] STEPS = {60, 3600, 86400, 604800, 2629746, 31556952};
    private static final String[] UNITS = {"minute", "hour", "day", "week", "month", "year"};

    private GitTimes() {
    }

    /** 「3 days ago」風の相対日時。1 分未満・未来は "just now"。 */
    static String relative(Date when) {
        if (when == null) {
            return "";
        }
        long secs = (System.currentTimeMillis() - when.getTime()) / 1000L;
        if (secs < 60) {
            return "just now";
        }
        int i = 0;
        for (; i < STEPS.length - 1; i++) {
            if (secs < STEPS[i + 1]) {
                break;
            }
        }
        long n = secs / STEPS[i];
        return n + " " + UNITS[i] + (n == 1 ? "" : "s") + " ago";
    }
}
