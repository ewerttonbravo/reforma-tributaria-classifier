package com.ebravo.taxclassifier.application;

/** The calculator did not answer, or answered with a server error: asking again later may succeed. */
public final class CalculatorUnavailable extends RuntimeException {

    public CalculatorUnavailable(Throwable cause) {
        super("the calculator is unavailable: " + cause.getMessage(), cause);
    }
}
