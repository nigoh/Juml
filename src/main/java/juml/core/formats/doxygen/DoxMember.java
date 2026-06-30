// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Doxygen XML の {@code <memberdef>} 1 件 (メソッド / フィールド / 列挙子 等) を表すモデル。
 *
 * <p>ツリー表示用の最小情報 (種別・名前・引数文字列・brief) に加え、R2 で API リファレンス
 * 詳細表示に使う情報 (戻り値型・detailed 説明・{@code @param}/{@code @return}/{@code @throws})
 * を保持する。詳細は {@link #setDetail} で後付けする (index.xml 縮退時は brief までで止まる)。</p>
 */
public final class DoxMember {

    private final String refid;
    /** Doxygen の member kind: {@code function} / {@code variable} / {@code enum} / {@code property} など。 */
    private final String kind;
    private final String name;
    /** メソッドの引数並び ({@code (int a, String b)} 形式)。フィールド等では空。 */
    private final String args;
    /** brief 説明 (1 行要約)。無ければ空文字。 */
    private final String brief;

    /** 戻り値型 / フィールド型 ({@code <type>})。無ければ空文字。 */
    private String type = "";
    /** detailed 説明の本文 (構造化された @param/@return/@throws を除いた散文)。 */
    private String detailed = "";
    private final List<DoxParam> params = new ArrayList<>();
    /** {@code @return} 説明。無ければ空文字。 */
    private String returns = "";
    private final List<DoxParam> throwsList = new ArrayList<>();
    /** R4: このメンバーが参照している先の名前 ({@code <references>})。 */
    private final List<String> references = new ArrayList<>();
    /** R4: このメンバーを参照している側の名前 ({@code <referencedby>})。 */
    private final List<String> referencedBy = new ArrayList<>();

    public DoxMember(String refid, String kind, String name, String args, String brief) {
        this.refid = refid != null ? refid : "";
        this.kind = kind != null ? kind : "";
        this.name = name != null ? name : "";
        this.args = args != null ? args : "";
        this.brief = brief != null ? brief : "";
    }

    /** R2: API リファレンス詳細を後付けする (compound XML パース時のみ)。 */
    public void setDetail(String type, String detailed, List<DoxParam> params,
                          String returns, List<DoxParam> throwsList) {
        this.type = type != null ? type : "";
        this.detailed = detailed != null ? detailed : "";
        this.returns = returns != null ? returns : "";
        this.params.clear();
        if (params != null) {
            this.params.addAll(params);
        }
        this.throwsList.clear();
        if (throwsList != null) {
            this.throwsList.addAll(throwsList);
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

    public String getArgs() {
        return args;
    }

    public String getBrief() {
        return brief;
    }

    public String getType() {
        return type;
    }

    public String getDetailed() {
        return detailed;
    }

    public List<DoxParam> getParams() {
        return Collections.unmodifiableList(params);
    }

    public String getReturns() {
        return returns;
    }

    public List<DoxParam> getThrows() {
        return Collections.unmodifiableList(throwsList);
    }

    /** R4: 参照先 / 参照元を後付けする (重複は呼び出し側で除去済みを想定)。 */
    public void setReferences(List<String> references, List<String> referencedBy) {
        this.references.clear();
        if (references != null) {
            this.references.addAll(references);
        }
        this.referencedBy.clear();
        if (referencedBy != null) {
            this.referencedBy.addAll(referencedBy);
        }
    }

    public List<String> getReferences() {
        return Collections.unmodifiableList(references);
    }

    public List<String> getReferencedBy() {
        return Collections.unmodifiableList(referencedBy);
    }

    /** ツリー表示用のラベル ({@code name(args)} 形式。args が空なら name のみ)。 */
    public String displayLabel() {
        return args.isEmpty() ? name : name + args;
    }
}
