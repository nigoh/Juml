// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidManifestInfo;
import juml.util.Messages;

import javax.swing.JLabel;
import java.text.MessageFormat;
import java.util.Locale;

/**
 * ツリーで選択された Manifest / コンポーネントをステータスバー文言へ整形する小ヘルパ (#37)。
 *
 * <p>ツリーノードを選んでも汎用 Manifest 図が開くだけで「どのノードを選んだか」が分から
 * なかったため、選択名をステータスバーへ出して図中を探す手掛かりにする。純関数として
 * 切り出し、headless で単体テストできるようにする。</p>
 */
final class TreeSelectionStatus {

    private TreeSelectionStatus() {
    }

    /** Manifest 選択のステータス文言 (パッケージ名が無ければ null)。 */
    static String forManifest(AndroidManifestInfo m) {
        if (m == null || m.getPackageName() == null || m.getPackageName().isEmpty()) {
            return null;
        }
        return MessageFormat.format(Messages.get("status.manifestSelected"), m.getPackageName());
    }

    /** コンポーネント選択のステータス文言 (種別 + 単純名。名前が無ければ null)。 */
    static String forComponent(AndroidComponentInfo c) {
        if (c == null || c.getName() == null || c.getName().isEmpty()) {
            return null;
        }
        String n = c.getName();
        String simple = n.substring(n.lastIndexOf('.') + 1);
        String kind = c.getKind() != null
                ? c.getKind().name().charAt(0)
                        + c.getKind().name().substring(1).toLowerCase(Locale.ROOT)
                : "Component";
        return MessageFormat.format(Messages.get("status.componentSelected"), kind, simple);
    }

    /** {@code text} が非 null かつ {@code label} が非 null のときだけステータスへ反映する。 */
    static void show(JLabel label, String text) {
        if (label != null && text != null) {
            label.setText(text);
        }
    }
}
