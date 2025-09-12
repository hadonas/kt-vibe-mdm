package com.company.app.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    private String majorCode;
    private String majorName;
    private String midCode;
    private String midName;
    private String subCode;
    private String subName;
    
    public String getFullCode() {
        return majorCode + "-" + midCode + "-" + subCode;
    }
    
    public String getFullName() {
        return majorName + " > " + midName + " > " + subName;
    }
}
