package com.dontdelay.exam.infra;

import com.dontdelay.exam.config.AwsProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

public class S3StorageClient implements StorageClient {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3StorageClient(AwsProperties awsProperties) {
        this.bucket = awsProperties.getS3().getBucket();
        this.s3Client = S3Client.builder()
                .region(software.amazon.awssdk.regions.Region.of(awsProperties.getRegion()))
                .build();
        this.presigner = S3Presigner.builder()
                .region(software.amazon.awssdk.regions.Region.of(awsProperties.getRegion()))
                .build();
    }

    @Override
    public void put(String key, InputStream body, long sizeBytes, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(body, sizeBytes));
    }

    @Override
    public InputStream get(String key) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    @Override
    public URL presignedGetUrl(String key, Duration ttl) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(b -> b.bucket(bucket).key(key))
                .build();
        return presigner.presignGetObject(presignRequest).url();
    }
}
