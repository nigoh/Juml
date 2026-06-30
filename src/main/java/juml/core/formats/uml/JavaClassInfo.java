// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * クラス・インタフェース・enum・@interface・AIDL interface の宣言情報。
 */
public class JavaClassInfo {

    /** 種別。 */
    public enum Kind { CLASS, INTERFACE, ENUM, ANNOTATION, AIDL_INTERFACE, RECORD, MODULE }

    /**
     * クラス情報の出所。
     * <ul>
     *   <li>{@link #SOURCE}: プロジェクトの .java / .aidl ソースから解析した既定値。</li>
     *   <li>{@link #EXTERNAL_JAR}: 依存 JAR/AAR のクラスファイル (ASM ヘッダ抽出) 経由。</li>
     *   <li>{@link #MISSING_JAR}: 参照されたが対応 JAR/AAR が発見できなかったプレースホルダ。</li>
     * </ul>
     */
    public enum Origin { SOURCE, EXTERNAL_JAR, MISSING_JAR }

    private String packageName = "";
    private String simpleName = "";
    private Kind kind = Kind.CLASS;
    private final List<String> imports = new ArrayList<>();
    private final List<String> modifiers = new ArrayList<>();
    private final List<String> annotations = new ArrayList<>();
    private String superClass;
    private final List<String> interfaces = new ArrayList<>();
    /** sealed 型の {@code permits} 列挙（継承を許可された子型）。interfaces とは別管理。 */
    private final List<String> permittedTypes = new ArrayList<>();
    private final List<JavaFieldInfo> fields = new ArrayList<>();
    private final List<JavaMethodInfo> methods = new ArrayList<>();
    private final List<String> enumConstants = new ArrayList<>();
    /** enum 定数の引数文字列（{@code ("+")} 等。引数なしは空文字）。enumConstants と添字対応。 */
    private final List<String> enumConstantArgs = new ArrayList<>();
    private String enclosingClass;
    private String aaosCategory;
    private String androidComponentType;
    private final List<String> jetpackStereotypes = new ArrayList<>();
    private String comment;
    private boolean detailed = true;
    private Origin origin = Origin.SOURCE;
    private String jarPath;
    private String sourceFile;
    private final List<JavaModuleDirective> moduleDirectives = new ArrayList<>();
    /** ジェネリック型パラメータ宣言（{@code "<T, U extends Number>"}）。なければ null。 */
    private String typeParameters;

    /** 完全修飾名。{@code com.foo.Outer.Inner} 形式。 */
    public String getQualifiedName() {
        StringBuilder sb = new StringBuilder();
        if (packageName != null && !packageName.isEmpty()) {
            sb.append(packageName).append('.');
        }
        if (enclosingClass != null && !enclosingClass.isEmpty()) {
            sb.append(enclosingClass).append('.');
        }
        sb.append(simpleName);
        return sb.toString();
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName == null ? "" : packageName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    /**
     * このソースファイルで宣言された import 文の完全修飾名 (またはワイルドカード) リスト。
     * 名前解決 ({@code juml.core.refs.NameResolver}) で単純名を FQN に解決する際に使用する。
     */
    public List<String> getImports() {
        return imports;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public String getSuperClass() {
        return superClass;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    /**
     * sealed 型が {@code permits} で明示した継承許可先の型名リスト。
     * これらは「このクラスが実装するインタフェース」ではなく「このクラスを継承し得る子型」なので、
     * {@link #getInterfaces()} とは区別する（クラス図で逆向き矢印を出さないため）。
     */
    public List<String> getPermittedTypes() {
        return permittedTypes;
    }

    public List<JavaFieldInfo> getFields() {
        return fields;
    }

    public List<JavaMethodInfo> getMethods() {
        return methods;
    }

    public boolean isAbstract() {
        return modifiers.contains("abstract") || kind == Kind.INTERFACE
                || kind == Kind.AIDL_INTERFACE;
    }

    public String getEnclosingClass() {
        return enclosingClass;
    }

    public void setEnclosingClass(String enclosingClass) {
        this.enclosingClass = enclosingClass;
    }

    public String getAaosCategory() {
        return aaosCategory;
    }

    public void setAaosCategory(String aaosCategory) {
        this.aaosCategory = aaosCategory;
    }

    /**
     * AndroidManifest.xml 上の宣言種別 (例: {@code "Activity"} / {@code "Service"} /
     * {@code "BroadcastReceiver"} / {@code "ContentProvider"})。manifest と紐付かなければ null。
     */
    public String getAndroidComponentType() {
        return androidComponentType;
    }

    public void setAndroidComponentType(String androidComponentType) {
        this.androidComponentType = androidComponentType;
    }

    /**
     * {@link JetpackPattern} 由来のステレオタイプ名リスト (例: {@code Fragment}, {@code ViewModel},
     * {@code AndroidEntryPoint})。Jetpack 解析が無効なら空のまま。
     */
    public List<String> getJetpackStereotypes() {
        return jetpackStereotypes;
    }

    /** enum 定数名のリスト (kind が ENUM の場合のみ意味を持つ)。 */
    public List<String> getEnumConstants() {
        return enumConstants;
    }

    /** ジェネリック型パラメータ宣言（{@code "<T, U extends Number>"}）。なければ null。 */
    public String getTypeParameters() {
        return typeParameters;
    }

    public void setTypeParameters(String typeParameters) {
        this.typeParameters = typeParameters;
    }

    /**
     * enum 定数の引数文字列リスト。{@link #getEnumConstants()} と添字が対応し、
     * {@code RED(255, 0, 0)} なら {@code "(255, 0, 0)"}、引数なしなら {@code ""}。
     */
    public List<String> getEnumConstantArgs() {
        return enumConstantArgs;
    }

    /** JavaDoc / 直前コメントを整形した文字列。未取得時は null。 */
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Stage B (詳細含む) としてロード済みかどうか。false の場合 fields/methods/comment 等は
     * 取得されておらず、必要なら {@link juml.core.formats.uml.ClassIndex#detail} で昇格させる。
     */
    public boolean isDetailed() {
        return detailed;
    }

    public void setDetailed(boolean detailed) {
        this.detailed = detailed;
    }

    /** クラス情報の出所。{@link Origin#SOURCE} がデフォルト。 */
    public Origin getOrigin() {
        return origin;
    }

    public void setOrigin(Origin origin) {
        this.origin = origin == null ? Origin.SOURCE : origin;
    }

    /** {@link Origin#EXTERNAL_JAR} 由来の場合に解決された JAR/AAR のパス。それ以外は null。 */
    public String getJarPath() {
        return jarPath;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    /** 解析元ソースのファイル名 (例: {@code Foo.java})。未設定時は null。 */
    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    /**
     * {@code module-info.java} に書かれた {@code requires} / {@code exports} /
     * {@code opens} / {@code uses} / {@code provides} ディレクティブの一覧。
     * {@link Kind#MODULE} のときのみ意味を持つ。
     */
    public List<JavaModuleDirective> getModuleDirectives() {
        return moduleDirectives;
    }
}
