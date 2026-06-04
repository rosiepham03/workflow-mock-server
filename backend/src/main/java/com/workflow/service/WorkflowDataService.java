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

    // Template loaded from resources. We keep this immutable and clone per PR.
    private WorkflowDTO workflowTemplate;
    // Per-PR instances keyed by documentId
    private final java.util.concurrent.ConcurrentMap<String, WorkflowDTO> workflowInstances = new java.util.concurrent.ConcurrentHashMap<>();
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
            try (InputStream workflowStream = getClass().getResourceAsStream("/mock-data/workflow.json")) {
                workflowTemplate = objectMapper.readValue(workflowStream, WorkflowDTO.class);
                log.info("Loaded workflow template with {} steps", workflowTemplate.getWorkflowSteps().size());
                // Clear documentId on the template so the template can be reused for many PRs
                // without carrying a fixed documentId from the resource file.
                if (workflowTemplate != null) {
                    workflowTemplate.setDocumentId(null);
                }
            }

            // Load users.json
            try (InputStream usersStream = getClass().getResourceAsStream("/mock-data/users.json")) {
                users = Arrays.asList(objectMapper.readValue(usersStream, UserDTO[].class));
                log.info("Loaded {} users", users.size());
            }

            // Load payment-request.json
            try (InputStream prStream = getClass().getResourceAsStream("/mock-data/payment-request.json")) {
                paymentRequests = objectMapper.readValue(prStream, Map.class);
                log.info("Loaded payment request data");

                // Initialize workflow instances for existing payment requests
                if (paymentRequests != null) {
                    for (Object key : paymentRequests.keySet()) {
                        if (key != null) {
                            String prId = key.toString();
                            WorkflowDTO instance = deepCloneTemplate(prId);
                            // try to align status with PR if available
                            Object prObj = paymentRequests.get(prId);
                            if (prObj instanceof Map) {
                                Map prMap = (Map) prObj;
                                Object status = prMap.get("status");
                                if (status != null) {
                                    instance.setStatus(status.toString());
                                }
                            }
                            workflowInstances.put(prId, instance);
                        }
                    }
                }
            }

            // Load return-option.json
            try (InputStream roStream = getClass().getResourceAsStream("/mock-data/return-option.json")) {
                returnOptionData = objectMapper.readValue(roStream, Map.class);
                log.info("Loaded return option data");
            }
        } catch (Exception e) {
            log.error("Error loading mock data", e);
            throw new RuntimeException("Failed to load mock data", e);
        }
    }

    private WorkflowDTO deepCloneTemplate(String documentId) {
        if (workflowTemplate == null)
            return null;
        WorkflowDTO clone = objectMapper.convertValue(workflowTemplate, WorkflowDTO.class);
        clone.setDocumentId(documentId);
        return clone;
    }

    // Get the template (for read-only or compatibility)
    public WorkflowDTO getWorkflowData() {
        return workflowTemplate;
    }

    // Get or create an instance for a given PR id
    public WorkflowDTO getWorkflowForPr(String prId) {
        // ConcurrentHashMap does not accept null keys - treat null as a request
        // for a transient clone of the template (do not store under null key).
        if (prId == null) {
            return deepCloneTemplate(null);
        }

        WorkflowDTO wf = workflowInstances.get(prId);

        if (wf == null) {
            wf = deepCloneTemplate(prId);
            if (wf != null) {
                workflowInstances.put(prId, wf);
            }
        }

        return wf;
    }

    public WorkflowStepDTO findWorkflowStep(String prId, String stepId) {
        WorkflowDTO wf = getWorkflowForPr(prId);
        if (wf == null || wf.getWorkflowSteps() == null)
            return null;
        WorkflowStepDTO step = wf.getWorkflowSteps().stream()
                .filter(s -> s.getId().equals(stepId))
                .findFirst()
                .orElse(null);
        if (step != null) {
            log.info("[DEBUG] findWorkflowStep prId={} stepId={} -> stepStatus={} approvers={} ", prId, stepId,
                    step.getStatus(), step.getStepApprovers() != null ? step.getStepApprovers().size() : 0);
            if (step.getStepApprovers() != null) {
                step.getStepApprovers().forEach(sa -> log.info("    - approverId={} proxyId={} status={}",
                        sa.getApproverId(), sa.getProxyApproverId(), sa.getStatus()));
            }
        } else {
            log.info("[DEBUG] findWorkflowStep prId={} stepId={} -> NOT FOUND", prId, stepId);
        }
        return step;
    }

    // Backwards-compatible update of template
    public void updateWorkflowData(WorkflowDTO workflow) {
        this.workflowTemplate = workflow;
    }

    // Update specific PR instance
    public void updateWorkflowData(String prId, WorkflowDTO workflow) {
        if (prId == null)
            return;
        workflowInstances.put(prId, workflow);
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

    public void updatePaymentRequest(String prId, Map<String, Object> updates) {
        if (paymentRequests.containsKey(prId)) {
            paymentRequests.put(prId, updates);
        }
    }

    // Reset only a specific PR instance back to template state
    public synchronized void resetInstance(String prId) {
        if (prId == null)
            return;
        WorkflowDTO instance = deepCloneTemplate(prId);
        workflowInstances.put(prId, instance);
        log.info("Reset workflow instance for PR {}", prId);
    }

    // Reload all templates and instances from resources (global reset)
    public synchronized void resetMockData() {
        log.info("Resetting mock data to original state from resources");
        loadMockData();
    }
}
