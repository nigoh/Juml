// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Android {@code res/layout/*.xml} 内の 1 つの View 要素を表すノード。
 *
 * <p>{@link AndroidLayoutParser} が DOM を再帰的に走査して構築する。
 * 頻出属性 (id / text / contentDescription / width / height) は専用フィールドに格納し、
 * それ以外の属性は {@link #getExtraAttributes()} に文字列として保持する。</p>
 *
 * <p>特殊要素はステレオタイプで識別する:</p>
 * <ul>
 *   <li>{@link Kind#INCLUDE} — {@code <include layout="@layout/foo">}</li>
 *   <li>{@link Kind#FRAGMENT} — {@code <fragment android:name="...">}</li>
 *   <li>{@link Kind#MERGE} — {@code <merge>} (ルート専用)</li>
 *   <li>{@link Kind#VIEW_GROUP} — 子を持つ View</li>
 *   <li>{@link Kind#VIEW} — リーフ View</li>
 * </ul>
 */
public class LayoutViewNode {

    /** ノード種別。図化時のステレオタイプ振り分けに使う。 */
    public enum Kind {
        VIEW,
        VIEW_GROUP,
        INCLUDE,
        FRAGMENT,
        MERGE
    }

    private final String tag;
    private String id;
    private String text;
    private String contentDescription;
    private String width;
    private String height;
    private String includeLayoutRef;
    private String fragmentClassName;
    private final Map<String, String> extraAttributes = new LinkedHashMap<>();
    private final List<LayoutViewNode> children = new ArrayList<>();

    public LayoutViewNode(String tag) {
        this.tag = tag == null ? "" : tag;
    }

    public String getTag() {
        return tag;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getContentDescription() {
        return contentDescription;
    }

    public void setContentDescription(String contentDescription) {
        this.contentDescription = contentDescription;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getIncludeLayoutRef() {
        return includeLayoutRef;
    }

    public void setIncludeLayoutRef(String includeLayoutRef) {
        this.includeLayoutRef = includeLayoutRef;
    }

    public String getFragmentClassName() {
        return fragmentClassName;
    }

    public void setFragmentClassName(String fragmentClassName) {
        this.fragmentClassName = fragmentClassName;
    }

    public Map<String, String> getExtraAttributes() {
        return extraAttributes;
    }

    public List<LayoutViewNode> getChildren() {
        return children;
    }

    /** タグ名から大まかな種別を判定する。子の有無は後で更新するために本メソッドでは見ない。 */
    public Kind classify() {
        if ("include".equals(tag)) {
            return Kind.INCLUDE;
        }
        if ("fragment".equals(tag)) {
            return Kind.FRAGMENT;
        }
        if ("merge".equals(tag)) {
            return Kind.MERGE;
        }
        return children.isEmpty() ? Kind.VIEW : Kind.VIEW_GROUP;
    }

    /** {@code android:id="@+id/foo"} → {@code foo} の短縮形を返す。id 未設定なら null。 */
    public String shortId() {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int slash = id.lastIndexOf('/');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }

    /** {@code android.widget.TextView} → {@code TextView}、{@code TextView} はそのまま。 */
    public String shortTag() {
        int dot = tag.lastIndexOf('.');
        return dot >= 0 ? tag.substring(dot + 1) : tag;
    }
}
