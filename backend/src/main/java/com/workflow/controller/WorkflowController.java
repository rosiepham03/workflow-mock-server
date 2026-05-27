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

    // ======================================================
    // 1. GET FULL WORKFLOW
    // ======================================================
    @GetMapping("/wfstep")
    public ResponseEntity<?> getFullWorkflow() {
        log.info("[GET] /wfstep - Lấy cấu trúc toàn bộ workflow");
        return ResponseEntity.ok(workflowDataService.getWorkflowData());
    }

    // ======================================================
    // 2. DYNAMIC RETURN OPTIONS
    // GET /returnoption?stepId=...&approverId=...
    // ======================================================
    @GetMapping("/api/workflow/return-options")
    public ResponseEntity<?> getReturnOptions(
            @RequestParam String stepId,
            @RequestParam String approverId) {

        log.info("[BE LOG] Tính toán danh sách Return hợp lệ cho step: {}", stepId);

        WorkflowStepDTO actionStep = workflowDataService.findWorkflowStep(stepId);
        if (actionStep == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Không tìm thấy Step hiện tại"));
        }

        List<Map<String, Object>> options = new ArrayList<>();

        // Luôn có tùy chọn mặc định trả về người tạo
        options.add(Map.of(
                "code", "RETURN_TO_REQUESTER",
                "targetStepId", "",
                "labelVn", "Trả về cho người tạo (Requester)"));

        // Thuật toán quét các bước trước hoàn toàn xử lý ở BE
        for (WorkflowStepDTO step : workflowDataService.getWorkflowData().getWorkflowSteps()) {
            if (step.getSortOrder() < actionStep.getSortOrder()) { // Chỉ lấy các bước nằm phía trước
                if (step.getStepApprovers() != null) {
                    for (StepApproverDTO sa : step.getStepApprovers()) {
                        String approverName = (sa.getApprover() != null) ? sa.getApprover().getFullName()
                                : sa.getApproverId();
                        options.add(Map.of(
                                "code", "RETURN_TO_PREVIOUS_APPROVER",
                                "targetStepId", step.getId(),
                                "targetApproverId", sa.getApproverId(),
                                "labelVn", "Trả về bước: " + step.getLevelName() + " - Người duyệt: " + approverName));
                    }
                }
            }
        }
        return ResponseEntity.ok(options);
    }
    // @GetMapping("/returnoption")
    // public ResponseEntity<?> getReturnOptions(
    // @RequestParam(required = false) String stepId,
    // @RequestParam(required = false) String approverId) {

    // log.info("[GET] /returnoption - Tính toán danh sách Return động");

    // if (stepId == null || approverId == null) {
    // return ResponseEntity.badRequest().body(
    // Map.of("error", "Thiếu tham số bắt buộc stepId và approverId.")
    // );
    // }

    // WorkflowStepDTO actionStep = workflowDataService.findWorkflowStep(stepId);
    // if (actionStep == null) {
    // return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
    // Map.of("error", "Không tìm thấy Step tương ứng.")
    // );
    // }

    // // Mặc định luôn có tùy chọn trả hẳn về ban đầu cho Người tạo (Requester)
    // List<ReturnOptionDTO> options = new ArrayList<>();
    // options.add(new ReturnOptionDTO());
    // options.get(0).setCode("RETURN_TO_REQUESTER");
    // options.get(0).setTargetStepId(null);
    // options.get(0).setLabelVn("Trả về cho người tạo (Requester)");
    // options.get(0).setLabelEn("Return to Requester");

    // // Kiểm tra xem bước của người đang bấm nút có phải là bước ĐANG CẦN XỬ LÝ
    // (READY/IN_PROGRESS) hay không
    // boolean isCurrentApprover = "READY".equals(actionStep.getStatus()) ||
    // "IN_PROGRESS".equals(actionStep.getStatus());

    // // Quét toàn bộ danh sách các bước trong Workflow để lọc ra các option hiển
    // thị lên Popup
    // for (WorkflowStepDTO step :
    // workflowDataService.getWorkflowData().getWorkflowSteps()) {
    // if (step.getStepApprovers() == null) continue;

    // for (StepApproverDTO sa : step.getStepApprovers()) {
    // String saStatus = sa.getStatus() != null ? sa.getStatus().toUpperCase() : "";
    // String approverLabel = (sa.getApprover() != null &&
    // sa.getApprover().getFullName() != null)
    // ? sa.getApprover().getFullName()
    // : (sa.getApprover() != null && sa.getApprover().getEmployeeID() != null)
    // ? sa.getApprover().getEmployeeID()
    // : sa.getApproverId() != null ? sa.getApproverId() : "Unknown";

    // if (isCurrentApprover) {
    // // Kịch bản 1: Current approver bấm Return -> list các approver đã "APPROVED"
    // ở các bước trước
    // if (step.getSortOrder() < actionStep.getSortOrder() &&
    // "APPROVED".equals(saStatus)) {
    // ReturnOptionDTO option = new ReturnOptionDTO();
    // option.setCode("RETURN_TO_PREVIOUS_APPROVER");
    // option.setTargetStepId(step.getId());
    // option.setApproverId(sa.getApproverId());
    // option.setLabelVn("Trả về người duyệt: " + approverLabel);
    // option.setLabelEn("Return to approver: " + approverLabel);
    // options.add(option);
    // }
    // } else {
    // // Kịch bản 2: Previous approver bấm Return
    // boolean isBefore = step.getSortOrder() < actionStep.getSortOrder() &&
    // "APPROVED".equals(saStatus);
    // boolean isCurrentActiveStep = "READY".equals(step.getStatus()) ||
    // "IN_PROGRESS".equals(step.getStatus());

    // if (isBefore || isCurrentActiveStep) {
    // ReturnOptionDTO option = new ReturnOptionDTO();
    // option.setCode(isCurrentActiveStep ? "RETURN_TO_CURRENT_APPROVER" :
    // "RETURN_TO_PREVIOUS_APPROVER");
    // option.setTargetStepId(step.getId());
    // option.setApproverId(sa.getApproverId());
    // String status = saStatus.isEmpty() ? step.getStatus() : saStatus;
    // option.setLabelVn("Trả về người duyệt: " + approverLabel + " (" + status +
    // ")");
    // option.setLabelEn("Return to approver: " + approverLabel + " (" + status +
    // ")");
    // options.add(option);
    // }
    // }
    // }
    // }

    // // Loại bỏ trùng lặp
    // Set<String> seen = new HashSet<>();
    // List<ReturnOptionDTO> unique = new ArrayList<>();
    // for (ReturnOptionDTO opt : options) {
    // String key = opt.getApproverId() != null
    // ? "A:" + opt.getApproverId()
    // : "S:" + opt.getTargetStepId() + ":" + opt.getLabelEn();
    // if (!seen.contains(key)) {
    // seen.add(key);
    // unique.add(opt);
    // }
    // }

    // return ResponseEntity.ok(new ReturnOptionResponse(
    // true,
    // isCurrentApprover ? "CURRENT_APPROVER" : "PREVIOUS_APPROVER",
    // unique
    // ));
    // }

    // ======================================================
    // 3. UPDATE WORKFLOW STEP STATUS & OUTCOMES
    // POST /api/workflow/WorkflowSteps/:id
    // ======================================================
    @PostMapping("/api/workflow/WorkflowSteps/{id}")
    public ResponseEntity<?> updateWorkflowStepStatus(
            @PathVariable String id,
            @RequestBody UpdateWorkflowStepRequest request) {

        log.info("[POST] Update Workflow Step Status -> ID: {}", id);
        log.info("-> Action Outcome: {} | Actor: {}", request.getStatusCode(), request.getActionBy());

        WorkflowStepDTO step = workflowDataService.findWorkflowStep(id);
        if (step == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Workflow Step not found trong hệ thống."));
        }

        StepApproverDTO currentApprover = null;
        if (step.getStepApprovers() != null && !step.getStepApprovers().isEmpty()) {
            if (request.getApproverId() != null) {
                currentApprover = step.getStepApprovers().stream()
                        .filter(sa -> sa.getApproverId().equals(request.getApproverId()))
                        .findFirst()
                        .orElse(null);
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

        WorkflowDTO workflow = workflowDataService.getWorkflowData();

        // State Machine xử lý vòng quanh 5 Outcomes
        switch (outcome) {
            case "APPROVE":
                currentApprover.setStatus("APPROVED");
                boolean allApproved = step.getStepApprovers().stream()
                        .allMatch(sa -> "APPROVED".equals(sa.getStatus()));

                if (step.getIsParallel() == null || !step.getIsParallel() || allApproved) {
                    step.setStatus("COMPLETED");
                    step.setCompleteAt(nowIso);

                    WorkflowStepDTO nextStep = workflow.getWorkflowSteps().stream()
                            .filter(s -> s.getSortOrder() == step.getSortOrder() + 1)
                            .findFirst()
                            .orElse(null);

                    if (nextStep != null) {
                        nextStep.setStatus("READY");
                        if (nextStep.getStepApprovers() != null) {
                            nextStep.getStepApprovers().forEach(sa -> sa.setStatus("READY"));
                        }
                        workflow.setStatus("IN_PROGRESS");
                    } else {
                        workflow.setStatus("APPROVED");
                    }
                } else {
                    step.setStatus("IN_PROGRESS");
                    workflow.setStatus("IN_PROGRESS");
                }
                break;

            case "REJECT":
                currentApprover.setStatus("REJECTED");
                step.setStatus("REJECTED");
                step.setCompleteAt(nowIso);
                workflow.setStatus("REJECTED");

                workflow.getWorkflowSteps().stream()
                        .filter(s -> "PLANNED".equals(s.getStatus()))
                        .forEach(s -> s.setStatus("CANCELLED"));
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
                                // Nếu frontend có truyền targetApproverId, chỉ reset đúng người đó.
                                // Nếu không truyền (trả về cả step), reset toàn bộ approver trong step.
                                if (request.getTargetApproverId() == null ||
                                        sa.getApproverId().equals(request.getTargetApproverId())) {
                                    sa.setStatus("READY");
                                    sa.setCompleteAt(null);
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
                            if (s.getStepApprovers() != null) {
                                s.getStepApprovers().forEach(sa -> sa.setStatus("PLANNED"));
                            }
                        });
                break;

            case "REASSIGN":
                currentApprover.setStatus("REASSIGNED");
                if (request.getTargetId() != null) {
                    currentApprover.setActualApproverId(request.getTargetId());
                    ApproverDTO newApprover = new ApproverDTO();
                    newApprover.setId(request.getTargetId());
                    newApprover.setEmployeeID("E_NEW_MOCK");
                    newApprover.setFullName(
                            request.getActionBy() != null ? request.getActionBy() : "Người Nhận Ủy Quyền Mới");
                    newApprover.setEmail("assigned.handler@vn.bosch.com");
                    currentApprover.setApprover(newApprover);

                    currentApprover.setStatus("READY");
                    currentApprover.setCompleteAt(null);
                    step.setStatus("READY");
                } else {
                    return ResponseEntity.badRequest().body(
                            Map.of("error", "Thiếu tham số targetId chứa ID của User nhận Reassign."));
                }
                break;

            case "ON_HOLD":
                currentApprover.setStatus("ON_HOLD");
                step.setStatus("ON_HOLD");
                workflow.setStatus("ON_HOLD");
                break;

            default:
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Action '" + request.getStatusCode()
                                + "' không hợp lệ. Cho phép: APPROVE, REJECT, RETURN, REASSIGN, ON_HOLD"));
        }

        String prId = workflow.getDocumentId();
        Map<String, Object> allPRs = workflowDataService.getPaymentRequests();
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

    // ======================================================
    // 4. GET SINGLE WORKFLOW STEP
    // ======================================================
    @GetMapping("/api/workflow/WorkflowSteps/{id}")
    public ResponseEntity<?> getWorkflowStep(@PathVariable String id) {
        log.info("[GET] Chi tiết Workflow Step: {}", id);

        WorkflowStepDTO step = workflowDataService.findWorkflowStep(id);
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

    // ======================================================
    // 5. UPDATE PAYMENT REQUEST STATUS (Cập nhật thô trực tiếp)
    // POST /api/pr/update-status
    // ======================================================
    // @PostMapping("/api/pr/update-status")
    // public ResponseEntity<?> updatePRStatus(@RequestBody Map<String, Object>
    // requestBody) {
    // log.info("[POST] /api/pr/update-status");

    // String prId = (String) requestBody.get("pr_id");
    // String status = (String) requestBody.get("status");
    // String comment = (String) requestBody.get("comment");
    // String approverId = (String) requestBody.get("approver_id");

    // log.info("-> PR ID: {} | Trạng thái tổng mới: {}", prId, status);

    // Map<String, Object> allPRs = workflowDataService.getPaymentRequests();
    // if (allPRs == null || !allPRs.containsKey(prId)) {
    // return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error",
    // "Payment Request not found"));
    // }

    // // Ép kiểu Map lồng từ cấu trúc JSON Object của Mock Data
    // Map<String, Object> paymentRequest = (Map<String, Object>) allPRs.get(prId);
    // paymentRequest.put("status", status);

    // // Đọc mảng history_log hiện tại ra để ghi đè log mới
    // List<Map<String, Object>> historyLog = (List<Map<String, Object>>)
    // paymentRequest.computeIfAbsent("history_log", k -> new ArrayList<>());

    // Map<String, Object> logEntry = new HashMap<>();
    // logEntry.put("step", paymentRequest.getOrDefault("current_step",
    // "DIRECT_UPDATE"));
    // logEntry.put("approver", approverId != null ? approverId :
    // "UNKNOWN_APPROVER");
    // logEntry.put("status", status != null ? status.toUpperCase() : "");
    // logEntry.put("comment", comment != null ? comment : "");
    // logEntry.put("timestamp", Instant.now().toString());
    // historyLog.add(logEntry);

    // return ResponseEntity.ok(Map.of(
    // "success", true,
    // "message", "Payment Request updated to '" + status + "'",
    // "current_data", paymentRequest
    // ));
    // }

    // ======================================================
    // 6. GET VALID REASSIGNEES (Lọc User đồng cấp hợp lệ)
    // POST /api/workflow/get-valid-reassignees
    // ======================================================
    @PostMapping("/api/workflow/get-valid-reassignees")
    public ResponseEntity<?> getValidReassignees(@RequestBody Map<String, String> requestBody) {
        String currentApproverId = requestBody.get("current_approver_id");
        String currentRole = requestBody.get("current_role");

        log.info("[POST] /api/workflow/get-valid-reassignees -> Tìm đồng cấp cho: {} với role: {}", currentApproverId,
                currentRole);

        List<UserDTO> allUsers = workflowDataService.getUsers();
        if (allUsers == null) {
            return ResponseEntity.ok(Map.of("success", true, "total", 0, "data", Collections.emptyList()));
        }

        // Lọc theo điều kiện: Active && Cùng Role && Không trùng ID bản thân
        List<UserDTO> validUsers = allUsers.stream()
                .filter(user -> "Active".equalsIgnoreCase(user.getStatus())
                        && Objects.equals(user.getRole(), currentRole)
                        && !Objects.equals(user.getId(), currentApproverId))
                .collect(Collectors.toList());

        log.info("Tìm thấy {} người dùng cùng role {}", validUsers.size(), currentRole);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", validUsers.size(),
                "data", validUsers));
    }

    // ======================================================
    // 7. GET HISTORY STEPS (Lấy lịch sử các bước đã duyệt thành công)
    // GET /api/pr/:id/history-steps
    // ======================================================
    @GetMapping("/api/pr/{id}/history-steps")
    public ResponseEntity<?> getPRHistorySteps(@PathVariable String id) {
        log.info("[GET] /api/pr/{}/history-steps - Lấy danh sách bước đã duyệt để Return", id);

        Map<String, Object> allPRs = workflowDataService.getPaymentRequests();
        if (allPRs == null || !allPRs.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Payment Request not found"));
        }

        Map<String, Object> paymentRequest = (Map<String, Object>) allPRs.get(id);
        List<Map<String, Object>> historyLog = (List<Map<String, Object>>) paymentRequest.get("history_log");

        if (historyLog == null) {
            return ResponseEntity.ok(Map.of("success", true, "data", Collections.emptyList()));
        }

        // Chấp nhận trạng thái APPROVE, APPROVED hoặc COMPLETED giống Node.js
        List<Map<String, Object>> filteredHistory = historyLog.stream()
                .filter(logEntry -> {
                    String status = (String) logEntry.get("status");
                    if (status == null)
                        return false;
                    String statusUpper = status.toUpperCase();
                    return statusUpper.contains("APPROVE") || "COMPLETED".equals(statusUpper);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("success", true, "data", filteredHistory));
    }

    // ======================================================
    // 8. GET PAYMENT REQUEST DETAIL
    // GET /api/pr/:id
    // ======================================================
    @GetMapping("/api/pr/{id}")
    public ResponseEntity<?> getPRDetail(@PathVariable String id) {
        log.info("[GET] Đọc thông tin chi tiết của PR ID: {}", id);

        Map<String, Object> allPRs = workflowDataService.getPaymentRequests();
        if (allPRs == null || !allPRs.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Payment Request not found"));
        }

        return ResponseEntity.ok(allPRs.get(id));
    }

    // ======================================================
    // Health Check
    // ======================================================
    @GetMapping
    public ResponseEntity<String> healthCheck() {
    return ResponseEntity.ok("OK");
    }
}
