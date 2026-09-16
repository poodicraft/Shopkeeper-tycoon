package com.poodicraft.shopkeeper.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Virtual movement stick for the lower-left of the screen.
 *
 * <p>The base recentres wherever the thumb lands rather than sitting at a fixed
 * spot, so the player never has to look down to find it. Pushing past the ring
 * asks for a run.
 */
public final class Joystick extends View {

    /** Normalised output: -1..1 on each axis, y positive meaning forwards. */
    public float outputX = 0f;
    public float outputY = 0f;
    public boolean running = false;

    private boolean active = false;
    private int pointerId = -1;
    private float baseX, baseY, knobX, knobY;
    private float idleAlpha = 0.5f;

    private final float density;
    private final float radius;
    private final float knobRadius;
    private final float runThreshold;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Joystick(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        radius = 62f * density;
        knobRadius = 30f * density;
        runThreshold = radius * 0.82f;
        setWillNotDraw(false);
    }

    /** True when the touch at (x, y) belongs to this control. */
    public boolean claims(float x, float y) {
        return x < getWidth() * 0.52f && y > getHeight() * 0.32f;
    }

    public boolean isActive() { return active; }

    public int activePointer() { return pointerId; }

    public void begin(int id, float x, float y) {
        pointerId = id;
        active = true;
        baseX = clampX(x);
        baseY = clampY(y);
        knobX = baseX;
        knobY = baseY;
        outputX = 0f;
        outputY = 0f;
        running = false;
        postInvalidateOnAnimation();
    }

    public void drag(float x, float y) {
        if (!active) return;
        float dx = x - baseX;
        float dy = y - baseY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        running = distance > runThreshold;
        if (distance > radius) {
            dx = dx / distance * radius;
            dy = dy / distance * radius;
            distance = radius;
        }
        knobX = baseX + dx;
        knobY = baseY + dy;

        // A small dead zone keeps a resting thumb from drifting the character.
        final float deadZone = 0.12f;
        float magnitude = distance / radius;
        if (magnitude < deadZone) {
            outputX = 0f;
            outputY = 0f;
        } else {
            // Rescale past the dead zone so the first responsive input is still slow.
            float scaled = (magnitude - deadZone) / (1f - deadZone);
            float inverse = 1f / Math.max(distance, 1e-4f);
            outputX = dx * inverse * scaled;
            // Screen y grows downward; forward on the stick is negative y.
            outputY = -dy * inverse * scaled;
        }
        postInvalidateOnAnimation();
    }

    public void end() {
        active = false;
        pointerId = -1;
        outputX = 0f;
        outputY = 0f;
        running = false;
        postInvalidateOnAnimation();
    }

    private float clampX(float x) {
        return Math.max(radius + 8f * density, Math.min(getWidth() - radius - 8f * density, x));
    }

    private float clampY(float y) {
        return Math.max(radius + 8f * density, Math.min(getHeight() - radius - 8f * density, y));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // Touch is routed by the parent so the stick and the camera can be used at
        // the same time; this view only draws.
        return false;
    }

    @Override protected void onDraw(Canvas canvas) {
        float cx, cy;
        float alpha;
        if (active) {
            cx = baseX; cy = baseY; alpha = 1f;
        } else {
            cx = radius + 26f * density;
            cy = getHeight() - radius - 26f * density;
            alpha = idleAlpha;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (46 * alpha), 10, 14, 20));
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * density);
        paint.setColor(Color.argb((int) (140 * alpha), 255, 255, 255));
        canvas.drawCircle(cx, cy, radius, paint);

        if (running) {
            paint.setStrokeWidth(3.4f * density);
            paint.setColor(Color.argb((int) (200 * alpha), 255, 206, 110));
            canvas.drawCircle(cx, cy, radius + 4f * density, paint);
        }

        float kx = active ? knobX : cx;
        float ky = active ? knobY : cy;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (190 * alpha), 245, 247, 250));
        canvas.drawCircle(kx, ky, knobRadius, paint);
        paint.setColor(Color.argb((int) (90 * alpha), 20, 26, 34));
        canvas.drawCircle(kx, ky, knobRadius * 0.42f, paint);
    }
}
