package io.github.matijaradovic.spona.config.props;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String url, Cors cors) {

    public record Cors(List<String> allowedOrigins) {
    }
}
