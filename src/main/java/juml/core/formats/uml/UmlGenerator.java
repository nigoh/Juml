// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidProjectAnalysis;
import juml.core.formats.android.AndroidProjectAnalyzer;
import juml.core.formats.android.GradleDependency;
import juml.core.formats.android.GradleProjectInfo;
import juml.core.formats.java.AndroidProjectScanner;
import juml.util.CancelToken;
import juml.util.ErrorListener;
import juml.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Java/AIDL ソースまたはディレクトリから PlantUML クラス図 / シーケンス図を生成する高レベル API。
 *
 * <p>AAOS 固有のパターン認識・AIDL のパースを行い、複数ファイルから一貫したダイアグラムを生成する。
 * 大規模プロジェクト向けに {@link #extractFromProject(File, AndroidProjectScanner.Options,
 * ErrorListener, ProgressListener, CancelToken, boolean, ParseMode) } の並列パース版を
 * 提供する。</p>
 */
public final class UmlGenerator {

    /** パースの粒度。詳細 (Stage B) かヘッダのみ (Stage A) かを切り替える。 */
    public enum ParseMode {
        /** フルパース (フィールド・メソッド・呼び出し列・コメントを保持)。 */
        FULL,
        /** ヘッダのみ ({@link JavaStructureExtractor#extractHeadersOnly} 相当)。 */
        HEADERS_ONLY
    }

    /** 並列パース結果。クラス情報リストとモジュール対応付き ClassIndex を含む。 */
    public static final class ProjectParseResult {
        private final List<JavaClassInfo> classes;
        private final ClassIndex index;
        private final DependencyJarIndex dependencyIndex;

        public ProjectParseResult(List<JavaClassInfo> classes, ClassIndex index) {
            this(classes, index, null);
        }

        public ProjectParseResult(List<JavaClassInfo> classes, ClassIndex index,
                                   DependencyJarIndex dependencyIndex) {
            this.classes = classes;
            this.index = index;
            this.dependencyIndex = dependencyIndex;
        }

        public List<JavaClassInfo> getClasses() {
            return classes;
        }

        public ClassIndex getIndex() {
            return index;
        }

        /**
         * 依存 JAR/AAR のクラス解決用インデックス。
         * 依存が無いまたは構築失敗時は null になることがあるので null チェック必須。
         */
        public DependencyJarIndex getDependencyIndex() {
            return dependencyIndex;
        }
    }

    private UmlGenerator() {
    }

    /** 入力ファイル (.java または .aidl) 1 つから ClassInfo リストを抽出する。 */
    public static List<JavaClassInfo> extractFromSource(String source, String fileName) {
        return extractFromSource(source, fileName, null);
    }

    /** エラーリスナー付き。 */
    public static List<JavaClassInfo> extractFromSource(String source, String fileName,
                                                         ErrorListener listener) {
        return extractFromSource(source, fileName, listener, null);
    }

