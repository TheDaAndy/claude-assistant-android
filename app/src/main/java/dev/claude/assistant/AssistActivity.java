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
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

public class AssistActivity extends Activity {
    private static final int SPEECH_REQUEST_CODE = 100;

    private EditText inputField;
    private TextView responseView;
    private ImageButton micButton;
    private ImageButton sendButton;
    private ProgressBar progressBar;

    private BroadcastReceiver responseReceiver;

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

        micButton.setOnClickListener(v -> startSpeechRecognition());
        sendButton.setOnClickListener(v -> sendToClaudeCode());

        // Auto-start speech recognition when opened via gesture
        if (getIntent().getAction() != null &&
            getIntent().getAction().equals(Intent.ACTION_ASSIST)) {
            startSpeechRecognition();
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

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.status_ready));
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    inputField.setText(results.get(0));
                    sendToClaudeCode();
                }
            } else {
                // Speech input cancelled - still allow text input
                responseView.setText(getString(R.string.speech_cancelled));
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void sendToClaudeCode() {
        String prompt = inputField.getText().toString().trim();
        if (prompt.isEmpty()) return;

        progressBar.setVisibility(View.VISIBLE);
        responseView.setText(getString(R.string.status_thinking));
        micButton.setEnabled(false);
        sendButton.setEnabled(false);

        TermuxBridge.executeClaudeCode(this, prompt);
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
        super.onDestroy();
        if (responseReceiver != null) {
            unregisterReceiver(responseReceiver);
        }
    }
}
