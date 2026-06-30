// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指定メソッドを起点に、どの関数を呼び出しているかを WBS (Work Breakdown Structure) 図で可視化する。
 *
 * <p>起点 ({@code entryClass.entryMethod}) から再帰的にメソッド呼び出しを追跡し、
 * 「クラス → メソッド → クラス → メソッド...」の階層ツリーとして出力する。
 * 同一クラスへの複数メソッド呼び出しは同一クラスノードの下にまとめる。
 * 循環呼び出し (DFS パス上の祖先) は {@code [↩]} で示し、それ以上展開しない。
 * 外部ライブラリへの呼び出しはノードのみ表示し、内部は追跡しない。</p>
 */
public final class PlantUmlCallGraphDiagram {

    /** 出力オプション。 */
    public static class Options {
        /** 凡例を末尾に追加する。 */
        public boolean includeLegend = true;
        /**
         * 再帰展開の最大階層数 (メソッド呼び出しの深さ)。
         * 1 = 起点が直接呼ぶメソッドのみ表示。デフォルト 4。
         */
        public int maxDepth = 4;
        /** 起点ノードの背景色 (PlantUML 形式)。 */
        public String entryColor = "#LightSkyBlue";
        /** プロジェクト内クラスのノード背景色。 */
        public String projectColor = "#LightYellow";
    }

    private PlantUmlCallGraphDiagram() {
    }

