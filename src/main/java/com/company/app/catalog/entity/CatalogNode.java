package com.company.app.catalog.entity;

import com.company.app.common.dto.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "catalog_nodes")
public class CatalogNode extends BaseEntity {
    
    @Id
    private String id;
    
    private Integer level; // 1: major, 2: mid, 3: sub
    private String code;
    private String name;
    private String parentCode;
    private Boolean active;
    private Integer order;
    private List<CatalogNode> children;
    
    public boolean isMajor() {
        return level == 1;
    }
    
    public boolean isMid() {
        return level == 2;
    }
    
    public boolean isSub() {
        return level == 3;
    }
}
