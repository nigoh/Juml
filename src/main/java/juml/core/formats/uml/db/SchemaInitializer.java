// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 解析インデックス DB のスキーマ初期化。
 *
 * <p>migration は持たない方針 ({@code schema_version} 不一致時は DB を退避して
 * 再構築する)。本クラスは「空 DB に対する現行スキーマの一括 CREATE」だけを担う。</p>
 *
 * <p>{@link #SCHEMA_VERSION} を変更したら、起動時に {@link IndexDatabase} が
 * 旧 DB を {@code index.db.discarded-v<N>-<timestamp>} に退避して
 * 再構築するので、CREATE 文を素直に増やすだけでよい。</p>
 */
public final class SchemaInitializer {

    /**
     * 現行スキーマバージョン。
     *
     * <ul>
     *   <li>v1 (PR1): {@code meta} のみ</li>
     *   <li>v2 (PR2): {@code modules / files / classes / class_interfaces /
     *                   class_imports / fields / methods} を追加</li>
     *   <li>v3 (PR3): {@code refs / unresolved} を追加 (逆参照インデックスを永続化)</li>
     *   <li>v4 (PR6a): {@code manifests / intent_filters / components} を追加
     *                  (Activity/Service/Fragment 集約と Manifest メタデータ)</li>
     *   <li>v5 (PR6b): {@code aidl_interfaces / aidl_methods / aidl_bindings /
     *                  external_endpoints} を追加 (AIDL 構造と Intent/Manifest/AIDL
     *                  を統合した外部境界俯瞰)</li>
     * </ul>
     */
    public static final int SCHEMA_VERSION = 6;

    private SchemaInitializer() {
    }

    /** 空 DB に対して現行スキーマを CREATE し、meta 行を投入する。 */
    public static void initialize(Connection conn, String projectRoot, String toolVersion)
            throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            createTables(conn);
            insertMeta(conn, projectRoot, toolVersion);
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            createCoreTables(st);
            createClassTables(st);
            createReferenceTables(st);
            createManifestTables(st);
            createAidlAndEndpointTables(st);
        }
    }

    private static void createCoreTables(Statement st) throws SQLException {
        // 0) meta
        st.executeUpdate(
            "CREATE TABLE meta ("
            + "  key   TEXT PRIMARY KEY,"
            + "  value TEXT NOT NULL"
            + ")"
        );

        // 1) modules (Gradle ":app", ":car-lib" 等)
        st.executeUpdate(
            "CREATE TABLE modules ("
            + "  id   INTEGER PRIMARY KEY,"
            + "  name TEXT NOT NULL UNIQUE,"
            + "  path TEXT NOT NULL"
            + ")"
        );

        // 2) files (.java / .aidl / AndroidManifest.xml / build.gradle ...)
        st.executeUpdate(
            "CREATE TABLE files ("
            + "  id          INTEGER PRIMARY KEY,"
            + "  path        TEXT NOT NULL UNIQUE,"
            + "  module_id   INTEGER REFERENCES modules(id) ON DELETE SET NULL,"
            + "  kind        TEXT NOT NULL,"
            + "  mtime       INTEGER NOT NULL,"
            + "  size        INTEGER NOT NULL,"
            + "  parsed_at   INTEGER NOT NULL,"
            + "  parse_error TEXT"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_files_kind   ON files(kind)");
        st.executeUpdate("CREATE INDEX idx_files_module ON files(module_id)");
    }

    private static void createClassTables(Statement st) throws SQLException {
        // 3) classes (class/interface/enum/annotation/AIDL_INTERFACE/record)
        st.executeUpdate(
            "CREATE TABLE classes ("
            + "  id             INTEGER PRIMARY KEY,"
            + "  qn             TEXT NOT NULL UNIQUE,"
            + "  simple_name    TEXT NOT NULL,"
            + "  package_name   TEXT NOT NULL,"
            + "  kind           TEXT NOT NULL,"
            + "  enclosing      TEXT,"
            + "  super_class    TEXT,"
            + "  modifiers      TEXT,"
            + "  annotations    TEXT,"
            + "  aaos_category  TEXT,"
            + "  android_comp   TEXT,"
            + "  jetpack_stereo TEXT,"
            + "  origin         TEXT NOT NULL,"
            + "  jar_path       TEXT,"
            + "  detailed       INTEGER NOT NULL,"
            + "  comment        TEXT,"
            + "  file_id        INTEGER REFERENCES files(id) ON DELETE CASCADE"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_classes_simple  ON classes(simple_name)");
        st.executeUpdate("CREATE INDEX idx_classes_package ON classes(package_name)");
        st.executeUpdate("CREATE INDEX idx_classes_super   ON classes(super_class)");
        st.executeUpdate("CREATE INDEX idx_classes_file    ON classes(file_id)");

        // 4) class_interfaces (multi-valued)
        st.executeUpdate(
            "CREATE TABLE class_interfaces ("
            + "  class_id INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,"
            + "  iface_qn TEXT NOT NULL,"
            + "  PRIMARY KEY (class_id, iface_qn)"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_class_iface_iface ON class_interfaces(iface_qn)");

        // 5) class_imports
        st.executeUpdate(
            "CREATE TABLE class_imports ("
            + "  class_id  INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,"
            + "  imp       TEXT NOT NULL,"
            + "  is_static INTEGER NOT NULL,"
            + "  PRIMARY KEY (class_id, imp)"
            + ")"
        );

        // 6) fields
        st.executeUpdate(
            "CREATE TABLE fields ("
            + "  id          INTEGER PRIMARY KEY,"
            + "  class_id    INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,"
            + "  name        TEXT NOT NULL,"
            + "  type        TEXT,"
            + "  visibility  TEXT NOT NULL,"
            + "  modifiers   TEXT,"
            + "  annotations TEXT"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_fields_class ON fields(class_id)");
        st.executeUpdate("CREATE INDEX idx_fields_name  ON fields(name)");

        // 7) methods (シグネチャまで。本体ツリーは Stage B でソース再パースで取り直す)
        st.executeUpdate(
            "CREATE TABLE methods ("
            + "  id           INTEGER PRIMARY KEY,"
            + "  class_id     INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,"
            + "  name         TEXT NOT NULL,"
            + "  return_type  TEXT,"
            + "  visibility   TEXT NOT NULL,"
            + "  modifiers    TEXT,"
            + "  annotations  TEXT,"
            + "  param_types  TEXT,"
            + "  param_names  TEXT,"
            + "  throws_types TEXT,"
            + "  is_abstract  INTEGER NOT NULL,"
            + "  comment      TEXT"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_methods_class ON methods(class_id)");
        st.executeUpdate("CREATE INDEX idx_methods_name  ON methods(name)");
    }

    private static void createReferenceTables(Statement st) throws SQLException {
        // 8) refs (逆参照インデックス。最大規模テーブル)
        //    ReferenceKey (callee 側) と ReferenceSite (caller 側) を 1 行に展開する。
        //    N-hop 逆引きは idx_refs_callee で「callee_owner_qn + callee_member + sym_kind」
        //    引きの BFS、増分更新は idx_refs_file (caller 側ファイル) の CASCADE。
        st.executeUpdate(
            "CREATE TABLE refs ("
            + "  id              INTEGER PRIMARY KEY,"
            + "  callee_owner_qn TEXT NOT NULL,"
            + "  callee_member   TEXT,"
            + "  sym_kind        TEXT NOT NULL,"
            + "  caller_qn       TEXT NOT NULL,"
            + "  caller_method   TEXT,"
            + "  file_id         INTEGER REFERENCES files(id) ON DELETE CASCADE,"
            + "  line_hint       INTEGER NOT NULL DEFAULT -1,"
            + "  ref_kind        TEXT NOT NULL,"
            + "  callee_signature TEXT"
            + ")"
        );
        st.executeUpdate(
            "CREATE INDEX idx_refs_callee "
            + "ON refs(callee_owner_qn, callee_member, sym_kind)");
        st.executeUpdate("CREATE INDEX idx_refs_caller ON refs(caller_qn)");
        st.executeUpdate("CREATE INDEX idx_refs_file   ON refs(file_id)");

        // 9) unresolved (名前解決失敗の診断ログ)
        st.executeUpdate(
            "CREATE TABLE unresolved ("
            + "  id        INTEGER PRIMARY KEY,"
            + "  symbol    TEXT NOT NULL,"
            + "  caller_qn TEXT,"
            + "  file_id   INTEGER REFERENCES files(id) ON DELETE CASCADE"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_unresolved_symbol ON unresolved(symbol)");
    }

    private static void createManifestTables(Statement st) throws SQLException {
        // 10) manifests (AndroidManifest.xml の概要)
        st.executeUpdate(
            "CREATE TABLE manifests ("
            + "  id                INTEGER PRIMARY KEY,"
            + "  file_id           INTEGER UNIQUE REFERENCES files(id) ON DELETE CASCADE,"
            + "  package_name      TEXT NOT NULL,"
            + "  source_set        TEXT NOT NULL,"
            + "  min_sdk           INTEGER,"
            + "  target_sdk        INTEGER,"
            + "  max_sdk           INTEGER,"
            + "  application_class TEXT"
            + ")"
        );

        // 11) components (Activity / Service / Fragment / Receiver / Provider 集約)
        //     Manifest 由来とソース継承 (AndroidSuperclassDetector) 由来を統合する。
        //     detection_src で出所を区別 ('MANIFEST' / 'SUPERCLASS' / 'BOTH')。
        st.executeUpdate(
            "CREATE TABLE components ("
            + "  id            INTEGER PRIMARY KEY,"
            + "  comp_type     TEXT NOT NULL,"
            + "  class_qn      TEXT NOT NULL,"
            + "  detection_src TEXT NOT NULL,"
            + "  manifest_id   INTEGER REFERENCES manifests(id) ON DELETE CASCADE,"
            + "  exported      INTEGER,"
            + "  permission    TEXT,"
            + "  enabled       INTEGER,"
            + "  UNIQUE(comp_type, class_qn)"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_components_type  ON components(comp_type)");
        st.executeUpdate("CREATE INDEX idx_components_class ON components(class_qn)");

        // 12) intent_filters (Manifest の <intent-filter>)
        //     1 filter = 1 行。actions / categories / data_schemes / data_mime_types は
        //     CSV 連結で保持する。完全に正規化するとテーブル数が膨らむので Phase 1 では
        //     filter 単位の round-trip を優先する。
        st.executeUpdate(
            "CREATE TABLE intent_filters ("
            + "  id              INTEGER PRIMARY KEY,"
            + "  component_id    INTEGER NOT NULL REFERENCES components(id) ON DELETE CASCADE,"
            + "  filter_index    INTEGER NOT NULL,"
            + "  actions         TEXT,"
            + "  categories      TEXT,"
            + "  data_schemes    TEXT,"
            + "  data_mime_types TEXT"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_intent_actions ON intent_filters(actions)");
    }

    private static void createAidlAndEndpointTables(Statement st) throws SQLException {
        // 13) aidl_interfaces (.aidl ソースから抽出した interface)
        //     1 行 = 1 interface。class_id は classes(Kind.AIDL_INTERFACE) を指す。
        //     interface 単体での検索 (実装が無い AIDL を見つけたい等) に使えるよう独立テーブル。
        st.executeUpdate(
            "CREATE TABLE aidl_interfaces ("
            + "  id           INTEGER PRIMARY KEY,"
            + "  class_id     INTEGER UNIQUE REFERENCES classes(id) ON DELETE CASCADE,"
            + "  package_name TEXT NOT NULL,"
            + "  simple_name  TEXT NOT NULL"
            + ")"
        );

        // 14) aidl_methods (interface のメソッドシグネチャ)
        //     methods テーブルと別にする理由: AIDL はソースの (oneway / in/out/inout)
        //     等の修飾子情報を保持したいが、Java メソッドと意味論が違う。
        st.executeUpdate(
            "CREATE TABLE aidl_methods ("
            + "  id          INTEGER PRIMARY KEY,"
            + "  aidl_id     INTEGER NOT NULL REFERENCES aidl_interfaces(id) ON DELETE CASCADE,"
            + "  name        TEXT NOT NULL,"
            + "  oneway      INTEGER NOT NULL,"
            + "  return_type TEXT,"
            + "  param_sig   TEXT"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_aidl_methods_aidl ON aidl_methods(aidl_id)");

        // 15) aidl_bindings (AIDL ↔ 実装クラスの紐付け)
        //     AidlBindingResolver.resolve の結果を保持。1 つの AIDL が複数 Stub に
        //     紐付くケース (process 別実装等) に備えて UNIQUE(aidl_qn, impl_qn)。
        st.executeUpdate(
            "CREATE TABLE aidl_bindings ("
            + "  id      INTEGER PRIMARY KEY,"
            + "  aidl_qn TEXT NOT NULL,"
            + "  impl_qn TEXT NOT NULL,"
            + "  UNIQUE(aidl_qn, impl_qn)"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_aidl_bind_aidl ON aidl_bindings(aidl_qn)");
        st.executeUpdate("CREATE INDEX idx_aidl_bind_impl ON aidl_bindings(impl_qn)");

        // 16) external_endpoints (Intent/Manifest/AIDL を統合した外部境界の一覧)
        //     source_kind:
        //       MANIFEST_ACTIVITY / MANIFEST_SERVICE / MANIFEST_RECEIVER / MANIFEST_PROVIDER
        //       INTENT_START_ACTIVITY / INTENT_START_FOR_RESULT / INTENT_SET_CLASS
        //       AIDL_INTERFACE / AIDL_BINDING
        //     from_qn は発火側 (manifest 宣言の場合は null), to_qn は到達側。
        st.executeUpdate(
            "CREATE TABLE external_endpoints ("
            + "  id          INTEGER PRIMARY KEY,"
            + "  source_kind TEXT NOT NULL,"
            + "  from_qn     TEXT,"
            + "  from_method TEXT,"
            + "  to_qn       TEXT NOT NULL,"
            + "  attributes  TEXT,"
            + "  file_id     INTEGER REFERENCES files(id) ON DELETE CASCADE,"
            + "  line_hint   INTEGER NOT NULL DEFAULT -1"
            + ")"
        );
        st.executeUpdate("CREATE INDEX idx_ep_from ON external_endpoints(from_qn)");
        st.executeUpdate("CREATE INDEX idx_ep_to   ON external_endpoints(to_qn)");
        st.executeUpdate("CREATE INDEX idx_ep_kind ON external_endpoints(source_kind)");
    }

    private static void insertMeta(Connection conn, String projectRoot, String toolVersion)
            throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO meta(key, value) VALUES (?, ?)")) {
            putMeta(ps, "schema_version", Integer.toString(SCHEMA_VERSION));
            putMeta(ps, "project_root", projectRoot == null ? "" : projectRoot);
            putMeta(ps, "tool_version", toolVersion == null ? "" : toolVersion);
            putMeta(ps, "created_at", Long.toString(now));
            putMeta(ps, "updated_at", Long.toString(now));
        }
    }

    private static void putMeta(PreparedStatement ps, String key, String value) throws SQLException {
        ps.setString(1, key);
        ps.setString(2, value);
        ps.executeUpdate();
    }
}
