// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.structdiff;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.structdiff.ClassStructureDiff.ChangeKind;
import juml.core.structdiff.ClassStructureDiff.ClassDiff;
import juml.core.structdiff.ClassStructureDiff.MemberDiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ClassStructureDiff} の結果を、差分を色分けした PlantUML クラス図テキストへ変換する。
 *
 * <p>コードの unified diff と同じ感覚で読めるよう、GitHub 風の配色を使う:
 * 追加 = 緑、削除 = 赤 (打ち消し線)、変更 = 黄 (旧宣言を打ち消し線で併記)。
 * クラス単位の追加/削除は背景色とステレオタイプ
 * ({@code <<added>>} / {@code <<removed>>} / {@code <<modified>>}) で示す。</p>
 */
public final class PlantUmlStructureDiffDiagram {

    /** 追加の背景色 (GitHub diff 風)。 */
    static final String ADDED_BG = "#E6FFEC";
    /** 削除の背景色。 */
    static final String REMOVED_BG = "#FFEBE9";
    /** 変更の背景色。 */
    static final String MODIFIED_BG = "#FFF8C5";
    /** 追加テキスト色。 */
    static final String ADDED_FG = "#1A7F37";
    /** 削除テキスト色。 */
    static final String REMOVED_FG = "#CF222E";
    /** 変更テキスト色。 */
    static final String MODIFIED_FG = "#9A6700";

    private PlantUmlStructureDiffDiagram() {
    }

    /** 出力オプション。 */
    public static class Options {
        /** タイトル文字列 (null で省略)。 */
        public String title;
        /** 変更のないメンバーも表示する (差分の前後関係が分かる)。既定 true。 */
        public boolean showUnchangedMembers = true;
        /** 変更のないクラスも表示する。既定 false (差分に集中)。 */
        public boolean includeUnchangedClasses = false;
        /** 凡例を出力する。既定 true。 */
        public boolean includeLegend = true;
        /** 差分がひとつもないときに図中へ出す文言。 */
        public String emptyMessage = "No structural changes";
    }

