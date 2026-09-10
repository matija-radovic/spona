package io.github.matijaradovic.spona.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.mail")
public record MailProperties(String from) {
}
