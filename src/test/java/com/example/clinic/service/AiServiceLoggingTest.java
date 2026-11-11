package com.example.clinic.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceLoggingTest {
    @ParameterizedTest
    @ValueSource(ints = {200, 500})
    void logsExcludeSymptomsProviderBodiesAndCredentialsEvenAtDebug(int status) throws Exception {
        String symptoms = "private-patient-marker chest pain";
        String body = "{\"choices\":[{\"message\":{\"content\":\"cardiology\"}}],"
                + "\"private\":\"provider-body-marker\"}";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        Logger logger = (Logger) LoggerFactory.getLogger(AiServiceImpl.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        server.start();
        try {
            AiServiceImpl service = new AiServiceImpl();
            ReflectionTestUtils.setField(service, "apiKey", "private-api-key-marker");
            ReflectionTestUtils.setField(service, "apiUrl", "http://127.0.0.1:"
                    + server.getAddress().getPort() + "/chat");
            ReflectionTestUtils.setField(service, "model", "fixture");
            assertThat(service.recommendSpecialty(symptoms, List.of("Cardiologie")))
                    .isEqualTo("Cardiologie");
            assertThat(service.analyzeSymptoms(symptoms).isUrgent()).isTrue();
            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(
                        symptoms, "private-patient-marker", "provider-body-marker",
                        "private-api-key-marker", body);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            server.stop(0);
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
