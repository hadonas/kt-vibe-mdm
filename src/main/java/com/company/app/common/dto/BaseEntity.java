package com.company.app.common.dto;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
public abstract class BaseEntity {
    
    @CreatedDate
    @Field("createdAt")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Field("updatedAt")
    private LocalDateTime updatedAt;
}
