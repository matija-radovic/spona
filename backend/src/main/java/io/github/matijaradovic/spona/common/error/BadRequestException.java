package io.github.matijaradovic.spona.common.error;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String detail) {
        super(detail);
    }
}
