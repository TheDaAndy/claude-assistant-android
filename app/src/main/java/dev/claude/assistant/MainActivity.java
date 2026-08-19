package dev.claude.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        presenter = new ConnectionPresenter(EncryptedPrefsSecretStore.connectionStore(this));
        PlaybackSettings playbackSettings = EncryptedPrefsSecretStore.playbackSettings(this);
        autoplaySwitch.setChecked(playbackSettings.isAutoplayEnabled());
        autoplaySwitch.setOnCheckedChangeListener((button, enabled) ->
                playbackSettings.setAutoplayEnabled(enabled));

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
}
