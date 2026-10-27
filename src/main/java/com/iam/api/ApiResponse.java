package com.iam.api;

/** What the router hands back to the servlet: status, content type, body and (for 405) the Allow header. */
public record ApiResponse(int status, String contentType, String body, String allow) {

    public ApiResponse(int status, String contentType, String body) {
        this(status, contentType, body, null);
    }
}
