package com.example.omrscanner.models;

public class ImageData {
    private String uri;
    private String filename;
    private String folderId;
    private long timestamp;
    private boolean hasCSV;

    public ImageData() {
        // Default constructor for deserialization
    }

    public ImageData(String uri, String filename, String folderId) {
        this.uri = uri;
        this.filename = filename;
        this.folderId = folderId;
        this.timestamp = System.currentTimeMillis();
        this.hasCSV = false;
    }

    public ImageData(String uri, String filename, String folderId, long timestamp, boolean hasCSV) {
        this.uri = uri;
        this.filename = filename;
        this.folderId = folderId;
        this.timestamp = timestamp;
        this.hasCSV = hasCSV;
    }

    // Getters and Setters
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean hasCSV() {
        return hasCSV;
    }

    public void setHasCSV(boolean hasCSV) {
        this.hasCSV = hasCSV;
    }
}