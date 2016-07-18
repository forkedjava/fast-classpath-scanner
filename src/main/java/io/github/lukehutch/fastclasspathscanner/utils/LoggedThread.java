package io.github.lukehutch.fastclasspathscanner.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;

public abstract class LoggedThread<T> implements Callable<T> {
    protected ThreadLog log = new ThreadLog();

    @Override
    public T call() throws Exception {
        try {
            return doWork();
        } catch (final Throwable e) {
            log.flush();
            if (FastClasspathScanner.verbose) {
                log.log("Thread " + Thread.currentThread().getName() + " threw " + e);
            }
            throw e;
        } finally {
            log.flush();
        }
    }

    public abstract T doWork() throws Exception;

    private static class ThreadLogEntry {
        private final int indentLevel;
        private final Date time;
        private final String msg;
        private final String stackTrace;
        private final long elapsedTimeNanos;
        private final SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmX");
        private final DecimalFormat nanoFormatter = new DecimalFormat("0.000000");

        public ThreadLogEntry(final int indentLevel, final String msg, final long elapsedTimeNanos, Throwable e) {
            this.indentLevel = indentLevel;
            this.msg = msg;
            this.time = Calendar.getInstance().getTime();
            this.elapsedTimeNanos = elapsedTimeNanos;
            if (e != null) {
                StringWriter writer = new StringWriter();
                e.printStackTrace(new PrintWriter(writer));
                stackTrace = writer.toString();
            } else {
                stackTrace = null;
            }
        }

        private void appendLogLine(String line, StringBuilder buf) {
            synchronized (dateTimeFormatter) {
                buf.append(dateTimeFormatter.format(time));
            }
            buf.append('\t');
            buf.append(FastClasspathScanner.class.getSimpleName());
            buf.append('\t');
            final int numIndentChars = 2 * indentLevel;
            for (int i = 0; i < numIndentChars - 1; i++) {
                buf.append('-');
            }
            if (numIndentChars > 0) {
                buf.append(" ");
            }
            buf.append(msg);
        }
        
        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            appendLogLine(msg, buf);
            if (elapsedTimeNanos >= 0L) {
                buf.append(" in ");
                buf.append(nanoFormatter.format(elapsedTimeNanos * 1e-9));
                buf.append(" sec");
            }
            if (stackTrace != null) {
                String[] parts = stackTrace.split("\n");
                for (int i = 0; i < parts.length; i++) {
                    buf.append('\n');
                    appendLogLine(parts[1], buf);
                }
            }
            return buf.toString();
        }
    }

    /**
     * Class for accumulating ordered log entries from threads, for later writing to the log without interleaving.
     */
    public static class ThreadLog implements AutoCloseable {
        private static AtomicBoolean versionLogged = new AtomicBoolean(false);
        private final Queue<ThreadLogEntry> logEntries = new ConcurrentLinkedQueue<>();

        public void log(final int indentLevel, final String msg, final long elapsedTimeNanos, final Throwable e) {
            logEntries.add(new ThreadLogEntry(indentLevel, msg, elapsedTimeNanos, e));
        }

        public void log(final int indentLevel, final String msg, final long elapsedTimeNanos) {
            logEntries.add(new ThreadLogEntry(indentLevel, msg, elapsedTimeNanos, null));
        }

        public void log(final int indentLevel, final String msg, final Throwable e) {
            logEntries.add(new ThreadLogEntry(indentLevel, msg, -1L, e));
        }

        public void log(final int indentLevel, final String msg) {
            logEntries.add(new ThreadLogEntry(indentLevel, msg, -1L, null));
        }

        public void log(final String msg, final long elapsedTimeNanos, final Throwable e) {
            logEntries.add(new ThreadLogEntry(0, msg, elapsedTimeNanos, e));
        }

        public void log(final String msg, final long elapsedTimeNanos) {
            logEntries.add(new ThreadLogEntry(0, msg, elapsedTimeNanos, null));
        }

        public void log(final String msg, final Throwable e) {
            logEntries.add(new ThreadLogEntry(0, msg, -1L, e));
        }

        public void log(final String msg) {
            logEntries.add(new ThreadLogEntry(0, msg, -1L, null));
        }

        public synchronized void flush() {
            if (!logEntries.isEmpty()) {
                final StringBuilder buf = new StringBuilder();
                if (versionLogged.compareAndSet(false, true)) {
                    if (FastClasspathScanner.verbose) {
                        // Log the version before the first log entry
                        buf.append(new ThreadLogEntry(0,
                                "FastClasspathScanner version " + FastClasspathScanner.getVersion(), 1L, null)
                                        .toString());
                        buf.append('\n');
                    }
                }
                for (ThreadLogEntry logEntry; (logEntry = logEntries.poll()) != null;) {
                    buf.append(logEntry.toString());
                    buf.append('\n');
                }
                System.err.print(buf.toString());
                System.err.flush();
            }
        }

        @Override
        public void close() {
            flush();
        }
    }
}