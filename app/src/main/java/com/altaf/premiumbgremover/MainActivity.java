package com.altaf.premiumbgremover;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_PICK_IMAGE = 1001;
    private static final int REQUEST_SAVE_PNG = 1002;
    private static final int MAX_RETRIES = 8;

    private ImageView previewImage;
    private TextView emptyHint;
    private TextView statusText;
    private TextView selectButton;
    private TextView saveButton;

    private SubjectSegmenter segmenter;
    private Bitmap resultBitmap;
    private Uri selectedUri;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean processing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewImage = findViewById(R.id.previewImage);
        emptyHint = findViewById(R.id.emptyHint);
        statusText = findViewById(R.id.statusText);
        selectButton = findViewById(R.id.selectButton);
        saveButton = findViewById(R.id.saveButton);

        previewImage.setBackground(createCheckerboard());
        setSaveEnabled(false);

        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build();
        segmenter = SubjectSegmentation.getClient(options);

        selectButton.setOnClickListener(v -> {
            if (!processing) {
                openImagePicker();
            }
        });

        saveButton.setOnClickListener(v -> {
            if (resultBitmap != null && !processing) {
                openSavePicker();
            }
        });
    }

    private BitmapDrawable createCheckerboard() {
        int tile = 36;
        Bitmap bitmap = Bitmap.createBitmap(tile * 2, tile * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Color.rgb(31, 33, 39));
        canvas.drawRect(0, 0, tile, tile, paint);
        canvas.drawRect(tile, tile, tile * 2, tile * 2, paint);

        paint.setColor(Color.rgb(43, 46, 54));
        canvas.drawRect(tile, 0, tile * 2, tile, paint);
        canvas.drawRect(0, tile, tile, tile * 2, paint);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
        drawable.setTileModeX(android.graphics.Shader.TileMode.REPEAT);
        drawable.setTileModeY(android.graphics.Shader.TileMode.REPEAT);
        return drawable;
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    private void openSavePicker() {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "BG_Removed_" + stamp + ".png");
        startActivityForResult(intent, REQUEST_SAVE_PNG);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        if (requestCode == REQUEST_PICK_IMAGE) {
            selectedUri = uri;
            resultBitmap = null;
            setSaveEnabled(false);
            emptyHint.setVisibility(View.GONE);
            previewImage.setImageURI(selectedUri);
            processSelectedImage(0);
        } else if (requestCode == REQUEST_SAVE_PNG) {
            savePng(uri);
        }
    }

    private void processSelectedImage(int attempt) {
        if (selectedUri == null) {
            return;
        }

        processing = true;
        selectButton.setAlpha(0.55f);
        setSaveEnabled(false);

        if (attempt == 0) {
            setStatus("Removing background…");
        } else {
            setStatus("Preparing AI model… " + attempt + "/" + MAX_RETRIES);
        }

        final InputImage inputImage;
        try {
            inputImage = InputImage.fromFilePath(this, selectedUri);
        } catch (IOException e) {
            finishWithError("Could not read this image.");
            return;
        }

        segmenter.process(inputImage)
                .addOnSuccessListener(this::handleSegmentationResult)
                .addOnFailureListener(error -> {
                    if (attempt < MAX_RETRIES) {
                        setStatus("First use: downloading AI model…");
                        handler.postDelayed(() -> processSelectedImage(attempt + 1), 2500);
                    } else {
                        finishWithError("AI model is not ready. Check internet and Google Play services, then select the image again.");
                    }
                });
    }

    private void handleSegmentationResult(SubjectSegmentationResult result) {
        Bitmap foreground = result.getForegroundBitmap();
        if (foreground == null) {
            finishWithError("No clear foreground subject was detected.");
            return;
        }

        resultBitmap = foreground.copy(Bitmap.Config.ARGB_8888, false);
        previewImage.setImageBitmap(resultBitmap);

        processing = false;
        selectButton.setAlpha(1f);
        setSaveEnabled(true);
        setStatus("Background removed • transparent PNG ready");
    }

    private void savePng(Uri destination) {
        if (resultBitmap == null) {
            return;
        }

        try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) {
                throw new IOException("Output stream unavailable");
            }

            boolean ok = resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            if (!ok) {
                throw new IOException("PNG encoder failed");
            }

            Toast.makeText(this, "Transparent PNG saved", Toast.LENGTH_LONG).show();
            setStatus("Saved successfully");
        } catch (Exception e) {
            Toast.makeText(this, "Could not save PNG", Toast.LENGTH_LONG).show();
            setStatus("Save failed • choose another folder");
        }
    }

    private void finishWithError(String message) {
        processing = false;
        selectButton.setAlpha(1f);
        setSaveEnabled(resultBitmap != null);
        setStatus(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private void setSaveEnabled(boolean enabled) {
        saveButton.setEnabled(enabled);
        saveButton.setAlpha(enabled ? 1f : 0.35f);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (segmenter != null) {
            segmenter.close();
        }
        super.onDestroy();
    }
}
