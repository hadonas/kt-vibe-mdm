package com.company.app.ingest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Slf4j
public class TempDirectoryCleanupService {
    
    private final String tempDir = System.getProperty("java.io.tmpdir") + "/mdm-repos";
    
    /**
     * 매 시간마다 임시 디렉토리 정리
     * 1시간 이상 된 임시 디렉토리들을 삭제
     */
    @Scheduled(fixedRate = 3600000) // 1시간 = 3600000ms
    public void cleanupOldTempDirectories() {
        try {
            Path tempPath = Paths.get(tempDir);
            if (!Files.exists(tempPath)) {
                return;
            }
            
            log.info("Starting scheduled cleanup of temporary directories");
            
            Instant cutoffTime = Instant.now().minus(1, ChronoUnit.HOURS);
            final int[] deletedCount = {0};
            
            Files.list(tempPath)
                .filter(Files::isDirectory)
                .filter(dir -> isOlderThan(dir, cutoffTime))
                .forEach(dir -> {
                    try {
                        deleteDirectoryRecursively(dir);
                        deletedCount[0]++;
                        log.debug("Deleted old temporary directory: {}", dir);
                    } catch (Exception e) {
                        log.warn("Failed to delete old temporary directory: {}", dir, e);
                    }
                });
            
            if (deletedCount[0] > 0) {
                log.info("Scheduled cleanup completed - deleted {} old temporary directories", deletedCount[0]);
            }
            
        } catch (Exception e) {
            log.error("Scheduled cleanup failed", e);
        }
    }
    
    /**
     * 애플리케이션 시작 시 오래된 임시 디렉토리 정리
     */
    @Scheduled(initialDelay = 30000, fixedRate = Long.MAX_VALUE) // 30초 후 한 번만 실행
    public void cleanupOnStartup() {
        try {
            Path tempPath = Paths.get(tempDir);
            if (!Files.exists(tempPath)) {
                return;
            }
            
            log.info("Starting startup cleanup of temporary directories");
            
            Instant cutoffTime = Instant.now().minus(1, ChronoUnit.HOURS);
            final int[] deletedCount = {0};
            
            Files.list(tempPath)
                .filter(Files::isDirectory)
                .filter(dir -> isOlderThan(dir, cutoffTime))
                .forEach(dir -> {
                    try {
                        deleteDirectoryRecursively(dir);
                        deletedCount[0]++;
                        log.debug("Deleted old temporary directory on startup: {}", dir);
                    } catch (Exception e) {
                        log.warn("Failed to delete old temporary directory on startup: {}", dir, e);
                    }
                });
            
            if (deletedCount[0] > 0) {
                log.info("Startup cleanup completed - deleted {} old temporary directories", deletedCount[0]);
            }
            
        } catch (Exception e) {
            log.error("Startup cleanup failed", e);
        }
    }
    
    private boolean isOlderThan(Path directory, Instant cutoffTime) {
        try {
            return Files.getLastModifiedTime(directory).toInstant().isBefore(cutoffTime);
        } catch (IOException e) {
            log.debug("Could not get last modified time for: {}", directory, e);
            return false;
        }
    }
    
    private void deleteDirectoryRecursively(Path path) {
        try {
            if (Files.exists(path)) {
                // 파일 권한을 수정하여 삭제 가능하게 만들기
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(this::makeWritableAndDelete);
                
                // 디렉토리가 완전히 삭제되었는지 확인
                if (Files.exists(path)) {
                    // 강제 삭제 시도
                    try {
                        Thread.sleep(100);
                        Files.walk(path)
                            .sorted(Comparator.reverseOrder())
                            .forEach(this::forceDelete);
                    } catch (Exception e) {
                        log.debug("Force delete failed for: {}", path, e);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to delete directory: {}", path, e);
        }
    }
    
    private void makeWritableAndDelete(Path path) {
        try {
            if (Files.exists(path)) {
                File file = path.toFile();
                file.setWritable(true);
                file.setReadable(true);
                file.setExecutable(true);
                
                if (Files.isDirectory(path)) {
                    Files.deleteIfExists(path);
                } else {
                    Files.deleteIfExists(path);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to delete file/directory: {}", path, e);
        }
    }
    
    private void forceDelete(Path path) {
        try {
            if (Files.exists(path)) {
                File file = path.toFile();
                if (file.isDirectory()) {
                    File[] files = file.listFiles();
                    if (files != null) {
                        for (File child : files) {
                            forceDelete(child.toPath());
                        }
                    }
                }
                if (!file.delete()) {
                    log.debug("Could not delete: {}", path);
                }
            }
        } catch (Exception e) {
            log.debug("Force delete failed for: {}", path, e);
        }
    }
}
