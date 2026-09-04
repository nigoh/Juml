// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.util.Messages;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * bug-hunt R3 で発見: 相対日時が英語固定で、日本語 UI でも "3 days ago" と出ていた。
 * メッセージリソース経由になっていることを検証する。
 */
public class GitTimesTest {

    @Test
    public void justNow_comesFromMessages() {
        assertEquals(Messages.get("git.time.justNow"), GitTimes.relative(new Date()));
    }

    @Test
    public void olderTimestamps_useTheLocalisedUnitKeys() {
        long now = System.currentTimeMillis();
        assertEquals(MessageFormat.format(Messages.get("git.time.day"), 3L),
                GitTimes.relative(new Date(now - 3L * 86_400_000L)));
        assertEquals(MessageFormat.format(Messages.get("git.time.hour"), 1L),
                GitTimes.relative(new Date(now - 3_600_000L)));
    }

    @Test
    public void nullDate_isEmpty() {
        assertEquals("", GitTimes.relative(null));
    }
}
