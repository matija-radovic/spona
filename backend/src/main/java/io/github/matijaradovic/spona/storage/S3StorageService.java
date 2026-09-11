package io.github.matijaradovic.spona.storage;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.github.matijaradovic.spona.config.props.StorageProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * One S3 client for MinIO in development and R2 or S3 in production. Only the
 * endpoint moves.
 */
@Service
public class S3StorageService implements StorageService {

    /**
     * The key is server generated, so a caller can never choose where its bytes
     * land.
     */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/webp", "webp",
            "application/pdf", "pdf");

    private final S3Client client;
    private final S3Presigner presigner;
    private final S3Utilities publicUrls;
    private final StorageProperties storage;

    public S3StorageService(S3Client client, S3Presigner presigner, S3Utilities publicUrls,
            StorageProperties storage) {
        this.client = client;
        this.presigner = presigner;
        this.publicUrls = publicUrls;
        this.storage = storage;
    }

    @Override
    public String putAvatar(byte[] content, String contentType) {
        String key = newKey(contentType);
        put(storage.avatarBucket(), key, content, contentType);
        return publicUrls.getUrl(
                GetUrlRequest
                        .builder()
                        .bucket(storage.avatarBucket())
                        .key(key)
                        .build())
                .toExternalForm();
    }

    @Override
    public String putCv(byte[] content, String contentType) {
        String key = newKey(contentType);
        put(storage.cvBucket(), key, content, contentType);
        return key;
    }

    @Override
    public String presignCv(String objectKey) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(CV_URL_TTL)
                .getObjectRequest(
                        GetObjectRequest.builder()
                                .bucket(storage.cvBucket())
                                .key(objectKey)
                                .build())
                .build())
                .url()
                .toExternalForm();
    }

    private void put(String bucket, String key, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build(),
                RequestBody.fromBytes(content));
    }

    private static String newKey(String contentType) {
        String extension = EXTENSIONS.get(contentType);
        UUID name = UUID.randomUUID();
        return extension == null ? name.toString() : name + "." + extension;
    }
}
