package io.github.matijaradovic.spona.config.props;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String url, Cors cors) {

    public String link(String path) {
        return (url.endsWith("/") ? url.substring(0, url.length() - 1) : url) + path;
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
