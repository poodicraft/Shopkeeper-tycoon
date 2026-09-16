package com.poodicraft.shopkeeper.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * The single context button: whatever the player is standing next to, this does it.
 *
 * <p>Its label changes with the interaction, and it dims rather than disappearing
 * when an action is visible but not currently possible - so the player learns what
 * is there and why it is unavailable.
 */
public final class ActionButton extends View {

    private String label = "";
    private String detail = null;
    private boolean enabledAction = false;
    private boolean visibleAction = false;
    private float pressAnimation = 0f;
    private long pressStartedAt = 0L;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ActionButton(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        setWillNotDraw(false);
    }

    public void setAction(String label, String detail, boolean enabled, boolean visible) {
        boolean changed = visible != visibleAction || enabled != enabledAction
                || !equalText(label, this.label) || !equalText(detail, this.detail);
        this.label = label == null ? "" : label;
        this.detail = detail;
        this.enabledAction = enabled;
        this.visibleAction = visible;
        if (changed) postInvalidateOnAnimation();
    }

    private static boolean equalText(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public boolean isActionEnabled() { return visibleAction && enabledAction; }

    public void flashPress() {
        pressStartedAt = System.currentTimeMillis();
        postInvalidateOnAnimation();
    }

    public float radius() { return 46f * density; }

    /** Centre of the button, used for hit testing from the parent. */
    public float centreX() { return getWidth() - radius() - 26f * density; }

    public float centreY() { return getHeight() - radius() - 34f * density; }

    public boolean contains(float x, float y) {
        float dx = x - centreX();
        float dy = y - centreY();
        float touchRadius = radius() * 1.35f;
        return dx * dx + dy * dy <= touchRadius * touchRadius;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!visibleAction) return;
        float cx = centreX(), cy = centreY(), r = radius();

        long since = System.currentTimeMillis() - pressStartedAt;
        pressAnimation = since < 220 ? 1f - since / 220f : 0f;
        if (pressAnimation > 0f) postInvalidateOnAnimation();

        int base = enabledAction ? 0xFF2E9E74 : 0xFF6A7078;
        float scale = 1f + pressAnimation * 0.09f;

        // Halo that pulses outward on press.
        if (pressAnimation > 0f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * density);
            paint.setColor(Color.argb((int) (150 * pressAnimation), 255, 255, 255));
            canvas.drawCircle(cx, cy, r * (1.05f + (1f - pressAnimation) * 0.35f), paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(70, 8, 12, 16));
        canvas.drawCircle(cx, cy + 3f * density, r * scale, paint);
        paint.setColor(base);
        canvas.drawCircle(cx, cy, r * scale, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.6f * density);
        paint.setColor(Color.argb(enabledAction ? 210 : 120, 255, 255, 255));
        canvas.drawCircle(cx, cy, r * scale, paint);

        // Label wraps onto a second line rather than being clipped.
        textPaint.setColor(Color.argb(enabledAction ? 255 : 190, 255, 255, 255));
        String[] lines = splitLabel(label);
        float size = lines.length > 1 ? 12.5f : 14f;
        textPaint.setTextSize(size * density);
        float lineHeight = size * 1.15f * density;
        float startY = cy - (lines.length - 1) * lineHeight * 0.5f + size * 0.36f * density;
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], cx, startY + i * lineHeight, textPaint);
        }

        if (detail != null) {
            textPaint.setTextSize(11f * density);
            textPaint.setColor(Color.argb(225, 255, 255, 255));
            paint.setStyle(Paint.Style.FILL);
            float width = textPaint.measureText(detail) + 18f * density;
            float top = cy + r + 10f * density;
            paint.setColor(Color.argb(190, 12, 16, 22));
            canvas.drawRoundRect(cx - width * 0.5f, top, cx + width * 0.5f,
                    top + 22f * density, 11f * density, 11f * density, paint);
            canvas.drawText(detail, cx, top + 15f * density, textPaint);
        }
    }

    private static String[] splitLabel(String text) {
        int space = text.indexOf(' ');
        if (space <= 0 || text.length() <= 10) return new String[]{text};
        return new String[]{text.substring(0, space), text.substring(space + 1)};
    }
}
