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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.claude.assistant.ankai.VoiceSubmission;
import dev.claude.assistant.ankai.AssistantInputPolicy;
import dev.claude.assistant.ankai.TextSubmission;
import dev.claude.assistant.ankai.VoiceUiFormatter;
import dev.claude.assistant.ankai.AnkaiProject;
import dev.claude.assistant.ankai.AnkaiRoutingException;
import dev.claude.assistant.ankai.LiveRunSnapshot;
import dev.claude.assistant.ankai.LiveRunSpeechController;
import dev.claude.assistant.ankai.LiveRunState;
import dev.claude.assistant.ankai.LiveRunSubscription;
import dev.claude.assistant.ankai.PlaybackSettings;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;

public class AssistActivity extends Activity {
    private static final int RECORD_AUDIO_REQUEST_CODE = 101;

    private EditText inputField;
    private TextView responseView;
    private MaterialButton actionButton;
    private ProgressBar progressBar;

    private BroadcastReceiver responseReceiver;
    private final ExecutorService voiceExecutor = Executors.newSingleThreadExecutor();
    private MediaRecorder recorder;
    private File recordingFile;
    private boolean recording;
    private LiveRunState observedRun;
    private LiveRunSubscription liveRunSubscription;
    private LiveRunSpeechController speechController;
    private AndroidServerSpeechPlayback speechPlayback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Window flags for overlay style
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_assist);
        setFinishOnTouchOutside(true);
        findViewById(R.id.assist_scrim).setOnClickListener(v -> finish());
        findViewById(R.id.assist_panel).setOnClickListener(v -> {
            // Klicks innerhalb des Assistant-Panels duerfen die Activity nicht schliessen.
        });

        inputField = findViewById(R.id.input_field);
        responseView = findViewById(R.id.response_view);
        actionButton = findViewById(R.id.action_button);
        progressBar = findViewById(R.id.progress_bar);

        actionButton.setOnClickListener(v -> performPrimaryAction());
        inputField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePrimaryAction();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        updatePrimaryAction();

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

    private void performPrimaryAction() {
        if (AssistantInputPolicy.action(recording, inputField.getText().toString())
                == AssistantInputPolicy.Action.SUBMIT) {
            if (!recording) {
                sendText();
                return;
            }
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
            updatePrimaryAction();
            responseView.setText(getString(R.string.status_recording));
            inputField.setEnabled(false);
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
        updatePrimaryAction();
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
                observeLatestRun();
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
        updatePrimaryAction();
        setVoiceBusy(false);
        responseView.setText(VoiceUiFormatter.error(error));
    }

    private void setVoiceBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        actionButton.setEnabled(!busy);
        inputField.setEnabled(!busy);
    }

    private void updatePrimaryAction() {
        if (actionButton == null || inputField == null) return;
        boolean submit = AssistantInputPolicy.action(recording, inputField.getText().toString())
                == AssistantInputPolicy.Action.SUBMIT;
        actionButton.setIconResource(submit ? R.drawable.ic_send : R.drawable.ic_mic);
        if (submit) actionButton.setText(R.string.btn_submit);
        else actionButton.setText("");
        actionButton.setContentDescription(getString(
                submit ? R.string.btn_submit : R.string.btn_mic_desc));
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
        actionButton.setEnabled(false);

        inputField.setEnabled(false);
        voiceExecutor.execute(() -> {
            try {
                new TextSubmission(EncryptedPrefsSecretStore.connectionStore(getApplicationContext()),
                        LiveRunRuntime.coordinator(getApplicationContext())).submit(prompt);
                startLiveRunService();
                runOnUiThread(() -> {
                    setVoiceBusy(false);
                    observeLatestRun();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> displayVoiceError(error));
            }
        });
    }

    private void displayResponse(String response, int exitCode) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            actionButton.setEnabled(true);

            if (exitCode == 0 && response != null && !response.isEmpty()) {
                responseView.setText(response);
            } else {
                responseView.setText(getString(R.string.error_no_response) +
                    (response != null ? ": " + response : ""));
            }
        });
    }

    private void observeLatestRun() {
        closeLiveRunUi(false);
        observedRun = LiveRunRuntime.coordinator(getApplicationContext()).registry().latest();
        if (observedRun == null) return;
        observedRun.attachOverlay();
        liveRunSubscription = observedRun.observe(this::displayLiveRun);
        PlaybackSettings playbackSettings = EncryptedPrefsSecretStore.playbackSettings(this);
        if (playbackSettings.isAutoplayEnabled()) {
            speechPlayback = new AndroidServerSpeechPlayback(getApplicationContext());
            speechController = new LiveRunSpeechController(observedRun, speechPlayback);
        }
    }

    private void displayLiveRun(LiveRunSnapshot snapshot) {
        runOnUiThread(() -> {
            String text = snapshot.text();
            responseView.setText(text == null || text.isEmpty()
                    ? getString(R.string.status_thinking)
                    : text);
        });
    }

    private void closeLiveRunUi(boolean closeOverlay) {
        if (liveRunSubscription != null) liveRunSubscription.close();
        if (closeOverlay && observedRun != null) observedRun.closeOverlay();
        if (speechController != null) speechController.close();
        if (speechPlayback != null) speechPlayback.shutdown();
        liveRunSubscription = null;
        speechController = null;
        speechPlayback = null;
        observedRun = null;
    }

    @Override
    protected void onDestroy() {
        closeLiveRunUi(true);
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
