sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/ui/model/json/JSONModel",
    "sap/m/MessageBox",
    "sap/m/MessageToast"
], function (Controller, JSONModel, MessageBox, MessageToast) {
    "use strict";

    return Controller.extend("com.workflow.ui5.controller.MainController", {

        fullWorkflowData: null,
        pendingAction: null,

        onInit: function () {
            // Tự động load workflow sau 500ms
            setTimeout(() => {
                this.onLoadWorkflow();
            }, 500);
        },

        // ======================================================
        // Helper Methods
        // ======================================================

        showLoading: function (bShow) {
            const oDialog = this.getView().byId("loadingDialog");
            if (bShow) {
                oDialog.open();
            } else {
                oDialog.close();
            }
        },

        updateConsole: function (status, data) {
            const oResponseModel = this.getView().getModel("response");
            oResponseModel.setProperty("/status", status);
            oResponseModel.setProperty("/data", JSON.stringify(data, null, 2));
        },

        clearConsole: function () {
            const oResponseModel = this.getView().getModel("response");
            oResponseModel.setProperty("/status", "N/A");
            oResponseModel.setProperty("/data", "// Kết quả đã xóa.");
        },

        // ======================================================
        // API Calls
        // ======================================================

        callApi: async function (endpoint, method, body = null, showLoading = true) {
            const oConfigModel = this.getView().getModel("config");
            const baseUrl = oConfigModel.getProperty("/baseUrl");
            const url = baseUrl + endpoint;

            if (showLoading) this.showLoading(true);

            const options = {
                method: method,
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            };

            if (body) {
                options.body = JSON.stringify(body);
            }

            try {
                const response = await fetch(url, options);
                let responseData;
                try {
                    responseData = await response.json();
                } catch (e) {
                    responseData = { text: await response.text() };
                }

                this.updateConsole(response.status, responseData);

                if (showLoading) this.showLoading(false);
                return responseData;
            } catch (error) {
                this.updateConsole('ERROR', {
                    message: "Không thể kết nối đến API. Hãy kiểm tra server và CORS.",
                    error: error.message
                });
                if (showLoading) this.showLoading(false);
                MessageBox.error(`Không thể kết nối đến API: ${error.message}`);
                return null;
            }
        },

        // ======================================================
        // Event Handlers
        // ======================================================

        onBaseUrlChange: function (oEvent) {
            const oConfigModel = this.getView().getModel("config");
            oConfigModel.setProperty("/baseUrl", oEvent.getParameter("value"));
        },

        onTestConnection: async function () {
            const result = await this.callApi("/wfstep", "GET");
            if (result && result.workflowSteps) {
                MessageToast.show("✅ Kết nối thành công! Workflow có " + result.workflowSteps.length + " steps.");
            } else if (result) {
                MessageToast.show("⚠️ Kết nối thành công nhưng cấu trúc dữ liệu không như mong đợi.");
            }
        },

        onLoadWorkflow: async function () {
            await this.fetchAndPopulateWorkflow();
        },

        onFetchWorkflow: async function () {
            await this.callApi("/wfstep", "GET");
        },

        onFetchPaymentRequest: async function () {
            await this.callApi("/api/pr/550e8400-e29b-41d4-a716-446655440003", "GET");
        },

        onFetchHistorySteps: async function () {
            await this.callApi("/api/pr/550e8400-e29b-41d4-a716-446655440003/history-steps", "GET");
        },

        onClearConsole: function () {
            this.clearConsole();
        },

        onActionChange: function (oEvent) {
            const action = oEvent.getParameter("selectedItem").getKey();

            if (action === "REJECT") {
                MessageBox.warning(
                    "⚠️ CẢNH BÁO: Hành động REJECT sẽ KẾT THÚC toàn bộ workflow và không thể khôi phục. Bạn có chắc chắn muốn từ chối?",
                    {
                        actions: [MessageBox.Action.OK, MessageBox.Action.CANCEL],
                        onClose: (sAction) => {
                            if (sAction !== MessageBox.Action.OK) {
                                const oSelect = this.getView().byId("smStatusCode");
                                const oModel = this.getView().getModel("workflow");
                                if (oSelect) oModel.setProperty("/statusCode", "APPROVE");
                            }
                        }
                    }
                );
            }
        },

        onApproverChange: function (oEvent) {
            const oWorkflowModel = this.getView().getModel("workflow");
            const selectedKey = oEvent.getParameter("selectedItem").getKey();
            const approvers = oWorkflowModel.getProperty("/approvers");
            const selectedApprover = approvers.find(a => a.id === selectedKey);
            oWorkflowModel.setProperty("/selectedApprover", selectedApprover);
        },

        onSubmitAction: async function () {
            const oWorkflowModel = this.getView().getModel("workflow");
            const stepId = oWorkflowModel.getProperty("/currentStep/ID");
            const statusCode = oWorkflowModel.getProperty("/statusCode");
            const approverId = oWorkflowModel.getProperty("/selectedApprover/id");
            const actionBy = oWorkflowModel.getProperty("/selectedApprover/name");
            const comment = oWorkflowModel.getProperty("/comment");

            if (!stepId) {
                MessageBox.warning("Không xác định được step hiện tại.");
                return;
            }

            if (!approverId) {
                MessageBox.warning("Vui lòng chọn Approver từ dropdown trước khi gửi lệnh.");
                return;
            }

            this.pendingAction = {
                stepId: stepId,
                statusCode: statusCode,
                comment: comment,
                approverId: approverId,
                actionBy: actionBy,
                targetData: null
            };

            if (statusCode === "RETURN") {
                await this.showReturnModal(stepId);
            } else if (statusCode === "REASSIGN") {
                await this.showReassignModal();
            } else {
                await this.sendStateMachineAction(this.pendingAction);
            }
        },

        // ======================================================
        // Workflow Logic
        // ======================================================

        fetchAndPopulateWorkflow: async function () {
            const oWorkflowModel = this.getView().getModel("workflow");
            oWorkflowModel.setProperty("/loading", true);

            try {
                const data = await this.callApi("/wfstep", "GET");
                if (!data) return;

                this.fullWorkflowData = data;
                let steps = data.workflowSteps || [];

                if (steps.length === 0) {
                    MessageBox.error("Không tìm thấy Step nào trong Workflow");
                    return;
                }

                // Sắp xếp steps theo sortOrder
                steps.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));

                // Tìm step hiện tại (status = READY hoặc IN_PROGRESS)
                let currentStep = steps.find(step =>
                    step.status === "READY" || step.status === "IN_PROGRESS"
                );

                if (!currentStep) {
                    currentStep = steps.find(step =>
                        step.status !== "COMPLETED" && step.status !== "REJECTED" && step.status !== "CANCELLED"
                    ) || steps[0];
                }

                // Cập nhật model
                oWorkflowModel.setProperty("/workflowData", data);
                oWorkflowModel.setProperty("/steps", steps);
                oWorkflowModel.setProperty("/currentStep", currentStep);
                oWorkflowModel.setProperty("/loading", false);

                // Cập nhật tiến trình
                const completedCount = steps.filter(s => s.status === "COMPLETED").length;
                const previousSteps = steps.filter(s => s.sortOrder < currentStep.sortOrder);
                const nextSteps = steps.filter(s => s.sortOrder > currentStep.sortOrder);

                let progressHtml = `<strong>📊 Tiến trình Workflow:</strong><br>
                    ✅ Đã duyệt: ${completedCount}/${steps.length} bước<br>
                    🔄 Đang ở: ${currentStep.levelName} (Bước ${currentStep.sortOrder}/${steps.length})<br>`;

                if (previousSteps.length > 0) {
                    progressHtml += `⬅️ Các bước trước: ${previousSteps.map(s => s.levelName).join(" → ")}<br>`;
                }
                if (nextSteps.length > 0) {
                    progressHtml += `➡️ Các bước tiếp theo: ${nextSteps.map(s => s.levelName).join(" → ")}`;
                }

                oWorkflowModel.setProperty("/workflowProgress", progressHtml);

                // Populate approvers
                await this.populateApprovers(currentStep.ID);

            } catch (error) {
                oWorkflowModel.setProperty("/loading", false);
                MessageBox.error(`Lỗi tải workflow: ${error.message}`);
            }
        },

        populateApprovers: async function (stepId) {
            const steps = this.fullWorkflowData?.workflowSteps || [];
            const step = steps.find(s => s.ID === stepId);

            if (!step || !step.stepApprovers || step.stepApprovers.length === 0) {
                const oWorkflowModel = this.getView().getModel("workflow");
                oWorkflowModel.setProperty("/approvers", []);
                oWorkflowModel.setProperty("/selectedApprover", null);
                return;
            }

            const approvers = step.stepApprovers.map(approver => {
                const isProxy = approver.proxyApprover_id && approver.proxyApprover?.id;
                const approverInfo = isProxy ? approver.proxyApprover : approver.approver;
                const proxyLabel = isProxy ? " (Ủy quyền)" : "";
                return {
                    id: isProxy ? approver.proxyApprover.id : approver.approver_id,
                    name: (approverInfo?.fullName || "Unknown") + proxyLabel,
                    email: approverInfo?.email || "",
                    isProxy: isProxy
                };
            });

            const oWorkflowModel = this.getView().getModel("workflow");
            oWorkflowModel.setProperty("/approvers", approvers);
            oWorkflowModel.setProperty("/selectedApprover", approvers[0]);
        },

        getPreviousSteps: function (currentStepId) {
            const steps = this.fullWorkflowData?.workflowSteps || [];
            const currentStep = steps.find(s => s.ID === currentStepId);
            if (!currentStep) return [];

            return steps.filter(s =>
                s.sortOrder < currentStep.sortOrder &&
                (s.status === "COMPLETED" || s.status === "APPROVED")
            ).sort((a, b) => b.sortOrder - a.sortOrder);
        },

        getCurrentRole: function () {
            const stepName = this.fullWorkflowData?.currentStep?.levelName || "";
            if (stepName.includes("Supervisor")) return "Supervisor";
            if (stepName.includes("Section Manager")) return "Section Manager";
            if (stepName.includes("Department Manager")) return "Department Manager";
            if (stepName.includes("Division Manager")) return "Division Manager";
            if (stepName.includes("Accountant")) return "Accountant";
            return stepName;
        },

        // ======================================================
        // Modal Handlers
        // ======================================================

        showReturnModal: async function (currentStepId) {
            const previousSteps = this.getPreviousSteps(currentStepId);
            const oDialog = this.getView().byId("actionDialog");

            const oRadioGroup = this.getView().byId("radioGroup");
            const oTargetSelect = this.getView().byId("targetSelect");
            const oMessage = this.getView().byId("dialogMessage");

            // Clear previous content
            oRadioGroup.destroyItems();
            oRadioGroup.setVisible(true);
            oTargetSelect.setVisible(false);

            // Add Requester option
            oRadioGroup.addItem(new sap.ui.core.Item({
                key: "requester",
                text: "📋 Trả về cho người tạo (Requester) - Đặt lại workflow"
            }));

            // Add previous steps with approvers
            previousSteps.forEach(step => {
                const approvers = step.stepApprovers || [];
                approvers.forEach(approver => {
                    const isProxy = approver.proxyApprover_id && approver.proxyApprover?.id;
                    const approverInfo = isProxy ? approver.proxyApprover : approver.approver;
                    const approverName = approverInfo?.fullName || "Unknown";
                    const proxyLabel = isProxy ? " (Ủy quyền)" : "";
                    const status = approver.status || "N/A";

                    oRadioGroup.addItem(new sap.ui.core.Item({
                        key: `approver_${step.ID}_${approverName}`,
                        text: `${step.levelName} → ${approverName}${proxyLabel}`,
                        additionalData: {
                            stepId: step.ID,
                            approverId: isProxy ? approver.proxyApprover.id : approver.approver_id,
                            approverName: approverName,
                            status: status
                        }
                    }));
                });
            });

            if (previousSteps.length === 0) {
                oMessage.setText("⚠️ Không có bước duyệt nào phía trước. Bạn có thể trả về cho người tạo.");
            } else {
                oMessage.setText("📤 Chọn bước và người duyệt cần trả về:");
            }

            oDialog.open();
        },

        showReassignModal: async function () {
            const oDialog = this.getView().byId("actionDialog");
            const oRadioGroup = this.getView().byId("radioGroup");
            const oTargetSelect = this.getView().byId("targetSelect");
            const oMessage = this.getView().byId("dialogMessage");

            oRadioGroup.setVisible(false);
            oTargetSelect.setVisible(true);
            oTargetSelect.removeAllItems();

            oMessage.setText("Đang tải danh sách người dùng...");
            oDialog.open();

            try {
                const oConfigModel = this.getView().getModel("config");
                const baseUrl = oConfigModel.getProperty("/baseUrl");
                const currentRole = this.getCurrentRole();
                const currentApproverId = this.getView().getModel("workflow").getProperty("/selectedApprover/id");

                const response = await fetch(`${baseUrl}/api/workflow/get-valid-reassignees`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        current_approver_id: currentApproverId,
                        current_role: currentRole
                    })
                });

                const result = await response.json();
                const users = result.data || [];

                if (users.length === 0) {
                    oMessage.setText(`⚠️ Không tìm thấy người dùng nào cùng cấp (${currentRole}) để ủy quyền.`);
                } else {
                    oMessage.setText(`🔄 Chọn người sẽ ủy quyền duyệt (cùng cấp - ${currentRole}):`);
                    oTargetSelect.addItem(new sap.ui.core.Item({ key: "", text: "-- Chọn người nhận ủy quyền --" }));
                    users.forEach(user => {
                        oTargetSelect.addItem(new sap.ui.core.Item({
                            key: user.id,
                            text: `${user.fullName} (${user.employeeID}) - ${user.department || user.role}`
                        }));
                    });
                    oTargetSelect.setSelectedKey("");
                }
            } catch (error) {
                oMessage.setText(`❌ Lỗi tải danh sách: ${error.message}`);
            }
        },

        onDialogConfirm: function () {
            const oDialog = this.getView().byId("actionDialog");
            const oRadioGroup = this.getView().byId("radioGroup");
            const oTargetSelect = this.getView().byId("targetSelect");

            if (this.pendingAction.statusCode === "RETURN") {
                const selected = oRadioGroup.getSelectedItem();
                if (!selected) {
                    MessageBox.warning("Vui lòng chọn bước và người duyệt cần trả về.");
                    return;
                }

                const key = selected.getKey();
                const additionalData = selected.getAdditionalData();

                if (key === "requester") {
                    this.pendingAction.targetData = { type: "REQUESTER" };
                } else if (additionalData) {
                    this.pendingAction.targetData = {
                        type: "APPROVER",
                        stepId: additionalData.stepId,
                        approverId: additionalData.approverId
                    };
                } else {
                    MessageBox.warning("Dữ liệu không hợp lệ.");
                    return;
                }
            } else if (this.pendingAction.statusCode === "REASSIGN") {
                const selectedKey = oTargetSelect.getSelectedKey();
                if (!selectedKey || selectedKey === "") {
                    MessageBox.warning("Vui lòng chọn người nhận ủy quyền.");
                    return;
                }
                this.pendingAction.targetData = { type: "USER", userId: selectedKey };
            }

            oDialog.close();
            this.sendStateMachineAction(this.pendingAction);
        },

        onDialogClose: function () {
            this.getView().byId("actionDialog").close();
            this.pendingAction = null;
        },

        sendStateMachineAction: async function (action) {
            // Định nghĩa payload chuẩn khít với các Annotation @JsonProperty trong Spring Boot
            const payload = {
                status_code: action.statusCode, // "APPROVE", "REJECT", "RETURN", "REASSIGN"
                approverId: action.approverId,
                comment: action.comment,
                actionBy: action.actionBy,
                targetId: null // Mặc định là null
            };

            // Xử lý gán targetId nếu là hành động quay lại (RETURN) hoặc ủy quyền (REASSIGN)
            if (action.statusCode === "RETURN") {
                if (action.targetData?.type === "REQUESTER") {
                    payload.targetId = null;
                } else if (action.targetData?.type === "APPROVER") {
                    payload.targetId = action.targetData.stepId;
                    // Nếu backend cần nhận thêm targetApproverId, class Java hiện tại chưa thấy có trường này, 
                    // tạm thời giữ cấu trúc tương thích với UpdateWorkflowStepRequest.java của bạn
                }
            } else if (action.statusCode === "REASSIGN") {
                payload.targetId = action.targetData.userId; // Gán ID người nhận ủy quyền vào targetId theo đúng mapping Java
            }

            console.log("📤 Sending payload to Spring Boot:", payload);

            // Thực hiện gọi API thông qua Proxy trung chuyển đã cấu hình tại ui5.yaml
            const result = await this.callApi(`/api/workflow/WorkflowSteps/${action.stepId}`, "POST", payload);

            if (result && result.success) {
                MessageToast.show(`✅ ${action.statusCode} thành công!`);
                setTimeout(() => {
                    this.fetchAndPopulateWorkflow();
                }, 500);
            }
        }
    });
});