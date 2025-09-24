package com.company.app.common.service;

import com.company.app.common.dto.Serial;
import com.company.app.document.entity.DocumentEntity;
import com.company.app.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 일련번호 생성 서비스
 * 카테고리 leaf code를 기준으로 순차적인 일련번호를 생성합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SerialNumberService {

    private final DocumentRepository documentRepository;

    /**
     * 카테고리 leaf code에 대한 다음 일련번호 생성
     */
    public Serial generateNextSerial(String leafCode) {
        try {
            if (leafCode == null || leafCode.isEmpty()) {
                leafCode = "UNCAT";
            }
            
            int nextNumber = getNextSerialNumber(leafCode);
            return Serial.of(leafCode, nextNumber);
            
        } catch (Exception e) {
            log.error("일련번호 생성 실패: {}", leafCode, e);
            // Fallback: 현재 시간 기반 번호
            int fallbackNumber = (int) (System.currentTimeMillis() % 10000);
            return Serial.of(leafCode, fallbackNumber);
        }
    }

    /**
     * 카테고리 코드별 다음 일련번호 조회
     */
    private int getNextSerialNumber(String leafCode) {
        try {
            // 해당 leafCode로 시작하는 마지막 일련번호 조회
            List<DocumentEntity> documents = documentRepository.findAll();
            int maxNumber = documents.stream()
                .filter(doc -> doc.getSerial() != null && doc.getSerial().getFull() != null)
                .filter(doc -> doc.getSerial().getFull().startsWith(leafCode + "-"))
                .mapToInt(doc -> {
                    try {
                        String[] parts = doc.getSerial().getFull().split("-");
                        return Integer.parseInt(parts[1]);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
            
            return maxNumber + 1;
            
        } catch (Exception e) {
            log.warn("일련번호 조회 실패, 랜덤 번호 사용: {}", leafCode, e);
            return (int) (Math.random() * 9000) + 1000; // 1000-9999 범위
        }
    }

    /**
     * 특정 카테고리 코드의 현재 최대 일련번호 조회
     */
    public int getCurrentMaxSerial(String leafCode) {
        try {
            List<DocumentEntity> documents = documentRepository.findAll();
            return documents.stream()
                .filter(doc -> doc.getSerial() != null && doc.getSerial().getFull() != null)
                .filter(doc -> doc.getSerial().getFull().startsWith(leafCode + "-"))
                .mapToInt(doc -> {
                    try {
                        String[] parts = doc.getSerial().getFull().split("-");
                        return Integer.parseInt(parts[1]);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
        } catch (Exception e) {
            log.warn("최대 일련번호 조회 실패: {}", leafCode, e);
            return 0;
        }
    }
}
