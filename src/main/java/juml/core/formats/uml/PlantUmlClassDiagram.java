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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * クラス情報 ({@link JavaClassInfo}) のリストから PlantUML 形式のクラス図テキストを生成する。
 *
 * <p>{@link Options} で出力する要素 (継承線、利用関係、可視性記号、AAOS マーカー)
 * を切り替えられる。</p>
 */
public final class PlantUmlClassDiagram {

    /** コメント表示スタイル。 */
    public enum CommentStyle {
        /** クラス本体内に {@code .. text ..} セパレータでコメントを埋め込む (コンパクト)。 */
        INLINE,
        /** クラス・メンバーに対して {@code note ...} ブロックを発行する (詳細)。 */
        NOTE
    }

    /** 出力オプション。 */
    public static class Options {
        public boolean showVisibility = true;
        public boolean visibilityIcons = true; // 可視性をカラーアイコンで描く(VisibilityIconStyle)。false で記号表示
        public boolean showInheritance = true;
        /**
         * {@code implements} によるインタフェース実装線を出力する。既定 true。
         * 関連線種別フィルタで extends と implements を別々に制御したい場合に使う。
         */
        public boolean showImplementations = true;
        public boolean showUsageRelations = true;
        public boolean showFields = true;
        public boolean showMethods = true;
        /**
         * 「メンバー変数で関数を変数として設定」している箇所
         * (フィールド初期化子の匿名クラス/ラムダ/メソッド参照、およびコンストラクタや
         * メソッド本体内での {@code this.field = ...} 形式の代入) で捕捉した
         * コールバック本体をクラス図に表示する。
         */
        public boolean showInlineFunctions = true;
        public boolean groupByPackage = true;
        public boolean markAaosCategories = true;
        /** 凡例ブロックをダイアグラム右に追加する。 */
        public boolean includeLegend = true;
        /** 利用関係を出すフィールド型の最大要素数 (1 クラスあたり)。多すぎる場合に抑制。 */
        public int maxUsagePerClass = 30;
        /** タイトル文字列 (null で省略)。 */
        public String title;
        /** JavaDoc / 直前コメントを出力する。 */
        public boolean showComments = true;
        /** コメント表示スタイル。 */
        public CommentStyle commentStyle = CommentStyle.INLINE;
        /**
         * インライン表示時のコメント 1 件あたり最大文字数。INLINE では省略、NOTE では折り返しに使用。
         * 0 以下は無制限 (全文表示、既定)。
         */
        public int commentMaxLength = 0;
        /**
         * コメント文字列の色 (PlantUML の {@code <color:#RRGGBB>} に渡す値)。
         * INLINE 表示時はテキストを {@code <color:...>...</color>} で囲み、
         * NOTE 表示時は note の枠線/文字色 skinparam に適用する。
         * null または空文字を指定すると色付けを行わない。
         */
        public String commentColor = "#008800";
        /** フィールド/メソッドのアノテーションを出力する。 */
        public boolean showAnnotations = true;
        /** {@link #showAnnotations} が true でも表示しないアノテーション名 (ノイズ抑制)。 */
        public Set<String> hiddenAnnotations = new HashSet<>(
                Arrays.asList("Override", "SuppressWarnings"));
        /** enum 定数を表示する。 */
        public boolean showEnumConstants = true;
        /** {@code final} フィールドに {@code &#123;final&#125;} マーカーを付ける。 */
        public boolean showFinal = true;
        /** メソッド/コンストラクタの {@code throws} 例外をシグネチャに併記する。既定 false (ノイズ抑制)。 */
        public boolean showThrows = false;
        /** {@code static final} 定数の初期化値を {@code = 100} のように併記する。既定 true。 */
        public boolean showConstantValues = true;
        /**
         * メンバー行に出す型/値などのテキスト断片 1 つあたりの安全上限 (文字数)。
         * これを超えた断片 (巨大な定数値・深いジェネリクス型など) は末尾を {@code …} で省略する。
         * メンバー行は折り返されないため、極端に長い 1 断片は行幅がキャンバス上限を超えて
         * 図全体が描画失敗する原因になる。0 以下で無制限 (従来どおり全文表示)。
         * 既定 {@link PlantUmlCommentFormatter#MEMBER_TEXT_SAFETY_LIMIT}。
         */
        public int maxMemberTextLength = PlantUmlCommentFormatter.MEMBER_TEXT_SAFETY_LIMIT;
        /**
         * {@code static final} 定数を通常フィールドの前にまとめ、間に区切り線を挿入する。
         * 定数定義がひと目で分かるようにする。既定 true。
         */
        public boolean groupConstants = true;
        /** ネストした型と外側の型を含有エッジ ({@code Outer +-- Inner}) で結ぶ。既定 false。 */
        public boolean showNestedContainment = false;
        /** 図全体に出すクラスの最大数 (0 以下で無制限)。超過時は先頭から切り詰める。 */
        public int maxClasses = 0;
        /** 図末尾に出す警告メッセージ (PlantUML の {@code footer} 行)。null/空で出力しない。 */
        public String footerWarning;
        /** Jetpack (Fragment / ViewModel / Hilt 等) ステレオタイプ・装飾の設定。既定で無効。 */
        public JetpackOptions jetpack = new JetpackOptions();
        /**
         * 各クラス宣言に {@code [[juml://class/<FQN>]]} を付与する。
         * GUI プレビューで右クリック→メソッド一覧のヒットテスト用に SVG へ
         * {@code <a xlink:href>} を埋め込みたい場合のみ true にする。
         * CLI 出力や保存図には影響させないため既定で false。
         */
        public boolean interactiveLinks = false;
        /**
         * {@link #interactiveLinks} の URL スキーム接頭辞。
         * SVG 上の {@code href} から FQN を取り出す側 (GUI) と揃える。
         */
        public String interactiveLinkPrefix = "juml://class/";
        /**
         * 外部ライブラリ (java.*, android.*, kotlin.* など) を完全に除外する。
         * 既定 false (除外せず {@code <<external>>} / {@code <<missing>>} ステレオタイプで区別表示する
         * 既存挙動を維持)。
         *
         * <p>判定は 2 段で行う:
         * <ol>
         *   <li>{@link JavaClassInfo#getOrigin()} が {@code EXTERNAL_JAR} / {@code MISSING_JAR}</li>
         *   <li>パッケージ名が {@link #externalPackagePrefixes} のいずれかに前方一致</li>
         * </ol>
         * いずれかに該当すれば除外される。</p>
         */
        public boolean excludeExternalLibraries = false;
        /**
         * 外部ライブラリ判定のパッケージ prefix セット。
         * null/空のときは {@link ExternalPackageMatcher#DEFAULT_PREFIXES} が使われる。
         */
        public Set<String> externalPackagePrefixes =
                new LinkedHashSet<>(ExternalPackageMatcher.DEFAULT_PREFIXES);
        /**
         * 図に含まれない継承先 (extends/implements 先) のうち、JDK 標準ライブラリや
         * 外部ライブラリと判定できるものを、ステレオタイプ付きノードとして補完描画する。
         * 既定 false (従来通り無装飾の暗黙ノードのまま)。
         *
         * <p>{@link #excludeExternalLibraries} が true の場合は除外が優先され、
         * external supertype ノードは描かない。</p>
         */
        public boolean markExternalSupertypes = false;
        /**
         * {@link #markExternalSupertypes} 時に、JDK 標準 ({@code java.}/{@code javax.}) を
         * {@code <<standard>>}、その他外部を {@code <<external>>} に分けるか。
         * false なら一律 {@code <<external>>}。既定 true。
         */
        public boolean distinguishStandardLibrary = true;
        /**
         * {@link #markExternalSupertypes} 時に継承先の単純名を FQN へ解決する任意の解決器。
         * null の場合はドットを含む型参照のみ判定対象となる (JAR/AAR 由来は FQN なので有効)。
         */
        public TypeRefResolver supertypeResolver;
        /**
         * {@link #markExternalSupertypes} 時に、FQN が依存 JAR/AAR
         * (Gradle cache・リポジトリ同梱のローカル JAR) に実在するかを判定する任意の判定器。
         * prefix 集合で判定できない社内ライブラリ等を {@code <<external>>} 扱いに
         * するために使う。null 可。
         */
        public java.util.function.Predicate<String> dependencyClassPredicate;
        /**
         * {@code public} 可視性のクラス / フィールド / メソッドのみを表示する。
         * 既定 false (全可視性を表示)。
         */
        public boolean publicOnly = false;
        /**
         * {@code top to bottom direction} を図冒頭に出力する。
         * 継承階層図のように親クラスを上・子クラスを下に並べたい場合に使う。既定 false。
         */
        public boolean topToBottomDirection = false;
        /**
         * {@link #topToBottomDirection} が true の場合に、同一親を持つ兄弟ノードを
         * 指定数ごとに折り返す。超えた分は隠しリンクで次の行（ランク）に押し出す。
         * 0 以下で無制限 (折り返しなし)。
         */
        public int maxSiblingsPerRow = 0;
        /** 関係線を種別ごとに色分け（継承=緑/実装=青/利用=灰破線）。既定 false。色定義は {@link PlantUmlClassRelations}。 */
        public boolean colorCodeRelations = false;
        /** フォーカス強調モードの焦点クラス FQN（単純名も可）。空で無効。{@link PlantUmlClassFocus} 参照。 */
        public String focusClass = "";
        /** 焦点の 1-hop 近傍 QN 集合（generate 内で計算してセット。利用側が直接触る必要はない）。 */
        public Set<String> focusEmphasis;
    }