    /** 差分リストから PlantUML テキストを生成する。 */
    public static String generate(List<ClassDiff> diffs, Options opt) {
        if (opt == null) {
            opt = new Options();
        }
        List<ClassDiff> shown = new ArrayList<>();
        if (diffs != null) {
            for (ClassDiff d : diffs) {
                if (d.kind != ChangeKind.UNCHANGED || opt.includeUnchangedClasses) {
                    shown.add(d);
                }
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (opt.title != null && !opt.title.isEmpty()) {
            out.append("title ").append(escape(opt.title)).append('\n');
        }
        out.append("skinparam classAttributeIconSize 0\n");
        out.append("hide empty members\n");

        if (shown.isEmpty()) {
            out.append("note \"").append(escape(opt.emptyMessage))
                    .append("\" as EMPTY\n");
        } else {
            Map<ClassDiff, String> aliases = assignAliases(shown);
            for (ClassDiff d : shown) {
                appendClass(out, d, aliases.get(d), opt);
            }
            appendInheritance(out, shown, aliases);
        }
        if (opt.includeLegend) {
            appendLegend(out);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * 片側 (旧 or 新) のクラス図テキストを生成する。左右 (side-by-side) 比較用。
     *
     * <p>旧側は削除メンバーを赤打ち消し・変更メンバーを旧宣言 (黄) で示し、追加メンバーは
     * 出さない。新側は追加を緑・変更を新宣言 (黄) で示し、削除は出さない。変更のあるノード
     * だけが図中で色付くため、旧図・新図を並べると「どこが変わったか」が図の中で分かる。</p>
     *
     * @param oldSide true なら旧 (比較元) 側、false なら新 (比較先) 側
     */
    public static String generateSide(List<ClassDiff> diffs, boolean oldSide, Options opt) {
        if (opt == null) {
            opt = new Options();
        }
        List<ClassDiff> shown = new ArrayList<>();
        if (diffs != null) {
            for (ClassDiff d : diffs) {
                // その側に存在するクラスだけを出す (旧側に ADDED は無い / 新側に REMOVED は無い)。
                boolean existsHere = oldSide
                        ? d.kind != ChangeKind.ADDED
                        : d.kind != ChangeKind.REMOVED;
                if (!existsHere) {
                    continue;
                }
                if (d.kind != ChangeKind.UNCHANGED || opt.includeUnchangedClasses) {
                    shown.add(d);
                }
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (opt.title != null && !opt.title.isEmpty()) {
            out.append("title ").append(escape(opt.title)).append('\n');
        }
        out.append("skinparam classAttributeIconSize 0\n");
        out.append("hide empty members\n");
        if (shown.isEmpty()) {
            out.append("note \"").append(escape(opt.emptyMessage))
                    .append("\" as EMPTY\n");
        } else {
            Map<ClassDiff, String> aliases = assignAliases(shown);
            for (ClassDiff d : shown) {
                appendClassSide(out, d, aliases.get(d), oldSide, opt);
            }
            appendInheritance(out, shown, aliases);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /** 片側のクラス宣言ブロックを出力する。 */
    private static void appendClassSide(StringBuilder out, ClassDiff d, String alias,
                                        boolean oldSide, Options opt) {
        out.append(keyword(d.anySide()))
                .append(" \"").append(escape(d.displayName())).append("\" as ")
                .append(alias);
        // その側で「変わる/変わった」クラスだけ背景を付ける。
        if (d.kind == ChangeKind.MODIFIED) {
            out.append(" <<modified>> ").append(MODIFIED_BG);
        } else if (oldSide && d.kind == ChangeKind.REMOVED) {
            out.append(" <<removed>> ").append(REMOVED_BG);
        } else if (!oldSide && d.kind == ChangeKind.ADDED) {
            out.append(" <<added>> ").append(ADDED_BG);
        }
        out.append(" {\n");

        StringBuilder fieldBuf = new StringBuilder();
        int hidden = appendMembersSide(fieldBuf, d.fields, oldSide, opt);
        StringBuilder methodBuf = new StringBuilder();
        hidden += appendMembersSide(methodBuf, d.methods, oldSide, opt);
        out.append(fieldBuf);
        if (fieldBuf.length() > 0 && methodBuf.length() > 0) {
            out.append("  --\n");
        }
        out.append(methodBuf);
        if (hidden > 0) {
            out.append("  .. ").append(hidden).append(" unchanged ..\n");
        }
        out.append("}\n");
    }

    /** 片側のメンバー行を出力する。表示省略した UNCHANGED 件数を返す。 */
    private static int appendMembersSide(StringBuilder out, List<MemberDiff> members,
                                         boolean oldSide, Options opt) {
        int hidden = 0;
        for (MemberDiff m : members) {
            switch (m.kind) {
                case ADDED:
                    if (!oldSide) {
                        out.append("  ").append(colored(ADDED_FG, escape(m.newLabel)))
                                .append('\n');
                    }
                    break;
                case REMOVED:
                    if (oldSide) {
                        out.append("  ").append(struck(REMOVED_FG, escape(m.oldLabel)))
                                .append('\n');
                    }
                    break;
                case MODIFIED:
                    out.append("  ").append(colored(MODIFIED_FG,
                            escape(oldSide ? m.oldLabel : m.newLabel))).append('\n');
                    break;
                default:
                    if (opt.showUnchangedMembers) {
                        out.append("  ").append(escape(m.label())).append('\n');
                    } else {
                        hidden++;
                    }
                    break;
            }
        }
        return hidden;
    }

    private static Map<ClassDiff, String> assignAliases(List<ClassDiff> shown) {
        Map<ClassDiff, String> aliases = new LinkedHashMap<>();
        int seq = 0;
        for (ClassDiff d : shown) {
            aliases.put(d, "C" + seq++);
        }
        return aliases;
    }

    /** クラス 1 件分の宣言ブロックを出力する。 */
    private static void appendClass(StringBuilder out, ClassDiff d, String alias,
                                    Options opt) {
        out.append(keyword(d.anySide()))
                .append(" \"").append(escape(d.displayName())).append("\" as ")
                .append(alias);
        switch (d.kind) {
            case ADDED:
                out.append(" <<added>> ").append(ADDED_BG);
                break;
            case REMOVED:
                out.append(" <<removed>> ").append(REMOVED_BG);
                break;
            case MODIFIED:
                out.append(" <<modified>> ").append(MODIFIED_BG);
                break;
            default:
                break;
        }
        out.append(" {\n");

        if (!d.headerChanges.isEmpty()) {
            for (String h : d.headerChanges) {
                out.append("  ").append(colored(MODIFIED_FG, escape(h))).append('\n');
            }
            out.append("  ..\n");
        }
        // フィールド区画とメソッド区画を別々に組み立て、両区画とも実際に行を
        // 出力したときだけ区切り線 "--" を挟む。showUnchangedMembers=false で
        // フィールドが全て不変 (= 1 行も出ない) のにメソッドがある場合に、空区画の
        // 直後へ孤立した "--" が残るのを防ぐ。
        StringBuilder fieldBuf = new StringBuilder();
        int hidden = appendMembers(fieldBuf, d, d.fields, opt);
        StringBuilder methodBuf = new StringBuilder();
        hidden += appendMembers(methodBuf, d, d.methods, opt);
        out.append(fieldBuf);
        if (fieldBuf.length() > 0 && methodBuf.length() > 0) {
            out.append("  --\n");
        }
        out.append(methodBuf);
        if (hidden > 0) {
            out.append("  .. ").append(hidden).append(" unchanged ..\n");
        }
        out.append("}\n");
    }

    /**
     * メンバー差分行を出力する。表示を省略した UNCHANGED 件数を返す。
     */
    private static int appendMembers(StringBuilder out, ClassDiff d,
                                     List<MemberDiff> members, Options opt) {
        int hidden = 0;
        for (MemberDiff m : members) {
            switch (m.kind) {
                case ADDED:
                    // クラスごと追加/削除の場合は背景色が示すので行の装飾は薄くする
                    out.append("  ").append(d.kind == ChangeKind.ADDED
                            ? escape(m.newLabel)
                            : colored(ADDED_FG, escape(m.newLabel))).append('\n');
                    break;
                case REMOVED:
                    out.append("  ").append(d.kind == ChangeKind.REMOVED
                            ? escape(m.oldLabel)
                            : struck(REMOVED_FG, escape(m.oldLabel))).append('\n');
                    break;
                case MODIFIED:
                    out.append("  ").append(struck(REMOVED_FG, escape(m.oldLabel)))
                            .append('\n');
                    out.append("  ").append(colored(MODIFIED_FG, escape(m.newLabel)))
                            .append('\n');
                    break;
                default:
                    if (opt.showUnchangedMembers) {
                        out.append("  ").append(escape(m.label())).append('\n');
                    } else {
                        hidden++;
                    }
                    break;
            }
        }
        return hidden;
    }

    /** 図に含まれるクラス同士の継承/実装エッジを出力する (図外の型へは張らない)。 */
    private static void appendInheritance(StringBuilder out, List<ClassDiff> shown,
                                          Map<ClassDiff, String> aliases) {
        Map<String, String> byName = new LinkedHashMap<>();
        for (ClassDiff d : shown) {
            String alias = aliases.get(d);
            byName.putIfAbsent(d.anySide().getSimpleName(), alias);
            byName.putIfAbsent(d.displayName(), alias);
            byName.putIfAbsent(d.anySide().getQualifiedName(), alias);
        }
        for (ClassDiff d : shown) {
            String child = aliases.get(d);
            JavaClassInfo c = d.anySide();
            String parent = resolve(byName, c.getSuperClass());
            if (parent != null && !parent.equals(child)) {
                out.append(parent).append(" <|-- ").append(child).append('\n');
            }
            for (String itf : c.getInterfaces()) {
                String target = resolve(byName, itf);
                if (target != null && !target.equals(child)) {
                    out.append(target).append(" <|.. ").append(child).append('\n');
                }
            }
        }
    }

    private static String resolve(Map<String, String> byName, String typeRef) {
        if (typeRef == null || typeRef.isEmpty()) {
            return null;
        }
        // ジェネリクス引数は照合から外す (Base<T> extends 対応)
        int lt = typeRef.indexOf('<');
        return byName.get(lt > 0 ? typeRef.substring(0, lt) : typeRef);
    }

    private static void appendLegend(StringBuilder out) {
        out.append("legend right\n");
        out.append("|<").append(ADDED_BG).append(">      | added |\n");
        out.append("|<").append(REMOVED_BG).append(">      | removed |\n");
        out.append("|<").append(MODIFIED_BG).append(">      | modified |\n");
        out.append("endlegend\n");
    }

    private static String keyword(JavaClassInfo c) {
        switch (c.getKind()) {
            case INTERFACE:
            case AIDL_INTERFACE:
                return "interface";
            case ENUM:
                return "enum";
            case ANNOTATION:
                return "annotation";
            default:
                return c.isAbstract() ? "abstract class" : "class";
        }
    }

    private static String colored(String color, String text) {
        return "<color:" + color + ">" + text + "</color>";
    }

    private static String struck(String color, String text) {
        return "<color:" + color + "><s>" + text + "</s></color>";
    }

    /**
     * テキスト文脈の {@code <} をチルダエスケープする
     * (同梱 PlantUML は HTML エンティティを解釈しないため。
     * {@code juml.core.formats.uml.PlantUmlCommentFormatter#escapeText} と同方式)。
     */
    static String escape(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("<", "~<");
    }
}
