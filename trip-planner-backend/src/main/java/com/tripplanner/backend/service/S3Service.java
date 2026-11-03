package com.tripplanner.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface S3Service {
    boolean isS3Configured();
    String uploadFile(MultipartFile file, String folder);
    void deleteFile(String objectKey);
    String getFileUrl(String objectKey);
}
