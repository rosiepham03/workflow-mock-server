sap.ui.define([
    "sap/ui/core/UIComponent",
    "sap/ui/model/json/JSONModel",
    "sap/ui/model/resource/ResourceModel"
], function (UIComponent, JSONModel, ResourceModel) {
    "use strict";

    return UIComponent.extend("com.workflow.ui5.Component", {

        metadata: {
            manifest: "json"
        },

        init: function () {
            // Gọi init của component cha
            UIComponent.prototype.init.apply(this, arguments);

            // Tạo model cho workflow data
            var oWorkflowModel = new JSONModel({
                workflowData: null,
                currentStep: null,
                steps: [],
                approvers: [],
                selectedApprover: null,
                statusCode: "APPROVE",
                comment: "Xử lý hồ sơ chứng từ trên hệ thống.",
                loading: false,
                workflowProgress: "",
                isReassignModal: false
            });
            this.setModel(oWorkflowModel, "workflow");

            // Tạo model cho response console
            var oResponseModel = new JSONModel({
                status: "N/A",
                data: "// Kết quả trả về từ API Spring Boot sẽ hiển thị tại đây..."
            });
            this.setModel(oResponseModel, "response");

            // Tạo model cho config
            var oConfigModel = new JSONModel({
                baseUrl: "http://localhost:8082"
            });
            this.setModel(oConfigModel, "config");

            // Tạo i18n model
            var i18nModel = new ResourceModel({
                bundleName: "com.workflow.ui5.i18n.i18n"
            });
            this.setModel(i18nModel, "i18n");

            // CHỈ gọi router nếu có routing trong manifest
            // Hiện tại manifest của bạn có routing, nhưng cần kiểm tra
            try {
                if (this.getRouter()) {
                    this.getRouter().initialize();
                }
            } catch (error) {
                console.warn("Router not initialized:", error);
            }
        },

        destroy: function () {
            UIComponent.prototype.destroy.apply(this, arguments);
        }
    });
});