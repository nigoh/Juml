// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import juml.core.formats.java.JavaLexer;
import juml.core.formats.uml.JavaClassInfo;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaParser を用いた Java ソースの解析フロントエンド。
 *
 * <p>{@link CompilationUnit} を {@link TypeDeclAdapter} / {@link MemberAdapter} /
 * {@link ModuleAdapter} で既存モデル ({@link JavaClassInfo}) に変換して返す。
 * P1 では構造（型・フィールド・メソッド署名・imports・アノテーション・モジュール）のみを移送し、
 * メソッド本体の statement tree は後続フェーズで追加する。</p>
 *
 * <p>解析は寛容（{@link ParserConfiguration.LanguageLevel#BLEEDING_EDGE}）で行い、
 * 構文エラーがあっても部分結果を返す。問題は {@link ErrorListener} に通知する。</p>
 */
public final class JavaParserFrontend {

    private JavaParserFrontend() {
    }

    /** ソースを解析して {@link JavaClassInfo} のリストを返す。 */
    public static List<JavaClassInfo> parse(String source, ErrorListener listener) {
        return parse(source, false, listener, null);
    }

    /** {@code headersOnly} のときはメソッド本体の statement tree を構築しない。 */
    public static List<JavaClassInfo> parse(String source, boolean headersOnly,
                                            ErrorListener listener) {
        return parse(source, headersOnly, listener, null);
    }

    /**
     * {@code solver} が非 null なら SymbolSolver で呼び出し先を解決し、
     * {@link juml.core.formats.uml.JavaMethodInfo.Call#getResolvedOwnerFqn()} を埋める。
     */
    public static List<JavaClassInfo> parse(String source, boolean headersOnly,
                                            ErrorListener listener, JpSolver solver) {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        List<JavaClassInfo> out = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        // Java の Unicode エスケープ (\\uXXXX) は識別子/キーワードにも使えるため、字句解析前に展開する。
        String expanded = JavaLexer.expandUnicodeEscapes(source);
        boolean resolve = solver != null && !headersOnly;
        JavaParser parser;
        if (solver != null) {
            parser = solver.newParser();
        } else {
            parser = new JavaParser(new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
        }
        ParseResult<CompilationUnit> result = parser.parse(expanded);
        if (!result.isSuccessful()) {
            for (Problem p : result.getProblems()) {
                l.onError(null, lineOf(p), p.getMessage());
            }
        }
        CompilationUnit cu = result.getResult().orElse(null);
        if (cu == null) {
            return out;
        }
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        List<String> imports = new ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            imports.add(importString(imp));
        }
        cu.getModule().ifPresent(m -> out.add(ModuleAdapter.adapt(m)));
        JpContext ctx = new JpContext(pkg, imports, out, headersOnly,
                new JpComments(expanded), resolve);
        for (TypeDeclaration<?> td : cu.getTypes()) {
            TypeDeclAdapter.adapt(td, null, ctx);
        }
        if (out.isEmpty()) {
            // 重度に壊れたソース（途中で切れた宣言など）でも、宣言ヘッダが読めれば
            // スケルトンだけは返す（既存パーサーの寛容さに合わせる）。
            recoverSkeletons(expanded, pkg, out);
        }
        return out;
    }

    // \b は @ の直前では単語境界として機能しないため、@interface を別の選択肢として分離する。
    // (?:\b(?:class|interface|enum|record)|@interface) で両方を正しくマッチさせる。
    private static final java.util.regex.Pattern TYPE_DECL = java.util.regex.Pattern.compile(
            "(?:\\b(?:class|interface|enum|record)|@interface)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    /**
     * ソースからコメント・文字列リテラルを空白に置き換えたテキストを返す。
     *
     * <p>{@link #recoverSkeletons} が文字列/コメント内に書かれた "class Foo" などを
     * 誤って型宣言と認識しないようにするための前処理。行番号を保つため
     * 置換後の文字数は元と同じにする（改行はそのまま残す）。</p>
     */
    private static String stripStringsAndComments(String src) {
        int len = src.length();
        StringBuilder sb = new StringBuilder(len);
        int i = 0;
        while (i < len) {
            char c = src.charAt(i);
            // 行コメント: 行末まで空白で埋める
            if (c == '/' && i + 1 < len && src.charAt(i + 1) == '/') {
                while (i < len && src.charAt(i) != '\n') {
                    sb.append(' ');
                    i++;
                }
                continue;
            }
            // ブロックコメント: */ まで空白で埋める（改行は保持）
            if (c == '/' && i + 1 < len && src.charAt(i + 1) == '*') {
                sb.append("  "); // '/' と '*' の 2 文字
                i += 2;
                while (i < len) {
                    char d = src.charAt(i);
                    if (d == '*' && i + 1 < len && src.charAt(i + 1) == '/') {
                        sb.append("  ");
                        i += 2;
                        break;
                    }
                    sb.append(d == '\n' ? '\n' : ' ');
                    i++;
                }
                continue;
            }
            // テキストブロック: """ ... """ まで空白で埋める
            if (c == '"' && i + 2 < len
                    && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                sb.append("   ");
                i += 3;
                while (i < len) {
                    char d = src.charAt(i);
                    if (d == '"' && i + 2 < len
                            && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                        sb.append("   ");
                        i += 3;
                        break;
                    }
                    sb.append(d == '\n' ? '\n' : ' ');
                    i++;
                }
                continue;
            }
            // 文字列リテラル: " ... " まで空白で埋める（エスケープ考慮）
            if (c == '"' || c == '\'') {
                char delim = c;
                sb.append(' ');
                i++;
                while (i < len) {
                    char d = src.charAt(i);
                    if (d == '\\' && i + 1 < len) {
                        sb.append("  ");
                        i += 2;
                        continue;
                    }
                    sb.append(d == '\n' ? '\n' : ' ');
                    i++;
                    if (d == delim) {
                        break;
                    }
                }
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static void recoverSkeletons(String src, String pkg, List<JavaClassInfo> out) {
        // コメント・文字列内の型キーワードを誤検出しないよう事前に除去する
        java.util.regex.Matcher m = TYPE_DECL.matcher(stripStringsAndComments(src));
        while (m.find()) {
            JavaClassInfo c = new JavaClassInfo();
            c.setPackageName(pkg);
            // グループ1 = 型名（パターン変更後はキーワードを非キャプチャグループにしたため）
            c.setSimpleName(m.group(1));
            // マッチ全体から先頭キーワードを取り出して kind を判定する
            String matchedText = m.group(0);
            c.setKind(skeletonKind(matchedText.startsWith("@interface") ? "@interface"
                    : matchedText.trim().split("\\s+")[0]));
            out.add(c);
        }
    }

    private static JavaClassInfo.Kind skeletonKind(String kw) {
        switch (kw) {
            case "interface":
                return JavaClassInfo.Kind.INTERFACE;
            case "enum":
                return JavaClassInfo.Kind.ENUM;
            case "record":
                return JavaClassInfo.Kind.RECORD;
            case "@interface":
                return JavaClassInfo.Kind.ANNOTATION;
            default:
                return JavaClassInfo.Kind.CLASS;
        }
    }

    private static String importString(ImportDeclaration imp) {
        String n = imp.getNameAsString();
        if (imp.isAsterisk()) {
            n = n + ".*";
        }
        if (imp.isStatic()) {
            n = "static " + n;
        }
        return n;
    }

    private static int lineOf(Problem p) {
        return p.getLocation()
                .flatMap(loc -> loc.getBegin().getRange())
                .map(r -> r.begin.line)
                .orElse(-1);
    }
}
