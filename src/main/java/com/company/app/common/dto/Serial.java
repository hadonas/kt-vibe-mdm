package com.company.app.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Serial {
    private String subCode;
    private Integer number;
    private String full;
    
    public static Serial of(String subCode, Integer number) {
        String full = subCode + "-" + String.format("%04d", number);
        return new Serial(subCode, number, full);
    }
}
