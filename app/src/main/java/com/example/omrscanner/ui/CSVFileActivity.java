package com.example.omrscanner.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Window;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.activity.OnBackPressedCallback;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.omrscanner.R;
import com.example.omrscanner.models.CSVFile;
import com.example.omrscanner.models.Folder;
import com.example.omrscanner.models.ImageData;
import com.example.omrscanner.utils.CSVExporter;
import com.example.omrscanner.utils.StorageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CSVFileActivity extends AppCompatActivity {

    // Intent extras
    public static final String EXTRA_CSV_FILEPATH = "csv_filepath";
    public static final String EXTRA_IMAGE_FILEPATH = "image_filepath";
    public static final String EXTRA_FOLDER_ID = "folder_id";
    public static final String EXTRA_ANSWERS = "answers";

    // UI Components
    private LinearLayout explorerHeader;
    private LinearLayout breadcrumbHeader;
    private TextView breadcrumbFolderName;
    private TextView breadcrumbCount;
    private Button backButton;
    private TextView statFoldersNumber;
    private TextView statImagesNumber;
    private TextView statCsvNumber;
    private RecyclerView folderRecyclerView;
    private RecyclerView imageRecyclerView;
    private LinearLayout emptyStateContainer;
    private LinearLayout emptyFolderContainer;
    private Button emptyFolderBackButton;
    private FrameLayout processingOverlay;

    // Data
    private StorageManager storageManager;
    private List<Folder> folders;
    private List<ImageData> allImages;
    private String selectedFolderId = null;

    // Adapters
    private FolderAdapter folderAdapter;
    private ImageAdapter imageAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_file);

        // Set status bar color to blue
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));

        // Initialize storage manager
        storageManager = new StorageManager(this);

        // Initialize views
        initViews();

        // Setup back press handling
        setupBackPressHandler();

        // Handle incoming data from ResultActivity
        handleIncomingData();

        // Load data and setup UI
        loadData();
        setupRecyclerViews();
        updateUI();
    }

    private void setupBackPressHandler() {
        // Use OnBackPressedDispatcher for backward compatibility
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (selectedFolderId != null) {
                    onBackToFolders();
                } else {
                    // Let the system handle the back press (finish activity)
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void initViews() {
        explorerHeader = findViewById(R.id.explorerHeader);
        breadcrumbHeader = findViewById(R.id.breadcrumbHeader);
        breadcrumbFolderName = findViewById(R.id.breadcrumbFolderName);
        breadcrumbCount = findViewById(R.id.breadcrumbCount);
        backButton = findViewById(R.id.backButton);

        statFoldersNumber = findViewById(R.id.statFoldersNumber);
        statImagesNumber = findViewById(R.id.statImagesNumber);
        statCsvNumber = findViewById(R.id.statCsvNumber);

        folderRecyclerView = findViewById(R.id.folderRecyclerView);
        imageRecyclerView = findViewById(R.id.imageRecyclerView);

        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        emptyFolderContainer = findViewById(R.id.emptyFolderContainer);
        emptyFolderBackButton = findViewById(R.id.emptyFolderBackButton);

        processingOverlay = findViewById(R.id.processingOverlay);

        // Setup back button
        backButton.setOnClickListener(v -> onBackToFolders());
        emptyFolderBackButton.setOnClickListener(v -> onBackToFolders());
    }

    private void handleIncomingData() {
        Intent intent = getIntent();

        // Check if we're receiving a new CSV file to save
        String csvFilePath = intent.getStringExtra(EXTRA_CSV_FILEPATH);
        String imageFilePath = intent.getStringExtra(EXTRA_IMAGE_FILEPATH);
        String folderId = intent.getStringExtra(EXTRA_FOLDER_ID);

        if (csvFilePath != null && imageFilePath != null) {
            // Show folder selection dialog to save the new file
            showFolderSelectionDialog(csvFilePath, imageFilePath, folderId);
        }
    }

    private void showFolderSelectionDialog(String csvFilePath, String imageFilePath, String suggestedFolderId) {
        loadData(); // Ensure we have latest folders

        if (folders.isEmpty()) {
            // No folders exist, create default one
            showCreateFolderDialog(csvFilePath, imageFilePath);
            return;
        }

        // Build folder selection dialog
        String[] folderNames = new String[folders.size() + 1];
        for (int i = 0; i < folders.size(); i++) {
            folderNames[i] = folders.get(i).getName();
        }
        folderNames[folders.size()] = "+ Create New Folder";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save to Folder");
        builder.setItems(folderNames, (dialog, which) -> {
            if (which == folders.size()) {
                // Create new folder
                showCreateFolderDialog(csvFilePath, imageFilePath);
            } else {
                // Save to selected folder
                Folder selectedFolder = folders.get(which);
                saveFilesToFolder(csvFilePath, imageFilePath, selectedFolder.getId());
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showCreateFolderDialog(String csvFilePath, String imageFilePath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create New Folder");

        final EditText input = new EditText(this);
        input.setHint("Folder name");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (folderName.isEmpty()) {
                Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create folder
            Folder newFolder = storageManager.createFolder(folderName);
            folders.add(newFolder);

            // Save files to new folder
            saveFilesToFolder(csvFilePath, imageFilePath, newFolder.getId());
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveFilesToFolder(String csvFilePath, String imageFilePath, String folderId) {
        showProcessing(true);

        new Thread(() -> {
            try {
                // Save image and CSV to storage
                ImageData savedImage = storageManager.saveImage(imageFilePath, folderId);

                if (savedImage != null) {
                    // Associate CSV with image
                    CSVFile csvFile = storageManager.saveCSV(csvFilePath, savedImage.getUri());

                    runOnUiThread(() -> {
                        showProcessing(false);

                        // Find folder name for better message
                        Folder folder = findFolderById(folderId);
                        String folderName = folder != null ? folder.getName() : "folder";

                        Toast.makeText(
                                this,
                                "✓ Saved to '" + folderName + "' - Tap 'SAVE CSV' to download",
                                Toast.LENGTH_LONG
                        ).show();

                        // Reload data and navigate to folder
                        loadData();
                        selectedFolderId = folderId;
                        updateUI();
                    });
                } else {
                    runOnUiThread(() -> {
                        showProcessing(false);
                        Toast.makeText(
                                this,
                                "Failed to save file",
                                Toast.LENGTH_SHORT
                        ).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    showProcessing(false);
                    Toast.makeText(
                            this,
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }
    private void loadData() {
        folders = storageManager.getAllFolders();
        allImages = storageManager.getAllImages();
    }

    private void setupRecyclerViews() {
        // Folder RecyclerView
        GridLayoutManager folderLayoutManager = new GridLayoutManager(this, 2);
        folderRecyclerView.setLayoutManager(folderLayoutManager);
        folderAdapter = new FolderAdapter(folders, this::onFolderClick);
        folderRecyclerView.setAdapter(folderAdapter);

        // Image RecyclerView
        LinearLayoutManager imageLayoutManager = new LinearLayoutManager(this);
        imageRecyclerView.setLayoutManager(imageLayoutManager);
        imageAdapter = new ImageAdapter(new ArrayList<>(), this);
        imageRecyclerView.setAdapter(imageAdapter);
    }

    private void updateUI() {
        // Update stats
        int totalCsvs = storageManager.getTotalCSVCount();
        statFoldersNumber.setText(String.valueOf(folders.size()));
        statImagesNumber.setText(String.valueOf(allImages.size()));
        statCsvNumber.setText(String.valueOf(totalCsvs));

        if (selectedFolderId == null) {
            // Show folder view
            showFolderView();
        } else {
            // Show file view for selected folder
            showFileView();
        }
    }

    private void showFolderView() {
        explorerHeader.setVisibility(View.VISIBLE);
        breadcrumbHeader.setVisibility(View.GONE);
        folderRecyclerView.setVisibility(View.VISIBLE);
        imageRecyclerView.setVisibility(View.GONE);
        emptyFolderContainer.setVisibility(View.GONE);

        if (folders.isEmpty()) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            folderRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateContainer.setVisibility(View.GONE);
            folderAdapter.updateFolders(folders);
        }
    }

    private void showFileView() {
        explorerHeader.setVisibility(View.GONE);
        breadcrumbHeader.setVisibility(View.VISIBLE);
        folderRecyclerView.setVisibility(View.GONE);
        imageRecyclerView.setVisibility(View.VISIBLE);
        emptyStateContainer.setVisibility(View.GONE);

        // Get folder info
        Folder currentFolder = findFolderById(selectedFolderId);
        if (currentFolder != null) {
            breadcrumbFolderName.setText(currentFolder.getName());
        }

        // Get images for this folder
        List<ImageData> folderImages = getImagesForFolder(selectedFolderId);
        breadcrumbCount.setText("(" + folderImages.size() + ")");

        if (folderImages.isEmpty()) {
            imageRecyclerView.setVisibility(View.GONE);
            emptyFolderContainer.setVisibility(View.VISIBLE);
        } else {
            emptyFolderContainer.setVisibility(View.GONE);
            imageAdapter.updateImages(folderImages);
        }
    }

    private List<ImageData> getImagesForFolder(String folderId) {
        List<ImageData> result = new ArrayList<>();
        for (ImageData image : allImages) {
            if (image.getFolderId().equals(folderId)) {
                result.add(image);
            }
        }
        return result;
    }

    private Folder findFolderById(String folderId) {
        for (Folder folder : folders) {
            if (folder.getId().equals(folderId)) {
                return folder;
            }
        }
        return null;
    }

    private void onFolderClick(Folder folder) {
        selectedFolderId = folder.getId();
        updateUI();
    }

    private void onBackToFolders() {
        selectedFolderId = null;
        updateUI();
    }

    public void onConvertToCSV(ImageData imageData) {
        Toast.makeText(this, "Converting to CSV...", Toast.LENGTH_SHORT).show();
        // This would trigger the OMR detection process
        // For now, this is a placeholder
    }

    public void onViewCSV(ImageData imageData) {
        CSVFile csvFile = storageManager.getCSVForImage(imageData.getUri());
        if (csvFile == null) {
            Toast.makeText(this, "CSV file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Read CSV content
        String csvContent = storageManager.readCSVContent(csvFile.getFilePath());

        // Show in dialog
        showCSVViewerDialog(csvFile.getFileName(), csvContent);
    }

    public void onSaveCSV(ImageData imageData) {
        CSVFile csvFile = storageManager.getCSVForImage(imageData.getUri());
        if (csvFile == null) {
            Toast.makeText(this, "CSV file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Share CSV file
        shareCSVFile(csvFile.getFilePath());
    }

    public void onDeleteImage(ImageData imageData) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Image")
                .setMessage("Are you sure you want to delete this exam paper?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    showProcessing(true);

                    new Thread(() -> {
                        boolean success = storageManager.deleteImage(imageData);

                        runOnUiThread(() -> {
                            showProcessing(false);
                            if (success) {
                                Toast.makeText(
                                        this,
                                        "Image deleted",
                                        Toast.LENGTH_SHORT
                                ).show();
                                loadData();
                                updateUI();
                            } else {
                                Toast.makeText(
                                        this,
                                        "Failed to delete image",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCSVViewerDialog(String filename, String content) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_csv_viewer, null);

        TextView tvFilename = dialogView.findViewById(R.id.tvFilename);
        TextView tvContent = dialogView.findViewById(R.id.tvContent);

        tvFilename.setText(filename);
        tvContent.setText(content);

        new AlertDialog.Builder(this)
                .setTitle("CSV Content")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void shareCSVFile(String filePath) {
        try {
            File csvFile = new File(filePath);
            if (!csvFile.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    csvFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Save CSV File"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(
                    this,
                    "Failed to share file: " + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    public void onViewFullImage(ImageData imageData) {
        Intent intent = new Intent(this, FullImageViewerActivity.class);
        intent.putExtra("image_uri", imageData.getUri());
        intent.putExtra("filename", imageData.getFilename());
        intent.putExtra("timestamp", imageData.getTimestamp());
        startActivity(intent);
    }

    private void showProcessing(boolean show) {
        processingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ==================== FOLDER ADAPTER ====================

    static class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {
        private List<Folder> folders;
        private OnFolderClickListener listener;

        interface OnFolderClickListener {
            void onFolderClick(Folder folder);
        }

        FolderAdapter(List<Folder> folders, OnFolderClickListener listener) {
            this.folders = folders;
            this.listener = listener;
        }

        void updateFolders(List<Folder> newFolders) {
            this.folders = newFolders;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_folder_card, parent, false);
            return new FolderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
            holder.bind(folders.get(position), listener);
        }

        @Override
        public int getItemCount() {
            return folders.size();
        }

        static class FolderViewHolder extends RecyclerView.ViewHolder {
            TextView folderName;
            TextView folderInfo;
            TextView folderBadge;

            FolderViewHolder(View itemView) {
                super(itemView);
                folderName = itemView.findViewById(R.id.folderName);
                folderInfo = itemView.findViewById(R.id.folderInfo);
                folderBadge = itemView.findViewById(R.id.folderBadge);
            }

            void bind(Folder folder, OnFolderClickListener listener) {
                folderName.setText(folder.getName());

                int itemCount = folder.getImageCount();
                folderInfo.setText(itemCount + (itemCount == 1 ? " item" : " items"));

                if (itemCount > 0) {
                    folderBadge.setText(String.valueOf(itemCount));
                    folderBadge.setVisibility(View.VISIBLE);
                } else {
                    folderBadge.setVisibility(View.GONE);
                }

                itemView.setOnClickListener(v -> listener.onFolderClick(folder));
            }
        }
    }

    // ==================== IMAGE ADAPTER ====================

    static class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
        private List<ImageData> images;
        private CSVFileActivity activity;

        ImageAdapter(List<ImageData> images, CSVFileActivity activity) {
            this.images = images;
            this.activity = activity;
        }

        void updateImages(List<ImageData> newImages) {
            this.images = newImages;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_image_card, parent, false);
            return new ImageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            holder.bind(images.get(position), activity);
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        static class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnailImage;
            TextView imageFilename;
            TextView imageDate;
            Button convertButton;
            Button viewButton;
            Button saveButton;
            Button deleteButton;
            View processingOverlay;
            View imagePreviewContainer;

            ImageViewHolder(View itemView) {
                super(itemView);
                thumbnailImage = itemView.findViewById(R.id.thumbnailImage);
                imageFilename = itemView.findViewById(R.id.imageFilename);
                imageDate = itemView.findViewById(R.id.imageDate);
                convertButton = itemView.findViewById(R.id.convertButton);
                viewButton = itemView.findViewById(R.id.viewButton);
                saveButton = itemView.findViewById(R.id.saveButton);
                deleteButton = itemView.findViewById(R.id.deleteButton);
                processingOverlay = itemView.findViewById(R.id.processingOverlay);
                imagePreviewContainer = itemView.findViewById(R.id.imagePreviewContainer);
            }

            void bind(ImageData imageData, CSVFileActivity activity) {
                // Load thumbnail
                Bitmap bitmap = BitmapFactory.decodeFile(imageData.getUri());
                if (bitmap != null) {
                    thumbnailImage.setImageBitmap(bitmap);
                }

                imageFilename.setText(imageData.getFilename());
                imageDate.setText(new java.text.SimpleDateFormat("MMM dd, yyyy")
                        .format(new java.util.Date(imageData.getTimestamp())));

                // Check if CSV exists
                boolean hasCSV = activity.storageManager.hasCSVForImage(imageData.getUri());

                if (hasCSV) {
                    convertButton.setVisibility(View.GONE);
                    viewButton.setVisibility(View.VISIBLE);
                    saveButton.setVisibility(View.VISIBLE);
                } else {
                    convertButton.setVisibility(View.VISIBLE);
                    viewButton.setVisibility(View.GONE);
                    saveButton.setVisibility(View.GONE);
                }

                // Set click listeners
                imagePreviewContainer.setOnClickListener(v ->
                        activity.onViewFullImage(imageData));

                convertButton.setOnClickListener(v ->
                        activity.onConvertToCSV(imageData));

                viewButton.setOnClickListener(v ->
                        activity.onViewCSV(imageData));

                saveButton.setOnClickListener(v ->
                        activity.onSaveCSV(imageData));

                deleteButton.setOnClickListener(v ->
                        activity.onDeleteImage(imageData));
            }
        }
    }
}