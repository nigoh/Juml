// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import java.util.Objects;

/**
 * VHAL (Vehicle Hardware Abstraction Layer) アクセス 1 件分の情報。
 *
 * <p>{@code CarPropertyManager.getProperty/setProperty/registerCallback/unregisterCallback}
 * の呼び出しを {@link VhalAnalyzer} が検出して 1 つの {@code VhalAccess} を生成する。</p>
 *
 * <p>{@link #getPropertyToken} は呼び出し引数の最初のトークン (例: {@code HVAC_FAN_SPEED}
 * や {@code VehiclePropertyIds.PERF_VEHICLE_SPEED}) をそのまま保持する。
 * 後段の {@link VehiclePropertyCatalog} と組み合わせて整数 ID にマップする。</p>
 */
public final class VhalAccess {

    /** アクセス種別。 */
    public enum Kind {
        /** {@code getProperty(...)} — プロパティ値の取得。 */
        GET,
        /** {@code setProperty(...)} — プロパティ値の書き込み。 */
        SET,
        /** {@code registerCallback(...)} — 変更通知の購読開始。 */
        SUBSCRIBE,
        /** {@code unregisterCallback(...)} — 変更通知の購読解除。 */
        UNSUBSCRIBE
    }

    private final String callerFqn;
    private final String callerMethod;
    private final String file;
    private final int lineHint;
    private final Kind kind;
    private final String propertyToken;
    private final String areaToken;

    public VhalAccess(String callerFqn, String callerMethod, String file,
                       int lineHint, Kind kind, String propertyToken,
                       String areaToken) {
        this.callerFqn = nz(callerFqn);
        this.callerMethod = nz(callerMethod);
        this.file = nz(file);
        this.lineHint = lineHint;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.propertyToken = nz(propertyToken);
        this.areaToken = nz(areaToken);
    }

    public String getCallerFqn() { return callerFqn; }
    public String getCallerMethod() { return callerMethod; }
    public String getFile() { return file; }
    public int getLineHint() { return lineHint; }
    public Kind getKind() { return kind; }

    /** 第 1 引数のソーステキスト ({@code HVAC_FAN_SPEED} や {@code 12345} など)。 */
    public String getPropertyToken() { return propertyToken; }

    /** 第 2 引数のソーステキスト (area ID 等)。無ければ空文字。 */
    public String getAreaToken() { return areaToken; }

    /** プロパティ短縮名 ({@code Foo.HVAC_FAN_SPEED} → {@code HVAC_FAN_SPEED})。 */
    public String getPropertyShortName() {
        int dot = propertyToken.lastIndexOf('.');
        return dot < 0 ? propertyToken : propertyToken.substring(dot + 1);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @Override
    public String toString() {
        return kind + "(" + propertyToken + ") @ "
                + callerFqn + "." + callerMethod
                + (file.isEmpty() ? "" : " (" + file + ":" + lineHint + ")");
    }
}
