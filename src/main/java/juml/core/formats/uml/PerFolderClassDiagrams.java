// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.java.AndroidProjectScanner;
import juml.util.ErrorListener;
import juml.util.ProgressListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * プロジェクトを再帰スキャンし、ソースファイルを直接含む各フォルダごとに
 * 1 枚ずつ PlantUML クラス図 (.puml + .svg) を出力するユーティリティ。
 *
 * <p>{@code projectRoot} を起点とした相対パスを保ったまま {@code outputDir} 配下に
 * サブディレクトリを作成し、それぞれの中に {@code classes.puml} と {@code classes.svg}
 * を書き出す。例: {@code <root>/com/example/ui/Foo.java} →
 * {@code <outputDir>/com/example/ui/classes.{puml,svg}}。</p>
 *
 * <p>CLI ({@code -c --per-folder}) と GUI (File メニュー) の双方から共通利用される。</p>
 */
public final class PerFolderClassDiagrams {

    /** 出力ファイル名 (拡張子別)。 */
    public static final String PUML_NAME = "classes.puml";
    public static final String SVG_NAME = "classes.svg";

    /** 出力結果サマリ。 */
    public static final class Result {
        private final int folderCount;
        private final int classCount;
        private final List<File> writtenFiles;

        Result(int folderCount, int classCount, List<File> writtenFiles) {
            this.folderCount = folderCount;
            this.classCount = classCount;
            this.writtenFiles = writtenFiles;
        }

        /** クラス図を書き出したフォルダ数 (= 出力先サブディレクトリ数)。 */
        public int getFolderCount() {
            return folderCount;
        }

        /** 図に含めたクラス総数 (ソース未解決でスキップしたものは除く)。 */
        public int getClassCount() {
            return classCount;
        }

        /** 書き出した全ファイル ({@code .puml} と {@code .svg} のペア)。 */
        public List<File> getWrittenFiles() {
            return Collections.unmodifiableList(writtenFiles);
        }
    }

    private PerFolderClassDiagrams() {
    }

