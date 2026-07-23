// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.AppLog;
import juml.util.ErrorCode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 自由編集 PlantUML エディタの自動保存 (下書き) ストア。
 *
 * <p>編集中テキストをタブキー単位で {@code <base>/drafts/} へ定期スナップショットし、
 * クラッシュや強制終了で失われた未保存編集を次回起動時に復元できるようにする。
 * 正常な保存・タブクローズ時には下書きを削除する (残っている = 異常終了の痕跡)。</p>
 *
 * <p>1 下書き = 本文 ({@code <hash>.puml}) + メタ情報 ({@code <hash>.properties};
 * タブキー・保存先ファイル・表示ラベル)。ファイル名はタブキーの SHA-1 で衝突を避ける。
 * 書き込みは一時ファイル経由のアトミック置換で行い、書き込み途中のクラッシュで
 * 壊れた下書きを復元してしまわないようにする。</p>
 *
 * <p>既知の制限: 複数の Juml インスタンスが同じ設定フォルダを共有すると、Untitled の
 * タブキー (連番) が衝突して互いの下書きを上書きし得る。軽減策として、復元プロンプトの
 * 辞退時は「そのとき提示した下書き」だけを破棄する (他インスタンスの生きている下書きを
 * 巻き添えにしない)。</p>
 */
final class DraftStore {

    /** 復元に必要な下書き 1 件分。 */
    static final class Draft {
        final String tabKey;
        final String text;
        /** 保存先 .puml (Untitled は null)。 */
        final File file;
        final String label;

        Draft(String tabKey, String text, File file, String label) {
            this.tabKey = tabKey;
            this.text = text;
            this.file = file;
            this.label = label;
        }
    }

    private final File dir;

    DraftStore(File dir) {
        this.dir = dir;
    }

    /** テスト用: 保存先ディレクトリ (既定保存先の場所を検証する)。 */
    File dirForTest() {
        return dir;
    }

    /**
     * 既定の保存先を使うストアを作る。保存先は OS 標準のユーザー設定領域配下
     * ({@code ~/.juml/drafts} 等、解析キャッシュ {@code ~/.juml/cache} と同じ親フォルダ)。
     *
     * <p>以前は作業ディレクトリ ({@code user.dir}) 配下の {@code drafts/} に置いていたため、
     * 別ディレクトリから起動するたびに無関係な下書きが見え、起動時の復元プロンプトが
     * 暴発していた。ユーザー単位の安定した場所へ移し、起動場所に依存せず「本当の未保存編集」
     * だけを復元対象にする。</p>
     */
    static DraftStore createDefault() {
        File cacheBase = DiskAnalysisCache.defaultBaseDir(); // ~/.juml/cache 等
        File home = cacheBase.getParentFile();               // ~/.juml 等
        return new DraftStore(new File(home != null ? home : cacheBase, "drafts"));
    }

    /** 編集中テキストをスナップショットする。失敗はログのみ (編集を妨げない)。 */
    void save(String tabKey, String text, File editorFile, String label) {
        if (tabKey == null || text == null) {
            return;
        }
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("cannot create drafts dir: " + dir);
            }
            String base = hash(tabKey);
            writeAtomically(new File(dir, base + ".puml"),
                    text.getBytes(StandardCharsets.UTF_8));
            Properties meta = new Properties();
            meta.setProperty("tabKey", tabKey);
            meta.setProperty("label", label != null ? label : "");
            if (editorFile != null) {
                meta.setProperty("file", editorFile.getAbsolutePath());
            }
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            meta.store(buf, "Juml editor draft");
            writeAtomically(new File(dir, base + ".properties"), buf.toByteArray());
        } catch (IOException ex) {
            AppLog.warn(ErrorCode.CFG_004, "DraftStore",
                    "draft snapshot failed: " + tabKey, ex);
        }
    }

    /**
     * 一時ファイルへ書いてからアトミックに置き換える。書き込み途中でクラッシュや
     * ディスクフルが起きても、壊れかけの内容が正規の下書きとして残らない。
     */
    private static void writeAtomically(File target, byte[] bytes) throws IOException {
        java.nio.file.Path tmp = target.toPath().resolveSibling(target.getName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            // アトミック移動非対応のファイルシステムでは通常置換にフォールバックする。
            Files.move(tmp, target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 指定タブの下書きを削除する (正常保存・クローズ時)。 */
    void delete(String tabKey) {
        if (tabKey == null) {
            return;
        }
        String base = hash(tabKey);
        // 存在しない場合の delete() は false を返すだけで害はない。
        new File(dir, base + ".puml").delete();
        new File(dir, base + ".properties").delete();
    }

    /** 残存する下書き (= 前回異常終了の未保存編集) をすべて読み込む。 */
    List<Draft> loadAll() {
        List<Draft> out = new ArrayList<>();
        File[] metas = dir.listFiles((d, name) -> name.endsWith(".properties"));
        if (metas == null) {
            return out;
        }
        for (File metaFile : metas) {
            try {
                Properties meta = new Properties();
                try (var is = Files.newInputStream(metaFile.toPath())) {
                    meta.load(is);
                }
                String key = meta.getProperty("tabKey");
                File body = new File(dir,
                        metaFile.getName().replaceFirst("\\.properties$", ".puml"));
                if (key == null || !body.isFile()) {
                    continue;
                }
                String text = Files.readString(body.toPath(), StandardCharsets.UTF_8);
                String path = meta.getProperty("file");
                out.add(new Draft(key, text,
                        path != null && !path.isEmpty() ? new File(path) : null,
                        meta.getProperty("label", "")));
            } catch (IOException ex) {
                AppLog.warn(ErrorCode.CFG_004, "DraftStore",
                        "draft load failed: " + metaFile.getName(), ex);
            }
        }
        return out;
    }

    /** すべての下書きを破棄する (復元辞退時)。 */
    void deleteAll() {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            f.delete();
        }
    }

    private static String hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-1 は必須アルゴリズムで欠けることはない。万一に備え素朴に無害化する。
            return key.replaceAll("[^A-Za-z0-9._-]", "_");
        }
    }
}
