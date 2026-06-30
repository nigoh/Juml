// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.util.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Functions / Members 一覧の「表示範囲」絞り込みとコンテキスト文言の組み立て。
 *
 * <p>アクティブタブの題材（{@link TreeNodeOpenRequest}）に応じて対象クラスを絞る純粋ロジックを
 * {@link UmlMainFrame} から切り出したもの（責務分離 + ファイル肥大の抑制）。</p>
 */
final class ListScope {

    private ListScope() {
    }

    /** Functions / Members 一覧の生成結果 (本文 + コンテキスト文言)。 */
    static final class Result {
        final String text;
        final String context;

        Result(String text, String context) {
            this.text = text;
            this.context = context;
        }
    }

    /**
     * 表示範囲セレクタの選択に応じて、一覧の対象クラスを絞り込む。
     * {@link FilteredListPanel.Scope#ACTIVE_TAB} かつ直近の図タブ題材が分かる場合のみ絞り込み、
     * それ以外（全体指定 / 図タブ未オープン / 題材不明）はプロジェクト全体を返す。
     */
    static List<JavaClassInfo> filter(List<JavaClassInfo> all,
                                      FilteredListPanel.Scope scope,
                                      TreeNodeOpenRequest subject) {
        if (scope == FilteredListPanel.Scope.PROJECT || subject == null) {
            return all;
        }
        List<JavaClassInfo> narrowed = new ArrayList<>();
        for (JavaClassInfo c : all) {
            if (matchesSubject(c, subject)) {
                narrowed.add(c);
            }
        }
        // 題材に一致するクラスが無ければ（モジュール/Soong 題材など）全体にフォールバック。
        return narrowed.isEmpty() ? all : narrowed;
    }

    /** クラス {@code c} が、アクティブタブの題材 {@code subject} のスコープに含まれるか。 */
    private static boolean matchesSubject(JavaClassInfo c, TreeNodeOpenRequest subject) {
        String qn = c.getQualifiedName() == null ? "" : c.getQualifiedName();
        String pkg = c.getPackageName() == null ? "" : c.getPackageName();
        switch (subject.target) {
            case CLASS:
            case METHOD:
                String target = subject.classInfo == null ? null
                        : subject.classInfo.getQualifiedName();
                return target != null && target.equals(qn);
            case PACKAGE:
                String p = subject.name == null ? "" : subject.name;
                return pkg.equals(p) || pkg.startsWith(p + ".");
            default:
                return false; // MODULE / SOONG はクラス単位の所属が一意でないため全体表示
        }
    }

    /** コンテキスト行（対象・件数）の文言を組み立てる。 */
    static String context(FilteredListPanel.Scope scope, TreeNodeOpenRequest subject,
                          int classCount) {
        String count = classCount + Messages.get("list.count.suffix");
        if (scope == FilteredListPanel.Scope.PROJECT || subject == null
                || subject.target == TreeNodeOpenRequest.Target.MODULE
                || subject.target == TreeNodeOpenRequest.Target.SOONG) {
            return Messages.get("list.context.project") + " — " + count;
        }
        String label;
        switch (subject.target) {
            case CLASS:
            case METHOD:
                label = subject.classInfo == null ? "?" : subject.classInfo.getSimpleName();
                break;
            case PACKAGE:
                label = subject.name;
                break;
            default:
                label = "?";
        }
        return Messages.get("list.context.activeTab") + " " + label + " — " + count;
    }
}
