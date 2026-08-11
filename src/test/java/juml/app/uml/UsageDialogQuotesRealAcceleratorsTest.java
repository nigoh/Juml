// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;
import org.junit.Test;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * ヘルプ &gt; 使い方 (F1) が案内するショートカットが、<b>実際のキーバインドと一致する</b>
 * ことの回帰テスト。
 *
 * <p>「後からコマンドに Shift を足したが、それを案内している文言を直し忘れる」という
 * ずれが繰り返し起きている。ツールバーのツールチップ側は直したのに、同じことを言う
 * <b>もう 1 か所</b>である使い方ダイアログには {@code Ctrl+F} が残っていた —
 * {@code Ctrl+F} は「図内を検索」に割り当てられているので、案内どおり押すと
 * <b>別のコマンドが走る</b>。</p>
 *
 * <p>1 行を固定するのではなく、本文に書かれた全ショートカットを実メニューの
 * アクセラレータと突き合わせる。こうしておけば、次に誰かがバインドを変えたときに
 * 文言だけ取り残されても落ちる。</p>
 */
public class UsageDialogQuotesRealAcceleratorsTest {

    /** {@code  - Diagram > Search Entities... (Ctrl+Shift+F): 説明} を拾う。 */
    private static final Pattern LINE = Pattern.compile(
            "^\\s*-\\s*(.+?)\\s*\\((Ctrl\\+[^)]*)\\)\\s*:", Pattern.MULTILINE);

    /** {@code Ctrl+Shift+F} → KeyStroke 相当の記述へ正規化する。 */
    private static String normalize(String spec) {
        return spec.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** 実メニューを組み立てて「メニュー項目ラベル → アクセラレータ」を集める。 */
    private static Map<String, String> actualAccelerators() throws Exception {
        MenuBarBuilder.Callbacks cb = new MenuBarBuilder.Callbacks();
        // Callbacks の Runnable / Consumer 等をすべて無害な no-op で埋める。
        // 1 つでも null が残ると build() が落ちる。
        for (Field f : MenuBarBuilder.Callbacks.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !f.getType().isInterface()) {
                continue;
            }
            f.set(cb, java.lang.reflect.Proxy.newProxyInstance(
                    MenuBarBuilder.class.getClassLoader(), new Class<?>[] {f.getType()},
                    (proxy, method, args) -> defaultFor(method.getReturnType())));
        }
        JMenuBar bar = new MenuBarBuilder(
                DiagramKind.CLASS, InputEvent.CTRL_DOWN_MASK, cb, null).build().menuBar;
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            collect(bar.getMenu(i), out);
        }
        return out;
    }

    private static Object defaultFor(Class<?> t) {
        if (!t.isPrimitive()) {
            return null;
        }
        if (t == boolean.class) {
            return false;
        }
        if (t == int.class) {
            return 0;
        }
        if (t == long.class) {
            return 0L;
        }
        if (t == double.class) {
            return 0.0;
        }
        return null;
    }

    private static void collect(JMenu menu, Map<String, String> out) {
        if (menu == null) {
            return;
        }
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item == null) {
                continue;
            }
            if (item instanceof JMenu) {
                collect((JMenu) item, out);
                continue;
            }
            KeyStroke ks = item.getAccelerator();
            if (ks != null && item.getText() != null) {
                out.put(item.getText(), describe(ks));
            }
        }
    }

    /** KeyStroke を {@code Ctrl+Shift+F} 形式へ。 */
    private static String describe(KeyStroke ks) {
        StringBuilder sb = new StringBuilder();
        int mod = ks.getModifiers();
        if ((mod & InputEvent.CTRL_DOWN_MASK) != 0 || (mod & InputEvent.META_DOWN_MASK) != 0) {
            sb.append("Ctrl+");
        }
        if ((mod & InputEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("Shift+");
        }
        if ((mod & InputEvent.ALT_DOWN_MASK) != 0) {
            sb.append("Alt+");
        }
        sb.append(java.awt.event.KeyEvent.getKeyText(ks.getKeyCode()));
        return sb.toString();
    }

    @Test
    public void everyShortcutInTheUsageDialogMatchesItsRealBinding() throws Exception {
        assumeFalse("headless ではメニューを組み立てられない",
                GraphicsEnvironment.isHeadless());
        Map<String, String> actual = actualAccelerators();
        String body = MessageFormat.format(Messages.get("dlg.usage.body"), "Ctrl");

        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        Matcher m = LINE.matcher(body);
        while (m.find()) {
            // "Diagram > Search Entities..." の最後の要素がメニュー項目のラベル。
            String path = m.group(1);
            int gt = path.lastIndexOf('>');
            String label = (gt >= 0 ? path.substring(gt + 1) : path).trim();
            String quoted = m.group(2).trim();
            String real = actual.get(label);
            if (real == null) {
                continue; // メニュー項目ではない行 (パレット等) はここでは見ない
            }
            checked++;
            if (!normalize(real).equals(normalize(quoted))) {
                mismatches.add(label + ": 文言=" + quoted + " / 実バインド=" + real);
            }
        }
        assertTrue("突き合わせ対象が 1 件も拾えないのはパターンの取りこぼし", checked > 0);
        assertEquals("使い方ダイアログが別コマンドのショートカットを案内している: " + mismatches,
                List.of(), mismatches);
    }

    /** 発端になった 1 行を名指しで固定する (Ctrl+F は「図内を検索」のもの)。 */
    @Test
    public void searchEntitiesIsQuotedAsCtrlShiftF() {
        String body = MessageFormat.format(Messages.get("dlg.usage.body"), "Ctrl");
        assertTrue("エンティティ検索は Ctrl+Shift+F と案内すること",
                body.contains("Search Entities... (Ctrl+Shift+F)"));
        assertFalse("Ctrl+F は図内検索のものなので案内に使わないこと",
                body.contains("Search Entities... (Ctrl+F)"));
    }
}
