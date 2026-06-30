// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * タブフォーカスの履歴を管理し、Alt+Left/Right による
 * 前後ナビゲーション (VS Code 風 Go Back / Go Forward) を実現する。
 *
 * <p>タブ選択が変わるたびに {@link #push(String)} を呼び、
 * 現在位置より先の "forward" 履歴を破棄して新しいエントリを積む。
 * {@link #back()} / {@link #forward()} はカーソルを移動するだけで
 * 履歴自体は変更しない。</p>
 */
final class NavigationHistory {

    private static final int MAX_SIZE = 50;

    private final List<String> entries = new ArrayList<>();
    private int cursor = -1;
    private boolean navigating;

    boolean canGoBack() {
        return cursor > 0;
    }

    boolean canGoForward() {
        return cursor < entries.size() - 1;
    }

    /**
     * 新しいタブキーを履歴に積む。ナビゲーション中 ({@link #back()}/{@link #forward()})
     * の選択変更では呼ばない (navigating フラグで抑制)。
     */
    void push(String tabKey) {
        if (navigating || tabKey == null) {
            return;
        }
        if (cursor >= 0 && cursor < entries.size()
                && Objects.equals(entries.get(cursor), tabKey)) {
            return;
        }
        if (cursor < entries.size() - 1) {
            entries.subList(cursor + 1, entries.size()).clear();
        }
        entries.add(tabKey);
        if (entries.size() > MAX_SIZE) {
            entries.remove(0);
        }
        cursor = entries.size() - 1;
    }

    /** 1 つ戻って対象のタブキーを返す。戻れなければ null。 */
    String back() {
        if (!canGoBack()) {
            return null;
        }
        cursor--;
        return entries.get(cursor);
    }

    /** 1 つ進んで対象のタブキーを返す。進めなければ null。 */
    String forward() {
        if (!canGoForward()) {
            return null;
        }
        cursor++;
        return entries.get(cursor);
    }

    /** ナビゲーション操作中は push を抑制するためのガード。 */
    void setNavigating(boolean navigating) {
        this.navigating = navigating;
    }

    /** タブが閉じられたとき履歴から除去する。 */
    void remove(String tabKey) {
        boolean beforeCursor = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (Objects.equals(entries.get(i), tabKey)) {
                if (i < cursor) {
                    beforeCursor = true;
                }
                entries.remove(i);
            }
        }
        if (beforeCursor) {
            cursor = Math.max(0, cursor - 1);
        }
        cursor = Math.min(cursor, entries.size() - 1);
    }

    void clear() {
        entries.clear();
        cursor = -1;
    }
}
