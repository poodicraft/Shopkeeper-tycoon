package com.poodicraft.shopkeeper;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.poodicraft.shopkeeper.audio.Sfx;
import com.poodicraft.shopkeeper.game.GameState;
import com.poodicraft.shopkeeper.game.Interaction;
import com.poodicraft.shopkeeper.game.Money;
import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.game.SaveManager;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.game.Upgrade;
import com.poodicraft.shopkeeper.ui.ActionButton;
import com.poodicraft.shopkeeper.ui.Hud;
import com.poodicraft.shopkeeper.ui.Joystick;
import com.poodicraft.shopkeeper.ui.OverlayView;
import com.poodicraft.shopkeeper.ui.Theme;

/** Hosts the 3D view, the controls and the HUD, and owns the game's lifecycle. */
public final class MainActivity extends Activity
        implements Hud.Actions, GameView.Callbacks, Shop.Listener {

    /** Guards the simulation: held by the GL thread each frame and by every UI action. */
    private final Object gameLock = new Object();

    private Shop shop;
    private GameView gameView;
    private OverlayView overlay;
    private Joystick joystick;
    private ActionButton actionButton;
    private Hud hud;
    private Sfx sfx;
    private View loadingScreen;

    private final Handler handler = new Handler();
    private boolean running = false;

    private final Runnable hudTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            synchronized (gameLock) {
                hud.refresh();
            }
            handler.postDelayed(this, 110L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        boolean firstRun = !SaveManager.hasSave(this);
        shop = new Shop(new GameState());
        shop.listener = this;
        double offlineEarnings = SaveManager.load(this, shop);

        sfx = new Sfx();
        sfx.setEnabled(shop.state.soundEnabled);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1018);

        overlay = new OverlayView(this);
        joystick = new Joystick(this);
        actionButton = new ActionButton(this);
        gameView = new GameView(this, shop, gameLock, overlay, joystick, actionButton, this);

        root.addView(gameView, matchParent());
        root.addView(overlay, matchParent());
        root.addView(joystick, matchParent());
        root.addView(actionButton, matchParent());
        hud = new Hud(this, root, this);
        loadingScreen = buildLoadingScreen();
        root.addView(loadingScreen, matchParent());

        setContentView(root);
        applyImmersiveMode();

        if (firstRun) {
            hud.showBanner("Fetch a crate from the stockroom and fill a shelf", Theme.GOLD);
        } else if (offlineEarnings > 0) {
            hud.showBanner("Your cashier took " + Money.exact(offlineEarnings)
                    + " while you were out", Theme.GREEN);
        }
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Covers the first second or two while textures and meshes are generated. */
    private View buildLoadingScreen() {
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(0xFF0B1018);
        layer.setClickable(true);

        LinearLayout column = Theme.column(this);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = Theme.text(this, "Shopkeeper", 30f, 0xFFF2EFE7, true);
        TextView subtitle = Theme.text(this, "Opening up…", 14f, 0xFF7E8B99, false);
        column.addView(title);
        column.addView(Theme.spacer(this, 0, 10));
        column.addView(subtitle);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        layer.addView(column, lp);
        return layer;
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override protected void onResume() {
        super.onResume();
        running = true;
        gameView.onResume();
        handler.post(hudTick);
    }

    @Override protected void onPause() {
        super.onPause();
        running = false;
        handler.removeCallbacks(hudTick);
        synchronized (gameLock) {
            SaveManager.save(this, shop);
        }
        gameView.onPause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (sfx != null) sfx.release();
    }

    @Override public void onBackPressed() {
        if (hud.onBackPressed()) return;
        super.onBackPressed();
    }

    // ----------------------------------------------------------- view callbacks

    @Override public void onAssetsReady() {
        loadingScreen.animate().alpha(0f).setDuration(420).withEndAction(new Runnable() {
            @Override public void run() { loadingScreen.setVisibility(View.GONE); }
        }).start();
    }

    @Override public void onActionPressed(Interaction.Kind kind, int shelfIndex) {
        switch (kind) {
            case COLLECT_STOCK:
                sfx.play(Sfx.SOUND_TAP);
                hud.showStockroomSheet();
                break;

            case RETURN_CRATE: {
                boolean ok;
                synchronized (gameLock) { ok = shop.returnCrate(); }
                sfx.play(ok ? Sfx.SOUND_RESTOCK : Sfx.SOUND_DENY);
                break;
            }

            case STOCK_SHELF: {
                boolean ok;
                synchronized (gameLock) { ok = shop.stockShelf(shelfIndex); }
                sfx.play(ok ? Sfx.SOUND_RESTOCK : Sfx.SOUND_DENY);
                break;
            }

            case SERVE: {
                boolean ok;
                synchronized (gameLock) { ok = shop.serveCustomer(); }
                if (!ok) {
                    sfx.play(Sfx.SOUND_DENY);
                    hud.showBanner("Nobody at the till yet", Theme.BLUE);
                }
                break;
            }

            case BUY_SHELF: {
                boolean ok;
                synchronized (gameLock) { ok = shop.buyShelf(shelfIndex); }
                sfx.play(ok ? Sfx.SOUND_COIN : Sfx.SOUND_DENY);
                if (!ok) hud.showBanner("Not enough money for shelving", Theme.RED);
                break;
            }

            case TERMINAL:
                sfx.play(Sfx.SOUND_TAP);
                hud.showTerminalSheet();
                break;

            case EDIT_SHELF:
                sfx.play(Sfx.SOUND_TAP);
                hud.showShelfSheet(shelfIndex);
                break;

            default:
                sfx.play(Sfx.SOUND_DENY);
                break;
        }
    }

    @Override public void onDayEnded(Shop.DaySummary summary) {
        hud.showDaySummary(summary);
        synchronized (gameLock) {
            SaveManager.save(this, shop);
        }
    }

    // ----------------------------------------------------------- shop events

    @Override public void onSale(float amount, int itemCount) {
        sfx.play(Sfx.SOUND_COIN);
    }

    @Override public void onScanBeep() {
        sfx.play(Sfx.SOUND_TAP);
    }

    @Override public void onCustomerLost(String reason) {
        // The floating label in the world already says what happened.
    }

    @Override public void onStockPlaced(int units) {
        sfx.play(Sfx.SOUND_RESTOCK);
    }

    @Override public void onLevelUp(final int newLevel) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                sfx.play(Sfx.SOUND_LEVEL);
                ProductType unlocked = null;
                for (int i = 0; i < ProductType.ALL.length; i++) {
                    if (ProductType.ALL[i].unlockLevel == newLevel) unlocked = ProductType.ALL[i];
                }
                hud.showBanner(unlocked == null
                        ? "Level " + newLevel + "!"
                        : "Level " + newLevel + " — " + unlocked.displayName + " unlocked",
                        Theme.GOLD);
            }
        });
    }

    // ------------------------------------------------------------ HUD actions

    @Override public Shop shop() { return shop; }

    @Override public void collectStock(ProductType product) {
        boolean ok;
        synchronized (gameLock) { ok = shop.collectStock(product); }
        sfx.play(ok ? Sfx.SOUND_RESTOCK : Sfx.SOUND_DENY);
    }

    @Override public void orderStock(ProductType product, int quantity) {
        boolean ok;
        synchronized (gameLock) { ok = shop.orderStock(product, quantity); }
        sfx.play(ok ? Sfx.SOUND_COIN : Sfx.SOUND_DENY);
    }

    @Override public void buyUpgrade(Upgrade upgrade) {
        boolean ok;
        synchronized (gameLock) {
            ok = shop.state.buyUpgrade(upgrade);
            if (ok) shop.syncStaff();
        }
        sfx.play(ok ? Sfx.SOUND_COIN : Sfx.SOUND_DENY);
        if (ok) hud.showBanner(upgrade.displayName + " upgraded", Theme.PURPLE);
    }

    @Override public void assignProduct(int shelfIndex, ProductType product) {
        synchronized (gameLock) { shop.assignProduct(shelfIndex, product); }
        sfx.play(Sfx.SOUND_TAP);
    }

    @Override public void setPrice(int shelfIndex, float price) {
        synchronized (gameLock) { shop.setPrice(shelfIndex, price); }
    }

    @Override public void setAutoRestock(boolean enabled) {
        synchronized (gameLock) { shop.state.autoRestockEnabled = enabled; }
        sfx.play(Sfx.SOUND_TAP);
    }

    @Override public void setSound(boolean enabled) {
        synchronized (gameLock) { shop.state.soundEnabled = enabled; }
        sfx.setEnabled(enabled);
        if (enabled) sfx.play(Sfx.SOUND_TAP);
    }

    @Override public void resetGame() {
        synchronized (gameLock) { SaveManager.clear(this); }
        finish();
        startActivity(getIntent());
    }
}
