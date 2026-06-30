// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 付箋の右クリック文脈メニューを組み立てる。{@link DiagramNotesLayer} から UI 構築を
 * 切り出したもので、各項目はレイヤのパッケージプライベートな操作メソッドへ委譲する。
 */
final class NoteContextMenu {

    /** コンテキストメニューの色見本の枠色。 */
    private static final Color BORDER = new Color(0xC9A227);

    /** 付箋色のパレット (ラベル → hex)。Color サブメニュー用。 */
    private static final Map<String, String> PALETTE = new LinkedHashMap<>();

    static {
        PALETTE.put("Yellow", "#FFF4B0");
        PALETTE.put("Green", "#D6F5C8");
        PALETTE.put("Blue", "#CFE6FF");
        PALETTE.put("Pink", "#FFD6E0");
        PALETTE.put("Gray", "#E8E8E8");
    }

    private NoteContextMenu() {
    }

    /**
     * {@code n} を右クリック対象として文脈メニューを表示する。
     * 選択は呼び出し側 ({@link DiagramNotesLayer#popup}) が確定済みとする。
     */
    static void show(DiagramNotesLayer layer, DiagramNote n, MouseEvent e, double zoom) {
        int count = layer.selectedNotes().size();
        JPopupMenu menu = new JPopupMenu();

        JMenuItem edit = new JMenuItem(Messages.get("note.menu.edit"));
        edit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        edit.setEnabled(count == 1); // 複数選択時は編集対象が曖昧なので無効化
        edit.addActionListener(a -> layer.editNote(n));
        menu.add(edit);

        JMenu color = new JMenu(Messages.get("note.menu.color"));
        for (Map.Entry<String, String> sw : PALETTE.entrySet()) {
            String label = Messages.get("note.color." + sw.getKey().toLowerCase(Locale.ROOT));
            JMenuItem mi = new JMenuItem(label,
                    new NoteSwatchIcon(NoteRenderer.parseColor(sw.getValue()), BORDER));
            mi.getAccessibleContext().setAccessibleName(label); // 色見本アイコンの読み上げ補助
            mi.addActionListener(a -> layer.setColorSelected(sw.getValue()));
            color.add(mi);
        }
        color.addSeparator();
        JMenuItem custom = new JMenuItem(Messages.get("note.color.custom"));
        custom.addActionListener(a -> layer.pickCustomColor());
        color.add(custom);
        menu.add(color);

        JMenuItem dup = new JMenuItem(Messages.get("note.menu.duplicate"));
        dup.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
        dup.addActionListener(a -> layer.duplicateSelected());
        menu.add(dup);

        JMenuItem fit = new JMenuItem(Messages.get("note.menu.fitHeight"));
        fit.addActionListener(a -> layer.fitHeightSelected());
        menu.add(fit);

        JMenuItem lock = new JMenuItem(
                Messages.get(layer.isAllSelectedLocked() ? "note.menu.unlock" : "note.menu.lock"));
        lock.addActionListener(a -> layer.toggleLockSelected());
        menu.add(lock);

        menu.addSeparator();
        JMenuItem front = new JMenuItem(Messages.get("note.menu.toFront"));
        front.addActionListener(a -> layer.bringToFront());
        menu.add(front);
        JMenuItem back = new JMenuItem(Messages.get("note.menu.toBack"));
        back.addActionListener(a -> layer.sendToBack());
        menu.add(back);

        menu.addSeparator();
        JMenuItem connect = new JMenuItem(Messages.get("note.menu.connect"));
        connect.setEnabled(count == 1); // 始点は単一の付箋
        connect.addActionListener(a -> layer.startConnectorFrom(n.getId()));
        menu.add(connect);
        if (layer.hasConnectorsTouchingSelection()) {
            JMenuItem unlink = new JMenuItem(Messages.get("note.menu.removeConnectors"));
            unlink.addActionListener(a -> layer.removeConnectorsTouchingSelection());
            menu.add(unlink);
        }

        menu.addSeparator();
        JMenuItem addHere = new JMenuItem(Messages.get("note.menu.addHere"));
        addHere.addActionListener(a -> layer.addNoteAt(e.getPoint(), zoom));
        menu.add(addHere);

        menu.addSeparator();
        JMenuItem del = new JMenuItem(
                Messages.get(count > 1 ? "note.menu.deleteSelected" : "note.menu.delete"));
        del.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        del.addActionListener(a -> layer.deleteSelected());
        menu.add(del);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }
}
