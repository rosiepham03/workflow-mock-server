const express = require('express');
const bodyParser = require('body-parser');

const app = express();
// const PORT = 3000;
const PORT = process.env.PORT || 3000;

// Import mock data từ bộ nhớ cache của Node.js
const workflowData = require('./mock-data/workflow.json');
const returnOptionData = require('./mock-data/return-option.json');
const paymentRequests = require('./mock-data/payment-request.json');
const users = require('./mock-data/users.json');

app.use(bodyParser.json());

// Helper tìm kiếm step trong mảng cấu hình workflow gốc
function findWorkflowStep(stepId) {
    if (!workflowData || !workflowData.workflowSteps) return null;
    return workflowData.workflowSteps.find(step => step.ID === stepId);
}

// Thêm API endpoint gốc phục vụ riêng cho SAP Cloud Foundry Health Check
app.get('/', (req, res) => {
    res.status(200).send('OK');
});

// ======================================================
// 1. GET FULL WORKFLOW
// ======================================================
app.get('/wfstep', (req, res) => {
    console.log('\n[GET] /wfstep - Lấy cấu trúc toàn bộ workflow');
    res.status(200).json(workflowData);
});

// ======================================================
// 2. DYNAMIC RETURN OPTIONS (Tính toán động danh sách bước có thể Return)
// GET /returnoption?stepId=...&approverId=...
// ======================================================
app.get('/returnoption', (req, res) => {
    const { stepId, approverId } = req.query;
    console.log(`\n[GET] /returnoption - Tính toán danh sách Return động`);

    if (!stepId || !approverId) {
        return res.status(400).json({ error: "Thiếu tham số bắt buộc stepId và approverId." });
    }

    // Tìm xem step người này đang tương tác nằm ở vị trí nào trong luồng
    const actionStep = findWorkflowStep(stepId);
    if (!actionStep) {
        return res.status(404).json({ error: "Không tìm thấy Step tương ứng." });
    }

    // Mặc định luôn có tùy chọn trả hẳn về ban đầu cho Người tạo (Requester)
    let options = [
        {
            code: "RETURN_TO_REQUESTER",
            targetStepId: null,
            label_vn: "Trả về cho người tạo (Requester)",
            label_en: "Return to Requester"
        }
    ];

    // Kiểm tra xem bước của người đang bấm nút có phải là bước ĐANG CẦN XỬ LÝ (READY/IN_PROGRESS) hay không
    const isCurrentApprover = (actionStep.status === 'READY' || actionStep.status === 'IN_PROGRESS');

    // Quét toàn bộ danh sách các bước trong Workflow để lọc ra các option hiển thị lên Popup
    // Mục tiêu: trả về danh sách "approver" phù hợp theo 2 kịch bản:
    // - Nếu Current Approver bấm Return: hiển thị các approver có status === 'APPROVED' (từ các bước trước)
    // - Nếu Previous Approver bấm Return: hiển thị các approver có status === 'APPROVED' (các bước trước)
    //   và kèm theo approver của bước hiện tại (READY/IN_PROGRESS) để có thể trả về cho Current Approver
    workflowData.workflowSteps.forEach(step => {
        if (!Array.isArray(step.stepApprovers)) return;

        step.stepApprovers.forEach(sa => {
            const saStatus = (sa.status || '').toUpperCase();
            const approverLabel = (sa.approver && (sa.approver.fullName || sa.approver.employeeID)) || sa.approver_id || 'Unknown';

            if (isCurrentApprover) {
                // Kịch bản 1: Current approver bấm Return -> list các approver đã "APPROVED" ở các bước trước
                if (step.sortOrder < actionStep.sortOrder && saStatus === 'APPROVED') {
                    options.push({
                        code: "RETURN_TO_PREVIOUS_APPROVER",
                        targetStepId: step.ID,
                        approverId: sa.approver_id,
                        label_vn: `Trả về người duyệt: ${approverLabel}`,
                        label_en: `Return to approver: ${approverLabel}`
                    });
                }
            } else {
                // Kịch bản 2: Previous approver bấm Return
                // - Hiện approver đã APPROVED ở các bước trước họ
                // - Kèm theo approver(s) của bước hiện tại (READY/IN_PROGRESS) để trả lại cho Current Approver
                const isBefore = (step.sortOrder < actionStep.sortOrder && saStatus === 'APPROVED');
                const isCurrentActiveStep = (step.status === 'READY' || step.status === 'IN_PROGRESS');

                if (isBefore || isCurrentActiveStep) {
                    options.push({
                        code: isCurrentActiveStep ? "RETURN_TO_CURRENT_APPROVER" : "RETURN_TO_PREVIOUS_APPROVER",
                        targetStepId: step.ID,
                        approverId: sa.approver_id,
                        label_vn: `Trả về người duyệt: ${approverLabel} (${saStatus || step.status})`,
                        label_en: `Return to approver: ${approverLabel} (${saStatus || step.status})`
                    });
                }
            }
        });
    });

    // Loại bỏ trùng lặp (theo approverId / step) để UI không hiển thị nhiều lần cùng một người
    const unique = [];
    const seen = new Set();
    options.forEach(opt => {
        const key = opt.approverId ? `A:${opt.approverId}` : `S:${opt.targetStepId}:${opt.label_en}`;
        if (!seen.has(key)) { seen.add(key); unique.push(opt); }
    });
    options = unique;

    return res.status(200).json({
        success: true,
        actorRole: isCurrentApprover ? "CURRENT_APPROVER" : "PREVIOUS_APPROVER",
        options: options
    });
});

