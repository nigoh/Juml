// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

import juml.app.cli.CliContext;
import juml.app.cli.CliDispatcher;
import juml.app.cli.CliOptions;
import juml.app.uml.UmlApp;
import juml.core.formats.doxygen.DoxygenLocator;
import juml.core.formats.uml.GraphvizLocator;
import juml.core.formats.uml.PlantUmlRenderer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * エントリポイントクラス。
 *
 * <p>初期化のあと {@link CliOptions} で引数をパースし、{@link CliDispatcher} へ委譲する。
 * どの CLI モードにも該当しなければ UML 専用 GUI を起動する。各出力モードの実体は
 * {@code juml.app.cli} 配下のコマンドクラスにある。</p>
 */
public class Main {

    /**
     * Settingを取得する（SettingManager経由）
     */
    public static Setting getSetting() {
        return SettingManager.getInstance().getSetting();
    }

    /**
     * Settingを保存する（SettingManager経由）
     */
    public static void saveSetting() {
        SettingManager.getInstance().save();
    }

    /**
     * 実行中の jar が置かれているディレクトリを返す。不明なら null。
     *
     * <p>jpackage の app-image やカスタム ClassLoader 経由の起動では
     * {@code getCodeSource()} からパスが取れないことがあるため、
     * {@code -Djuml.home=<dir>} システムプロパティ →
     * {@code JUML_HOME} 環境変数の順でフォールバックする。
     * 解決できなかった場合はログに残す (同梱 dot/doxygen の検出が
     * カレントディレクトリ基点のみになり、静かに機能低下するのを防ぐため)。</p>
     */
    private static File detectJarDir() {
        File fromCodeSource = null;
        try {
            URL loc = Main.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                File f = new File(loc.toURI());
                fromCodeSource = f.isFile() ? f.getParentFile() : f;
            }
        } catch (URISyntaxException | IllegalArgumentException | SecurityException e) {
            juml.util.AppLog.info("Main",
                    "jar location unresolved from code source: " + e);
        }
        if (fromCodeSource != null && fromCodeSource.isDirectory()) {
            return fromCodeSource;
        }
        String home = System.getProperty("juml.home", System.getenv("JUML_HOME"));
        if (home != null && !home.isEmpty()) {
            File dir = new File(home);
            if (dir.isDirectory()) {
                juml.util.AppLog.info("Main", "jar dir resolved via juml.home/JUML_HOME: " + dir);
                return dir;
            }
        }
        juml.util.AppLog.info("Main",
                "jar dir not resolved — bundled dot/doxygen lookup falls back to cwd");
        return null;
    }

    /**
     * エントリポイント
     * @param args 引数
     */
    public static void main(String[] args) throws IOException {
        // 最初にロギング基盤を初期化する (未捕捉例外ハンドラ + System.err ティー設置)。
        // 以降に発生したエラーは GUI のログビューア / logs/juml.log で確認できる。
        juml.util.AppLog.init();
        // 専用サブコマンドの早期分岐 (option parser を経由しない)。
        // 既存の "java -jar Juml.jar -c <path>" のような呼び出しは args[0] が "-c"
        // 等のオプション or 入力パスになるので、ここでは "index" だけを intercept する。
        if (args.length > 0 && "index".equals(args[0])) {
            int code = juml.app.cli.IndexCommand.execute(
                    args, juml.util.ErrorListener.stderr());
            if (code != 0) {
                System.exit(code);
            }
            return;
        }

        // SettingManager / ProjectRepository を初期化し、永続化されたスタイルをレンダラへ反映
        SettingManager.initialize();
        ProjectRepository.initialize();
        PlantUmlRenderer.setStyle(SettingManager.getInstance().getSetting().getStyle());
        PlantUmlRenderer.configureImageLimit();
        // 永続化された図プレビューの描画品質 (HiDPI 追従のラスタライズ解像度) を反映。
        juml.app.uml.DiagramRenderQuality.setCurrent(
                juml.app.uml.DiagramRenderQuality.fromKey(
                        SettingManager.getInstance().getSetting().getDiagramRenderQuality()));
        // タブ上限設定をシステムプロパティに反映 (TabMemoryManager が参照)。
        Setting tabSetting = SettingManager.getInstance().getSetting();
        System.setProperty("juml.maxDiagramTabs",
                Integer.toString(tabSetting.getMaxDiagramTabs()));
        System.setProperty("juml.renderedTabs",
                Integer.toString(tabSetting.getRenderedTabs()));
        File jarDir = detectJarDir();
        GraphvizLocator.init(jarDir);
        DoxygenLocator.init(jarDir);

        CliOptions options = new CliOptions();
        options.parse(args);
        if (options.helpRequested()) {
            options.printUsage();
            System.exit(1);
        }

        CliContext ctx = CliContext.from(options);
        if (ctx == null) {
            return; // 引数エラー: CliContext.from 内で System.exit 済み
        }

        if (CliDispatcher.dispatch(options, ctx)) {
            return;
        }

        if (ctx.fileOut != null) {
            System.err.println("-o requires one of: -c / -q / -d / -G / -g / -m / -M / -D"
                    + " / --summary / -A / -Q / --list-methods");
            System.exit(1);
            return;
        }
        // 既定: UML 専用 GUI を起動。引数があれば初期プロジェクトとして渡す。
        // 引数指定が無い場合のみ、設定に応じて前回プロジェクトの復元を試みる。
        File initialProject = ctx.fileIn;
        if (initialProject == null) {
            initialProject = restoreLastProjectIfEnabled();
        }
        UmlApp.launch(initialProject);
    }

    /**
     * 設定で「起動時に前回プロジェクトを復元」が有効なとき、最後に開いたプロジェクトの
     * ルートを返す。無効・履歴なし・ディレクトリ消失時は null。
     */
    private static File restoreLastProjectIfEnabled() {
        try {
            if (!getSetting().isRestoreLastProjectOnStartup()) {
                return null;
            }
            java.util.List<ProjectRecord> recent =
                    ProjectRepository.getInstance().listRecent(1);
            if (recent.isEmpty()) {
                return null;
            }
            File root = recent.get(0).root();
            return root.isDirectory() ? root : null;
        } catch (RuntimeException ex) {
            // 復元はベストエフォート (設定/リポジトリ未初期化など)
            return null;
        }
    }
}