    static final Pattern PRIMITIVE_OR_BUILTIN = Pattern.compile(
            "^(void|boolean|byte|char|short|int|long|float|double"
                    + "|String|Object|CharSequence|Number"
                    + "|Integer|Long|Short|Byte|Float|Double|Boolean|Character"
                    + "|Class|Map|List|Set|Collection|Iterable|Iterator|Queue"
                    + "|HashMap|ArrayList|LinkedList|HashSet|LinkedHashMap)$");

    /** デフォルト Options で生成。 */
    public static String generate(List<JavaClassInfo> classes) {
        return generate(classes, null);
    }

    /** オプション付き生成。 */
    public static String generate(List<JavaClassInfo> classes, Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        Options o = opts != null ? opts : new Options();
        // 0. module-info (Kind.MODULE) は描画対象外。さらに FQN 昇順で安定ソートし、
        //    エイリアス ID/出力順/maxClasses 切り詰めの実行ごとのぶれを無くす (再現性確保)。
        classes = classes.stream()
                .filter(c -> c.getKind() != JavaClassInfo.Kind.MODULE)
                .sorted(java.util.Comparator.comparing(JavaClassInfo::getQualifiedName))
                .collect(java.util.stream.Collectors.toList());
        // 1. 外部ライブラリ除外: Origin が EXTERNAL_JAR / MISSING_JAR か、
        //    パッケージ名が externalPackagePrefixes に前方一致するクラスを除く。
        if (o.excludeExternalLibraries) {
            List<JavaClassInfo> next = new ArrayList<>(classes.size());
            for (JavaClassInfo c : classes) {
                if (isExternalClass(c, o)) {
                    continue;
                }
                next.add(c);
            }
            classes = next;
        }
        // 2. publicOnly: クラス自体の public 修飾子で絞る。
        if (o.publicOnly) {
            List<JavaClassInfo> next = new ArrayList<>(classes.size());
            for (JavaClassInfo c : classes) {
                if (isPublicLike(c)) {
                    next.add(c);
                }
            }
            classes = next;
        }
        // 3. maxClasses が指定されていれば先頭から切り詰める。
        int originalTotal = classes.size();
        if (o.maxClasses > 0 && classes.size() > o.maxClasses) {
            classes = classes.subList(0, o.maxClasses);
        }
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.topToBottomDirection) {
            out.append("top to bottom direction\n");
        }
        if (o.title != null && !o.title.isEmpty()) {
            // title 行に <> & が含まれると PlantUML が HTML タグとして誤認するためエスケープする。
            out.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        }
        VisibilityIconStyle.appendSkinparams(out, o.showVisibility && o.visibilityIcons);
        // NOTE 表示時のコメント色を skinparam で指定 (INLINE 時は <color:..> タグで個別色付けするため出力しない)。
        if (o.showComments && o.commentStyle == CommentStyle.NOTE
                && o.commentColor != null && !o.commentColor.isEmpty()) {
            out.append("skinparam noteBorderColor ").append(o.commentColor).append('\n');
            out.append("skinparam noteFontColor ").append(o.commentColor).append('\n');
        }
        // クラスごとに一意のエイリアスを発行する (PlantUML は "a.b.c" をネスト解釈するため引用符名 + as で切り離す)。
        Set<String> knownNames = new HashSet<>();
        java.util.Map<String, String> aliasByQn = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> qnBySimple = new java.util.HashMap<>();
        int aliasSeq = 0;
        for (JavaClassInfo c : classes) {
            String qn = c.getQualifiedName();
            knownNames.add(qn);
            aliasByQn.put(qn, "C" + (aliasSeq++));
            qnBySimple.putIfAbsent(c.getSimpleName(), qn);
            if (c.getEnclosingClass() != null && !c.getEnclosingClass().isEmpty()) {
                qnBySimple.putIfAbsent(
                        c.getEnclosingClass() + "." + c.getSimpleName(), qn);
            }
        }
        // フォーカス強調モード: 焦点 FQN の正規化と 1-hop 近傍計算を o に反映する。
        PlantUmlClassFocus.prepare(o, classes, knownNames, qnBySimple);