// ======================================================
// 3. UPDATE WORKFLOW STEP STATUS & OUTCOMES (Trọng tâm xử lý 5 biến thể)
// POST /api/workflow/WorkflowSteps/:id
// ======================================================
app.post('/api/workflow/WorkflowSteps/:id', (req, res) => {
    const stepId = req.params.id;
    const { status_code, comment, actionBy, approverId, targetId } = req.body;

    console.log(`\n[POST] Update Workflow Step Status -> ID: ${stepId}`);
    console.log(`-> Action Outcome: ${status_code} | Actor: ${actionBy}`);

    const step = findWorkflowStep(stepId);
    if (!step) {
        return res.status(404).json({ error: 'Workflow Step not found trong hệ thống.' });
    }

    // Xác định đối tượng approver cụ thể trong mảng lồng nhau
    let currentApprover = null;
    if (step.stepApprovers && step.stepApprovers.length > 0) {
        if (approverId) {
            currentApprover = step.stepApprovers.find(sa => sa.approver_id === approverId);
        } else {
            currentApprover = step.stepApprovers[0]; // Fallback lấy phần tử đầu tiên
        }
    }

    if (!currentApprover) {
        return res.status(404).json({ error: 'Không tìm thấy thông tin Người duyệt (approverId) trong Step này.' });
    }

    const outcome = status_code ? status_code.toUpperCase() : '';
    const nowIso = new Date().toISOString();

    // Lưu vết log cá nhân trên bản ghi Approver
    currentApprover.comment = comment || null;
    currentApprover.completeAt = nowIso;
    currentApprover.modifiedAt = nowIso;
    currentApprover.modifiedBy = actionBy || 'SYSTEM';

    // State Machine xử lý vòng quanh 5 Outcomes theo thiết kế luồng doanh nghiệp
    switch (outcome) {
        case 'APPROVE':
            currentApprover.status = 'APPROVED';

            // Xử lý kiểm thử bài toán duyệt song song (isParallel)
            const allApproved = step.stepApprovers.every(sa => sa.status === 'APPROVED');
            if (step.isParallel === false || allApproved) {
                step.status = 'COMPLETED';
                step.completeAt = nowIso;

                // Tự động kích hoạt READY cho Step kế tiếp dựa trên cấu trúc thứ tự `sortOrder`
                const nextStep = workflowData.workflowSteps.find(s => s.sortOrder === step.sortOrder + 1);
                if (nextStep) {
                    nextStep.status = 'READY';
                    if (nextStep.stepApprovers) {
                        nextStep.stepApprovers.forEach(sa => sa.status = 'READY');
                    }
                    workflowData.status = 'IN_PROGRESS';
                } else {
                    // Không còn bước nào => Đã cán đích, toàn bộ Workflow hoàn tất thành công
                    workflowData.status = 'APPROVED';
                }
            } else {
                step.status = 'IN_PROGRESS';
                workflowData.status = 'IN_PROGRESS';
            }
            break;

        case 'REJECT':
            currentApprover.status = 'REJECTED';
            step.status = 'REJECTED';
            step.completeAt = nowIso;

            // Từ chối thẳng cánh => Đóng băng kết thúc luôn luồng chạy tổng thể
            workflowData.status = 'REJECTED';

            // Chuyển toàn bộ các bước dự kiến (PLANNED) phía sau thành CANCELLED
            workflowData.workflowSteps.forEach(s => {
                if (s.status === 'PLANNED') s.status = 'CANCELLED';
            });
            break;

        case 'RETURN':
            currentApprover.status = 'RETURNED';
            step.status = 'RETURNED';

            if (targetId) {
                // Return về một step cũ cụ thể đã từng xử lý trước đó
                const targetStep = workflowData.workflowSteps.find(s => s.ID === targetId);
                if (targetStep) {
                    targetStep.status = 'READY';
                    if (targetStep.stepApprovers) {
                        targetStep.stepApprovers.forEach(sa => {
                            sa.status = 'READY';
                            sa.completeAt = null; // Reset trạng thái để thực hiện ký duyệt lại
                        });
                    }
                    workflowData.status = `RETURNED_TO_${targetStep.levelName.toUpperCase().replace(/\s+/g, '_')}`;
                }
            } else {
                // Trả ngược hẳn về cho người tạo khởi tạo (Requester) sửa đổi chứng từ
                workflowData.status = 'RETURNED_TO_REQUESTER';
            }

            // Toàn bộ các bước đứng từ vị trí hiện tại đổ đi sẽ bị đưa ngược về trạng thái chờ PLANNED
            workflowData.workflowSteps.forEach(s => {
                if (s.sortOrder >= step.sortOrder) {
                    s.status = 'PLANNED';
                    if (s.stepApprovers) s.stepApprovers.forEach(sa => sa.status = 'PLANNED');
                }
            });
            break;

        case 'REASSIGN':
            currentApprover.status = 'REASSIGNED';
            if (targetId) {
                // Gán quyền xử lý thực tế sang cho User ID mới nhận bàn giao công việc
                currentApprover.actualApprover_id = targetId;

                // Mapping giả lập ghi đè nhanh thông tin định danh mới hiển thị trực quan lên UI
                currentApprover.approver = {
                    id: targetId,
                    employeeID: "E_NEW_MOCK",
                    fullName: actionBy || "Người Nhận Ủy Quyền Mới",
                    email: "assigned.handler@vn.bosch.com"
                };

                // Trả trạng thái approver này về lại READY để người mới vào có quyền tác động nút bấm
                currentApprover.status = 'READY';
                currentApprover.completeAt = null;
                step.status = 'READY';
            } else {
                return res.status(400).json({ error: 'Thiếu tham số targetId chứa ID của User nhận Reassign.' });
            }
            break;

        case 'ON_HOLD':
            currentApprover.status = 'ON_HOLD';
            step.status = 'ON_HOLD';

            // Đóng băng tạm thời toàn luồng xử lý
            workflowData.status = 'ON_HOLD';
            break;

        default:
            return res.status(400).json({
                error: `Action '${status_code}' không hợp lệ. Cho phép: APPROVE, REJECT, RETURN, REASSIGN, ON_HOLD`
            });
    }

    // Đồng bộ ngược thuộc tính trạng thái tổng và ghi log lịch sử sang phía Payment Request tương ứng nếu có link kết nối
    const prId = workflowData.documentId;
    if (paymentRequests && paymentRequests[prId]) {
        paymentRequests[prId].status = workflowData.status;
        paymentRequests[prId].history_log.push({
            step: step.levelName,
            approver: actionBy || "UNKNOWN",
            status: outcome,
            comment: comment || "",
            timestamp: nowIso
        });
    }

    // Cập nhật cấu trúc audit metadata của Workflow gốc
    step.status = step.status;
    step.modifiedAt = nowIso;
    step.modifiedBy = actionBy || 'SYSTEM';

    return res.status(200).json({
        success: true,
        message: `Workflow Step ${stepId} processed successfully with outcome [${outcome}]`,
        data: {
            ID: step.ID,
            stepStatus: step.status,
            workflowStatus: workflowData.status,
            currentApproverStatus: currentApprover.status
        }
    });
});

