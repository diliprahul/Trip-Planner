package com.tripplanner.backend.service.impl;

import com.tripplanner.backend.service.S3Service;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class S3ServiceImpl implements S3Service {

    @Value("${aws.s3.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.s3.bucket.name:}")
    private String bucketName;

    @Value("${aws.s3.use-presigned-url:true}")
    private boolean usePresignedUrl;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    @PostConstruct
    public void init() {
        if (bucketName != null && !bucketName.isBlank()) {
            try {
                Region region = Region.of(awsRegion);
                this.s3Client = S3Client.builder().region(region).build();
                if (usePresignedUrl) {
                    this.s3Presigner = S3Presigner.builder().region(region).build();
                }
                log.info("Initialized AWS S3 Client for bucket: {} in region: {}", bucketName, awsRegion);
            } catch (Exception e) {
                log.warn("Failed to initialize AWS S3 client: {}. S3 features may be disabled.", e.getMessage());
            }
        } else {
            log.info("AWS S3 bucket name not configured. Falling back to local file storage.");
        }
    }

    @Override
    public boolean isS3Configured() {
        return bucketName != null && !bucketName.isBlank() && s3Client != null;
    }

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        if (!isS3Configured()) {
            throw new IllegalStateException("S3 is not configured");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        String key = (folder != null && !folder.isBlank() ? folder.replaceAll("/+$", "") + "/" : "") + UUID.randomUUID().toString() + ext;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
            log.info("Successfully uploaded file to S3: bucket={}, key={}", bucketName, key);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file bytes", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        if (!isS3Configured() || objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted S3 object: bucket={}, key={}", bucketName, objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", objectKey, e.getMessage());
        }
    }

    @Override
    public String getFileUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }
        if (!isS3Configured()) {
            return null;
        }

        try {
            if (usePresignedUrl && s3Presigner != null) {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();

                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofHours(2))
                        .getObjectRequest(getObjectRequest)
                        .build();

                PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
                return presignedRequest.url().toString();
            } else {
                return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, awsRegion, objectKey);
            }
        } catch (Exception e) {
            log.warn("Failed to generate S3 URL for key {}: {}", objectKey, e.getMessage());
            return null;
        }
    }
}
