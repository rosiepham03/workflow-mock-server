package com.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDTO {
    @JsonProperty("@context")
    private String context;
    
    @JsonProperty("@metadataEtag")
    private String metadataEtag;
    
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
    
    @JsonProperty("documentId")
    private String documentId;
    
    @JsonProperty("documentType")
    private String documentType;
    
    @JsonProperty("rule_ID")
    private String ruleId;
    
    @JsonProperty("faRule_ID")
    private String faRuleId;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("amount")
    private Long amount;
    
    @JsonProperty("currency_code")
    private String currencyCode;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("requester_id")
    private String requesterId;
    
    @JsonProperty("processCategoryId")
    private String processCategoryId;
    
    @JsonProperty("workflowSteps")
    private List<WorkflowStepDTO> workflowSteps;
}
