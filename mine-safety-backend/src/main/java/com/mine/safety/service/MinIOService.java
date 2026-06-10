package com.mine.safety.service;

import com.mine.safety.config.MinIOConfig;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinIOService {

    private final MinioClient minioClient;
    private final MinIOConfig minIOConfig;

    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minIOConfig.getBucket()).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minIOConfig.getBucket()).build());
                log.info("MinIO Bucket已创建: {}", minIOConfig.getBucket());
            }
        } catch (Exception e) {
            log.warn("MinIO Bucket初始化失败: {}", e.getMessage());
        }
    }

    public String uploadFile(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minIOConfig.getBucket())
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());

            String url = getPresignedUrl(objectName);
            log.info("文件已上传到MinIO: {}, URL: {}", objectName, url);
            return url;
        } catch (Exception e) {
            log.error("上传文件到MinIO失败: {}", e.getMessage(), e);
            throw new RuntimeException("上传文件失败: " + e.getMessage());
        }
    }

    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minIOConfig.getBucket())
                    .object(objectName)
                    .expiry(minIOConfig.getPresignExpiryHours(), TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            log.error("获取预签名URL失败: {}", e.getMessage());
            return null;
        }
    }

    public InputStream downloadFile(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minIOConfig.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("从MinIO下载文件失败: {}", e.getMessage());
            throw new RuntimeException("下载文件失败: " + e.getMessage());
        }
    }

    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minIOConfig.getBucket())
                    .object(objectName)
                    .build());
            log.info("文件已从MinIO删除: {}", objectName);
        } catch (Exception e) {
            log.error("删除MinIO文件失败: {}", e.getMessage());
        }
    }
}
