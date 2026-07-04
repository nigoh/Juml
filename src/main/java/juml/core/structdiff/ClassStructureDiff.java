// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.structdiff;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 同一ソースの新旧 2 バージョンを解析した {@link JavaClassInfo} リスト同士を比較し、
 * クラス構造レベルの差分 (クラス/フィールド/メソッド/enum 定数の追加・削除・変更) を求める。
 *
 * <p>git 履歴の「コミット間でクラス構造がどう変わったか」を UML で可視化する用途を想定し、
 * 行単位のテキスト diff ではなく宣言単位の構造 diff を返す。照合キーはクラスが完全修飾名、
 * フィールド/enum 定数が名前、メソッドが「名前 + 引数型リスト」(オーバーロード対応)。</p>
 */
public final class ClassStructureDiff {

    private ClassStructureDiff() {
    }

    /** 差分の種別。 */
    public enum ChangeKind { ADDED, REMOVED, MODIFIED, UNCHANGED }

    /** メンバー (フィールド / メソッド / enum 定数) 1 件分の差分。 */
    public static final class MemberDiff {
        public final ChangeKind kind;
        /** 旧側の表示ラベル (ADDED のとき null)。 */
        public final String oldLabel;
        /** 新側の表示ラベル (REMOVED のとき null)。 */
        public final String newLabel;

        MemberDiff(ChangeKind kind, String oldLabel, String newLabel) {
            this.kind = kind;
            this.oldLabel = oldLabel;
            this.newLabel = newLabel;
        }

        /** 表示用ラベル (新側優先)。 */
        public String label() {
            return newLabel != null ? newLabel : oldLabel;
        }
    }

    /** クラス 1 件分の差分。 */
    public static final class ClassDiff {
        public final ChangeKind kind;
        /** 旧側のクラス情報 (ADDED のとき null)。 */
        public final JavaClassInfo oldClass;
        /** 新側のクラス情報 (REMOVED のとき null)。 */
        public final JavaClassInfo newClass;
        /** フィールド (enum 定数含む) の差分。新側の宣言順、削除分は末尾。 */
        public final List<MemberDiff> fields;
        /** メソッド / コンストラクタの差分。新側の宣言順、削除分は末尾。 */
        public final List<MemberDiff> methods;
        /** クラス宣言ヘッダの変化 ("extends: A → B" 等の表示用文字列)。 */
        public final List<String> headerChanges;

        ClassDiff(ChangeKind kind, JavaClassInfo oldClass, JavaClassInfo newClass,
                  List<MemberDiff> fields, List<MemberDiff> methods,
                  List<String> headerChanges) {
            this.kind = kind;
            this.oldClass = oldClass;
            this.newClass = newClass;
            this.fields = fields;
            this.methods = methods;
            this.headerChanges = headerChanges;
        }

        /** どちらか存在する側のクラス情報 (新側優先)。 */
        public JavaClassInfo anySide() {
            return newClass != null ? newClass : oldClass;
        }

        /** 表示名 ({@code Outer.Inner} 形式)。 */
        public String displayName() {
            JavaClassInfo c = anySide();
            String enc = c.getEnclosingClass();
            return (enc != null && !enc.isEmpty() ? enc + "." : "") + c.getSimpleName();
        }
    }

    /**
     * 新旧クラスリストを比較して差分リストを返す。
     * 並び順は「新側の宣言順 → 旧側にのみ存在するクラス」。
     */
    public static List<ClassDiff> compare(List<JavaClassInfo> oldClasses,
                                          List<JavaClassInfo> newClasses) {
        Map<String, JavaClassInfo> oldByQn = byQualifiedName(oldClasses);
        Map<String, JavaClassInfo> newByQn = byQualifiedName(newClasses);

        List<ClassDiff> out = new ArrayList<>();
        for (Map.Entry<String, JavaClassInfo> e : newByQn.entrySet()) {
            JavaClassInfo oldC = oldByQn.get(e.getKey());
            if (oldC == null) {
                out.add(wholeClass(ChangeKind.ADDED, null, e.getValue()));
            } else {
                out.add(diffClass(oldC, e.getValue()));
            }
        }
        for (Map.Entry<String, JavaClassInfo> e : oldByQn.entrySet()) {
            if (!newByQn.containsKey(e.getKey())) {
                out.add(wholeClass(ChangeKind.REMOVED, e.getValue(), null));
            }
        }
        return out;
    }

