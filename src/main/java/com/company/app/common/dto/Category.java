package com.company.app.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryLevel {
        private int level;
        private String code;
        private String name;
    }
}