// ======================================================
// 4. GET SINGLE WORKFLOW STEP
// ======================================================
app.get('/api/workflow/WorkflowSteps/:id', (req, res) => {
    const stepId = req.params.id;
    console.log(`\n[GET] Chi tiết Workflow Step: ${stepId}`);

    const step = findWorkflowStep(stepId);
    if (!step) {
        return res.status(404).json({ error: 'Workflow Step not found' });
    }

    res.status(200).json({
        ID: step.ID,
        status_code: step.status,
        IsActiveEntity: step.IsActiveEntity,
        stepApprovers: step.stepApprovers
    });
});

// ======================================================
// 5. UPDATE PAYMENT REQUEST STATUS (Dùng cho update trạng thái thô trực tiếp)
// ======================================================
app.post('/api/pr/update-status', (req, res) => {
    const { pr_id, status, comment, approver_id } = req.body;

    console.log(`\n[POST] /api/pr/update-status`);
    console.log(`-> PR ID: ${pr_id} | Trạng thái tổng mới: ${status}`);

    const paymentRequest = paymentRequests[pr_id];
    if (!paymentRequest) {
        return res.status(404).json({ error: "Payment Request not found" });
    }

    paymentRequest.status = status;
    paymentRequest.history_log.push({
        step: paymentRequest.current_step || "DIRECT_UPDATE",
        approver: approver_id || "UNKNOWN_APPROVER",
        status: status.toUpperCase(),
        comment: comment || "",
        timestamp: new Date().toISOString()
    });

    return res.status(200).json({
        success: true,
        message: `Payment Request updated to '${status}'`,
        current_data: paymentRequest
    });
});

