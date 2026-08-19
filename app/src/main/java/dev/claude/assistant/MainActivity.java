package dev.claude.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.claude.assistant.ankai.AnkaiProject;
import dev.claude.assistant.ankai.ConnectionPresenter;
import dev.claude.assistant.ankai.ConnectionUiState;
import dev.claude.assistant.ankai.PlaybackSettings;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;

public class MainActivity extends Activity {
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private ConnectionPresenter presenter;
    private EditText instanceUrl;
    private EditText username;
    private EditText password;
    private Button connectButton;
    private Button disconnectButton;
    private Spinner projectSpinner;
    private TextView connectionStatus;
    private TextView connectionError;
    private ProgressBar connectionProgress;
    private Switch autoplaySwitch;
    private Spinner engineSpinner;
    private Spinner voiceSpinner;
    private PlaybackSettings playbackSettings;
    private TextToSpeech ttsProbe;
    private int ttsProbeGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        presenter = new ConnectionPresenter(EncryptedPrefsSecretStore.connectionStore(this));
        playbackSettings = EncryptedPrefsSecretStore.playbackSettings(this);
        autoplaySwitch.setChecked(playbackSettings.isAutoplayEnabled());
        autoplaySwitch.setOnCheckedChangeListener((button, enabled) ->
                playbackSettings.setAutoplayEnabled(enabled));
        loadSpeechEngines();

