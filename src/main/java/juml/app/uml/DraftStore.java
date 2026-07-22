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
 * タブキー・保存先ファイル・表示ラベル)。ファイル名はタブキーの SHA-1 で衝突を避ける。</p>
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

    /** 既定の保存先 ({@code <basePath>/drafts}) を使うストアを作る。 */
    static DraftStore createDefault() {
        return new DraftStore(new File(juml.util.PathUtil.getBasePath(), "drafts"));
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
            Files.write(new File(dir, base + ".puml").toPath(),
                    text.getBytes(StandardCharsets.UTF_8));
            Properties meta = new Properties();
            meta.setProperty("tabKey", tabKey);
            meta.setProperty("label", label != null ? label : "");
            if (editorFile != null) {
                meta.setProperty("file", editorFile.getAbsolutePath());
            }
            try (var os = Files.newOutputStream(new File(dir, base + ".properties").toPath())) {
                meta.store(os, "Juml editor draft");
            }
        } catch (IOException ex) {
            AppLog.warn(ErrorCode.CFG_004, "DraftStore",
                    "draft snapshot failed: " + tabKey, ex);
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
