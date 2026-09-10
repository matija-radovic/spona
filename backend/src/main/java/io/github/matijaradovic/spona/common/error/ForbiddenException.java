package io.github.matijaradovic.spona.common.error;

import java.net.URI;

public class ForbiddenException extends RuntimeException {

    private final URI type;

    public ForbiddenException(String detail) {
        this(detail, null);
    }

    public ForbiddenException(String detail, URI type) {
        super(detail);
        this.type = type;
    }

    public URI getType() {
        return type;
    }
}
