// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナーが編集するシーケンス図モデル (参加者群 + タイムライン項目群)。
 *
 * <p>PlantUML テキストとの相互変換は {@link SeqSketchCodec} が担う。
 * このクラスは構造の保持と基本操作 (追加・削除・改名・並べ替え) のみ。</p>
 */
public final class SeqSketchModel {

    private final List<SeqParticipant> participants = new ArrayList<>();
    private final List<SeqItem> items = new ArrayList<>();
    /** {@code @startuml <name>} の名前サフィックス (無ければ空文字)。往復で保全する。 */
    private String diagramName = "";

    /** 参加者 (ライフライン)。リスト順が横並び順。 */
    public List<SeqParticipant> getParticipants() {
        return participants;
    }

    /** タイムライン項目。リスト順が時系列。 */
    public List<SeqItem> getItems() {
        return items;
    }

    /** {@code @startuml} に付いた図名 (無ければ空文字)。 */
    public String getDiagramName() {
        return diagramName;
    }

    /** {@code @startuml} の図名を設定する (null は空文字として扱う)。 */
    public void setDiagramName(String name) {
        this.diagramName = name != null ? name : "";
    }

    /** 名前で参加者を探す (無ければ null)。 */
    public SeqParticipant findParticipant(String name) {
        for (SeqParticipant p : participants) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    /** 名前で参加者を探し、無ければ暗黙参加者 (declared=false) として末尾に作る。 */
    public SeqParticipant obtainParticipant(String name) {
        SeqParticipant p = findParticipant(name);
        if (p == null) {
            p = new SeqParticipant(name, SeqParticipant.Kind.PARTICIPANT, false);
            participants.add(p);
        }
        return p;
    }

    /** {@code base}, {@code base2}, ... の形式で未使用の参加者名を作る。 */
    public String uniqueName(String base) {
        if (findParticipant(base) == null) {
            return base;
        }
        int n = 2;
        while (findParticipant(base + n) != null) {
            n++;
        }
        return base + n;
    }

    /** 参加者を削除し、その参加者に関係する項目もまとめて取り除く。 */
    public void removeParticipant(SeqParticipant target) {
        participants.remove(target);
        items.removeIf(m -> m.touches(target.getName()));
    }

    /** 参加者名の変更に追随して項目の端点も付け替える。 */
    public void renameParticipant(SeqParticipant target, String newName) {
        String old = target.getName();
        target.setName(newName);
        for (SeqItem m : items) {
            if (m.getKind() == SeqItem.Kind.MESSAGE) {
                if (m.getFrom().equals(old)) {
                    m.setFrom(newName);
                }
                if (m.getTo().equals(old)) {
                    m.setTo(newName);
                }
            } else if (m.getTarget().equals(old)) {
                m.setTarget(newName);
            }
        }
    }

    /** 参加者を指定位置へ並べ替える (範囲外は端へ丸める)。 */
    public void moveParticipant(SeqParticipant target, int newIndex) {
        int cur = participants.indexOf(target);
        if (cur < 0) {
            return;
        }
        participants.remove(cur);
        int idx = Math.max(0, Math.min(newIndex, participants.size()));
        participants.add(idx, target);
    }

    /** タイムライン項目を指定位置へ並べ替える (範囲外は端へ丸める)。 */
    public void moveItem(SeqItem target, int newIndex) {
        int cur = items.indexOf(target);
        if (cur < 0) {
            return;
        }
        items.remove(cur);
        int idx = Math.max(0, Math.min(newIndex, items.size()));
        items.add(idx, target);
    }
}
