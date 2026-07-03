// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * 自由編集 PlantUML エディタタブのファイル IO ヘルパ (.puml の開く/保存ダイアログと
 * UTF-8 読み書き)。
 *
 * <p>{@link DiagramTabPane} 本体の肥大化を避けるため、状態を持たない静的ユーティリティを
 * ここへ切り出している。</p>
 */
final class PumlEditorSupport {

    /** .puml として扱う拡張子 (小文字)。 */
    private static final String[] PUML_EXTENSIONS = {"puml", "plantuml", "pu"};

    private PumlEditorSupport() {
    }

    /** ファイル名が PlantUML テキストの拡張子か (Open ダイアログ / ドロップ判定用)。 */
    static boolean isPumlFile(File f) {
        if (f == null) {
            return false;
        }
        String name = f.getName().toLowerCase(Locale.ROOT);
        for (String ext : PUML_EXTENSIONS) {
            if (name.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    /** 開く .puml ファイルをダイアログで選択する (キャンセル時 null)。 */
    static File choosePumlToOpen(Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(Messages.get("menubar.file.openPuml"));
        fc.setAcceptAllFileFilterUsed(true);
        fc.setFileFilter(new FileNameExtensionFilter(
                "PlantUML (*.puml, *.plantuml, *.pu)", PUML_EXTENSIONS));
        if (fc.showOpenDialog(SwingUtilities.getWindowAncestor(parent))
                != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return fc.getSelectedFile();
    }

    /**
     * 保存先の .puml ファイルをダイアログで選択する (キャンセル時 null)。
     * 既知の PlantUML 拡張子が付いていなければ {@code .puml} を補う。
     *
     * @param parent        ダイアログ親コンポーネント
     * @param suggestedName 初期ファイル名 (タブラベルなど。null 可)
     */
    static File choosePumlSaveTarget(Component parent, String suggestedName) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(Messages.get("menubar.file.savePumlAs"));
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(new FileNameExtensionFilter(
                "PlantUML (*.puml)", PUML_EXTENSIONS));
        if (suggestedName != null && !suggestedName.isEmpty()) {
            fc.setSelectedFile(new File(fc.getCurrentDirectory(),
                    ensurePumlExtension(suggestedName)));
        }
        if (fc.showSaveDialog(SwingUtilities.getWindowAncestor(parent))
                != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File chosen = fc.getSelectedFile();
        if (!isPumlFile(chosen)) {
            chosen = new File(chosen.getAbsolutePath() + ".puml");
        }
        // 拡張子補完後の最終保存先で上書き確認する (JFileChooser は確認しない上、
        // 補完後のパスはダイアログに表示されていないため)。
        if (!DialogUtils.confirmOverwrite(parent, chosen)) {
            return null;
        }
        return chosen;
    }

    /** ファイル名に PlantUML 拡張子が無ければ {@code .puml} を付けて返す。 */
    static String ensurePumlExtension(String name) {
        return isPumlFile(new File(name)) ? name : name + ".puml";
    }

    /** UTF-8 BOM (EF BB BF)。Windows Notepad 等が付けることがある。 */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * .puml テキストを UTF-8 で読み込む。先頭の UTF-8 BOM は取り除く
     * (残すと不可視の {@code ﻿} が {@code @startuml} の前に挿入され、
     * 行 1 のキャレット挙動が狂い、保存で BOM が伝播する)。
     */
    static String read(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        int offset = 0;
        if (bytes.length >= 3 && bytes[0] == UTF8_BOM[0]
                && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]) {
            offset = 3;
        }
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    /** .puml テキストを UTF-8 で書き込む (親ディレクトリが無ければ作る)。 */
    static void write(File file, String text) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory()) {
            Files.createDirectories(dir.toPath());
        }
        Files.write(file.toPath(), (text == null ? "" : text)
                .getBytes(StandardCharsets.UTF_8));
    }
}
