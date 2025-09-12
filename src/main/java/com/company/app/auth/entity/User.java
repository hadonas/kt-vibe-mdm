package com.company.app.auth.entity;

import com.company.app.common.dto.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User extends BaseEntity {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String email;
    
    private String name;
    private String passwordHash;
    private List<Role> roles;
    private LocalDateTime lastLoginAt;
    
    public enum Role {
        USER, APPROVER, ADMIN
    }
}
