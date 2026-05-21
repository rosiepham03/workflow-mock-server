package com.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("employeeID")
    private String employeeID;
    
    @JsonProperty("fullName")
    private String fullName;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("status")
    private String status;
}
