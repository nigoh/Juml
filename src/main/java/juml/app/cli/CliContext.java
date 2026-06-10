// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.formats.uml.PlantUmlRenderer;
import juml.util.ErrorListener;

import java.io.File;

/**
 * CLI 各モードのハンドラが共通で必要とする入力・出力・描画設定をまとめた不変オブジェクト。
 *
 * <p>従来は各 {@code handleXxx} がこれら 6 つの値を個別引数として受け渡していたが、
 * 1 つにまとめることで引数渡しの重複を排し、{@code ParameterNumber} を抑える。</p>
 */
public final class CliContext {

    /** 入力ファイルまたはプロジェクトディレクトリ。引数なしなら null。 */
    public final File fileIn;
    /** 出力先。{@code -o} 未指定なら null (標準出力)。 */
    public final File fileOut;
    /** パース警告などの通知先 ({@code -v} で stderr、既定は no-op)。 */
    public final ErrorListener listener;
    /** 凡例の強制 ON/OFF。null なら各図のデフォルトに従う。 */
    public final Boolean legendOverride;
    /** クラス図の Manifest 自動マージを行うか。 */
    public final boolean mergeManifest;
    /** UML 系出力の上書きオプション束。 */
    public final UmlOverrides overrides;
    /** テストソース (src/test, tests/, *Test.java 等) を解析対象に含めるか。 */
    public final boolean includeTests;

    public CliContext(File fileIn, File fileOut, ErrorListener listener,
                      Boolean legendOverride, boolean mergeManifest,
                      UmlOverrides overrides) {
        this(fileIn, fileOut, listener, legendOverride, mergeManifest, overrides, false);
    }

    public CliContext(File fileIn, File fileOut, ErrorListener listener,
                      Boolean legendOverride, boolean mergeManifest,
                      UmlOverrides overrides, boolean includeTests) {
        this.fileIn = fileIn;
        this.fileOut = fileOut;
        this.listener = listener;
        this.legendOverride = legendOverride;
        this.mergeManifest = mergeManifest;
        this.overrides = overrides;
        this.includeTests = includeTests;
    }

    /**
     * {@code --include-tests} を反映したプロジェクト走査オプションを返す。
     * CLI ハンドラが {@code UmlGenerator.extractFromProject*} に渡す共通設定。
     */
    public juml.core.formats.java.AndroidProjectScanner.Options scanOptions() {
        juml.core.formats.java.AndroidProjectScanner.Options o =
                new juml.core.formats.java.AndroidProjectScanner.Options();
        o.includeTests = includeTests;
        return o;
    }

    /**
     * パース済みオプションから実行コンテキストを構築する。
     * {@code --preset} などの不正値があった場合は ({@link UmlOverrides#build} 内で
     * {@code System.exit(1)} した上で) null を返す。
     */
    public static CliContext from(CliOptions options) {
        File fileIn = options.arguments().isEmpty()
                ? null : requireReadable(new File(options.arguments().get(0)));
        File fileOut = options.out.getArguments().isEmpty()
                ? null : new File(options.out.getArguments().getLast());

        ErrorListener listener = options.verbose.isSet()
                ? ErrorListener.stderr() : ErrorListener.silent();
        // PlantUML 同梱 Smetana が直接 stderr に書く UNSURE_ABOUT 等のデバッグ出力を
        // 既定で抑制する。-v 時は素通しさせて Smetana 内部のログも見えるようにする。
        PlantUmlRenderer.setVerbose(options.verbose.isSet());

        // UML 凡例は既定 ON。明示的な --legend / --no-legend で上書き可。
        Boolean legendOverride = null;
        if (options.legend.isSet()) {
            legendOverride = Boolean.TRUE;
        } else if (options.noLegend.isSet()) {
            legendOverride = Boolean.FALSE;
        }
        boolean mergeManifest = !options.noManifestMerge.isSet();
        UmlOverrides overrides = UmlOverrides.build(options);
        if (overrides == null) {
            return null;
        }
        return new CliContext(fileIn, fileOut, listener,
                legendOverride, mergeManifest, overrides,
                options.includeTests.isSet());
    }

    /** 指定パスが存在する/読める File を返す。問題があれば stderr に出して System.exit(1)。 */
    private static File requireReadable(File f) {
        if (f == null) {
            return null;
        }
        if (!f.exists()) {
            System.err.println("Input not found: " + f.getPath());
            System.exit(1);
        }
        if (!f.canRead()) {
            System.err.println("Cannot read: " + f.getPath());
            System.exit(1);
        }
        return f;
    }
}
