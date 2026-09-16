package com.poodicraft.shopkeeper;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import com.poodicraft.shopkeeper.art.LabelPainter;
import com.poodicraft.shopkeeper.art.TextureFactory;
import com.poodicraft.shopkeeper.game.Customer;
import com.poodicraft.shopkeeper.game.Interaction;
import com.poodicraft.shopkeeper.game.Money;
import com.poodicraft.shopkeeper.game.Player;
import com.poodicraft.shopkeeper.game.Shelf;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.gl.Camera;
import com.poodicraft.shopkeeper.gl.RenderPipeline;
import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.math.Vec3;
import com.poodicraft.shopkeeper.ui.ActionButton;
import com.poodicraft.shopkeeper.ui.Joystick;
import com.poodicraft.shopkeeper.ui.OverlayView;
import com.poodicraft.shopkeeper.world.SceneRenderer;
import com.poodicraft.shopkeeper.world.ShopLayout;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The 3D view: owns the GL thread, the third-person camera, touch input and the
 * frame loop.
 *
 * <p>Simulation and rendering both run on the GL thread while holding the shared
 * game lock, so UI-thread actions can mutate the shop safely.
 */
public final class GameView extends GLSurfaceView {

    /** Events raised back to the activity, always on the UI thread. */
    public interface Callbacks {
        void onActionPressed(Interaction.Kind kind, int shelfIndex);
        void onDayEnded(Shop.DaySummary summary);
        void onAssetsReady();
    }

    private final Shop shop;
    private final Object gameLock;
    private final OverlayView overlay;
    private final Joystick joystick;
    private final ActionButton actionButton;
    private final Callbacks callbacks;

    private final RenderPipeline pipeline = new RenderPipeline();
    private final SceneRenderer scene = new SceneRenderer();
    public final Camera camera = new Camera();

    private final TextureFactory textures = new TextureFactory();
    private volatile boolean assetsGenerated = false;
    private volatile boolean assetsAnnounced = false;

    private long lastFrameNanos = 0L;
    private boolean paused = false;

    private final Vec3 projected = new Vec3();

    // Touch state.
    private int lookPointerId = -1;
    private float lastLookX, lastLookY;
    private int pinchPointerA = -1, pinchPointerB = -1;
    private float lastPinchDistance = 0f;

    public GameView(Context context, Shop shop, Object gameLock, OverlayView overlay,
                    Joystick joystick, ActionButton actionButton, Callbacks callbacks) {
        super(context);
        this.shop = shop;
        this.gameLock = gameLock;
        this.overlay = overlay;
        this.joystick = joystick;
        this.actionButton = actionButton;
        this.callbacks = callbacks;

        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 0, 24, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(new SceneGLRenderer());
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        camera.roomHalfWidth = ShopLayout.HALF_WIDTH;
        camera.roomHalfDepth = ShopLayout.HALF_DEPTH;
        camera.roomHeight = ShopLayout.CEILING_HEIGHT;
        camera.follow(shop.player.position.x, 1.35f, shop.player.position.z);
        camera.yaw = shop.player.heading;
        camera.snap();

        startAssetGeneration();
    }

