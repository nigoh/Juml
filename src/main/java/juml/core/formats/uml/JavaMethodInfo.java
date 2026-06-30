// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * メソッド宣言情報。
 *
 * <p>シーケンス図生成のため、メソッド本体内の文を構造化して
 * {@link #getStatements()} に保持する。各文は {@link Call}（メソッド呼び出し）
 * もしくは {@link Block}（{@code if/while/for/try/...} 等の制御ブロック）。
 * {@link #getCalls()} は後方互換のため、ネストを平坦化した呼び出し列を返す。</p>
 */
public class JavaMethodInfo {

    /** メソッド本体内の文の共通インタフェース。 */
    public interface Statement {
    }

    /** {@code receiver.method(...)} 形式の呼び出し情報。 */
    public static class Call implements Statement {
        private final String receiver;
        private final String methodName;
        private final List<JavaMethodInfo> inlineMethods = new ArrayList<>();
        private String firstArgLabel;
        private String resolvedOwnerFqn;
        private String resolvedSignature;

        public Call(String receiver, String methodName) {
            this.receiver = receiver;
            this.methodName = methodName;
        }

        public String getReceiver() {
            return receiver;
        }

        public String getMethodName() {
            return methodName;
        }

        /**
         * シンボル解決器が特定した呼び出し先の宣言型 FQN。
         * 解決済みなら逆参照インデックスはこれを優先し、receiver 文字列のヒューリスティックを
         * 飛ばす。チェーン呼び出し・オーバーロード・継承・ジェネリクスを正確に辿れる。
         * 未解決なら null。
         */
        public String getResolvedOwnerFqn() {
            return resolvedOwnerFqn;
        }

        public void setResolvedOwnerFqn(String resolvedOwnerFqn) {
            this.resolvedOwnerFqn = resolvedOwnerFqn;
        }

        /** 解決済みメソッドのシグネチャ (例: {@code doIt(int, java.lang.String)})。未解決なら null。 */
        public String getResolvedSignature() {
            return resolvedSignature;
        }

        public void setResolvedSignature(String resolvedSignature) {
            this.resolvedSignature = resolvedSignature;
        }

        /**
         * 呼び出し引数にラムダ/匿名クラス/メソッド参照が渡された場合、その本体から
         * 抽出したメソッド一覧。{@code button.setOnClickListener(v -> ...)} のような
         * リスナー登録呼び出しでコールバック本体を保持する。空なら該当なし。
         */
        public List<JavaMethodInfo> getInlineMethods() {
            return inlineMethods;
        }

        /**
         * 呼び出しの第 1 引数が定数シンボル参照 (例:
         * {@code VehiclePropertyIds.HVAC_TEMPERATURE_SET},
         * {@code Manifest.permission.READ_PHONE_STATE},
         * 単独 {@code MAX_VALUE}) のとき、そのシンボル文字列を保持する。
         * シーケンス図のラベルに {@code method(SYMBOL)} 形式で添える用途。
         * 該当しなければ null。
         */
        public String getFirstArgLabel() {
            return firstArgLabel;
        }

        public void setFirstArgLabel(String firstArgLabel) {
            this.firstArgLabel = firstArgLabel;
        }
    }

    /**
     * {@code return ...;} 文。
     *
     * <p>{@code expression} は {@code return} の右側のソース文字列を整形したもの。
     * {@code return;} (void) の場合は空文字。アクティビティ図で末端ノードとして描画する。</p>
     */
    public static class Return implements Statement {
        private final String expression;

        public Return(String expression) {
            this.expression = expression == null ? "" : expression;
        }

        public String getExpression() {
            return expression;
        }
    }

    /**
     * {@code throw ...;} 文。
     *
     * <p>{@code expression} は {@code throw} の右側のソース文字列を整形したもの
     * (例: {@code new IllegalArgumentException("..")})。アクティビティ図で
     * 異常終了ノードとして描画する。</p>
     */
    public static class Throw implements Statement {
        private final String expression;

        public Throw(String expression) {
            this.expression = expression == null ? "" : expression;
        }

        public String getExpression() {
            return expression;
        }
    }

    /**
     * {@code yield expr;} 文 (Java 14+ switch 式の値返却)。
     *
     * <p>switch 式のアーム本体ブロック内でのみ意味を持つ。アクティビティ図では
     * 値を返すノードとして描画でき、シーケンス図では参考情報として利用できる。</p>
     */
    public static class Yield implements Statement {
        private final String expression;

        public Yield(String expression) {
            this.expression = expression == null ? "" : expression;
        }

        public String getExpression() {
            return expression;
        }
    }

    /** {@code break [label];} 文。ループ外の break (switch) でも同じクラスを使う。 */
    public static class Break implements Statement {
        private final String label;

        public Break(String label) {
            this.label = label == null ? "" : label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** {@code continue [label];} 文。 */
    public static class Continue implements Statement {
        private final String label;

        public Continue(String label) {
            this.label = label == null ? "" : label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * ローカル変数宣言文。例: {@code String name = getData();}
     *
     * <p>アクティビティ図で変数定義ノードとして描画する。
     * initExpr が空なら初期化なし ({@code Type varName;})。
     * initExpr にラムダ/匿名クラスが含まれていた場合は {@link #inlineMethods} に
     * コールバック本体が格納される。</p>
     */
    public static class LocalVar implements Statement {
        private final String type;
        private final String varName;
        private final String initExpr;
        private final List<JavaMethodInfo> inlineMethods = new ArrayList<>();

        public LocalVar(String type, String varName, String initExpr) {
            this.type = type == null ? "" : type;
            this.varName = varName == null ? "" : varName;
            this.initExpr = initExpr == null ? "" : initExpr;
        }

        public String getType() {
            return type;
        }

        public String getVarName() {
            return varName;
        }

        public String getInitExpr() {
            return initExpr;
        }

        public List<JavaMethodInfo> getInlineMethods() {
            return inlineMethods;
        }
    }

    /**
     * メソッド本体内のインラインコメント (行コメント / ブロックコメント)。
     *
     * <p>アクティビティ図で note として描画する。
     * {@link JavaCommentScanner#cleanText(JavaCommentScanner.Comment)} 済みのテキストを保持する。</p>
     */
    public static class InlineComment implements Statement {
        private final String text;

        public InlineComment(String text) {
            this.text = text == null ? "" : text;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 制御ブロック ({@code if}/{@code while}/{@code for}/{@code do-while}/
     * {@code switch}/{@code try}/{@code synchronized})。
     *
     * <p>{@code if/else if/else} や {@code try/catch/finally} のように
     * 複数の分岐を持つ構文は {@link Branch} の列で表現する。
     * 単一の本体しかもたない {@code while}/{@code for}/{@code synchronized} は
     * 分岐 1 つだけ持つ。</p>
     */
    public static class Block implements Statement {

        /** ブロック種別。 */
        public enum Kind { IF, WHILE, FOR, DO_WHILE, SWITCH, TRY, SYNCHRONIZED }

        private final Kind kind;
        private final List<Branch> branches = new ArrayList<>();

        public Block(Kind kind) {
            this.kind = kind;
        }

        public Kind getKind() {
            return kind;
        }

        public List<Branch> getBranches() {
            return branches;
        }
    }

    /** {@link Block} 内の 1 つの分岐 (if 節、case 節、catch 節など)。 */
    public static class Branch {
        /** "if" / "else if" / "else" / "case" / "default" / "try" / "catch" / "finally" / "while" / "for" / "do" / "synchronized" 等。 */
        private final String type;
        /** 条件式や case ラベル等の元ソース文字列。なければ空文字。 */
        private String label;
        private final List<Statement> body = new ArrayList<>();

        public Branch(String type, String label) {
            this.type = type;
            this.label = label == null ? "" : label;
        }

        public String getType() {
            return type;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label == null ? "" : label;
        }

        public List<Statement> getBody() {
            return body;
        }
    }

    private String name;
    private int startLine = -1;
    private String returnType;
    private final List<String> parameterTypes = new ArrayList<>();
    private final List<String> parameterNames = new ArrayList<>();
    private Visibility visibility = Visibility.PACKAGE;
    private boolean isStatic;
    private boolean isAbstract;
    private boolean isConstructor;
    private final List<String> annotations = new ArrayList<>();
    private final List<String> throwsTypes = new ArrayList<>();
    private final List<Statement> statements = new ArrayList<>();
    private String comment;
    private final List<String> bodyComments = new ArrayList<>();
    /** アノテーション属性の {@code default} 値（{@code @interface} 専用）。未指定は null。 */
    private String defaultValue;
    /** メソッドレベルのジェネリック宣言（{@code "<T, R>"}）。なければ null。 */
    private String typeParameters;

    public String getName() {
        return name;
    }

    /** アノテーション属性の {@code default} 値（例: {@code "30"}）。未指定は null。 */
    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /** メソッドレベルのジェネリック宣言（{@code "<T, R>"}）。なければ null。 */
    public String getTypeParameters() {
        return typeParameters;
    }

    public void setTypeParameters(String typeParameters) {
        this.typeParameters = typeParameters;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** メソッド/コンストラクタ宣言の開始行 (1始まり)。未取得時は -1。 */
    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public List<String> getParameterNames() {
        return parameterNames;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean aStatic) {
        isStatic = aStatic;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean anAbstract) {
        isAbstract = anAbstract;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public void setConstructor(boolean constructor) {
        isConstructor = constructor;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    /** {@code throws} 節で宣言された例外型名のリスト (宣言順)。 */
    public List<String> getThrowsTypes() {
        return throwsTypes;
    }

    /** メソッド本体の構造化された文ツリー。 */
    public List<Statement> getStatements() {
        return statements;
    }

    /**
     * 後方互換: メソッド本体内の呼び出しを平坦化して返す。
     * 制御ブロック内の呼び出しもすべて含む。返値は新規リストなので
     * 変更してもツリー側には影響しない。
     */
    public List<Call> getCalls() {
        List<Call> out = new ArrayList<>();
        collectCalls(statements, out);
        return out;
    }

    private static void collectCalls(List<Statement> in, List<Call> out) {
        for (Statement s : in) {
            if (s instanceof Call) {
                out.add((Call) s);
            } else if (s instanceof Block) {
                for (Branch b : ((Block) s).getBranches()) {
                    collectCalls(b.getBody(), out);
                }
            } else if (s instanceof LocalVar) {
                for (JavaMethodInfo inline : ((LocalVar) s).getInlineMethods()) {
                    collectCalls(inline.getStatements(), out);
                }
            }
        }
    }

    /** JavaDoc / 直前コメントを整形した文字列。未取得時は null。 */
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * メソッド本体内で出現したコメント (行/ブロック/JavaDoc) を整形済み文字列で順に保持する。
     * シーケンス図の note にメソッド意図を併載する際に利用する。
     */
    public List<String> getBodyComments() {
        return bodyComments;
    }
}
