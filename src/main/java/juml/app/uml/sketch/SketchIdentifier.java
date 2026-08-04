// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.regex.Pattern;

/**
 * ビジュアル設計器が扱う「引用符なし識別子 (別名)」の共通規則。
 *
 * <p>以前は各コーデックが {@code [A-Za-z_$][\w$]*} と ASCII 限定で書いていた。Java の
 * {@code \w} は既定で {@code [a-zA-Z_0-9]} しか含まないため、<b>日本語で名前を付けた要素が
 * 丸ごと失われていた</b>。実機 (PlantUML 1.2026.6) で計測した結果は下記のとおりで、
 * PlantUML 側は引用符なしの日本語識別子をすべて受け付ける:</p>
 *
 * <pre>
 * class ユーザ / entity ユーザ / actor 利用者 / component 認証 / state 待機 /
 * object 田中さん / participant 利用者 / node サーバ … いずれも描画成功
 * </pre>
 *
 * <p>それに対し設計器側は、たとえば {@code entity ユーザ} を未対応行に落として
 * <b>エンティティを 0 個</b>にし ({@code ErSketchCodec}) 、{@code 氏名 : varchar} の列を
 * 落としたうえで編集をロックしていた。ASCII 規則を Unicode へ一般化して揃える。</p>
 *
 * <p>先頭に数字を許さない点は従来どおり (PlantUML 自体は {@code class 1st} も通すが、
 * 座標コメントやカーディナリティ等の数字トークンと取り違えないよう保守的に据え置く)。</p>
 */
final class SketchIdentifier {

    private SketchIdentifier() {
    }

    /**
     * 先頭に置ける 1 文字 (Unicode 文字・{@code _}・{@code $})。図種判定
     * ({@link SketchDiagramType}) のように「識別子が始まっていること」だけを見る用途で使う。
     */
    static final String HEAD_ONLY = "[\\p{L}_$]";

    /** {@link #HEAD_ONLY} の別名 (この中で連結するための短縮)。 */
    private static final String HEAD = HEAD_ONLY;

    /** 素の識別子 (ドットを含まない)。従来の {@code [A-Za-z_$][\w$]*} の Unicode 版。 */
    static final String BARE = HEAD + "[\\p{L}\\p{N}_$]*";

    /** ドット付き識別子 (FQN 風の名前を許す種別用)。従来の {@code [A-Za-z_$][\w$.]*} 版。 */
    static final String DOTTED = HEAD + "[\\p{L}\\p{N}_$.]*";

    /** ドット・ハイフン付き識別子 (配置図のノード別名用)。 */
    static final String DOTTED_DASH = HEAD + "[\\p{L}\\p{N}_$.-]*";

    /** {@link #BARE} の全体一致用パターン (ダイアログの入力検証)。 */
    static final Pattern BARE_PATTERN = Pattern.compile(BARE);

    /** {@link #DOTTED} の全体一致用パターン (ダイアログの入力検証)。 */
    static final Pattern DOTTED_PATTERN = Pattern.compile(DOTTED);
}
