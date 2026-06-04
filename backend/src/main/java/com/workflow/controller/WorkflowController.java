package com.workflow.controller;

import com.workflow.model.*;
import com.workflow.service.WorkflowDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/")
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", exposedHeaders = "*", methods = { RequestMethod.GET,
        RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS }, allowCredentials = "true")
public class WorkflowController {

    @Autowired
    private WorkflowDataService workflowDataService;

    private String mapLevelToRole(String levelName) {
        if (levelName == null)
            return "Supervisor";

        if (levelName.contains("Supervisor"))
            return "Supervisor";
        if (levelName.contains("Section Manager"))
            return "Section Manager";
        if (levelName.contains("Department Manager"))
            return "Department Manager";
        if (levelName.contains("Deputy Division Manager"))
            return "Deputy Division Manager";
        if (levelName.contains("Division Manager"))
            return "Division Manager";
        if (levelName.contains("Accountant"))
            return "Accountant";

        return levelName;
    }

    // 1. GET FULL WORKFLOW
    @GetMapping("/wfstep")
    public ResponseEntity<?> getFullWorkflow(@RequestParam(required = false) String prId) {
        log.info("[GET] /wfstep - Lấy cấu trúc toàn bộ workflow for prId={}", prId);
        WorkflowDTO wf = workflowDataService.getWorkflowForPr(prId);
        if (wf != null && wf.getWorkflowSteps() != null) {
            log.info("[DEBUG] /wfstep prId={} -> instanceHash={} firstStepStatus={}", prId,
                    System.identityHashCode(wf), wf.getWorkflowSteps().get(0).getStatus());
        } else {
            log.info("[DEBUG] /wfstep prId={} -> no workflow found", prId);
        }
        return ResponseEntity.ok(wf);
    }

    // 2. DYNAMIC RETURN OPTIONS
    @GetMapping("/api/workflow/return-options")
    public ResponseEntity<?> getReturnOptions(
            @RequestParam String stepId,
            @RequestParam String approverId,
            @RequestParam(required = false) String prId) {

        log.info("[BE LOG] Tính toán danh sách Return hợp lệ cho step: {}", stepId);

        // Tìm thông tin bước hiện tại
        WorkflowStepDTO actionStep = workflowDataService.findWorkflowStep(prId, stepId);
        if (actionStep == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Không tìm thấy Step hiện tại"));
        }

        // Khởi tạo đối tượng Response chính theo Model
        ReturnOptionResponse response = new ReturnOptionResponse();
        response.setSuccess(true);
        // Map tên level hiện tại thành Role (ví dụ: "Section Manager")
        response.setActorRole(mapLevelToRole(actionStep.getLevelName()));

        List<ReturnOptionDTO> options = new ArrayList<>();

        for (WorkflowStepDTO step : workflowDataService.getWorkflowForPr(prId).getWorkflowSteps()) {
            if (step.getSortOrder() < actionStep.getSortOrder()) { // Chỉ lấy các bước nằm phía trước
                if (step.getStepApprovers() != null) {
                    for (StepApproverDTO sa : step.getStepApprovers()) {

                        // Khởi tạo DTO cho từng lựa chọn trả về
                        ReturnOptionDTO dto = new ReturnOptionDTO();

                        // Nếu approver có proxy, sử dụng proxy làm approver thực tế
                        ApproverDTO effective = null;
                        boolean isProxy = false;
                        if (sa.getProxyApprover() != null) {
                            effective = sa.getProxyApprover();
                            isProxy = true;
                        } else if (sa.getApprover() != null) {
                            effective = sa.getApprover();
                        }

                        String approverName = (effective != null) ? effective.getFullName() : sa.getApproverId();
                        if (isProxy) {
                            approverName = approverName + " (Ủy quyền)";
                        }
                        String approverEmail = (effective != null) ? effective.getEmail() : null;

                        // Mapping dữ liệu vào Model ReturnOptionDTO
                        dto.setName(approverName);
                        dto.setEmail(approverEmail);
                        dto.setOrder(step.getSortOrder());
                        dto.setStepId(step.getId());
                        dto.setUserId(effective != null ? effective.getId() : sa.getApproverId());
                        dto.setPosition(step.getLevelName()); // Chức vụ/Vị trí tương ứng với Level bước duyệt
                        dto.setDepartment(null);

                        options.add(dto);
                    }
                }
            }
        }

        // Gán danh sách options vào đối tượng response
        response.setOptions(options);

        return ResponseEntity.ok(response);
    }

