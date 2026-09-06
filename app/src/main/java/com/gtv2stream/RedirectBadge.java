package com.gtv2stream;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Small app-logo confirmation badge shown briefly at the top right of the screen
 * after every successful redirect. Uses an application overlay window, so it needs
 * the "Display over other apps" permission; without it the badge silently does
 * nothing. It is non-focusable and non-touchable and never steals input from the
 * launched app.
 */
final class RedirectBadge {
    private static final String TAG = "GTV2STREAM";
    private static final long HOLD_MS = 1500L;
    private static final long FADE_IN_MS = 150L;
    private static final long FADE_OUT_MS = 250L;
    /**
     * Display is delayed so the overlay window never exists while the launcher is
     * still in the foreground — by the time it shows, the target app is on screen.
     */
    private static final long LAUNCH_SETTLE_MS = 300L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    // Application-scoped singleton: the badge is created from the application
    // context, so the static field can never leak an Activity or Service.
    @SuppressLint("StaticFieldLeak")
    private static FrameLayout badge;
    private static float density = 1f;
    private static boolean attached;
    private static Runnable pendingShow;
    private static final Runnable HIDE = RedirectBadge::hide;

    private RedirectBadge() { }

    /** Safe from any thread; honors the user preference, then shows on the main handler. */
    static void show(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (!AppPrefs.badgeEnabled(applicationContext)) return;
        HANDLER.removeCallbacks(pendingShow);
        pendingShow = () -> display(applicationContext);
        HANDLER.postDelayed(pendingShow, LAUNCH_SETTLE_MS);
    }

    private static void display(Context applicationContext) {
        try {
            if (!Settings.canDrawOverlays(applicationContext)) return;
            WindowManager manager = applicationContext.getSystemService(WindowManager.class);
            if (manager == null) return;
            density = applicationContext.getResources().getDisplayMetrics().density;

            HANDLER.removeCallbacks(HIDE);
            if (badge == null) badge = createBadge(applicationContext);

            if (attached) {
                try {
                    manager.removeView(badge);
                    attached = false;
                } catch (RuntimeException ignored) {
                    // The window is being replaced; addView below rebuilds the state.
                }
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.END;
            params.x = dp(24);
            params.y = dp(24);
            manager.addView(badge, params);
            attached = true;

            badge.animate().cancel();
            badge.setAlpha(0f);
            badge.animate().alpha(1f).setDuration(FADE_IN_MS).start();
            HANDLER.postDelayed(HIDE, HOLD_MS);
        } catch (RuntimeException error) {
            Log.w(TAG, "Redirect badge unavailable: " + error.getMessage());
        }
    }

    private static void hide() {
        if (!attached || badge == null) return;
        badge.animate().cancel();
        badge.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction(() -> {
            try {
                // The badge view holds the application context, so its window
                // manager is the application-scoped instance we attached with.
                WindowManager manager = badge.getContext().getSystemService(WindowManager.class);
                if (manager != null) manager.removeView(badge);
                attached = false;
            } catch (RuntimeException error) {
                Log.w(TAG, "Redirect badge removal failed: " + error.getMessage());
            }
        }).start();
    }

    private static FrameLayout createBadge(Context context) {
        FrameLayout container = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(230, 16, 19, 26));
        background.setCornerRadius(dp(14));
        container.setBackground(background);
        container.setPadding(dp(8), dp(8), dp(8), dp(8));
        container.setElevation(dp(4));
        container.setClipToOutline(true);

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.gtv2stream_logo);
        logo.setLayoutParams(new FrameLayout.LayoutParams(dp(48), dp(48)));
        container.addView(logo);
        container.setContentDescription(context.getString(R.string.app_name));
        return container;
    }

    private static int dp(int value) {
        // Displayed on the main thread, so the density is always set by then.
        return Math.round(value * density + 0.5f);
    }
}
