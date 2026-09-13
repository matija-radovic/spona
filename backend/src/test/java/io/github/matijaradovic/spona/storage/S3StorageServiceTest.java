package io.github.matijaradovic.spona.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.matijaradovic.spona.config.props.StorageProperties;
import io.github.matijaradovic.spona.support.ContainersConfig;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/**
 * Runs against the MinIO in compose, not a Testcontainers instance, so the stack has to be up. The
 * assertions are made over plain HTTP with no credentials, because that is what a browser is.
 */
@SpringBootTest
@Import(ContainersConfig.class)
class S3StorageServiceTest {

    private static final byte[] AVATAR = "not really a png".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CV = "not really a pdf".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private StorageService storage;

    @Autowired
    private StorageProperties properties;

    @Autowired
    private S3Client client;

    @Test
    void bothBucketsExistAfterBoot() {
        assertThat(headBucket(properties.avatarBucket())).isTrue();
        assertThat(headBucket(properties.cvBucket())).isTrue();
    }

    @Test
    void anAvatarUrlNamesThePublicEndpointAndTheAvatarBucket() {
        String url = storage.putAvatar(AVATAR, "image/png");

        assertThat(url)
                .startsWith(properties.publicEndpoint())
                .contains(properties.avatarBucket())
                .endsWith(".png");
    }

    @Test
    void anAvatarIsReadableWithoutCredentials() throws Exception {
        String url = storage.putAvatar(AVATAR, "image/png");

        HttpResponse<byte[]> response = get(url);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(AVATAR);
    }

    @Test
    void everyUploadGetsItsOwnKey() {
        assertThat(storage.putAvatar(AVATAR, "image/png"))
                .isNotEqualTo(storage.putAvatar(AVATAR, "image/png"));
    }

    @Test
    void aDeletedAvatarIsGone() throws Exception {
        String url = storage.putAvatar(AVATAR, "image/png");
        storage.deleteAvatar(url);
        assertThat(get(url).statusCode()).isEqualTo(404);
    }

    @Test
    void aUrlThatIsNotOneOfOursIsNeverDeleted() throws Exception {
        String url = storage.putAvatar(AVATAR, "image/png");
        String key = url.substring(url.lastIndexOf('/') + 1);
        storage.deleteAvatar("https://elsewhere.example/" + properties.avatarBucket() + "/" + key);

        assertThat(get(url).statusCode()).isEqualTo(200);
    }

    @Test
    void aCvUploadReturnsAKeyAndNotAUrl() {
        String key = storage.putCv(CV, "application/pdf");

        assertThat(key).doesNotStartWith("http").doesNotContain("/").endsWith(".pdf");
    }

    @Test
    void aCvIsNotReadableWithoutASignature() throws Exception {
        String key = storage.putCv(CV, "application/pdf");
        String unsigned = "%s/%s/%s".formatted(properties.publicEndpoint(), properties.cvBucket(), key);

        assertThat(get(unsigned).statusCode()).isNotEqualTo(200);
    }

    @Test
    void aPresignedCvUrlCarriesASignature() {
        String url = storage.presignCv(storage.putCv(CV, "application/pdf"));

        assertThat(url).contains("X-Amz-Signature=").contains("X-Amz-Credential=");
    }

    @Test
    void aPresignedCvUrlExpiresInFiveMinutes() {
        String url = storage.presignCv(storage.putCv(CV, "application/pdf"));

        assertThat(url).contains("X-Amz-Expires=" + StorageService.CV_URL_TTL.toSeconds());
        assertThat(StorageService.CV_URL_TTL.toSeconds()).isEqualTo(300);
    }

    @Test
    void aPresignedCvUrlIsSignedForThePublicEndpoint() {
        String url = storage.presignCv(storage.putCv(CV, "application/pdf"));

        assertThat(url).startsWith(properties.publicEndpoint());
    }

    @Test
    void aPresignedCvUrlFetchesTheBytes() throws Exception {
        String url = storage.presignCv(storage.putCv(CV, "application/pdf"));

        HttpResponse<byte[]> response = get(url);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(CV);
    }

    @Test
    void aPresignedUrlForAMissingKeyDoesNotResolve() throws Exception {
        String url = storage.presignCv("no-such-object.pdf");

        assertThat(get(url).statusCode()).isEqualTo(404);
    }

    private boolean headBucket(String bucket) {
        client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        return true;
    }

    private static HttpResponse<byte[]> get(String url) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        }
    }
}
