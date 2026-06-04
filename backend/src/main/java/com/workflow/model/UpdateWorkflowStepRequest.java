package com.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkflowStepRequest {
    @JsonProperty("status_code")
    private String statusCode;
    
    @JsonProperty("comment")
    private String comment;
    
    @JsonProperty("actionBy")
    private String actionBy;
    
    @JsonProperty("approverId")
    private String approverId;
    
    @JsonProperty("targetId")
    private String targetId;

    @JsonProperty("targetApproverId")
    private String targetApproverId;

    @JsonProperty("documentId")
    private String documentId;
}