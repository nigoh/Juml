// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.explore;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import java.awt.Dimension;

/**
 * 解析パネル共通の「キャンセルボタン + 不定進捗バー」を束ねる小さなヘルパ。
 *
 * <p>各パネルはツールバーに {@link #cancelButton()} と {@link #progressBar()} を並べ、
 * 解析開始時に {@link #started(SwingWorker)}、終了時 (done の finally) に
 * {@link #finished()} を呼ぶだけでよい。実行中の {@link SwingWorker} を保持し、
 * キャンセル要求を橋渡しする。</p>
 */
final class AnalysisRunControls {

    private final JButton cancelButton;
    private final JProgressBar progress;
    private SwingWorker<?, ?> active;

    AnalysisRunControls() {
        cancelButton = new JButton(Messages.get("analysis.cancel"));
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> {
            if (active != null) {
                active.cancel(true);
            }
        });
        progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(120, 16));
    }

    JButton cancelButton() {
        return cancelButton;
    }

    JProgressBar progressBar() {
        return progress;
    }

    /** 解析開始: 進捗バーを見せ、キャンセルボタンを有効化する。 */
    void started(SwingWorker<?, ?> worker) {
        active = worker;
        cancelButton.setEnabled(true);
        progress.setVisible(true);
    }

    /** 解析終了 (成功/失敗/キャンセル問わず): 進捗バーを隠しキャンセルを無効化する。 */
    void finished() {
        active = null;
        cancelButton.setEnabled(false);
        progress.setVisible(false);
    }
}