    private static Map<String, JavaClassInfo> byQualifiedName(List<JavaClassInfo> classes) {
        Map<String, JavaClassInfo> map = new LinkedHashMap<>();
        if (classes != null) {
            for (JavaClassInfo c : classes) {
                map.putIfAbsent(c.getQualifiedName(), c);
            }
        }
        return map;
    }

    /** クラス丸ごと追加/削除の ClassDiff を作る (全メンバーが同じ種別になる)。 */
    private static ClassDiff wholeClass(ChangeKind kind, JavaClassInfo oldC,
                                        JavaClassInfo newC) {
        JavaClassInfo c = newC != null ? newC : oldC;
        List<MemberDiff> fields = new ArrayList<>();
        for (Map.Entry<String, String> e : fieldLabels(c).entrySet()) {
            fields.add(member(kind, e.getValue()));
        }
        List<MemberDiff> methods = new ArrayList<>();
        for (Map.Entry<String, String> e : methodLabels(c).entrySet()) {
            methods.add(member(kind, e.getValue()));
        }
        return new ClassDiff(kind, oldC, newC, fields, methods, List.of());
    }

    private static MemberDiff member(ChangeKind kind, String label) {
        return new MemberDiff(kind,
                kind == ChangeKind.ADDED ? null : label,
                kind == ChangeKind.REMOVED ? null : label);
    }

    /** 両側に存在するクラスのメンバー・ヘッダを比較する。 */
    private static ClassDiff diffClass(JavaClassInfo oldC, JavaClassInfo newC) {
        List<MemberDiff> fields = diffLabels(fieldLabels(oldC), fieldLabels(newC));
        List<MemberDiff> methods = diffLabels(methodLabels(oldC), methodLabels(newC));
        List<String> header = headerChanges(oldC, newC);

        boolean modified = !header.isEmpty()
                || anyChange(fields) || anyChange(methods);
        return new ClassDiff(modified ? ChangeKind.MODIFIED : ChangeKind.UNCHANGED,
                oldC, newC, fields, methods, header);
    }

    private static boolean anyChange(List<MemberDiff> members) {
        for (MemberDiff m : members) {
            if (m.kind != ChangeKind.UNCHANGED) {
                return true;
            }
        }
        return false;
    }

    /**
     * 照合キー → 表示ラベルの 2 つの Map を突き合わせ、メンバー差分を作る。
     * キーが一致しラベルも一致なら UNCHANGED、ラベルが違えば MODIFIED。
     */
    private static List<MemberDiff> diffLabels(Map<String, String> oldMap,
                                               Map<String, String> newMap) {
        List<MemberDiff> out = new ArrayList<>();
        for (Map.Entry<String, String> e : newMap.entrySet()) {
            String oldLabel = oldMap.get(e.getKey());
            if (oldLabel == null) {
                out.add(new MemberDiff(ChangeKind.ADDED, null, e.getValue()));
            } else if (oldLabel.equals(e.getValue())) {
                out.add(new MemberDiff(ChangeKind.UNCHANGED, oldLabel, e.getValue()));
            } else {
                out.add(new MemberDiff(ChangeKind.MODIFIED, oldLabel, e.getValue()));
            }
        }
        for (Map.Entry<String, String> e : oldMap.entrySet()) {
            if (!newMap.containsKey(e.getKey())) {
                out.add(new MemberDiff(ChangeKind.REMOVED, e.getValue(), null));
            }
        }
        return out;
    }

    /** フィールドと enum 定数の「照合キー → 表示ラベル」(宣言順)。 */
    private static Map<String, String> fieldLabels(JavaClassInfo c) {
        Map<String, String> map = new LinkedHashMap<>();
        List<String> constants = c.getEnumConstants();
        for (int i = 0; i < constants.size(); i++) {
            String args = i < c.getEnumConstantArgs().size()
                    ? c.getEnumConstantArgs().get(i) : "";
            map.putIfAbsent("enum:" + constants.get(i), constants.get(i) + args);
        }
        for (JavaFieldInfo f : c.getFields()) {
            map.putIfAbsent("field:" + f.getName(), fieldLabel(f));
        }
        return map;
    }

