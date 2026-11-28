package com.ebravo.taxclassifier.application;

/**
 * The AI did not answer, or answered 408, 409, 429 or 5xx: asking again later may succeed. A type apart from {@link AiFailure},
 * not a kind of it, so a retry can tell the two apart by class.
 */
public final class AiUnavailable extends RuntimeException {

    public AiUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}
