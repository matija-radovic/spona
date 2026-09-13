package io.github.matijaradovic.spona.storage;

import static io.github.matijaradovic.spona.support.AvatarFixtures.canvasPng;
import static io.github.matijaradovic.spona.support.AvatarFixtures.pngDataUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.matijaradovic.spona.support.ContainersConfig;

@SpringBootTest
@Import(ContainersConfig.class)
class AvatarServiceIntegrationTest {

    @Autowired
    private AvatarService avatars;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aWriteThatRollsBackLeavesNoObjectBehind() throws Exception {
        String url = transaction().execute(status -> {
            String stored = avatars.store(pngDataUrl(canvasPng()));
            status.setRollbackOnly();
            return stored;
        });

        assertThat(fetch(url).statusCode()).isEqualTo(404);
    }

    @Test
    void aCommittedReplacementDeletesTheOldObjectAndServesTheNewOne() throws Exception {
        String old = transaction().execute(status -> avatars.store(pngDataUrl(canvasPng())));
        String replacement = transaction().execute(status -> avatars.replace(old, pngDataUrl(canvasPng())));

        assertThat(fetch(old).statusCode()).isEqualTo(404);
        HttpResponse<byte[]> served = fetch(replacement);
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(served.body()).isEqualTo(canvasPng());
    }

    @Test
    void aCommittedDiscardDeletesTheObject() throws Exception {
        String url = transaction().execute(status -> avatars.store(pngDataUrl(canvasPng())));

        transaction().executeWithoutResult(status -> avatars.discard(url));

        assertThat(fetch(url).statusCode()).isEqualTo(404);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static HttpResponse<byte[]> fetch(String url) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        }
    }
}
