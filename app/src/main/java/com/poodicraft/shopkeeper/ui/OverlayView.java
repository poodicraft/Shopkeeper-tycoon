package com.poodicraft.shopkeeper.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;

/**
 * 2D annotations drawn on top of the 3D view: shelf cards, mood bubbles and
 * floating money labels.
 *
 * <p>The GL thread projects world positions and publishes a list of items; this view
 * only draws whatever it was last handed, so the two threads never share mutable
 * state mid-frame.
 */
public final class OverlayView extends View {

    public static final int TYPE_POPUP = 0;
    public static final int TYPE_SHELF = 1;
    public static final int TYPE_MOOD = 2;
    public static final int TYPE_BUY = 3;
    public static final int TYPE_QUEUE = 4;

    /** One projected annotation. Pooled to keep the render loop allocation-free. */
    public static final class Item {
        public int type;
        public float x, y;
        public float scale = 1f;
        public float alpha = 1f;
        public int color;
        public String title;
        public String subtitle;
        /** 0..1 meter shown as a bar (shelf fill) or an arc (customer patience). */
        public float meter;
        public boolean warn;
    }

    private final Object lock = new Object();
    /** Written by the GL thread. */
    private final ArrayList<Item> staging = new ArrayList<Item>();
    /** Read by the UI thread; holds its own copies so the two never share an object. */
    private final ArrayList<Item> visible = new ArrayList<Item>();
    private int stagingCount = 0;
    private int visibleCount = 0;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final float density;