    // 3. UPDATE WORKFLOW STEP STATUS & OUTCOMES
    @PostMapping("/api/workflow/WorkflowSteps/{id}")
    public ResponseEntity<?> updateWorkflowStepStatus(@PathVariable String id,
            @RequestBody UpdateWorkflowStepRequest request,
            @RequestParam(required = false) String prId) {
        // prefer documentId from body, fallback to prId query param
        String bodyDocId = request.getDocumentId();
        if (bodyDocId != null && !bodyDocId.isBlank()) {
            prId = bodyDocId;
        }
        if (prId == null || prId.isBlank()) {
            log.warn("Missing documentId/prId in UpdateWorkflowStepRequest for step {}. Rejecting.", id);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing documentId (provide in JSON as documentId) or prId query param"));
        }
        WorkflowStepDTO step = workflowDataService.findWorkflowStep(prId, id);
        if (step == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Workflow Step not found trong hệ thống."));
        }

        log.info("[DEBUG] updateWorkflowStepStatus called for stepId={} prId={}", id, prId);

        StepApproverDTO currentApprover = null;
        if (step.getStepApprovers() != null && !step.getStepApprovers().isEmpty()) {
            if (request.getApproverId() != null) {
                String selectedId = request.getApproverId();
                currentApprover = step.getStepApprovers().stream()
                        .filter(sa -> (sa.getApproverId() != null && sa.getApproverId().equals(selectedId))
                                || (sa.getProxyApproverId() != null && sa.getProxyApproverId().equals(selectedId))
                                || (sa.getApprover() != null && sa.getApprover().getId() != null
                                        && sa.getApprover().getId().equals(selectedId))
                                || (sa.getProxyApprover() != null && sa.getProxyApprover().getId() != null
                                        && sa.getProxyApprover().getId().equals(selectedId)))
                        .findFirst()
                        .orElse(null);

                if (currentApprover != null) {
                    log.info("[DEBUG] matched approver candidate by selectedId={}", selectedId);
                    // If the selected id belongs to the proxy, record actualApprover and prefer
                    // proxy as approver for display
                    boolean usedProxy = (currentApprover.getProxyApproverId() != null
                            && currentApprover.getProxyApproverId().equals(selectedId))
                            || (currentApprover.getProxyApprover() != null
                                    && currentApprover.getProxyApprover().getId() != null
                                    && currentApprover.getProxyApprover().getId().equals(selectedId));
                    currentApprover.setActualApproverId(selectedId);
                    if (usedProxy && currentApprover.getProxyApprover() != null) {
                        currentApprover.setApprover(currentApprover.getProxyApprover());
                    }
                }
            }
            if (currentApprover == null) {
                currentApprover = step.getStepApprovers().get(0);
            }
        }

        if (currentApprover == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Không tìm thấy thông tin Người duyệt (approverId) trong Step này."));
        }

        String outcome = request.getStatusCode() != null ? request.getStatusCode().toUpperCase() : "";
        String nowIso = Instant.now().toString();

        // Lưu vết log cá nhân trên bản ghi Approver
        currentApprover.setComment(request.getComment());
        currentApprover.setCompleteAt(nowIso);
        currentApprover.setModifiedAt(nowIso);
        currentApprover.setModifiedBy(request.getActionBy() != null ? request.getActionBy() : "SYSTEM");

        WorkflowDTO workflow = workflowDataService.getWorkflowForPr(prId);

        switch (outcome) {
            case "APPROVE":

                currentApprover.setStatus("APPROVED");
                currentApprover.setCompleteAt(nowIso);

                if (step.getStepApprovers() != null) {

                    for (StepApproverDTO sa : step.getStepApprovers()) {

                        boolean isDirectApprover = Objects.equals(sa.getApproverId(),
                                currentApprover.getApproverId());

                        boolean isAssignedApprover = sa.getApprover() != null
                                && currentApprover.getApprover() != null
                                && Objects.equals(
                                        sa.getApprover().getId(),
                                        currentApprover.getApprover().getId());

                        boolean isProxyApprover = Objects.equals(
                                sa.getProxyApproverId(),
                                currentApprover.getActualApproverId());

                        if (isDirectApprover
                                || isAssignedApprover
                                || isProxyApprover) {

                            sa.setStatus("APPROVED");
                            sa.setCompleteAt(nowIso);
                        }
                    }
                }

                log.info("[DEBUG] Step '{}' approver statuses AFTER SYNC:", step.getLevelName());

                if (step.getStepApprovers() != null) {
                    step.getStepApprovers()
                            .forEach(sa -> log.info("  - approverId={} status={}", sa.getApproverId(), sa.getStatus()));
                }

                boolean allApproved = step.getStepApprovers() != null &&
                        !step.getStepApprovers().isEmpty() &&
                        step.getStepApprovers().stream()
                                .allMatch(sa -> "APPROVED".equals(sa.getStatus()));

                if (step.getIsParallel() == null || !step.getIsParallel() || allApproved) {

                    step.setStatus("COMPLETED");
                    step.setCompleteAt(nowIso);

                    WorkflowStepDTO nextStep = workflow.getWorkflowSteps().stream()
                            .filter(s -> s.getSortOrder() == step.getSortOrder() + 1)
                            .findFirst()
                            .orElse(null);

                    if (nextStep != null) {

                        boolean canActivate = nextStep.getStatus() == null ||
                                "PLANNED".equals(nextStep.getStatus()) ||
                                "RETURNED".equals(nextStep.getStatus());

                        if (canActivate) {
                            nextStep.setStatus("READY");
                        }

                        if (nextStep.getStepApprovers() != null) {

                            for (StepApproverDTO sa : nextStep.getStepApprovers()) {

                                boolean resetAllowed = sa.getStatus() == null ||
                                        "PLANNED".equals(sa.getStatus()) ||
                                        "RETURNED".equals(sa.getStatus());

                                if (resetAllowed) {
                                    sa.setStatus("READY");
                                    sa.setCompleteAt(null);
                                    sa.setComment(null);
                                }
                            }
                        }

                        workflow.setStatus("IN_PROGRESS");
                    }

                } else {
                    step.setStatus("IN_PROGRESS");
                    workflow.setStatus("IN_PROGRESS");
                }

                log.info("[DEBUG] Step '{}' FINAL approver statuses:", step.getLevelName());

                if (step.getStepApprovers() != null) {
                    step.getStepApprovers()
                            .forEach(sa -> log.info("  - approverId={} status={}", sa.getApproverId(), sa.getStatus()));
                }

                break;

            case "REJECT":
                currentApprover.setStatus("REJECTED");
                step.setStatus("REJECTED");
                step.setCompleteAt(nowIso);
                workflow.setStatus("REJECTED");

                workflow.getWorkflowSteps().forEach(s -> {
                    if ("PLANNED".equals(s.getStatus())) {
                        s.setStatus("CANCELLED");
                    }
                    if (s.getStepApprovers() != null) {
                        s.getStepApprovers().forEach(sa -> {
                            if (!"REJECTED".equals(sa.getStatus())) {
                                sa.setStatus("PLANNED");
                                sa.setCompleteAt(null);
                            }
                        });
                    }
                });
                break;

            case "RETURN":
                currentApprover.setStatus("RETURNED");
                step.setStatus("RETURNED");

                if (request.getTargetId() != null) {
                    WorkflowStepDTO targetStep = workflow.getWorkflowSteps().stream()
                            .filter(s -> s.getId().equals(request.getTargetId()))
                            .findFirst()
                            .orElse(null);

                    if (targetStep != null) {
                        targetStep.setStatus("READY");
                        if (targetStep.getStepApprovers() != null) {
                            targetStep.getStepApprovers().forEach(sa -> {
                                if (request.getTargetApproverId() == null ||
                                        sa.getApproverId().equals(request.getTargetApproverId())) {
                                    sa.setStatus("READY");
                                    sa.setCompleteAt(null);
                                    sa.setComment(null);
                                } else {
                                    sa.setStatus("PLANNED");
                                }
                            });
                        }
                        workflow.setStatus("RETURNED_TO_" + targetStep.getLevelName().toUpperCase().replace(" ", "_"));
                    }
                } else {
                    workflow.setStatus("RETURNED_TO_REQUESTER");
                }

                int currentSortOrder = step.getSortOrder();
                workflow.getWorkflowSteps().stream()
                        .filter(s -> s.getSortOrder() >= currentSortOrder)
                        .forEach(s -> {
                            s.setStatus("PLANNED");
                            s.setCompleteAt(null);

                            if (s.getStepApprovers() != null) {
                                s.getStepApprovers().forEach(sa -> {
                                    sa.setStatus("PLANNED");
                                    sa.setCompleteAt(null);
                                    sa.setComment(null);
                                });
                            }
                        });
                break;

            case "REASSIGN":

                if (request.getTargetId() == null) {
                    return ResponseEntity.badRequest().body(
                            Map.of("error", "Thiếu targetId"));
                }

                UserDTO targetUser = workflowDataService.getUsers().stream()
                        .filter(u -> u.getId().equals(request.getTargetId()))
                        .findFirst()
                        .orElse(null);

                if (targetUser == null) {
                    return ResponseEntity.badRequest().body(
                            Map.of("error", "Không tìm thấy user được assign"));
                }

                ApproverDTO newApprover = new ApproverDTO();
                newApprover.setId(targetUser.getId());
                newApprover.setEmployeeID(targetUser.getEmployeeID());
                newApprover.setFullName(targetUser.getFullName());
                newApprover.setEmail(targetUser.getEmail());

                // lưu người cũ để audit nếu cần
                currentApprover.setActualApproverId(
                        currentApprover.getApproverId());

                // QUAN TRỌNG:
                // đổi owner của step sang người mới
                currentApprover.setApproverId(targetUser.getId());

                currentApprover.setApprover(newApprover);

                currentApprover.setProxyApprover(null);
                currentApprover.setProxyApproverId(null);

                currentApprover.setStatus("READY");
                currentApprover.setCompleteAt(null);
                currentApprover.setComment(null);

                // REASSIGN không reset workflow
                step.setStatus("IN_PROGRESS");
                workflow.setStatus("IN_PROGRESS");

                log.info(
                        "[REASSIGN] Step={} reassigned from {} to {}",
                        step.getLevelName(),
                        currentApprover.getActualApproverId(),
                        targetUser.getId());

                break;

            default:
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Action '" + request.getStatusCode()
                                + "' không hợp lệ. Cho phép: APPROVE, REJECT, RETURN, REASSIGN, ON_HOLD"));
        }

        Map<String, Object> allPRs = workflowDataService.getPaymentRequests();
        // prId is already known (from request). If not provided, try
        // workflow.documentId
        if (prId == null && workflow != null) {
            prId = workflow.getDocumentId();
        }
        if (prId != null && allPRs != null && allPRs.containsKey(prId)) {
            Map<String, Object> paymentRequest = (Map<String, Object>) allPRs.get(prId);
            paymentRequest.put("status", workflow.getStatus());

            List<Map<String, Object>> historyLog = (List<Map<String, Object>>) paymentRequest
                    .computeIfAbsent("history_log", k -> new ArrayList<>());
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("step", step.getLevelName());
            logEntry.put("approver", request.getActionBy() != null ? request.getActionBy() : "UNKNOWN");
            logEntry.put("status", outcome);
            logEntry.put("comment", request.getComment() != null ? request.getComment() : "");
            logEntry.put("timestamp", nowIso);
            historyLog.add(logEntry);
        }

        step.setModifiedAt(nowIso);
        step.setModifiedBy(request.getActionBy() != null ? request.getActionBy() : "SYSTEM");

        // If workflow reached a final state, reset only this PR instance
        String wfStatus = workflow != null ? workflow.getStatus() : null;
        if (prId != null && wfStatus != null) {
            String wfStatusUpper = wfStatus.toUpperCase();
            if ("APPROVED".equals(wfStatusUpper) || "REJECTED".equals(wfStatusUpper)) {
                log.info("Workflow for PR {} finished with status {} - resetting instance", prId, wfStatusUpper);
                // workflowDataService.resetInstance(prId);
            }
        }

        // Persist changes to the in-memory instance so subsequent GETs reflect updates
        if (prId != null && workflow != null) {
            workflowDataService.updateWorkflowData(prId, workflow);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("message", "Workflow Step " + id + " processed successfully with outcome [" + outcome + "]");

        Map<String, Object> inner = new HashMap<>();
        inner.put("ID", step.getId());
        inner.put("stepStatus", step.getStatus());
        inner.put("workflowStatus", workflow.getStatus());
        inner.put("currentApproverStatus", currentApprover.getStatus());

        data.put("data", inner);

        return ResponseEntity.ok(data);
    }

    // 4. GET SINGLE WORKFLOW STEP
    @GetMapping("/api/workflow/WorkflowSteps/{id}")
    public ResponseEntity<?> getWorkflowStep(@PathVariable String id) {
        log.info("[GET] Chi tiết Workflow Step: {}", id);
        String prId = null;
        // extract prId from request (via RequestParam cannot be added here easily) -
        // fallback to template
        WorkflowStepDTO step = workflowDataService.findWorkflowStep(prId, id);
        if (step == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Workflow Step not found"));
        }

        return ResponseEntity.ok(new HashMap<String, Object>() {
            {
                put("ID", step.getId());
                put("status_code", step.getStatus());
                put("IsActiveEntity", step.getIsActiveEntity());
                put("stepApprovers", step.getStepApprovers());
            }
        });
    }

    // 6. GET VALID REASSIGNEES (Lọc User đồng cấp hợp lệ)
    @GetMapping("/api/workflow/valid-reassignees")
        public ResponseEntity<?> getValidReassignees(
            @RequestParam String currentApproverId,
            @RequestParam(required = false) String currentRole) {

        return internalGetValidReassignees(currentApproverId, currentRole);
        }

        // Compatibility: accept old POST path and also GET on the old path
        @PostMapping("/api/workflow/get-valid-reassignees")
        public ResponseEntity<?> postGetValidReassignees(HttpServletRequest request) {
            String currentApproverId = null;
            String currentRole = null;

            try {
                String contentType = request.getContentType();
                if (contentType != null && contentType.contains("application/json")) {
                    String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                    if (body != null && !body.isBlank()) {
                        ObjectMapper om = new ObjectMapper();
                        Map<String, Object> map = om.readValue(body, new TypeReference<Map<String, Object>>() {
                        });
                        if (map != null) {
                            if (map.get("current_approver_id") != null)
                                currentApproverId = String.valueOf(map.get("current_approver_id"));
                            if (currentApproverId == null && map.get("currentApproverId") != null)
                                currentApproverId = String.valueOf(map.get("currentApproverId"));

                            if (map.get("current_role") != null)
                                currentRole = String.valueOf(map.get("current_role"));
                            if (currentRole == null && map.get("currentRole") != null)
                                currentRole = String.valueOf(map.get("currentRole"));
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to parse JSON request for get-valid-reassignees: {}", ex.getMessage());
            }

            // fallback to request params (form or query)
            if (currentApproverId == null) {
                currentApproverId = request.getParameter("current_approver_id");
                if (currentApproverId == null) currentApproverId = request.getParameter("currentApproverId");
            }
            if (currentRole == null) {
                currentRole = request.getParameter("current_role");
                if (currentRole == null) currentRole = request.getParameter("currentRole");
            }

            log.info("[POST] /api/workflow/get-valid-reassignees -> approverId={}, role={}", currentApproverId,
                    currentRole);

            return internalGetValidReassignees(currentApproverId, currentRole);
        }

        @GetMapping("/api/workflow/get-valid-reassignees")
        public ResponseEntity<?> getCompatGetValidReassignees(
            @RequestParam String currentApproverId,
            @RequestParam(required = false) String currentRole) {

        log.info("[GET] /api/workflow/get-valid-reassignees -> approverId={}, role={}", currentApproverId,
            currentRole);

        return internalGetValidReassignees(currentApproverId, currentRole);
        }

        // Shared helper used by all variants
        private ResponseEntity<?> internalGetValidReassignees(String currentApproverId, String currentRole) {

        log.info("[INTERNAL] getValidReassignees -> approverId={}, role={}", currentApproverId, currentRole);

        List<UserDTO> allUsers = workflowDataService.getUsers();

        if (allUsers == null) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "total", 0,
                "data", Collections.emptyList()));
        }

        // Nếu không truyền role thì tự tìm từ user hiện tại
        String role = currentRole;

        if (role == null || role.isBlank()) {
            role = allUsers.stream()
                .filter(u -> Objects.equals(u.getId(), currentApproverId))
                .map(UserDTO::getRole)
                .findFirst()
                .orElse(null);
        }

        if (role == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Cannot determine current role"));
        }

        final String finalRole = role;

        List<UserDTO> validUsers = allUsers.stream()
            .filter(user -> "Active".equalsIgnoreCase(user.getStatus())
                && Objects.equals(user.getRole(), finalRole)
                && !Objects.equals(user.getId(), currentApproverId))
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "total", validUsers.size(),
            "data", validUsers));
        }

    // 7. GET HISTORY STEPS
    @GetMapping("/api/pr/{id}/history-steps")
    public ResponseEntity<?> getPRHistorySteps(@PathVariable String id) {
        log.info("[GET] /api/pr/{}/history-steps - Lấy danh sách lịch sử đầy đủ trạng thái", id);

        Map<String, Object> allPRs = workflowDataService.getPaymentRequests();
        if (allPRs == null || !allPRs.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Payment Request not found"));
        }

        Map<String, Object> paymentRequest = (Map<String, Object>) allPRs.get(id);
        List<Map<String, Object>> historyLog = (List<Map<String, Object>>) paymentRequest.get("history_log");

        if (historyLog == null) {
            return ResponseEntity.ok(Map.of("success", true, "data", Collections.emptyList()));
        }

        List<Map<String, Object>> filteredHistory = historyLog.stream()
                .filter(logEntry -> {
                    String status = (String) logEntry.get("status");
                    if (status == null)
                        return false;

                    String statusUpper = status.toUpperCase();

                    return statusUpper.contains("APPROVE")
                            || "COMPLETED".equals(statusUpper)
                            || "REJECT".equals(statusUpper)
                            || "RETURN".equals(statusUpper)
                            || "REASSIGN".equals(statusUpper)
                            || "ON_HOLD".equals(statusUpper);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("success", true, "data", filteredHistory));
    }

    // 8. GET PAYMENT REQUEST DETAIL
    @GetMapping("/api/pr/{id}")
    public ResponseEntity<?> getPRDetail(@PathVariable String id) {
        log.info("[GET] Đọc thông tin chi tiết của PR ID: {}", id);

        Map<String, Object> allPRs = workflowDataService.getPaymentRequests();
        if (allPRs == null || !allPRs.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Payment Request not found"));
        }

        return ResponseEntity.ok(allPRs.get(id));
    }

    // 9. Upload attachment for PR (simple demo - stores filename in memory)
    @PostMapping("/api/pr/{id}/attachment")
    public ResponseEntity<?> uploadPrAttachment(@PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        Map<String, Object> allPRs = workflowDataService.getPaymentRequests();
        if (allPRs == null || !allPRs.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Payment Request not found"));
        }

        try {
            // destination under project resources (absolute path to workspace backend)
            Path uploadsDir = Paths
                    .get("/home/user/projects/workflow-mock-server/backend/src/main/resources/mock-data/file");
            Files.createDirectories(uploadsDir);

            String rawName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
            String safeName = rawName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String filename = System.currentTimeMillis() + "_" + safeName;

            Path dest = uploadsDir.resolve(filename);
            // save file to disk
            file.transferTo(dest.toFile());

            // store filename into the in-memory payment request map under key 'attachment'
            Map<String, Object> paymentRequest = (Map<String, Object>) allPRs.get(id);
            paymentRequest.put("attachment", filename);

            // return info about stored file
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("filename", filename);
            resp.put("filePath", dest.toString());

            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            log.error("Error saving uploaded file", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save file", "detail", ex.getMessage()));
        }
    }

    // Health Check
    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
