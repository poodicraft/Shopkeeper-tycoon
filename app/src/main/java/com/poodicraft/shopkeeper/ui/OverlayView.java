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
 * World-anchored 2D annotations drawn over the 3D view: the prompt on whatever the
 * player is standing at, thought bubbles over shoppers, and floating money.
 *
 * <p>The GL thread projects world positions and publishes values; this view draws
 * only what it was last handed, so the two threads never share mutable state
 * mid-frame.
 */
public final class OverlayView extends View {

    public static final int TYPE_POPUP = 0;
    public static final int TYPE_PROMPT = 1;
    public static final int TYPE_MOOD = 2;
    public static final int TYPE_BUBBLE = 3;
    public static final int TYPE_SHELF_TAG = 4;

    /** One projected annotation. */
    public static final class Item {
        public int type;
        public float x, y;
        public float scale = 1f;
        public float alpha = 1f;
        public int color;
        public String title;
        public String subtitle;
        public float meter;
        public boolean warn;
    }

    private final Object lock = new Object();
    private final ArrayList<Item> staging = new ArrayList<Item>();
    private final ArrayList<Item> visible = new ArrayList<Item>();
    private int stagingCount = 0;
    private int visibleCount = 0;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    public OverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        density = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        outline.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        outline.setStyle(Paint.Style.STROKE);
        outline.setColor(0xCC0E1218);
    }

    // ------------------------------------------------- publishing (GL thread)

    public void beginUpdate() { stagingCount = 0; }

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

    /** Copies values across rather than publishing the objects, so a draw in flight
     *  can never see a half-written label. */
    public void commit() {
        synchronized (lock) {
            while (visible.size() < stagingCount) visible.add(new Item());
            for (int i = 0; i < stagingCount; i++) {
                Item from = staging.get(i);
                Item to = visible.get(i);
                to.type = from.type;
                to.x = from.x; to.y = from.y;
                to.scale = from.scale; to.alpha = from.alpha;
                to.color = from.color;
                to.title = from.title; to.subtitle = from.subtitle;
                to.meter = from.meter; to.warn = from.warn;
            }
            visibleCount = stagingCount;
        }
        postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------ draw

    @Override protected void onDraw(Canvas canvas) {
        synchronized (lock) {
            for (int i = 0; i < visibleCount; i++) {
                Item item = visible.get(i);
                switch (item.type) {
                    case TYPE_PROMPT: drawPrompt(canvas, item); break;
                    case TYPE_MOOD: drawMood(canvas, item); break;
                    case TYPE_BUBBLE: drawBubble(canvas, item); break;
                    case TYPE_SHELF_TAG: drawShelfTag(canvas, item); break;
                    default: drawPopup(canvas, item); break;
                }
            }
        }
    }

    /** Pill above whatever the player can interact with, with a pointer beneath. */
    private void drawPrompt(Canvas canvas, Item item) {
        float s = density;
        text.setTextSize(13.5f * s);
        float titleWidth = text.measureText(item.title);
        text.setTextSize(11.5f * s);
        float subWidth = item.subtitle == null ? 0f : text.measureText(item.subtitle);
        float width = Math.max(titleWidth, subWidth) + 26f * s;
        float height = (item.subtitle == null ? 30f : 47f) * s;

        float left = item.x - width * 0.5f;
        float top = item.y - height - 12f * s;
        rect.set(left, top, left + width, top + height);

        int alpha = (int) (item.alpha * 240);
        fill.setColor(Color.argb(alpha, 16, 20, 26));
        canvas.drawRoundRect(rect, 10f * s, 10f * s, fill);
        stroke.setStrokeWidth(1.8f * s);
        stroke.setColor(item.warn
                ? Color.argb(alpha, 230, 160, 90)
                : Color.argb(alpha, 96, 216, 176));
        canvas.drawRoundRect(rect, 10f * s, 10f * s, stroke);

        // Pointer down toward the object.
        fill.setColor(Color.argb(alpha, 16, 20, 26));
        android.graphics.Path pointer = new android.graphics.Path();
        pointer.moveTo(item.x - 7f * s, top + height - 1f);
        pointer.lineTo(item.x + 7f * s, top + height - 1f);
        pointer.lineTo(item.x, top + height + 9f * s);
        pointer.close();
        canvas.drawPath(pointer, fill);

        text.setTextSize(13.5f * s);
        text.setColor(Color.argb(alpha, 255, 255, 255));
        canvas.drawText(item.title, item.x - titleWidth * 0.5f, top + 20f * s, text);
        if (item.subtitle != null) {
            text.setTextSize(11.5f * s);
            text.setColor(Color.argb((int) (alpha * 0.82f), 168, 196, 214));
            canvas.drawText(item.subtitle, item.x - subWidth * 0.5f, top + 37f * s, text);
        }
    }

    /** Mood bubble with a patience ring, shown over queueing shoppers. */
    private void drawMood(Canvas canvas, Item item) {
        float s = density * item.scale;
        float radius = 11f * s;
        int alpha = (int) (item.alpha * 245);

        fill.setColor(Color.argb(alpha, 252, 252, 250));
        canvas.drawCircle(item.x, item.y, radius, fill);

        stroke.setStrokeWidth(3f * s);
        stroke.setColor(Color.argb((int) (alpha * 0.45f), 230, 230, 230));
        rect.set(item.x - radius - 2.6f * s, item.y - radius - 2.6f * s,
                item.x + radius + 2.6f * s, item.y + radius + 2.6f * s);
        canvas.drawArc(rect, -90f, 360f, false, stroke);
        stroke.setColor(Theme.withAlpha(moodColor(item.meter), alpha));
        canvas.drawArc(rect, -90f, 360f * Math.max(0.02f, item.meter), false, stroke);

        fill.setColor(Color.argb(alpha, 30, 34, 42));
        canvas.drawCircle(item.x - 3.8f * s, item.y - 2.4f * s, 1.7f * s, fill);
        canvas.drawCircle(item.x + 3.8f * s, item.y - 2.4f * s, 1.7f * s, fill);
        stroke.setStrokeWidth(1.8f * s);
        float curve = (item.meter - 0.5f) * 9f * s;
        rect.set(item.x - 5f * s, item.y + 1f * s - Math.abs(curve),
                item.x + 5f * s, item.y + 1f * s + Math.abs(curve));
        canvas.drawArc(rect, curve >= 0 ? 0f : 180f, 180f, false, stroke);
    }

    private int moodColor(float mood) {
        if (mood > 0.6f) return Theme.GREEN;
        if (mood > 0.3f) return Theme.GOLD;
        return Theme.RED;
    }

    /** Speech bubble for what a shopper just picked up or said. */
    private void drawBubble(Canvas canvas, Item item) {
        float s = density * item.scale;
        text.setTextSize(11.5f * s);
        float width = text.measureText(item.title) + 20f * s;
        float height = 24f * s;
        rect.set(item.x - width * 0.5f, item.y - height, item.x + width * 0.5f, item.y);
        int alpha = (int) (item.alpha * 235);
        fill.setColor(Theme.withAlpha(item.color, alpha));
        canvas.drawRoundRect(rect, height * 0.5f, height * 0.5f, fill);
        text.setColor(Color.argb(alpha, 255, 255, 255));
        canvas.drawText(item.title, item.x - (width - 20f * s) * 0.5f, item.y - 7.5f * s, text);
    }

    /** Small tag floating over a shelf: product, price and how full it is. */
    private void drawShelfTag(Canvas canvas, Item item) {
        float s = density * item.scale;
        text.setTextSize(11.5f * s);
        float width = text.measureText(item.title) + 20f * s;
        float height = item.subtitle == null ? 22f * s : 22f * s;
        float left = item.x - width * 0.5f;
        float top = item.y - height;
        rect.set(left, top, left + width, top + height);

        int alpha = (int) (item.alpha * 205);
        fill.setColor(Color.argb(alpha, 14, 18, 24));
        canvas.drawRoundRect(rect, 6f * s, 6f * s, fill);

        text.setColor(Color.argb(alpha + 30 > 255 ? 255 : alpha + 30, 236, 242, 248));
        canvas.drawText(item.title, left + 10f * s, top + 15f * s, text);

        float barTop = top + height - 3.4f * s;
        rect.set(left + 6f * s, barTop, left + width - 6f * s, barTop + 2.2f * s);
        fill.setColor(Color.argb((int) (alpha * 0.35f), 255, 255, 255));
        canvas.drawRoundRect(rect, 1.6f * s, 1.6f * s, fill);
        rect.set(left + 6f * s, barTop,
                left + 6f * s + (width - 12f * s) * Math.max(0.02f, item.meter),
                barTop + 2.2f * s);
        fill.setColor(Theme.withAlpha(item.meter < 0.18f ? Theme.RED : Theme.GREEN, alpha));
        canvas.drawRoundRect(rect, 1.6f * s, 1.6f * s, fill);
    }

    private void drawPopup(Canvas canvas, Item item) {
        float s = density * item.scale;
        text.setTextSize(15f * s);
        outline.setTextSize(15f * s);
        outline.setStrokeWidth(3.4f * s);
        int alpha = (int) (item.alpha * 255);
        float width = text.measureText(item.title);
        outline.setColor(Color.argb((int) (alpha * 0.75f), 12, 16, 22));
        canvas.drawText(item.title, item.x - width * 0.5f, item.y, outline);
        text.setColor(Theme.withAlpha(item.color, alpha));
        canvas.drawText(item.title, item.x - width * 0.5f, item.y, text);
    }
}
