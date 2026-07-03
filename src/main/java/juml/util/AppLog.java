// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * アプリ全体の軽量ロギング基盤。外部ライブラリ (SLF4J/Logback 等) には依存しない。
 *
 * <p>ログは 3 つの宛先に流れる:</p>
 * <ul>
 *   <li><b>メモリ上のリングバッファ</b> — GUI のログビューア ({@code LogViewerDialog})
 *       が最新 N 件を表示するために参照する。</li>
 *   <li><b>ログファイル</b> — {@code <basePath>/logs/juml.log}。一定サイズで 1 世代だけ
 *       ローテーション ({@code juml.log.1})。アプリ再起動後も原因調査できるようにする。</li>
 *   <li><b>リスナー</b> — ビューアが開いている間、1 件ごとにライブ追記される。</li>
 * </ul>
 *
 * <p>{@link #init()} で初期化すると、未捕捉例外ハンドラの設置と
 * {@code System.err} のティー (元の出力を保ちつつログにも記録) を行うため、既存の
 * {@code System.err.println} 出力やスタックトレースも自動でログに取り込まれる。</p>
 *
 * <p>本クラスは {@code System.err}/{@code System.out} へは決して書き込まない
 * (ティーとの無限再帰を避けるため)。ファイル書き込みに失敗した場合はファイル出力を
 * 黙って無効化し、メモリ + リスナーへの記録は継続する。</p>
 */
public final class AppLog {

    /** ログレベル。 */
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    /** ログ 1 件分の不変レコード。 */
    public static final class Entry {
        private final long seq;
        private final long timeMillis;
        private final Level level;
        /** エラーカタログ ID。ID なしのログは {@link ErrorCode#NONE}。 */
        private final ErrorCode code;
        private final String thread;
        private final String message;
        /** スタックトレース文字列 (例外なしなら null)。 */
        private final String detail;

        Entry(long seq, long timeMillis, Level level, ErrorCode code, String thread,
              String message, String detail) {
            this.seq = seq;
            this.timeMillis = timeMillis;
            this.level = level;
            this.code = code != null ? code : ErrorCode.NONE;
            this.thread = thread == null ? "" : thread;
            this.message = message == null ? "" : message;
            this.detail = detail;
        }

        /** 生成順に単調増加する一意な連番。ビューアの重複排除に使う。 */
        public long getSeq() {
            return seq;
        }

        public long getTimeMillis() {
            return timeMillis;
        }

        public Level getLevel() {
            return level;
        }

        /** エラーカタログ ID (ID なしのログは {@link ErrorCode#NONE})。 */
        public ErrorCode getCode() {
            return code;
        }

        public String getThread() {
            return thread;
        }

        public String getMessage() {
            return message;
        }

        /** スタックトレース等の詳細 (なければ null)。 */
        public String getDetail() {
            return detail;
        }

        /** 時刻部分を {@code HH:mm:ss.SSS} で返す。 */
        public String formatTime() {
            return TIME_FMT.format(Instant.ofEpochMilli(timeMillis));
        }

        /**
         * ファイル / クリップボード向けの 1 行表現 (詳細は含まない)。
         * エラー ID を {@code [UML-R001]} 形式で埋め込む (ID なしは {@code [-]})。
         */
        public String formatLine() {
            return DATE_TIME_FMT.format(Instant.ofEpochMilli(timeMillis))
                    + " [" + pad(level.name()) + "] " + code.tag()
                    + " [" + thread + "] " + message;
        }

        private static String pad(String s) {
            StringBuilder sb = new StringBuilder(s);
            while (sb.length() < 5) {
                sb.append(' ');
            }
            return sb.toString();
        }
    }

    /** ログ追記を購読するリスナー (GUI ビューア用)。 */
    @FunctionalInterface
    public interface Listener {
        void onLog(Entry entry);
    }

    /** リングバッファの最大保持件数。 */
    private static final int MAX_ENTRIES = 5000;

    /** ローテーション閾値 (バイト)。これを超えたら {@code .1} へ退避する。 */
    private static final long ROTATE_BYTES = 2L * 1024 * 1024;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZONE);
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZONE);

    private static final Object LOCK = new Object();
    private static final Deque<Entry> BUFFER = new ArrayDeque<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final java.util.concurrent.atomic.AtomicLong SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    private static volatile boolean initialized;
    private static volatile Level minLevel = Level.DEBUG;
    private static File logFile;
    private static PrintWriter writer;
    private static boolean fileLoggingDisabled;
    /** ティー設置前の元 {@code System.err} (コンソール出力の退行を防ぐため保持)。 */
    private static volatile PrintStream consoleErr;

    private AppLog() {
    }

    /**
     * ロギング基盤を初期化する。アプリ起動の最初期に 1 度だけ呼ぶこと。
     *
     * <p>ログファイルを開き、未捕捉例外ハンドラと {@code System.err} ティーを設置する。
     * 2 回目以降の呼び出しは無視される (冪等)。GUI 非依存なので CLI でも安全。</p>
     */
    public static void init() {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            initialized = true;
            openLogFile();
        }
        installUncaughtHandler();
        installStderrTee();
        info("AppLog", "Juml started; logging to "
                + (logFile != null ? logFile.getAbsolutePath() : "(memory only)"));
    }

    /** 記録する最小レベルを設定する (これ未満は破棄)。既定は DEBUG。 */
    public static void setMinLevel(Level level) {
        if (level != null) {
            minLevel = level;
        }
    }

    public static void debug(String source, String message) {
        record(Level.DEBUG, source, message, null);
    }

    public static void info(String source, String message) {
        record(Level.INFO, source, message, null);
    }

    /**
     * ID なし WARN。原則 {@link #warn(ErrorCode, String, String)} を使うこと
     * (採番ルールは {@code .claude/rules/error-logging.md})。
     */
    public static void warn(String source, String message) {
        record(Level.WARN, ErrorCode.NONE, source, message, null);
    }

    /** ID なし WARN (例外付き)。原則 ErrorCode 付きのオーバーロードを使うこと。 */
    public static void warn(String source, String message, Throwable t) {
        record(Level.WARN, ErrorCode.NONE, source, message, t);
    }

    /** エラー ID 付き WARN。 */
    public static void warn(ErrorCode code, String source, String message) {
        record(Level.WARN, code, source, message, null);
    }

    /** エラー ID 付き WARN (例外付き)。 */
    public static void warn(ErrorCode code, String source, String message, Throwable t) {
        record(Level.WARN, code, source, message, t);
    }

    /**
     * ID なし ERROR。原則 {@link #error(ErrorCode, String, String)} を使うこと
     * (採番ルールは {@code .claude/rules/error-logging.md})。
     */
    public static void error(String source, String message) {
        record(Level.ERROR, ErrorCode.NONE, source, message, null);
    }

    /** ID なし ERROR (例外付き)。原則 ErrorCode 付きのオーバーロードを使うこと。 */
    public static void error(String source, String message, Throwable t) {
        record(Level.ERROR, ErrorCode.NONE, source, message, t);
    }

    /** エラー ID 付き ERROR。 */
    public static void error(ErrorCode code, String source, String message) {
        record(Level.ERROR, code, source, message, null);
    }

    /** エラー ID 付き ERROR (例外付き)。 */
    public static void error(ErrorCode code, String source, String message, Throwable t) {
        record(Level.ERROR, code, source, message, t);
    }

    /** 現在のリングバッファのスナップショット (古い順)。 */
    public static List<Entry> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<>(BUFFER);
        }
    }

    /** リングバッファを空にする (ファイルやリスナーには影響しない)。 */
    public static void clearBuffer() {
        synchronized (LOCK) {
            BUFFER.clear();
        }
    }

    public static void addListener(Listener l) {
        if (l != null) {
            LISTENERS.add(l);
        }
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    /** 現在のログファイル (未初期化 / ファイル無効時は null)。 */
    public static File getLogFile() {
        synchronized (LOCK) {
            return logFile;
        }
    }

    // ── 内部 ───────────────────────────────────────────────────────────

    private static void record(Level level, String source, String message, Throwable t) {
        record(level, ErrorCode.NONE, source, message, t);
    }

    private static void record(Level level, ErrorCode code, String source,
            String message, Throwable t) {
        if (level.ordinal() < minLevel.ordinal()) {
            return;
        }
        String thread = Thread.currentThread().getName();
        String msg = (source != null && !source.isEmpty())
                ? source + ": " + message
                : (message == null ? "" : message);
        String detail = t != null ? stackTraceOf(t) : null;
        Entry entry = new Entry(SEQ.getAndIncrement(), System.currentTimeMillis(),
                level, code, thread, msg, detail);
        synchronized (LOCK) {
            BUFFER.addLast(entry);
            while (BUFFER.size() > MAX_ENTRIES) {
                BUFFER.removeFirst();
            }
            writeToFile(entry);
        }
        notifyListeners(entry);
    }

    private static void notifyListeners(Entry entry) {
        for (Listener l : LISTENERS) {
            try {
                l.onLog(entry);
            } catch (RuntimeException ignored) {
                // リスナー側の例外でロギングを巻き込まない
            }
        }
    }

    /** 呼び出し側が LOCK を保持している前提でファイルへ 1 件書く。 */
    private static void writeToFile(Entry entry) {
        if (writer == null || fileLoggingDisabled) {
            return;
        }
        try {
            writer.println(entry.formatLine());
            if (entry.detail != null) {
                writer.print(entry.detail);
            }
            writer.flush();
            maybeRotate();
        } catch (RuntimeException ex) {
            // 失敗したらファイル出力だけ無効化 (System.err には出さない: ティー再帰回避)
            fileLoggingDisabled = true;
        }
    }

    private static void openLogFile() {
        try {
            File dir = new File(PathUtil.getBasePath(), "logs");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            logFile = new File(dir, "juml.log");
            Writer w = Files.newBufferedWriter(logFile.toPath(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            writer = new PrintWriter(w);
        } catch (IOException | RuntimeException ex) {
            writer = null;
            fileLoggingDisabled = true;
        }
    }

    /** 呼び出し側が LOCK を保持している前提でローテーションを行う。 */
    private static void maybeRotate() {
        if (logFile == null || logFile.length() < ROTATE_BYTES) {
            return;
        }
        try {
            writer.close();
            File rolled = new File(logFile.getParentFile(), "juml.log.1");
            if (rolled.exists() && !rolled.delete()) {
                // 旧世代を消せない場合はローテーションを諦め、現行へ追記継続
                reopenAppend();
                return;
            }
            if (!logFile.renameTo(rolled)) {
                reopenAppend();
                return;
            }
            reopenAppend();
        } catch (RuntimeException ex) {
            fileLoggingDisabled = true;
        }
    }

    private static void reopenAppend() {
        try {
            Writer w = Files.newBufferedWriter(logFile.toPath(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            writer = new PrintWriter(w);
        } catch (IOException | RuntimeException ex) {
            writer = null;
            fileLoggingDisabled = true;
        }
    }

    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static void installUncaughtHandler() {
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            error(ErrorCode.SYS_002, "uncaught",
                    "Uncaught exception in thread \"" + thread.getName() + "\"", ex);
            if (prev != null) {
                prev.uncaughtException(thread, ex);
            } else if (consoleErr != null) {
                // 既定ハンドラ未設定時、JVM 既定のコンソール出力が失われないよう
                // 元 (ティー前) の標準エラーへも出す。ティーを経由しないため二重記録しない。
                consoleErr.println("Exception in thread \"" + thread.getName() + "\"");
                ex.printStackTrace(consoleErr);
            }
        });
    }

    /**
     * {@code System.err} をティーする。元の標準エラー出力へはそのまま流しつつ、
     * 1 行ごとに WARN として記録するため、既存の {@code System.err.println} や
     * EDT が出すスタックトレースも追加実装なしでログに残る。
     */
    private static void installStderrTee() {
        PrintStream original = System.err;
        consoleErr = original;
        System.setErr(new PrintStream(new TeeOutputStream(original), true,
                StandardCharsets.UTF_8));
    }

    /**
     * 書き込まれたバイトを元ストリームへ素通しさせつつ、改行区切りで 1 行を組み立てて
     * {@link AppLog} へ WARN 記録する。{@code record} は {@code System.err} に書かないため
     * 再帰しない。
     */
    private static final class TeeOutputStream extends OutputStream {
        private final PrintStream original;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream(128);

        TeeOutputStream(PrintStream original) {
            this.original = original;
        }

        @Override
        public synchronized void write(int b) {
            original.write(b);
            if (b == '\n') {
                flushLine();
            } else if (b != '\r') {
                line.write(b);
            }
        }

        @Override
        public void flush() {
            original.flush();
        }

        private void flushLine() {
            if (line.size() == 0) {
                return;
            }
            String text = new String(line.toByteArray(), StandardCharsets.UTF_8);
            line.reset();
            // 発生元不明の stderr 出力は未分類 ID (SYS-001) で拾う。
            // 頻出するパターンが見つかったら専用 ID への昇格を検討する。
            record(Level.WARN, ErrorCode.SYS_001, "stderr", text, null);
        }
    }
}
