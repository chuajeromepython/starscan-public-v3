package com.example.omrscanner.models;

public class CSVFile {
    private String imageUri;
    private String filePath;
    private String fileName;
    private long timestamp;

    public CSVFile() {
        // Default constructor for deserialization
    }

    public CSVFile(String imageUri, String filePath, String fileName) {
        this.imageUri = imageUri;
        this.filePath = filePath;
        this.fileName = fileName;
        this.timestamp = System.currentTimeMillis();
    }

    public CSVFile(String imageUri, String filePath, String fileName, long timestamp) {
        this.imageUri = imageUri;
        this.filePath = filePath;
        this.fileName = fileName;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}