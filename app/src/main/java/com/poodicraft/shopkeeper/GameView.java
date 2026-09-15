package com.poodicraft.shopkeeper;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.poodicraft.shopkeeper.game.Customer;
import com.poodicraft.shopkeeper.game.Money;
import com.poodicraft.shopkeeper.game.Shelf;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.gl.Camera;
import com.poodicraft.shopkeeper.gl.Renderer3D;
import com.poodicraft.shopkeeper.math.Vec3;
import com.poodicraft.shopkeeper.scene.SceneAssets;
import com.poodicraft.shopkeeper.scene.ShopRenderer;
import com.poodicraft.shopkeeper.ui.OverlayView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The 3D view: owns the GL thread, the camera, the touch gestures and the frame loop.
 *
 * <p>Simulation and rendering both run on the GL thread while holding the shared game
 * lock, so UI-thread actions can mutate the shop safely.
 */
public final class GameView extends GLSurfaceView {

    /** Events the view raises back to the activity, always on the UI thread. */
    public interface Callbacks {
        void onShelfTapped(int shelfIndex);
        void onRegisterTapped();
        void onCustomerTapped(int customerIndex);
        void onDayEnded(Shop.DaySummary summary);
        void onEmptyTapped();
    }

    private final Shop shop;
    private final Object gameLock;
    private final OverlayView overlay;
    private final Callbacks callbacks;

    private final SceneAssets assets = new SceneAssets();
    private final Renderer3D renderer3d = new Renderer3D();
    private final ShopRenderer shopRenderer = new ShopRenderer(assets, renderer3d);
    public final Camera camera = new Camera();

    private final GestureDetector gestures;
    private final ScaleGestureDetector pinch;

    private long lastFrameNanos = 0L;
    private boolean paused = false;

    private final Vec3 pickPoint = new Vec3();
    private final Vec3 projected = new Vec3();

    /** Multi-touch panning state. */
    private float lastFocusX, lastFocusY;
    private boolean panning = false;

    public GameView(Context context, Shop shop, Object gameLock, OverlayView overlay,
                    Callbacks callbacks) {
        super(context);
        this.shop = shop;
        this.gameLock = gameLock;
        this.overlay = overlay;
        this.callbacks = callbacks;

        assets.build();

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(new SceneRenderer());
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        camera.lookAtPoint(0f, 1.5f);
        camera.snapToTarget();

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }

