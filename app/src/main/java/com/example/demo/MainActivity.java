
package com.example.demo;

import android.content.ContentValues;

import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView txtStatus;
    private Uri selectedImageUri;
    private Uri cameraImageUri;

    private TextRecognizer textRecognizer;
    private ImageLabeler imageLabeler;
    private TextToSpeech textToSpeech;

    private String recognizedText = "";
    private boolean ttsReady = false;

    private Translator hindiTranslator;


    // Gallery image picker
    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            selectedImageUri = uri;
                            recognizedText = "";
                            txtStatus.setText(
                                    "Image selected! Tap Read Text."
                            );
                        } else {
                            txtStatus.setText("No image selected.");
                        }
                    }
            );

    // Full-resolution camera capture
    private final ActivityResultLauncher<Uri> fullCameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicture(),
                    success -> {
                        if (success && cameraImageUri != null) {
                            selectedImageUri = cameraImageUri;
                            recognizedText = "";
                            txtStatus.setText(
                                    "Photo captured! Tap Read Text."
                            );
                        } else {
                            txtStatus.setText(
                                    "Photo capture cancelled or failed."
                            );
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage("en")
                .setTargetLanguage("hi")
                .build();

        hindiTranslator = Translation.getClient(options);


        txtStatus = findViewById(R.id.txtStatus);

        Button btnCaptureImage =
                findViewById(R.id.btnCaptureImage);
        Button btnChooseImage =
                findViewById(R.id.btnChooseImage);
        Button btnReadText =
                findViewById(R.id.btnReadText);
        Button btnSpeakText =
                findViewById(R.id.btnSpeakText);
        Button btnDescribeImage =
                findViewById(R.id.btnDescribeImage);

        textRecognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );
        imageLabeler = ImageLabeling.getClient(
                ImageLabelerOptions.DEFAULT_OPTIONS
        );

        textToSpeech = new TextToSpeech(
                this,
                status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result =
                                textToSpeech.setLanguage(Locale.US);

                        ttsReady =
                                result != TextToSpeech.LANG_MISSING_DATA
                                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
                    }
                }
        );

        btnChooseImage.setOnClickListener(v ->
                imagePicker.launch("image/*")
        );

        btnCaptureImage.setOnClickListener(v ->
                captureFullResolutionImage()
        );

        btnReadText.setOnClickListener(v ->
                readTextFromImage()
        );

        btnSpeakText.setOnClickListener(v ->
                speakRecognizedText()
        );


        btnDescribeImage.setOnClickListener(v ->
                describeImage()
        );

    }

    private void captureFullResolutionImage() {
        ContentValues values = new ContentValues();
        values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "AI_Vision_" + System.currentTimeMillis() + ".jpg"
        );
        values.put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/AI Vision Assistant"
            );
        }

        Uri newImageUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
        );

        if (newImageUri == null) {
            txtStatus.setText("Could not prepare camera image.");
            return;
        }

        cameraImageUri = newImageUri;
        txtStatus.setText("Opening camera...");

        try {
            fullCameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            getContentResolver().delete(cameraImageUri, null, null);
            cameraImageUri = null;
            txtStatus.setText("Could not open camera.");
        }
    }

    private void readTextFromImage() {
        if (selectedImageUri == null) {
            txtStatus.setText("Please choose or capture an image first.");
            return;
        }

        txtStatus.setText("Reading text from image...");

        try {
            InputImage image = InputImage.fromFilePath(
                    this,
                    selectedImageUri
            );

            android.util.Log.d(
                    "AI_VISION",
                    "Image dimensions: "
                            + image.getWidth() + " x " + image.getHeight()
            );

            textRecognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        recognizedText = visionText.getText();

                        if (recognizedText.trim().isEmpty()) {
                            txtStatus.setText(
                                    "No text found. Try a clearer image."
                            );
                        } else {
                            txtStatus.setText(recognizedText);
                        }
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e(
                                "AI_VISION",
                                "OCR failed",
                                e
                        );
                        txtStatus.setText(
                                "Could not read text. Please try again."
                        );
                    });

        } catch (IOException e) {
            android.util.Log.e(
                    "AI_VISION",
                    "Could not open image",
                    e
            );
            txtStatus.setText(
                    "Could not open this image. Please select another."
            );
        }
    }


    private void speakRecognizedText() {
        String text = txtStatus.getText().toString().trim();

        if (text.isEmpty()) {
            txtStatus.setText("Please describe an image first.");
            return;
        }

        if (!ttsReady || textToSpeech == null) {
            Toast.makeText(
                    this,
                    "Speech engine is not ready. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        textToSpeech.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "vision_description"
        );
    }




    private void describeImage() {
        if (selectedImageUri == null) {
            txtStatus.setText(
                    "Please choose or capture an image first."
            );
            return;
        }

        txtStatus.setText("Analyzing image...");

        try {
            InputImage image = InputImage.fromFilePath(
                    this,
                    selectedImageUri
            );

            imageLabeler.process(image)
                    .addOnSuccessListener(labels -> {
                        java.util.List<String> englishLabels =
                                new java.util.ArrayList<>();

                        for (ImageLabel label : labels) {
                            if (label.getConfidence() >= 0.40f) {
                                englishLabels.add(label.getText());
                            }
                        }

                        if (englishLabels.isEmpty()) {
                            txtStatus.setText(
                                    "Clear objects nahi mile. "
                                            + "Try a clearer photo."
                            );
                            return;
                        }

                        txtStatus.setText(
                                "Preparing Hindi translation..."
                        );

                        hindiTranslator.downloadModelIfNeeded()
                                .addOnSuccessListener(unused -> {
                                    translateLabels(englishLabels);
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e(
                                            "AI_VISION",
                                            "Hindi model download failed",
                                            e
                                    );
                                    txtStatus.setText(
                                            "Hindi model download failed. "
                                                    + "Check internet and try again."
                                    );
                                });
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e(
                                "AI_VISION",
                                "Image labeling failed",
                                e
                        );
                        txtStatus.setText(
                                "Could not analyze image. Try again."
                        );
                    });

        } catch (java.io.IOException e) {
            txtStatus.setText(
                    "Could not open image. Please select another."
            );
        }
    }



    private void translateLabels(
            java.util.List<String> englishLabels) {

        java.util.List<String> hindiLabels =
                new java.util.ArrayList<>();

        translateNextLabel(englishLabels, hindiLabels, 0);
    }

    private void translateNextLabel(
            java.util.List<String> englishLabels,
            java.util.List<String> hindiLabels,
            int index) {

        if (index >= englishLabels.size()) {
            StringBuilder result = new StringBuilder();
            result.append("Image Description / तस्वीर का विवरण\n\n");
            result.append("English labels: ");
            result.append(englishLabels.toString());
            result.append("\n\nHindi description: तस्वीर में ");
            for (int i = 0; i < hindiLabels.size(); i++) {
                if (i > 0) {
                    result.append(", ");
                }
                result.append(hindiLabels.get(i));
            }

            result.append(" दिखाई दे सकते हैं।");
            txtStatus.setText(result.toString());
            return;
        }

        hindiTranslator.translate(englishLabels.get(index))
                .addOnSuccessListener(translatedText -> {
                    android.util.Log.d(
                            "AI_VISION",
                            englishLabels.get(index) + " -> " + translatedText
                    );

                    hindiLabels.add(translatedText);

                    translateNextLabel(
                            englishLabels,
                            hindiLabels,
                            index + 1
                    );
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(
                            "AI_VISION",
                            "Label translation failed",
                            e
                    );

                    txtStatus.setText(
                            "Hindi translation failed. Please retry."
                    );
                });
    }

    @Override
    protected void onDestroy() {
        if (textRecognizer != null) {
            textRecognizer.close();
        }

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
}