    /**
     * 既存解析結果 (GUI 側で既に読み込み済みなど) を流用するエントリ。
     *
     * @param projectRoot プロジェクトルート (相対パス計算の基準)
     * @param outputDir   出力先ディレクトリ (存在しなければ作成)
     * @param classes     対象クラス集合
     * @param index       {@link ClassIndex#source(String)} でソースファイルを引けるインデックス
     * @param clsOpts     PlantUML クラス図オプション (null で既定)
     * @param progress    進捗リスナー (null で silent)
     * @param listener    警告リスナー (null で silent)
     */
    public static Result generate(
            File projectRoot,
            File outputDir,
            List<JavaClassInfo> classes,
            ClassIndex index,
            PlantUmlClassDiagram.Options clsOpts,
            ProgressListener progress,
            ErrorListener listener) throws IOException {
        if (projectRoot == null) {
            throw new IllegalArgumentException("projectRoot is null");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir is null");
        }
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        ErrorListener err = listener != null ? listener : ErrorListener.silent();
        ProgressListener prog = progress != null ? progress : ProgressListener.silent();

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDir);
        }
        if (!outputDir.isDirectory()) {
            throw new IOException("Output is not a directory: " + outputDir);
        }

        Map<File, List<JavaClassInfo>> byFolder = groupByFolder(
                projectRoot, classes, index, err);
        if (byFolder.isEmpty()) {
            return new Result(0, 0, Collections.emptyList());
        }

        Path rootPath = projectRoot.getAbsoluteFile().toPath().normalize();
        List<File> written = new ArrayList<>(byFolder.size() * 2);
        int total = byFolder.size();
        int done = 0;
        int totalClasses = 0;

        prog.onProgress(0, total, "Generating per-folder class diagrams...");
        for (Map.Entry<File, List<JavaClassInfo>> entry : byFolder.entrySet()) {
            File folder = entry.getKey();
            List<JavaClassInfo> infos = entry.getValue();
            done++;
            if (infos.isEmpty()) {
                prog.onProgress(done, total, null);
                continue;
            }

            File subDir = resolveSubDir(rootPath, folder, outputDir);
            if (!subDir.exists() && !subDir.mkdirs()) {
                err.onError(juml.util.ErrorCode.EXP_005, folder.getAbsolutePath(), -1,
                        "Failed to create output subdir: " + subDir);
                prog.onProgress(done, total, null);
                continue;
            }

            String puml = PlantUmlClassDiagram.generate(infos, clsOpts);
            File pumlFile = new File(subDir, PUML_NAME);
            File svgFile = new File(subDir, SVG_NAME);
            try {
                writeText(pumlFile, puml);
                written.add(pumlFile);
            } catch (IOException ex) {
                err.onError(juml.util.ErrorCode.EXP_005, pumlFile.getAbsolutePath(), -1,
                        "Failed to write " + PUML_NAME + ": " + ex.getMessage());
                prog.onProgress(done, total, null);
                continue;
            }
            try {
                PlantUmlRenderer.renderSvg(puml, svgFile);
                written.add(svgFile);
            } catch (IOException | juml.util.JumlException ex) {
                // 描画失敗 (unchecked の PlantUmlRenderFailedException 含む) でも
                // 1 フォルダの失敗で全体を止めず、次のフォルダへ続行する。
                err.onError(juml.util.JumlException.codeOf(ex, juml.util.ErrorCode.UML_R007),
                        svgFile.getAbsolutePath(), -1,
                        "Failed to render " + SVG_NAME + ": " + ex.getMessage());
            }
            totalClasses += infos.size();
            prog.onProgress(done, total, subDir.getName());
        }

        return new Result(byFolder.size(), totalClasses, written);
    }

    /**
     * CLI から呼ぶ便利オーバーロード。内部で
     * {@link UmlGenerator#extractFromProjectDetailed} を使ってスキャンと解析を行う。
     *
     * @param projectRoot     プロジェクトルート (ディレクトリ)
     * @param outputDir       出力先ディレクトリ
     * @param scanOpts        スキャンオプション (null で既定)
     * @param clsOpts         クラス図オプション (null で既定)
     * @param mergeManifest   AndroidManifest 連携を行うか
     * @param progress        進捗リスナー (null で silent)
     * @param listener        警告リスナー (null で silent)
     */
    public static Result generate(
            File projectRoot,
            File outputDir,
            AndroidProjectScanner.Options scanOpts,
            PlantUmlClassDiagram.Options clsOpts,
            boolean mergeManifest,
            ProgressListener progress,
            ErrorListener listener) throws IOException {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            throw new IllegalArgumentException(
                    "projectRoot must be an existing directory: " + projectRoot);
        }
        UmlGenerator.ProjectParseResult parsed = UmlGenerator.extractFromProjectDetailed(
                projectRoot, scanOpts, listener, progress, null,
                mergeManifest, UmlGenerator.ParseMode.FULL);
        return generate(projectRoot, outputDir, parsed.getClasses(), parsed.getIndex(),
                clsOpts, progress, listener);
    }

    // --- 内部ヘルパ ---------------------------------------------------------

    private static Map<File, List<JavaClassInfo>> groupByFolder(
            File projectRoot,
            List<JavaClassInfo> classes,
            ClassIndex index,
            ErrorListener err) {
        Map<File, List<JavaClassInfo>> byFolder = new LinkedHashMap<>();
        Path rootPath = projectRoot.getAbsoluteFile().toPath().normalize();
        for (JavaClassInfo info : classes) {
            if (info == null) {
                continue;
            }
            Optional<File> src = index.source(info.getQualifiedName());
            if (src.isEmpty()) {
                err.onError(info.getQualifiedName(), -1,
                        "skip: source file unknown (per-folder)");
                continue;
            }
            File parent = src.get().getAbsoluteFile().getParentFile();
            if (parent == null) {
                continue;
            }
            Path parentPath = parent.toPath().normalize();
            if (!parentPath.startsWith(rootPath)) {
                err.onError(info.getQualifiedName(), -1,
                        "skip: source outside project root (per-folder)");
                continue;
            }
            byFolder.computeIfAbsent(parent, k -> new ArrayList<>()).add(info);
        }
        return byFolder;
    }

    private static File resolveSubDir(Path rootPath, File folder, File outputDir) {
        Path folderPath = folder.toPath().normalize();
        Path rel = rootPath.relativize(folderPath);
        if (rel.toString().isEmpty()) {
            return outputDir;
        }
        return new File(outputDir, rel.toString());
    }

    private static void writeText(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }
}
