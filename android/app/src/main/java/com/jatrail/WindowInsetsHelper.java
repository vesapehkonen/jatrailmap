package com.jatrail;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ScrollView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

final class WindowInsetsHelper {
    private WindowInsetsHelper() {
    }

    static void enableEdgeToEdge(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
        boolean darkTheme = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(!darkTheme);
    }

    static void setUpToolbar(AppCompatActivity activity, @StringRes int title, boolean showUp) {
        Toolbar toolbar = activity.findViewById(R.id.app_toolbar);
        activity.setSupportActionBar(toolbar);
        activity.setTitle(title);
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(showUp);
        }
        applySafePadding(toolbar, true, true, true, false);
    }

    static void applyContentInsets(Activity activity) {
        applyContentInsets(activity, false);
    }

    static void applyContentInsets(Activity activity, boolean includeTop) {
        applySafePadding(
                activity.findViewById(R.id.activity_content), true, includeTop, true, true);
    }

    static void applyImeAwareContentInsets(Activity activity) {
        ScrollView scrollView = activity.findViewById(R.id.activity_content);
        int initialLeft = scrollView.getPaddingLeft();
        int initialTop = scrollView.getPaddingTop();
        int initialRight = scrollView.getPaddingRight();
        int initialBottom = scrollView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, windowInsets) -> {
            Insets safe = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(
                    initialLeft + safe.left,
                    initialTop,
                    initialRight + safe.right,
                    initialBottom + Math.max(safe.bottom, ime.bottom));
            View focused = activity.getCurrentFocus();
            if (focused instanceof EditText && isDescendantOf(focused, scrollView)) {
                revealFieldAboveIme(scrollView, focused, ime.bottom);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(scrollView);
        scrollView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (!(newFocus instanceof EditText) || !isDescendantOf(newFocus, scrollView)) {
                return;
            }
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(scrollView);
            int imeBottom = insets == null
                    ? 0
                    : insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            revealFieldAboveIme(scrollView, newFocus, imeBottom);
        });
    }

    private static boolean isDescendantOf(View view, View ancestor) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent == ancestor) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static void revealFieldAboveIme(
            ScrollView scrollView, View field, int imeBottom) {
        if (imeBottom <= 0) {
            return;
        }
        scrollView.post(() -> {
            int[] scrollLocation = new int[2];
            int[] fieldLocation = new int[2];
            scrollView.getLocationOnScreen(scrollLocation);
            field.getLocationOnScreen(fieldLocation);
            int margin = Math.round(16 * field.getResources().getDisplayMetrics().density);
            int visibleBottom = scrollLocation[1] + scrollView.getHeight() - imeBottom;
            int fieldBottom = fieldLocation[1] + field.getHeight() + margin;
            int overlap = fieldBottom - visibleBottom;
            if (overlap > 0) {
                scrollView.smoothScrollBy(0, overlap);
            }
        });
    }

    static void useContentBackgroundStatusBarIcons(Activity activity) {
        boolean darkTheme = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightStatusBars(!darkTheme);
    }

    private static void applySafePadding(
            View view, boolean left, boolean top, boolean right, boolean bottom) {
        int initialLeft = view.getPaddingLeft();
        int initialTop = view.getPaddingTop();
        int initialRight = view.getPaddingRight();
        int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets safe = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(
                    initialLeft + (left ? safe.left : 0),
                    initialTop + (top ? safe.top : 0),
                    initialRight + (right ? safe.right : 0),
                    initialBottom + (bottom ? safe.bottom : 0));
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
