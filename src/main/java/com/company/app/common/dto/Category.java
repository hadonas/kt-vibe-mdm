package com.company.app.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Category {
    // 기존 3단계 호환성을 위한 필드들
    private String majorCode;
    private String majorName;
    private String midCode;
    private String midName;
    private String subCode;
    private String subName;
    
    // 가변 계층 지원을 위한 새 필드들
    private String fullCode;
    private String fullName;
    private List<CategoryLevel> hierarchy;
    
    // 기존 생성자와 호환성을 위한 생성자
    public Category(String majorCode, String majorName, String midCode, String midName, String subCode, String subName) {
        this.majorCode = majorCode;
        this.majorName = majorName;
        this.midCode = midCode;
        this.midName = midName;
        this.subCode = subCode;
        this.subName = subName;
        this.fullCode = majorCode + "-" + midCode + "-" + subCode;
        this.fullName = majorName + " > " + midName + " > " + subName;
    }
    
    // 가변 계층을 위한 새 생성자
    public Category(String fullCode, String fullName, List<CategoryLevel> hierarchy) {
        this.fullCode = fullCode;
        this.fullName = fullName;
        this.hierarchy = hierarchy;
        
        // 기존 필드 호환성을 위해 설정 (3단계까지만)
        if (hierarchy != null && !hierarchy.isEmpty()) {
            if (hierarchy.size() >= 1) {
                this.majorCode = hierarchy.get(0).getCode();
                this.majorName = hierarchy.get(0).getName();
            }
            if (hierarchy.size() >= 2) {
                this.midCode = hierarchy.get(1).getCode();
                this.midName = hierarchy.get(1).getName();
            }
            if (hierarchy.size() >= 3) {
                this.subCode = hierarchy.get(2).getCode();
                this.subName = hierarchy.get(2).getName();
            }
        }
    }
    
    public String getFullCode() {
        return fullCode != null ? fullCode : (majorCode + "-" + midCode + "-" + subCode);
    }
    
    public String getFullName() {
        return fullName != null ? fullName : (majorName + " > " + midName + " > " + subName);
    }

    // ----- Helper methods for hierarchy-first refactor (non-breaking) -----
    /**
     * Returns hierarchy codes in order. Falls back to legacy 3-level fields if hierarchy is null.
     */
    public List<String> getCodes() {
        if (hierarchy != null && !hierarchy.isEmpty()) {
            java.util.List<String> list = new java.util.ArrayList<>();
            for (CategoryLevel level : hierarchy) {
                list.add(level.getCode());
            }
            return list;
        }
        return new java.util.ArrayList<>(java.util.Arrays.asList(majorCode, midCode, subCode));
    }

    /**
     * Returns hierarchy names in order. Falls back to legacy 3-level fields if hierarchy is null.
     */
    public List<String> getNames() {
        if (hierarchy != null && !hierarchy.isEmpty()) {
            java.util.List<String> list = new java.util.ArrayList<>();
            for (CategoryLevel level : hierarchy) {
                list.add(level.getName());
            }
            return list;
        }
        return new java.util.ArrayList<>(java.util.Arrays.asList(majorName, midName, subName));
    }

    /** Depth of the category (hierarchy size or 3 if legacy). */
    public int getDepth() {
        if (hierarchy != null && !hierarchy.isEmpty()) {
            return hierarchy.size();
        }
        return 3;
    }

    /** Leaf code (last code in hierarchy or subCode). */
    public String getLeafCode() {
        if (hierarchy != null && !hierarchy.isEmpty()) {
            return hierarchy.get(hierarchy.size() - 1).getCode();
        }
        return subCode;
    }

    /** Leaf name (last name in hierarchy or subName). */
    public String getLeafName() {
        if (hierarchy != null && !hierarchy.isEmpty()) {
            return hierarchy.get(hierarchy.size() - 1).getName();
        }
        return subName;
    }

    /** Display path like "A > B > C" built dynamically. */
    public String getDisplayPath() {
        if (hierarchy != null && !hierarchy.isEmpty()) {
            return String.join(" > ", getNames());
        }
        return getFullName();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryLevel {
        private int level;
        private String code;
        private String name;
    }
}
