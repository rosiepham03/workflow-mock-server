sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/ui/model/json/JSONModel",
    "sap/m/MessageBox",
    "sap/m/MessageToast"
], function(Controller, JSONModel, MessageBox, MessageToast) {
    "use strict";

    return Controller.extend("com.workflow.ui5.controller.MainController", {

        fullWorkflowData: null,
        pendingAction: null,

        onInit: function() {
            // Initialize models
            var oWorkflowModel = new JSONModel({
                requestId: "48865 · Advanced: 589946637",
                classification: "Cash/Bank/Deduct",
                amount: "20.000.000 VND",
                dueDate: "June 23, 2024 (Contract deadline)",
                paymentDate: "August 06, 2024",
                workflowStatus: "IN_PROGRESS",
                statusState: "Success",
                currentStep: {},
                currentApprover: "",
                progressPercent: 0,
                canApprove: true,
                canReject: true,
                canReturn: true,
                canReassign: true,
                canHold: true,
                comment: "",
                steps: [],
                workflowData: null
            });
            this.getView().setModel(oWorkflowModel, "workflow");

            var oConsoleModel = new JSONModel({
                statusState: "Success",
                statusText: "Ready",
                output: "// Ready to process workflow actions\n// Backend API: Spring Boot WorkflowController\n// Available outcomes: APPROVE, REJECT, RETURN, REASSIGN, ON_HOLD\n"
            });
            this.getView().setModel(oConsoleModel, "console");

            // Load workflow data
            this.loadWorkflowData();
        },

        // ======================================================
        // API Calls to Spring Boot Backend
        // ======================================================

        callApi: function(endpoint, method, body) {
            var that = this;
            var sUrl = endpoint.startsWith("/api") ? endpoint : "/api" + endpoint;
            
            this.showLoading(true);
            
            return new Promise(function(resolve, reject) {
                $.ajax({
                    url: sUrl,
                    method: method,
                    contentType: "application/json",
                    dataType: "json",
                    data: body ? JSON.stringify(body) : undefined,
                    success: function(data, textStatus, xhr) {
                        that.updateConsole(xhr.status, data);
                        that.showLoading(false);
                        resolve(data);
                    },
                    error: function(xhr, status, error) {
                        var errorMsg = error;
                        try {
                            var response = JSON.parse(xhr.responseText);
                            errorMsg = response.error || response.message || error;
                        } catch(e) {}
                        
                        that.updateConsole(xhr.status, { error: errorMsg, details: xhr.responseText });
                        that.showLoading(false);
                        reject(new Error(errorMsg));
                    }
                });
            });
        },

        updateConsole: function(status, data) {
            var oConsoleModel = this.getView().getModel("console");
            var sStatusText = status + "";
            var sStatusState = "Success";
            
            if (status >= 400) {
                sStatusState = "Error";
            } else if (status >= 300) {
                sStatusState = "Warning";
            }
            
            oConsoleModel.setProperty("/statusText", sStatusText);
            oConsoleModel.setProperty("/statusState", sStatusState);
            
            var currentOutput = oConsoleModel.getProperty("/output");
            var timestamp = new Date().toLocaleTimeString();
            var newOutput = currentOutput + "\n\n[" + timestamp + "] Response (" + status + "):\n" + JSON.stringify(data, null, 2);
            
            // Keep last 50KB only
            if (newOutput.length > 50000) {
                newOutput = newOutput.substring(newOutput.length - 50000);
            }
            
            oConsoleModel.setProperty("/output", newOutput);
        },

        clearConsole: function() {
            var oConsoleModel = this.getView().getModel("console");
            oConsoleModel.setProperty("/output", "// Console cleared at " + new Date().toLocaleTimeString() + "\n");
            oConsoleModel.setProperty("/statusText", "Cleared");
            MessageToast.show("Console cleared");
        },

        showLoading: function(bShow) {
            var oDialog = this.getView().byId("loadingDialog");
            if (oDialog) {
                if (bShow) oDialog.open();
                else oDialog.close();
            }
        },

        // ======================================================
        // Load Workflow from Backend
        // ======================================================

        loadWorkflowData: function() {
            var that = this;
            
            this.addConsoleLog("Fetching workflow from /api/wfstep...", "info");
            
            this.callApi("/api/wfstep", "GET")
                .then(function(data) {
                    that.fullWorkflowData = data;
                    that.processWorkflowData(data);
                    that.addConsoleLog("✅ Workflow loaded successfully", "success");
                })
                .catch(function(error) {
                    that.addConsoleLog("❌ Failed to load workflow: " + error.message, "error");
                    MessageBox.error("Failed to load workflow: " + error.message);
                });
        },

        processWorkflowData: function(data) {
            var oWorkflowModel = this.getView().getModel("workflow");
            var aSteps = data.workflowSteps || [];
            
            // Sort steps by sortOrder
            aSteps.sort(function(a, b) {
                return (a.sortOrder || 0) - (b.sortOrder || 0);
            });
            
            // Find current step (READY or IN_PROGRESS)
            var currentStep = aSteps.find(function(step) {
                return step.status === "READY" || step.status === "IN_PROGRESS";
            });
            
            if (!currentStep && aSteps.length > 0) {
                currentStep = aSteps[0];
            }
            
            // Calculate progress
            var completedCount = aSteps.filter(function(step) {
                return step.status === "COMPLETED" || step.status === "APPROVED";
            }).length;
            
            var progressPercent = aSteps.length > 0 ? (completedCount / aSteps.length) * 100 : 0;
            
            // Get current approver name
            var currentApprover = "";
            if (currentStep && currentStep.stepApprovers && currentStep.stepApprovers.length > 0) {
                var approver = currentStep.stepApprovers[0];
                if (approver.approver) {
                    currentApprover = approver.approver.fullName || approver.approver.employeeID || "Unknown";
                } else {
                    currentApprover = approver.approverId || "Unknown";
                }
            }
            
            // Update workflow status display
            var workflowStatus = data.status || "IN_PROGRESS";
            var statusState = "Success";
            if (workflowStatus === "REJECTED") statusState = "Error";
            else if (workflowStatus === "ON_HOLD") statusState = "Warning";
            
            oWorkflowModel.setProperty("/workflowData", data);
            oWorkflowModel.setProperty("/steps", aSteps);
            oWorkflowModel.setProperty("/currentStep", currentStep);
            oWorkflowModel.setProperty("/currentApprover", currentApprover);
            oWorkflowModel.setProperty("/progressPercent", progressPercent);
            oWorkflowModel.setProperty("/workflowStatus", workflowStatus);
            oWorkflowModel.setProperty("/statusState", statusState);
            
            // Update step visualization
            this.updateStepVisualization(aSteps, currentStep);
        },

        updateStepVisualization: function(aSteps, currentStep) {
            var oContainer = this.getView().byId("workflowStepsContainer");
            if (!oContainer) return;
            
            oContainer.removeAllItems();
            
            var that = this;
            aSteps.forEach(function(step, index) {
                var isCompleted = step.status === "COMPLETED" || step.status === "APPROVED";
                var isCurrent = currentStep && step.ID === currentStep.ID;
                var isRejected = step.status === "REJECTED";
                var isOnHold = step.status === "ON_HOLD";
                
                var sIcon = "sap-icon://circle";
                var sColor = "Default";
                
                if (isCompleted) {
                    sIcon = "sap-icon://accept";
                    sColor = "Success";
                } else if (isCurrent) {
                    sIcon = "sap-icon://action";
                    sColor = "Emphasized";
                } else if (isRejected) {
                    sIcon = "sap-icon://decline";
                    sColor = "Error";
                } else if (isOnHold) {
                    sIcon = "sap-icon://pause";
                    sColor = "Warning";
                }
                
                var oStepContainer = new sap.m.VBox({
                    class: "workflowStep",
                    width: "140px",
                    alignItems: "Center",
                    justifyContent: "Center",
                    style: "margin: 8px; padding: 12px; border: 1px solid #e0e0e0; border-radius: 8px; background-color: " + (isCurrent ? "#f0f7ff" : "#ffffff")
                });
                
                var oIcon = new sap.m.Icon({
                    src: sIcon,
                    size: "2rem",
                    color: sColor,
                    class: "sapUiSmallMarginBottom"
                });
                
                var oStepName = new sap.m.Label({
                    text: step.levelName || "Step " + (index + 1),
                    textAlign: "Center",
                    width: "100%",
                    design: isCurrent ? "Bold" : "Standard",
                    class: "sapUiSmallMarginBottom"
                });
                
                var oStepStatus = new sap.m.ObjectStatus({
                    state: that.getStatusState(step.status),
                    text: step.status || "PLANNED"
                });
                
                oStepContainer.addItem(oIcon);
                oStepContainer.addItem(oStepName);
                oStepContainer.addItem(oStepStatus);
                
                oContainer.addItem(oStepContainer);
            });
        },

        getStatusState: function(status) {
            if (!status) return "None";
            var sStatus = status.toUpperCase();
            if (sStatus === "COMPLETED" || sStatus === "APPROVED") return "Success";
            if (sStatus === "REJECTED") return "Error";
            if (sStatus === "ON_HOLD") return "Warning";
            if (sStatus === "READY" || sStatus === "IN_PROGRESS") return "Emphasized";
            return "None";
        },

        addConsoleLog: function(message, type) {
            var oConsoleModel = this.getView().getModel("console");
            var currentOutput = oConsoleModel.getProperty("/output");
            var timestamp = new Date().toLocaleTimeString();
            var prefix = type === "error" ? "❌" : (type === "success" ? "✅" : "ℹ️");
            var newOutput = currentOutput + "\n[" + timestamp + "] " + prefix + " " + message;
            
            if (newOutput.length > 50000) {
                newOutput = newOutput.substring(newOutput.length - 50000);
            }
            
            oConsoleModel.setProperty("/output", newOutput);
        },

        // ======================================================
        // Action Handlers
        // ======================================================

        onApprove: function() {
            this.submitAction("APPROVE");
        },

        onReject: function() {
            var that = this;
            MessageBox.confirm("Are you sure you want to REJECT this payment request?", {
                title: "Confirm Rejection",
                actions: [MessageBox.Action.YES, MessageBox.Action.NO],
                onClose: function(sAction) {
                    if (sAction === MessageBox.Action.YES) {
                        that.submitAction("REJECT");
                    }
                }
            });
        },

        onReturn: function() {
            this.showReturnDialog();
        },

        onReassign: function() {
            this.showReassignDialog();
        },

        onOnHold: function() {
            this.submitAction("ON_HOLD");
        },

        onRefresh: function() {
            this.loadWorkflowData();
            MessageToast.show("Refreshing workflow data...");
        },

        // ======================================================
        // Submit Action to Backend
        // ======================================================

        submitAction: function(sOutcome, sTargetId, sCustomComment) {
            var that = this;
            var oWorkflowModel = this.getView().getModel("workflow");
            var currentStep = oWorkflowModel.getProperty("/currentStep");
            
            if (!currentStep || !currentStep.ID) {
                MessageBox.error("Cannot determine current workflow step");
                return;
            }
            
            var sComment = sCustomComment || oWorkflowModel.getProperty("/comment") || "";
            var sApproverId = "";
            
            // Get current approver ID
            if (currentStep.stepApprovers && currentStep.stepApprovers.length > 0) {
                var approver = currentStep.stepApprovers[0];
                sApproverId = approver.approver_id || (approver.approver ? approver.approver.id : "");
            }
            
            var payload = {
                status_code: sOutcome,
                approverId: sApproverId,
                comment: sComment,
                actionBy: "UI5_User",
                targetId: sTargetId || null
            };
            
            this.addConsoleLog("Submitting " + sOutcome + " action to /api/workflow/WorkflowSteps/" + currentStep.ID, "info");
            this.addConsoleLog("Payload: " + JSON.stringify(payload), "info");
            
            this.callApi("/api/workflow/WorkflowSteps/" + currentStep.ID, "POST", payload)
                .then(function(result) {
                    that.addConsoleLog(sOutcome + " action completed successfully", "success");
                    MessageToast.show(sOutcome + " completed successfully!");
                    
                    // Clear comment after successful action
                    oWorkflowModel.setProperty("/comment", "");
                    
                    // Reload workflow data
                    setTimeout(function() {
                        that.loadWorkflowData();
                    }, 500);
                })
                .catch(function(error) {
                    that.addConsoleLog(sOutcome + " action failed: " + error.message, "error");
                    MessageBox.error("Action failed: " + error.message);
                });
        },

        // ======================================================
        // Return Dialog
        // ======================================================

        showReturnDialog: function() {
            var that = this;
            var oWorkflowModel = this.getView().getModel("workflow");
            var aSteps = oWorkflowModel.getProperty("/steps");
            var currentStep = oWorkflowModel.getProperty("/currentStep");
            
            var oDialog = this.getView().byId("returnDialog");
            var oSelect = this.getView().byId("returnStepSelect");
            
            oSelect.removeAllItems();
            
            // Add requester option
            oSelect.addItem(new sap.ui.core.ListItem({
                key: "REQUESTER",
                text: "📋 Return to Requester (Reset workflow)"
            }));
            
            // Add previous steps
            aSteps.forEach(function(step) {
                if (step.sortOrder < currentStep.sortOrder && 
                    (step.status === "COMPLETED" || step.status === "APPROVED")) {
                    oSelect.addItem(new sap.ui.core.ListItem({
                        key: step.ID,
                        text: "⬅️ " + step.levelName + " - Already approved"
                    }));
                }
            });
            
            if (oSelect.getItems().length === 1) {
                MessageBox.warning("No previous steps available to return to");
                return;
            }
            
            oSelect.setSelectedKey("");
            this.getView().byId("returnComment").setValue("");
            oDialog.open();
        },

        onConfirmReturn: function() {
            var oSelect = this.getView().byId("returnStepSelect");
            var sTargetId = oSelect.getSelectedKey();
            var sComment = this.getView().byId("returnComment").getValue();
            
            if (!sTargetId) {
                MessageBox.warning("Please select a return destination");
                return;
            }
            
            var sTarget = sTargetId === "REQUESTER" ? null : sTargetId;
            this.submitAction("RETURN", sTarget, sComment);
            
            this.getView().byId("returnDialog").close();
        },

        onCloseReturnDialog: function() {
            this.getView().byId("returnDialog").close();
        },

        // ======================================================
        // Reassign Dialog
        // ======================================================

        showReassignDialog: function() {
            var that = this;
            var oDialog = this.getView().byId("reassignDialog");
            var oSelect = this.getView().byId("reassignSelect");
            
            oSelect.removeAllItems();
            oSelect.setSelectedKey("");
            this.getView().byId("reassignComment").setValue("");
            
            // Get current step and role
            var oWorkflowModel = this.getView().getModel("workflow");
            var currentStep = oWorkflowModel.getProperty("/currentStep");
            var sCurrentRole = this.getCurrentRole(currentStep);
            var sCurrentApproverId = "";
            
            if (currentStep.stepApprovers && currentStep.stepApprovers.length > 0) {
                sCurrentApproverId = currentStep.stepApprovers[0].approver_id || 
                    (currentStep.stepApprovers[0].approver ? currentStep.stepApprovers[0].approver.id : "");
            }
            
            this.addConsoleLog("Fetching valid reassignees for role: " + sCurrentRole, "info");
            
            this.callApi("/api/workflow/get-valid-reassignees", "POST", {
                current_approver_id: sCurrentApproverId,
                current_role: sCurrentRole
            }).then(function(result) {
                var aUsers = result.data || [];
                
                if (aUsers.length === 0) {
                    oSelect.addItem(new sap.ui.core.ListItem({
                        key: "",
                        text: "No available users found for reassignment"
                    }));
                    MessageBox.warning("No users available for reassignment with role: " + sCurrentRole);
                } else {
                    oSelect.addItem(new sap.ui.core.ListItem({
                        key: "",
                        text: "-- Select new approver --"
                    }));
                    
                    aUsers.forEach(function(user) {
                        oSelect.addItem(new sap.ui.core.ListItem({
                            key: user.id,
                            text: user.fullName + " (" + user.employeeID + ") - " + (user.department || user.role)
                        }));
                    });
                }
                
                oDialog.open();
            }).catch(function(error) {
                that.addConsoleLog("Failed to fetch reassignees: " + error.message, "error");
                MessageBox.error("Failed to load reassignment options: " + error.message);
            });
        },

        getCurrentRole: function(currentStep) {
            if (!currentStep || !currentStep.levelName) return "Supervisor";
            
            var sLevel = currentStep.levelName;
            if (sLevel.includes("Supervisor")) return "Supervisor";
            if (sLevel.includes("Section Manager")) return "Section Manager";
            if (sLevel.includes("Department Manager")) return "Department Manager";
            if (sLevel.includes("Deputy Division Manager")) return "Deputy Division Manager";
            if (sLevel.includes("Division Manager")) return "Division Manager";
            if (sLevel.includes("Accountant")) return "Accountant";
            
            return sLevel;
        },

        onConfirmReassign: function() {
            var oSelect = this.getView().byId("reassignSelect");
            var sTargetId = oSelect.getSelectedKey();
            var sComment = this.getView().byId("reassignComment").getValue();
            
            if (!sTargetId || sTargetId === "") {
                MessageBox.warning("Please select an approver to reassign to");
                return;
            }
            
            this.submitAction("REASSIGN", sTargetId, sComment);
            this.getView().byId("reassignDialog").close();
        },

        onCloseReassignDialog: function() {
            this.getView().byId("reassignDialog").close();
        },

        onClearConsole: function() {
            this.clearConsole();
        }
    });
});