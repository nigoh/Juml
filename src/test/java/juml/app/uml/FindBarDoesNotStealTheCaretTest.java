// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * 検索バーを開いたまま本文を編集しても、<b>キャレットを奪わない</b>ことの回帰テスト。
 *
 * <p>ラウンド 28 は「バーを開いたまま編集するとヒットの int オフセットが陳腐化し、
 * Ctrl+H が無関係な本文を破壊する」問題を、対象文書の監視 → 再検索で塞いだ。
 * ところが再検索の最後は必ず {@code showCurrent()} を呼び、そこで
 * {@code setCaretPosition} / {@code moveCaretPosition} を実行する。</p>
 *
 * <p>結果、バーを開いたまま 1 文字打つたびに EDT の次サイクルでキャレットがヒット位置へ
 * 引き戻され、ヒット語が<b>選択状態</b>になる。続けて打った次の 1 文字は
 * {@code JTextComponent} の既定動作で選択範囲の置換になるので、打鍵が本来の位置ではなく
 * 検索ヒットの上に落ちてヒット語が消える。<b>陳腐化を防ぐために入れた監視が、
 * 防いだものより破壊的な誤編集を作っていた。</b></p>
 *
 * <p>編集追随はヒットとハイライトの再計算だけを行い、キャレットには触らない。
 * 打っているのは利用者であり、その位置は利用者のものである。</p>
 */
public class FindBarDoesNotStealTheCaretTest {

    private static void setQuery(SourceFindBar bar, String query) throws Exception {
        Field f = SourceFindBar.class.getDeclaredField("field");
        f.setAccessible(true);
        ((JTextField) f.get(bar)).setText(query);
    }

    private static void flushEdt() throws Exception {
        for (int i = 0; i < 8; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(40);
        }
    }

    @Test
    public void typingWithTheBarOpenKeepsTheCaretWhereTheUserPutIt() throws Exception {
        assumeFalse("headless では Swing コンポーネントを作れない",
                GraphicsEnvironment.isHeadless());
        JTextPane[] pane = new JTextPane[1];
        SourceFindBar[] bar = new SourceFindBar[1];
        JFrame[] frame = new JFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                pane[0] = new JTextPane();
                pane[0].setText("@startuml\n  alpha here\nclass B\n@enduml\n");
                bar[0] = new SourceFindBar(pane[0], () -> { }, true);
                frame[0] = new JFrame();
                frame[0].getContentPane().add(new JScrollPane(pane[0]),
                        java.awt.BorderLayout.CENTER);
                frame[0].getContentPane().add(bar[0], java.awt.BorderLayout.SOUTH);
                frame[0].setSize(600, 400);
                frame[0].setVisible(true);
                bar[0].activateWithReplace();
            });
            flushEdt();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    setQuery(bar[0], "alpha");
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            flushEdt();

            // 利用者が本文末尾へキャレットを置いて 1 文字打つ。
            int[] typedAt = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                try {
                    int end = pane[0].getDocument().getLength();
                    pane[0].setCaretPosition(end);
                    pane[0].getDocument().insertString(end, "X", null);
                    typedAt[0] = pane[0].getCaretPosition();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            // 編集追随は invokeLater で来るので、ここで消化させる。
            flushEdt();

            int[] caret = new int[3];
            SwingUtilities.invokeAndWait(() -> {
                caret[0] = pane[0].getCaretPosition();
                caret[1] = pane[0].getSelectionStart();
                caret[2] = pane[0].getSelectionEnd();
            });
            assertEquals("打った位置からキャレットが動かないこと", typedAt[0], caret[0]);
            assertEquals("ヒットが選択状態にされないこと (次の 1 打で置換されてしまう)",
                    caret[1], caret[2]);

            // 続けて 1 文字打つ。選択があるとここで本文が化けていた。
            String[] text = new String[1];
            SwingUtilities.invokeAndWait(() -> {
                pane[0].replaceSelection("Z");
                text[0] = pane[0].getText();
            });
            assertTrue("打鍵が末尾に落ちること: " + text[0].replace("\n", "\\n"),
                    text[0].endsWith("XZ\n") || text[0].endsWith("XZ"));
            assertTrue("検索ヒットが上書きされないこと: " + text[0].replace("\n", "\\n"),
                    text[0].contains("alpha here"));
        } finally {
            if (frame[0] != null) {
                SwingUtilities.invokeAndWait(() -> frame[0].dispose());
            }
        }
    }

    /** 非退行: 編集後もヒットは取り直され、件数が現在の本文に追随すること (R28 の本来の効果)。 */
    @Test
    public void hitsAreStillRefreshedAfterAnEdit() throws Exception {
        assumeFalse("headless では Swing コンポーネントを作れない",
                GraphicsEnvironment.isHeadless());
        JTextPane[] pane = new JTextPane[1];
        SourceFindBar[] bar = new SourceFindBar[1];
        JFrame[] frame = new JFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                pane[0] = new JTextPane();
                pane[0].setText("alpha\n");
                bar[0] = new SourceFindBar(pane[0], () -> { }, true);
                frame[0] = new JFrame();
                frame[0].getContentPane().add(new JScrollPane(pane[0]),
                        java.awt.BorderLayout.CENTER);
                frame[0].getContentPane().add(bar[0], java.awt.BorderLayout.SOUTH);
                frame[0].setSize(600, 400);
                frame[0].setVisible(true);
                bar[0].activateWithReplace();
            });
            flushEdt();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    setQuery(bar[0], "alpha");
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            flushEdt();
            // もう 1 つ alpha を足す。追随していればヒットが 2 件になる。
            SwingUtilities.invokeAndWait(() -> {
                try {
                    pane[0].getDocument().insertString(
                            pane[0].getDocument().getLength(), "alpha\n", null);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            flushEdt();

            int[] hitCount = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Field f = SourceFindBar.class.getDeclaredField("hits");
                    f.setAccessible(true);
                    hitCount[0] = ((java.util.List<?>) f.get(bar[0])).size();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            assertEquals("編集後もヒットが取り直されること", 2, hitCount[0]);
        } finally {
            if (frame[0] != null) {
                SwingUtilities.invokeAndWait(() -> frame[0].dispose());
            }
        }
    }
}
