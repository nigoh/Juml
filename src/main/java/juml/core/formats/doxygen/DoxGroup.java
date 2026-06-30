// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Doxygen の論理グループ ({@code @defgroup} / {@code @ingroup} / {@code @addtogroup}) 1 件を表すモデル。
 *
 * <p>doxygen XML では {@code index.xml} の {@code <compound kind="group">} と、対応する
 * {@code group___*.xml} ({@code <compounddef kind="group">}) に現れる。各グループは表示用タイトル、
 * 所属する型 ({@code <innerclass>}) と下位グループ ({@code <innergroup>}) を持つ。R4 でグループ階層を
 * ツリー表示する。</p>
 */
public final class DoxGroup {

    private final String id;
    /** 人間可読のタイトル ({@code <title>})。無ければ id を使う想定。 */
    private final String title;
    /** 所属する型の完全修飾名 ({@code <innerclass>} のテキスト)。 */
    private final List<String> innerClassNames = new ArrayList<>();
    /** 下位グループの id ({@code <innergroup>} のテキスト)。 */
    private final List<String> innerGroupIds = new ArrayList<>();

    public DoxGroup(String id, String title) {
        this.id = id != null ? id : "";
        this.title = title != null && !title.isEmpty() ? title : this.id;
    }

    public void addInnerClass(String name) {
        if (name != null && !name.isEmpty()) {
            innerClassNames.add(name);
        }
    }

    public void addInnerGroup(String groupId) {
        if (groupId != null && !groupId.isEmpty()) {
            innerGroupIds.add(groupId);
        }
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getInnerClassNames() {
        return Collections.unmodifiableList(innerClassNames);
    }

    public List<String> getInnerGroupIds() {
        return Collections.unmodifiableList(innerGroupIds);
    }
}