        connectButton.setOnClickListener(view -> connect());
        disconnectButton.setOnClickListener(view -> runNetwork(presenter::disconnect));
        projectSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            Object selected = projectSpinner.getItemAtPosition(position);
            if (selected instanceof ProjectChoice) {
                ProjectChoice choice = (ProjectChoice) selected;
                if (!choice.projectIdEquals(presenter.state().defaultProjectId)) {
                    render(presenter.selectDefaultProject(choice.projectId));
                }
            }
        }));

        setBusy(true);
        runNetwork(presenter::refresh);
    }

    private void bindViews() {
        instanceUrl = findViewById(R.id.ankai_instance_url);
        username = findViewById(R.id.ankai_username);
        password = findViewById(R.id.ankai_password);
        connectButton = findViewById(R.id.ankai_connect);
        disconnectButton = findViewById(R.id.ankai_disconnect);
        projectSpinner = findViewById(R.id.ankai_default_project);
        connectionStatus = findViewById(R.id.ankai_connection_status);
        connectionError = findViewById(R.id.ankai_connection_error);
        connectionProgress = findViewById(R.id.ankai_connection_progress);
        autoplaySwitch = findViewById(R.id.ankai_autoplay);
        engineSpinner = findViewById(R.id.playback_engine);
        voiceSpinner = findViewById(R.id.playback_voice);
    }

    private void loadSpeechEngines() {
        engineSpinner.setEnabled(false);
        voiceSpinner.setEnabled(false);
        if (ttsProbe != null) ttsProbe.shutdown();
        int generation = ++ttsProbeGeneration;
        String enginePackage = playbackSettings.getEnginePackage();
        ttsProbe = enginePackage == null
                ? new TextToSpeech(getApplicationContext(), status ->
                        runOnUiThread(() -> renderSpeechEngines(status, generation)))
                : new TextToSpeech(getApplicationContext(), status ->
                        runOnUiThread(() -> renderSpeechEngines(status, generation)), enginePackage);
    }

    private void renderSpeechEngines(int status, int generation) {
        if (generation != ttsProbeGeneration || isFinishing() || isDestroyed()) return;
        if (status != TextToSpeech.SUCCESS && playbackSettings.getEnginePackage() != null) {
            playbackSettings.setEnginePackage(null);
            loadSpeechEngines();
            return;
        }
        List<EngineChoice> choices = new ArrayList<>();
        choices.add(new EngineChoice(null, getString(R.string.playback_engine_system)));
        if (status == TextToSpeech.SUCCESS && ttsProbe != null) {
            List<TextToSpeech.EngineInfo> engines = new ArrayList<>(ttsProbe.getEngines());
            engines.sort(Comparator.comparing(this::engineLabel,
                    String.CASE_INSENSITIVE_ORDER));
            for (TextToSpeech.EngineInfo engine : engines) {
                choices.add(new EngineChoice(engine.name, engineLabel(engine)));
            }
        }

        String selectedPackage = playbackSettings.getEnginePackage();
        int selectedPosition = 0;
        for (int index = 1; index < choices.size(); index++) {
            if (choices.get(index).packageEquals(selectedPackage)) selectedPosition = index;
        }
        if (selectedPackage != null && selectedPosition == 0 && status == TextToSpeech.SUCCESS) {
            playbackSettings.setEnginePackage(null);
        }
        ArrayAdapter<EngineChoice> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        engineSpinner.setAdapter(adapter);
        engineSpinner.setSelection(selectedPosition, false);
        engineSpinner.setEnabled(status == TextToSpeech.SUCCESS);
        engineSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            Object selected = engineSpinner.getItemAtPosition(position);
            if (selected instanceof EngineChoice) {
                String packageName = ((EngineChoice) selected).packageName;
                String previous = playbackSettings.getEnginePackage();
                if (previous == null ? packageName != null : !previous.equals(packageName)) {
                    playbackSettings.setEnginePackage(packageName);
                    loadSpeechEngines();
                }
            }
        }));
        renderVoices(status);
    }

    private void renderVoices(int status) {
        List<VoiceChoice> choices = new ArrayList<>();
        choices.add(new VoiceChoice(null, getString(R.string.playback_voice_default)));
        if (status == TextToSpeech.SUCCESS && ttsProbe != null && ttsProbe.getVoices() != null) {
            List<Voice> voices = new ArrayList<>(ttsProbe.getVoices());
            voices.sort(Comparator.comparing(this::voiceLabel, String.CASE_INSENSITIVE_ORDER));
            for (Voice voice : voices) choices.add(new VoiceChoice(voice.getName(), voiceLabel(voice)));
        }
        String selectedName = playbackSettings.getVoiceName();
        int selectedPosition = 0;
        for (int index = 1; index < choices.size(); index++) {
            if (choices.get(index).nameEquals(selectedName)) selectedPosition = index;
        }
        if (selectedName != null && selectedPosition == 0 && status == TextToSpeech.SUCCESS) {
            playbackSettings.setVoiceName(null);
        }
        ArrayAdapter<VoiceChoice> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(adapter);
        voiceSpinner.setSelection(selectedPosition, false);
        voiceSpinner.setEnabled(status == TextToSpeech.SUCCESS);
        voiceSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            Object selected = voiceSpinner.getItemAtPosition(position);
            if (selected instanceof VoiceChoice) {
                playbackSettings.setVoiceName(((VoiceChoice) selected).name);
            }
        }));
    }

    private String voiceLabel(Voice voice) {
        String availability = voice.isNetworkConnectionRequired()
                ? getString(R.string.playback_voice_network)
                : getString(R.string.playback_voice_offline);
        return voice.getName() + " · " + voice.getLocale().toLanguageTag() + " · " + availability;
    }

    private String engineLabel(TextToSpeech.EngineInfo engine) {
        return engine.label == null || engine.label.toString().trim().isEmpty()
                ? engine.name : engine.label.toString();
    }

    private void connect() {
        String url = instanceUrl.getText().toString();
        String user = username.getText().toString();
        String secret = password.getText().toString();
        runNetwork(() -> presenter.connect(url, user, secret));
    }

    private void runNetwork(StateOperation operation) {
        setBusy(true);
        networkExecutor.execute(() -> {
            ConnectionUiState state = operation.run();
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) render(state);
            });
        });
    }

    private void setBusy(boolean busy) {
        connectionProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        connectButton.setEnabled(!busy);
        disconnectButton.setEnabled(!busy);
        projectSpinner.setEnabled(!busy);
    }

    private void render(ConnectionUiState state) {
        setBusy(false);
        connectionError.setText(state.error == null ? "" : state.error);
        connectionError.setVisibility(state.error == null ? View.GONE : View.VISIBLE);

        int loginVisibility = state.connected ? View.GONE : View.VISIBLE;
        instanceUrl.setVisibility(loginVisibility);
        username.setVisibility(loginVisibility);
        password.setVisibility(loginVisibility);
        connectButton.setVisibility(loginVisibility);
        disconnectButton.setVisibility(state.connected ? View.VISIBLE : View.GONE);
        projectSpinner.setVisibility(state.connected ? View.VISIBLE : View.GONE);
        findViewById(R.id.ankai_default_project_label)
                .setVisibility(state.connected ? View.VISIBLE : View.GONE);

        if (!state.connected) {
            connectionStatus.setText(R.string.ankai_not_connected);
            password.setText("");
            return;
        }

        connectionStatus.setText(getString(R.string.ankai_connected_as, state.username, state.baseUrl));
        List<ProjectChoice> choices = new ArrayList<>();
        choices.add(new ProjectChoice(null, getString(R.string.ankai_no_default_project)));
        int selectedPosition = 0;
        for (AnkaiProject project : state.projects) {
            choices.add(new ProjectChoice(project.id, project.name));
            if (project.id.equals(state.defaultProjectId)) selectedPosition = choices.size() - 1;
        }
        ArrayAdapter<ProjectChoice> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        projectSpinner.setAdapter(adapter);
        projectSpinner.setSelection(selectedPosition, false);
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        if (ttsProbe != null) ttsProbe.shutdown();
        super.onDestroy();
    }

    private interface StateOperation {
        ConnectionUiState run();
    }

    private static final class ProjectChoice {
        final String projectId;
        final String label;

        ProjectChoice(String projectId, String label) {
            this.projectId = projectId;
            this.label = label;
        }

        boolean projectIdEquals(String other) {
            return projectId == null ? other == null : projectId.equals(other);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class EngineChoice {
        final String packageName;
        final String label;

        EngineChoice(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }

        boolean packageEquals(String other) {
            return packageName == null ? other == null : packageName.equals(other);
        }

        @Override public String toString() { return label; }
    }

    private static final class VoiceChoice {
        final String name;
        final String label;

        VoiceChoice(String name, String label) {
            this.name = name;
            this.label = label;
        }

        boolean nameEquals(String other) {
            return name == null ? other == null : name.equals(other);
        }

        @Override public String toString() { return label; }
    }
}
