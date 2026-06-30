// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.android.GradleDependency;
import juml.util.ErrorListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Gradle 依存宣言から JAR/AAR を {@code ~/.gradle/caches} や {@code ~/.m2/repository}
 * 配下で探索し、含まれるクラスを {@link JavaClassInfo} ヘッダとして遅延ロードするインデックス。
 *
 * <p>大規模 Android プロジェクトでも起動時間を犠牲にしないよう、構築時には
 * 各 JAR の {@link ZipEntry} 名 (= 含有クラスの FQN) だけをカタログ化する。
 * 実際の {@code .class} バイト列の ASM 解析は
 * {@link #resolve(String)} 呼び出し時に初めて実行され、結果は
 * {@link ConcurrentHashMap} にキャッシュされる。</p>
 *
 * <p>解決失敗 (依存は宣言されているが JAR が見つからない / 同名クラスが
 * 複数 JAR に出現するなど) は {@link #getMissingArtifacts()} に蓄積され、
 * クラス図/シーケンス図で {@code <<missing>> ⚠} マーカーとして表示する元データになる。</p>
 */
public final class DependencyJarIndex {

    /** JAR/AAR の中身を遅延参照するためのエントリ。 */
    private static final class JarSource {
        final Path jarPath;
        /** AAR の中に含まれる classes.jar を抽出したメモリバイト。AAR 由来でなければ null。 */
        final byte[] classesJarBytes;
        /** ZipEntry 名 (例: {@code androidx/appcompat/app/AppCompatActivity.class}) のセット。 */
        final Set<String> entries;

        JarSource(Path jarPath, byte[] classesJarBytes, Set<String> entries) {
            this.jarPath = jarPath;
            this.classesJarBytes = classesJarBytes;
            this.entries = entries;
        }
    }

    /** FQN → 含む JarSource (重複時は先勝ち)。 */
    private final Map<String, JarSource> byFqn = new LinkedHashMap<>();
    /** simpleName → 含む JarSource のリスト (パッケージ不明の引き当て用)。 */
    private final Map<String, List<String>> simpleToFqn = new LinkedHashMap<>();
    /** Gradle で宣言されているが JAR/AAR が解決できなかった notation。 */
    private final Set<String> missingArtifacts = new LinkedHashSet<>();
    /** 解決済みの JavaClassInfo を FQN でキャッシュ。 */
    private final Map<String, JavaClassInfo> resolvedCache = new ConcurrentHashMap<>();
    /** 「依存に宣言されているが見つからない」 simple/qualified 名のマーキング。 */
    private final Set<String> declaredButMissing = new HashSet<>();

    /**
     * 与えられた依存リストから JAR/AAR を探索しインデックスを構築する。
     *
     * <p>各依存に対し以下の順で検索する:</p>
     * <ol>
     *   <li>{@code ~/.gradle/caches/modules-2/files-2.1/&lt;group&gt;/&lt;name&gt;/&lt;version&gt;/}
     *       配下のサブディレクトリ (sha1 ハッシュ) を再帰的に探索</li>
     *   <li>{@code ~/.m2/repository/&lt;group as path&gt;/&lt;name&gt;/&lt;version&gt;/&lt;name&gt;-&lt;version&gt;.jar}</li>
     * </ol>
     * 見つかった JAR/AAR は ZipEntry リストだけメモリに保持する (遅延ロード)。
     */
    public static DependencyJarIndex build(List<GradleDependency> deps, ErrorListener listener) {
        DependencyJarIndex idx = new DependencyJarIndex();
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        if (deps == null) {
            return idx;
        }
        Set<String> visited = new HashSet<>();
        for (GradleDependency d : deps) {
            if (d.isModuleReference()) {
                continue;
            }
            String group = d.getGroup();
            String name = d.getName();
            String version = d.getVersion();
            if (group == null || name == null || version == null
                    || group.isEmpty() || name.isEmpty() || version.isEmpty()) {
                continue;
            }
            String notation = group + ":" + name + ":" + version;
            if (!visited.add(notation)) {
                continue;
            }
            Path artifact = findArtifact(group, name, version);
            if (artifact == null) {
                idx.missingArtifacts.add(notation);
                l.onError(notation, -1, "dependency artifact not found");
                continue;
            }
            try {
                idx.indexArtifact(artifact);
            } catch (IOException ex) {
                idx.missingArtifacts.add(notation);
                l.onError(artifact.toString(), -1,
                        "failed to index artifact: " + ex.getMessage());
            }
        }
        return idx;
    }

    /** Gradle cache → Maven local の順に JAR/AAR ファイルを探す。 */
    private static Path findArtifact(String group, String name, String version) {
        String home = System.getProperty("user.home");
        if (home == null) {
            return null;
        }
        // ~/.gradle/caches/modules-2/files-2.1/<group>/<name>/<version>/<hash>/<name>-<version>.jar
        Path gradleBase = Paths.get(home, ".gradle", "caches",
                "modules-2", "files-2.1", group, name, version);
        Path hit = scanForArtifact(gradleBase, name, version);
        if (hit != null) {
            return hit;
        }
        // ~/.m2/repository/<group-as-path>/<name>/<version>/<name>-<version>.jar
        String groupPath = group.replace('.', '/');
        Path m2Base = Paths.get(home, ".m2", "repository", groupPath, name, version);
        Path m2Jar = m2Base.resolve(name + "-" + version + ".jar");
        if (Files.isRegularFile(m2Jar)) {
            return m2Jar;
        }
        Path m2Aar = m2Base.resolve(name + "-" + version + ".aar");
        if (Files.isRegularFile(m2Aar)) {
            return m2Aar;
        }
        return null;
    }

    /** Gradle cache 形式は sha1 サブディレクトリ階層なので深さ 2 で walk する。 */
    private static Path scanForArtifact(Path base, String name, String version) {
        if (!Files.isDirectory(base)) {
            return null;
        }
        String jarName = name + "-" + version + ".jar";
        String aarName = name + "-" + version + ".aar";
        try (java.util.stream.Stream<Path> walk = Files.walk(base, 2)) {
            return walk
                    .filter(p -> {
                        String fn = p.getFileName().toString();
                        return jarName.equals(fn) || aarName.equals(fn);
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    /** JAR (または AAR 内の classes.jar) のエントリ名を列挙して内部マップに登録する。 */
    private void indexArtifact(Path artifact) throws IOException {
        String name = artifact.getFileName().toString().toLowerCase();
        byte[] classesJarBytes = null;
        Set<String> entries;
        if (name.endsWith(".aar")) {
            classesJarBytes = extractClassesJar(artifact);
            if (classesJarBytes == null) {
                return;
            }
            entries = listZipEntries(new ByteArrayInputStream(classesJarBytes));
        } else {
            entries = listZipEntries(Files.newInputStream(artifact));
        }
        JarSource src = new JarSource(artifact, classesJarBytes, entries);
        for (String entry : entries) {
            if (!entry.endsWith(".class")) {
                continue;
            }
            String fqn = entry.substring(0, entry.length() - ".class".length())
                    .replace('/', '.');
            // 内部クラスは外部 API でほぼ使われないのでスキップ (検索ノイズ削減)
            if (fqn.contains("$")) {
                continue;
            }
            byFqn.putIfAbsent(fqn, src);
            int dot = fqn.lastIndexOf('.');
            String simple = dot < 0 ? fqn : fqn.substring(dot + 1);
            simpleToFqn.computeIfAbsent(simple, k -> new ArrayList<>()).add(fqn);
        }
    }

    /** AAR ファイル内の classes.jar をバイト列としてメモリに展開する。なければ null。 */
    private static byte[] extractClassesJar(Path aar) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(aar))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if ("classes.jar".equals(e.getName())) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream(
                            Math.max(1024, (int) Math.min(e.getSize(), Integer.MAX_VALUE)));
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = zip.read(chunk)) > 0) {
                        buf.write(chunk, 0, n);
                    }
                    return buf.toByteArray();
                }
            }
        }
        return null;
    }

    private static Set<String> listZipEntries(InputStream in) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    names.add(e.getName());
                }
            }
        }
        return names;
    }

    /**
     * 完全修飾名 or シンプル名から外部 JavaClassInfo を引く。見つからなければ
     * 候補が依存リストに宣言されていたかを {@link #declaredButMissing} で確認できる。
     *
     * <p>シンプル名で複数 JAR にヒットした場合は最初の候補を採用 (起動時の登録順)。
     * 結果は内部キャッシュに保存され、再呼び出しは O(1)。</p>
     */
    public Optional<JavaClassInfo> resolve(String nameOrFqn) {
        if (nameOrFqn == null || nameOrFqn.isEmpty()) {
            return Optional.empty();
        }
        JavaClassInfo cached = resolvedCache.get(nameOrFqn);
        if (cached != null) {
            return Optional.of(cached);
        }
        // 1. 完全修飾名で引く
        JarSource src = byFqn.get(nameOrFqn);
        String fqn = nameOrFqn;
        // 2. シンプル名 (ドット含まない) で引く
        if (src == null && nameOrFqn.indexOf('.') < 0) {
            List<String> fqns = simpleToFqn.get(nameOrFqn);
            if (fqns != null && !fqns.isEmpty()) {
                fqn = fqns.get(0);
                src = byFqn.get(fqn);
            }
        }
        if (src == null) {
            return Optional.empty();
        }
        try {
            JavaClassInfo info = loadFromSource(src, fqn);
            if (info != null) {
                resolvedCache.put(nameOrFqn, info);
                resolvedCache.put(fqn, info);
                return Optional.of(info);
            }
        } catch (IOException ex) {
            // 解決失敗は missing 扱いに昇格させる
            declaredButMissing.add(nameOrFqn);
        }
        return Optional.empty();
    }

    /** FQN を JarSource から実体ロードする。 */
    private JavaClassInfo loadFromSource(JarSource src, String fqn) throws IOException {
        String entryPath = fqn.replace('.', '/') + ".class";
        InputStream base = src.classesJarBytes != null
                ? new ByteArrayInputStream(src.classesJarBytes)
                : Files.newInputStream(src.jarPath);
        try (ZipInputStream zip = new ZipInputStream(base)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (entryPath.equals(e.getName())) {
                    return ExternalClassReader.readHeader(zip, src.jarPath.toString());
                }
            }
        }
        return null;
    }

    /**
     * 「依存として宣言されているがクラス自体は見つからない」場合に使う placeholder。
     * シーケンス図 / クラス図側で {@code <<missing>>} マーカー付き ClassInfo を出すために
     * 呼び出される。
     */
    public JavaClassInfo missingPlaceholder(String simpleNameOrFqn) {
        JavaClassInfo info = new JavaClassInfo();
        int dot = simpleNameOrFqn.lastIndexOf('.');
        if (dot >= 0) {
            info.setPackageName(simpleNameOrFqn.substring(0, dot));
            info.setSimpleName(simpleNameOrFqn.substring(dot + 1));
        } else {
            info.setSimpleName(simpleNameOrFqn);
        }
        info.setOrigin(JavaClassInfo.Origin.MISSING_JAR);
        info.setDetailed(false);
        return info;
    }

    /**
     * 当該クラスが「Gradle で宣言された依存に属するはずだが、JAR/AAR が
     * 解決できなかった」と推定できれば true。
     *
     * <p>厳密な判定は不可能 (group:name に対しどのクラスが入っているか
     * 知るには JAR を開かねばならない) ので、{@link #missingArtifacts} が
     * 1 つでもあれば true を返す保守的実装。</p>
     */
    public boolean isDeclaredButMissing(String simpleOrFqn) {
        if (simpleOrFqn == null || simpleOrFqn.isEmpty()) {
            return false;
        }
        if (declaredButMissing.contains(simpleOrFqn)) {
            return true;
        }
        return !missingArtifacts.isEmpty();
    }

    /** 解決できなかった依存の notation 一覧 (例: {@code androidx.appcompat:appcompat:1.7.0})。 */
    public Set<String> getMissingArtifacts() {
        return Collections.unmodifiableSet(missingArtifacts);
    }

    /** インデックスに含まれる JAR 内クラスの総数 (テスト用)。 */
    public int indexedClassCount() {
        return byFqn.size();
    }
}
