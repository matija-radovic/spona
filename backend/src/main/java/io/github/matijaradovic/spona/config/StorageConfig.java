package io.github.matijaradovic.spona.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.matijaradovic.spona.config.props.StorageProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** object store client and two URL builders. */
@Configuration
public class StorageConfig {

    @Bean
    S3Client s3Client(StorageProperties storage) {
        return S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(addressing(storage))
                .build();
    }

    @Bean
    S3Presigner s3Presigner(StorageProperties storage) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(storage.publicEndpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(addressing(storage))
                .build();
    }

    /** Builds unsigned URLs for the public bucket, so it too addresses the public endpoint. */
    @Bean
    S3Utilities s3PublicUrls(StorageProperties storage) {
        return S3Utilities.builder()
                .endpoint(URI.create(storage.publicEndpoint()))
                .region(Region.of(storage.region()))
                .s3Configuration(addressing(storage))
                .build();
    }

    private static StaticCredentialsProvider credentials(StorageProperties storage) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
    }

    private static S3Configuration addressing(StorageProperties storage) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(storage.pathStyle())
                .build();
    }
}
