// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * AOSP 解析の「規則が兄弟経路へ届いている」ことの回帰テスト (ラウンド 29)。
 *
 * <p>3 件とも同じ形をしている: ある規則が 1 つの経路にだけ適用され、隣に無かった。</p>
 *
 * <ul>
 *   <li>SELinux: objectClass だけ {@code stripBraces} を通し、subject / target は
 *       reluctant な文字クラス任せだった。{@code neverallow { domain -init } foo:…} の
 *       subject が {@code "{"} になり、AOSP の neverallow はほぼ全部この集合形なので
 *       実ツリーでは Subject 列が全行 {@code "{"} に潰れていた。</li>
 *   <li>AIDL backend: 「backend ブロック無し = Soong の既定 (java/cpp/ndk)」の規則が、
 *       ブロックが<b>有る</b>ときの「書かれていない言語」へ適用されていなかった。
 *       1 言語だけ調整する実 AOSP の定型 (VHAL 等) で他言語が消えていた。</li>
 *   <li>defaults 継承: srcs / deps は継承するのにスカラを継承しなかったので、
 *       {@code vendor: true} を cc_defaults に置く定型で、それを継承する HAL 実装が
 *       partitions 図で system に分類されていた。</li>
 * </ul>
 */
public class RulesReachSiblingScansTest {

    /** ブレース集合の subject / target が 1 トークンとして読めること。 */
    @Test
    public void braceSetSubjectsParseAsOneToken() {
        SelinuxPolicyParser p = new SelinuxPolicyParser();
        List<SelinuxRule> rules = p.parseSource(
                "neverallow { domain -init } hal_vehicle_default:process ptrace;\n"
                + "allow { hal_a hal_b } vehicle_device:chr_file rw_file_perms;\n",
                "t.te");
        assertEquals(2, rules.size());
        assertEquals("domain -init", rules.get(0).getSubject());
        assertEquals("hal_vehicle_default", rules.get(0).getTarget());
        assertEquals("process", rules.get(0).getObjectClass());
        assertEquals("hal_a hal_b", rules.get(1).getSubject());
        assertEquals("vehicle_device", rules.get(1).getTarget());
    }

    /** 非退行: 単一トークンの subject / target は従来どおり。 */
    @Test
    public void singleTokenRulesStillParse() {
        SelinuxPolicyParser p = new SelinuxPolicyParser();
        List<SelinuxRule> rules = p.parseSource(
                "allow single_t other_t:file { read write };\n", "t.te");
        assertEquals(1, rules.size());
        assertEquals("single_t", rules.get(0).getSubject());
        assertEquals("other_t", rules.get(0).getTarget());
        assertTrue(rules.get(0).getPermissions().contains("read"));
    }

    /** backend ブロックは「書いた言語だけを上書き」し、他言語は Soong の既定のまま。 */
    @Test
    public void aBackendBlockDoesNotDisableTheUnmentionedLanguages() {
        AndroidBpParser bp = new AndroidBpParser();
        List<AndroidBpModule> ms = bp.parseSource(
                "aidl_interface {\n"
                + "  name: \"vhal\",\n"
                + "  backend: {\n"
                + "    java: {\n      sdk_version: \"module_current\",\n    },\n"
                + "    rust: {\n      enabled: true,\n    },\n"
                + "  },\n"
                + "}\n", "Android.bp");
        assertEquals("java,cpp,ndk,rust", ms.get(0).scalar("backends"));
    }

    /** enabled: false は効き続けること (上書きの向きは両方生きる)。 */
    @Test
    public void disablingABackendStillWorks() {
        AndroidBpParser bp = new AndroidBpParser();
        List<AndroidBpModule> ms = bp.parseSource(
                "aidl_interface {\n  name: \"x\",\n"
                + "  backend: {\n    cpp: {\n      enabled: false,\n    },\n  },\n"
                + "}\n", "Android.bp");
        assertEquals("java,ndk", ms.get(0).scalar("backends"));
    }

    /** cc_defaults のスカラ (vendor 等) がモジュールへ継承され、配置分類に効くこと。 */
    @Test
    public void scalarPropertiesAreInheritedFromDefaults() {
        AndroidBpParser bp = new AndroidBpParser();
        List<AndroidBpModule> mods = new ArrayList<>();
        mods.addAll(bp.parseSource(
                "cc_defaults {\n  name: \"hal_defaults\",\n  vendor: true,\n}\n", "a.bp"));
        mods.addAll(bp.parseSource(
                "cc_binary {\n  name: \"vhal_impl\",\n  defaults: [\"hal_defaults\"],\n}\n",
                "b.bp"));
        AndroidBpParser.resolveDefaults(mods);
        AndroidBpModule impl = mods.stream()
                .filter(m -> m.getName().equals("vhal_impl")).findFirst().orElseThrow();
        assertTrue("vendor が継承されること", impl.boolProp("vendor"));
        assertEquals("配置分類が vendor になること", "vendor", impl.getPartition());
    }

    /** モジュール自身の宣言は defaults に負けないこと (Soong と同じ優先順位)。 */
    @Test
    public void aModulesOwnScalarBeatsTheDefaults() {
        AndroidBpParser bp = new AndroidBpParser();
        List<AndroidBpModule> mods = new ArrayList<>();
        mods.addAll(bp.parseSource(
                "cc_defaults {\n  name: \"d\",\n  sdk_version: \"29\",\n}\n", "a.bp"));
        mods.addAll(bp.parseSource(
                "cc_library {\n  name: \"lib\",\n  defaults: [\"d\"],\n"
                + "  sdk_version: \"current\",\n}\n", "b.bp"));
        AndroidBpParser.resolveDefaults(mods);
        AndroidBpModule lib = mods.stream()
                .filter(m -> m.getName().equals("lib")).findFirst().orElseThrow();
        assertEquals("current", lib.scalar("sdk_version"));
    }
}
