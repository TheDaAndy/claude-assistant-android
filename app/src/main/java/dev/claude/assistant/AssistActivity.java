package dev.claude.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.claude.assistant.ankai.VoiceSubmission;
import dev.claude.assistant.ankai.TextSubmission;
import dev.claude.assistant.ankai.VoiceUiFormatter;
import dev.claude.assistant.ankai.AnkaiProject;
import dev.claude.assistant.ankai.AnkaiRoutingException;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;

public class AssistActivity extends Activity {
    private static final int RECORD_AUDIO_REQUEST_CODE = 101;

    private EditText inputField;
    private TextView responseView;
    private ImageButton micButton;
    private ImageButton sendButton;
    private ProgressBar progressBar;

    private BroadcastReceiver responseReceiver;
    private final ExecutorService voiceExecutor = Executors.newSingleThreadExecutor();
    private MediaRecorder recorder;
    private File recordingFile;
    private boolean recording;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Window flags for overlay style
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_assist);

        inputField = findViewById(R.id.input_field);
        responseView = findViewById(R.id.response_view);
        micButton = findViewById(R.id.mic_button);
        sendButton = findViewById(R.id.send_button);
        progressBar = findViewById(R.id.progress_bar);

        micButton.setOnClickListener(v -> toggleRecording());
        sendButton.setOnClickListener(v -> sendText());

        // Auto-start speech recognition when opened via gesture
        if (getIntent().getAction() != null &&
            getIntent().getAction().equals(Intent.ACTION_ASSIST)) {
            startRecordingWithPermission();
        }

        // Register response receiver
        setupResponseReceiver();
    }

    private void setupResponseReceiver() {
        responseReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String response = intent.getStringExtra("response");
                int exitCode = intent.getIntExtra("exitCode", -1);
                displayResponse(response, exitCode);
            }
        };

        IntentFilter filter = new IntentFilter("dev.claude.assistant.RESPONSE_RECEIVED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(responseReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(responseReceiver, filter);
        }
    }

    private void toggleRecording() {
        if (recording) {
            stopRecordingAndSubmit();
        } else {
            startRecordingWithPermission();
        }
    }

    private void startRecordingWithPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST_CODE);
            return;
        }
        startRecording();
    }

    private void startRecording() {
        recordingFile = new File(getCacheDir(), "ankai-voice-" + System.currentTimeMillis() + ".m4a");
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(128000);
        recorder.setAudioSamplingRate(44100);
        recorder.setOutputFile(recordingFile.getAbsolutePath());
        try {
            recorder.prepare();
            recorder.start();
            recording = true;
            responseView.setText(getString(R.string.status_recording));
            inputField.setEnabled(false);
            sendButton.setEnabled(false);
        } catch (IOException | RuntimeException error) {
            releaseRecorder();
            deleteRecording();
            displayVoiceError(error);
        }
    }

    private void stopRecordingAndSubmit() {
        try {
            recorder.stop();
        } catch (RuntimeException error) {
            releaseRecorder();
            deleteRecording();
            displayVoiceError(new IOException(getString(R.string.error_recording_too_short), error));
            return;
        }
        releaseRecorder();
        recording = false;
        setVoiceBusy(true);
        responseView.setText(getString(R.string.status_uploading));
        File file = recordingFile;
        voiceExecutor.execute(() -> submitRecording(file));
    }

    private void submitRecording(File file) {
        try {
            byte[] audio = Files.readAllBytes(file.toPath());
            submitAudio(file.getName(), audio, null);
        } catch (Throwable error) {
            runOnUiThread(() -> displayVoiceError(error));
        } finally {
            if (file != null) file.delete();
            recordingFile = null;
        }
    }

    private void submitAudio(String filename, byte[] audio, String projectId) {
        try {
            VoiceSubmission submission = new VoiceSubmission(
                    EncryptedPrefsSecretStore.connectionStore(getApplicationContext()),
                    LiveRunRuntime.coordinator(getApplicationContext()));
            runOnUiThread(() -> responseView.setText(getString(R.string.status_uploading)));
            dev.claude.assistant.ankai.VoiceResult result = projectId == null
                    ? submission.submit(filename, "audio/mp4", audio, this::displayVoiceProgress)
                    : submission.submitToProject(filename, "audio/mp4", audio, projectId,
                            this::displayVoiceProgress);
            startLiveRunService();
            runOnUiThread(() -> {
                setVoiceBusy(false);
                responseView.setText(VoiceUiFormatter.result(result));
            });
        } catch (AnkaiRoutingException error) {
            runOnUiThread(() -> showProjectCandidates(error, filename, audio));
        } catch (Throwable error) {
            runOnUiThread(() -> displayVoiceError(error));
        }
    }

    private void startLiveRunService() {
        Intent service = new Intent(getApplicationContext(), AssistantService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(service);
        } else {
            getApplicationContext().startService(service);
        }
    }

    private void displayVoiceProgress(int percent, String stage) {
        runOnUiThread(() -> responseView.setText(VoiceUiFormatter.progress(percent, stage)));
    }

    private void showProjectCandidates(AnkaiRoutingException error, String filename, byte[] audio) {
        if (error.candidates.isEmpty()) {
            displayVoiceError(error);
            return;
        }
        setVoiceBusy(false);
        String[] labels = new String[error.candidates.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = error.candidates.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(error.getMessage())
                .setItems(labels, (dialog, which) -> {
                    AnkaiProject selected = error.candidates.get(which);
                    setVoiceBusy(true);
                    voiceExecutor.execute(() -> submitAudio(filename, audio, selected.id));
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> displayVoiceError(error))
                .setOnCancelListener(dialog -> displayVoiceError(error))
                .show();
    }

    private void displayVoiceError(Throwable error) {
        recording = false;
        setVoiceBusy(false);
        responseView.setText(VoiceUiFormatter.error(error));
    }

    private void setVoiceBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        micButton.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        inputField.setEnabled(!busy);
    }

    private void releaseRecorder() {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
    }

    private void deleteRecording() {
        if (recordingFile != null) recordingFile.delete();
        recordingFile = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != RECORD_AUDIO_REQUEST_CODE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            responseView.setText(getString(R.string.error_microphone_permission));
        }
    }

    private void sendText() {
        String prompt = inputField.getText().toString().trim();
        if (prompt.isEmpty()) return;

        progressBar.setVisibility(View.VISIBLE);
        responseView.setText(getString(R.string.status_thinking));
        micButton.setEnabled(false);
        sendButton.setEnabled(false);

        inputField.setEnabled(false);
        voiceExecutor.execute(() -> {
            try {
                new TextSubmission(EncryptedPrefsSecretStore.connectionStore(getApplicationContext()),
                        LiveRunRuntime.coordinator(getApplicationContext())).submit(prompt);
                startLiveRunService();
                runOnUiThread(() -> {
                    setVoiceBusy(false);
                    responseView.setText(getString(R.string.status_thinking));
                });
            } catch (Throwable error) {
                runOnUiThread(() -> displayVoiceError(error));
            }
        });
    }

    private void displayResponse(String response, int exitCode) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            micButton.setEnabled(true);
            sendButton.setEnabled(true);

            if (exitCode == 0 && response != null && !response.isEmpty()) {
                responseView.setText(response);
            } else {
                responseView.setText(getString(R.string.error_no_response) +
                    (response != null ? ": " + response : ""));
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (responseReceiver != null) {
            unregisterReceiver(responseReceiver);
        }
        if (recording && recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
                // Eine zu kurze Aufnahme ist beim Schliessen absichtlich unbrauchbar.
            }
        }
        releaseRecorder();
        deleteRecording();
        voiceExecutor.shutdownNow();
        super.onDestroy();
    }
}
