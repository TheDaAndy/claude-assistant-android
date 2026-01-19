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

        // Command: Execute Claude Code with prompt
        String command = "/data/data/com.termux/files/usr/bin/claude";

        intent.putExtra("com.termux.RUN_COMMAND_PATH", command);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
            new String[]{"-p", prompt, "--output-format", "text"});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR",
            "/data/data/com.termux/files/home");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");

        // PendingIntent for result callback
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
            // Fallback: Open Termux app
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

    public static void speakWithTTS(Context context, String text) {
        if (text == null || text.isEmpty()) return;

        Intent ttsIntent = new Intent();
        ttsIntent.setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE);
        ttsIntent.setAction(ACTION_RUN_COMMAND);
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
