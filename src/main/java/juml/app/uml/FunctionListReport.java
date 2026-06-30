// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.actions.UiActionEntry;
import juml.core.formats.android.actions.UiActionScanner;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.MethodUsageReport;
import juml.util.Messages;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Functions 一覧 (メソッド利用状況レポート) のテキスト生成を {@link UmlMainFrame} から
 * 切り出した純粋ヘルパ。
 *
 * <p>本体ファイルの肥大化 (行数上限) を避けるためにここへ集約している。UI 依存は
 * ステータス通知の {@code Consumer} 経由のみで、状態は持たない。</p>
 */
final class FunctionListReport {

    private FunctionListReport() {
    }

    /**
     * 解析済みクラス群から Functions レポートを生成する。
     * Android プロジェクトなら UI アクション (クリック→画面遷移など) も併記する。
     *
     * @param classes     対象クラス (詳細解析済み)
     * @param format      出力フォーマット (TABLE / CSV など)
     * @param projectRoot UI アクション走査の起点 (null ならスキップ)
     * @param refs        参照インデックス (呼び出し関係の解決に使用)
     * @param status      ステータスバー通知 (EDT で呼ぶ; 走査失敗の案内用)
     */
    static String build(List<JavaClassInfo> classes, MethodUsageReport.Format format,
                        File projectRoot, ReferenceIndexCache refs, Consumer<String> status) {
        List<UiActionEntry> actions = Collections.emptyList();
        if (projectRoot != null) {
            try {
                actions = new UiActionScanner().analyzeProject(projectRoot);
            } catch (IOException ex) {
                // バックグラウンドスレッドから呼ばれうるため、ステータス更新は EDT へ委譲する。
                final String msg = Messages.get("status.uiActionScanFailed") + ex.getMessage();
                SwingUtilities.invokeLater(() -> status.accept(msg));
            }
        }
        return MethodUsageReport.render(classes, refs.get(), actions, format);
    }
}
