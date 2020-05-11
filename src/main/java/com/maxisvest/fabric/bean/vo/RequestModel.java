package com.maxisvest.fabric.bean.vo;

/**
 * Create by yuyang
 * 2020/5/8 18:42
 */
public class RequestModel {


    private String userID;
    private String contentType;
    private String content;

    public RequestModel() {
    }

    public RequestModel(String userID, String contentType, String content) {
        this.userID = userID;
        this.contentType = contentType;
        this.content = content;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
