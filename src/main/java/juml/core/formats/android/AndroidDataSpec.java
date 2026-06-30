// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * AndroidManifest.xml の {@code <intent-filter>} 配下にある単一の
 * {@code <data>} 要素を表現する。
 *
 * <p>既存の {@code AndroidIntentFilter} は scheme と mimeType のみを
 * フラットなリストとして保持していたが、Deep Link / App Links の解析には
 * 1 つの {@code <data>} 要素に含まれる scheme + host + path 系属性を
 * 組として扱う必要がある。本クラスはその「組」を保持する。</p>
 */
public class AndroidDataSpec {

    private String scheme;
    private String host;
    private String port;
    private String path;
    private String pathPrefix;
    private String pathPattern;
    private String pathSuffix;
    private String pathAdvancedPattern;
    private String mimeType;

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    public String getPathSuffix() {
        return pathSuffix;
    }

    public void setPathSuffix(String pathSuffix) {
        this.pathSuffix = pathSuffix;
    }

    public String getPathAdvancedPattern() {
        return pathAdvancedPattern;
    }

    public void setPathAdvancedPattern(String pathAdvancedPattern) {
        this.pathAdvancedPattern = pathAdvancedPattern;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /** scheme / host / port / path 系の何かしらが指定されていれば URI 系 data とみなす。 */
    public boolean hasUriComponent() {
        return notEmpty(scheme) || notEmpty(host) || notEmpty(port)
                || notEmpty(path) || notEmpty(pathPrefix) || notEmpty(pathPattern)
                || notEmpty(pathSuffix) || notEmpty(pathAdvancedPattern);
    }

    /**
     * Deep Link URI 表現を組み立てる。
     * {@code scheme://host[:port]/path} の形式で、未指定要素はワイルドカード
     * ({@code *}) で補う。host も path 系も未指定なら null を返す。
     */
    public String toDeepLinkUri() {
        if (!hasUriComponent()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(notEmpty(scheme) ? scheme : "*").append("://");
        sb.append(notEmpty(host) ? host : "*");
        if (notEmpty(port)) {
            sb.append(':').append(port);
        }
        String p = effectivePath();
        if (p != null) {
            if (!p.startsWith("/")) {
                sb.append('/');
            }
            sb.append(p);
        }
        return sb.toString();
    }

    /**
     * path / pathPrefix / pathPattern / pathSuffix / pathAdvancedPattern の
     * 中で最初に値があるものを優先表示用に返す。
     */
    public String effectivePath() {
        if (notEmpty(path)) {
            return path;
        }
        if (notEmpty(pathPrefix)) {
            return pathPrefix + "*";
        }
        if (notEmpty(pathPattern)) {
            return pathPattern;
        }
        if (notEmpty(pathSuffix)) {
            return "*" + pathSuffix;
        }
        if (notEmpty(pathAdvancedPattern)) {
            return pathAdvancedPattern;
        }
        return null;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
