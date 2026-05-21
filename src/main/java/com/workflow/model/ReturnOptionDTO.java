package com.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnOptionDTO {
    @JsonProperty("code")
    private String code;
    
    @JsonProperty("targetStepId")
    private String targetStepId;
    
    @JsonProperty("approverId")
    private String approverId;
    
    @JsonProperty("label_vn")
    private String labelVn;
    
    @JsonProperty("label_en")
    private String labelEn;
}
