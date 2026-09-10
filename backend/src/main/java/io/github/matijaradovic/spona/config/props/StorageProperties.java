package io.github.matijaradovic.spona.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String region,
        boolean pathStyle,
        String avatarBucket,
        String cvBucket) {
}