    public OverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        density = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textOutline.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textOutline.setStyle(Paint.Style.STROKE);
        textOutline.setStrokeWidth(density * 3f);
        textOutline.setColor(0xCC101418);
    }

    // ---------------------------------------------------- publishing (GL thread)

    public void beginUpdate() {
        stagingCount = 0;
    }

    /** Grabs a pooled item to fill in; call {@link #commit()} when the frame is done. */
    public Item nextItem() {
        while (stagingCount >= staging.size()) staging.add(new Item());
        Item item = staging.get(stagingCount++);
        item.title = null;
        item.subtitle = null;
        item.meter = 0f;
        item.warn = false;
        item.scale = 1f;
        item.alpha = 1f;
        item.color = 0xFFFFFFFF;
        return item;
    }

    /**
     * Hands the frame to the UI thread.
     *
     * <p>Values are copied rather than the objects published, so the next frame can
     * start overwriting the staging items while a draw is still in flight.
     */
    public void commit() {
        synchronized (lock) {
            while (visible.size() < stagingCount) visible.add(new Item());
            for (int i = 0; i < stagingCount; i++) {
                Item from = staging.get(i);
                Item to = visible.get(i);
                to.type = from.type;
                to.x = from.x;
                to.y = from.y;
                to.scale = from.scale;
                to.alpha = from.alpha;
                to.color = from.color;
                to.title = from.title;
                to.subtitle = from.subtitle;
                to.meter = from.meter;
                to.warn = from.warn;
            }
            visibleCount = stagingCount;
        }
        postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas canvas) {
        synchronized (lock) {
            for (int i = 0; i < visibleCount; i++) {
                Item item = visible.get(i);
                switch (item.type) {
                    case TYPE_SHELF: drawShelfCard(canvas, item); break;
                    case TYPE_MOOD: drawMood(canvas, item); break;
                    case TYPE_BUY: drawBuyTag(canvas, item); break;
                    case TYPE_QUEUE: drawQueueTag(canvas, item); break;
                    default: drawPopup(canvas, item); break;
                }
            }
        }
    }

    private void drawShelfCard(Canvas canvas, Item item) {
        float s = density * item.scale;
        float padH = 8f * s, padV = 5f * s;
        textPaint.setTextSize(12f * s);
        float titleWidth = textPaint.measureText(item.title);
        textPaint.setTextSize(10.5f * s);
        float subWidth = item.subtitle == null ? 0 : textPaint.measureText(item.subtitle);
        float dot = 7f * s;
        float width = Math.max(titleWidth + dot + 4f * s, subWidth) + padH * 2;
        float height = (item.subtitle == null ? 16f : 29f) * s + padV * 2;

        float left = item.x - width * 0.5f;
        float top = item.y - height;
        rect.set(left, top, left + width, top + height);

        int alpha = (int) (item.alpha * 235);
        fill.setColor(Color.argb(alpha, 24, 28, 36));
        canvas.drawRoundRect(rect, 7f * s, 7f * s, fill);
        stroke.setStrokeWidth(1.4f * s);
        stroke.setColor(Color.argb((int) (item.alpha * 120), 255, 255, 255));
        canvas.drawRoundRect(rect, 7f * s, 7f * s, stroke);

        // Product colour chip.
        fill.setColor(Theme.withAlpha(item.color, alpha));
        canvas.drawCircle(left + padH + dot * 0.5f, top + padV + 6.5f * s, dot * 0.5f, fill);

        textPaint.setTextSize(12f * s);
        textPaint.setColor(Color.argb(alpha, 255, 255, 255));
        canvas.drawText(item.title, left + padH + dot + 4f * s, top + padV + 11f * s, textPaint);

        if (item.subtitle != null) {
            textPaint.setTextSize(10.5f * s);
            textPaint.setColor(Color.argb((int) (alpha * 0.8f),
                    item.warn ? 255 : 190, item.warn ? 140 : 200, item.warn ? 120 : 215));
            canvas.drawText(item.subtitle, left + padH, top + padV + 25f * s, textPaint);

            // Stock meter along the bottom edge.
            float barTop = top + height - 4f * s;
            rect.set(left + padH, barTop, left + width - padH, barTop + 2.6f * s);
            fill.setColor(Color.argb((int) (alpha * 0.35f), 255, 255, 255));
            canvas.drawRoundRect(rect, 2f * s, 2f * s, fill);
            rect.set(left + padH, barTop,
                    left + padH + (width - padH * 2) * Math.max(0.02f, item.meter), barTop + 2.6f * s);
            fill.setColor(Theme.withAlpha(item.meter < 0.18f ? Theme.RED : Theme.GREEN, alpha));
            canvas.drawRoundRect(rect, 2f * s, 2f * s, fill);
        }
    }

    private void drawMood(Canvas canvas, Item item) {
        float s = density * item.scale;
        float radius = 11f * s;
        int alpha = (int) (item.alpha * 245);

        fill.setColor(Color.argb(alpha, 255, 255, 255));
        canvas.drawCircle(item.x, item.y, radius, fill);

        // Patience arc around the bubble.
        stroke.setStrokeWidth(3f * s);
        stroke.setColor(Color.argb(alpha, 230, 230, 230));
        rect.set(item.x - radius - 2.4f * s, item.y - radius - 2.4f * s,
                item.x + radius + 2.4f * s, item.y + radius + 2.4f * s);
        canvas.drawArc(rect, -90f, 360f, false, stroke);
        stroke.setColor(Theme.withAlpha(moodColor(item.meter), alpha));
        canvas.drawArc(rect, -90f, 360f * Math.max(0.02f, item.meter), false, stroke);

        // Simple face: two eyes and a mouth curve that follows the mood.
        fill.setColor(Color.argb(alpha, 30, 34, 42));
        canvas.drawCircle(item.x - 3.8f * s, item.y - 2.4f * s, 1.7f * s, fill);
        canvas.drawCircle(item.x + 3.8f * s, item.y - 2.4f * s, 1.7f * s, fill);
        stroke.setStrokeWidth(1.8f * s);
        stroke.setColor(Color.argb(alpha, 30, 34, 42));
        float curve = (item.meter - 0.5f) * 9f * s;
        rect.set(item.x - 5f * s, item.y + 1f * s - Math.abs(curve),
                item.x + 5f * s, item.y + 1f * s + Math.abs(curve));
        canvas.drawArc(rect, curve >= 0 ? 0f : 180f, 180f, false, stroke);

        if (item.title != null) {
            textPaint.setTextSize(10f * s);
            textPaint.setColor(Color.argb(alpha, 255, 255, 255));
            textOutline.setTextSize(10f * s);
            textOutline.setStrokeWidth(2.6f * s);
            float w = textPaint.measureText(item.title);
            canvas.drawText(item.title, item.x - w * 0.5f, item.y + radius + 12f * s, textOutline);
            canvas.drawText(item.title, item.x - w * 0.5f, item.y + radius + 12f * s, textPaint);
        }
    }

    private int moodColor(float mood) {
        if (mood > 0.6f) return Theme.GREEN;
        if (mood > 0.3f) return Theme.GOLD;
        return Theme.RED;
    }

    private void drawBuyTag(Canvas canvas, Item item) {
        float s = density * item.scale;
        textPaint.setTextSize(12.5f * s);
        float width = textPaint.measureText(item.title) + 20f * s;
        float height = 24f * s;
        rect.set(item.x - width * 0.5f, item.y - height * 0.5f,
                item.x + width * 0.5f, item.y + height * 0.5f);
        int alpha = (int) (item.alpha * 235);
        fill.setColor(Theme.withAlpha(item.warn ? Theme.DISABLED : Theme.GREEN, alpha));
        canvas.drawRoundRect(rect, height * 0.5f, height * 0.5f, fill);
        textPaint.setColor(Color.argb(alpha, 255, 255, 255));
        float w = textPaint.measureText(item.title);
        canvas.drawText(item.title, item.x - w * 0.5f, item.y + 4.4f * s, textPaint);
    }

    private void drawQueueTag(Canvas canvas, Item item) {
        float s = density * item.scale;
        float radius = 13f * s;
        int alpha = (int) (item.alpha * 240);
        fill.setColor(Theme.withAlpha(Theme.BLUE, alpha));
        canvas.drawCircle(item.x, item.y, radius, fill);
        stroke.setStrokeWidth(2.4f * s);
        stroke.setColor(Color.argb(alpha, 255, 255, 255));
        canvas.drawCircle(item.x, item.y, radius, stroke);
        textPaint.setTextSize(13f * s);
        textPaint.setColor(Color.argb(alpha, 255, 255, 255));
        float w = textPaint.measureText(item.title);
        canvas.drawText(item.title, item.x - w * 0.5f, item.y + 4.6f * s, textPaint);
    }

    private void drawPopup(Canvas canvas, Item item) {
        float s = density * item.scale;
        textPaint.setTextSize(15f * s);
        textOutline.setTextSize(15f * s);
        textOutline.setStrokeWidth(3.4f * s);
        int alpha = (int) (item.alpha * 255);
        float w = textPaint.measureText(item.title);
        textOutline.setColor(Color.argb((int) (alpha * 0.75f), 12, 16, 22));
        canvas.drawText(item.title, item.x - w * 0.5f, item.y, textOutline);
        textPaint.setColor(Theme.withAlpha(item.color, alpha));
        canvas.drawText(item.title, item.x - w * 0.5f, item.y, textPaint);
    }
}
