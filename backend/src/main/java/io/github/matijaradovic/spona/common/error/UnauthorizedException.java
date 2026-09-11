package io.github.matijaradovic.spona.common.error;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String detail) {
        super(detail);
    }
}
