package io.github.matijaradovic.spona.common.error;

public abstract class ApiException extends RuntimeException {

    private final ErrorCode.Formatted error;

    protected ApiException(ErrorCode.Formatted error) {
        super(error.detail());
        this.error = error;
    }

    public ErrorCode getErrorCode() {
        return error.errorCode();
    }

    public ErrorCode.Formatted getError() {
        return error;
    }
}
