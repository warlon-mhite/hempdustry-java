package com.warlonmhite.hempdustry.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * What the server logs while a piece of a test runs, for a test whose subject is a log line. The lines still reach the
 * log, so a warning a test causes on purpose must also be one the log gate expects.
 */
final class LogLines {
    private LogLines() {
    }

    /** Every WARN or worse logged while {@code body} runs, as its formatted message. */
    static List<String> warnings(Runnable body) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        var appender = new AbstractAppender("hempdustry-log-lines", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                    lines.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        Logger root = (Logger) LogManager.getRootLogger();
        root.addAppender(appender);
        try {
            body.run();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
        return List.copyOf(lines);
    }
}
