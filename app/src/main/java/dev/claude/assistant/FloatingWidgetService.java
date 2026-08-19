package dev.claude.assistant;

import android.app.Service;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import dev.claude.assistant.ankai.LiveRunSnapshot;
import dev.claude.assistant.ankai.LiveRunState;
import dev.claude.assistant.ankai.LiveRunSubscription;

public class FloatingWidgetService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private View liveRunStatus;
    private LiveRunSubscription latestRunSubscription;
    private LiveRunSubscription runSubscription;

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
        liveRunStatus = floatingView.findViewById(R.id.live_run_status);
        floatingView.setContentDescription(getString(R.string.floating_widget_ready));
        latestRunSubscription = LiveRunRuntime.coordinator(getApplicationContext())
            .registry().observeLatest(this::observeRun);

        // Drag support
        setupDragListener(floatingView, params);
    }

    private void observeRun(LiveRunState run) {
        if (runSubscription != null) runSubscription.close();
        runSubscription = run.observe(this::displayRunStatus);
    }

    private void displayRunStatus(LiveRunSnapshot snapshot) {
        liveRunStatus.post(() -> {
            boolean done = snapshot.isDone();
            int color = getColor(done ? R.color.live_run_done : R.color.live_run_active);
            liveRunStatus.setBackgroundTintList(ColorStateList.valueOf(color));
            liveRunStatus.setVisibility(View.VISIBLE);
            floatingView.setContentDescription(getString(done
                ? R.string.floating_widget_run_done
                : R.string.floating_widget_run_active));
        });
    }

    private void setupDragListener(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            moved = true;
                        }
                        params.x = initialX + deltaX;
                        params.y = initialY + deltaY;
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            v.performClick();
                        }
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
        if (runSubscription != null) runSubscription.close();
        if (latestRunSubscription != null) latestRunSubscription.close();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
        super.onDestroy();
    }
}
