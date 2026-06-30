// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.util.ErrorListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ユーザーが指定した任意パスの {@code .jar} / {@code .aar} / {@code .class} ファイル
 * (またはそれらを含むディレクトリ) を、解析対象として {@link JavaClassInfo} のリストへ
 * 直接展開するローダ。
 *
 * <p>{@link DependencyJarIndex} は Gradle 依存宣言を {@code ~/.gradle}/{@code ~/.m2}
 * から探す経路に特化しているが、本クラスは「任意パスのアーカイブそのものを読む」用途を担う。
 * {@code .class} のヘッダ抽出には {@link ExternalClassReader} を再利用し、得られる
 * {@link JavaClassInfo} の {@link JavaClassInfo#getOrigin()} は
 * {@link JavaClassInfo.Origin#EXTERNAL_JAR} になる。</p>
 *
 * <p>メソッド本体 (バイトコード) は読まず、クラス宣言・親クラス・実装インタフェース・
 * 公開メンバーのシグネチャだけを抽出する (ASM の {@code SKIP_CODE})。</p>
 */
public final class ArchiveClassReader {

    private ArchiveClassReader() {
    }

    /**
     * 入力が {@code .jar}/{@code .aar}/{@code .class} か、またはそれらを含むディレクトリかを判定する。
     */
    public static boolean isArchiveInput(File f) {
        if (f == null || !f.exists()) {
            return false;
        }
        if (f.isFile()) {
            return hasArchiveExtension(f.getName());
        }
        // ディレクトリ: 直下/再帰にアーカイブが 1 つでもあれば true
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(f.toPath())) {
            return walk.anyMatch(p -> Files.isRegularFile(p)
                    && hasArchiveExtension(p.getFileName().toString()));
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean hasArchiveExtension(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jar") || n.endsWith(".aar") || n.endsWith(".class");
    }

    /**
     * 拡張子・ディレクトリを自動判別して読み込む。ディレクトリの場合は配下の
     * {@code .jar}/{@code .aar}/{@code .class} を再帰的に収集して結合する。
     */
    public static List<JavaClassInfo> read(File input, ErrorListener listener) throws IOException {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        if (input == null || !input.exists()) {
            throw new IOException("input not found: " + input);
        }
        if (input.isDirectory()) {
            return readDirectory(input, l);
        }
        String name = input.getName().toLowerCase();
        if (name.endsWith(".jar")) {
            return readJar(input, l);
        }
        if (name.endsWith(".aar")) {
            return readAar(input, l);
        }
        if (name.endsWith(".class")) {
            return readClassFile(input, l);
        }
        throw new IOException("unsupported archive input (expected .jar/.aar/.class): "
                + input.getName());
    }

    /** ディレクトリ配下の全 {@code .jar}/{@code .aar}/{@code .class} を再帰収集して結合する。 */
    private static List<JavaClassInfo> readDirectory(File dir, ErrorListener l) throws IOException {
        List<File> archives = new ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(dir.toPath())) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> hasArchiveExtension(p.getFileName().toString()))
                    .forEach(p -> archives.add(p.toFile()));
        }
        List<JavaClassInfo> all = new ArrayList<>();
        for (File f : archives) {
            try {
                all.addAll(read(f, l));
            } catch (IOException ex) {
                l.onError(f.getPath(), -1, "failed to read archive: " + ex.getMessage());
            }
        }
        return all;
    }

    /** 単一 {@code .class} ファイルを読み込む。 */
    public static List<JavaClassInfo> readClassFile(File classFile, ErrorListener listener)
            throws IOException {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        List<JavaClassInfo> out = new ArrayList<>(1);
        try (InputStream in = Files.newInputStream(classFile.toPath())) {
            out.add(ExternalClassReader.readHeader(in, classFile.getPath()));
        } catch (IOException ex) {
            l.onError(classFile.getPath(), -1, "failed to read .class: " + ex.getMessage());
            throw ex;
        }
        return out;
    }

    /** {@code .jar} 内の全 {@code .class} (内部クラスを除く) を読み込む。 */
    public static List<JavaClassInfo> readJar(File jar, ErrorListener listener) throws IOException {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        try (InputStream in = Files.newInputStream(jar.toPath())) {
            return readClassesFromZip(in, jar.getPath(), l);
        }
    }

    /** {@code .aar} 内の {@code classes.jar} を展開し、その中の {@code .class} を読み込む。 */
    public static List<JavaClassInfo> readAar(File aar, ErrorListener listener) throws IOException {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        byte[] classesJar = extractClassesJar(aar);
        if (classesJar == null) {
            l.onError(aar.getPath(), -1, "aar has no classes.jar");
            return new ArrayList<>();
        }
        return readClassesFromZip(new ByteArrayInputStream(classesJar), aar.getPath(), l);
    }

    /**
     * ZIP/JAR ストリームを走査して各 {@code .class} を {@link ExternalClassReader} で読む。
     * 内部クラス ({@code $} を含む) は検索ノイズになるためスキップする。
     */
    private static List<JavaClassInfo> readClassesFromZip(InputStream in, String archivePath,
                                                          ErrorListener l) throws IOException {
        List<JavaClassInfo> out = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                String entryName = e.getName();
                if (!entryName.endsWith(".class")) {
                    continue;
                }
                String fqnPart = entryName.substring(0, entryName.length() - ".class".length());
                if (fqnPart.contains("$") || entryName.endsWith("module-info.class")
                        || entryName.endsWith("package-info.class")) {
                    continue;
                }
                try {
                    out.add(ExternalClassReader.readHeader(zip, archivePath));
                } catch (IOException ex) {
                    l.onError(archivePath, -1,
                            "failed to read class " + entryName + ": " + ex.getMessage());
                }
            }
        }
        return out;
    }

    /** AAR ファイル内の {@code classes.jar} をバイト列としてメモリに展開する。なければ null。 */
    private static byte[] extractClassesJar(File aar) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(aar.toPath()))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if ("classes.jar".equals(e.getName())) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream(
                            Math.max(1024, (int) Math.min(Math.max(e.getSize(), 0), Integer.MAX_VALUE)));
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
}
