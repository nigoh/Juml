// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Android Car App Library ({@code androidx.car.app.*}) のクラス階層を認識し、
 * PlantUML 用ステレオタイプを返すユーティリティ。
 *
 * <p>判定はソースから取れる情報 (superClass / package) のみで行う。
 * 既存の {@link JetpackPattern} と同じく単純名末尾一致で extends を解釈する
 * ため、ユーザコードでベース型を完全修飾名・単純名どちらで書いていても
 * 拾える。多段継承 ({@code class HomeScreen extends BaseScreen} で
 * {@code BaseScreen extends Screen}) は import 解決を行わないため拾えない。
 * 本パターンは AAOS の Car App Library を想定するが、{@code Session} や
 * {@code Screen} のような汎用名と衝突しないようパッケージヒントも併用する。</p>
 */
public final class CarAppLibraryPattern {

    /**
     * Car App Library で公式に extends 想定のベース型 (単純名)。
     * 各エントリは {@code (baseSimpleName, stereotype)} の組。配列順は
     * チェック順序で、最初に当たったものを返す。
     */
    private static final List<String[]> BASES;
    static {
        List<String[]> b = new ArrayList<>();
        b.add(new String[] {"CarAppService", "CarAppService"});
        b.add(new String[] {"Session", "CarAppSession"});
        b.add(new String[] {"Screen", "CarAppScreen"});
        BASES = Collections.unmodifiableList(b);
    }

    /** Car App Library 由来クラスを示すパッケージプレフィックス。 */
    private static final List<String> PACKAGE_PREFIXES = Collections.unmodifiableList(
            Arrays.asList("androidx.car.app", "androidx.car.app.model",
                    "androidx.car.app.navigation"));

    private CarAppLibraryPattern() {
    }

    /**
     * クラスから Car App Library 系ステレオタイプ名のリストを返す。
     * 該当なしの場合は空リスト。複数候補がある場合は出現順で重複を除去する。
     *
     * <p>判定ロジック:</p>
     * <ul>
     *   <li>{@code Session} / {@code Screen} のような汎用名は、それ単独だと
     *       無関係な型と衝突しうるため、自クラス自体が {@code androidx.car.app.*}
     *       配下にあるか、もしくは superClass が完全修飾形 ({@code androidx.car.app.Screen})
     *       で書かれている場合だけ採用する。</li>
     *   <li>{@code CarAppService} は単純名自体が車載特有なので追加チェック不要。</li>
     * </ul>
     */
    public static List<String> classify(JavaClassInfo info) {
        if (info == null) {
            return Collections.emptyList();
        }
        String sup = info.getSuperClass();
        if (sup == null || sup.isEmpty()) {
            return Collections.emptyList();
        }
        String supSimple = JetpackPattern.simpleName(sup);
        if (supSimple.isEmpty()) {
            return Collections.emptyList();
        }
        boolean fqnFromCarApp = isFqnFromCarApp(sup);
        boolean inCarAppPkg = isInCarAppPackage(info.getPackageName());

        List<String> out = new ArrayList<>();
        for (String[] entry : BASES) {
            String base = entry[0];
            String stereotype = entry[1];
            if (!base.equals(supSimple)) {
                continue;
            }
            // CarAppService は車載特有の名前なので superClass マッチだけで採用
            if ("CarAppService".equals(base)) {
                out.add(stereotype);
                continue;
            }
            // Session / Screen は汎用名なので、追加ヒント
            // (superClass が FQN で androidx.car.app.* か、クラス自体が
            // androidx.car.app.* パッケージにある) があるときだけ採用
            if (fqnFromCarApp || inCarAppPkg) {
                out.add(stereotype);
            }
        }
        return out;
    }

    /**
     * superClass 文字列が {@code androidx.car.app} 系の完全修飾名で書かれているか。
     * 単純名 ({@code Screen}) や別パッケージの完全修飾名 (例:
     * {@code com.example.Screen}) では false を返す。
     */
    static boolean isFqnFromCarApp(String superClass) {
        if (superClass == null || superClass.isEmpty()) {
            return false;
        }
        String s = superClass.trim();
        int lt = s.indexOf('<');
        if (lt >= 0) {
            s = s.substring(0, lt).trim();
        }
        if (s.indexOf('.') < 0) {
            return false;
        }
        for (String p : PACKAGE_PREFIXES) {
            if (s.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }

    /** パッケージ名が {@code androidx.car.app} 系プレフィックスにマッチするか。 */
    static boolean isInCarAppPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        for (String p : PACKAGE_PREFIXES) {
            if (pkg.equals(p) || pkg.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }
}
