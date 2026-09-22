package io.github.matijaradovic.spona.common.error;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(ErrorCode code) {
        super(code.with());
    }

    public UnauthorizedException(ErrorCode.Formatted error) {
        super(error);
    }
}
