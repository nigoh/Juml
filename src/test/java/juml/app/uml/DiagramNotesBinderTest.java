// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * {@link DiagramNotesBinder} のストア生成 (プロジェクトルート単位のキャッシュ/再生成) を検証する。
 *
 * <p>{@code storeFor} は private だが Swing も非同期 IO も伴わない純ロジックなので、
 * リフレクションで直接呼び出して決定的に検証する（{@code bind()} 経由だと
 * {@link SvgPreviewPanel} と {@link java.util.concurrent.ExecutorService} が絡み非決定的になる）。
 * {@link DiagramNotesStore} のコンストラクタはパス構築のみで IO しないためヘッドレスで完結する。</p>
 */
public class DiagramNotesBinderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Object storeFor(DiagramNotesBinder binder, File root) throws Exception {
        Method m = DiagramNotesBinder.class.getDeclaredMethod("storeFor", File.class);
        m.setAccessible(true);
        return m.invoke(binder, root);
    }

    @Test
    public void storeFor_sameRoot_returnsCachedInstance() throws Exception {
        DiagramNotesBinder binder = new DiagramNotesBinder();
        File root = tmp.newFolder("ProjA");
        Object first = storeFor(binder, root);
        Object second = storeFor(binder, root);
        assertSame("同一ルートではストアをキャッシュして使い回すこと", first, second);
    }

    @Test
    public void storeFor_differentRoot_regeneratesStore() throws Exception {
        DiagramNotesBinder binder = new DiagramNotesBinder();
        File rootA = tmp.newFolder("ProjA");
        File rootB = tmp.newFolder("ProjB");
        Object storeA = storeFor(binder, rootA);
        Object storeB = storeFor(binder, rootB);
        assertNotSame("プロジェクト切替時はストアを作り直すこと（付箋の混入防止）", storeA, storeB);

        // 元のルートに戻すと、現在のルートと異なるため再び作り直される
        Object storeA2 = storeFor(binder, rootA);
        assertNotSame("ルートが変わるたびに作り直されること", storeB, storeA2);
    }

    @Test
    public void storeFor_nullToRoot_regenerates() throws Exception {
        DiagramNotesBinder binder = new DiagramNotesBinder();
        Object nullStore = storeFor(binder, null);
        Object rootStore = storeFor(binder, tmp.newFolder("ProjA"));
        assertNotSame("null ルートから実ルートへ切り替えると作り直されること",
                nullStore, rootStore);
    }
}
