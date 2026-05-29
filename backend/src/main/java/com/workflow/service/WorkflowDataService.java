package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class WorkflowDataService {
    
    @Autowired
    private ObjectMapper objectMapper; 
    
    private WorkflowDTO workflowData;
    private Map<String, Object> returnOptionData;
    private Map<String, Object> paymentRequests;
    private List<UserDTO> users;

    public WorkflowDataService() {
    }

    @PostConstruct
    public void init() {
        loadMockData();
    }

    private void loadMockData() {
        try {
            // Load workflow.json
            InputStream workflowStream = getClass().getResourceAsStream("/mock-data/workflow.json");
            workflowData = objectMapper.readValue(workflowStream, WorkflowDTO.class);
            log.info("Loaded workflow data with {} steps", workflowData.getWorkflowSteps().size());

            // Load users.json
            InputStream usersStream = getClass().getResourceAsStream("/mock-data/users.json");
            users = Arrays.asList(objectMapper.readValue(usersStream, UserDTO[].class));
            log.info("Loaded {} users", users.size());

            // Load payment-request.json
            InputStream prStream = getClass().getResourceAsStream("/mock-data/payment-request.json");
            paymentRequests = objectMapper.readValue(prStream, Map.class);
            log.info("Loaded payment request data");

            // Load return-option.json
            InputStream roStream = getClass().getResourceAsStream("/mock-data/return-option.json");
            returnOptionData = objectMapper.readValue(roStream, Map.class);
            log.info("Loaded return option data");
        } catch (Exception e) {
            log.error("Error loading mock data", e);
            throw new RuntimeException("Failed to load mock data", e);
        }
    }

    public WorkflowDTO getWorkflowData() {
        return workflowData;
    }

    public WorkflowStepDTO findWorkflowStep(String stepId) {
        if (workflowData == null || workflowData.getWorkflowSteps() == null) {
            return null;
        }
        return workflowData.getWorkflowSteps().stream()
                .filter(step -> step.getId().equals(stepId))
                .findFirst()
                .orElse(null);
    }

    public List<UserDTO> getUsers() {
        return users;
    }

    public Map<String, Object> getPaymentRequests() {
        return paymentRequests;
    }

    public Map<String, Object> getReturnOptions() {
        return returnOptionData;
    }

    public void updateWorkflowData(WorkflowDTO workflow) {
        this.workflowData = workflow;
    }

    public void updatePaymentRequest(String prId, Map<String, Object> updates) {
        if (paymentRequests.containsKey(prId)) {
            paymentRequests.put(prId, updates);
        }
    }
}
