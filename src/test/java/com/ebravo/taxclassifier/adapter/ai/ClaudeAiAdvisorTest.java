package com.ebravo.taxclassifier.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebravo.taxclassifier.domain.Product;
import com.ebravo.taxclassifier.domain.Suggestion;
import com.ebravo.taxclassifier.domain.TaxClassification;
import com.ebravo.taxclassifier.domain.TaxSituation;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Replays a Messages API response from a local stub; the real API is never called. */
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

    @BeforeEach
    void startStub() throws IOException {
        claude = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        claude.createContext("/v1/messages", exchange -> {
            request = JsonMapper.shared().readTree(exchange.getRequestBody());
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = recorded("messages-structured-response.json");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        claude.start();
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

        assertThat(suggestion).isEqualTo(new Suggestion("200", "200003", new BigDecimal("0.92"),
                "Feijão é alimento destinado ao consumo humano listado no Anexo I."));
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

    private JsonNode product() {
        return JsonMapper.shared().readTree(request.path("messages").path(0).path("content").asString());
    }

    private static byte[] recorded(String file) throws IOException {
        try (InputStream in = ClaudeAiAdvisorTest.class.getResourceAsStream("/claude/" + file)) {
            return in.readAllBytes();
        }
    }
}
