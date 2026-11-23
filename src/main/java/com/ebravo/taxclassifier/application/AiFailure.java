package com.ebravo.taxclassifier.application;

import java.util.OptionalInt;

/** The AI gave no suggestion. An HTTP error carries the status the API answered with. */
public final class AiFailure extends RuntimeException {

    public enum Kind { REFUSED, TRUNCATED, MALFORMED, HTTP_ERROR }

    private final Kind kind;
    // Integer, not OptionalInt: an exception is Serializable and OptionalInt is not.
    private final Integer status;

    public AiFailure(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = null;
    }

    public AiFailure(int status, Throwable cause) {
        super("the AI answered with HTTP " + status, cause);
        this.kind = Kind.HTTP_ERROR;
        this.status = status;
    }

    public Kind kind() {
        return kind;
    }

    public OptionalInt status() {
        return status == null ? OptionalInt.empty() : OptionalInt.of(status);
    }
}
