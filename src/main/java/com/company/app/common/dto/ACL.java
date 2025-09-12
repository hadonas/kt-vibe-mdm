package com.company.app.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ACL {
    private String ownerId;
    private List<SharedUser> shared;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedUser {
        private String userId;
        private Permission permission;
    }
    
    public enum Permission {
        READ, WRITE
    }
}
