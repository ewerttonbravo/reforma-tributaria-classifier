package com.ebravo.taxclassifier.adapter.calculator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebravo.taxclassifier.application.CalculatorUnavailable;
import com.ebravo.taxclassifier.application.OfficialCandidates;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;

// Port 1 is reserved and has no listener on a developer's machine or a CI runner, so the connection is refused.
@RestClientTest(components = CalculatorOfficialCandidates.class, properties = "calculator.base-url=http://localhost:1")
@AutoConfigureMockRestServiceServer(enabled = false)
// The slice leaves out the HTTP client configuration that the application gets, timeouts included.
@ImportAutoConfiguration({HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class})
class CalculatorDownTest {

    @Autowired
    private OfficialCandidates candidates;

    @Test
    void calculatorThatRefusesTheConnectionIsUnavailable() {
        assertThatThrownBy(() -> candidates.on(LocalDate.of(2027, 3, 10))).isInstanceOf(CalculatorUnavailable.class);
    }
}