// ======================================================
// 6. GET VALID REASSIGNEES (Lọc User đồng cấp hợp lệ phục vụ Reassign)
// ======================================================
app.post('/api/workflow/get-valid-reassignees', (req, res) => {
    const { current_approver_id, current_role } = req.body;

    console.log(`\n[POST] /api/workflow/get-valid-reassignees -> Đang tìm đồng cấp cho: ${current_approver_id}`);

    const validUsers = users.filter(user =>
        user.status === 'Active' &&
        user.role === current_role &&
        user.id !== current_approver_id
    );

    return res.status(200).json({
        success: true,
        total: validUsers.length,
        data: validUsers
    });
});

// ======================================================
// 7. GET HISTORY STEPS (Lấy lịch sử các bước đã từng ký duyệt xong thành công)
// ======================================================
app.get('/api/pr/:id/history-steps', (req, res) => {
    const prId = req.params.id;
    console.log(`\n[GET] /api/pr/${prId}/history-steps - Lấy danh sách bước đã duyệt để thực hiện Return`);

    const paymentRequest = paymentRequests[prId];
    if (!paymentRequest) {
        return res.status(404).json({ error: "Payment Request not found" });
    }

    // Sửa điều kiện: Chấp nhận cả trạng thái "APPROVE", "APPROVED" hoặc "COMPLETED"
    const historySteps = paymentRequest.history_log.filter(log => {
        if (!log.status) return false;
        const statusUpper = log.status.toUpperCase();
        return statusUpper.includes("APPROVE") || statusUpper === "COMPLETED";
    });

    return res.status(200).json({
        success: true,
        data: historySteps
    });
});

// ======================================================
// 8. GET PAYMENT REQUEST DETAIL
// ======================================================
app.get('/api/pr/:id', (req, res) => {
    const prId = req.params.id;
    console.log(`\n[GET] Đọc thông tin chi tiết của PR ID: ${prId}`);

    const paymentRequest = paymentRequests[prId];
    if (!paymentRequest) {
        return res.status(404).json({ error: "Payment Request not found" });
    }

    return res.status(200).json(paymentRequest);
});

// ======================================================
// SERVER START
// ======================================================
app.listen(PORT, () => {
    console.log('=======================================================');
    console.log(`WORKFLOW MOCK SERVER IS READY FOR LOCAL TESTING`);
    console.log(`Listening on Port: ${PORT}`);
    console.log('=======================================================');
});