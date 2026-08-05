// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.AtomicFileWrite;
import juml.util.MiniJson;
import juml.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 利用者が自分で足したスニペットの保管庫 ({@code ~/.juml/snippets.json})。
 *
 * <p>同梱のスニペット ({@link PumlSnippets}) はどの現場でも使う一般形しか置けない。
 * 実際に何度も書くのは「自社の決まりごとを載せたヘッダ」「いつもの登場人物一式」
 * といったその人固有の型で、そこを埋められないと打鍵は減りきらない。</p>
 *
 * <p>プロジェクト単位ではなくユーザー単位に置くのは、PlantUML エディタが
 * プロジェクトを開いていなくても (単体の {@code .puml} を開いただけでも) 使えるため。
 * どこで作図していても自分の型が付いて回るほうが目的に合う。</p>
 *
 * <p>読めないファイルは空として扱う。壊れた JSON で起動できなくなるより、
 * 追加分が見えないほうが害が小さい (同梱スニペットは無関係に動き続ける)。</p>
 */
final class PumlUserSnippets {

    private static final int VERSION = 1;

    /** 1 件のユーザースニペット。 */
    static final class Entry {
        private final String trigger;
        private final String label;
        private final String body;

        Entry(String trigger, String label, String body) {
            this.trigger = trigger;
            this.label = label;
            this.body = body;
        }

        /** 補完で引き当てる語。 */
        String trigger() {
            return trigger;
        }

        /** 一覧に出す名前 (空なら trigger を使う)。 */
        String label() {
            return label == null || label.isBlank() ? trigger : label;
        }

        /** 挿入本文 ({@link PumlSnippetTemplate} の記法が使える)。 */
        String body() {
            return body;
        }
    }

    private final File jsonFile;
    /** null = 未ロード。 */
    private List<Entry> cached;

    /** 既定の保存先 ({@code ~/.juml/snippets.json}) を使う。 */
    PumlUserSnippets() {
        this(new File(PathUtil.getUserDataDir(), "snippets.json"));
    }

    /** 保存先を明示する (テスト用)。 */
    PumlUserSnippets(File jsonFile) {
        this.jsonFile = jsonFile;
    }

    /** 保存先のパス (設定画面の案内用)。 */
    File file() {
        return jsonFile;
    }

    /** 登録済みスニペットを登録順で返す。 */
    synchronized List<Entry> load() {
        if (cached == null) {
            cached = readFile();
        }
        return new ArrayList<>(cached);
    }

    /** 一覧をまるごと差し替えて保存する。成功したら true。 */
    synchronized boolean save(List<Entry> entries) {
        List<Entry> clean = new ArrayList<>();
        for (Entry e : entries) {
            // トリガも本文も無いものは補完から引けず、一覧を汚すだけ。
            if (e != null && !e.trigger().isBlank() && !e.body().isEmpty()) {
                clean.add(e);
            }
        }
        cached = clean;
        if (jsonFile == null) {
            return false;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        List<Object> arr = new ArrayList<>();
        for (Entry e : clean) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("trigger", e.trigger());
            o.put("label", e.label());
            o.put("body", e.body());
            arr.add(o);
        }
        root.put("snippets", arr);
        try {
            File dir = jsonFile.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            byte[] bytes = MiniJson.write(root).getBytes(StandardCharsets.UTF_8);
            // 直接上書きすると、途中で落ちたときに壊れた JSON が残って全件失われる。
            AtomicFileWrite.write(jsonFile, os -> os.write(bytes));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /** 1 件足して保存する。同じトリガが既にあれば置き換える。 */
    synchronized boolean add(Entry entry) {
        List<Entry> list = new ArrayList<>(load());
        list.removeIf(e -> e.trigger().equalsIgnoreCase(entry.trigger()));
        list.add(entry);
        return save(list);
    }

    /** 次回 {@link #load()} で読み直させる (外部でファイルが変わったとき)。 */
    synchronized void invalidate() {
        cached = null;
    }

    private List<Entry> readFile() {
        List<Entry> out = new ArrayList<>();
        if (jsonFile == null || !jsonFile.isFile()) {
            return out;
        }
        try {
            String json = new String(Files.readAllBytes(jsonFile.toPath()),
                    StandardCharsets.UTF_8);
            Object root = MiniJson.parse(json);
            if (!(root instanceof Map)) {
                return out;
            }
            Object arr = ((Map<?, ?>) root).get("snippets");
            if (!(arr instanceof List)) {
                return out;
            }
            for (Object o : (List<?>) arr) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<?, ?> m = (Map<?, ?>) o;
                String trigger = str(m.get("trigger"));
                String body = str(m.get("body"));
                if (trigger.isBlank() || body.isEmpty()) {
                    continue;
                }
                out.add(new Entry(trigger, str(m.get("label")), body));
            }
        } catch (IOException | RuntimeException ex) {
            // 壊れたファイルは空として扱う (同梱スニペットは影響を受けない)。
            return new ArrayList<>();
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * トリガとして使える形へ整える (空白を詰め、補完で引ける文字だけ残す)。
     *
     * <p>{@code @} と {@code !} は {@code @startuml} や {@code !include} のように
     * 語頭でのみ意味を持つ。語中や語尾に残すと、打っても引けない飾りになるだけなので
     * 先頭の 1 文字だけ通す。</p>
     */
    static String normalizeTrigger(String raw) {
        if (raw == null) {
            return "";
        }
        String src = raw.strip();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            boolean lead = sb.length() == 0 && (c == '@' || c == '!');
            if (Character.isLetterOrDigit(c) || c == '_' || lead) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
