// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.android.actions.MarkdownActionReport;
import juml.core.formats.android.actions.UiActionEntry;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 全クラスの関数（メソッド）一覧を、各関数の「利用側（呼び出し元）」と
 * 「実行条件（呼び出しを囲む分岐条件 / リスナーの UI トリガ）」付きで出力する。
 *
 * <p>出力形式は {@link Format} で選択する（Markdown テーブル または CSV）。空欄になりうる箇所は
 * 必ず理由付きの表記（{@link Derivation}）に置き換え、Markdown 末尾に「## 算出ロジック」節として
 * 算出手順と理由凡例を併記する。署名整形は {@link PlantUmlClassDiagram#emitMethod} 相当をプレーン化、
 * 呼び出し元は {@link ReferenceIndex}、実行条件はパース済み statement tree の分岐ラベルから求める。</p>
 */
public final class MethodUsageReport {

    /** 出力形式。 */
    public enum Format {
        /** Markdown テーブル（1メソッド=1行 + UI Actions 表 + 算出ロジック節）。 */
        TABLE,
        /** カンマ区切り（スプレッドシート取込向け、reason 列付き）。 */
        CSV;

        /** 文字列 ("table" / "csv") から形式を解決する。不明・null は TABLE。 */
        public static Format fromString(String s) {
            return s != null && "csv".equalsIgnoreCase(s.trim()) ? CSV : TABLE;
        }

        /**
         * 厳密解決。null / 空 / "table" は TABLE、"csv" は CSV、それ以外 (typo や
         * 未対応の "json" 等) は {@code null} を返す。呼び出し側は null を不正入力として
         * 扱い、黙って TABLE にフォールバックせずエラー終了できる。
         */
        public static Format fromStringStrict(String s) {
            if (s == null) {
                return TABLE;
            }
            String t = s.trim().toLowerCase(java.util.Locale.ROOT);
            if (t.isEmpty() || "table".equals(t)) {
                return TABLE;
            }
            if ("csv".equals(t)) {
                return CSV;
            }
            return null;
        }
    }

    /** 利用側・実行条件をどう導出したか（＝空欄理由）の分類。 */
    private enum Derivation {
        /** リスナー本体。実行条件は UI トリガ。 */
        LISTENER("リスナー本体（UIトリガ）"),
        /** 解析対象コード内に呼び出し箇所が見つからない。 */
        NO_CALLER("呼び出し元なし（外部/起点/未使用/動的呼び出しの可能性）"),
        /** 呼び出し元があり、分岐に囲まれている。 */
        GUARDED("分岐ガードあり"),
        /** 呼び出し元はあるが分岐に囲まれていない。 */
        DIRECT("直接呼び出し（分岐に囲まれていない）"),
        /** 呼び出し元が Kotlin 等で構文木を持たず、分岐条件を算出できない。 */
        UNANALYZED("未算出（Kotlin軽量解析で分岐木なし）");

        final String reason;

        Derivation(String reason) {
            this.reason = reason;
        }
    }

    /** 分岐に囲まれていない（無条件）呼び出しを表すラベル。条件集合内でも併記できる。 */
    private static final String DIRECT_CALL = "(直接呼び出し)";

    private MethodUsageReport() {
    }

    public static String render(List<JavaClassInfo> classes,
                                ReferenceIndex refIndex,
                                List<UiActionEntry> actions) {
        return render(classes, refIndex, actions, Format.TABLE);
    }

    public static String render(List<JavaClassInfo> classes,
                                ReferenceIndex refIndex,
                                List<UiActionEntry> actions,
                                Format format) {
        List<Row> rows = buildRows(classes, refIndex);
        return format == Format.CSV ? renderCsv(rows, actions) : renderTable(rows, actions);
    }

    /** 1メソッド（または1リスナー）分の出力行。形式非依存の中間表現。 */
    private static final class Row {
        final String classFqn;
        final String simpleName;
        final String kind;
        final String signature;
        final String sourceFile;
        final int startLine;
        final boolean listener;
        final List<String> callers;
        final List<String> conditions;
        final Derivation derivation;

        Row(String classFqn, String simpleName, String kind, String signature,
            String sourceFile, int startLine, boolean listener, List<String> callers,
            List<String> conditions, Derivation derivation) {
            this.classFqn = classFqn;
            this.simpleName = simpleName;
            this.kind = kind;
            this.signature = signature;
            this.sourceFile = sourceFile;
            this.startLine = startLine;
            this.listener = listener;
            this.callers = callers;
            this.conditions = conditions;
            this.derivation = derivation;
        }
    }

    private static List<Row> buildRows(List<JavaClassInfo> classes, ReferenceIndex refIndex) {
        Map<String, JavaClassInfo> byQn = new HashMap<>();
        List<JavaClassInfo> sorted = new ArrayList<>();
        for (JavaClassInfo c : classes) {
            if (c.getKind() == JavaClassInfo.Kind.MODULE) {
                continue;
            }
            sorted.add(c);
            if (c.getQualifiedName() != null) {
                byQn.put(c.getQualifiedName(), c);
            }
        }
        sorted.sort(Comparator.comparing(c -> nz(c.getQualifiedName())));
        List<Row> rows = new ArrayList<>();
        for (JavaClassInfo c : sorted) {
            String qn = nz(c.getQualifiedName());
            String simple = nz(c.getSimpleName());
            String kind = String.valueOf(c.getKind());
            String file = nz(c.getSourceFile());
            for (JavaMethodInfo m : c.getMethods()) {
                rows.add(methodRow(qn, simple, kind, file, m, refIndex, byQn));
            }
            for (JavaMethodInfo listener : collectListeners(c)) {
                List<String> trigger = new ArrayList<>();
                trigger.add(triggerOf(listener.getName()));
                rows.add(new Row(qn, simple, kind, signature(listener), file,
                        listener.getStartLine(), true, new ArrayList<>(), trigger,
                        Derivation.LISTENER));
            }
        }
        return rows;
    }

    private static Row methodRow(String qn, String simple, String kind, String file,
                                 JavaMethodInfo m, ReferenceIndex refIndex,
                                 Map<String, JavaClassInfo> byQn) {
        List<ReferenceSite> sites = refIndex == null
                ? new ArrayList<>()
                : refIndex.sitesByMember(ReferenceKey.Kind.METHOD, qn, nz(m.getName()));
        List<String> callers = new ArrayList<>();
        Set<String> conditions = new LinkedHashSet<>();
        boolean hasJava = false;
        boolean hasKotlin = false;
        for (ReferenceSite s : sites) {
            callers.add(callerLabel(s));
            String f = nz(s.getFile()).toLowerCase(Locale.ROOT);
            if (f.endsWith(".java")) {
                hasJava = true;
            } else if (f.endsWith(".kt")) {
                hasKotlin = true;
            }
            JavaClassInfo caller = byQn.get(s.getCallerFqn());
            if (caller != null && !nz(s.getCallerMethod()).isEmpty()) {
                for (JavaMethodInfo cm : caller.getMethods()) {
                    if (s.getCallerMethod().equals(cm.getName())) {
                        collectConditions(cm.getStatements(), new ArrayDeque<>(),
                                nz(m.getName()), conditions);
                    }
                }
            }
        }
        boolean hasGuard = false;
        for (String c : conditions) {
            if (!DIRECT_CALL.equals(c)) {
                hasGuard = true;
                break;
            }
        }
        Derivation d;
        if (sites.isEmpty()) {
            d = Derivation.NO_CALLER;
        } else if (hasGuard) {
            // 一部が分岐内（無条件呼び出しがあれば conditions に併記される）
            d = Derivation.GUARDED;
        } else if (!conditions.isEmpty() || hasJava || !hasKotlin) {
            // 構文木内で無条件にのみ出現、または Java だが呼び出し位置を特定できない（ラムダ内等）
            d = Derivation.DIRECT;
        } else {
            d = Derivation.UNANALYZED;
        }
        return new Row(qn, simple, kind, signature(m), file, m.getStartLine(), false,
                callers, new ArrayList<>(conditions), d);
    }

    private static String renderTable(List<Row> rows, List<UiActionEntry> actions) {
        StringBuilder out = new StringBuilder();
        out.append("# 関数使用マップ (Function usage map)\n\n");
        out.append("| クラス | クラス名 | 種別 | 関数 | ファイル | 行 | 利用側 | 実行条件 |\n");
        out.append("|---|---|---|---|---|---|---|---|\n");
        for (Row r : rows) {
            String sig = r.listener ? "[listener] " + r.signature : r.signature;
            out.append("| `").append(mdInline(r.classFqn)).append("` | `")
                    .append(mdInline(r.simpleName)).append("` | ")
                    .append(mdInline(r.kind)).append(" | `")
                    .append(mdInline(sig)).append("` | ")
                    .append(mdInline(fileLabel(r))).append(" | ")
                    .append(mdInline(lineLabel(r))).append(" | ")
                    .append(callersCell(r)).append(" | ")
                    .append(conditionsCell(r)).append(" |\n");
        }
        out.append('\n');
        if (actions != null && !actions.isEmpty()) {
            out.append("## UI Actions（ボタン押下時のリスナー: XML / Compose / メニュー含む）\n\n");
            out.append(MarkdownActionReport.render(actions));
            out.append('\n');
        }
        appendMethodology(out);
        return out.toString();
    }

    private static String callersCell(Row r) {
        if (r.listener) {
            return "—（リスナー登録）";
        }
        if (r.derivation == Derivation.NO_CALLER) {
            return "(呼び出し元なし: 外部/起点/未使用の可能性)";
        }
        return joinMd(r.callers);
    }

    private static String conditionsCell(Row r) {
        switch (r.derivation) {
            case LISTENER:
            case GUARDED:
                return joinMd(r.conditions);
            case DIRECT:
                return DIRECT_CALL;
            case UNANALYZED:
                return "(未算出: Kotlin等で分岐木なし)";
            default:
                return "(呼び出し元なし)";
        }
    }

    private static String joinMd(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append("<br>");
            }
            sb.append(mdInline(items.get(i)));
        }
        return sb.toString();
    }

    private static String renderCsv(List<Row> rows, List<UiActionEntry> actions) {
        StringBuilder out = new StringBuilder();
        out.append("区分,クラス,クラス名,種別,関数,ファイル,行,利用側,実行条件,理由\n");
        for (Row r : rows) {
            out.append(csv(r.listener ? "listener" : "method")).append(',')
                    .append(csv(r.classFqn)).append(',')
                    .append(csv(r.simpleName)).append(',')
                    .append(csv(r.kind)).append(',')
                    .append(csv(r.signature)).append(',')
                    .append(csv(fileLabel(r))).append(',')
                    .append(csv(lineLabel(r))).append(',')
                    .append(csv(callersText(r))).append(',')
                    .append(csv(conditionsText(r))).append(',')
                    .append(csv(r.derivation.reason)).append('\n');
        }
        if (actions != null) {
            for (UiActionEntry a : actions) {
                String component = a.componentId.isEmpty() ? "(コンポーネント指定なし)" : a.componentId;
                String source = actionSource(a);
                out.append(csv("ui-action")).append(',')
                        .append(csv(component)).append(',')
                        .append(csv("")).append(',')
                        .append(csv(a.actionType.label)).append(',')
                        .append(csv(a.handler)).append(',')
                        .append(csv("")).append(',')
                        .append(csv("")).append(',')
                        .append(csv(source.isEmpty() ? "(場所不明)" : source)).append(',')
                        .append(csv("(UIトリガ)")).append(',')
                        .append(csv("UIアクション（XML/Compose/メニュー検出）")).append('\n');
            }
        }
        return out.toString();
    }

    /** callers 列の本文（プレーン）。空欄になりうる箇所は理由付きで埋める。 */
    private static String callersText(Row r) {
        if (r.listener) {
            return "(リスナー登録)";
        }
        if (r.derivation == Derivation.NO_CALLER) {
            return "(呼び出し元なし: 外部/起点/未使用の可能性)";
        }
        return String.join("; ", r.callers);
    }

    /** conditions 列の本文（プレーン）。空欄になりうる箇所は理由付きで埋める。 */
    private static String conditionsText(Row r) {
        switch (r.derivation) {
            case LISTENER:
            case GUARDED:
                return String.join("; ", r.conditions);
            case DIRECT:
                return DIRECT_CALL;
            case UNANALYZED:
                return "(未算出: Kotlin等で分岐木なし)";
            default:
                return "(呼び出し元なし)";
        }
    }

    /** 算出手順と理由凡例を Markdown で追記する。 */
    private static void appendMethodology(StringBuilder out) {
        out.append("## 算出ロジック (How values are derived)\n\n");
        out.append("- **ファイル / 行 (file / line)**: 各関数の宣言位置を"
                + "ファイル名と開始行に分けて示す（ソースをそのまま開いて該当行へ辿れる）。"
                + "行が取れない場合（Kotlin軽量解析・ラムダ本体等）は行を空欄にする。\n");
        out.append("- **利用側 (callers)**: 逆参照インデックス (ReferenceIndex) で"
                + "「このメソッドを呼んでいる箇所」を収集し、`呼び出し元クラスFQN.メソッド (ファイル名[:行])`"
                + " で列挙する。空欄理由 = 解析対象ソース内に呼び出しが見つからない"
                + "（フレームワーク/ライフサイクル起点・外部公開API・未使用・リフレクション等の動的呼び出し）。\n");
        out.append("- **実行条件 (conditions)**: 各呼び出し元メソッドの構文木 (statement tree) を辿り、"
                + "当該呼び出しを囲む if/else/while/for/switch(case/default)/try(catch/finally) 等の分岐ラベルを"
                + "根→葉で連鎖 (`→`) して記録する。呼び出し元メソッド単位で集約し、複数経路は表では `<br>`・"
                + "CSV では `;` 区切りで併記する。同一メソッドが無条件呼び出しと分岐内呼び出しの両方を持つ場合は、"
                + "`(直接呼び出し)` と分岐条件を併記する。\n");
        out.append("- **リスナー本体 ([listener])**: `setOnXxxListener(...)` 等へ渡したラムダ/匿名本体を"
                + " SAM 解決で抽出する。実行条件は UI トリガ（onClick→クリック 等）。\n");
        out.append("- **UI Actions**: UiActionScanner が XML(`android:onClick`) / Compose(`onClick`)"
                + " / メニューのハンドラを横断検出する。\n");
        out.append("- **既知の制約**: (1) 呼び出し元/実行条件はメソッド名で対応付けるため、"
                + "オーバーロード（同名・異シグネチャ）は呼び出し情報が合算される。"
                + "(2) ラムダ/匿名内部クラスの本体「内側」の分岐条件は辿らない"
                + "（その呼び出しは登録メソッド側の `(直接呼び出し)` として扱う）。"
                + "(3) Kotlin は軽量解析のため分岐条件を算出しない（`(未算出)` と表示）。\n\n");
        out.append("### 理由凡例 (実行条件の表記)\n\n");
        out.append("| 表記 | 意味 |\n");
        out.append("|---|---|\n");
        out.append("| (直接呼び出し) | 呼び出し元はあるが分岐に囲まれていない（無条件で実行）。"
                + "分岐条件と併記される場合は両方の経路があることを示す |\n");
        out.append("| (呼び出し元なし) | 呼び出し箇所が無いため条件算出の対象がない |\n");
        out.append("| (未算出: Kotlin等で分岐木なし) | "
                + "呼び出し元が Kotlin 軽量解析対象で構文木を持たず、分岐条件を判定できない |\n");
        out.append("| if(...) → for(...) 等 | 呼び出しを囲む分岐の入れ子チェーン（ガード条件） |\n");
    }

    private static String actionSource(UiActionEntry a) {
        String name = a.shortFileName();
        if (a.line > 0) {
            return name.isEmpty() ? String.valueOf(a.line) : name + ":" + a.line;
        }
        return name;
    }

    /** クラス内の `setOnClickListener(...)` 等で渡されたラムダ/匿名本体を収集する。 */
    private static List<JavaMethodInfo> collectListeners(JavaClassInfo c) {
        List<JavaMethodInfo> out = new ArrayList<>();
        for (JavaMethodInfo m : c.getMethods()) {
            for (JavaMethodInfo.Call call : m.getCalls()) {
                out.addAll(call.getInlineMethods());
            }
        }
        for (JavaFieldInfo f : c.getFields()) {
            out.addAll(f.getInlineMethods());
        }
        return out;
    }

    /**
     * caller の statement tree を再帰走査し、{@code calleeName} の呼び出しを囲む分岐条件
     * （if/while/switch/try 等のラベル連鎖）を {@code acc} に集約する。
     * 分岐に囲まれない（無条件）呼び出しは {@link #DIRECT_CALL} として記録するため、
     * 同じメソッドが「無条件呼び出し」と「分岐内呼び出し」の両方を持つ場合に両者を併記できる。
     */
    private static void collectConditions(List<JavaMethodInfo.Statement> stmts,
                                          Deque<String> ctx, String calleeName, Set<String> acc) {
        for (JavaMethodInfo.Statement s : stmts) {
            if (s instanceof JavaMethodInfo.Call) {
                if (calleeName.equals(((JavaMethodInfo.Call) s).getMethodName())) {
                    acc.add(ctx.isEmpty() ? DIRECT_CALL : String.join(" → ", ctx));
                }
            } else if (s instanceof JavaMethodInfo.Block) {
                for (JavaMethodInfo.Branch br : ((JavaMethodInfo.Block) s).getBranches()) {
                    ctx.addLast(branchLabel(br));
                    collectConditions(br.getBody(), ctx, calleeName, acc);
                    ctx.removeLast();
                }
            }
        }
    }

    private static String branchLabel(JavaMethodInfo.Branch br) {
        String type = nz(br.getType());
        String label = nz(br.getLabel()).replaceAll("\\s+", " ").trim();
        return label.isEmpty() ? type : type + " (" + label + ")";
    }

    /** SAM メソッド名から UI トリガの説明を返す。 */
    private static String triggerOf(String samName) {
        if (samName == null || samName.isEmpty()) {
            return "リスナー";
        }
        switch (samName) {
            case "onClick":
                return "クリック (onClick)";
            case "onLongClick":
                return "長押し (onLongClick)";
            case "onCheckedChanged":
                return "チェック変更 (onCheckedChanged)";
            case "onTouch":
                return "タッチ (onTouch)";
            default:
                return "リスナー (" + samName + ")";
        }
    }

    /** メソッド署名を `<可視性> [static] [abstract] name(name: type, ...): returnType` で整形。 */
    private static String signature(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        if (m.getVisibility() != null) {
            sb.append(m.getVisibility().mark()).append(' ');
        }
        if (m.isStatic()) {
            sb.append("static ");
        }
        if (m.isAbstract()) {
            sb.append("abstract ");
        }
        sb.append(nz(m.getName())).append('(');
        List<String> types = m.getParameterTypes();
        List<String> names = m.getParameterNames();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String name = i < names.size() ? names.get(i) : "";
            if (name != null && !name.isEmpty()) {
                sb.append(name).append(": ");
            }
            sb.append(types.get(i) == null ? "?" : types.get(i));
        }
        sb.append(')');
        if (!m.isConstructor() && m.getReturnType() != null && !m.getReturnType().isEmpty()) {
            sb.append(": ").append(m.getReturnType());
        }
        return sb.toString();
    }

    /** 関数が宣言されたソースのファイル名。未取得時は空。 */
    private static String fileLabel(Row r) {
        return nz(r.sourceFile);
    }

    /** 関数の宣言開始行。未取得時は空。 */
    private static String lineLabel(Row r) {
        return r.startLine > 0 ? String.valueOf(r.startLine) : "";
    }

    private static String callerLabel(ReferenceSite s) {
        StringBuilder sb = new StringBuilder(nz(s.getCallerFqn()));
        if (!nz(s.getCallerMethod()).isEmpty()) {
            sb.append('.').append(s.getCallerMethod());
        }
        String file = nz(s.getFile());
        if (!file.isEmpty()) {
            int sep = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
            String fileName = sep >= 0 ? file.substring(sep + 1) : file;
            sb.append(" (").append(fileName);
            if (s.getLineHint() > 0) {
                sb.append(':').append(s.getLineHint());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private static String mdInline(String s) {
        return nz(s).replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private static String csv(String s) {
        String v = nz(s);
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
