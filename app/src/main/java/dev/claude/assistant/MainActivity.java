package dev.claude.assistant;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
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
import dev.claude.assistant.ankai.ConnectionFormPolicy;
import dev.claude.assistant.ankai.ConnectionUiState;
import dev.claude.assistant.ankai.DefaultAssistantSetupPolicy;
import dev.claude.assistant.ankai.PlaybackSettings;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;

public class MainActivity extends Activity {
    private static final int REQUEST_ASSISTANT_ROLE = 1001;
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
    private Button defaultAssistantButton;
    private PlaybackSettings playbackSettings;

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
        defaultAssistantButton.setOnClickListener(view -> openDefaultAssistantSetup());

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
        defaultAssistantButton = findViewById(R.id.default_assistant_setup);
    }

    private void openDefaultAssistantSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && DefaultAssistantSetupPolicy.shouldRequestAssistantRole(
                    Build.VERSION.SDK_INT, roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT))) {
                try {
                    startActivityForResult(
                            roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
                            REQUEST_ASSISTANT_ROLE);
                    return;
                } catch (ActivityNotFoundException | SecurityException unavailable) {
                    // Einige Hersteller melden die Rolle als verfuegbar, bieten aber keinen Dialog an.
                }
            }
        }
        openDefaultAppsSettings();
    }

    private void openDefaultAppsSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (ActivityNotFoundException unavailable) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ASSISTANT_ROLE
                && DefaultAssistantSetupPolicy.shouldOpenSettingsAfterRoleRequest(
                        isDefaultAssistant())) {
            openDefaultAppsSettings();
        }
    }

    private boolean isDefaultAssistant() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        RoleManager roleManager = getSystemService(RoleManager.class);
        return roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
                && roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (defaultAssistantButton != null) {
            boolean selected = isDefaultAssistant();
            defaultAssistantButton.setText(selected
                    ? R.string.default_assistant_active
                    : R.string.default_assistant_setup);
            defaultAssistantButton.setEnabled(!selected);
        }
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
        if (ConnectionFormPolicy.shouldClearPassword(state)) password.setText("");
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
