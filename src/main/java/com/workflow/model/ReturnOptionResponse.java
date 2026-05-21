package com.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnOptionResponse {
    @JsonProperty("success")
    private Boolean success;
    
    @JsonProperty("actorRole")
    private String actorRole;
    
    @JsonProperty("options")
    private List<ReturnOptionDTO> options;
}
