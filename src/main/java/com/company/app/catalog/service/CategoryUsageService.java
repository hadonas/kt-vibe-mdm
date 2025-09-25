package com.company.app.catalog.service;

import com.company.app.catalog.repository.CatalogNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 카테고리 문서 사용량 증감 전용 서비스.
 * 계층 전체(documentCount)와 리프(leafDocumentCount)를 분리 집계.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryUsageService {
    private final CatalogNodeRepository catalogNodeRepository;

    @Transactional
    public void incrementUsage(List<String> codes, String leafCode) {
        if (codes == null || codes.isEmpty()) return;
        try {
            for (String code : codes) {
                catalogNodeRepository.findByCode(code).ifPresent(node -> {
                    if (node.getDocumentCount() == null) node.setDocumentCount(0);
                    node.setDocumentCount(node.getDocumentCount() + 1);
                    if (code.equals(leafCode)) {
                        if (node.getLeafDocumentCount() == null) node.setLeafDocumentCount(0);
                        node.setLeafDocumentCount(node.getLeafDocumentCount() + 1);
                    }
                    catalogNodeRepository.save(node);
                });
            }
        } catch (Exception e) {
            log.warn("카테고리 사용량 증가 중 오류: {} -> {}", codes, e.getMessage());
        }
    }

    @Transactional
    public void decrementUsage(List<String> codes, String leafCode) {
        if (codes == null || codes.isEmpty()) return;
        try {
            for (String code : codes) {
                catalogNodeRepository.findByCode(code).ifPresent(node -> {
                    if (node.getDocumentCount() == null) node.setDocumentCount(0);
                    node.setDocumentCount(Math.max(0, node.getDocumentCount() - 1));
                    if (code.equals(leafCode)) {
                        if (node.getLeafDocumentCount() == null) node.setLeafDocumentCount(0);
                        node.setLeafDocumentCount(Math.max(0, node.getLeafDocumentCount() - 1));
                    }
                    catalogNodeRepository.save(node);
                });
            }
        } catch (Exception e) {
            log.warn("카테고리 사용량 감소 중 오류: {} -> {}", codes, e.getMessage());
        }
    }
}
