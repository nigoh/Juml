// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 図タブの付箋メモを {@link DiagramNotesStore} に結び付けるヘルパ。
 *
 * <p>{@link DiagramTabPane} 本体の肥大化を避けるため、ストアの生成 (プロジェクト
 * ルート単位) と各タブの {@link SvgPreviewPanel} へのロード/保存配線をここに集約する。</p>
 *
 * <p>ファイル IO (ロード/保存) は単一スレッドの {@link ExecutorService} に逃がして
 * EDT をブロックしない。保存は変更のたびに投入されるが単一スレッドで直列化されるため
 * 順序は保たれる。</p>
 */
final class DiagramNotesBinder {

    /** 付箋ファイル IO 用のデーモンスレッド (EDT を止めない)。 */
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "juml-notes-io");
        t.setDaemon(true);
        return t;
    });

    private DiagramNotesStore store;
    private File storeRoot;
    /** ステータスバー通知 (保存失敗・ロード件数)。null 可。 */
    private final Consumer<String> status;
    /** 保存失敗メッセージの乱発抑制 (一度出したら次の成功までは再表示しない)。 */
    private final AtomicBoolean saveFailed = new AtomicBoolean(false);

    DiagramNotesBinder() {
        this(null);
    }

    DiagramNotesBinder(Consumer<String> status) {
        this.status = status;
    }

    private void report(String msg) {
        if (status != null) {
            SwingUtilities.invokeLater(() -> status.accept(msg));
        }
    }

    /** 現在のプロジェクトルートに対応するストア (ルート変更時は作り直す)。 */
    private synchronized DiagramNotesStore storeFor(File projectRoot) {
        if (store == null || !Objects.equals(projectRoot, storeRoot)) {
            store = new DiagramNotesStore(projectRoot);
            storeRoot = projectRoot;
        }
        return store;
    }

    /**
     * IO スレッドを停止し、投入済みの保存タスクが完了するまで短時間待つ。
     *
     * <p>IO スレッドはデーモンのため、アプリ終了時にここを呼ばないと
     * キュー内の保存タスクがドロップされ付箋メモが失われうる。
     * ウィンドウを閉じる直前 (dispose 前) に呼ぶこと。</p>
     */
    void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 指定タブのプレビューに保存済み付箋をロードし、変更時に保存するよう配線する。
     * ロード/保存はバックグラウンドスレッドで行い EDT を止めない。
     *
     * @param preview     対象タブの SVG プレビュー
     * @param projectRoot プロジェクトルート (null なら永続化されない)
     * @param diagramKey  図タブの識別キー (図種 + 題材)
     */
    /**
     * プレビューごとの現在のバインド世代トークン。非同期ロードの完了時に照合し、
     * その間に再バインド (図種切替・Save As のキー移行) が起きていたら古いロード結果を
     * 捨てる。捨てないと別キーの付箋が現在のタブへ注入され、次の保存で誤ったキーへ
     * 永続化される (図をまたいだ付箋の混線)。
     */
    private final Map<SvgPreviewPanel, Object> bindToken =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** プレビューごとの現在の保存先ルート (再バインド判断に使う)。 */
    private final Map<SvgPreviewPanel, File> boundRoot =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * プロジェクト切替に伴う再バインド。<b>保存先を移してよいときだけ</b>移す。
     *
     * <p>{@link #bind} は保存フックを新ストアへ差し替えるが、ロード側は
     * 「レイヤが空のときだけ反映する」規則しか持っていない。つまり規則が掛かって
     * いるのは<b>ロード経路だけ</b>で、保存経路には掛かっていなかった。旧プロジェクトの
     * 付箋が載ったままのタブを無条件に再バインドすると、切替先の保存済み付箋は
     * (レイヤが空でないので) 読み込まれず、その状態で付箋を 1 つ触った瞬間に
     * <b>画面上の旧プロジェクトの付箋一覧が切替先の .juml/notes.json を丸ごと上書き</b>
     * する。タブキーは Untitled エディタでは再利用されるため衝突は現実に起きるし、
     * 衝突しなくても他プロジェクトのメモ本文が (git 共有前提の) ファイルへ混入する。</p>
     *
     * <p>判断は 1 つ: <b>画面の付箋が既に別プロジェクトのものなら、保存先は移さない</b>。
     * 空なら移して切替先の付箋を読み込む。まだ一度も実プロジェクトへ束ねていない
     * (プロジェクト未ロードで作った) 付箋は<b>どこのものでもない</b>ので、
     * 切替先が引き取ってよい — これが「プロジェクトを開く前に書いたメモ」の正規の導線である。</p>
     */
    void rebindForProject(SvgPreviewPanel preview, File newRoot, String diagramKey) {
        File previous = boundRoot.get(preview);
        if (previous != null && !Objects.equals(previous, newRoot)
                && preview.notes().hasNotes()) {
            return;
        }
        bind(preview, newRoot, diagramKey);
    }

    void bind(SvgPreviewPanel preview, File projectRoot, String diagramKey) {
        final DiagramNotesStore s = storeFor(projectRoot);
        final Object token = new Object();
        bindToken.put(preview, token);
        if (projectRoot != null) {
            boundRoot.put(preview, projectRoot);
        }
        // 変更時はバックグラウンドで保存 (移動・リサイズ・削除・色変更時の EDT フリーズ防止)。
        // スナップショットは EDT 上で深いコピーを取る。ライブオブジェクトを IO スレッドで
        // 直列化すると、ドラッグ中の座標変更やタグ変更と競合して不正な値が保存されうる。
        preview.notes().setOnChange(() -> {
            List<DiagramNote> snapshot = deepCopy(preview.notes().getNotes());
            List<DiagramConnector> connectors = preview.notes().getConnectors();
            io.submit(() -> {
                boolean ok = s.save(diagramKey, snapshot, connectors);
                if (!ok) {
                    if (saveFailed.compareAndSet(false, true)) {
                        report(Messages.get("note.save.failed"));
                    }
                } else if (saveFailed.compareAndSet(true, false)) {
                    report(Messages.get("note.save.recovered"));
                }
            });
        });
        // 既存付箋 + コネクタをバックグラウンドでロードして EDT で反映。
        io.submit(() -> {
            List<DiagramNote> loaded = s.load(diagramKey);
            List<DiagramConnector> loadedConns = s.loadConnectors(diagramKey);
            if (!loaded.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    // 再バインド済み、またはロード完了前にユーザーが付箋を追加していたら
                    // 反映しない (直前の編集を黙って消さない)。
                    if (bindToken.get(preview) == token && !preview.notes().hasNotes()) {
                        preview.notes().setData(loaded, loadedConns);
                        report(Messages.get("note.loaded") + loaded.size());
                    }
                });
            }
        });
    }

    /**
     * 図キーの変更 (Save As のタブキー移行) に合わせて保存エントリを新キーへ移す。
     * IO スレッドで実行するため EDT を止めない。
     */
    void renameKey(File projectRoot, String oldKey, String newKey) {
        final DiagramNotesStore s = storeFor(projectRoot);
        io.submit(() -> s.rename(oldKey, newKey));
    }

    private static List<DiagramNote> deepCopy(List<DiagramNote> notes) {
        List<DiagramNote> out = new java.util.ArrayList<>(notes.size());
        for (DiagramNote n : notes) {
            out.add(n.copyDeep());
        }
        return out;
    }
}
