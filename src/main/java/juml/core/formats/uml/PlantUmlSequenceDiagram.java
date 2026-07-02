// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * クラス情報からメソッド呼び出しを抽出して PlantUML 形式のシーケンス図を生成する。
 *
 * <p>特定メソッド (entryClass + entryMethod) を起点に、その本体で行われる
 * 呼び出しを並べる。呼び出し先メソッドが入力に含まれているクラスのものなら、
 * 設定上限 ({@link Options#maxDepth}) まで再帰的に展開する。
 * 制御構造 ({@code if/while/for/do/switch/try/synchronized}) は PlantUML の
 * {@code alt/opt/loop/group/critical} に変換する。</p>
 */
public final class PlantUmlSequenceDiagram {

    /** Consumer/Predicate/Function 等の汎用 SAM 名。参加者名の決定で calling method 名を優先する。 */
    private static final Set<String> GENERIC_SAM_NAMES = new HashSet<>(Arrays.asList(
            "accept", "test", "apply", "get",
            "getAsInt", "getAsLong", "getAsDouble", "<inline>"));

    /**
     * Stream 中間/終端操作のうち、コールバックを持たない場合にシーケンス図から除外するメソッド名。
     * ラムダ引数がある場合は除外しない。
     */
    private static final Set<String> STREAM_OPS = new HashSet<>(Arrays.asList(
            "stream", "parallelStream",
            "filter", "map", "flatMap",
            "mapToInt", "mapToLong", "mapToDouble", "mapToObj",
            "flatMapToInt", "flatMapToLong", "flatMapToDouble",
            "sorted", "distinct", "limit", "skip", "peek",
            "boxed", "sequential", "parallel", "unordered",
            "collect", "toList", "toArray",
            "count", "findFirst", "findAny",
            "anyMatch", "allMatch", "noneMatch",
            "min", "max", "sum", "average", "reduce", "summaryStatistics"));

    /** 繰り返し意味論を持つ操作名。コールバック展開を {@code loop} ブロックで囲む。 */
    private static final Set<String> FOR_EACH_OPS = new HashSet<>(Arrays.asList(
            "forEach", "forEachOrdered"));

    /** コメントを表示する位置。 */
    public enum CommentPlacement {
        /** 各メソッド呼び出しの直下に note を出す (デフォルト)。 */
        AT_CALL_SITE,
        /** participant 宣言の直後に participant ごとに集約 note を出す (旧動作)。 */
        PARTICIPANT_TOP
    }

    /** 出力オプション。 */
    public static class Options {
        /** ダイアグラムのタイトル。 */
        public String title;
        /** 起点メソッドを呼び出す仮想の participant 名。 */
        public String callerName = "Caller";
        /** receiver が空 (this 呼び出し) の場合に使用する自クラス名のサフィックス。 */
        public boolean inlineSelfCalls = true;
        /** 凡例ブロックを末尾に追加する。 */
        public boolean includeLegend = true;
        /**
         * 再帰展開の最大深さ。1 = 起点メソッドのみ、2 以上で呼び出し先の本体も展開。
         * 0 以下なら制限なし (ただしサイクル検出で停止する)。デフォルト 5。
         */
        public int maxDepth = 5;
        /**
         * プロジェクト内で解析済みのクラス (= 入力 {@code classes} に含まれるクラス)
         * に該当する participant を色付けする。外部ライブラリやシステムクラス
         * との視認性を高めるために使用する。デフォルト true。
         */
        public boolean highlightProjectClasses = true;
        /**
         * プロジェクト内クラスの participant 背景色 (PlantUML 形式)。
         * 例: {@code "#LightSkyBlue"}, {@code "#FFE4B5"}, {@code "#ADD8E6"}。
         * {@link #highlightProjectClasses} が true のときのみ使用される。
         * null または空文字を指定すると色付けを行わない。
         */
        public String projectClassColor = "#LightSkyBlue";
        /**
         * クラス/メソッドの JavaDoc・直前コメントを {@code note} として出力する。
         * 位置は {@link #commentPlacement} で制御する。
         */
        public boolean showComments = true;
        /**
         * コメントの表示位置。
         * {@link CommentPlacement#AT_CALL_SITE}: 各呼び出しの直下に出す (デフォルト)。
         * {@link CommentPlacement#PARTICIPANT_TOP}: participant 宣言直後に集約。
         */
        public CommentPlacement commentPlacement = CommentPlacement.AT_CALL_SITE;
        /** コメント表示スタイル (クラス図と共通)。INLINE は 1 行 note、NOTE は複数行ブロック。 */
        public PlantUmlClassDiagram.CommentStyle commentStyle =
                PlantUmlClassDiagram.CommentStyle.INLINE;
        /** INLINE 時の 1 行最大文字数。NOTE 時の本体内コメント 1 行制限にも使用。0 以下で無制限 (既定)。 */
        public int commentMaxLength = 0;
        /**
         * コメント文字列の色 (PlantUML の {@code <color:#RRGGBB>} 値)。
         * INLINE 表示時はテキストを {@code <color:...>...</color>} で囲み、
         * NOTE 表示時は {@code skinparam noteBorderColor / noteFontColor} に適用する。
         * null または空文字で色付け無効。
         */
        public String commentColor = "#008800";
        /** NOTE モードでメソッド本体内のコメントも note 内に列挙する。 */
        public boolean showMethodBodyComments = true;
        /**
         * 依存 JAR/AAR のクラス解決インデックス。{@code mService.foo()} のような
         * 外部クラス参照を participant として描画するために参照する。null 可。
         */
        public DependencyJarIndex dependencyIndex;
        /** 外部 JAR 由来 participant の装飾を有効にする (`<<external>>` ステレオタイプ)。 */
        public boolean showExternalParticipants = true;
        /** 解決できなかった依存先 participant の装飾を有効にする (`<<missing>>` + 警告色)。 */
        public boolean showMissingParticipants = true;
        /** 解決できなかった participant に使う背景色。 */
        public String missingParticipantColor = "#LightYellow";
        /** メソッド引数に渡されたコールバック/リスナー ({@code <<inline>>}) の背景色。null/空文字で色なし。 */
        public String inlineParticipantColor = "#FFE4E1";
        /**
         * メソッド呼び出しラベルにクラス名を付けるか。
         * true (デフォルト) なら {@code "A -> B: B.method()"} の形で出力する。
         * false なら旧動作の {@code "A -> B: method()"}。
         */
        public boolean qualifyMethodNames = true;
        /**
         * シーケンス図から除外する participant 名の集合。
         * 含まれる participant への呼び出しは {@code A -> B: ...} 行も note も
         * 出力されず、ボディも再帰展開されない。
         * null/空なら全 participant を表示する (デフォルト)。
         */
        public Set<String> hiddenParticipants;
    }

    /**
     * クラス・メソッドオブジェクトを直接指定して 1 本のシーケンス図を生成する。
     *
     * <p>オーバーロードが複数ある場合など、{@link JavaMethodInfo} オブジェクト自体を
     * 起点として渡したいときに使う。{@link #generate(List, String, String, Options)} の
     * 呼び出し側が既に目的のメソッドを特定している場合に名前のみ検索を回避できる。</p>
     */
    public static String generate(List<JavaClassInfo> classes,
                                  JavaClassInfo cls,
                                  JavaMethodInfo method,
                                  Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        if (cls == null || method == null) {
            throw new IllegalArgumentException("cls/method is null");
        }
        Options o = opts != null ? opts : new Options();
        return generateBody(classes, cls, method, o);
    }

    /** クラス・メソッドを指定して 1 本のシーケンス図を生成する。 */
    public static String generate(List<JavaClassInfo> classes,
                                  String entryClass,
                                  String entryMethod,
                                  Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        if (entryClass == null || entryMethod == null) {
            throw new IllegalArgumentException("entryClass/entryMethod is null");
        }
        Options o = opts != null ? opts : new Options();
        JavaClassInfo cls = findClass(classes, entryClass);
        if (cls == null) {
            return emptyDiagram(o, "Class not found: " + entryClass);
        }
        JavaMethodInfo method = findMethod(cls, entryMethod);
        if (method == null) {
            return emptyDiagram(o, "Method not found: " + entryClass + "." + entryMethod);
        }
        return generateBody(classes, cls, method, o);
    }

    /**
     * {@link JavaClassInfo} と {@link JavaMethodInfo} を直接受け取り、シーケンス図本体を生成する。
     * {@link #generate(List, String, String, Options)} と
     * {@link #generate(List, JavaClassInfo, JavaMethodInfo, Options)} の共通実装。
     */
    private static String generateBody(List<JavaClassInfo> classes,
                                       JavaClassInfo cls,
                                       JavaMethodInfo method,
                                       Options o) {
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        } else {
            out.append("title ").append(cls.getSimpleName()).append('.')
                    .append(method.getName()).append('\n');
        }
        // NOTE モードでコメント色が指定されていれば、note の枠線・文字色を skinparam で設定
        if (o.showComments
                && o.commentStyle == PlantUmlClassDiagram.CommentStyle.NOTE
                && o.commentColor != null
                && !o.commentColor.isEmpty()) {
            out.append("skinparam noteBorderColor ").append(o.commentColor).append('\n');
            out.append("skinparam noteFontColor ").append(o.commentColor).append('\n');
        }
        Set<String> participants = new LinkedHashSet<>();
        Set<String> inlineParticipants = new LinkedHashSet<>();
        participants.add(o.callerName);
        // 起点クラスが除外指定されていれば、空のシーケンスとして扱う
        boolean entryHidden = isHidden(o, cls.getSimpleName());
        if (!entryHidden) {
            participants.add(cls.getSimpleName());
        }
        // participant ごとに「シーケンス内で登場したメソッド名」を順序付きで記録する。
        // 冒頭の note 集約 (emitCommentNotes) で JavaDoc を表示するメソッドを決めるのに使う。
        Map<String, LinkedHashSet<String>> participantMethods = new LinkedHashMap<>();
        if (!entryHidden) {
            participantMethods.computeIfAbsent(cls.getSimpleName(),
                    k -> new LinkedHashSet<>()).add(method.getName());
        }

        StringBuilder body = new StringBuilder();
        if (!entryHidden) {
            body.append(idRef(o.callerName)).append(" -> ").append(idRef(cls.getSimpleName()))
                    .append(": ").append(PlantUmlCommentFormatter.escapeLabel(formatCallLabel(cls.getSimpleName(),
                            method.getName(), o))).append('\n');
            SeqEmitters.emitCallSiteComment(body, "", cls.getSimpleName(), method, o);
            body.append("activate ").append(idRef(cls.getSimpleName())).append('\n');

            Set<String> stack = new HashSet<>();
            stack.add(cls.getSimpleName() + "." + method.getName());
            walkStatements(method.getStatements(), cls, 1, "", new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, o));
            stack.remove(cls.getSimpleName() + "." + method.getName());

            body.append("deactivate ").append(idRef(cls.getSimpleName())).append('\n');
        }

        // walk 完了後に、解析対象外の participant を依存 JAR で解決 (EXTERNAL_JAR)
        // または missing 判定する。これにより emitCall 系の再帰呼び出しに
        // origin マップを引き回す必要がなくなる。
        Map<String, JavaClassInfo.Origin> participantOrigins
                = resolveParticipantOrigins(participants, cls, classes, o);

        // participant 宣言を先に書く。プロジェクト内で解析済みのクラスは色付けする。
        boolean colorize = o.highlightProjectClasses
                && o.projectClassColor != null
                && !o.projectClassColor.isEmpty();
        for (String p : participants) {
            out.append("participant ").append(quote(p));
            JavaClassInfo.Origin origin = participantOrigins.get(p);
            if (origin == JavaClassInfo.Origin.EXTERNAL_JAR && o.showExternalParticipants) {
                out.append(" <<external>>");
            } else if (origin == JavaClassInfo.Origin.MISSING_JAR && o.showMissingParticipants) {
                out.append(" <<missing>>");
                if (o.missingParticipantColor != null && !o.missingParticipantColor.isEmpty()) {
                    out.append(' ').append(o.missingParticipantColor);
                }
            } else if (inlineParticipants.contains(p)) {
                out.append(" <<inline>>");
                if (o.inlineParticipantColor != null && !o.inlineParticipantColor.isEmpty()) {
                    out.append(' ').append(o.inlineParticipantColor);
                }
            } else if (colorize && findClass(classes, p) != null) {
                out.append(' ').append(o.projectClassColor);
            }
            // AIDL 生成 stub を継承する binder service 実装は、`<<binder>>`
            // ステレオタイプを付与して participant 上で IPC 境界を示す。
            // 既存色付け・external/missing と独立に追加する。
            JavaClassInfo pCls = findClass(classes, p);
            if (pCls != null && AaosPattern.isAidlBinderImpl(pCls)) {
                out.append(" <<binder>>");
            }
            out.append('\n');
        }
        // PARTICIPANT_TOP モードのときだけ、participant 宣言の直後に集約 note を発行する。
        // AT_CALL_SITE (デフォルト) では emitCall 側で各呼び出しの直下に note を出す。
        if (o.showComments && o.commentPlacement == CommentPlacement.PARTICIPANT_TOP) {
            PlantUmlSequenceComments.emit(out, o, participants, participantMethods, classes);
        }
        out.append(body);
        if (o.includeLegend) {
            emitLegend(out, participants.size(), colorize, o.projectClassColor,
                    !inlineParticipants.isEmpty());
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * 指定された entry を起点にした場合に登場し得る participant 名を列挙する。
     * フィルタダイアログ等で利用するため、隠す側 ({@link Options#hiddenParticipants})
     * の設定は無視して、本来出るはずだった全 participant を返す。
     *
     * @return 出現順 ({@code Caller}, 起点クラス, 以降は呼び出し順) を保持した集合。
     *         entryClass / entryMethod が見つからない場合は空。
     */
    public static Set<String> collectParticipants(List<JavaClassInfo> classes,
                                                   String entryClass,
                                                   String entryMethod,
                                                   Options opts) {
        Set<String> result = new LinkedHashSet<>();
        if (classes == null || entryClass == null || entryMethod == null) {
            return result;
        }
        JavaClassInfo cls = findClass(classes, entryClass);
        if (cls == null) {
            return result;
        }
        JavaMethodInfo method = findMethod(cls, entryMethod);
        if (method == null) {
            return result;
        }
        // フィルタを無視した「全体像」用の Options を組み立てる
        Options o = opts != null ? opts : new Options();
        Options scanOpts = new Options();
        scanOpts.callerName = o.callerName;
        scanOpts.inlineSelfCalls = o.inlineSelfCalls;
        scanOpts.maxDepth = o.maxDepth;
        scanOpts.qualifyMethodNames = o.qualifyMethodNames;
        scanOpts.showComments = false;
        scanOpts.includeLegend = false;
        scanOpts.hiddenParticipants = null;
        result.add(scanOpts.callerName);
        result.add(cls.getSimpleName());
        Map<String, LinkedHashSet<String>> participantMethods = new LinkedHashMap<>();
        participantMethods.computeIfAbsent(cls.getSimpleName(),
                k -> new LinkedHashSet<>()).add(method.getName());
        Set<String> stack = new HashSet<>();
        stack.add(cls.getSimpleName() + "." + method.getName());
        // body は捨てる。participants だけ取り出す。
        StringBuilder discard = new StringBuilder();
        walkStatements(method.getStatements(), cls, 1, "", new SeqRender(classes, result, new HashSet<>(), participantMethods, discard, stack, scanOpts));
        return result;
    }

    /** すべてのメソッドに対してシーケンス図を順に生成 (簡易プロジェクトサマリ用)。 */
    public static String generateAll(List<JavaClassInfo> classes, Options opts) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (JavaClassInfo c : classes) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.getStatements().isEmpty()) {
                    continue;
                }
                if (!first) {
                    out.append("newpage\n");
                }
                first = false;
                // JavaMethodInfo オブジェクトを直接渡してオーバーロード解決漏れを防ぐ
                String single = generate(classes, c, m, opts);
                // @startuml/@enduml を 1 ファイルに統合する
                // タイトル等のオプション付き @startuml (例: @startuml MyDiagram) にも対応するため
                // 行末まで含む正規表現で除去する
                single = single.replaceFirst("(?m)^@startuml.*\n", "");
                single = single.replaceFirst("(?m)@enduml\\s*$", "");
                if (out.length() == 0) {
                    out.append("@startuml\n");
                }
                out.append(single);
            }
        }
        if (out.length() == 0) {
            return emptyDiagram(opts != null ? opts : new Options(), "No method calls found");
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * シーケンス図に出現しうる Class.method 候補を列挙する。
     * 呼び出しを 1 件以上含むメソッドを優先 (count 降順、クラス名昇順)。
     */
    public static List<Candidate> listCandidates(List<JavaClassInfo> classes) {
        List<Candidate> list = new ArrayList<>();
        if (classes == null) {
            return list;
        }
        for (JavaClassInfo c : classes) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.isAbstract()) {
                    continue;
                }
                list.add(new Candidate(c.getSimpleName(), m.getName(),
                        m.getCalls().size(), m.getVisibility()));
            }
        }
        list.sort((a, b) -> {
            if (a.callCount != b.callCount) {
                return Integer.compare(b.callCount, a.callCount);
            }
            int n = a.className.compareTo(b.className);
            if (n != 0) {
                return n;
            }
            return a.methodName.compareTo(b.methodName);
        });
        return list;
    }

    /** {@link #listCandidates(List)} の戻り値要素。 */
    public static class Candidate {
        public final String className;
        public final String methodName;
        public final int callCount;
        public final Visibility visibility;

        public Candidate(String className, String methodName, int callCount,
                          Visibility visibility) {
            this.className = className;
            this.methodName = methodName;
            this.callCount = callCount;
            this.visibility = visibility;
        }

        /** {@code "Class.method"} の形式。 */
        public String getEntry() {
            return className + "." + methodName;
        }

        @Override
        public String toString() {
            return getEntry();
        }
    }

    /** 文ツリーを順に走査して、呼び出しと制御ブロックを emit する。 */
    static void walkStatements(List<JavaMethodInfo.Statement> stmts, JavaClassInfo currentClass, int depth, String indent, SeqRender r) {
        StringBuilder body = r.body;
        for (JavaMethodInfo.Statement s : stmts) {
            if (s instanceof JavaMethodInfo.Call) {
                emitCall((JavaMethodInfo.Call) s, currentClass, depth, indent, r);
            } else if (s instanceof JavaMethodInfo.Return) {
                emitReturnStatement((JavaMethodInfo.Return) s,
                        currentClass.getSimpleName(), body, indent, r.opts.commentMaxLength);
            } else if (s instanceof JavaMethodInfo.Throw) {
                emitThrowStatement((JavaMethodInfo.Throw) s,
                        currentClass.getSimpleName(), body, indent, r.opts.commentMaxLength);
            } else if (s instanceof JavaMethodInfo.Block) {
                SeqEmitters.emitBlock((JavaMethodInfo.Block) s, currentClass, depth, indent, r);
            } else if (s instanceof JavaMethodInfo.LocalVar) {
                // ローカル変数のラムダ/匿名本体 (Runnable x = () -> svc.work();) を展開し取りこぼさない。
                // 本体は this をキャプチャするため currentClass を保ったまま walk する。
                for (JavaMethodInfo inline : ((JavaMethodInfo.LocalVar) s).getInlineMethods()) {
                    walkStatements(inline.getStatements(), currentClass, depth, indent, r);
                }
            } else if (s instanceof JavaMethodInfo.InlineComment) {
                // インラインコメント (メソッド本体内の // / /* */ コメント) を note として出力する。
                // AT_CALL_SITE モードで呼び出し順序内にコメントの位置を保持できるようにする。
                emitInlineComment((JavaMethodInfo.InlineComment) s, currentClass.getSimpleName(), body, indent, r.opts);
            }
        }
    }

    /** インラインコメントを note over として emit する。 */
    private static void emitInlineComment(JavaMethodInfo.InlineComment comment,
                                           String participant,
                                           StringBuilder body,
                                           String indent,
                                           Options opts) {
        String text = comment.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        String oneLine = PlantUmlCommentFormatter.escapeLabel(text, opts.commentMaxLength);
        if (oneLine.isEmpty()) {
            return;
        }
        body.append(indent).append("note over ").append(quote(participant))
                .append(" : ").append(oneLine).append('\n');
    }

    private static void emitReturnStatement(JavaMethodInfo.Return r,
                                             String participant,
                                             StringBuilder body,
                                             String indent, int maxLen) {
        String expr = r.getExpression();
        String text = (expr == null || expr.isEmpty()) ? "return" : "return " + expr;
        body.append(indent).append("note over ").append(quote(participant))
                .append(" : ").append(PlantUmlCommentFormatter.escapeLabel(text, maxLen)).append('\n');
    }

    private static void emitThrowStatement(JavaMethodInfo.Throw t,
                                            String participant,
                                            StringBuilder body,
                                            String indent, int maxLen) {
        String expr = t.getExpression();
        String text = (expr == null || expr.isEmpty()) ? "throw" : "throw " + expr;
        body.append(indent).append("note over ").append(quote(participant))
                .append(" : ").append(PlantUmlCommentFormatter.escapeLabel(text, maxLen)).append('\n');
    }

    private static void emitCall(JavaMethodInfo.Call call, JavaClassInfo currentClass, int depth, String indent, SeqRender r) {
            List<JavaClassInfo> classes = r.classes;
            Set<String> participants = r.participants;
            Set<String> inlineParticipants = r.inlineParticipants;
            Map<String, LinkedHashSet<String>> participantMethods = r.participantMethods;
            StringBuilder body = r.body;
            Set<String> stack = r.stack;
            Options opts = r.opts;

        String target = resolveTarget(currentClass, call, opts);
        if (target.equals(currentClass.getSimpleName()) && !opts.inlineSelfCalls) {
            return;
        }
        // 除外指定された participant への呼び出しは矢印も note も出力しない
        if (isHidden(opts, target)) {
            return;
        }
        // Stream 中間/終端操作でコールバックがない場合はノイズを抑制
        if (STREAM_OPS.contains(call.getMethodName()) && call.getInlineMethods().isEmpty()) {
            return;
        }
        participants.add(target);
        participantMethods.computeIfAbsent(target, k -> new LinkedHashSet<>())
                .add(call.getMethodName());
        body.append(indent).append(idRef(currentClass.getSimpleName()))
                .append(" -> ").append(idRef(target))
                .append(": ").append(PlantUmlCommentFormatter.escapeLabel(formatCallLabel(target, call.getMethodName(),
                        call.getFirstArgLabel(), opts)))
                .append('\n');

        // 解析済みクラスに該当する呼び出し先メソッドを引いておく (note と再帰展開で共有)
        JavaClassInfo nextCls = findClass(classes, target);
        JavaMethodInfo nextMethod = nextCls != null
                ? findMethod(nextCls, call.getMethodName()) : null;
        // AT_CALL_SITE モードのとき、呼び出しの直下に呼ばれるメソッドのコメント note を出す
        SeqEmitters.emitCallSiteComment(body, indent, target, nextMethod, opts);

        // 再帰展開: 呼び出し先メソッドが解析済みクラスにあれば深掘りする
        boolean canRecurse = opts.maxDepth <= 0 || depth < opts.maxDepth;
        if (!canRecurse) {
            return;
        }
        // Case 1/1b: コールバック (ラムダ/匿名クラス) があれば、呼び出し先が解析済み
        // プロジェクトクラスのメソッドに解決できても、その本体再帰ではなく
        // コールバック本体を展開する。例: repo.findAll(item -> process(item)) で
        // Repository.findAll が解決済みでも、ラムダ本体の process() を取りこぼさない。
        if (!call.getInlineMethods().isEmpty()) {
            // forEach/forEachOrdered は繰り返し意味論を持つので loop ブロックで囲む
            boolean isLoop = FOR_EACH_OPS.contains(call.getMethodName());
            if (isLoop) {
                body.append(indent).append("loop ")
                        .append(PlantUmlCommentFormatter.escapeLabel(call.getMethodName()))
                        .append('\n');
            }
            for (JavaMethodInfo inline : call.getInlineMethods()) {
                // 参加者名: 定義元クラス$コールバック識別子
                // SAM 名が汎用的 (accept/test/apply/get 等) なら calling method 名を使う
                String inlineLabel = GENERIC_SAM_NAMES.contains(inline.getName())
                        ? call.getMethodName() : inline.getName();
                String inlineName = currentClass.getSimpleName() + "$" + inlineLabel;
                inlineParticipants.add(inlineName);
                participants.add(inlineName);
                participantMethods.computeIfAbsent(inlineName, k -> new LinkedHashSet<>())
                        .add(inlineLabel);
                String inlineKey = inlineName + "." + inlineLabel;
                if (stack.contains(inlineKey)) {
                    body.append(indent).append("note over ").append(quote(inlineName))
                            .append(" : recursive call (")
                            .append(PlantUmlCommentFormatter.escapeLabel(inlineLabel))
                            .append(")\n");
                    continue;
                }
                body.append(indent).append(idRef(target)).append(" -> ").append(quote(inlineName))
                        .append(": ").append(PlantUmlCommentFormatter.escapeLabel(inlineLabel)).append("()\n");
                if (inline.getStatements().isEmpty()) {
                    continue;
                }
                body.append(indent).append("activate ").append(quote(inlineName)).append('\n');
                stack.add(inlineKey);
                // inline body 内の `this.foo()` を定義元クラスに解決させるため currentClass を保つ
                walkStatements(inline.getStatements(), currentClass, depth + 1, isLoop ? indent + "    " : indent + "  ", new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
                stack.remove(inlineKey);
                body.append(indent).append("deactivate ").append(quote(inlineName)).append('\n');
            }
            if (isLoop) {
                body.append(indent).append("end\n");
            }
            return;
        }
        if (nextCls == null) {
            // Case 2: フィールド初期化子 (匿名クラス / ラムダ) で定義された inline メソッド
            JavaMethodInfo inline = findInlineMethod(currentClass, call);
            if (inline == null || inline.getStatements().isEmpty()) {
                return;
            }
            String inlineKey = target + "." + inline.getName();
            if (stack.contains(inlineKey)) {
                body.append(indent).append("note over ").append(quote(target))
                        .append(" : recursive call (")
                        .append(PlantUmlCommentFormatter.escapeLabel(inline.getName()))
                        .append(")\n");
                return;
            }
            body.append(indent).append("activate ").append(idRef(target)).append('\n');
            stack.add(inlineKey);
            // inline body 内の `this.foo()` を呼び出し元クラスに解決させるため currentClass を保つ
            walkStatements(inline.getStatements(), currentClass, depth + 1, indent, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
            stack.remove(inlineKey);
            body.append(indent).append("deactivate ").append(idRef(target)).append('\n');
            return;
        }
        if (nextMethod == null || nextMethod.getStatements().isEmpty()) {
            return;
        }
        String key = nextCls.getSimpleName() + "." + nextMethod.getName();
        if (stack.contains(key)) {
            // サイクル検出: 既にスタック上にいるので展開しない
            body.append(indent).append("note over ").append(quote(target))
                    .append(" : recursive call (")
                    .append(PlantUmlCommentFormatter.escapeLabel(nextMethod.getName()))
                    .append(")\n");
            return;
        }
        body.append(indent).append("activate ").append(idRef(target)).append('\n');
        stack.add(key);
        walkStatements(nextMethod.getStatements(), nextCls, depth + 1, indent, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
        stack.remove(key);
        body.append(indent).append("deactivate ").append(idRef(target)).append('\n');
    }

    /**
     * 解析対象外の participant について、依存 JAR/AAR で解決できれば EXTERNAL_JAR、
     * 「依存に宣言されているのに見つからない」場合は MISSING_JAR としてマップに記録する。
     * 解析済みプロジェクトクラスや virtual な Caller は対象外。
     */
    private static Map<String, JavaClassInfo.Origin> resolveParticipantOrigins(
            Set<String> participants, JavaClassInfo entry, List<JavaClassInfo> classes,
            Options o) {
        Map<String, JavaClassInfo.Origin> out = new LinkedHashMap<>();
        if (o.dependencyIndex == null) {
            return out;
        }
        for (String p : participants) {
            if (o.callerName.equals(p)) {
                continue;
            }
            if (findClass(classes, p) != null) {
                continue;
            }
            Optional<JavaClassInfo> resolved = o.dependencyIndex.resolve(p);
            if (resolved.isPresent()) {
                out.put(p, JavaClassInfo.Origin.EXTERNAL_JAR);
            } else if (o.dependencyIndex.isDeclaredButMissing(p)) {
                out.put(p, JavaClassInfo.Origin.MISSING_JAR);
            }
        }
        return out;
    }

    /**
     * 呼び出し receiver が {@code currentClass} のフィールドであり、そのフィールド初期化子
     * (匿名クラス / ラムダ) で定義された inline メソッドの中から、呼び出されたメソッド名に
     * 合致するものを返す。見つからなければ null。SAM 名が解決できなかったため
     * {@code <inline>} で保持されているケースは receiver さえ一致すれば fall through する。
     */
    private static JavaMethodInfo findInlineMethod(JavaClassInfo currentClass,
                                                    JavaMethodInfo.Call call) {
        if (currentClass == null || call == null) {
            return null;
        }
        String receiver = call.getReceiver();
        if (receiver == null || receiver.isEmpty()) {
            return null;
        }
        String head = receiver;
        int dot = head.indexOf('.');
        if (dot >= 0) {
            head = head.substring(0, dot);
        }
        for (JavaFieldInfo f : currentClass.getFields()) {
            if (!head.equals(f.getName())) {
                continue;
            }
            for (JavaMethodInfo m : f.getInlineMethods()) {
                if (call.getMethodName().equals(m.getName())
                        || "<inline>".equals(m.getName())) {
                    return m;
                }
            }
        }
        return null;
    }

    /** call ラベルの整形。{@link Options#qualifyMethodNames} 次第で {@code Class.method()} 形に。 */
    private static String formatCallLabel(String target, String methodName, Options o) {
        return formatCallLabel(target, methodName, null, o);
    }

    /**
     * 引数ラベル付きの call ラベル整形。{@code firstArgLabel} に値があれば
     * {@code method(<arg>)} 形式で添える (例:
     * {@code getProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET)})。
     * null/空のときは引数なし表記 ({@code method()})。
     */
    private static String formatCallLabel(String target, String methodName,
                                            String firstArgLabel, Options o) {
        String parens = firstArgLabel != null && !firstArgLabel.isEmpty()
                ? "(" + firstArgLabel + ")"
                : "()";
        if (o.qualifyMethodNames && target != null && !target.isEmpty()) {
            return target + "." + methodName + parens;
        }
        return methodName + parens;
    }

    /** {@code o.hiddenParticipants} に含まれているかを安全に判定する。 */
    private static boolean isHidden(Options o, String participant) {
        return o != null && o.hiddenParticipants != null
                && participant != null
                && o.hiddenParticipants.contains(participant);
    }

    /**
     * AT_CALL_SITE モードのとき、呼び出し対象メソッドの JavaDoc コメントを
     * 呼び出し行の直下に note として出力する。method が解析対象外 (null) や
     * コメントが空のときは何も出さない。
     */


    private static void emitLegend(StringBuilder out, int participantCount,
                                    boolean colorize, String projectClassColor,
                                    boolean hasInlineParticipants) {
        out.append("legend top left\n");
        out.append("== シーケンス図 ==\n");
        out.append("participant         関与クラス/オブジェクト\n");
        out.append("X →→ Y : Z.m()      X から Y への同期メッセージ (呼び出し先 Class.method)\n");
        out.append("note right of Y      呼び出し直下の呼び出し先メソッドコメント\n");
        out.append("activate Y          Y のアクティベーション開始\n");
        out.append("deactivate Y        Y のアクティベーション終了\n");
        out.append("alt/opt/loop        if-else / 単一分岐 / ループ (while/for/do)\n");
        out.append("group/critical      try-catch-finally / synchronized\n");
        if (colorize) {
            out.append("<back:").append(projectClassColor).append(">  </back>")
                    .append("              プロジェクト内で解析済みのクラス (独自クラス)\n");
        }
        out.append("<<external>>    依存 JAR/AAR で解決できた外部ライブラリのクラス\n");
        out.append("<<missing>>     依存宣言はあるが JAR/AAR が見つからないクラス (要確認)\n");
        if (hasInlineParticipants) {
            out.append("<<inline>>      引数に渡されたコールバック/リスナー (定義元クラス$メソッド名)\n");
        }
        if (participantCount > 0) {
            out.append("Caller              仮想の呼び出し元 (オプションで変更可)\n");
        }
        out.append("endlegend\n");
    }

    private static String resolveTarget(JavaClassInfo cls,
                                        JavaMethodInfo.Call call,
                                        Options o) {
        // シンボル解決済みなら宣言型の最外殻型名を participant にする
        // (チェーン/new 起点でも "new Action"/"getScreenManager()" のようなノイズを避け、
        //  実際の宣言型 "Action"/"ScreenManager" に解決できる)。
        String resolved = call.getResolvedOwnerFqn();
        if (resolved != null && !resolved.isEmpty()) {
            return outerSimpleName(resolved);
        }
        String receiver = call.getReceiver();
        if (receiver == null || receiver.isEmpty() || "this".equals(receiver)) {
            return cls.getSimpleName();
        }
        // super.foo() は participant "super" ではなく親クラスの単純名に解決する。
        // 親クラスが不明なら自クラス扱い (this 相当) にフォールバックする。
        if ("super".equals(receiver)) {
            String sc = cls.getSuperClass();
            return (sc == null || sc.isEmpty())
                    ? cls.getSimpleName() : reduceTypeToSimpleName(sc);
        }
        // フィールドへのアクセスなら、フィールド型を participant とする
        // 例: receiver = "mAudioService" → field "mAudioService: IAudioService" → "IAudioService"
        String head = receiver;
        int dot = head.indexOf('.');
        if (dot >= 0) {
            head = head.substring(0, dot);
        }
        for (JavaFieldInfo f : cls.getFields()) {
            if (head.equals(f.getName()) && f.getType() != null && !f.getType().isEmpty()) {
                return reduceTypeToSimpleName(f.getType());
            }
        }
        // それ以外は receiver の先頭シンボルをそのまま使用
        return head;
    }

    /** 型表記からジェネリクス・FQN・配列を落として単純名にする ({@code [] → Array})。 */
    private static String reduceTypeToSimpleName(String type) {
        String t = type;
        int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt);
        }
        int last = t.lastIndexOf('.');
        if (last >= 0) {
            t = t.substring(last + 1);
        }
        return t.replace("[]", "Array").trim();
    }

    /**
     * FQN から最外殻の型単純名を取り出す (パッケージ後の最初の大文字始まりセグメント)。
     * 例: {@code androidx.car.app.model.Action.Builder} → {@code Action}、
     * {@code com.x.ScreenManager} → {@code ScreenManager}。
     */
    static String outerSimpleName(String fqn) {
        String[] segs = fqn.split("\\.");
        for (String s : segs) {
            if (!s.isEmpty() && Character.isUpperCase(s.charAt(0))) {
                return s;
            }
        }
        return segs.length > 0 ? segs[segs.length - 1] : fqn;
    }

    private static JavaClassInfo findClass(List<JavaClassInfo> classes, String name) {
        for (JavaClassInfo c : classes) {
            if (c.getSimpleName().equals(name) || c.getQualifiedName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static JavaMethodInfo findMethod(JavaClassInfo cls, String name) {
        for (JavaMethodInfo m : cls.getMethods()) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    static String quote(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    /** 引用符なしで書ける単純な participant 識別子のパターン。 */
    private static final java.util.regex.Pattern SAFE_PARTICIPANT =
            java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    // participant 参照表記: 単純名は素のまま、空白/$/<>/. 等を含む名前のみ quote() で囲む。
    // 宣言側は常に quote するため、特殊文字名を素で参照すると構文エラーになるのを防ぐ。
    static String idRef(String s) {
        return s != null && SAFE_PARTICIPANT.matcher(s).matches() ? s : quote(s == null ? "" : s);
    }

    private static String emptyDiagram(Options o, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            sb.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        }
        sb.append("participant \"info\"\n");
        sb.append("note over \"info\" : ").append(PlantUmlCommentFormatter.escapeLabel(reason)).append('\n');
        sb.append("@enduml\n");
        return sb.toString();
    }

    private PlantUmlSequenceDiagram() {
    }
}
