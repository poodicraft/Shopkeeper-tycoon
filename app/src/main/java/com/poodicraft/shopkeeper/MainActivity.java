package com.poodicraft.shopkeeper;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.poodicraft.shopkeeper.audio.Sfx;
import com.poodicraft.shopkeeper.game.Customer;
import com.poodicraft.shopkeeper.game.GameState;
import com.poodicraft.shopkeeper.game.Money;
import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.game.SaveManager;
import com.poodicraft.shopkeeper.game.Shelf;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.game.Upgrade;
import com.poodicraft.shopkeeper.ui.Hud;
import com.poodicraft.shopkeeper.ui.OverlayView;
import com.poodicraft.shopkeeper.ui.Theme;

/** Hosts the 3D view, the overlay and the HUD, and owns the game's lifecycle. */
public final class MainActivity extends Activity
        implements Hud.Actions, GameView.Callbacks, Shop.Listener {

    /** Guards the simulation: held by the GL thread each frame and by every UI action. */
    private final Object gameLock = new Object();

    private Shop shop;
    private GameView gameView;
    private OverlayView overlay;
    private Hud hud;
    private Sfx sfx;

    private final Handler handler = new Handler();
    private boolean running = false;

    private final Runnable hudTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            synchronized (gameLock) {
                hud.refresh();
            }
            handler.postDelayed(this, 100L);
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
        root.setBackgroundColor(0xFF0E1220);

        overlay = new OverlayView(this);
        gameView = new GameView(this, shop, gameLock, overlay, this);

        root.addView(gameView, matchParent());
        root.addView(overlay, matchParent());
        hud = new Hud(this, root, this);

        setContentView(root);
        applyImmersiveMode();

        if (firstRun) {
            hud.showBanner("Tap a shelf to stock it and set a price", Theme.GOLD);
        } else if (offlineEarnings > 0) {
            hud.showBanner("Your cashier took " + Money.exact(offlineEarnings) + " while you were out",
                    Theme.GREEN);
        }
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void applyImmersiveMode() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
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

    // ------------------------------------------------------------ view callbacks

    @Override public void onShelfTapped(int shelfIndex) {
        gameView.setSelectedShelf(shelfIndex);
        Shelf shelf = shop.shelf(shelfIndex);
        if (shelf == null) return;

        // An empty shelf with goods waiting in the back restocks on a single tap.
        if (shelf.owned && shelf.product != null && shelf.stock == 0) {
            int moved;
            synchronized (gameLock) {
                moved = shop.restockShelf(shelfIndex);
            }
            if (moved > 0) {
                sfx.play(Sfx.SOUND_RESTOCK);
                return;
            }
        }
        sfx.play(Sfx.SOUND_TAP);
        hud.showShelfSheet(shelfIndex);
    }

    @Override public void onRegisterTapped() {
        boolean served;
        synchronized (gameLock) {
            served = shop.serveAtRegister();
        }
        sfx.play(served ? Sfx.SOUND_TAP : Sfx.SOUND_DENY);
        if (!served) {
            hud.showBanner("Nobody at the till yet", Theme.BLUE);
        }
    }

    @Override public void onCustomerTapped(int customerIndex) {
        String message = null;
        boolean served = false;
        synchronized (gameLock) {
            if (customerIndex >= 0 && customerIndex < shop.customers.size()) {
                Customer c = shop.customers.get(customerIndex);
                if (c.state == Customer.State.PAYING || c.state == Customer.State.QUEUEING) {
                    served = shop.serveAtRegister();
                    message = served ? null : "Serving as fast as we can";
                } else {
                    message = describeShoppingList(c);
                }
            }
        }
        sfx.play(Sfx.SOUND_TAP);
        if (message != null) hud.showBanner(message, Theme.BLUE);
    }

    private String describeShoppingList(Customer c) {
        StringBuilder sb = new StringBuilder("Looking for ");
        int listed = 0;
        for (int i = 0; i < c.wants.size(); i++) {
            Customer.Want want = c.wants.get(i);
            if (want.quantity <= 0) continue;
            if (listed > 0) sb.append(", ");
            sb.append(want.quantity).append("x ").append(want.product.displayName);
            listed++;
        }
        if (listed == 0) return "Heading to the till";
        return sb.toString();
    }

    @Override public void onEmptyTapped() {
        gameView.setSelectedShelf(-1);
        if (hud.isSheetOpen()) hud.hideSheet();
    }

    @Override public void onDayEnded(Shop.DaySummary summary) {
        hud.showDaySummary(summary);
        synchronized (gameLock) {
            SaveManager.save(this, shop);
        }
    }

    // --------------------------------------------------------------- shop events

    @Override public void onSale(float amount, int itemCount) {
        sfx.play(Sfx.SOUND_COIN);
    }

    @Override public void onCustomerLost(String reason) {
        // Deliberately quiet: the floating label in the world already says what happened.
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

    // ------------------------------------------------------------- HUD callbacks

    @Override public Shop shop() { return shop; }

    @Override public void buyShelf(int shelfIndex) {
        boolean ok;
        synchronized (gameLock) {
            ok = shop.buyShelf(shelfIndex);
        }
        sfx.play(ok ? Sfx.SOUND_COIN : Sfx.SOUND_DENY);
        if (ok) {
            gameView.setSelectedShelf(shelfIndex);
            Shelf shelf = shop.shelf(shelfIndex);
            if (shelf != null) gameView.focusOn(shelf.x, shelf.z);
        }
    }

    @Override public void assignProduct(int shelfIndex, ProductType product) {
        synchronized (gameLock) {
            shop.assignProduct(shelfIndex, product);
        }
        sfx.play(Sfx.SOUND_TAP);
    }

    @Override public void setPrice(int shelfIndex, float price) {
        synchronized (gameLock) {
            shop.setPrice(shelfIndex, price);
        }
    }

    @Override public void orderStock(ProductType product, int quantity) {
        boolean ok;
        synchronized (gameLock) {
            ok = shop.orderStock(product, quantity);
        }
        sfx.play(ok ? Sfx.SOUND_RESTOCK : Sfx.SOUND_DENY);
    }

    @Override public void quickRestock(int shelfIndex) {
        int moved;
        synchronized (gameLock) {
            moved = shop.quickRestock(shelfIndex);
        }
        sfx.play(moved > 0 ? Sfx.SOUND_RESTOCK : Sfx.SOUND_DENY);
    }

    @Override public void buyUpgrade(Upgrade upgrade) {
        boolean ok;
        synchronized (gameLock) {
            ok = shop.state.buyUpgrade(upgrade);
            if (ok) shop.syncStaff();
        }
        sfx.play(ok ? Sfx.SOUND_COIN : Sfx.SOUND_DENY);
        if (ok) {
            hud.showBanner(upgrade.displayName + " upgraded", Theme.PURPLE);
        }
    }

    @Override public void setAutoRestock(boolean enabled) {
        synchronized (gameLock) {
            shop.state.autoRestockEnabled = enabled;
        }
        sfx.play(Sfx.SOUND_TAP);
    }

    @Override public void setSound(boolean enabled) {
        synchronized (gameLock) {
            shop.state.soundEnabled = enabled;
        }
        sfx.setEnabled(enabled);
        if (enabled) sfx.play(Sfx.SOUND_TAP);
    }

    @Override public void focusShelf(int shelfIndex) {
        Shelf shelf = shop.shelf(shelfIndex);
        if (shelf == null) return;
        gameView.setSelectedShelf(shelfIndex);
        gameView.focusOn(shelf.x, shelf.z);
    }

    @Override public void resetGame() {
        synchronized (gameLock) {
            SaveManager.clear(this);
        }
        // A clean restart is simplest and safest with a live GL context around.
        finish();
        startActivity(getIntent());
    }
}
