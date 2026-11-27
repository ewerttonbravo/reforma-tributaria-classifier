package com.ebravo.taxclassifier.adapter.calculator;

import com.ebravo.taxclassifier.application.CalculatorUnavailable;
import java.util.function.Supplier;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** Names the failures that asking again may cure; a 4xx or an answer that does not parse is left as it is. */
final class CalculatorCall {

    private CalculatorCall() {}

    /** No answer within the timeouts, a refused connection or a server error is {@link CalculatorUnavailable}. */
    static <T> T send(Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new CalculatorUnavailable(e);
        }
    }
}
