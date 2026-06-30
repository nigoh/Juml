// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.MiniJson;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UML 図の付箋メモを {@code <projectRoot>/.juml/notes.json} に永続化するストア。
 *
 * <p>git で共有できるよう、人が読める整形 JSON でプロジェクト内に保存する。
 * 図ごとに {@link DiagramTabPane} のタブキー (図種 + 題材) を辞書キーにして束ねる。</p>
 *
 * <p>ファイル全体をメモリに保持し、{@link #save} のたびに読み・更新・書きを行う
 * (付箋の保存頻度は低いため十分)。パース不能なファイルは握り潰して空として扱い、
 * 既存メモが壊れていてもアプリが落ちないようにする。</p>
 */
final class DiagramNotesStore {

    private static final int VERSION = 1;

    private final File jsonFile;
    /** 図キー → 付箋リスト。null = 未ロード。 */
    private Map<String, List<DiagramNote>> byDiagram;
    /** 図キー → コネクタリスト。 */
    private Map<String, List<DiagramConnector>> connByDiagram;

    /** {@code projectRoot} が null の場合は何も永続化しない no-op ストアになる。 */
    DiagramNotesStore(File projectRoot) {
        this.jsonFile = projectRoot == null ? null
                : new File(new File(projectRoot, ".juml"), "notes.json");
    }

    /** 指定図キーの付箋一覧 (コピー) を返す。 */
    synchronized List<DiagramNote> load(String diagramKey) {
        ensureLoaded();
        List<DiagramNote> list = byDiagram.get(diagramKey);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /** 指定図キーのコネクタ一覧 (コピー) を返す。 */
    synchronized List<DiagramConnector> loadConnectors(String diagramKey) {
        ensureLoaded();
        List<DiagramConnector> list = connByDiagram.get(diagramKey);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /** 付箋のみ保存 (コネクタは空に)。 */
    synchronized boolean save(String diagramKey, List<DiagramNote> notes) {
        return save(diagramKey, notes, java.util.Collections.emptyList());
    }

    /**
     * 指定図キーの付箋 + コネクタを保存する (付箋が空なら当該キーを丸ごと削除)。
     *
     * @return 書き込みに成功した (または永続化対象外で何もしなかった) なら true、
     *         IO 失敗時は false。呼び出し側はこれでユーザー通知を判断できる。
     */
    synchronized boolean save(String diagramKey, List<DiagramNote> notes,
                              List<DiagramConnector> connectors) {
        if (jsonFile == null) {
            return true;
        }
        ensureLoaded();
        if (notes == null || notes.isEmpty()) {
            byDiagram.remove(diagramKey);
            connByDiagram.remove(diagramKey);
        } else {
            byDiagram.put(diagramKey, new ArrayList<>(notes));
            if (connectors == null || connectors.isEmpty()) {
                connByDiagram.remove(diagramKey);
            } else {
                connByDiagram.put(diagramKey, new ArrayList<>(connectors));
            }
        }
        return writeFile();
    }

    private void ensureLoaded() {
        if (byDiagram != null) {
            return;
        }
        byDiagram = new LinkedHashMap<>();
        connByDiagram = new LinkedHashMap<>();
        if (jsonFile == null || !jsonFile.isFile()) {
            return;
        }
        try {
            String json = new String(Files.readAllBytes(jsonFile.toPath()),
                    StandardCharsets.UTF_8);
            Object root = MiniJson.parse(json);
            if (!(root instanceof Map)) {
                return;
            }
            Object diagrams = ((Map<?, ?>) root).get("diagrams");
            if (!(diagrams instanceof Map)) {
                return;
            }
            for (Map.Entry<?, ?> e : ((Map<?, ?>) diagrams).entrySet()) {
                String key = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (v instanceof List) {
                    // 旧形式: 値が付箋配列そのもの。
                    byDiagram.put(key, toNotes((List<?>) v));
                } else if (v instanceof Map) {
                    // 新形式: {notes:[...], connectors:[...]}。
                    Map<?, ?> mm = (Map<?, ?>) v;
                    if (mm.get("notes") instanceof List) {
                        byDiagram.put(key, toNotes((List<?>) mm.get("notes")));
                    }
                    if (mm.get("connectors") instanceof List) {
                        connByDiagram.put(key, toConnectors((List<?>) mm.get("connectors")));
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            // 壊れた notes.json は空とみなす (アプリを落とさない)。
            byDiagram = new LinkedHashMap<>();
            connByDiagram = new LinkedHashMap<>();
        }
    }

    private boolean writeFile() {
        try {
            File dir = jsonFile.getParentFile();
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
            }
            if (byDiagram.isEmpty() && !jsonFile.exists()) {
                return true; // 何も無ければファイルを作らない
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", VERSION);
            Map<String, Object> diagrams = new LinkedHashMap<>();
            for (Map.Entry<String, List<DiagramNote>> e : byDiagram.entrySet()) {
                List<DiagramConnector> conns = connByDiagram.get(e.getKey());
                if (conns != null && !conns.isEmpty()) {
                    // コネクタがある図だけ {notes, connectors} のオブジェクト形式で書く。
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("notes", toJsonList(e.getValue()));
                    d.put("connectors", toJsonConnList(conns));
                    diagrams.put(e.getKey(), d);
                } else {
                    diagrams.put(e.getKey(), toJsonList(e.getValue()));
                }
            }
            root.put("diagrams", diagrams);
            Files.write(jsonFile.toPath(),
                    MiniJson.write(root).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException ex) {
            // 保存失敗を呼び出し側へ伝え、ステータスバー通知に使う (サイレント消失を防ぐ)。
            return false;
        }
    }

    /** 保存先 (.juml/notes.json) のパス。ステータス通知の文言用。null = 永続化対象外。 */
    File jsonFile() {
        return jsonFile;
    }

    private static List<Object> toJsonList(List<DiagramNote> notes) {
        List<Object> out = new ArrayList<>();
        for (DiagramNote n : notes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("anchor", n.getAnchor().name());
            m.put("x", n.getX());
            m.put("y", n.getY());
            m.put("w", n.getWidth());
            m.put("h", n.getHeight());
            m.put("color", n.getColor());
            if (n.isLocked()) {
                m.put("locked", true);
            }
            if (!n.getTags().isEmpty()) {
                m.put("tags", new ArrayList<Object>(n.getTags()));
            }
            if (n.getTargetRef() != null) {
                m.put("targetRef", n.getTargetRef());
            }
            m.put("text", n.getText());
            out.add(m);
        }
        return out;
    }

    private static List<Object> toJsonConnList(List<DiagramConnector> conns) {
        List<Object> out = new ArrayList<>();
        for (DiagramConnector c : conns) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", c.getFromId());
            m.put("to", c.getToId());
            out.add(m);
        }
        return out;
    }

    private static List<DiagramConnector> toConnectors(List<?> list) {
        List<DiagramConnector> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) o;
                String from = str(m, "from");
                String to = str(m, "to");
                if (from != null && to != null) {
                    out.add(new DiagramConnector(from, to));
                }
            }
        }
        return out;
    }

    private static List<DiagramNote> toNotes(List<?> list) {
        List<DiagramNote> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<?, ?> m = (Map<?, ?>) o;
            DiagramNote n = new DiagramNote();
            n.setId(str(m, "id"));
            n.setAnchor(parseAnchor(str(m, "anchor")));
            n.setX(num(m, "x"));
            n.setY(num(m, "y"));
            n.setWidth(num(m, "w"));
            n.setHeight(num(m, "h"));
            n.setColor(str(m, "color"));
            n.setLocked(bool(m, "locked"));
            n.setTags(strList(m, "tags"));
            n.setTargetRef(str(m, "targetRef"));
            n.setText(str(m, "text"));
            out.add(n);
        }
        return out;
    }

    private static DiagramNote.Anchor parseAnchor(String s) {
        try {
            return s == null ? DiagramNote.Anchor.FREE : DiagramNote.Anchor.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return DiagramNote.Anchor.FREE;
        }
    }

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static double num(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    private static boolean bool(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    private static List<String> strList(Map<?, ?> m, String key) {
        Object v = m.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object o : (List<?>) v) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
        }
        return out;
    }
}
