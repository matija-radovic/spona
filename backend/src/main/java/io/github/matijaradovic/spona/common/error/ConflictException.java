package io.github.matijaradovic.spona.common.error;

import java.net.URI;

public class ConflictException extends RuntimeException {

    private final URI type;

    public ConflictException(String detail) {
        this(detail, null);
    }

    public ConflictException(String detail, URI type) {
        super(detail);
        this.type = type;
    }

    public URI getType() {
        return type;
    }
}
