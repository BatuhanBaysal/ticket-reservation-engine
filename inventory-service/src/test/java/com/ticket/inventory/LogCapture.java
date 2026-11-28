package com.ticket.inventory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final CollectingAppender appender = new CollectingAppender();

    private LogCapture(String loggerName) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerName);
        appender.start();
        logger.addAppender(appender);
    }

    static LogCapture of(String loggerName) {
        return new LogCapture(loggerName);
    }

    List<String> mdcValues(String key) {
        return appender.events.stream()
            .map(event -> event.getMDCPropertyMap().get(key))
            .filter(value -> value != null)
            .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static final class CollectingAppender extends AppenderBase<ILoggingEvent> {

        private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            events.add(event);
        }
    }
}
