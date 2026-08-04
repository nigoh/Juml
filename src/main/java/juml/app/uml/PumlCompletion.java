// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PumlCompletionItem.Kind;
import juml.app.uml.PumlSnippets.Group;
import juml.util.Messages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自由編集エディタ ({@link PumlSourcePanel}) の入力補完エンジン (純関数)。
 *
 * <p>狙いは「作図の打鍵数を減らすこと」。そのために 4 つを組み合わせる:</p>
 * <ol>
 *   <li><b>スニペット展開</b> — {@code alt} と打てば {@code alt/else/end} のブロックが
 *       丸ごと入り、{@code Tab} で穴だけ埋められる ({@link PumlSnippets})。</li>
 *   <li><b>あいまい一致</b> — {@code pt} で {@code participant}、{@code sp} で
 *       {@code skinparam} のように、頭文字 + 部分列でも引ける。</li>
 *   <li><b>図種を見た並べ替え</b> — {@link PumlCompletionContext} が判定した図種で
 *       使う語を上げ、使わない語を下げる (矢印キーを押す回数が減る)。</li>
 *   <li><b>文脈候補</b> — 矢印記法・{@code !theme} のテーマ名・{@code skinparam} の属性・
 *       本文中の既存識別子を、それが要る位置でだけ出す。</li>
 * </ol>
 */
final class PumlCompletion {

    /** 補完候補の最大件数 (ポップアップが長くなりすぎないように)。 */
    static final int MAX_CANDIDATES = 20;

    /** 図種が一致した語への加点。 */
    private static final int ON_FLAVOR = 300;
    /** 全図種共通の語への加点 (図種特化の語よりわずかに下)。 */
    private static final int COMMON_FLAVOR = 150;
    /** 判定済みの図種で使わない語への減点。 */
    private static final int OFF_FLAVOR = -250;
    /** 行頭でのスニペットへの加点 (最も打鍵を減らせるので上位に出す)。 */
    private static final int SNIPPET_BONUS = 120;
    /** 引数値としてだけ意味を持つ語 (テーマ名など) への減点。 */
    private static final int VALUE_PENALTY = -400;
    /** キャレットに最も近い識別子への加点。 */
    private static final int PROXIMITY_BONUS = 120;
    /** 近さの加点が 1 件ごとに減る量。 */
    private static final int PROXIMITY_STEP = 12;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** 「名前 + 空白」で終わる行 (= 次に関係記法が来る位置)。 */
    private static final Pattern BEFORE_ARROW =
            Pattern.compile("^\\s*[\\w\"()\\[\\]]+\\s+$");
    /** 矢印記法の直後 (= 次に相手の名前が来る位置)。 */
    private static final Pattern AFTER_ARROW =
            Pattern.compile(".*(--|\\.\\.|->|<-|\\|>|<\\|)[\\w\\[\\]{}|*o<>.\\-]*\\s*$");

