package com.example.omrscanner.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.omrscanner.models.CSVFile;
import com.example.omrscanner.models.Folder;
import com.example.omrscanner.models.ImageData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Manages file storage for images, CSVs, and folder organization
 */
public class StorageManager {
    private static final String PREFS_NAME = "omr_storage";
    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_IMAGES = "images";
    private static final String KEY_CSVS = "csvs";

    private Context context;
    private SharedPreferences prefs;
    private Gson gson;

    // In-memory cache
    private List<Folder> folders;
    private List<ImageData> images;
    private Map<String, CSVFile> csvFiles; // Key: imageUri, Value: CSVFile

    public StorageManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();

        loadFromPreferences();
    }

    // ==================== FOLDER OPERATIONS ====================

    public Folder createFolder(String name) {
        String folderId = UUID.randomUUID().toString();
        Folder folder = new Folder(folderId, name);
        folders.add(folder);
        saveToPreferences();
        return folder;
    }

    public List<Folder> getAllFolders() {
        return new ArrayList<>(folders);
    }

    public Folder getFolderById(String folderId) {
        for (Folder folder : folders) {
            if (folder.getId().equals(folderId)) {
                return folder;
            }
        }
        return null;
    }

    public boolean deleteFolder(String folderId) {
        // Delete all images in folder first
        List<ImageData> folderImages = getImagesInFolder(folderId);
        for (ImageData image : folderImages) {
            deleteImage(image);
        }

        // Remove folder
        Folder folderToRemove = null;
        for (Folder folder : folders) {
            if (folder.getId().equals(folderId)) {
                folderToRemove = folder;
                break;
            }
        }

        if (folderToRemove != null) {
            folders.remove(folderToRemove);
            saveToPreferences();
            return true;
        }
        return false;
    }

    // ==================== IMAGE OPERATIONS ====================

    public ImageData saveImage(String sourcePath, String folderId) {
        try {
            // Create images directory
            File imagesDir = new File(context.getFilesDir(), "images");
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            // Generate unique filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            String filename = "exam_" + timestamp + ".jpg";
            File destFile = new File(imagesDir, filename);

            // Copy file
            File sourceFile = new File(sourcePath);
            copyFile(sourceFile, destFile);

            // Create ImageData
            ImageData imageData = new ImageData(
                    destFile.getAbsolutePath(),
                    filename,
                    folderId
            );

            images.add(imageData);

            // Update folder count
            Folder folder = getFolderById(folderId);
            if (folder != null) {
                folder.incrementImageCount();
            }

            saveToPreferences();
            return imageData;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<ImageData> getAllImages() {
        return new ArrayList<>(images);
    }

    public List<ImageData> getImagesInFolder(String folderId) {
        List<ImageData> result = new ArrayList<>();
        for (ImageData image : images) {
            if (image.getFolderId().equals(folderId)) {
                result.add(image);
            }
        }
        return result;
    }

    public boolean deleteImage(ImageData imageData) {
        try {
            // Delete image file
            File imageFile = new File(imageData.getUri());
            if (imageFile.exists()) {
                imageFile.delete();
            }

            // Delete associated CSV
            CSVFile csvFile = csvFiles.get(imageData.getUri());
            if (csvFile != null) {
                File csv = new File(csvFile.getFilePath());
                if (csv.exists()) {
                    csv.delete();
                }
                csvFiles.remove(imageData.getUri());
            }

            // Remove from list
            images.remove(imageData);

            // Update folder count
            Folder folder = getFolderById(imageData.getFolderId());
            if (folder != null) {
                folder.decrementImageCount();
            }

            saveToPreferences();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ==================== CSV OPERATIONS ====================

    public CSVFile saveCSV(String sourcePath, String imageUri) {
        try {
            // Create CSV directory
            File csvDir = new File(context.getFilesDir(), "csv");
            if (!csvDir.exists()) {
                csvDir.mkdirs();
            }

            // Generate filename based on image
            File sourceFile = new File(sourcePath);
            String filename = sourceFile.getName();
            File destFile = new File(csvDir, filename);

            // Copy file
            copyFile(sourceFile, destFile);

            // Create CSVFile
            CSVFile csvFile = new CSVFile(
                    imageUri,
                    destFile.getAbsolutePath(),
                    filename
            );

            csvFiles.put(imageUri, csvFile);

            // Update image hasCSV flag
            for (ImageData image : images) {
                if (image.getUri().equals(imageUri)) {
                    image.setHasCSV(true);
                    break;
                }
            }

            saveToPreferences();
            return csvFile;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public CSVFile getCSVForImage(String imageUri) {
        return csvFiles.get(imageUri);
    }

    public boolean hasCSVForImage(String imageUri) {
        return csvFiles.containsKey(imageUri);
    }

    public String readCSVContent(String csvFilePath) {
        try {
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(csvFilePath));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            return content.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public int getTotalCSVCount() {
        return csvFiles.size();
    }

    // ==================== PERSISTENCE ====================

    private void loadFromPreferences() {
        // Load folders
        String foldersJson = prefs.getString(KEY_FOLDERS, "[]");
        Type folderListType = new TypeToken<List<Folder>>(){}.getType();
        folders = gson.fromJson(foldersJson, folderListType);
        if (folders == null) {
            folders = new ArrayList<>();
        }

        // Load images
        String imagesJson = prefs.getString(KEY_IMAGES, "[]");
        Type imageListType = new TypeToken<List<ImageData>>(){}.getType();
        images = gson.fromJson(imagesJson, imageListType);
        if (images == null) {
            images = new ArrayList<>();
        }

        // Load CSV files
        String csvsJson = prefs.getString(KEY_CSVS, "{}");
        Type csvMapType = new TypeToken<Map<String, CSVFile>>(){}.getType();
        csvFiles = gson.fromJson(csvsJson, csvMapType);
        if (csvFiles == null) {
            csvFiles = new HashMap<>();
        }
    }

    private void saveToPreferences() {
        SharedPreferences.Editor editor = prefs.edit();

        // Save folders
        String foldersJson = gson.toJson(folders);
        editor.putString(KEY_FOLDERS, foldersJson);

        // Save images
        String imagesJson = gson.toJson(images);
        editor.putString(KEY_IMAGES, imagesJson);

        // Save CSV files
        String csvsJson = gson.toJson(csvFiles);
        editor.putString(KEY_CSVS, csvsJson);

        editor.apply();
    }

    // ==================== UTILITIES ====================

    /**
     * Copy file from source to destination
     * Compatible with all API levels
     */
    private void copyFile(File source, File dest) throws IOException {
        InputStream in = null;
        OutputStream out = null;

        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(dest);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Clear all data (for testing/debugging)
     */
    public void clearAllData() {
        folders.clear();
        images.clear();
        csvFiles.clear();
        saveToPreferences();

        // Delete all files
        deleteDirectory(new File(context.getFilesDir(), "images"));
        deleteDirectory(new File(context.getFilesDir(), "csv"));
    }

    private void deleteDirectory(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}