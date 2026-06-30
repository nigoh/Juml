// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Doxygen XML の {@code <compounddef>} 1 件 (クラス / インタフェース / 列挙 / パッケージ 等) を表すモデル。
 *
 * <p>MVP では種別・名前・brief 説明・メンバー一覧のみ保持する。継承関係や詳細説明、
 * グループ所属は後段ラウンドで追加する。</p>
 */
public final class DoxCompound {

    private final String refid;
    /** Doxygen の compound kind: {@code class} / {@code interface} / {@code enum} / {@code namespace} など。 */
    private final String kind;
    /** 完全修飾名 (Java なら {@code juml.core.Foo})。 */
    private final String name;
    /** brief 説明 (1 行要約)。無ければ空文字。 */
    private final String brief;
    /** detailed 説明の本文。R2 で API リファレンス表示に使う。無ければ空文字。 */
    private String detailed = "";
    private final List<DoxMember> members = new ArrayList<>();
    /** R4: 基底型 (extends/implements 先) の名前 ({@code <basecompoundref>})。 */
    private final List<String> baseTypes = new ArrayList<>();
    /** R4: 派生型 (既知のサブクラス) の名前 ({@code <derivedcompoundref>})。 */
    private final List<String> derivedTypes = new ArrayList<>();

    public DoxCompound(String refid, String kind, String name, String brief) {
        this.refid = refid != null ? refid : "";
        this.kind = kind != null ? kind : "";
        this.name = name != null ? name : "";
        this.brief = brief != null ? brief : "";
    }

    /** R2: detailed 説明を後付けする (compound XML パース時のみ)。 */
    public void setDetailed(String detailed) {
        this.detailed = detailed != null ? detailed : "";
    }

    public String getDetailed() {
        return detailed;
    }

    public void addMember(DoxMember member) {
        if (member != null) {
            members.add(member);
        }
    }

    public String getRefid() {
        return refid;
    }

    public String getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getBrief() {
        return brief;
    }

    public List<DoxMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public void addBaseType(String name) {
        if (name != null && !name.isEmpty()) {
            baseTypes.add(name);
        }
    }

    public void addDerivedType(String name) {
        if (name != null && !name.isEmpty()) {
            derivedTypes.add(name);
        }
    }

    public List<String> getBaseTypes() {
        return Collections.unmodifiableList(baseTypes);
    }

    public List<String> getDerivedTypes() {
        return Collections.unmodifiableList(derivedTypes);
    }

    /** ツリー表示用の短い名前 (最後の {@code .} 以降。無ければ全体)。 */
    public String simpleName() {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1) : name;
    }
}
