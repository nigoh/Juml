// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

/**
 * Android Runtime Resource Overlay (RRO) 1 件分の情報。
 *
 * <p>{@code AndroidManifest.xml} に {@code <overlay targetPackage="..." />}
 * が含まれる場合、そのパッケージは RRO オーバレイとして識別される。
 * 上書き先のパッケージ名 ({@code targetPackage}) と上書き元 ({@code package}) を保持。</p>
 */
public final class RroOverlay {

    private final String overlayPackage;
    private final String targetPackage;
    private final String targetName;
    private final boolean isStatic;
    private final int priority;
    private final String file;

    public RroOverlay(String overlayPackage, String targetPackage,
                       String targetName, boolean isStatic, int priority,
                       String file) {
        this.overlayPackage = nz(overlayPackage);
        this.targetPackage = nz(targetPackage);
        this.targetName = nz(targetName);
        this.isStatic = isStatic;
        this.priority = priority;
        this.file = nz(file);
    }

    /** オーバレイ自身のパッケージ名 ({@code &lt;manifest package="com.x"&gt;})。 */
    public String getOverlayPackage() { return overlayPackage; }
    /** 上書き対象のパッケージ ({@code targetPackage="com.android.systemui"})。 */
    public String getTargetPackage() { return targetPackage; }
    /** {@code targetName="..."} (任意)。 */
    public String getTargetName() { return targetName; }
    /** {@code isStatic="true"} (Static RRO) かどうか。 */
    public boolean isStatic() { return isStatic; }
    /** {@code priority="..."} 数値。未指定なら -1。 */
    public int getPriority() { return priority; }
    public String getFile() { return file; }

    private static String nz(String s) { return s == null ? "" : s; }

    @Override
    public String toString() {
        return overlayPackage + " ⇒ " + targetPackage
                + (isStatic ? " [static]" : "")
                + (priority >= 0 ? " priority=" + priority : "");
    }
}
