// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 向き指定 ({@code left to right direction} / {@code top to bottom direction}) を
 * シーケンス図・アクティビティ図へ前置するとレンダリングエラーになる不具合の回帰テスト。
 *
 * <p>これらの図種は向き指定ディレクティブを受け付けないため、
 * {@link PlantUmlRenderer#injectLayout(String)} は図種を判別して向き指定を抑制する。
 * クラス図など向き指定が有効な図種では従来どおり出力する。</p>
 */
public class PlantUmlDirectionInjectionTest {

    private static final String SEQ =
            "@startuml\ntitle T\nparticipant A\nparticipant B\nA -> B : hi\n@enduml\n";
    private static final String ACT =
            "@startuml\ntitle T\nstart\n:Step 1;\n:Step 2;\nstop\n@enduml\n";
    private static final String CLS =
            "@startuml\nclass A\nclass B\nA --> B : uses\n@enduml\n";
    private static final String STATE =
            "@startuml\n[*] --> S1\nS1 --> S2 : go\nS2 --> [*]\n@enduml\n";

    private String savedFallbackFont;

    @Before
    public void setUp() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        savedFallbackFont = PlantUmlRenderer.getFallbackFontName();
        PlantUmlRenderer.setFallbackFontName("");
    }

    @After
    public void tearDown() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        PlantUmlRenderer.setFallbackFontName(savedFallbackFont);
    }

    private static void setDirection(DiagramStyle.Direction d) {
        DiagramStyle s = new DiagramStyle();
        s.setDirection(d);
        PlantUmlRenderer.setStyle(s);
    }

    // --- supportsDirection の単体判定 ----------------------------------------

    @Test
    public void supportsDirection_falseForSequenceAndActivity() {
        assertFalse("sequence は向き指定不可", PlantUmlRenderer.supportsDirection(SEQ));
        assertFalse("activity は向き指定不可", PlantUmlRenderer.supportsDirection(ACT));
    }

    @Test
    public void supportsDirection_trueForGraphDiagrams() {
        assertTrue("class は向き指定可", PlantUmlRenderer.supportsDirection(CLS));
        assertTrue("state は向き指定可", PlantUmlRenderer.supportsDirection(STATE));
        // usecase (actor) を誤って sequence と判定しないこと
        assertTrue("usecase は向き指定可",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nactor User\nUser --> (Do)\n@enduml\n"));
    }

    // --- injectLayout の挿入抑制 ---------------------------------------------

    @Test
    public void injectLayout_omitsDirectionForSequence() {
        setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        String out = PlantUmlRenderer.injectLayout(SEQ);
        assertFalse("sequence に direction を入れない: " + out,
                out.contains("direction"));
    }

    @Test
    public void injectLayout_omitsDirectionForActivity() {
        setDirection(DiagramStyle.Direction.TOP_TO_BOTTOM);
        String out = PlantUmlRenderer.injectLayout(ACT);
        assertFalse("activity に direction を入れない: " + out,
                out.contains("direction"));
    }

    @Test
    public void injectLayout_keepsDirectionForClass() {
        setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        String out = PlantUmlRenderer.injectLayout(CLS);
        assertTrue("class には direction を出力する: " + out,
                out.contains("left to right direction"));
    }

    // --- レンダリング回帰 (ユーザ報告のエラー再現) ---------------------------

    @Test
    public void sequenceRendersWithoutErrorForEveryDirection() throws Exception {
        for (DiagramStyle.Direction d : DiagramStyle.Direction.values()) {
            setDirection(d);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 修正前は LEFT_TO_RIGHT / TOP_TO_BOTTOM で
            // PlantUmlRenderFailedException が投げられていた。
            PlantUmlRenderer.renderSvg(SEQ, out);
            assertTrue("SVG 出力が空でない (dir=" + d + ")", out.size() > 0);
        }
    }

    @Test
    public void activityRendersWithoutErrorForEveryDirection() throws Exception {
        for (DiagramStyle.Direction d : DiagramStyle.Direction.values()) {
            setDirection(d);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PlantUmlRenderer.renderSvg(ACT, out);
            assertTrue("SVG 出力が空でない (dir=" + d + ")", out.size() > 0);
        }
    }

    // --- ビルダ直書きの向きを明示設定が上書きする (設定が無視されない) ----------

    /** 本体に top to bottom 直書きがある図でも、横向き設定で上書きされること。 */
    private static final String CLASS_TTB =
            "@startuml\ntop to bottom direction\nclass A\nclass B\nA --> B\n@enduml\n";

    @Test
    public void explicitLeftToRightOverridesHardcodedTopToBottom() {
        setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        String out = PlantUmlRenderer.injectLayout(CLASS_TTB);
        assertFalse("直書きの top to bottom は除去される: " + out,
                out.contains("top to bottom direction"));
        assertTrue("ユーザ指定の left to right が効く: " + out,
                out.contains("left to right direction"));
    }

    @Test
    public void explicitTopToBottomKeepsSingleDirectionLine() {
        setDirection(DiagramStyle.Direction.TOP_TO_BOTTOM);
        String out = PlantUmlRenderer.injectLayout(CLASS_TTB);
        // 直書き分は除去され、prelude 側の 1 行だけが残る (重複しない)。
        int first = out.indexOf("top to bottom direction");
        assertTrue("top to bottom が残る", first >= 0);
        assertFalse("top to bottom は重複しない",
                out.indexOf("top to bottom direction", first + 1) >= 0);
    }

    @Test
    public void defaultDirectionKeepsHardcodedTopToBottom() {
        setDirection(DiagramStyle.Direction.DEFAULT);
        String out = PlantUmlRenderer.injectLayout(CLASS_TTB);
        // 未指定なら従来どおりビルダの既定 (縦) をそのまま尊重する。
        assertTrue("DEFAULT では直書きの縦をそのまま残す: " + out,
                out.contains("top to bottom direction"));
        assertFalse("DEFAULT では横を足さない: " + out,
                out.contains("left to right direction"));
    }
}
