package io.github.matijaradovic.spona.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.github.matijaradovic.spona.config.props.StorageProperties;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;

/**
 * Creates the two buckets on boot, and opens the avatar bucket to anonymous
 * GET. Both are idempotent and run every start
 */
@Component
class BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);

    private static final String ANONYMOUS_READ = """
            {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
            "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""";

    private final S3Client client;
    private final StorageProperties storage;

    BucketInitializer(S3Client client, StorageProperties storage) {
        this.client = client;
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureBucket(storage.avatarBucket());
            ensureBucket(storage.cvBucket());
            openToAnonymousRead(storage.avatarBucket());
        } catch (SdkException e) {
            log.warn("Object store at {} is not ready, buckets were not prepared: {}",
                    storage.endpoint(), e.getMessage());
        }
    }

    private void ensureBucket(String bucket) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException absent) {
            create(bucket);
        }
    }

    private void create(String bucket) {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created bucket {}", bucket);
        } catch (BucketAlreadyOwnedByYouException raced) {
            log.debug("Bucket {} already existed", bucket);
        }
    }

    private void openToAnonymousRead(String bucket) {
        client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucket)
                .policy(ANONYMOUS_READ.formatted(bucket))
                .build());
    }
}
