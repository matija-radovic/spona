package io.github.matijaradovic.spona.common.error;

public class ConflictException extends ApiException {

    public ConflictException(ErrorCode code) {
        super(code.with());
    }

    public ConflictException(ErrorCode.Formatted error) {
        super(error);
    }
}
