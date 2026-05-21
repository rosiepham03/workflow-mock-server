package com.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepApproverDTO {
    @JsonProperty("ID")
    private String id;
    
    @JsonProperty("createdAt")
    private String createdAt;
    
    @JsonProperty("createdBy")
    private String createdBy;
    
    @JsonProperty("modifiedAt")
    private String modifiedAt;
    
    @JsonProperty("modifiedBy")
    private String modifiedBy;
    
    @JsonProperty("step_ID")
    private String stepId;
    
    @JsonProperty("approver_id")
    private String approverId;
    
    @JsonProperty("proxyApprover_id")
    private String proxyApproverId;
    
    @JsonProperty("actualApprover_id")
    private String actualApproverId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("completeAt")
    private String completeAt;
    
    @JsonProperty("comment")
    private String comment;
    
    @JsonProperty("IsActiveEntity")
    private Boolean isActiveEntity;
    
    @JsonProperty("approver")
    private ApproverDTO approver;
    
    @JsonProperty("proxyApprover")
    private ApproverDTO proxyApprover;
}