    /**
     * コールグラフ (WBS 形式) の PlantUML テキストを生成する。
     *
     * @param classes     プロジェクト内の全クラス情報
     * @param entryClass  起点クラス名 (simple name)
     * @param entryMethod 起点メソッド名
     * @param opts        出力オプション (null 可 → デフォルト使用)
     * @return PlantUML テキスト (@startwbs ... @endwbs)
     */
    public static String generate(List<JavaClassInfo> classes,
                                   String entryClass, String entryMethod,
                                   Options opts) {
        if (opts == null) {
            opts = new Options();
        }

        Map<String, JavaClassInfo> bySimpleName = buildSimpleNameIndex(classes);

        JavaClassInfo entryCls = bySimpleName.get(entryClass);
        if (entryCls == null) {
            return "@startwbs\n* [Class not found] " + entryClass + "\n@endwbs\n";
        }
        JavaMethodInfo entryMeth = findMethod(entryCls, entryMethod);
        if (entryMeth == null) {
            return "@startwbs\n* [Method not found] " + entryClass + "." + entryMethod
                    + "()\n@endwbs\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("@startwbs\n");
        sb.append("skinparam Padding 5\n");
        sb.append("\n");

        // 起点ルートノード (WBS depth 1)
        sb.append("*[").append(opts.entryColor).append("] ")
          .append(kindTag(entryCls.getKind())).append(entryClass).append(".").append(entryMethod).append("()\n");

        // DFS パス (循環検出のための祖先集合)
        Set<String> path = new LinkedHashSet<>();
        path.add(nodeKey(entryClass, entryMethod));

        buildWbs(sb, entryMeth, entryClass, bySimpleName, path, 1, opts);

        if (opts.includeLegend) {
            sb.append("\n");
            sb.append("legend top left\n");
            sb.append("  ").append(opts.entryColor).append(" 起点メソッド (entry)\n");
            sb.append("  ").append(opts.projectColor).append(" プロジェクト内クラス\n");
            sb.append("  (white) 外部 / 未解決クラス\n");
            sb.append("  [↩] 再帰 / 循環呼び出し\n");
            sb.append("  [C] クラス / [I] インタフェース\n");
            sb.append("endlegend\n");
        }

        sb.append("@endwbs\n");
        return sb.toString();
    }

    /**
     * 指定メソッドの呼び出し先を WBS ノードとして出力し、プロジェクト内クラスなら再帰展開する。
     *
     * <p>WBS 深さのパターン:
     * <ul>
     *   <li>callDepth × 2     — 呼び出し先クラスノード</li>
     *   <li>callDepth × 2 + 1 — 呼び出し先メソッドノード</li>
     * </ul>
     * </p>
     */
    private static void buildWbs(StringBuilder sb,
                                  JavaMethodInfo method, String ownerClass,
                                  Map<String, JavaClassInfo> bySimpleName,
                                  Set<String> path,
                                  int callDepth, Options opts) {
        if (method.getStatements() == null || method.getStatements().isEmpty()) {
            return;
        }

        // 呼び出し先をクラス単位でまとめる (呼び出し順を維持する)
        Map<String, List<String>> callsByClass = new LinkedHashMap<>();
        collectCalls(method.getStatements(), ownerClass, callsByClass);

        if (callsByClass.isEmpty()) {
            return;
        }

        // callDepth=1 → クラスノードは "**", メソッドノードは "***"
        String classIndent = "*".repeat(callDepth * 2);
        String methodIndent = "*".repeat(callDepth * 2 + 1);

        for (Map.Entry<String, List<String>> e : callsByClass.entrySet()) {
            String calleeClass = e.getKey();
            List<String> methods = e.getValue();
            boolean isProject = bySimpleName.containsKey(calleeClass);

            // クラスノード
            if (isProject) {
                JavaClassInfo calleeCls = bySimpleName.get(calleeClass);
                String tag = calleeCls != null ? kindTag(calleeCls.getKind()) : "";
                sb.append(classIndent).append("[").append(opts.projectColor).append("] ")
                  .append(tag).append(calleeClass).append("\n");
            } else {
                sb.append(classIndent).append(" ").append(calleeClass).append("\n");
            }

            // メソッドノード (クラス配下)
            for (String meth : methods) {
                String key = nodeKey(calleeClass, meth);
                boolean isCycle = path.contains(key);

                sb.append(methodIndent).append(" ").append(meth).append("()");
                if (isCycle) {
                    sb.append(" [↩]");
                }
                sb.append("\n");

                if (!isCycle && isProject && callDepth < opts.maxDepth) {
                    JavaClassInfo calleeCls = bySimpleName.get(calleeClass);
                    JavaMethodInfo calleeMeth = findMethod(calleeCls, meth);
                    if (calleeMeth != null) {
                        path.add(key);
                        buildWbs(sb, calleeMeth, calleeClass, bySimpleName,
                                path, callDepth + 1, opts);
                        path.remove(key);
                    }
                }
            }
        }
    }

    /** {@code stmts} から呼び出しをすべて収集し、クラス名 → メソッド名リストとしてまとめる。 */
    private static void collectCalls(List<JavaMethodInfo.Statement> stmts,
                                      String ownerClass,
                                      Map<String, List<String>> out) {
        for (JavaMethodInfo.Statement stmt : stmts) {
            if (stmt instanceof JavaMethodInfo.Call) {
                JavaMethodInfo.Call call = (JavaMethodInfo.Call) stmt;
                String receiver;
                String resolved = call.getResolvedOwnerFqn();
                if (resolved != null && !resolved.isEmpty()) {
                    // シンボル解決済みなら宣言型の最外殻型名でグルーピング
                    // ("new Action.Builder()" / "getScreenManager()" のノイズを避ける)。
                    receiver = PlantUmlSequenceDiagram.outerSimpleName(resolved);
                } else {
                    receiver = call.getReceiver();
                    if (receiver == null || receiver.isEmpty()) {
                        receiver = ownerClass;
                    }
                }
                String meth = call.getMethodName();
                if (meth == null || meth.isEmpty()) {
                    continue;
                }
                List<String> list = out.computeIfAbsent(receiver, k -> new ArrayList<>());
                if (!list.contains(meth)) {
                    list.add(meth);
                }
                // インラインラムダ/匿名クラス内の呼び出しも追跡
                for (JavaMethodInfo inline : call.getInlineMethods()) {
                    if (inline.getStatements() != null) {
                        collectCalls(inline.getStatements(), ownerClass, out);
                    }
                }
            } else if (stmt instanceof JavaMethodInfo.Block) {
                JavaMethodInfo.Block block = (JavaMethodInfo.Block) stmt;
                for (JavaMethodInfo.Branch branch : block.getBranches()) {
                    collectCalls(branch.getBody(), ownerClass, out);
                }
            }
        }
    }

    private static JavaMethodInfo findMethod(JavaClassInfo cls, String name) {
        if (cls.getMethods() == null) {
            return null;
        }
        for (JavaMethodInfo m : cls.getMethods()) {
            if (name.equals(m.getName()) && !m.isAbstract()) {
                return m;
            }
        }
        return null;
    }

    private static String nodeKey(String cls, String method) {
        return cls + "." + method;
    }

    private static String kindTag(JavaClassInfo.Kind kind) {
        if (kind == JavaClassInfo.Kind.INTERFACE || kind == JavaClassInfo.Kind.AIDL_INTERFACE) {
            return "[I] ";
        }
        return "[C] ";
    }

    private static Map<String, JavaClassInfo> buildSimpleNameIndex(List<JavaClassInfo> classes) {
        Map<String, JavaClassInfo> map = new LinkedHashMap<>();
        if (classes == null) {
            return map;
        }
        for (JavaClassInfo c : classes) {
            if (c.getSimpleName() != null) {
                map.put(c.getSimpleName(), c);
            }
        }
        return map;
    }
}
