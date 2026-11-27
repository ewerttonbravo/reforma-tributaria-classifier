package com.ebravo.taxclassifier.adapter.calculator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.ebravo.taxclassifier.application.CalculatorUnavailable;
import com.ebravo.taxclassifier.application.OfficialCandidates;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A real HTTP server, as no mock can stay silent or refuse a connection. One adapter is enough: the timeouts are the
 * configuration's and the translation is shared by the three.
 */
@RestClientTest(components = CalculatorOfficialCandidates.class, properties = "spring.http.clients.read-timeout=200ms")
@AutoConfigureMockRestServiceServer(enabled = false)
// The slice leaves out the HTTP client configuration that the application gets, timeouts included.
@ImportAutoConfiguration({HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class})
class CalculatorUnavailableTest {

    private static final CountDownLatch RELEASE = new CountDownLatch(1);
    private static final HttpServer SILENT = silentServer();

    @Autowired
    private OfficialCandidates candidates;

    @DynamicPropertySource
    static void calculator(DynamicPropertyRegistry registry) {
        registry.add("calculator.base-url", () -> "http://localhost:" + SILENT.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        RELEASE.countDown();
        SILENT.stop(0);
    }

    /** Fails instead of hanging when the read timeout is not applied. */
    @Test
    void calculatorThatDoesNotAnswerIsUnavailable() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThatThrownBy(() -> candidates.on(LocalDate.of(2027, 3, 10))).isInstanceOf(CalculatorUnavailable.class));
    }

    /** Accepts the request and holds the answer until the tests are done. */
    private static HttpServer silentServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    RELEASE.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
