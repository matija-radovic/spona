package io.github.matijaradovic.spona.common.error;

public class ForbiddenException extends ApiException {

    public ForbiddenException(ErrorCode code) {
        super(code.with());
    }

    public ForbiddenException(ErrorCode.Formatted error) {
        super(error);
    }
}
