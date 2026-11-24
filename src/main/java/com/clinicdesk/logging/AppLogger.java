package com.clinicdesk.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.*;

/**
 * Centralized application logger utilizing java.util.logging as required by the specification.
 * Configured to format logs cleanly for terminal output and diagnostic debugging.
 */
public final class AppLogger {

    private static final String ROOT_PACKAGE = "com.clinicdesk";
    private static boolean initialized = false;

    private AppLogger() {
        // Utility class
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        Logger rootLogger = Logger.getLogger(ROOT_PACKAGE);
        rootLogger.setUseParentHandlers(false);
        rootLogger.setLevel(Level.INFO);

        // Remove old handlers if any
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        consoleHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("[%1$tF %1$tT] [%2$-7s] [%3$s] %4$s%n",
                        record.getMillis(),
                        record.getLevel().getLocalizedName(),
                        record.getLoggerName().substring(record.getLoggerName().lastIndexOf('.') + 1),
                        formatMessage(record));
            }
        });

        rootLogger.addHandler(consoleHandler);
        initialized = true;
    }

    public static Logger getLogger(Class<?> clazz) {
        if (!initialized) {
            init();
        }
        return Logger.getLogger(clazz.getName());
    }
}
