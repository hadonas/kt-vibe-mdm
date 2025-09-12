package com.company.app.catalog.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "counters")
public class Counter {
    
    @Id
    private String subCode;
    private Integer seq;
    
    public Counter(String subCode) {
        this.subCode = subCode;
        this.seq = 0;
    }
}
