// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Doxygen XML 出力一式 ({@code index.xml} + 各 compound XML) をパースした結果のルートモデル。
 *
 * <p>MVP ではクラス/インタフェース等の compound 一覧のみを保持する。
 * TODO/Bug/Deprecated 集約 (xrefitem) やグループ階層は後段ラウンドでフィールドを追加する。</p>
 */
public final class DoxModel {

    private final List<DoxCompound> compounds = new ArrayList<>();
    /** R3: プロジェクト横断の xref 項目 (@todo / @bug / @deprecated)。 */
    private final List<DoxXrefItem> xrefItems = new ArrayList<>();
    /** R4: 論理グループ (@defgroup / @ingroup)。 */
    private final List<DoxGroup> groups = new ArrayList<>();

    public void addCompound(DoxCompound compound) {
        if (compound != null) {
            compounds.add(compound);
        }
    }

    public void addXrefItem(DoxXrefItem item) {
        if (item != null) {
            xrefItems.add(item);
        }
    }

    public List<DoxCompound> getCompounds() {
        return Collections.unmodifiableList(compounds);
    }

    public List<DoxXrefItem> getXrefItems() {
        return Collections.unmodifiableList(xrefItems);
    }

    public void addGroup(DoxGroup group) {
        if (group != null) {
            groups.add(group);
        }
    }

    public List<DoxGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public boolean isEmpty() {
        return compounds.isEmpty();
    }
}
