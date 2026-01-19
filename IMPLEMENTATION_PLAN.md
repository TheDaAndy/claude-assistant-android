# Claude Assistant Android App - Implementation Plan

## Overview

Eine Android-App, die als persönlicher KI-Assistent fungiert und Claude Code über Termux ausführt. Die App kann über verschiedene Trigger aktiviert werden und nutzt Termux:API für bidirektionale Kommunikation.

## Wichtige Erkenntnisse aus der Recherche

### Lösung: ACTION_ASSIST Intent (wie Tasker/AutoVoice)

Es gibt **ZWEI** verschiedene Wege, als Assistant zu funktionieren:

1. **VoiceInteractionService** (NICHT nutzbar)
   - Erfordert `BIND_VOICE_INTERACTION` (System-Signatur-Berechtigung)
   - Nur für vorinstallierte System-Apps

2. **ACTION_ASSIST Intent-Filter** (NUTZBAR!)
   - Einfacher Intent-Filter in der Activity
   - App erscheint in **Einstellungen > Apps > Standard-Apps > Digitaler Assistent**
   - Benutzer kann App als Standard-Assistent auswählen
   - **So funktionieren Tasker und AutoVoice!**

```xml
<activity android:name=".AssistActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.ASSIST" />
        <category android:name="android.intent.category.DEFAULT"/>
    </intent-filter>
</activity>
```

### Zusätzliche Aktivierungsmethoden
1. **Quick Settings Tile** - Schnellzugriff aus der Benachrichtigungsleiste
2. **Floating Widget** - Schwebendes Overlay-Icon
3. **App Shortcut** - Startbildschirm-Verknüpfung

### Termux Integration
- Termux bietet `RUN_COMMAND` Intent seit Version 0.95
- Berechtigung: `com.termux.permission.RUN_COMMAND`
- Voraussetzung: `allow-external-apps = true` in `~/.termux/termux.properties`
- Rückgabe von Ergebnissen möglich ab Termux 0.109

## Architektur

```
┌─────────────────────────────────────────────────────────────┐
│                    Claude Assistant App                      │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ Quick Tile   │  │ Float Widget │  │ Accessibility│       │
│  │ Trigger      │  │ Trigger      │  │ Trigger      │       │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘       │
│         │                 │                 │               │
│         └─────────────────┼─────────────────┘               │
│                           ▼                                 │
│                 ┌──────────────────┐                        │
│                 │ AssistantService │                        │
│                 │ (Foreground)     │                        │
│                 └────────┬─────────┘                        │
│                          │                                  │
│         ┌────────────────┼────────────────┐                 │
│         ▼                ▼                ▼                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Speech-to-  │  │ Text Input  │  │ Response    │         │
│  │ Text (STT)  │  │ Dialog      │  │ Display     │         │
│  └──────┬──────┘  └──────┬──────┘  └──────▲──────┘         │
│         │                │                │                 │
│         └────────────────┼────────────────┘                 │
│                          ▼                                  │
│                ┌───────────────────┐                        │
│                │ TermuxBridge      │                        │
│                │ (RUN_COMMAND)     │                        │
│                └─────────┬─────────┘                        │
└──────────────────────────┼──────────────────────────────────┘
                           │
                           ▼ Intent
┌──────────────────────────────────────────────────────────────┐
│                         Termux                               │
├──────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────┐  │
│  │ RunCommandService                                      │  │
│  │ - Empfängt RUN_COMMAND Intent                          │  │
│  │ - Führt Claude Code CLI aus                            │  │
│  │ - Gibt Ergebnis zurück via PendingIntent               │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Termux:API                                             │  │
│  │ - termux-notification (Antwort anzeigen)               │  │
│  │ - termux-tts-speak (Antwort vorlesen)                  │  │
│  │ - termux-toast (Kurznachrichten)                       │  │
│  │ - termux-dialog (Interaktive Eingabe)                  │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

## Was wir NICHT implementieren

- VoiceInteractionService mit AssistStructure-Zugriff (System-Berechtigung erforderlich)
- Hotword Detection ("Hey Claude") - erfordert spezielle Hardware-APIs
- Eigene Speech-to-Text Engine (nutzen Android's eingebaute)

## Was wir IMPLEMENTIEREN

- **Standard-Assistent-Funktion** via ACTION_ASSIST (wie Tasker!)
- Home-Button lange drücken → Claude Assistant öffnet sich
- Spracheingabe → Claude Code → Sprachausgabe

## Implementierungsphasen

---

## Phase 1: Projekt-Setup & Grundstruktur

### Overview
Gradle-Projekt mit Android-Konfiguration erstellen, das auf Termux ohne Android Studio gebaut werden kann.

### Änderungen:

#### 1. Build-Konfiguration
**File**: `build.gradle`
```groovy
plugins {
    id 'com.android.application'
}

