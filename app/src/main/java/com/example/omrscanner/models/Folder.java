package com.example.omrscanner.models;

public class Folder {
    private String id;
    private String name;
    private long createdAt;
    private int imageCount;

    public Folder() {
        // Default constructor for deserialization
    }

    public Folder(String id, String name) {
        this.id = id;
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.imageCount = 0;
    }

    public Folder(String id, String name, long createdAt, int imageCount) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.imageCount = imageCount;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getImageCount() {
        return imageCount;
    }

    public void setImageCount(int imageCount) {
        this.imageCount = imageCount;
    }

    public void incrementImageCount() {
        this.imageCount++;
    }

    public void decrementImageCount() {
        if (this.imageCount > 0) {
            this.imageCount--;
        }
    }
}