        if (o.groupByPackage) {
            Map<String, List<JavaClassInfo>> byPkg = new LinkedHashMap<>();
            for (JavaClassInfo c : classes) {
                byPkg.computeIfAbsent(
                        c.getPackageName() == null ? "" : c.getPackageName(),
                        k -> new ArrayList<>()).add(c);
            }
            for (Map.Entry<String, List<JavaClassInfo>> e : byPkg.entrySet()) {
                String pkg = e.getKey();
                if (pkg.isEmpty()) {
                    for (JavaClassInfo c : e.getValue()) {
                        emitClass(out, c, o, "", aliasByQn);
                    }
                } else {
                    out.append("package ").append(quoteId(pkg)).append(" {\n");
                    for (JavaClassInfo c : e.getValue()) {
                        emitClass(out, c, o, "  ", aliasByQn);
                    }
                    out.append("}\n");
                }
            }
        } else {
            for (JavaClassInfo c : classes) {
                emitClass(out, c, o, "", aliasByQn);
            }
        }

        // 機能A: 図に含まれない継承先のうち標準/外部ライブラリと判定できるものを
        // ステレオタイプ付きノードとして補完宣言する (excludeExternalLibraries 時は描かない)。
        Map<String, String> externalSupertypeAlias = java.util.Collections.emptyMap();
        if (o.markExternalSupertypes && !o.excludeExternalLibraries
                && (o.showInheritance || o.showImplementations)) {
            externalSupertypeAlias = PlantUmlClassRelations.emitExternalSupertypes(
                    out, classes, o, knownNames, aliasByQn, qnBySimple);
        }

