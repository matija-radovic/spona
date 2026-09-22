package io.github.matijaradovic.spona.common.error;

public class NotFoundException extends ApiException {

    public NotFoundException(ErrorCode code) {
        super(code.with());
    }

    public NotFoundException(ErrorCode.Formatted error) {
        super(error);
    }
}