    /**
     * {@code solver} が非 null なら Java ソースの呼び出し先をシンボル解決して
     * {@code Call.resolvedOwnerFqn} を埋める (プロジェクト FULL 解析時のみ)。
     */
    public static List<JavaClassInfo> extractFromSource(String source, String fileName,
                                                        ErrorListener listener,
                                                        juml.core.formats.java.jp.JpSolver
                                                                solver) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        ErrorListener wrapped = (listener == null) ? ErrorListener.silent()
                : wrapWithSource(listener, fileName);
        List<JavaClassInfo> infos;
        String lowerName = fileName == null ? "" : fileName.toLowerCase();
        if (lowerName.endsWith(".aidl")) {
            infos = new ArrayList<>(AidlParser.parse(source, wrapped));
        } else if (lowerName.endsWith(".kt") || lowerName.endsWith(".kts")) {
            infos = new ArrayList<>(
                    juml.core.formats.kotlin.KotlinLightScanner.scan(source, wrapped));
        } else {
            infos = new ArrayList<>(JavaStructureExtractor.extract(source, wrapped, solver));
        }
        for (JavaClassInfo info : infos) {
            if (fileName != null) {
                info.setSourceFile(fileName);
            }
            String cat = AaosPattern.categorize(info);
            if (cat != null) {
                info.setAaosCategory(cat);
            }
            info.getJetpackStereotypes().addAll(JetpackPattern.classify(info));
        }
        return infos;
    }

    /**
     * 任意パスの {@code .jar} / {@code .aar} / {@code .class} ファイル、または
     * それらを含むディレクトリから ClassInfo リストを抽出する。
     *
     * <p>{@link ArchiveClassReader} でバイトコードヘッダを読み、ソース解析時と同様に
     * AAOS / Jetpack のステレオタイプ後処理を適用して一貫した装飾を付ける。</p>
     */
    public static List<JavaClassInfo> extractFromArchive(File input, ErrorListener listener)
            throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        List<JavaClassInfo> infos = ArchiveClassReader.read(input, l);
        for (JavaClassInfo info : infos) {
            String cat = AaosPattern.categorize(info);
            if (cat != null) {
                info.setAaosCategory(cat);
            }
            info.getJetpackStereotypes().addAll(JetpackPattern.classify(info));
        }
        return infos;
    }

    /** ヘッダのみ版。fields/methods/comments を破棄して軽量化したヘッダリストを返す。 */
    public static List<JavaClassInfo> extractHeadersFromSource(String source, String fileName,
                                                                ErrorListener listener) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        ErrorListener wrapped = (listener == null) ? ErrorListener.silent()
                : wrapWithSource(listener, fileName);
        List<JavaClassInfo> infos;
        String lowerName = fileName == null ? "" : fileName.toLowerCase();
        if (lowerName.endsWith(".aidl")) {
            // AIDL は元々サイズが小さいのでフル解析からヘッダだけ拾う
            List<JavaClassInfo> full = new ArrayList<>(AidlParser.parse(source, wrapped));
            infos = new ArrayList<>(full.size());
            for (JavaClassInfo c : full) {
                JavaClassInfo h = stripToHeader(c);
                infos.add(h);
            }
        } else if (lowerName.endsWith(".kt") || lowerName.endsWith(".kts")) {
            List<JavaClassInfo> full = juml.core.formats.kotlin.KotlinLightScanner
                    .scan(source, wrapped);
            infos = new ArrayList<>(full.size());
            for (JavaClassInfo c : full) {
                JavaClassInfo h = stripToHeader(c);
                infos.add(h);
            }
        } else {
            infos = new ArrayList<>(JavaStructureExtractor.extractHeadersOnly(source, wrapped));
        }
        for (JavaClassInfo info : infos) {
            String cat = AaosPattern.categorize(info);
            if (cat != null) {
                info.setAaosCategory(cat);
            }
            // ヘッダのみでも extends と class アノテーションは取れるので Jetpack 判定は走らせる。
            // コンストラクタ @Inject のような members を見る判定はヘッダ段階では空振りする。
            info.getJetpackStereotypes().addAll(JetpackPattern.classify(info));
        }
        return infos;
    }

    private static JavaClassInfo stripToHeader(JavaClassInfo c) {
        JavaClassInfo h = new JavaClassInfo();
        h.setPackageName(c.getPackageName());
        h.setSimpleName(c.getSimpleName());
        h.setKind(c.getKind());
        h.getModifiers().addAll(c.getModifiers());
        h.getAnnotations().addAll(c.getAnnotations());
        h.setSuperClass(c.getSuperClass());
        h.getInterfaces().addAll(c.getInterfaces());
        h.setEnclosingClass(c.getEnclosingClass());
        h.setAaosCategory(c.getAaosCategory());
        h.setAndroidComponentType(c.getAndroidComponentType());
        h.getJetpackStereotypes().addAll(c.getJetpackStereotypes());
        // 型パラメータ宣言 ("T extends Entity" など) は Stage A でも保持する
        // (クラス図の型パラメータ表示に必要なため)。
        h.setTypeParameters(c.getTypeParameters());
        h.setDetailed(false);
        return h;
    }

    private static ErrorListener wrapWithSource(ErrorListener inner, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return inner;
        }
        return (source, line, message) ->
                inner.onError(source != null && !source.isEmpty() ? source : fileName,
                        line, message);
    }

    /** Gradle プロジェクト全体を走査して全 ClassInfo を集める。 */
    public static List<JavaClassInfo> extractFromProject(File root) throws IOException {
        return extractFromProject(root, null, null);
    }

    /** オプション付きプロジェクトスキャン。 */
    public static List<JavaClassInfo> extractFromProject(File root,
                                                          AndroidProjectScanner.Options opts)
            throws IOException {
        return extractFromProject(root, opts, null);
    }

    /** オプション + エラーリスナー付き。個別ファイルの読込失敗は listener に通知して継続する。 */
    public static List<JavaClassInfo> extractFromProject(File root,
                                                          AndroidProjectScanner.Options opts,
                                                          ErrorListener listener)
            throws IOException {
        return extractFromProject(root, opts, listener, true);
    }

    /**
     * manifest 自動マージ制御つきプロジェクト走査。
     * @param mergeManifest true なら同プロジェクト直下の AndroidManifest.xml を自動検出し、
     *                       対応する JavaClassInfo に {@code androidComponentType} を反映
     */
    public static List<JavaClassInfo> extractFromProject(File root,
                                                          AndroidProjectScanner.Options opts,
                                                          ErrorListener listener,
                                                          boolean mergeManifest)
            throws IOException {
        return extractFromProjectDetailed(root, opts, listener, null, null,
                mergeManifest, ParseMode.FULL).getClasses();
    }

    /**
     * 並列パース + 進捗 + キャンセル + Stage A/B 切替に対応した汎用版。
     *
     * <p>結果 ClassInfo のリスト順は元のファイル順とは異なる (並列実行のため)。
     * ClassInfo の用途は通常順序に依存しないが、テストで決定的順序を期待する場合は
     * {@code listener.onError} に渡された順や、自前で再ソートすること。</p>
     *
     * @param progress  進捗リスナー。null なら silent
     * @param cancel    キャンセルトークン。null なら NONE
     * @param mode      パース粒度 (FULL / HEADERS_ONLY)
     */
    public static ProjectParseResult extractFromProjectDetailed(File root,
            AndroidProjectScanner.Options opts,
            ErrorListener listener,
            ProgressListener progress,
            CancelToken cancel,
            boolean mergeManifest,
            ParseMode mode)
            throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        AndroidProjectScanner.Options scanOpts = (opts != null) ? opts
                : new AndroidProjectScanner.Options();
        scanOpts.includeAidl = true;
        scanOpts.includeKotlin = true;
        if (cancel != null && scanOpts.cancelToken == null) {
            scanOpts.cancelToken = cancel;
        }
        final ErrorListener err = listener != null ? listener : ErrorListener.silent();
        final ProgressListener prog = progress != null ? progress : ProgressListener.silent();
        final CancelToken can = cancel != null ? cancel : CancelToken.NONE;
        final ParseMode m = mode != null ? mode : ParseMode.FULL;

        prog.onProgress(0, -1, "Scanning files...");
        List<File> files = AndroidProjectScanner.scan(root, scanOpts);
        if (can.isCancelled()) {
            return new ProjectParseResult(new ArrayList<>(), new ClassIndex());
        }

        final int total = files.size();
        final List<JavaClassInfo> all = new ArrayList<>(total);
        final ClassIndex index = new ClassIndex();

        if (total == 0) {
            return new ProjectParseResult(all, index);
        }

        // FULL 解析時のみシンボル解決器を構築 (呼び出し先 FQN/シグネチャの解決用)。
        final juml.core.formats.java.jp.JpSolver solver =
                (m == ParseMode.FULL)
                        ? juml.core.formats.java.jp.JpSolver.fromSourceRoots(
                                deriveSourceRoots(files))
                        : null;

        int workers = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService pool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "UmlGenerator-parse");
            t.setDaemon(true);
            return t;
        });
        try {
            CompletionService<FileParseOutcome> cs = new ExecutorCompletionService<>(pool);
            int submitted = 0;
            for (File f : files) {
                String name = f.getName().toLowerCase();
                if (!name.endsWith(".java") && !name.endsWith(".aidl")
                        && !name.endsWith(".kt")) {
                    continue;
                }
                if (can.isCancelled()) {
                    break;
                }
                final File file = f;
                final String module = AndroidProjectAnalyzer.inferModuleName(root, file);
                cs.submit(new ParseTask(file, module, err, m, solver));
                submitted++;
            }
            int done = 0;
            for (int i = 0; i < submitted; i++) {
                if (can.isCancelled()) {
                    pool.shutdownNow();
                    break;
                }
                try {
                    FileParseOutcome out = cs.take().get();
                    if (out != null) {
                        for (JavaClassInfo c : out.classes) {
                            all.add(c);
                            index.put(c, out.file, out.module);
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ee) {
                    err.onError(null, -1, "parse task failed: " + ee.getMessage());
                }
                done++;
                prog.onProgress(done, submitted, null);
            }
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException ie) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (mergeManifest && root.isDirectory() && !can.isCancelled()) {
            mergeManifestInto(all, root, err);
            // index 側にも反映 (header と all は別インスタンスなので個別に)
            for (JavaClassInfo c : all) {
                if (c.getAndroidComponentType() != null) {
                    index.header(c.getQualifiedName())
                            .ifPresent(h -> h.setAndroidComponentType(c.getAndroidComponentType()));
                }
            }
        }
        // 依存 JAR/AAR の遅延ロード用カタログを構築 (Gradle 依存宣言から)
        DependencyJarIndex depIndex = buildDependencyIndex(root, err, can);
        return new ProjectParseResult(all, index, depIndex);
    }

    /**
     * プロジェクトの Gradle 解析結果から依存 JAR/AAR のインデックスを構築する。
     * AndroidProjectAnalyzer の {@link AndroidProjectAnalysis#getGradleByModule()} で
     * 取得した {@link GradleProjectInfo} の依存をフラットに結合し
     * {@link DependencyJarIndex#build(List, ErrorListener)} に渡す。
     * 失敗時は空の {@link DependencyJarIndex} を返す。
     */
    private static DependencyJarIndex buildDependencyIndex(File root,
                                                            ErrorListener err,
                                                            juml.util.CancelToken can) {
        if (root == null || !root.isDirectory() || can.isCancelled()) {
            return new DependencyJarIndex();
        }
        try {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(root, err);
            List<GradleDependency> all = new ArrayList<>();
            for (GradleProjectInfo info : analysis.getGradleByModule().values()) {
                all.addAll(info.getDependencies());
            }
            DependencyJarIndex idx = DependencyJarIndex.build(all, err);
            indexLocalJars(idx, root, analysis, err);
            return idx;
        } catch (IOException ex) {
            err.onError(null, -1, "dependency index build failed: " + ex.getMessage());
            return new DependencyJarIndex();
        }
    }

    /**
     * リポジトリに同梱されたローカル JAR/AAR をインデックスへ追加登録する。
     * Gradle cache に無い依存でも、プロジェクト内に実体があれば
     * {@code <<external>>} クラスとして解決できるようにするのが目的。
     *
     * <p>対象は各モジュールの build.gradle が宣言する
     * {@code files('...')} / {@code fileTree(dir: '...')} と、
     * 宣言が拾えなかった場合の保険としての慣習ディレクトリ {@code <module>/libs/}。</p>
     */
    private static void indexLocalJars(DependencyJarIndex idx, File root,
                                       AndroidProjectAnalysis analysis,
                                       ErrorListener err) {
        java.util.Set<java.nio.file.Path> done = new java.util.HashSet<>();
        for (Map.Entry<String, GradleProjectInfo> e
                : analysis.getGradleByModule().entrySet()) {
            java.nio.file.Path moduleDir = moduleDirOf(root, e.getKey());
            for (GradleDependency d : e.getValue().getDependencies()) {
                if (d.getFilePath() != null) {
                    java.nio.file.Path p = moduleDir.resolve(d.getFilePath()).normalize();
                    if (done.add(p)) {
                        idx.indexLocalArtifact(p, err);
                    }
                } else if (d.getFileTreeDir() != null) {
                    indexJarsUnder(idx, moduleDir.resolve(d.getFileTreeDir()).normalize(),
                            done, err);
                }
            }
            // 慣習の libs/ ディレクトリ (fileTree 宣言が .kts 方言などで
            // 拾えなかった場合の保険。存在しなければ何もしない)
            indexJarsUnder(idx, moduleDir.resolve("libs"), done, err);
        }
    }

    /** ディレクトリ直下 (2 階層まで) の *.jar / *.aar をローカル依存として索引する。 */
    private static void indexJarsUnder(DependencyJarIndex idx, java.nio.file.Path dir,
                                       java.util.Set<java.nio.file.Path> done,
                                       ErrorListener err) {
        if (!java.nio.file.Files.isDirectory(dir)) {
            return;
        }
        try (java.util.stream.Stream<java.nio.file.Path> walk
                     = java.nio.file.Files.walk(dir, 2)) {
            walk.filter(p -> {
                String fn = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                return fn.endsWith(".jar") || fn.endsWith(".aar");
            }).forEach(p -> {
                java.nio.file.Path n = p.normalize();
                if (done.add(n)) {
                    idx.indexLocalArtifact(n, err);
                }
            });
        } catch (IOException ex) {
            err.onError(dir.toString(), -1,
                    "local jar scan failed: " + ex.getMessage());
        }
    }

    /**
     * {@link juml.core.formats.android.AndroidProjectAnalyzer#inferModuleName(File, File)}
     * が返すモジュール名 (例: {@code app} / {@code core:common} / {@code :root}) から
     * モジュールディレクトリを復元する。
     */
    private static java.nio.file.Path moduleDirOf(File root, String moduleName) {
        if (moduleName == null || moduleName.isEmpty() || ":root".equals(moduleName)) {
            return root.toPath();
        }
        return root.toPath().resolve(moduleName.replace(':', File.separatorChar));
    }

    private static final class FileParseOutcome {
        final File file;
        final String module;
        final List<JavaClassInfo> classes;

        FileParseOutcome(File file, String module, List<JavaClassInfo> classes) {
            this.file = file;
            this.module = module;
            this.classes = classes;
        }
    }

    /** 標準的なレイアウト ({@code .../src/<set>/java|kotlin/...}) からソースルート群を推定する。 */
    private static java.util.Set<File> deriveSourceRoots(List<File> files) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "^(.*[/\\\\]src[/\\\\][^/\\\\]+[/\\\\](?:java|kotlin))[/\\\\]");
        java.util.LinkedHashSet<File> roots = new java.util.LinkedHashSet<>();
        for (File f : files) {
            java.util.regex.Matcher m = p.matcher(f.getPath());
            if (m.find()) {
                roots.add(new File(m.group(1)));
            }
        }
        return roots;
    }

    private static final class ParseTask implements Callable<FileParseOutcome> {
        private final File file;
        private final String module;
        private final ErrorListener err;
        private final ParseMode mode;
        private final juml.core.formats.java.jp.JpSolver solver;

        ParseTask(File file, String module, ErrorListener err, ParseMode mode,
                  juml.core.formats.java.jp.JpSolver solver) {
            this.file = file;
            this.module = module;
            this.err = err;
            this.mode = mode;
            this.solver = solver;
        }

        @Override
        public FileParseOutcome call() {
            String src;
            try {
                src = AndroidProjectScanner.readFile(file);
            } catch (IOException ex) {
                err.onError(file.getName(), -1, "read failed: " + ex.getMessage());
                return new FileParseOutcome(file, module, new ArrayList<>());
            }
            List<JavaClassInfo> classes;
            try {
                if (mode == ParseMode.HEADERS_ONLY) {
                    classes = extractHeadersFromSource(src, file.getName(), err);
                } else {
                    classes = extractFromSource(src, file.getName(), err, solver);
                }
            } catch (RuntimeException ex) {
                err.onError(file.getName(), -1, "parse failed: " + ex.getMessage());
                classes = new ArrayList<>();
            }
            return new FileParseOutcome(file, module, classes);
        }
    }

    /**
     * 指定ディレクトリ配下の AndroidManifest.xml を解析し、対応するクラスに
     * {@code androidComponentType} を反映する。
     */
    public static void mergeManifestInto(List<JavaClassInfo> classes, File root,
                                          ErrorListener listener) {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        try {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(root, l);
            for (AndroidComponentInfo c : analysis.allComponents()) {
                String fqn = c.getName();
                if (fqn == null || fqn.isEmpty()) {
                    continue;
                }
                for (JavaClassInfo cls : classes) {
                    if (matches(cls, fqn)) {
                        cls.setAndroidComponentType(c.getKind().label());
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            l.onError(null, -1, "manifest merge failed: " + ex.getMessage());
        }
    }

    private static boolean matches(JavaClassInfo cls, String fqn) {
        if (cls.getQualifiedName().equals(fqn)) {
            return true;
        }
        // simple name match (manifest 側で package が省略されている場合の救済)
        int dot = fqn.lastIndexOf('.');
        if (dot >= 0) {
            String simple = fqn.substring(dot + 1);
            return cls.getSimpleName().equals(simple);
        }
        return cls.getSimpleName().equals(fqn);
    }

    /** クラス図 PlantUML テキストを Java ソース文字列 1 つから生成。 */
    public static String classDiagram(String source) {
        return PlantUmlClassDiagram.generate(extractFromSource(source, null));
    }

    /** クラス図 PlantUML テキストをプロジェクトから生成。 */
    public static String classDiagramFromProject(File root) throws IOException {
        return PlantUmlClassDiagram.generate(extractFromProject(root));
    }

    /** Java ソース内の特定メソッドからシーケンス図を生成。 */
    public static String sequenceDiagram(String source, String entryClass, String entryMethod) {
        return PlantUmlSequenceDiagram.generate(
                extractFromSource(source, null), entryClass, entryMethod, null);
    }

    /** プロジェクト内の特定メソッドからシーケンス図を生成。 */
    public static String sequenceDiagramFromProject(File root,
                                                     String entryClass,
                                                     String entryMethod) throws IOException {
        return PlantUmlSequenceDiagram.generate(
                extractFromProject(root), entryClass, entryMethod, null);
    }
}
