package dev.claude.assistant;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;

import dev.claude.assistant.ankai.LiveRunSnapshot;
import dev.claude.assistant.ankai.LiveRunState;
import dev.claude.assistant.ankai.LiveRunSubscription;

public class AssistantDialogActivity extends Activity {
    private static final int SPEECH_REQUEST_CODE = 100;

    private EditText inputField;
    private TextView responseView;
    private ImageButton micButton;
    private ImageButton sendButton;

    private BroadcastReceiver responseReceiver;
    private LiveRunState observedRun;
    private LiveRunSubscription liveRunSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant_dialog);

        inputField = findViewById(R.id.input_field);
        responseView = findViewById(R.id.response_view);
        micButton = findViewById(R.id.mic_button);
        sendButton = findViewById(R.id.send_button);

        micButton.setOnClickListener(v -> startSpeechRecognition());
        sendButton.setOnClickListener(v -> sendToClaudeCode());

        setupResponseReceiver();
        observeLatestRun();
    }

    private void observeLatestRun() {
        observedRun = LiveRunRuntime.coordinator(getApplicationContext()).registry().latest();
        if (observedRun == null) return;
        observedRun.attachOverlay();
        liveRunSubscription = observedRun.observe(this::displayLiveRun);
    }

    private void displayLiveRun(LiveRunSnapshot snapshot) {
        runOnUiThread(() -> {
            String text = snapshot.text();
            if (text == null || text.isEmpty()) {
                responseView.setText(getString(R.string.status_thinking));
            } else {
                responseView.setText(text);
            }
        });
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

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.status_ready));
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> results = data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                inputField.setText(results.get(0));
                sendToClaudeCode();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void sendToClaudeCode() {
        String prompt = inputField.getText().toString().trim();
        if (prompt.isEmpty()) return;

        responseView.setText(getString(R.string.status_thinking));
        micButton.setEnabled(false);
        sendButton.setEnabled(false);

        TermuxBridge.executeClaudeCode(this, prompt);
    }

    private void displayResponse(String response, int exitCode) {
        runOnUiThread(() -> {
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
        if (liveRunSubscription != null) liveRunSubscription.close();
        if (observedRun != null) observedRun.closeOverlay();
        if (responseReceiver != null) {
            unregisterReceiver(responseReceiver);
        }
        super.onDestroy();
    }
}
