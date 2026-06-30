// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.ProjectRecord;
import juml.ProjectRepository;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * プロジェクト未ロード時に中央へ表示する「ようこそ (Welcome)」空状態パネル。
 *
 * <p>初見ユーザーが最初の図にたどり着けるよう、(1) プロジェクトを開く / (2) アーカイブを開く /
 * (3) 最近開いたプロジェクトから再開する、の導線を 1 画面にまとめる。プロジェクトが
 * ロードされたら {@link UmlMainFrame} がワークスペース (ツリー + タブ) 表示へ切り替える。</p>
 *
 * <p>VS Code 風タブモデルの「Home タブ」ではない: 図のロジックは持たず、純粋な入口導線のみ。</p>
 */
final class WelcomePanel extends JPanel {

    private final Consumer<File> onOpenRecent;
    private final JPanel recentList = new JPanel();

    WelcomePanel(Runnable onOpenProject, Runnable onOpenArchive, Consumer<File> onOpenRecent) {
        super(new GridBagLayout());
        this.onOpenRecent = onOpenRecent;

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        JLabel title = new JLabel(Messages.get("welcome.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 14f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);

        JLabel subtitle = new JLabel(Messages.get("welcome.subtitle"));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 18, 0));
        card.add(subtitle);

        JButton openProject = new JButton(Messages.get("menubar.file.open"),
                MaterialIcons.toolbar(MaterialIcons.Glyph.FOLDER_OPEN));
        openProject.setIconTextGap(8);
        openProject.addActionListener(e -> onOpenProject.run());
        openProject.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(openProject);
        card.add(Box.createVerticalStrut(8));

        JButton openArchive = new JButton(Messages.get("menubar.file.openArchive"),
                MaterialIcons.toolbar(MaterialIcons.Glyph.ARCHIVE));
        openArchive.setIconTextGap(8);
        openArchive.addActionListener(e -> onOpenArchive.run());
        openArchive.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(openArchive);

        JLabel recentHeading = new JLabel(Messages.get("welcome.recent"));
        recentHeading.setFont(recentHeading.getFont().deriveFont(Font.BOLD));
        recentHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        recentHeading.setBorder(BorderFactory.createEmptyBorder(22, 0, 6, 0));
        card.add(recentHeading);

        recentList.setLayout(new BoxLayout(recentList, BoxLayout.Y_AXIS));
        recentList.setAlignmentX(Component.LEFT_ALIGNMENT);
        JScrollPane recentScroll = new JScrollPane(recentList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        recentScroll.setBorder(BorderFactory.createEmptyBorder());
        recentScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        recentScroll.setPreferredSize(new Dimension(460, 200));
        recentScroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(recentScroll);

        add(card, new GridBagConstraints());
        refreshRecent();
    }

    /** 最近開いたプロジェクト一覧を再構築する (Welcome 表示時に呼ぶ)。 */
    void refreshRecent() {
        recentList.removeAll();
        List<ProjectRecord> records;
        try {
            records = ProjectRepository.getInstance().listRecent(8);
        } catch (RuntimeException ex) {
            records = List.of(); // リポジトリ未初期化 (テスト等) では空表示
        }
        if (records.isEmpty()) {
            JLabel none = new JLabel(Messages.get("menubar.file.recentNone"));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            none.setEnabled(false);
            recentList.add(none);
        } else {
            for (ProjectRecord r : records) {
                recentList.add(recentEntry(r));
            }
        }
        recentList.revalidate();
        recentList.repaint();
    }

    private JComponent recentEntry(ProjectRecord r) {
        File root = r.root();
        // 名前 + パスを 2 行で見せる link 風ボタン。存在しないパスは淡色＋無効化。
        boolean exists = root.isDirectory();
        String label = "<html><b>" + escape(r.getName()) + "</b><br>"
                + "<span style='font-size:90%;color:" + subtleHex() + "'>" + escape(r.getPath())
                + (exists ? "" : "  (missing)") + "</span></html>";
        JButton b = new JButton(label);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
        b.setToolTipText(r.getPath());
        b.setEnabled(exists);
        if (exists) {
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> onOpenRecent.accept(root));
        }
        return b;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** テーマの淡色 (パス表示用) を HTML の #RRGGBB 文字列で返す。ダーク時も追従する。 */
    private static String subtleHex() {
        java.awt.Color c = javax.swing.UIManager.getColor("Label.disabledForeground");
        if (c == null) {
            c = new java.awt.Color(0x888888);
        }
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