    /**
     * Generates textures and meshes off the GL thread.
     *
     * <p>Both are pure computation; doing them on the GL thread would stall the
     * first frames for a second or more on a phone.
     */
    private void startAssetGeneration() {
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                textures.generateAll();
                LabelPainter.paintLabels(textures.albedo);
                scene.buildMeshes();
                pipeline.setTextures(textures.albedo, textures.normal);
                assetsGenerated = true;
            }
        }, "asset-build");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    public boolean assetsReady() { return assetsGenerated; }

    public RenderPipeline pipeline() { return pipeline; }

    public SceneRenderer scene() { return scene; }

    public void setPausedSimulation(boolean value) { paused = value; }

    // ------------------------------------------------------------------ input

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                int id = event.getPointerId(index);
                float x = event.getX(index), y = event.getY(index);

                if (actionButton.contains(x, y)) {
                    fireAction();
                    return true;
                }
                if (!joystick.isActive() && joystick.claims(x, y)) {
                    joystick.begin(id, x, y);
                    return true;
                }
                if (lookPointerId < 0) {
                    lookPointerId = id;
                    lastLookX = x;
                    lastLookY = y;
                } else if (pinchPointerA < 0) {
                    pinchPointerA = lookPointerId;
                    pinchPointerB = id;
                    lastPinchDistance = pointerDistance(event, pinchPointerA, pinchPointerB);
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    if (id == joystick.activePointer()) {
                        joystick.drag(event.getX(i), event.getY(i));
                    } else if (id == lookPointerId && pinchPointerB < 0) {
                        float dx = event.getX(i) - lastLookX;
                        float dy = event.getY(i) - lastLookY;
                        lastLookX = event.getX(i);
                        lastLookY = event.getY(i);
                        orbit(dx, dy);
                    }
                }
                if (pinchPointerB >= 0) {
                    float distance = pointerDistance(event, pinchPointerA, pinchPointerB);
                    if (distance > 0f && lastPinchDistance > 0f) {
                        final float factor = lastPinchDistance / distance;
                        queueEvent(new Runnable() {
                            @Override public void run() { camera.zoom(factor); }
                        });
                    }
                    lastPinchDistance = distance;
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int index = event.getActionIndex();
                int id = event.getPointerId(index);
                if (id == joystick.activePointer()) joystick.end();
                if (id == lookPointerId) lookPointerId = -1;
                if (id == pinchPointerA || id == pinchPointerB) {
                    pinchPointerA = -1;
                    pinchPointerB = -1;
                    lastPinchDistance = 0f;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    joystick.end();
                    lookPointerId = -1;
                    pinchPointerA = -1;
                    pinchPointerB = -1;
                }
                return true;
            }
        }
        return true;
    }

    private static float pointerDistance(MotionEvent event, int idA, int idB) {
        int a = event.findPointerIndex(idA);
        int b = event.findPointerIndex(idB);
        if (a < 0 || b < 0) return 0f;
        float dx = event.getX(a) - event.getX(b);
        float dy = event.getY(a) - event.getY(b);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void orbit(final float dx, final float dy) {
        queueEvent(new Runnable() {
            @Override public void run() {
                // Dragging right swings the view right, which means yaw decreases.
                camera.orbit(-dx * 0.0055f, dy * 0.0042f);
            }
        });
    }

    private void fireAction() {
        actionButton.flashPress();
        final Interaction.Kind kind;
        final int shelfIndex;
        synchronized (gameLock) {
            kind = shop.interaction.kind;
            shelfIndex = shop.interaction.shelfIndex;
            if (!shop.interaction.enabled) {
                post(new Runnable() {
                    @Override public void run() {
                        callbacks.onActionPressed(Interaction.Kind.NONE, -1);
                    }
                });
                return;
            }
        }
        post(new Runnable() {
            @Override public void run() { callbacks.onActionPressed(kind, shelfIndex); }
        });
    }

    // -------------------------------------------------------------- rendering

    private final class SceneGLRenderer implements GLSurfaceView.Renderer {

        @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            // A recreated context invalidates every GPU name from the old one.
            pipeline.invalidate();
            scene.invalidate();
            pipeline.init();
            if (assetsGenerated) pipeline.setTextures(textures.albedo, textures.normal);
            lastFrameNanos = 0L;
        }

        @Override public void onSurfaceChanged(GL10 unused, int width, int height) {
            camera.setViewport(width, height);
            camera.recompute();
            pipeline.resize(width, height);
        }

        @Override public void onDrawFrame(GL10 unused) {
            long now = System.nanoTime();
            float dt = lastFrameNanos == 0L ? 0.016f : (now - lastFrameNanos) / 1_000_000_000f;
            lastFrameNanos = now;
            // A long stall must not teleport the simulation.
            if (dt > 0.1f) dt = 0.1f;

            if (!assetsGenerated || !scene.isBuilt()) {
                GLES30.glClearColor(0.05f, 0.07f, 0.10f, 1f);
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
                return;
            }
            if (!assetsAnnounced) {
                assetsAnnounced = true;
                post(new Runnable() {
                    @Override public void run() { callbacks.onAssetsReady(); }
                });
            }

            Shop.DaySummary summary = null;
            synchronized (gameLock) {
                if (!paused) {
                    stepPlayer(dt);
                    shop.update(dt);
                    if (shop.pendingSummary != null) {
                        summary = shop.pendingSummary;
                        shop.pendingSummary = null;
                    }
                }
                followPlayer(dt);
                camera.update(dt);

                scene.updateEnvironment(shop.state);
                pipeline.beginFrame();
                scene.submit(pipeline, shop, camera);
                pipeline.render(camera, scene.environment, dt);

                buildOverlay();
                publishAction();
            }

            if (summary != null) {
                final Shop.DaySummary delivered = summary;
                post(new Runnable() {
                    @Override public void run() { callbacks.onDayEnded(delivered); }
                });
            }
        }
    }

    /** Applies stick input in camera space and walks the player. */
    private void stepPlayer(float dt) {
        float stickX = joystick.outputX;
        float stickY = joystick.outputY;
        float worldX = camera.flatRight.x * stickX + camera.flatForward.x * stickY;
        float worldZ = camera.flatRight.z * stickX + camera.flatForward.z * stickY;
        shop.player.move(worldX, worldZ, joystick.running, dt, shop.collision);
    }

    /** Keeps the camera on the player, framed a little above their shoulders. */
    private void followPlayer(float dt) {
        Player player = shop.player;
        float targetHeight = 1.30f * player.appearance.height;
        // Drop the framing while crouched so the character stays in shot.
        targetHeight -= player.animator.crouch * 0.28f;
        camera.follow(player.position.x, targetHeight, player.position.z);
    }

    /** Projects world anchors into overlay items. Runs on the GL thread under the lock. */
    private void buildOverlay() {
        overlay.beginUpdate();
        float labelScale = MathUtil.clamp(3.6f / Math.max(1.2f, camera.distance), 0.75f, 1.25f);

        // Prompt on whatever the player can act on.
        Interaction interaction = shop.interaction;
        if (interaction.isAvailable()
                && camera.worldToScreen(interaction.anchorX, interaction.anchorY,
                        interaction.anchorZ, projected)
                && projected.z <= 1f) {
            OverlayView.Item item = overlay.nextItem();
            item.type = OverlayView.TYPE_PROMPT;
            item.x = projected.x;
            item.y = projected.y;
            item.title = interaction.label;
            item.subtitle = interaction.detail;
            item.warn = !interaction.enabled;
        }

        // Shelf tags, so the player can read prices from across the aisle.
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            float anchorY = ShopLayout.SHELF_HEIGHT + 0.22f;
            if (!camera.worldToScreen(shelf.x, anchorY, shelf.z, projected)) continue;
            if (projected.z > 1f) continue;
            float distance = (float) Math.hypot(shelf.x - camera.eye.x, shelf.z - camera.eye.z);
            if (distance > 9f) continue;

            OverlayView.Item item = overlay.nextItem();
            item.type = OverlayView.TYPE_SHELF_TAG;
            item.x = projected.x;
            item.y = projected.y;
            item.scale = labelScale;
            item.alpha = MathUtil.clamp((9f - distance) / 2.5f, 0f, 1f);
            if (!shelf.owned) {
                item.title = "Empty slot  " + Money.exact(shelf.purchaseCost);
                item.meter = 0f;
            } else if (shelf.product == null) {
                item.title = "Unassigned";
                item.meter = 0f;
            } else {
                item.title = shelf.product.displayName + "  " + Money.exact(shelf.price);
                item.meter = shelf.fillRatio(shop.state);
            }
        }

        // Shoppers: a mood ring while queueing, a bubble for what they just said.
        for (int i = 0; i < shop.customers.size(); i++) {
            Customer c = shop.customers.get(i);
            float alpha = Math.min(c.fadeIn, c.fadeOut);
            if (alpha <= 0.05f) continue;
            float head = c.headHeight() + 0.30f;

            boolean queueing = c.state == Customer.State.QUEUEING
                    || c.state == Customer.State.BEING_SERVED;
            if (queueing && camera.worldToScreen(c.position.x, head, c.position.z, projected)
                    && projected.z <= 1f) {
                OverlayView.Item item = overlay.nextItem();
                item.type = OverlayView.TYPE_MOOD;
                item.x = projected.x;
                item.y = projected.y;
                item.scale = labelScale;
                item.alpha = alpha;
                item.meter = c.mood;
            }
            if (c.bubbleTimer > 0f && c.bubbleText != null
                    && camera.worldToScreen(c.position.x, head + 0.22f, c.position.z, projected)
                    && projected.z <= 1f) {
                OverlayView.Item item = overlay.nextItem();
                item.type = OverlayView.TYPE_BUBBLE;
                item.x = projected.x;
                item.y = projected.y;
                item.scale = labelScale;
                item.alpha = alpha * MathUtil.clamp(c.bubbleTimer, 0f, 1f);
                item.color = c.state == Customer.State.LEAVING_UPSET ? 0xFFC05048 : 0xFF2E7D8E;
                item.title = c.bubbleText;
            }
        }

        // Running total while serving, floating over the till.
        Customer serving = shop.servingCustomer();
        if (serving != null
                && camera.worldToScreen(ShopLayout.TILL_X, 1.75f, ShopLayout.TILL_Z, projected)
                && projected.z <= 1f) {
            OverlayView.Item item = overlay.nextItem();
            item.type = OverlayView.TYPE_BUBBLE;
            item.x = projected.x;
            item.y = projected.y;
            item.color = 0xFF2E9E74;
            item.title = serving.scanned + "/" + serving.basketCount() + "  "
                    + Money.exact(serving.scannedTotal());
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

    private String lastActionLabel = null;
    private String lastActionDetail = null;
    private boolean lastActionEnabled = false;
    private boolean lastActionVisible = false;

    /**
     * Mirrors the current interaction onto the action button.
     *
     * <p>Only posts when something actually changed: the prompt is stable for
     * seconds at a time, and posting a fresh runnable every frame would hand the UI
     * thread sixty allocations a second for nothing.
     */
    private void publishAction() {
        final Interaction interaction = shop.interaction;
        final boolean visible = interaction.isAvailable();
        final String label = interaction.label;
        final String detail = interaction.detail;
        final boolean enabled = interaction.enabled;

        if (visible == lastActionVisible && enabled == lastActionEnabled
                && equalText(label, lastActionLabel) && equalText(detail, lastActionDetail)) {
            return;
        }
        lastActionVisible = visible;
        lastActionEnabled = enabled;
        lastActionLabel = label;
        lastActionDetail = detail;

        post(new Runnable() {
            @Override public void run() {
                actionButton.setAction(label, detail, enabled, visible);
            }
        });
    }

    private static boolean equalText(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
