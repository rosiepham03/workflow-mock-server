package com.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepDTO {
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
    
    @JsonProperty("workflow_ID")
    private String workflowId;
    
    @JsonProperty("level_code")
    private Integer levelCode;
    
    @JsonProperty("levelName")
    private String levelName;
    
    @JsonProperty("faLevel_code")
    private Integer faLevelCode;
    
    @JsonProperty("stepType")
    private String stepType;
    
    @JsonProperty("sortOrder")
    private Integer sortOrder;
    
    @JsonProperty("deadline")
    private String deadline;
    
    @JsonProperty("completeAt")
    private String completeAt;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("comment")
    private String comment;
    
    @JsonProperty("isParallel")
    private Boolean isParallel;
    
    @JsonProperty("allowSkip")
    private Boolean allowSkip;
    
    @JsonProperty("IsActiveEntity")
    private Boolean isActiveEntity;
    
    @JsonProperty("HasActiveEntity")
    private Boolean hasActiveEntity;
    
    @JsonProperty("HasDraftEntity")
    private Boolean hasDraftEntity;
    
    @JsonProperty("stepApprovers")
    private List<StepApproverDTO> stepApprovers;

    @JsonProperty("DraftMessages") 
    private List<String> draftMessages;
}
