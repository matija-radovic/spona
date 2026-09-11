package io.github.matijaradovic.spona.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.matijaradovic.spona.support.ContainersConfig;

@SpringBootTest
@Import(ContainersConfig.class)
@EnabledIfSystemProperty(named = "spona.mailpit", matches = "true")
class MailpitDeliveryTest {

    private static final String MAILPIT = "http://localhost:8025/api/v1/messages";
    private static final Duration ARRIVAL_BUDGET = Duration.ofSeconds(10);

    @Autowired
    private MailService mail;

    @Test
    void aVerificationMailArrivesInMailpit() throws Exception {
        String recipient = "b9-" + UUID.randomUUID() + "@example.com";

        mail.sendVerification(recipient, "Matija Radovic",
                "http://localhost:5173/verify?token=b9-demonstration-token");

        assertThat(waitForMessageTo(recipient))
                .as("Mailpit holds the message, open http://localhost:8025 to read it")
                .isTrue();
    }

    private static boolean waitForMessageTo(String recipient) throws Exception {
        Instant deadline = Instant.now().plus(ARRIVAL_BUDGET);
        try (HttpClient http = HttpClient.newHttpClient()) {
            while (Instant.now().isBefore(deadline)) {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create(MAILPIT + "?limit=200")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.body().contains(recipient)) {
                    return true;
                }
                Thread.sleep(250);
            }
        }
        return false;
    }
}
