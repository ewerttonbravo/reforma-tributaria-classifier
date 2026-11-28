package com.ebravo.taxclassifier.application;

/**
 * The AI did not answer, was rate limited or answered with a server error: asking again later may succeed. A type apart from
 * {@link AiFailure}, not a kind of it, so a retry can tell the two apart by class.
 */
public final class AiUnavailable extends RuntimeException {

    public AiUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