            @Override public boolean onScroll(MotionEvent down, MotionEvent move,
                                              final float dx, final float dy) {
                if (move.getPointerCount() > 1) return false;
                queueEvent(new Runnable() {
                    @Override public void run() {
                        camera.orbit(dx * 0.006f, -dy * 0.004f);
                    }
                });
                return true;
            }
        });

        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                final float factor = detector.getScaleFactor();
                queueEvent(new Runnable() {
                    @Override public void run() { camera.zoom(1f / factor); }
                });
                return true;
            }
        });
    }

    public ShopRenderer shopRenderer() { return shopRenderer; }

    public void setSelectedShelf(final int index) {
        queueEvent(new Runnable() {
            @Override public void run() { shopRenderer.selectedShelf = index; }
        });
    }

    /** Eases the camera over to a shelf so the player can see what they just changed. */
    public void focusOn(final float x, final float z) {
        queueEvent(new Runnable() {
            @Override public void run() {
                camera.lookAtPoint(x, z + 2.2f);
                if (camera.distance > 16f) camera.zoom(0.75f);
            }
        });
    }

    public void setPausedSimulation(boolean value) { paused = value; }

    // ------------------------------------------------------------------- touch

    @Override public boolean onTouchEvent(MotionEvent event) {
        pinch.onTouchEvent(event);

        int action = event.getActionMasked();
        if (event.getPointerCount() >= 2) {
            float fx = 0f, fy = 0f;
            for (int i = 0; i < event.getPointerCount(); i++) {
                fx += event.getX(i);
                fy += event.getY(i);
            }
            fx /= event.getPointerCount();
            fy /= event.getPointerCount();

            if (action == MotionEvent.ACTION_POINTER_DOWN || !panning) {
                panning = true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                final float dx = fx - lastFocusX;
                final float dy = fy - lastFocusY;
                queueEvent(new Runnable() {
                    @Override public void run() {
                        camera.pan(dx, dy, Shop.HALF_WIDTH + 2f, Shop.HALF_DEPTH + 2f);
                    }
                });
            }
            lastFocusX = fx;
            lastFocusY = fy;
        } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            panning = false;
        }

        if (!pinch.isInProgress() && !panning) {
            gestures.onTouchEvent(event);
        }
        return true;
    }

    private void handleTap(final float screenX, final float screenY) {
        queueEvent(new Runnable() {
            @Override public void run() {
                if (!camera.screenToFloor(screenX, screenY, 0f, pickPoint)) return;
                final Shop.Selection selection;
                synchronized (gameLock) {
                    selection = shop.pick(pickPoint.x, pickPoint.z);
                }
                post(new Runnable() {
                    @Override public void run() {
                        switch (selection.type) {
                            case SHELF: callbacks.onShelfTapped(selection.index); break;
                            case CUSTOMER: callbacks.onCustomerTapped(selection.index); break;
                            case COUNTER: callbacks.onRegisterTapped(); break;
                            default: callbacks.onEmptyTapped(); break;
                        }
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- rendering

    private final class SceneRenderer implements GLSurfaceView.Renderer {

        @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            // A recreated context invalidates every buffer name from the old one.
            assets.invalidate();
            renderer3d.init();
            assets.upload();
            lastFrameNanos = 0L;
        }

        @Override public void onSurfaceChanged(GL10 unused, int width, int height) {
            android.opengl.GLES20.glViewport(0, 0, width, height);
            camera.setViewport(width, height);
            camera.recompute();
        }

        @Override public void onDrawFrame(GL10 unused) {
            long now = System.nanoTime();
            float dt = lastFrameNanos == 0L ? 0.016f : (now - lastFrameNanos) / 1_000_000_000f;
            lastFrameNanos = now;
            // A long stall (backgrounded, GC) must not teleport the simulation.
            if (dt > 0.1f) dt = 0.1f;

            camera.update(dt);

            Shop.DaySummary summary = null;
            synchronized (gameLock) {
                if (!paused) {
                    shop.update(dt);
                    if (shop.pendingSummary != null) {
                        summary = shop.pendingSummary;
                        shop.pendingSummary = null;
                    }
                }
                shopRenderer.render(shop, camera, dt);
                buildOverlay();
            }

            if (summary != null) {
                final Shop.DaySummary delivered = summary;
                post(new Runnable() {
                    @Override public void run() { callbacks.onDayEnded(delivered); }
                });
            }
        }
    }

    /** Projects world anchors into overlay items. Runs on the GL thread under the lock. */
    private void buildOverlay() {
        overlay.beginUpdate();
        float labelScale = Math.max(0.62f, Math.min(1.2f, 13f / camera.distance));

        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (shelf.owned) {
                if (shelf.product == null) continue;
                if (!camera.worldToScreen(shelf.x, 2.05f, shelf.z, projected)) continue;
                if (projected.z > 1f) continue;
                OverlayView.Item item = overlay.nextItem();
                item.type = OverlayView.TYPE_SHELF;
                item.x = projected.x;
                item.y = projected.y;
                item.scale = labelScale;
                item.color = 0xFF000000 | shelf.product.color;
                item.title = shelf.product.displayName + "  " + Money.exact(shelf.price);
                item.meter = shelf.fillRatio(shop.state);
                item.subtitle = shelf.stock == 0 ? "Empty — tap to restock"
                        : shelf.stock + " in stock";
                item.warn = shelf.stock == 0;
            } else {
                if (!camera.worldToScreen(shelf.x, 0.95f, shelf.z, projected)) continue;
                if (projected.z > 1f) continue;
                OverlayView.Item item = overlay.nextItem();
                item.type = OverlayView.TYPE_BUY;
                item.x = projected.x;
                item.y = projected.y;
                item.scale = labelScale;
                item.title = "Shelf  " + Money.exact(shelf.purchaseCost);
                item.warn = shop.state.money < shelf.purchaseCost;
            }
        }

        for (int i = 0; i < shop.customers.size(); i++) {
            Customer c = shop.customers.get(i);
            boolean waiting = c.state == Customer.State.QUEUEING || c.state == Customer.State.PAYING;
            boolean showing = waiting || c.bubbleTimer > 0f
                    || c.state == Customer.State.LEAVING_UPSET;
            if (!showing) continue;
            if (!camera.worldToScreen(c.pos.x, 2.25f, c.pos.z, projected)) continue;
            if (projected.z > 1f) continue;

            OverlayView.Item item = overlay.nextItem();
            item.type = OverlayView.TYPE_MOOD;
            item.x = projected.x;
            item.y = projected.y;
            item.scale = labelScale;
            item.meter = c.mood;
            item.alpha = Math.min(c.fadeIn, c.fadeOut);
            if (c.state == Customer.State.PAYING) {
                item.title = Money.exact(c.basketTotal());
            } else if (c.bubbleTimer > 0f && !c.basket.isEmpty()) {
                item.title = c.basket.get(c.basket.size() - 1).product.displayName;
            } else {
                item.title = null;
            }
        }

        if (shop.queueLength() > 0) {
            if (camera.worldToScreen(Shop.REGISTER_X, 2.4f, Shop.REGISTER_Z, projected)
                    && projected.z <= 1f) {
                OverlayView.Item item = overlay.nextItem();
                item.type = OverlayView.TYPE_QUEUE;
                item.x = projected.x;
                item.y = projected.y;
                item.scale = labelScale;
                item.title = String.valueOf(shop.queueLength());
            }
        }

        for (int i = 0; i < shop.popups.size(); i++) {
            Shop.Popup p = shop.popups.get(i);
            if (!camera.worldToScreen(p.x, p.y, p.z, projected)) continue;
            if (projected.z > 1f) continue;
            OverlayView.Item item = overlay.nextItem();
            item.type = OverlayView.TYPE_POPUP;
            item.x = projected.x;
            item.y = projected.y;
            item.scale = labelScale;
            item.color = p.color;
            item.alpha = p.alpha();
            item.title = p.text;
        }

        overlay.commit();
    }
}
