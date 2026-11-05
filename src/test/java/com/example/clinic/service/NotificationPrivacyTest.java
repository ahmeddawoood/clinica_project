package com.example.clinic.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationPrivacyTest {
    @Test
    void disabledEmailDoesNotLogPasswordResetToken() {
        Logger logger=(Logger) LoggerFactory.getLogger(NotificationServiceImpl.class);
        var appender=new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var service=new NotificationServiceImpl(mock(TemplateEngine.class),mock(MetricsService.class));
            service.sendPasswordResetEmail("fixture@example.test", "http://localhost/reset-password?token=private-reset-marker");
            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                    .doesNotContain("private-reset-marker", "reset-password?token="));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
