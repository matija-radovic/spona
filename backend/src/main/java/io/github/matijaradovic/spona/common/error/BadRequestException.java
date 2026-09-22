package io.github.matijaradovic.spona.common.error;

public class BadRequestException extends ApiException {

    public BadRequestException(ErrorCode code) {
        super(code.with());
    }

    public BadRequestException(ErrorCode.Formatted error) {
        super(error);
    }
}