    /**
     * 補完対象の語を構成する文字か。{@code @} と {@code !} を先頭に含めることで、
     * {@code @startuml} 等のブロック指定子と {@code !include}/{@code !theme} 等の
     * プリプロセッサ・ディレクティブも補完できるようにする。
     */
    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '!';
    }

    /** 矢印記法を構成しうる文字。 */
    private static boolean isArrowChar(char c) {
        return "-.<>|*o{}/\\".indexOf(c) >= 0;
    }

    private PumlCompletion() {
    }

    // -------------------------------------------------------------------------
    // 語の切り出し
    // -------------------------------------------------------------------------

    /** キャレット直前の補完対象語 (無ければ空文字)。 */
    static String wordPrefix(String text, int caret) {
        if (text == null || caret <= 0 || caret > text.length()) {
            return "";
        }
        int i = caret;
        while (i > 0 && isWordChar(text.charAt(i - 1))) {
            i--;
        }
        return text.substring(i, caret);
    }

    /**
     * キャレット位置から語の終端 (排他端) を返す。語中で補完を確定したとき、
     * キャレット後方の語の残り (例: {@code cl|a} の {@code a}) も含めて候補で
     * 置換するために使う ({@code classa} のような残余崩れを防ぐ)。
     */
    static int wordEnd(String text, int caret) {
        if (text == null) {
            return Math.max(0, caret);
        }
        int i = Math.max(0, Math.min(caret, text.length()));
        while (i < text.length() && isWordChar(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * キャレット直前の矢印記法の打ちかけ (無ければ空文字)。{@code -} か {@code .} を
     * 含み、かつ直前が英数字でないものだけを矢印とみなす ({@code foo} の {@code oo} を
     * 矢印と誤認しないため)。
     */
    static String arrowPrefix(String text, int caret) {
        if (text == null || caret <= 0 || caret > text.length()) {
            return "";
        }
        int i = caret;
        while (i > 0 && isArrowChar(text.charAt(i - 1))) {
            i--;
        }
        String run = text.substring(i, caret);
        if (run.indexOf('-') < 0 && run.indexOf('.') < 0) {
            return "";
        }
        if (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) {
            return "";
        }
        return run;
    }

    /** 矢印の打ちかけを置換するときの終端 (キャレット後方に続く矢印文字も含める)。 */
    static int arrowEnd(String text, int caret) {
        if (text == null) {
            return Math.max(0, caret);
        }
        int i = Math.max(0, Math.min(caret, text.length()));
        while (i < text.length() && isArrowChar(text.charAt(i))) {
            i++;
        }
        return i;
    }

    // -------------------------------------------------------------------------
    // 候補生成
    // -------------------------------------------------------------------------

    /**
     * キャレット位置の補完候補を、確度の高い順に返す。
     *
     * @param explicit {@code Ctrl+Space} による明示起動なら true。打ちかけの語が
     *                 無くても「その位置で書けるもの」を一覧する。
     */
    static List<PumlCompletionItem> items(String text, int caret, boolean explicit) {
        PumlCompletionContext ctx = PumlCompletionContext.at(text, caret);
        String prefix = ctx.prefix();
        // 矢印を打ちかけているなら矢印だけを出す (キーワードが混ざっても選べない)。
        if (prefix.isEmpty()) {
            String arrow = arrowPrefix(text, caret);
            if (!arrow.isEmpty()) {
                return arrowItems(ctx, arrow);
            }
            if (explicit && BEFORE_ARROW.matcher(ctx.linePrefix()).matches()) {
                return arrowItems(ctx, "");
            }
        }
        List<PumlCompletionItem> valueItems = valueItems(ctx, prefix);
        if (valueItems != null) {
            return valueItems;
        }
        return generalItems(ctx, prefix);
    }

    /**
     * 打ちかけの矢印に前方一致する矢印記法。図種が判っていればその図種のものを先に出す。
     */
    private static List<PumlCompletionItem> arrowItems(PumlCompletionContext ctx,
                                                       String typed) {
        List<PumlCompletionItem> out = new ArrayList<>();
        for (PumlCompletionDictionary.Entry e : PumlCompletionDictionary.arrows()) {
            if (!e.word().startsWith(typed) || e.word().equals(typed)) {
                continue;
            }
            int score = 1000 - e.word().length() + flavorBonus(e, ctx.flavor());
            out.add(PumlCompletionItem
                    .word(Kind.ARROW, e.word(), Messages.get(e.detailKey()))
                    .withScore(score));
        }
        return rank(out);
    }

    /**
     * 決まった語しか書けない引数位置 ({@code !theme <名前>}、{@code note <位置>}、
     * {@code <<ステレオタイプ>>} など) なら、その候補だけを返す。
     * 引数位置でなければ null (通常の候補生成へ進む)。
     */
    private static List<PumlCompletionItem> valueItems(PumlCompletionContext ctx,
                                                       String prefix) {
        String line = ctx.linePrefix() + prefix;
        for (PumlCompletionDictionary.ArgRule rule : PumlCompletionDictionary.argRules()) {
            if (!rule.matches(line)) {
                continue;
            }
            String detail = Messages.get(rule.detailKey());
            List<PumlCompletionItem> out = new ArrayList<>();
            for (String v : rule.values()) {
                int score = matchScore(v, prefix);
                if (score < 0 || v.equalsIgnoreCase(prefix)) {
                    continue;
                }
                out.add(PumlCompletionItem.word(Kind.VALUE, v, detail).withScore(score));
            }
            // 規則には当てはまったが候補が全部絞り落ちた場合は、通常の候補生成へ戻す
            // (打ちかけが規則の想定から外れているので、閉じてしまうより広く出す)。
            if (!out.isEmpty()) {
                return rank(out);
            }
        }
        return null;
    }

    /** スニペット + キーワード + 本文識別子を混ぜた通常の候補一覧。 */
    private static List<PumlCompletionItem> generalItems(PumlCompletionContext ctx,
                                                         String prefix) {
        List<PumlCompletionItem> out = new ArrayList<>();
        Group flavor = ctx.flavor();
        // 1. スニペット。行頭でだけ出す (行の途中にブロックを挿し込むのは事故のもと)。
        if (ctx.atLineStart()) {
            for (PumlSnippets.Snippet snip : PumlSnippets.all()) {
                int score = matchScore(snip.trigger(), prefix);
                if (score < 0) {
                    continue;
                }
                score += SNIPPET_BONUS + flavorBonus(snip.group(), flavor);
                out.add(PumlCompletionItem
                        .snippet(snip.trigger(), snip.body(), preview(snip.body()))
                        .withScore(score));
            }
        }
        // 2. 辞書キーワード。
        Set<String> seen = new LinkedHashSet<>();
        for (PumlCompletionDictionary.Entry e : PumlCompletionDictionary.keywords()) {
            int score = matchScore(e.word(), prefix);
            if (score < 0 || e.word().equalsIgnoreCase(prefix)) {
                continue;
            }
            score += flavorBonus(e, flavor);
            if (PumlCompletionDictionary.isValueWord(e.word())) {
                score += VALUE_PENALTY;
            }
            seen.add(e.word());
            out.add(PumlCompletionItem
                    .word(Kind.KEYWORD, e.word(), e.primaryGroup().displayName())
                    .withScore(score));
        }
        // 3. 本文中の識別子。矢印の直後 (相手の名前を書く位置) では最優先にする。
        boolean naming = AFTER_ARROW.matcher(ctx.linePrefix()).matches();
        String detail = Messages.get("puml.completion.detail.identifier");
        List<String> ids = bufferIdentifiers(ctx, prefix, seen);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int score = matchScore(id, prefix);
            if (score < 0) {
                continue;
            }
            // 近さの加点。大きな図では「さっき書いた名前」をまた書くことが多く、
            // 遠くの同名候補より手前にあるものを先に出したほうが当たる。
            int nearness = Math.max(0, PROXIMITY_BONUS - i * PROXIMITY_STEP);
            out.add(PumlCompletionItem
                    .word(Kind.IDENTIFIER, id, detail)
                    .withScore(score + nearness + (naming ? ON_FLAVOR * 2 : 0)));
        }
        return rank(out);
    }

    /**
     * 本文に現れる識別子のうち補完に値するもの。宣言済みの名前 (クラス名・参加者名) を
     * 先に、その他の語を後に返す。それぞれの中ではキャレットに近い順に並べる。
     * {@code seen} (辞書で既に出した語) と打ち終わった語は除く。
     */
    private static List<String> bufferIdentifiers(PumlCompletionContext ctx, String prefix,
                                                  Set<String> seen) {
        Set<String> declared = new LinkedHashSet<>();
        for (String name : ctx.declaredNames()) {
            if (!seen.contains(name) && !name.equalsIgnoreCase(prefix)) {
                declared.add(name);
            }
        }
        Set<String> out = new LinkedHashSet<>(nearestFirst(declared, ctx));
        String text = ctx.text();
        Matcher m = IDENTIFIER.matcher(text);
        while (m.find()) {
            String id = m.group();
            if (seen.contains(id) || id.equalsIgnoreCase(prefix) || id.length() < 2) {
                continue;
            }
            // ディレクティブの綴りの一部 (@startuml の "startuml"、!theme の "theme") は
            // 識別子ではない。辞書側がディレクティブ全体を候補に持っているので、
            // ここで裸の綴りを混ぜると同じものが二重に並ぶだけになる。
            char before = m.start() > 0 ? text.charAt(m.start() - 1) : ' ';
            if (before == '@' || before == '!') {
                continue;
            }
            out.add(id);
        }
        List<String> others = new ArrayList<>(out);
        others.removeAll(declared);
        List<String> ordered = new ArrayList<>(nearestFirst(declared, ctx));
        ordered.addAll(nearestFirst(others, ctx));
        return ordered;
    }

    /**
     * キャレットに最も近い出現位置の順に並べ替える。同着は元の順序を保つ。
     */
    private static List<String> nearestFirst(java.util.Collection<String> names,
                                             PumlCompletionContext ctx) {
        String text = ctx.text();
        int caret = ctx.caretForTest();
        List<String> out = new ArrayList<>(names);
        out.sort(Comparator.comparingInt(n -> nearestDistance(text, n, caret)));
        return out;
    }

    /** {@code name} の出現のうちキャレットに最も近いものまでの距離。 */
    private static int nearestDistance(String text, String name, int caret) {
        int best = Integer.MAX_VALUE;
        for (int i = text.indexOf(name); i >= 0; i = text.indexOf(name, i + 1)) {
            best = Math.min(best, Math.abs(caret - i));
        }
        return best;
    }

    /** 得点降順に並べ、上限件数で打ち切る。同点は生成順 (辞書順) を保つ。 */
    private static List<PumlCompletionItem> rank(List<PumlCompletionItem> items) {
        List<PumlCompletionItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(PumlCompletionItem::score).reversed());
        return sorted.size() <= MAX_CANDIDATES
                ? sorted : new ArrayList<>(sorted.subList(0, MAX_CANDIDATES));
    }

    /** 辞書エントリの図種加点。 */
    private static int flavorBonus(PumlCompletionDictionary.Entry e, Group flavor) {
        if (flavor == Group.COMMON) {
            // 図種を判定できていない。ここで上下させると誤判定がそのまま順位の歪みになる。
            return 0;
        }
        if (e.groups().contains(flavor)) {
            return ON_FLAVOR;
        }
        return e.groups().contains(Group.COMMON) ? COMMON_FLAVOR : OFF_FLAVOR;
    }

    /** スニペットの図種加点。 */
    private static int flavorBonus(Group group, Group flavor) {
        if (flavor == Group.COMMON) {
            return 0;
        }
        if (group == flavor) {
            return ON_FLAVOR;
        }
        return group == Group.COMMON ? COMMON_FLAVOR : OFF_FLAVOR;
    }

    /** スニペット本文の 1 行要約 (プレースホルダを外し、改行を {@code ⏎} で畳む)。 */
    static String preview(String body) {
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(body);
        String flat = ex.text().strip().replace("\n", " ⏎ ").replaceAll("\\s{2,}", " ");
        return flat.length() <= 48 ? flat : flat.substring(0, 47) + "…";
    }

    // -------------------------------------------------------------------------
    // 一致スコア (前方一致 → あいまい一致)
    // -------------------------------------------------------------------------

    /**
     * {@code candidate} が {@code prefix} にどれだけ良く一致するか。一致しなければ負値。
     *
     * <p>段階は 3 つ: 大文字小文字まで含む前方一致 → 大小無視の前方一致 →
     * 頭文字を共有する部分列 (あいまい一致)。あいまい一致は 2 文字以上のときだけ許し、
     * 頭文字の一致を必須にする。これを外すと候補が一気に無関係になり、
     * 「短く打つ」利点より「選ぶ手間」の損が上回る。</p>
     */
    static int matchScore(String candidate, String prefix) {
        if (prefix == null) {
            return -1;
        }
        if (prefix.isEmpty()) {
            // 明示起動 (Ctrl+Space)。すべて通し、順位は文脈加点だけで決める。
            return 100;
        }
        if (candidate.startsWith(prefix)) {
            return 1000 - lengthPenalty(candidate);
        }
        String lc = candidate.toLowerCase(Locale.ROOT);
        String lp = prefix.toLowerCase(Locale.ROOT);
        if (lc.startsWith(lp)) {
            return 950 - lengthPenalty(candidate);
        }
        if (prefix.length() < 2 || lc.charAt(0) != lp.charAt(0)) {
            return -1;
        }
        int bonus = subsequenceBonus(candidate, lp);
        return bonus < 0 ? -1 : 600 + bonus - lengthPenalty(candidate);
    }

    /** 長い候補をわずかに下げる (同じ打鍵で短く済むものを先に出す)。 */
    private static int lengthPenalty(String candidate) {
        return Math.min(candidate.length(), 40);
    }

    /**
     * {@code prefix} が {@code candidate} の部分列として現れるなら、その質を加点で返す。
     * 現れなければ負値。連続した一致と語境界での一致を高く評価する
     * ({@code pt} → {@code participant} より {@code pa} → {@code participant} が上)。
     */
    private static int subsequenceBonus(String candidate, String lowerPrefix) {
        int at = 0;
        int bonus = 0;
        int prev = -2;
        for (int i = 0; i < lowerPrefix.length(); i++) {
            int found = indexOfIgnoreCase(candidate, lowerPrefix.charAt(i), at);
            if (found < 0) {
                return -1;
            }
            if (found == prev + 1) {
                bonus += 20;
            }
            if (isBoundary(candidate, found)) {
                bonus += 15;
            }
            prev = found;
            at = found + 1;
        }
        return bonus;
    }

    private static int indexOfIgnoreCase(String s, char lower, int from) {
        for (int i = Math.max(0, from); i < s.length(); i++) {
            if (Character.toLowerCase(s.charAt(i)) == lower) {
                return i;
            }
        }
        return -1;
    }

    /** 語の切れ目 (先頭・区切り文字の直後・キャメルケースの山) か。 */
    private static boolean isBoundary(String s, int i) {
        if (i == 0) {
            return true;
        }
        char prev = s.charAt(i - 1);
        return prev == '_' || prev == '!' || prev == '@' || prev == '-'
                || Character.isLowerCase(prev) && Character.isUpperCase(s.charAt(i));
    }

    // -------------------------------------------------------------------------
    // 旧 API (文字列候補)
    // -------------------------------------------------------------------------

    /**
     * {@code prefix} に一致する語をキーワード → 本文識別子の順で重複なく返す。
     * 文脈を見ない素朴な照合で、候補件数の目安表示とテストに使う。実際の
     * ポップアップは文脈を見る {@link #items(String, int, boolean)} を使う。
     */
    static List<String> candidates(String prefix, String docText) {
        List<String> out = new ArrayList<>();
        if (prefix == null) {
            return out;
        }
        List<PumlCompletionItem> scored = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (PumlCompletionDictionary.Entry e : PumlCompletionDictionary.keywords()) {
            int score = matchScore(e.word(), prefix);
            if (score < 0 || e.word().equalsIgnoreCase(prefix)) {
                continue;
            }
            seen.add(e.word());
            scored.add(PumlCompletionItem.word(Kind.KEYWORD, e.word(), "").withScore(score));
        }
        if (docText != null) {
            Matcher m = IDENTIFIER.matcher(docText);
            while (m.find()) {
                String id = m.group();
                int score = matchScore(id, prefix);
                if (score < 0 || id.equalsIgnoreCase(prefix) || !seen.add(id)) {
                    continue;
                }
                scored.add(PumlCompletionItem
                        .word(Kind.IDENTIFIER, id, "").withScore(score));
            }
        }
        for (PumlCompletionItem item : rank(scored)) {
            out.add(item.label());
        }
        return out;
    }

    /** テスト用: 既定辞書に含まれるキーワード数 (辞書が空でないことの確認)。 */
    static int keywordCountForTest() {
        return PumlCompletionDictionary.keywords().size();
    }
}