android {
    namespace 'dev.claude.assistant'
    compileSdk 34

    defaultConfig {
        applicationId "dev.claude.assistant"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
        debug {
            debuggable true
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}
```

#### 2. Android Manifest
**File**: `app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="dev.claude.assistant">

    <!-- Berechtigungen -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="com.termux.permission.RUN_COMMAND" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.ClaudeAssistant">

        <!-- Main Activity (Launcher) -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- WICHTIG: Assist Activity - ermöglicht Registrierung als Standard-Assistent -->
        <activity
            android:name=".AssistActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:theme="@style/Theme.ClaudeAssistant.Transparent"
            android:excludeFromRecents="true">
            <intent-filter>
                <action android:name="android.intent.action.ASSIST" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <meta-data
                android:name="com.android.systemui.action_assist_icon"
                android:resource="@drawable/ic_assistant" />
        </activity>

        <!-- Quick Settings Tile -->
        <service
            android:name=".AssistantTileService"
            android:icon="@drawable/ic_assistant"
            android:label="@string/tile_label"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>

        <!-- Foreground Service -->
        <service
            android:name=".AssistantService"
            android:foregroundServiceType="specialUse"
            android:exported="false" />

        <!-- Result Receiver -->
        <receiver
            android:name=".TermuxResultReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="dev.claude.assistant.TERMUX_RESULT" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

### Success Criteria:

#### Automated Verification:
- [ ] Projekt kompiliert: `./gradlew assembleDebug`
- [ ] APK wird erstellt in `app/build/outputs/apk/debug/`

#### Manual Verification:
- [ ] APK kann auf Gerät installiert werden
- [ ] App startet ohne Crash

---

## Phase 2: Assist Activity (Kern-Feature)

### Overview
Die AssistActivity ist das Herzstück - sie wird aufgerufen, wenn der Benutzer die Assistant-Geste macht (lange Home-Taste) und unsere App als Standard-Assistent eingestellt ist.

### Änderungen:

#### 1. Assist Activity
**File**: `app/src/main/java/dev/claude/assistant/AssistActivity.java`
```java
package dev.claude.assistant;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ProgressBar;
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

        // Fenster-Flags für Overlay-Stil
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

        // Sofort Spracheingabe starten wenn per Geste geöffnet
        if (getIntent().getAction() != null &&
            getIntent().getAction().equals(Intent.ACTION_ASSIST)) {
            startSpeechRecognition();
        }

        // Response Receiver registrieren
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
        registerReceiver(responseReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Was kann ich für dich tun?");
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
                // Spracheingabe abgebrochen - trotzdem offen lassen für Texteingabe
                responseView.setText("Spracheingabe abgebrochen. Du kannst auch tippen.");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void sendToClaudeCode() {
        String prompt = inputField.getText().toString().trim();
        if (prompt.isEmpty()) return;

        progressBar.setVisibility(android.view.View.VISIBLE);
        responseView.setText("Claude denkt nach...");
        micButton.setEnabled(false);
        sendButton.setEnabled(false);

        TermuxBridge.executeClaudeCode(this, prompt);
    }

    private void displayResponse(String response, int exitCode) {
        runOnUiThread(() -> {
            progressBar.setVisibility(android.view.View.GONE);
            micButton.setEnabled(true);
            sendButton.setEnabled(true);

            if (exitCode == 0 && response != null && !response.isEmpty()) {
                responseView.setText(response);
            } else {
                responseView.setText("Fehler: " + (response != null ? response : "Keine Antwort"));
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
```

#### 2. Assist Layout
**File**: `app/src/main/res/layout/activity_assist.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="@drawable/assist_background"
    android:elevation="8dp">

    <!-- Header -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="12dp">

        <ImageView
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:src="@drawable/ic_assistant"
            android:layout_marginEnd="8dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Claude Assistant"
            android:textSize="18sp"
            android:textStyle="bold" />

        <ProgressBar
            android:id="@+id/progress_bar"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:layout_marginStart="8dp"
            android:visibility="gone" />
    </LinearLayout>

    <!-- Response Area -->
    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:minHeight="100dp"
        android:maxHeight="250dp"
        android:layout_marginBottom="12dp">

        <TextView
            android:id="@+id/response_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Wie kann ich dir helfen?"
            android:textSize="16sp"
            android:padding="8dp"
            android:background="@drawable/response_background" />
    </ScrollView>

    <!-- Input Area -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <EditText
            android:id="@+id/input_field"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Nachricht eingeben..."
            android:inputType="text"
            android:maxLines="3"
            android:background="@drawable/input_background"
            android:padding="12dp" />

        <ImageButton
            android:id="@+id/mic_button"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_marginStart="8dp"
            android:src="@drawable/ic_mic"
            android:background="@drawable/button_background"
            android:contentDescription="Spracheingabe" />

        <ImageButton
            android:id="@+id/send_button"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_marginStart="8dp"
            android:src="@drawable/ic_send"
            android:background="@drawable/button_background"
            android:contentDescription="Senden" />
    </LinearLayout>
</LinearLayout>
```

#### 3. Transparentes Theme
**File**: `app/src/main/res/values/themes.xml` (hinzufügen)
```xml
<style name="Theme.ClaudeAssistant.Transparent" parent="Theme.ClaudeAssistant">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:backgroundDimEnabled">true</item>
    <item name="android:windowAnimationStyle">@android:style/Animation.Dialog</item>
</style>
```

### Success Criteria:

#### Automated Verification:
- [ ] Kompiliert ohne Fehler: `./gradlew assembleDebug`

#### Manual Verification:
- [ ] App erscheint in Einstellungen > Apps > Standard-Apps > Digitaler Assistent
- [ ] App kann als Standard-Assistent ausgewählt werden
- [ ] Lange Home-Taste (oder Geste) öffnet Claude Assistant
- [ ] Spracheingabe startet automatisch
- [ ] Eingabe wird an Claude Code gesendet
- [ ] Antwort wird angezeigt

---

## Phase 3: Quick Settings Tile Implementation

### Overview
Quick Settings Tile für schnellen Zugriff aus der Benachrichtigungsleiste.

### Änderungen:

#### 1. Tile Service
**File**: `app/src/main/java/dev/claude/assistant/AssistantTileService.java`
```java
package dev.claude.assistant;

import android.content.Intent;
import android.service.quicksettings.TileService;

public class AssistantTileService extends TileService {

    @Override
    public void onClick() {
        // Starte Assistant Dialog
        Intent intent = new Intent(this, AssistantDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(intent);
    }

    @Override
    public void onStartListening() {
        // Tile sichtbar - Status aktualisieren
    }

    @Override
    public void onStopListening() {
        // Tile nicht mehr sichtbar
    }
}
```

#### 2. Dialog Activity (Overlay)
**File**: `app/src/main/java/dev/claude/assistant/AssistantDialogActivity.java`
```java
package dev.claude.assistant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import java.util.ArrayList;

public class AssistantDialogActivity extends Activity {
    private static final int SPEECH_REQUEST_CODE = 100;
    private EditText inputField;
    private TextView responseView;
    private ImageButton micButton;
    private ImageButton sendButton;

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
    }

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich deinen Befehl...");
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

        responseView.setText("Verarbeite...");
        TermuxBridge.executeClaudeCode(this, prompt);
    }

    public void displayResponse(String response) {
        runOnUiThread(() -> responseView.setText(response));
    }
}
```

### Success Criteria:

#### Automated Verification:
- [ ] Kompiliert ohne Fehler: `./gradlew assembleDebug`

#### Manual Verification:
- [ ] Tile erscheint in Quick Settings
- [ ] Klick auf Tile öffnet Dialog
- [ ] Spracheingabe funktioniert

---

## Phase 3: Termux Integration

### Overview
Bidirektionale Kommunikation mit Termux über RUN_COMMAND Intent.

### Änderungen:

#### 1. Termux Bridge
**File**: `app/src/main/java/dev/claude/assistant/TermuxBridge.java`
```java
package dev.claude.assistant;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class TermuxBridge {

    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String RUN_COMMAND_SERVICE =
        "com.termux.app.RunCommandService";
    private static final String ACTION_RUN_COMMAND =
        "com.termux.RUN_COMMAND";

    public static void executeClaudeCode(Context context, String prompt) {
        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE);
        intent.setAction(ACTION_RUN_COMMAND);

        // Befehl: Claude Code mit Prompt ausführen
        String command = "/data/data/com.termux/files/usr/bin/claude";

        intent.putExtra("com.termux.RUN_COMMAND_PATH", command);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
            new String[]{"-p", prompt, "--output-format", "text"});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR",
            "/data/data/com.termux/files/home");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");

        // PendingIntent für Ergebnis-Callback
        Intent resultIntent = new Intent(context, TermuxResultReceiver.class);
        resultIntent.setAction("dev.claude.assistant.TERMUX_RESULT");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 0, resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        Bundle resultConfig = new Bundle();
        resultConfig.putParcelable("com.termux.RUN_COMMAND_PENDING_INTENT",
            pendingIntent);
        intent.putExtra("com.termux.RUN_COMMAND_RESULT_BUNDLE", resultConfig);

        try {
            context.startService(intent);
        } catch (Exception e) {
            // Fallback: Termux App öffnen
            openTermuxWithCommand(context, prompt);
        }
    }

    private static void openTermuxWithCommand(Context context, String prompt) {
        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE,
            "com.termux.app.TermuxActivity");
        intent.setAction(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
```

#### 2. Result Receiver
**File**: `app/src/main/java/dev/claude/assistant/TermuxResultReceiver.java`
```java
package dev.claude.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class TermuxResultReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle resultBundle = intent.getBundleExtra(
            "com.termux.RUN_COMMAND_RESULT_BUNDLE");

        if (resultBundle != null) {
            String stdout = resultBundle.getString("stdout", "");
            String stderr = resultBundle.getString("stderr", "");
            int exitCode = resultBundle.getInt("exitCode", -1);

            String response = stdout.isEmpty() ? stderr : stdout;

            // Broadcast an aktive Activity
            Intent responseIntent = new Intent(
                "dev.claude.assistant.RESPONSE_RECEIVED");
            responseIntent.putExtra("response", response);
            responseIntent.putExtra("exitCode", exitCode);
            context.sendBroadcast(responseIntent);

            // Optional: TTS Ausgabe über Termux:API
            if (!response.isEmpty() && exitCode == 0) {
                speakResponse(context, response);
            }
        }
    }

    private void speakResponse(Context context, String text) {
        // Nutze termux-tts-speak für Sprachausgabe
        Intent ttsIntent = new Intent();
        ttsIntent.setClassName("com.termux",
            "com.termux.app.RunCommandService");
        ttsIntent.setAction("com.termux.RUN_COMMAND");
        ttsIntent.putExtra("com.termux.RUN_COMMAND_PATH",
            "/data/data/com.termux/files/usr/bin/termux-tts-speak");
        ttsIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
            new String[]{text});
        ttsIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);

        try {
            context.startService(ttsIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### Success Criteria:

#### Automated Verification:
- [ ] Kompiliert ohne Fehler
- [ ] APK enthält alle Klassen

#### Manual Verification:
- [ ] Termux `allow-external-apps = true` gesetzt
- [ ] Prompt wird an Claude Code gesendet
- [ ] Antwort wird empfangen und angezeigt
- [ ] TTS-Ausgabe funktioniert

---

## Phase 4: Floating Widget (Optional)

### Overview
Schwebendes Overlay-Icon für schnellen Zugriff von überall.

### Änderungen:

#### 1. Floating Service
**File**: `app/src/main/java/dev/claude/assistant/FloatingWidgetService.java`
```java
package dev.claude.assistant;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

public class FloatingWidgetService extends Service {
    private WindowManager windowManager;
    private View floatingView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        floatingView = LayoutInflater.from(this)
            .inflate(R.layout.floating_widget, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(floatingView, params);

        ImageView fabIcon = floatingView.findViewById(R.id.fab_icon);
        fabIcon.setOnClickListener(v -> openAssistantDialog());

        // Drag-Unterstützung
        setupDragListener(floatingView, params);
    }

    private void setupDragListener(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void openAssistantDialog() {
        Intent intent = new Intent(this, AssistantDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
    }
}
```

### Success Criteria:

#### Manual Verification:
- [ ] Overlay-Berechtigung kann erteilt werden
- [ ] Widget erscheint auf dem Bildschirm
- [ ] Widget ist verschiebbar
- [ ] Klick öffnet Assistant Dialog

---

## Phase 5: UI/UX Polish

### Overview
Layouts, Themes und Resources finalisieren.

### Änderungen:

#### 1. Dialog Layout
**File**: `app/src/main/res/layout/activity_assistant_dialog.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="@drawable/dialog_background">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Claude Assistant"
        android:textSize="20sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:minHeight="150dp"
        android:maxHeight="300dp">

        <TextView
            android:id="@+id/response_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Bereit für deine Anfrage..."
            android:textSize="14sp"
            android:padding="8dp" />
    </ScrollView>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginTop="16dp">

        <EditText
            android:id="@+id/input_field"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Nachricht eingeben..."
            android:inputType="text"
            android:maxLines="3" />

        <ImageButton
            android:id="@+id/mic_button"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@drawable/ic_mic"
            android:contentDescription="Spracheingabe" />

        <ImageButton
            android:id="@+id/send_button"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@drawable/ic_send"
            android:contentDescription="Senden" />
    </LinearLayout>
</LinearLayout>
```

#### 2. Floating Widget Layout
**File**: `app/src/main/res/layout/floating_widget.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="56dp"
    android:layout_height="56dp">

    <ImageView
        android:id="@+id/fab_icon"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:src="@drawable/ic_assistant"
        android:background="@drawable/fab_background"
        android:padding="12dp"
        android:elevation="6dp" />
</FrameLayout>
```

### Success Criteria:

#### Manual Verification:
- [ ] UI sieht ansprechend aus
- [ ] Dialog ist gut lesbar
- [ ] Icons sind erkennbar

---

## Phase 6: Termux-Setup Script

### Overview
Script zum Konfigurieren von Termux für die App-Integration.

### Änderungen:

#### 1. Setup Script
**File**: `scripts/setup-termux.sh`
```bash
#!/data/data/com.termux/files/usr/bin/bash

echo "=== Claude Assistant Termux Setup ==="

# 1. termux.properties konfigurieren
PROPS_FILE="$HOME/.termux/termux.properties"
mkdir -p "$HOME/.termux"

if ! grep -q "allow-external-apps" "$PROPS_FILE" 2>/dev/null; then
    echo "allow-external-apps = true" >> "$PROPS_FILE"
    echo "[OK] allow-external-apps aktiviert"
else
    sed -i 's/allow-external-apps.*/allow-external-apps = true/' "$PROPS_FILE"
    echo "[OK] allow-external-apps aktualisiert"
fi

# 2. Termux:API prüfen
if ! command -v termux-tts-speak &>/dev/null; then
    echo "[INFO] Installiere termux-api..."
    pkg install termux-api -y
fi

# 3. Claude Code prüfen
if ! command -v claude &>/dev/null; then
    echo "[WARNUNG] Claude Code nicht gefunden!"
    echo "Bitte installiere Claude Code mit: npm install -g @anthropic-ai/claude-code"
else
    echo "[OK] Claude Code gefunden: $(which claude)"
fi

# 4. Termux neu laden
termux-reload-settings

echo ""
echo "=== Setup abgeschlossen ==="
echo "Bitte erteile der Claude Assistant App die Berechtigung"
echo "'com.termux.permission.RUN_COMMAND' in den Android-Einstellungen."
```

### Success Criteria:

#### Manual Verification:
- [ ] Script läuft ohne Fehler
- [ ] termux.properties korrekt konfiguriert
- [ ] Termux:API funktioniert
- [ ] Claude Code erreichbar

---

## Testing Strategy

### Unit Tests:
- TermuxBridge Intent-Erstellung
- Response-Parsing

### Integration Tests:
- Vollständiger Workflow: Spracheingabe → Claude Code → Antwort → TTS

### Manual Testing Steps:
1. Quick Settings Tile hinzufügen und antippen
2. Spracheingabe testen: "Was ist das Wetter heute?"
3. Text-Eingabe testen
4. Antwort prüfen (Text und TTS)
5. Floating Widget aktivieren und testen
6. App-Verhalten bei geschlossenem Termux testen

---

## Bekannte Einschränkungen

1. **Keine Hotword Detection** - "Hey Claude" nicht möglich ohne System-Integration
2. **Termux muss laufen** - Zumindest im Hintergrund für schnelle Antworten
3. **Latenz** - Erste Anfrage nach App-Start dauert länger (Cold Start)
4. **Kein AssistStructure-Zugriff** - Wir können nicht den aktuellen Bildschirminhalt analysieren (das erfordert VoiceInteractionService mit System-Signatur)

## Was FUNKTIONIERT

1. **Assistant-Geste** - Lange Home-Taste (oder Geste) startet unsere App!
2. **Standard-Assistent** - App kann in Einstellungen als Standard gewählt werden
3. **Spracheingabe** - Android's eingebaute Speech-to-Text
4. **Claude Code Integration** - Vollständiger Zugriff auf Claude über Termux

---

## Alternatives Konzept: Termux Widget + Script

Falls die APK-Entwicklung zu komplex wird, gibt es eine einfachere Alternative:

1. **Termux:Widget** installieren
2. Script `~/.shortcuts/claude-voice.sh` erstellen:
```bash
#!/data/data/com.termux/files/usr/bin/bash
prompt=$(termux-dialog speech -t "Claude Assistant" | jq -r '.text')
if [ -n "$prompt" ]; then
    response=$(claude -p "$prompt" --output-format text)
    termux-tts-speak "$response"
    termux-notification -t "Claude" -c "$response"
fi
```
3. Widget auf Homescreen platzieren

Diese Lösung erfordert keine eigene App, nutzt aber Termux direkt.

---

## Referenzen

- [VoiceInteractionService API](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
- [Termux RUN_COMMAND Wiki](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Quick Settings Tile Guide](https://developer.android.com/develop/ui/views/quicksettings-tiles)
- [Android Accessibility Service](https://developer.android.com/guide/topics/ui/accessibility/service)
- [AOSP Voice Interaction Guide](https://source.android.com/docs/automotive/voice/voice_interaction_guide)