        // 関係線
        if (o.showInheritance || o.showImplementations) {
            for (JavaClassInfo c : classes) {
                PlantUmlClassRelations.emitInheritance(
                        out, c, o, aliasByQn, qnBySimple, externalSupertypeAlias);
            }
            if (o.topToBottomDirection && o.maxSiblingsPerRow > 0) {
                PlantUmlClassRelations.emitSiblingWrapHints(
                        out, classes, aliasByQn, qnBySimple, externalSupertypeAlias, o);
            }
        }
        if (o.showUsageRelations || o.showNestedContainment) {
            // 型解決の接尾辞索引を 1 度だけ構築 (クラスごとの全走査 O(n^2) を回避)。
            KnownTypeIndex knownIdx = new KnownTypeIndex(knownNames);
            for (JavaClassInfo c : classes) {
                if (o.showUsageRelations) {
                    PlantUmlClassRelations.emitUsage(
                            out, c, knownIdx, aliasByQn, qnBySimple, externalSupertypeAlias, o);
                }
                if (o.showNestedContainment) {
                    PlantUmlClassRelations.emitNesting(out, c, aliasByQn);
                }
            }
        }
        if (o.includeLegend) {
            PlantUmlClassLegend.emit(out, classes, o);
        }
        // フッタ警告: maxClasses で切り詰めた場合の自動メッセージを優先
        String footer = o.footerWarning;
        if ((footer == null || footer.isEmpty())
                && o.maxClasses > 0 && originalTotal > classes.size()) {
            footer = "showing " + classes.size() + " of " + originalTotal + " classes";
        }
        if (footer != null && !footer.isEmpty()) {
            // footer テキストの < をチルダエスケープしてタグ誤認を防ぐ
            out.append("footer ").append(PlantUmlCommentFormatter.escapeText(footer)).append('\n');
        }
        out.append("@enduml\n");
        return out.toString();
    }

    static boolean hasVisibleAnnotation(List<String> annotations, Options o) {
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        for (String a : annotations) {
            if (a == null || a.isEmpty()) {
                continue;
            }
            String name = annotationName(a);
            if (o.hiddenAnnotations != null && o.hiddenAnnotations.contains(name)) {
                continue;
            }
            return true;
        }
        return false;
    }

    static String stereoDesc(String stereo) {
        switch (stereo) {
            case "CarManager": return "AAOS の Car*Manager クラス";
            case "CarService": return "AAOS の Car*Service クラス";
            case "ICarInterface": return "ICar* 命名規約の AIDL 派生インタフェース";
            case "AIDL": return "AIDL ファイル由来のインタフェース";
            case "AaosApi": return "@AddedIn 等の AAOS API アノテーション付きクラス";
            case "aidl": return "AIDL 由来 (補助)";
            case "record": return "Java 16+ record 宣言";
            case "sealed": return "sealed クラス/インタフェース (permits で継承先を限定)";
            default: return stereo;
        }
    }

    static String androidStereoDesc(String stereo) {
        switch (stereo) {
            case "Activity": return "AndroidManifest.xml の <activity>";
            case "Service": return "AndroidManifest.xml の <service>";
            case "BroadcastReceiver": return "AndroidManifest.xml の <receiver>";
            case "ContentProvider": return "AndroidManifest.xml の <provider>";
            default: return stereo;
        }
    }

    static String jetpackStereoDesc(String stereo) {
        switch (stereo) {
            case "Fragment": return "androidx.fragment.app.Fragment 派生";
            case "DialogFragment": return "DialogFragment 派生";
            case "BottomSheetDialogFragment": return "Material BottomSheetDialogFragment 派生";
            case "NavHostFragment": return "Navigation Component の NavHostFragment 派生";
            case "ViewModel": return "androidx.lifecycle.ViewModel 派生";
            case "AndroidViewModel": return "androidx.lifecycle.AndroidViewModel 派生";
            case "AndroidEntryPoint": return "@AndroidEntryPoint 注入対象 (Hilt)";
            case "HiltViewModel": return "@HiltViewModel (Hilt 注入の ViewModel)";
            case "HiltAndroidApp": return "@HiltAndroidApp (Hilt のアプリ起点)";
            case "HiltModule": return "@Module + @InstallIn (Hilt モジュール)";
            case "DaggerModule": return "@Module (Dagger モジュール)";
            case "Injectable": return "@Inject コンストラクタを持つクラス";
            default: return stereo;
        }
    }

    private static void emitClass(StringBuilder out, JavaClassInfo c,
                                  Options o, String indent,
                                  java.util.Map<String, String> aliasByQn) {
        String kw = classKeyword(c);
        String stereo = stereotype(c, o);
        // ステレオタイプ末尾に埋め込まれた色 (MISSING_JAR の #LightYellow) を分離する。
        // PlantUML の class 宣言は色とリンクを両方付けるとき `[[link]] #color` の順しか
        // 受け付けず、逆順 (`#color [[link]]`) は Smetana/dot でレイアウトエラーになり
        // 描画失敗する。色は必ずリンクの後ろへ単独で出すため、ここで色を切り出しておく。
        String stereoColor = null;
        if (stereo.endsWith(" #LightYellow")) {
            stereoColor = "#LightYellow";
            stereo = stereo.substring(0, stereo.length() - " #LightYellow".length());
        }
        // フォーカス強調: 焦点=アクセント / 周辺外=淡色。色は 1 つしか付けられないため、
        // 焦点色があれば最優先し、無ければステレオタイプ由来色を使う (二重指定を防ぐ)。
        String focusColor = PlantUmlClassFocus.nodeColor(c.getQualifiedName(), o);
        String bgColor = focusColor != null ? focusColor : stereoColor;
        out.append(indent).append(kw).append(' ');
        out.append(quoteId(displayId(c, o.maxMemberTextLength)));
        String alias = aliasByQn.get(c.getQualifiedName());
        if (alias != null) {
            out.append(" as ").append(alias);
        }
        if (!stereo.isEmpty()) {
            out.append(' ').append(stereo);
        }
        // 重要 (描画失敗対策): リンク → 色 の順で出す。PlantUML は `#color [[link]]` を
        // 構文エラー扱いし、図全体が「描画できませんでした」になる (単体クラス図のフォーカス
        // 強調 + インタラクティブリンク併用時に顕在化していた)。
        if (o.interactiveLinks) {
            String prefix = o.interactiveLinkPrefix != null
                    ? o.interactiveLinkPrefix : "juml://class/";
            out.append(" [[").append(prefix).append(c.getQualifiedName()).append("]]");
        }
        if (bgColor != null) {
            out.append(' ').append(bgColor);
        }
        out.append(" {\n");
        // INLINE 表示時はクラスコメントを本体の先頭に置く
        if (o.showComments && o.commentStyle == CommentStyle.INLINE) {
            emitInlineComment(out, c.getComment(), o, indent + "  ");
        }
        if (o.showEnumConstants
                && c.getKind() == JavaClassInfo.Kind.ENUM
                && !c.getEnumConstants().isEmpty()) {
            List<String> args = c.getEnumConstantArgs();
            for (int i = 0; i < c.getEnumConstants().size(); i++) {
                out.append(indent).append("  ").append(c.getEnumConstants().get(i));
                // 定数引数 RED(255, 0, 0) を併記（添字対応・タグ誤認防止のため HTML エスケープ）。
                // 巨大な引数 (長い文字列定数など) は安全上限で切り詰めて行幅の暴走を防ぐ。
                if (i < args.size() && !args.get(i).isEmpty()) {
                    out.append(PlantUmlCommentFormatter.escapeMember(
                            args.get(i), o.maxMemberTextLength));
                }
                out.append('\n');
            }
            // 定数と他メンバーの区切り (PlantUML の区切り線)
            boolean hasOtherMembers = (o.showFields && !c.getFields().isEmpty())
                    || (o.showMethods && !c.getMethods().isEmpty());
            if (hasOtherMembers) {
                out.append(indent).append("  --\n");
            }
        }
        if (o.showFields) {
            // 定数 (static final) を先頭にまとめ、通常フィールドとの間に区切り線を入れて
            // 定数定義がひと目で分かるようにする (enum 定数の区切りと同じ流儀)。
            List<JavaFieldInfo> constants = new ArrayList<>();
            List<JavaFieldInfo> plainFields = new ArrayList<>();
            for (JavaFieldInfo f : c.getFields()) {
                if (o.publicOnly && f.getVisibility() != Visibility.PUBLIC) {
                    continue;
                }
                if (o.groupConstants && f.isStatic() && f.isFinal()) {
                    constants.add(f);
                } else {
                    plainFields.add(f);
                }
            }
            for (JavaFieldInfo f : constants) {
                if (o.showComments && o.commentStyle == CommentStyle.INLINE) {
                    emitInlineComment(out, f.getComment(), o, indent + "  ");
                }
                emitField(out, f, o, indent + "  ");
            }
            if (!constants.isEmpty() && !plainFields.isEmpty()) {
                out.append(indent).append("  ..\n");
            }
            for (JavaFieldInfo f : plainFields) {
                if (o.showComments && o.commentStyle == CommentStyle.INLINE) {
                    emitInlineComment(out, f.getComment(), o, indent + "  ");
                }
                emitField(out, f, o, indent + "  ");
            }
        }
        if (o.showMethods) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (o.showComments && o.commentStyle == CommentStyle.INLINE) {
                    emitInlineComment(out, m.getComment(), o, indent + "  ");
                }
                emitMethod(out, m, o, indent + "  ", c.getQualifiedName());
            }
        }
        // フィールド初期化子・コンストラクタ内代入で捕捉した「関数を変数として設定」する
        // 匿名クラス/ラムダ/メソッド参照の本体メソッドをフィールド単位でまとめて表示する。
        if (o.showFields && o.showMethods && o.showInlineFunctions) {
            emitFieldInlineMethods(out, c, o, indent + "  ");
        }
        out.append(indent).append("}\n");
        // NOTE 表示時はクラスの外に note ブロックを発行
        if (o.showComments && o.commentStyle == CommentStyle.NOTE && alias != null) {
            emitNoteBlocks(out, c, alias, o, indent);
        }
    }

    /** INLINE モード用のコメント行 {@code .. text ..} を 1 行発行する。空コメントは何もしない。 */
    private static void emitInlineComment(StringBuilder out, String comment,
                                           Options o, String indent) {
        if (comment == null || comment.isEmpty()) {
            return;
        }
        String line = JavaCommentScanner.firstLine(comment);
        if (line.isEmpty()) {
            return;
        }
        line = PlantUmlCommentFormatter.sanitizeInlineComment(line, o.commentMaxLength);
        out.append(indent).append(".. ");
        if (o.commentColor != null && !o.commentColor.isEmpty()) {
            out.append("<color:").append(o.commentColor).append('>')
                    .append(line)
                    .append("</color>");
        } else {
            out.append(line);
        }
        out.append(" ..\n");
    }

    /** NOTE モード: クラス・各メンバーの JavaDoc を {@code note ...} で出力。 */
    private static void emitNoteBlocks(StringBuilder out, JavaClassInfo c,
                                        String alias, Options o, String indent) {
        if (c.getComment() != null && !c.getComment().isEmpty()) {
            out.append(indent).append("note top of ").append(alias).append('\n');
            PlantUmlCommentFormatter.appendNoteBody(out, c.getComment(), indent, o.commentMaxLength);
            out.append(indent).append("end note\n");
        }
        if (o.showFields) {
            for (JavaFieldInfo f : c.getFields()) {
                if (f.getComment() == null || f.getComment().isEmpty()) {
                    continue;
                }
                if (f.getName() == null || f.getName().isEmpty()) {
                    continue;
                }
                // フィールド名に <clinit>/<init> 等の < > が含まれる場合 HTML タグと誤認されるためエスケープする。
                out.append(indent).append("note right of ").append(alias).append("::")
                        .append(PlantUmlCommentFormatter.escapeText(f.getName())).append('\n');
                PlantUmlCommentFormatter.appendNoteBody(out, f.getComment(), indent, o.commentMaxLength);
                out.append(indent).append("end note\n");
            }
        }
        if (o.showMethods) {
            // PlantUML の `note right of Class::method` はメソッド名のみで参照するため、
            // オーバーロード (同名メソッド) があると同一ターゲットへ複数 note が出て
            // 最後の 1 つしか有効にならない/構文エラーになる。メソッド名でまとめ、
            // 1 note に各オーバーロードの JavaDoc を連結する。
            Map<String, List<String>> commentsByName = new LinkedHashMap<>();
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.getComment() == null || m.getComment().isEmpty()) {
                    continue;
                }
                if (m.getName() == null || m.getName().isEmpty()) {
                    continue;
                }
                commentsByName.computeIfAbsent(m.getName(), k -> new ArrayList<>())
                        .add(m.getComment());
            }
            for (Map.Entry<String, List<String>> e : commentsByName.entrySet()) {
                // メソッド名に <init>/<clinit> 等の < > が含まれる場合 HTML タグと誤認されるためエスケープする。
                out.append(indent).append("note right of ").append(alias).append("::")
                        .append(PlantUmlCommentFormatter.escapeText(e.getKey())).append('\n');
                for (String cm : e.getValue()) {
                    PlantUmlCommentFormatter.appendNoteBody(out, cm, indent, o.commentMaxLength);
                }
                out.append(indent).append("end note\n");
            }
        }
    }

    private static String classKeyword(JavaClassInfo c) {
        switch (c.getKind()) {
            case INTERFACE: return "interface";
            case ENUM: return "enum";
            case ANNOTATION: return "annotation";
            case AIDL_INTERFACE: return "interface";
            case RECORD: return "class";
            case CLASS:
            default:
                return c.isAbstract() ? "abstract class" : "class";
        }
    }

    private static String stereotype(JavaClassInfo c, Options o) {
        List<String> parts = new ArrayList<>();
        // 外部 JAR 由来 / 解決失敗のステレオタイプを先頭に出す (視認性最優先)
        switch (c.getOrigin()) {
            case EXTERNAL_JAR:
                parts.add("external");
                break;
            case MISSING_JAR:
                parts.add("missing");
                break;
            case SOURCE:
            default:
                break;
        }
        if (o.markAaosCategories) {
            String cat = c.getAaosCategory();
            if (cat == null) {
                cat = AaosPattern.categorize(c);
            }
            if (cat != null) {
                parts.add(cat);
            }
            // API 可視性 (`@SystemApi`/`@TestApi`/JavaDoc `@hide`) と AIDL
            // binder impl を別ステレオタイプとして併記。既存カテゴリと併用可。
            String visibility = AaosPattern.apiVisibilityStereotype(c);
            if (visibility != null) {
                parts.add(visibility);
            }
            if (AaosPattern.isAidlBinderImpl(c)) {
                parts.add("binder");
            }
            // AAOS API レベルバッジ (`@ApiRequirements` / `@AddedIn` 等)
            String apiBadge = AaosPattern.apiLevelBadge(c);
            if (apiBadge != null) {
                parts.add(apiBadge);
            }
            // Car App Library (androidx.car.app.*) のベース型継承を検出
            for (String s : CarAppLibraryPattern.classify(c)) {
                if (!parts.contains(s)) {
                    parts.add(s);
                }
            }
        }
        if (c.getAndroidComponentType() != null && !c.getAndroidComponentType().isEmpty()) {
            parts.add(c.getAndroidComponentType());
        }
        if (c.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE) {
            parts.add("aidl");
        }
        if (c.getKind() == JavaClassInfo.Kind.RECORD) {
            parts.add("record");
        }
        if (c.getModifiers().contains("sealed")) {
            parts.add("sealed");
        }
        if (o.jetpack != null && o.jetpack.enabled) {
            for (String j : c.getJetpackStereotypes()) {
                if (!parts.contains(j)) {
                    parts.add(j);
                }
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append("<<").append(p).append(">>");
        }
        // MISSING_JAR には警告色を suffix で付与
        if (c.getOrigin() == JavaClassInfo.Origin.MISSING_JAR) {
            sb.append(" #LightYellow");
        }
        return sb.toString();
    }

    private static String displayId(JavaClassInfo c, int maxMemberTextLength) {
        // 表示名は装飾用 (関係はエイリアス経由)。型パラメータ Box<T> を HTML エスケープして併記。
        // 深い型パラメータで極端に長い場合は安全上限で切り詰めてノード幅の暴走を防ぐ。
        String tp = c.getTypeParameters();
        return (tp == null || tp.isEmpty()) ? c.getQualifiedName()
                : c.getQualifiedName()
                        + PlantUmlCommentFormatter.escapeMember(tp, maxMemberTextLength);
    }

    static String quoteId(String id) {
        return "\"" + id.replace("\"", "\\\"") + "\"";
    }

    private static void emitField(StringBuilder out, JavaFieldInfo f,
                                   Options o, String indent) {
        if (o.publicOnly && f.getVisibility() != Visibility.PUBLIC) {
            return;
        }
        out.append(indent);
        if (o.showVisibility) {
            out.append(f.getVisibility().mark());
        }
        if (f.isStatic()) {
            out.append("{static} ");
        }
        if (o.showFinal && f.isFinal()) {
            out.append("{final} ");
        }
        appendAnnotations(out, f.getAnnotations(), o);
        if (f.getName() != null && !f.getName().isEmpty()) {
            out.append(f.getName());
        }
        if (f.getType() != null && !f.getType().isEmpty()) {
            // ジェネリクス型 (Map<String,Integer> 等) の < > を PlantUML が
            // タグとして解釈しないよう HTML エンティティ化する。深いジェネリクスで
            // 極端に長い型は安全上限で切り詰めて行幅の暴走を防ぐ。
            out.append(": ").append(PlantUmlCommentFormatter.escapeMember(
                    f.getType(), o.maxMemberTextLength));
        }
        // static final 定数の初期化値を " = 100" のように併記する (改行は 1 行に畳む)。
        // 通常長は全文表示のまま、安全上限を超える巨大な値だけ末尾を省略する。
        if (o.showConstantValues
                && f.getConstantValue() != null && !f.getConstantValue().isEmpty()) {
            String val = f.getConstantValue().replaceAll("\\s+", " ").trim();
            out.append(" = ").append(PlantUmlCommentFormatter.escapeMember(
                    val, o.maxMemberTextLength));
        }
        out.append('\n');
    }

    /** PlantUML 行に表示するアノテーションを {@code @Foo @Bar } 形式で追記する。 */
    private static void appendAnnotations(StringBuilder out, List<String> annotations,
                                           Options o) {
        if (!o.showAnnotations || annotations == null || annotations.isEmpty()) {
            return;
        }
        for (String raw : annotations) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String name = annotationName(raw);
            if (o.hiddenAnnotations != null && o.hiddenAnnotations.contains(name)) {
                continue;
            }
            out.append('@').append(name).append(' ');
        }
    }

    /** {@code "Nullable"} や {@code "Retention(RetentionPolicy.RUNTIME)"} から名前部分を取り出す。 */
    private static String annotationName(String raw) {
        String s = raw.trim().replaceFirst("^@", ""); // Kotlin "@Foo" の先頭 @ 除去 (二重 @@ 防止)
        int paren = s.indexOf('(');
        if (paren >= 0) {
            s = s.substring(0, paren);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        return s;
    }

    /**
     * フィールドの {@code inlineMethods} (匿名クラス/ラムダ/メソッド参照の本体や、
     * メソッド本体内で {@code this.field = ...} で代入されたコールバック) を、
     * フィールド単位の区切り線 {@code .. field: Type ..} に続けて列挙する。
     * 該当フィールドがゼロなら何も出力しない。
     */
    private static void emitFieldInlineMethods(StringBuilder out, JavaClassInfo c,
                                                Options o, String indent) {
        for (JavaFieldInfo f : c.getFields()) {
            List<JavaMethodInfo> inlines = f.getInlineMethods();
            if (inlines == null || inlines.isEmpty()) {
                continue;
            }
            if (o.publicOnly && f.getVisibility() != Visibility.PUBLIC) {
                continue;
            }
            // フィールドごとの区切り線 (PlantUML: .. text ..)
            String label = f.getName() == null ? "inline" : f.getName();
            if (f.getType() != null && !f.getType().isEmpty()) {
                label = label + ": " + PlantUmlCommentFormatter.escapeMember(
                        f.getType(), o.maxMemberTextLength);
            }
            out.append(indent).append(".. ").append(label).append(" ..\n");
            for (JavaMethodInfo m : inlines) {
                // インラインメソッド (匿名クラス/ラムダ) はメソッドリンク不要
                emitMethod(out, m, o, indent, null);
            }
        }
    }

    private static void emitMethod(StringBuilder out, JavaMethodInfo m,
                                    Options o, String indent, String classFqn) {
        if (o.publicOnly && m.getVisibility() != Visibility.PUBLIC) {
            return;
        }
        out.append(indent);
        if (o.showVisibility) {
            out.append(m.getVisibility().mark());
        }
        if (m.isStatic()) {
            out.append("{static} ");
        }
        if (m.isAbstract()) {
            out.append("{abstract} ");
        }
        appendAnnotations(out, m.getAnnotations(), o);
        // メソッドレベルのジェネリック宣言 <T> を名前の前に併記する
        if (m.getTypeParameters() != null && !m.getTypeParameters().isEmpty()) {
            out.append(PlantUmlCommentFormatter.escapeMember(
                    m.getTypeParameters(), o.maxMemberTextLength)).append(' ');
        }
        // 擬似名 <clinit>/<init> は < > を含むためタグ誤認回避にエスケープする
        out.append(m.getName() == null ? "" : PlantUmlCommentFormatter.escapeText(m.getName()))
                .append('(');
        for (int i = 0; i < m.getParameterTypes().size(); i++) {
            out.append(i == 0 ? "" : ", ");
            String type = m.getParameterTypes().get(i);
            String name = i < m.getParameterNames().size()
                    ? m.getParameterNames().get(i) : "";
            if (name != null && !name.isEmpty()) {
                out.append(name).append(": ");
            }
            out.append(type == null ? "?"
                    : PlantUmlCommentFormatter.escapeMember(type, o.maxMemberTextLength));
        }
        out.append(')');
        if (!m.isConstructor() && m.getReturnType() != null && !m.getReturnType().isEmpty()) {
            out.append(": ").append(PlantUmlCommentFormatter.escapeMember(
                    m.getReturnType(), o.maxMemberTextLength));
        }
        // アノテーション属性の default 値を " = 30" のように併記する
        if (m.getDefaultValue() != null && !m.getDefaultValue().isEmpty()) {
            out.append(" = ").append(PlantUmlCommentFormatter.escapeMember(
                    m.getDefaultValue(), o.maxMemberTextLength));
        }
        // throws 例外を併記する (showThrows 有効時のみ)
        if (o.showThrows && !m.getThrowsTypes().isEmpty()) {
            out.append(" throws ");
            for (int i = 0; i < m.getThrowsTypes().size(); i++) {
                out.append(i == 0 ? "" : ", ")
                        .append(PlantUmlCommentFormatter.escapeMember(
                                m.getThrowsTypes().get(i), o.maxMemberTextLength));
            }
        }
        // interactiveLinks 有効かつ通常メソッド (非コンストラクタ) にメソッドリンクを埋め込む
        // ラベルに "▶" を使うことで URL 文字列がそのまま SVG に露出しないようにする
        // <clinit>/<init> などの合成メソッド名は '<' を含むため URL に埋め込まず除外する
        if (o.interactiveLinks
                && !m.isConstructor()
                && classFqn != null && !classFqn.isEmpty()
                && m.getName() != null && !m.getName().isEmpty()
                && !m.getName().contains("<")) {
            out.append(" [[juml://method/")
               .append(classFqn).append('#').append(m.getName()).append(" ▶]]");
        }
        out.append('\n');
    }

    /**
     * 外部ライブラリ由来クラス判定。
     * Origin が EXTERNAL_JAR / MISSING_JAR、またはパッケージ名が
     * {@link Options#externalPackagePrefixes} に前方一致する場合に true。
     */
    private static boolean isExternalClass(JavaClassInfo c, Options o) {
        JavaClassInfo.Origin origin = c.getOrigin();
        if (origin == JavaClassInfo.Origin.EXTERNAL_JAR
                || origin == JavaClassInfo.Origin.MISSING_JAR) {
            return true;
        }
        return ExternalPackageMatcher.isExternal(c.getPackageName(), o.externalPackagePrefixes);
    }

    /** クラスが {@code public} 修飾子を持つか (publicOnly フィルタ用)。 */
    private static boolean isPublicLike(JavaClassInfo c) {
        List<String> mods = c.getModifiers();
        return mods != null && mods.contains("public");
    }

    private PlantUmlClassDiagram() {
    }
}
