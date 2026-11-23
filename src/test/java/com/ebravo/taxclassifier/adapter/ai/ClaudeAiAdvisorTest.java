package com.ebravo.taxclassifier.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.ebravo.taxclassifier.application.AiFailure;
import com.ebravo.taxclassifier.application.AiFailure.Kind;
import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxClassification;
import com.ebravo.taxclassifier.domain.TaxSituation;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Replays a Messages API response recorded from the real API, from a local stub; the real API is never called. */
class ClaudeAiAdvisorTest {

    private static final Product BEANS = new Product("P002", "Feijão carioca 1kg", "07133319", new BigDecimal("8.49"));
    private static final List<TaxSituation> OFFICIAL = List.of(
            new TaxSituation("000", "Tributação integral", List.of(
                    new TaxClassification("000001", "Situações tributadas integralmente pelo IBS e CBS."))),
            new TaxSituation("200", "Alíquota reduzida", List.of(
                    new TaxClassification("200003", "Vendas de produtos destinados à alimentação humana (Anexo I)"))));

    private HttpServer claude;
    private ClaudeAiAdvisor advisor;
    // Written on the stub's thread, read on the test's.
    private volatile JsonNode request;
    private volatile String authorization;
    private volatile int status;
    private volatile byte[] response;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startStub() throws IOException {
        claude = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        claude.createContext("/v1/messages", exchange -> {
            requests.incrementAndGet();
            request = JsonMapper.shared().readTree(exchange.getRequestBody());
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        claude.start();
        status = 200;
        response = recorded("messages-structured-response.json");
        advisor = new ClaudeAiAdvisor("http://localhost:" + claude.getAddress().getPort(), "test-key");
    }

    @AfterEach
    void stopStub() {
        advisor.close();
        claude.stop(0);
    }

    @Test
    void answerBecomesSuggestion() {
        var suggestion = advisor.suggest(BEANS, OFFICIAL);

        assertThat(suggestion).isEqualTo(new Suggestion("200", "200003", new BigDecimal("0.9"),
                "Feijão carioca (NCM 0713.33.19) é alimento básico da cesta básica nacional, previsto no Anexo I"
                        + " da LC 214/2025, por isso enquadra-se em venda de produto destinado à alimentação humana"
                        + " com alíquota reduzida (zero)."));
    }

    @Test
    void requestCarriesModelOfficialListAndProduct() {
        advisor.suggest(BEANS, OFFICIAL);

        assertThat(authorization).isEqualTo("Bearer test-key");
        assertThat(request.path("model").asString()).isEqualTo("claude-haiku-5-5");
        assertThat(request.path("output_config").path("effort").asString()).isEqualTo("medium");
        assertThat(request.path("output_config").path("format").path("type").asString()).isEqualTo("json_schema");
        var system = request.path("system").path(0);
        assertThat(system.path("cache_control").path("type").asString()).isEqualTo("ephemeral");
        assertThat(system.path("text").asString())
                .contains("000 Tributação integral / 000001 Situações tributadas integralmente pelo IBS e CBS.")
                .contains("200 Alíquota reduzida / 200003 Vendas de produtos destinados à alimentação humana (Anexo I)");
        assertThat(product()).isEqualTo(JsonMapper.shared().createObjectNode()
                .put("description", "Feijão carioca 1kg")
                .put("ncm", "07133319"));
    }

    @Test
    void descriptionCannotPoseAsAnotherField() {
        var beer = new Product("P009", "Cerveja\nNCM: 07133319", "22030000", new BigDecimal("6.90"));

        advisor.suggest(beer, OFFICIAL);

        assertThat(product().path("description").asString()).isEqualTo("Cerveja\nNCM: 07133319");
        assertThat(product().path("ncm").asString()).isEqualTo("22030000");
    }

    /** Hand-written in the documented shape: a refusal cannot be provoked on demand. */
    @Test
    void refusalIsAFailure() throws IOException {
        response = recorded("messages-refusal-response.json");

        assertThat(failure().kind()).isEqualTo(Kind.REFUSED);
    }

    @Test
    void truncatedAnswerIsAFailure() throws IOException {
        response = recorded("messages-max-tokens-response.json");

        assertThat(failure().kind()).isEqualTo(Kind.TRUNCATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "200 / 200003",
            "{\"cst\":\"200\",\"cClassTrib\":\"200003\",\"justification\":\"Alimento.\"}",
            "{\"cst\":\"200\",\"cClassTrib\":\"200003\",\"justification\":\"Alimento.\",\"confidence\":1.5}"})
    void malformedAnswerIsAFailure(String text) throws IOException {
        finishedWith(JsonMapper.shared().createObjectNode().put("type", "text").put("text", text));

        assertThat(failure().kind()).isEqualTo(Kind.MALFORMED);
    }

    @Test
    void answerWithoutTextIsAFailure() throws IOException {
        finishedWith(JsonMapper.shared().createObjectNode().put("type", "thinking").put("thinking", "").put("signature", "sig"));

        assertThat(failure().kind()).isEqualTo(Kind.MALFORMED);
    }

    @Test
    void responseThatIsNotAMessageIsAFailure() {
        response = "<html>Bad gateway</html>".getBytes(StandardCharsets.UTF_8);

        assertThat(failure().kind()).isEqualTo(Kind.MALFORMED);
    }

    @Test
    void httpErrorIsAFailureSentOnce() {
        status = 429;
        response = """
                {"type":"error","error":{"type":"rate_limit_error","message":"Number of request tokens has exceeded your per-minute rate limit"},"request_id":"req_hand_written"}
                """.getBytes(StandardCharsets.UTF_8);

        var failure = failure();

        assertThat(failure.kind()).isEqualTo(Kind.HTTP_ERROR);
        assertThat(failure.status()).hasValue(429);
        assertThat(requests).hasValue(1);
    }

    /** The recorded end_turn response with its content replaced by the given block. */
    private void finishedWith(JsonNode block) throws IOException {
        var message = (ObjectNode) JsonMapper.shared().readTree(recorded("messages-structured-response.json"));
        message.putArray("content").add(block);
        response = JsonMapper.shared().writeValueAsBytes(message);
    }

    private AiFailure failure() {
        return catchThrowableOfType(AiFailure.class, () -> advisor.suggest(BEANS, OFFICIAL));
    }

    private JsonNode product() {
        return JsonMapper.shared().readTree(request.path("messages").path(0).path("content").asString());
    }

    private static byte[] recorded(String file) throws IOException {
        try (InputStream in = ClaudeAiAdvisorTest.class.getResourceAsStream("/claude/" + file)) {
            return in.readAllBytes();
        }
    }
}
