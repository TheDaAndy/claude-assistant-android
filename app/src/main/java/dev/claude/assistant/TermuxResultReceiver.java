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

            // Broadcast to active Activity
            Intent responseIntent = new Intent(
                "dev.claude.assistant.RESPONSE_RECEIVED");
            responseIntent.putExtra("response", response);
            responseIntent.putExtra("exitCode", exitCode);
            context.sendBroadcast(responseIntent);

            // Optional: TTS output via Termux:API
            if (!response.isEmpty() && exitCode == 0) {
                TermuxBridge.speakWithTTS(context, response);
            }
        }
    }
}
