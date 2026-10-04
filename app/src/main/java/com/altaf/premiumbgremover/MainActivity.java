package com.altaf.premiumbgremover;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_PICK_IMAGE = 1001;
    private static final int REQUEST_SAVE_PNG = 1002;
    private static final int MAX_RETRIES = 8;
    private static final double PAID_PRICE_USD = 0.1047;

    private ImageView previewImage;
    private TextView emptyHint;
    private TextView statusText;
    private TextView selectButton;
    private TextView saveButton;
    private TextView freeModeButton;
    private TextView paidModeButton;
    private LinearLayout paidPanel;
    private EditText apiKeyInput;

    private SubjectSegmenter segmenter;
    private Bitmap resultBitmap;
    private Uri selectedUri;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean processing = false;
    private boolean paidMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewImage = findViewById(R.id.previewImage);
        emptyHint = findViewById(R.id.emptyHint);
        statusText = findViewById(R.id.statusText);
        selectButton = findViewById(R.id.selectButton);
        saveButton = findViewById(R.id.saveButton);
        freeModeButton = findViewById(R.id.freeModeButton);
        paidModeButton = findViewById(R.id.paidModeButton);
        paidPanel = findViewById(R.id.paidPanel);
        apiKeyInput = findViewById(R.id.apiKeyInput);

        previewImage.setBackground(createCheckerboard());
        setSaveEnabled(false);
        setMode(false);

        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build();
        segmenter = SubjectSegmentation.getClient(options);

        freeModeButton.setOnClickListener(v -> {
            if (!processing) setMode(false);
        });

        paidModeButton.setOnClickListener(v -> {
            if (!processing) setMode(true);
        });

        findViewById(R.id.getApiKeyButton).setOnClickListener(v -> {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://app.leonardo.ai/api-access"));
            startActivity(browser);
        });

        selectButton.setOnClickListener(v -> {
            if (!processing) {
                if (paidMode && apiKeyInput.getText().toString().trim().isEmpty()) {
                    Toast.makeText(this, "Paid Pro ke liye Leonardo API key paste karein.", Toast.LENGTH_LONG).show();
                    apiKeyInput.requestFocus();
                    return;
                }
                openImagePicker();
            }
        });

        saveButton.setOnClickListener(v -> {
            if (resultBitmap != null && !processing) {
                openSavePicker();
            }
        });
    }

    private void setMode(boolean paid) {
        paidMode = paid;
        paidPanel.setVisibility(paid ? View.VISIBLE : View.GONE);
        freeModeButton.setAlpha(paid ? 0.45f : 1f);
        paidModeButton.setAlpha(paid ? 1f : 0.45f);
        setStatus(paid
                ? "Paid Pro • Leonardo/remove.bg quality • $0.1047 per image"
                : "Free AI • on-device processing");
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

            if (paidMode) {
                confirmPaidCall();
            } else {
                processFreeSelectedImage(0);
            }
        } else if (requestCode == REQUEST_SAVE_PNG) {
            savePng(uri);
        }
    }

    private void confirmPaidCall() {
        new AlertDialog.Builder(this)
                .setTitle("Use Paid Pro API?")
                .setMessage("Is image ke background removal par approx $0.1047 charge hoga. Actual charge Leonardo response me show hoga.")
                .setNegativeButton("Cancel", (dialog, which) -> setStatus("Paid request cancelled"))
                .setPositiveButton("Continue", (dialog, which) -> processPaidSelectedImage())
                .show();
    }

    private void processFreeSelectedImage(int attempt) {
        if (selectedUri == null) return;

        processing = true;
        selectButton.setAlpha(0.55f);
        setSaveEnabled(false);

        if (attempt == 0) {
            setStatus("Free AI • removing background…");
        } else {
            setStatus("Preparing free AI model… " + attempt + "/" + MAX_RETRIES);
        }

        final InputImage inputImage;
        try {
            inputImage = InputImage.fromFilePath(this, selectedUri);
        } catch (IOException e) {
            finishWithError("Could not read this image.");
            return;
        }

        segmenter.process(inputImage)
                .addOnSuccessListener(this::handleFreeSegmentationResult)
                .addOnFailureListener(error -> {
                    if (attempt < MAX_RETRIES) {
                        setStatus("First use: downloading free AI model…");
                        handler.postDelayed(() -> processFreeSelectedImage(attempt + 1), 2500);
                    } else {
                        finishWithError("Free AI model is not ready. Check internet and Google Play services, then try again.");
                    }
                });
    }

    private void handleFreeSegmentationResult(SubjectSegmentationResult result) {
        Bitmap foreground = result.getForegroundBitmap();
        if (foreground == null) {
            finishWithError("Free AI could not detect a clear foreground subject.");
            return;
        }

        resultBitmap = foreground.copy(Bitmap.Config.ARGB_8888, false);
        previewImage.setImageBitmap(resultBitmap);

        processing = false;
        selectButton.setAlpha(1f);
        setSaveEnabled(true);
        setStatus("Free AI • background removed • transparent PNG ready");
    }

    private void processPaidSelectedImage() {
        final String apiKey = apiKeyInput.getText().toString().trim();
        if (selectedUri == null || apiKey.isEmpty()) return;

        processing = true;
        selectButton.setAlpha(0.55f);
        setSaveEnabled(false);
        setStatus("Paid Pro • uploading securely to Leonardo…");

        new Thread(() -> {
            try {
                byte[] imageBytes = readAllBytes(selectedUri);
                if (imageBytes.length > 25 * 1024 * 1024) {
                    throw new IOException("Image is larger than the 25 MB API limit.");
                }

                String encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                JSONObject image = new JSONObject();
                image.put("type", "BASE64");
                image.put("data", encoded);

                JSONObject imageWrapper = new JSONObject();
                imageWrapper.put("image", image);

                JSONArray imageReference = new JSONArray();
                imageReference.put(imageWrapper);

                JSONObject guidances = new JSONObject();
                guidances.put("image_reference", imageReference);

                JSONObject parameters = new JSONObject();
                parameters.put("size", "full");
                parameters.put("type", "auto");
                parameters.put("format", "png");
                parameters.put("channels", "rgba");
                parameters.put("semitransparency", true);
                parameters.put("shadow_type", "none");
                parameters.put("guidances", guidances);

                JSONObject body = new JSONObject();
                body.put("model", "remove-bg");
                body.put("public", false);
                body.put("ephemeral", true);
                body.put("parameters", parameters);

                HttpURLConnection connection = (HttpURLConnection)
                        new URL("https://cloud.leonardo.ai/api/rest/v2/generationssync").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bodyBytes.length);

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bodyBytes);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String responseText = new String(readStream(stream), StandardCharsets.UTF_8);

                if (status < 200 || status >= 300) {
                    String friendly;
                    if (status == 401 || status == 403) {
                        friendly = "API key invalid ya API access enabled nahi hai.";
                    } else if (status == 402) {
                        friendly = "Leonardo API balance/credits insufficient hain.";
                    } else if (status == 429) {
                        friendly = "API rate limit hit hua. Thodi der baad try karein.";
                    } else {
                        friendly = "Paid API error " + status + ". " + trimError(responseText);
                    }
                    throw new IOException(friendly);
                }

                JSONObject response = new JSONObject(responseText);
                JSONArray results = response.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    throw new IOException("Paid API ne output image return nahi ki.");
                }

                JSONObject first = results.getJSONObject(0);
                String resultUrl = first.optString("url", "");
                if (resultUrl.isEmpty()) {
                    throw new IOException("Paid API result URL missing hai.");
                }

                Bitmap paidBitmap = downloadBitmap(resultUrl);
                if (paidBitmap == null) {
                    throw new IOException("Paid result image download nahi hui.");
                }

                JSONObject cost = response.optJSONObject("cost");
                final String charged = cost != null
                        ? cost.optString("amount", String.format(Locale.US, "%.4f", PAID_PRICE_USD))
                        : String.format(Locale.US, "%.4f", PAID_PRICE_USD);

                runOnUiThread(() -> {
                    resultBitmap = paidBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    previewImage.setImageBitmap(resultBitmap);
                    processing = false;
                    selectButton.setAlpha(1f);
                    setSaveEnabled(true);
                    setStatus("Paid Pro done • charged $" + charged + " • transparent PNG ready");
                });

            } catch (Exception e) {
                runOnUiThread(() -> finishWithError(e.getMessage() == null
                        ? "Paid API request failed."
                        : e.getMessage()));
            }
        }).start();
    }

    private byte[] readAllBytes(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open selected image.");
            return readStream(input);
        }
    }

    private byte[] readStream(InputStream input) throws IOException {
        if (input == null) return new byte[0];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private Bitmap downloadBitmap(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod("GET");
        connection.connect();

        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            throw new IOException("Paid result download failed: HTTP " + connection.getResponseCode());
        }

        try (InputStream input = connection.getInputStream()) {
            return BitmapFactory.decodeStream(input);
        }
    }

    private String trimError(String text) {
        if (text == null) return "";
        String cleaned = text.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() > 180 ? cleaned.substring(0, 180) : cleaned;
    }

    private void savePng(Uri destination) {
        if (resultBitmap == null) return;

        try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) throw new IOException("Output stream unavailable");

            boolean ok = resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            if (!ok) throw new IOException("PNG encoder failed");

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
        if (segmenter != null) segmenter.close();
        super.onDestroy();
    }
}
