package dev.claude.assistant;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

public class AssistantTileService extends TileService {

    @Override
    public void onClick() {
        // Start Assistant Dialog
        Intent intent = new Intent(this, AssistantDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }

    @Override
    public void onStartListening() {
        // Tile visible - update status if needed
    }

    @Override
    public void onStopListening() {
        // Tile no longer visible
    }
}
