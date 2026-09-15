package com.poodicraft.shopkeeper.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Styling helpers for the heads-up display.
 *
 * <p>Everything is built in code rather than XML so the whole UI lives beside the
 * game logic and needs no resource plumbing.
 */
public final class Theme {
    private Theme() { }

    public static final int INK           = 0xFF1B1F27;
    public static final int INK_SOFT      = 0xFF5A6474;
    public static final int PAPER         = 0xFFF7F4EC;
    public static final int PANEL         = 0xFFFFFFFF;
    public static final int PANEL_SUNKEN  = 0xFFEDE8DC;
    public static final int SCRIM         = 0x99000000;

    public static final int GREEN         = 0xFF2E9E74;
    public static final int GREEN_DARK    = 0xFF1F7A58;
    public static final int GOLD          = 0xFFE9A13B;
    public static final int GOLD_DARK     = 0xFFC9822A;
    public static final int RED           = 0xFFD9584C;
    public static final int BLUE          = 0xFF3D7BC4;
    public static final int PURPLE        = 0xFF7A5BC4;
    public static final int DISABLED      = 0xFFB8B2A6;

    public static final int HUD_CARD      = 0xD91B2029;
    public static final int HUD_TEXT      = 0xFFFFFFFF;

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable roundRect(Context context, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(context, radiusDp));
        return d;
    }

    public static GradientDrawable roundRect(Context context, int color, float radiusDp,
                                             int strokeColor, float strokeDp) {
        GradientDrawable d = roundRect(context, color, radiusDp);
        d.setStroke(dp(context, strokeDp), strokeColor);
        return d;
    }

    /** Pressed/disabled aware background for tappable controls. */
    public static StateListDrawable button(Context context, int color, float radiusDp) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{-android.R.attr.state_enabled}, roundRect(context, DISABLED, radiusDp));
        list.addState(new int[]{android.R.attr.state_pressed}, roundRect(context, darken(color, 0.82f), radiusDp));
        list.addState(new int[]{}, roundRect(context, color, radiusDp));
        return list;
    }

    public static int darken(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a, r, g, b);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static TextView text(Context context, String value, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(context);
        tv.setText(value);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static TextView label(Context context, String value) {
        TextView tv = text(context, value, 12f, INK_SOFT, true);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.06f);
        return tv;
    }

    public static TextView button(Context context, String value, int color) {
        TextView tv = text(context, value, 15f, 0xFFFFFFFF, true);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(button(context, color, 12f));
        int padH = dp(context, 18), padV = dp(context, 12);
        tv.setPadding(padH, padV, padH, padV);
        tv.setClickable(true);
        return tv;
    }

    public static LinearLayout row(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    public static View spacer(Context context, int widthDp, int heightDp) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                widthDp <= 0 ? 0 : dp(context, widthDp),
                heightDp <= 0 ? 0 : dp(context, heightDp)));
        return v;
    }

    public static View flexSpacer(Context context) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }
}