    /** メソッド/コンストラクタの「照合キー → 表示ラベル」(宣言順)。 */
    private static Map<String, String> methodLabels(JavaClassInfo c) {
        Map<String, String> map = new LinkedHashMap<>();
        for (JavaMethodInfo m : c.getMethods()) {
            // コンストラクタ名はクラス単純名なので、同名メソッド (合法だが稀な
            // class Foo { Foo(int){} void Foo(int){} }) とキーが衝突して片方が
            // putIfAbsent で脱落するのを防ぐため種別で名前空間を分ける。
            String key = (m.isConstructor() ? "ctor:" : "method:") + m.getName()
                    + "(" + String.join(",", m.getParameterTypes()) + ")";
            map.putIfAbsent(key, methodLabel(m));
        }
        return map;
    }

    /**
     * フィールドの正規化ラベル。可視性・static/final・型まで含むため、
     * ラベル不一致 = 宣言のいずれかが変わったことを意味する。
     */
    static String fieldLabel(JavaFieldInfo f) {
        StringBuilder sb = new StringBuilder();
        sb.append(f.getVisibility().mark()).append(' ').append(f.getName());
        if (f.getType() != null && !f.getType().isEmpty()) {
            sb.append(" : ").append(f.getType());
        }
        if (f.isStatic()) {
            sb.append(" {static}");
        }
        if (f.isFinal()) {
            sb.append(" {final}");
        }
        return sb.toString();
    }

    /** メソッドの正規化ラベル ({@link #fieldLabel} と同趣旨)。 */
    static String methodLabel(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getVisibility().mark()).append(' ').append(m.getName());
        sb.append('(').append(String.join(", ", m.getParameterTypes())).append(')');
        if (!m.isConstructor() && m.getReturnType() != null
                && !m.getReturnType().isEmpty()) {
            sb.append(" : ").append(m.getReturnType());
        }
        if (m.isStatic()) {
            sb.append(" {static}");
        }
        if (m.isAbstract()) {
            sb.append(" {abstract}");
        }
        return sb.toString();
    }

    /** クラス宣言ヘッダ (kind / extends / implements / modifiers / 型パラメータ) の変化。 */
    private static List<String> headerChanges(JavaClassInfo oldC, JavaClassInfo newC) {
        List<String> out = new ArrayList<>();
        if (oldC.getKind() != newC.getKind()) {
            out.add("kind: " + oldC.getKind() + " → " + newC.getKind());
        }
        if (!Objects.equals(oldC.getSuperClass(), newC.getSuperClass())) {
            out.add("extends: " + orNone(oldC.getSuperClass())
                    + " → " + orNone(newC.getSuperClass()));
        }
        setChange(out, "implements",
                new LinkedHashSet<>(oldC.getInterfaces()),
                new LinkedHashSet<>(newC.getInterfaces()));
        setChange(out, "modifiers",
                new LinkedHashSet<>(oldC.getModifiers()),
                new LinkedHashSet<>(newC.getModifiers()));
        if (!Objects.equals(oldC.getTypeParameters(), newC.getTypeParameters())) {
            out.add("type params: " + orNone(oldC.getTypeParameters())
                    + " → " + orNone(newC.getTypeParameters()));
        }
        return out;
    }

    /** 集合の増減を "label: +A -B" 形式で 1 行にまとめる (変化なしなら何もしない)。 */
    private static void setChange(List<String> out, String label,
                                  Set<String> oldSet, Set<String> newSet) {
        List<String> parts = new ArrayList<>();
        for (String s : newSet) {
            if (!oldSet.contains(s)) {
                parts.add("+" + s);
            }
        }
        for (String s : oldSet) {
            if (!newSet.contains(s)) {
                parts.add("-" + s);
            }
        }
        if (!parts.isEmpty()) {
            out.add(label + ": " + String.join(" ", parts));
        }
    }

    private static String orNone(String s) {
        return s == null || s.isEmpty() ? "(none)" : s;
    }
}
