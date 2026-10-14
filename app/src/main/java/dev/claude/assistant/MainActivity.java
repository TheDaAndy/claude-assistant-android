package dev.claude.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.Manifest;
import dev.claude.assistant.ankai.AnkaiConnection;
import dev.claude.assistant.remote.DeviceLinkStore;
import dev.claude.assistant.remote.DeviceHttp;
import dev.claude.assistant.remote.DeviceChannelService;
import dev.claude.assistant.remote.DeviceScopeActivity;
import dev.claude.assistant.remote.MaximumControlActivity;
import org.json.JSONObject;
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
    private DeviceLinkStore deviceStore;
    private Button devicePair, deviceRevoke, deviceScope;
    private Switch deviceEnabled;
    private TextView deviceStatus;
    private boolean renderingDevice;
    private boolean deviceBusy;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        deviceStore = new DeviceLinkStore(this);
        devicePair = findViewById(R.id.device_pair);
        deviceRevoke = findViewById(R.id.device_revoke);
        deviceEnabled = findViewById(R.id.device_enabled);
        deviceStatus = findViewById(R.id.device_status);
        devicePair.setOnClickListener(v -> startActivity(new Intent(this, DeviceScopeActivity.class)));
        deviceScope=findViewById(R.id.device_scope);
        deviceScope.setOnClickListener(v -> startActivity(new Intent(this, DeviceScopeActivity.class)));
        findViewById(R.id.device_maximum).setOnClickListener(v -> startActivity(new Intent(this,MaximumControlActivity.class)));
        findViewById(R.id.device_install).setOnClickListener(v -> startActivity(new Intent(this,dev.claude.assistant.remote.InstallControlActivity.class)));
        findViewById(R.id.device_ui).setOnClickListener(v -> startActivity(new Intent(this,dev.claude.assistant.remote.UiControlActivity.class)));
        deviceRevoke.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Gerätekopplung widerrufen?")
                .setMessage("Alle bisherigen Freigaben verlieren den Gerätezugriff. Der Kanal wird sofort pausiert.")
                .setNegativeButton("Abbrechen", null).setPositiveButton("Widerrufen", (d,w) -> revokeDevice()).show());
        deviceEnabled.setOnCheckedChangeListener((v, enabled) -> {
            if (renderingDevice) return;
            if (enabled && !getSystemService(NotificationManager.class).areNotificationsEnabled()) {
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
                deviceStore.status("Benachrichtigungen erlauben, danach erneut aktivieren"); renderDevice(); return;
            }
            if(!enabled)deviceStore.uiResume(false);
            deviceStore.status(enabled?"Verbinde Gerätekanal …":"Pausiert");
            deviceStore.enabled(enabled);
            if (enabled) startForegroundService(new Intent(this, DeviceChannelService.class));
            else stopService(new Intent(this, DeviceChannelService.class));
            renderDevice();
        });
        presenter = new ConnectionPresenter(EncryptedPrefsSecretStore.connectionStore(this));
        playbackSettings = EncryptedPrefsSecretStore.playbackSettings(this);
        autoplaySwitch.setChecked(playbackSettings.isAutoplayEnabled());
        autoplaySwitch.setOnCheckedChangeListener((button, enabled) ->
                playbackSettings.setAutoplayEnabled(enabled));
        defaultAssistantButton.setOnClickListener(view -> openDefaultAssistantSetup());

        connectButton.setOnClickListener(view -> connect());
        disconnectButton.setOnClickListener(view -> {
            if (deviceStore.id() != null) {
                connectionError.setText("Bitte zuerst die Gerätekopplung widerrufen.");
                connectionError.setVisibility(View.VISIBLE); return;
            }
            runNetwork(presenter::disconnect);
        });
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
        if (deviceStore != null) {
            boolean active = false;
            android.app.ActivityManager manager = getSystemService(android.app.ActivityManager.class);
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(50)) {
                if (DeviceChannelService.class.getName().equals(service.service.getClassName())) active = true;
            }
            if (deviceStore.enabled() && !active) {
                deviceStore.uiResume(false); deviceStore.enabled(false); deviceStore.status("Pausiert · nach Prozessende wieder aktivieren");
            }
            renderDevice();
        }
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
        renderDevice();
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
                android.R.layout.simple_spinner_item, choices) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) ((TextView) view).setTextColor(getColor(R.color.text_primary));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        projectSpinner.setAdapter(adapter);
        projectSpinner.setSelection(selectedPosition, false);
    }

    private void renderDevice() {
        if (deviceStore == null) return;
        renderingDevice = true;
        boolean paired = deviceStore.id() != null;
        AnkaiConnection connection = EncryptedPrefsSecretStore.connectionStore(this).load();
        deviceStatus.setText(paired ? "Gekoppelt: " + deviceStore.scopeLabel() + "\n" + deviceStore.status() : "Nicht gekoppelt · Freigabe unabhängig vom Sprach-Standardprojekt");
        devicePair.setEnabled(!deviceBusy && !paired && connection != null);
        devicePair.setVisibility(paired ? View.GONE : View.VISIBLE);
        findViewById(R.id.device_install).setOnClickListener(v -> startActivity(new Intent(this,dev.claude.assistant.remote.InstallControlActivity.class)));
        findViewById(R.id.device_ui).setEnabled(paired&&!deviceBusy);
        findViewById(R.id.device_maximum).setVisibility(paired?View.VISIBLE:View.GONE);
        deviceScope.setVisibility(paired ? View.VISIBLE : View.GONE);
        deviceScope.setEnabled(!deviceBusy);
        deviceRevoke.setVisibility(paired ? View.VISIBLE : View.GONE);
        deviceRevoke.setEnabled(!deviceBusy);
        deviceEnabled.setEnabled(!deviceBusy && paired);
        deviceEnabled.setChecked(paired && deviceStore.enabled());
        renderingDevice = false;
    }

    private void revokeDevice() {
        deviceStore.uiResume(false); deviceStore.enabled(false);
        stopService(new Intent(this, DeviceChannelService.class));
        deviceBusy = true; renderDevice();
        networkExecutor.execute(() -> {
            String error = null;
            try { new DeviceHttp().channel(deviceStore, "", "DELETE", null); deviceStore.clear(); }
            catch (DeviceHttp.Failure e) { if (e.status == 401 || e.status == 404) deviceStore.clear(); else error = "Widerruf fehlgeschlagen · lokal pausiert, bitte erneut versuchen."; }
            catch (Exception e) { error = "Offline · lokal pausiert. Server-Widerruf bitte erneut versuchen."; }
            final String message = error;
            runOnUiThread(() -> { deviceBusy = false; if (!isDestroyed()) { renderDevice(); connectionError.setText(message == null ? "" : message); connectionError.setVisibility(message == null ? View.GONE : View.VISIBLE); } });
        });
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
