package com.example.ravenwebhook.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdmissionReview {

    private String apiVersion;
    private String kind;
    private AdmissionRequest request;
    private AdmissionResponse response;

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public AdmissionRequest getRequest() {
        return request;
    }

    public void setRequest(AdmissionRequest request) {
        this.request = request;
    }

    public AdmissionResponse getResponse() {
        return response;
    }

    public void setResponse(AdmissionResponse response) {
        this.response = response;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdmissionRequest {
        private String uid;
        private String kind;
        private String resource;
        private String subResource;
        private String requestKind;
        private String requestResource;
        private String requestSubResource;
        private String name;
        private String namespace;
        private String operation;
        private Map<String, Object> userInfo;
        private Object object;
        private Object oldObject;
        private boolean dryRun;

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getSubResource() {
            return subResource;
        }

        public void setSubResource(String subResource) {
            this.subResource = subResource;
        }

        public String getRequestKind() {
            return requestKind;
        }

        public void setRequestKind(String requestKind) {
            this.requestKind = requestKind;
        }

        public String getRequestResource() {
            return requestResource;
        }

        public void setRequestResource(String requestResource) {
            this.requestResource = requestResource;
        }

        public String getRequestSubResource() {
            return requestSubResource;
        }

        public void setRequestSubResource(String requestSubResource) {
            this.requestSubResource = requestSubResource;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public Map<String, Object> getUserInfo() {
            return userInfo;
        }

        public void setUserInfo(Map<String, Object> userInfo) {
            this.userInfo = userInfo;
        }

        public Object getObject() {
            return object;
        }

        public void setObject(Object object) {
            this.object = object;
        }

        public Object getOldObject() {
            return oldObject;
        }

        public void setOldObject(Object oldObject) {
            this.oldObject = oldObject;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdmissionResponse {
        private String uid;
        private boolean allowed;
        private Status status;
        private String patch;
        private String patchType;
        private Map<String, String> auditAnnotations;
        private String[] warnings;

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public void setAllowed(boolean allowed) {
            this.allowed = allowed;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public String getPatch() {
            return patch;
        }

        public void setPatch(String patch) {
            this.patch = patch;
        }

        public String getPatchType() {
            return patchType;
        }

        public void setPatchType(String patchType) {
            this.patchType = patchType;
        }

        public Map<String, String> getAuditAnnotations() {
            return auditAnnotations;
        }

        public void setAuditAnnotations(Map<String, String> auditAnnotations) {
            this.auditAnnotations = auditAnnotations;
        }

        public String[] getWarnings() {
            return warnings;
        }

        public void setWarnings(String[] warnings) {
            this.warnings = warnings;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Status {
        private String status;
        private String message;
        private String reason;
        private Integer code;

        public Status() {}

        public Status(String status, String message, Integer code) {
            this.status = status;
            this.message = message;
            this.code = code;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }
    }
}
