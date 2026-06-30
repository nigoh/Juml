// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.java.AndroidProjectScanner;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * プロジェクト全体のクラス情報を保持するインデックス。
 *
 * <p>Stage A (ヘッダのみ) を全件メモリに持ち、必要に応じて Stage B (詳細) を
 * オンデマンドで取得する 2 相構造。AOSP 級プロジェクトでも全件 Stage A だけなら
 * 数十 MB 程度に収まる。</p>
 *
 * <p>保持する情報:</p>
 * <ul>
 *   <li>{@code headers}: 完全修飾名 → ヘッダのみ {@link JavaClassInfo}</li>
 *   <li>{@code sourceByQn}: 完全修飾名 → ソースファイル (Stage B 昇格時の再読み込み元)</li>
 *   <li>{@code qnToModule}: 完全修飾名 → Gradle モジュール名 ({@code ":app"} など)</li>
 *   <li>{@code detailedCache}: 一度 Stage B 化したクラスのキャッシュ (再パース回避)</li>
 * </ul>
 */
public final class ClassIndex {

    /**
     * {@code detailedCache} の既定上限 (件数)。長時間のナビゲーションで詳細パース結果が
     * 際限なくメモリに滞留する問題を防ぐため、LRU で古いものから退避する。
     * システムプロパティ {@code juml.detailCacheSize} で上書き可能 (0 以下で無制限)。
     */
    private static final int DEFAULT_DETAIL_CACHE_MAX = 4096;

    private final Map<String, JavaClassInfo> headers = new LinkedHashMap<>();
    private final Map<String, File> sourceByQn = new LinkedHashMap<>();
    private final Map<String, String> qnToModule = new LinkedHashMap<>();
    /** 一度 Stage B 化したクラスの LRU キャッシュ (再パース回避 / 上限超過で古いものを退避)。 */
    private final Map<String, JavaClassInfo> detailedCache;
    private final int detailCacheMax;

    public ClassIndex() {
        this(resolveDetailCacheMax());
    }

    /** テスト用: 詳細キャッシュ上限を指定して生成する (0 以下で無制限)。 */
    ClassIndex(int detailCacheMax) {
        this.detailCacheMax = detailCacheMax;
        this.detailedCache = Collections.synchronizedMap(
                new LinkedHashMap<String, JavaClassInfo>(256, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(
                            Map.Entry<String, JavaClassInfo> eldest) {
                        return detailCacheMax > 0 && size() > detailCacheMax;
                    }
                });
    }

    private static int resolveDetailCacheMax() {
        String v = System.getProperty("juml.detailCacheSize");
        if (v != null) {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException ignore) {
                // 不正値は既定にフォールバック
            }
        }
        return DEFAULT_DETAIL_CACHE_MAX;
    }

    /** ヘッダクラス情報とソースファイル/モジュールを 1 件登録する。 */
    public synchronized void put(JavaClassInfo header, File source, String module) {
        if (header == null) {
            return;
        }
        String qn = header.getQualifiedName();
        headers.put(qn, header);
        if (source != null) {
            sourceByQn.put(qn, source);
        }
        if (module != null) {
            qnToModule.put(qn, module);
        }
    }

    /** 別の ClassIndex の内容を取り込む。並列パース結果のマージに使用。 */
    public synchronized void merge(ClassIndex other) {
        if (other == null) {
            return;
        }
        headers.putAll(other.headers);
        sourceByQn.putAll(other.sourceByQn);
        qnToModule.putAll(other.qnToModule);
        detailedCache.putAll(other.detailedCache);
    }

    /** Stage A のヘッダ全件 (登録順)。 */
    public synchronized List<JavaClassInfo> headers() {
        return new ArrayList<>(headers.values());
    }

    /** 完全修飾名のセット (登録順)。 */
    public synchronized Collection<String> qualifiedNames() {
        return new ArrayList<>(headers.keySet());
    }

    /** 完全修飾名からヘッダクラス情報を引く。 */
    public synchronized Optional<JavaClassInfo> header(String qn) {
        return Optional.ofNullable(headers.get(qn));
    }

    /** 完全修飾名 → モジュール名 (Gradle 解析由来) を引く。 */
    public synchronized Optional<String> module(String qn) {
        return Optional.ofNullable(qnToModule.get(qn));
    }

    /** 完全修飾名 → ソースファイルを引く。 */
    public synchronized Optional<File> source(String qn) {
        return Optional.ofNullable(sourceByQn.get(qn));
    }

    /** モジュール紐付けマップ (読み取り専用)。 */
    public synchronized Map<String, String> moduleMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(qnToModule));
    }

    /** 含まれるクラス数 (Stage A ベース)。 */
    public synchronized int size() {
        return headers.size();
    }

    /**
     * 指定 QN を Stage B (詳細) に昇格させる。
     *
     * <p>ソースファイルを再読み込みしてフルパースを実行し、対応する QN のクラス情報を
     * 返す。初回呼び出しのみパースが走り、以降はキャッシュ参照。AAOS カテゴリ・
     * AndroidComponentType はヘッダから引き継ぐ。</p>
     *
     * @return 詳細化済み ClassInfo。ソース未登録 / パース失敗時は元のヘッダを返す
     */
    public JavaClassInfo detail(String qn, ErrorListener listener) {
        JavaClassInfo cached = detailedCache.get(qn);
        if (cached != null) {
            return cached;
        }
        File source;
        JavaClassInfo header;
        synchronized (this) {
            source = sourceByQn.get(qn);
            header = headers.get(qn);
        }
        if (source == null || header == null) {
            return header;
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        String src;
        try {
            src = AndroidProjectScanner.readFile(source);
        } catch (IOException ex) {
            l.onError(source.getName(), -1, "detail read failed: " + ex.getMessage());
            return header;
        }
        List<JavaClassInfo> full;
        try {
            full = UmlGenerator.extractFromSource(src, source.getName(), l);
        } catch (RuntimeException ex) {
            l.onError(source.getName(), -1, "detail parse failed: " + ex.getMessage());
            return header;
        }
        JavaClassInfo found = null;
        for (JavaClassInfo c : full) {
            if (qn.equals(c.getQualifiedName())) {
                found = c;
            }
            // 同じファイル内の全クラスをキャッシュ。
            // headers は LinkedHashMap (非スレッドセーフ) で put() は synchronized のため、
            // 並行する Stage B 昇格と put() の競合を避けるべく get() も同じロックで保護する。
            if (c.getAndroidComponentType() == null) {
                JavaClassInfo h;
                synchronized (this) {
                    h = headers.get(c.getQualifiedName());
                }
                if (h != null && h.getAndroidComponentType() != null) {
                    c.setAndroidComponentType(h.getAndroidComponentType());
                }
            }
            c.setDetailed(true);
            detailedCache.put(c.getQualifiedName(), c);
        }
        return found != null ? found : header;
    }

    /** テスト用: 現在の詳細キャッシュ件数。 */
    int detailedCacheSize() {
        return detailedCache.size();
    }

    /** キャッシュをクリア。 */
    public synchronized void clear() {
        headers.clear();
        sourceByQn.clear();
        qnToModule.clear();
        detailedCache.clear();
    }
}
