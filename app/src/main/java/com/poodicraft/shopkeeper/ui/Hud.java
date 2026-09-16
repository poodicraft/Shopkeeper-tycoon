package com.poodicraft.shopkeeper.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.poodicraft.shopkeeper.game.GameState;
import com.poodicraft.shopkeeper.game.Money;
import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.game.Shelf;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.game.Upgrade;

/**
 * The 2D interface: status bar, the crate indicator, and the panels the player
 * opens by walking up to something and pressing the action button.
 *
 * <p>Built in code rather than XML so the UI lives beside the game logic.
 */
public final class Hud {

    /** Everything the HUD can ask the game to do. */
    public interface Actions {
        Shop shop();
        void collectStock(ProductType product);
        void orderStock(ProductType product, int quantity);
        void buyUpgrade(Upgrade upgrade);
        void assignProduct(int shelfIndex, ProductType product);
        void setPrice(int shelfIndex, float price);
        void setAutoRestock(boolean enabled);
        void setSound(boolean enabled);
        void resetGame();
    }

    private final Activity activity;
    private final Actions actions;
    private final FrameLayout root;

    private TextView moneyText, dayText, clockText, levelText, satisfactionText;
    private TextView queueText, carryText;
    private View xpBarFill, dayBarFill;

    private FrameLayout sheetScrim;
    private MaxHeightLinearLayout sheet;
    private LinearLayout sheetBody;
    private TextView sheetTitle;
    private final java.util.ArrayList<Runnable> sheetRefreshers = new java.util.ArrayList<Runnable>();
    private boolean sheetOpen = false;

    private FrameLayout dialogLayer;
    private LinearLayout banner;
    private TextView bannerText;
    private long bannerHideAt = 0L;

    public Hud(Activity activity, FrameLayout root, Actions actions) {
        this.activity = activity;
        this.root = root;
        this.actions = actions;
        buildStatusBar();
        buildCarryBadge();
        buildSheet();
        buildBanner();
        dialogLayer = new FrameLayout(activity);
        root.addView(dialogLayer, matchParent());
        dialogLayer.setVisibility(View.GONE);
    }

    private int dp(float v) { return Theme.dp(activity, v); }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // --------------------------------------------------------------- status

    private void buildStatusBar() {
        LinearLayout bar = Theme.row(activity);
        bar.setPadding(dp(12), dp(10), dp(12), 0);

        LinearLayout moneyCard = card();
        moneyText = Theme.text(activity, "$0", 17f, Theme.GOLD, true);
        moneyCard.addView(moneyText);
        bar.addView(moneyCard);
        bar.addView(Theme.spacer(activity, 8, 0));

        LinearLayout dayCard = card();
        LinearLayout dayCol = Theme.column(activity);
        LinearLayout dayRow = Theme.row(activity);
        dayText = Theme.text(activity, "Day 1", 13f, Theme.HUD_TEXT, true);
        clockText = Theme.text(activity, "  08:00", 13f, 0xFF9FB0C4, true);
        dayRow.addView(dayText);
        dayRow.addView(clockText);
        dayCol.addView(dayRow);
        dayCol.addView(Theme.spacer(activity, 0, 4));
        dayCol.addView(meter(dp(76), Theme.BLUE));
        dayBarFill = lastMeterFill;
        dayCard.addView(dayCol);
        bar.addView(dayCard);

        bar.addView(Theme.flexSpacer(activity));

        LinearLayout statusCard = card();
        LinearLayout statusCol = Theme.column(activity);
        LinearLayout levelRow = Theme.row(activity);
        levelText = Theme.text(activity, "Lv 1", 13f, Theme.GOLD, true);
        satisfactionText = Theme.text(activity, "  75%", 13f, Theme.GREEN, true);
        levelRow.addView(levelText);
        levelRow.addView(satisfactionText);
        statusCol.addView(levelRow);
        statusCol.addView(Theme.spacer(activity, 0, 4));
        statusCol.addView(meter(dp(72), Theme.GOLD));
        xpBarFill = lastMeterFill;
        statusCard.addView(statusCol);
        bar.addView(statusCard);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        root.addView(bar, lp);

        queueText = Theme.text(activity, "", 13f, 0xFFFFFFFF, true);
        queueText.setPadding(dp(12), dp(6), dp(12), dp(6));
        queueText.setVisibility(View.GONE);
        FrameLayout.LayoutParams qlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        qlp.topMargin = dp(68);
        root.addView(queueText, qlp);
    }

    /** Shows what is in the crate on the player's shoulder. */
    private void buildCarryBadge() {
        carryText = Theme.text(activity, "", 13f, 0xFF1B1F27, true);
        carryText.setBackground(Theme.roundRect(activity, Theme.withAlpha(Theme.GOLD, 0xF0), 14f));
        carryText.setPadding(dp(14), dp(8), dp(14), dp(8));
        carryText.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.topMargin = dp(68);
        lp.leftMargin = dp(12);
        root.addView(carryText, lp);
    }

    private LinearLayout card() {
        LinearLayout c = Theme.row(activity);
        c.setBackground(Theme.roundRect(activity, Theme.HUD_CARD, 12f));
        c.setPadding(dp(12), dp(8), dp(12), dp(8));
        return c;
    }

    private View lastMeterFill;

    private View meter(int widthPx, int color) {
        FrameLayout track = new FrameLayout(activity);
        track.setBackground(Theme.roundRect(activity, 0x33FFFFFF, 3f));
        View fill = new View(activity);
        fill.setBackground(Theme.roundRect(activity, color, 3f));
        track.addView(fill, new FrameLayout.LayoutParams(widthPx / 2, dp(5)));
        track.setLayoutParams(new LinearLayout.LayoutParams(widthPx, dp(5)));
        fill.setTag(Integer.valueOf(widthPx));
        lastMeterFill = fill;
        return track;
    }

    private void setMeter(View fill, float ratio) {
        if (fill == null) return;
        Object tag = fill.getTag();
        int full = tag instanceof Integer ? ((Integer) tag).intValue() : dp(72);
        ViewGroup.LayoutParams lp = fill.getLayoutParams();
        int want = Math.max(dp(3), (int) (full * Math.max(0f, Math.min(1f, ratio))));
        if (lp.width != want) {
            lp.width = want;
            fill.setLayoutParams(lp);
        }
    }

    // ---------------------------------------------------------------- sheet

    private void buildSheet() {
        sheetScrim = new FrameLayout(activity);
        sheetScrim.setBackgroundColor(Theme.SCRIM);
        sheetScrim.setVisibility(View.GONE);
        sheetScrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hideSheet(); }
        });
        root.addView(sheetScrim, matchParent());

        sheet = new MaxHeightLinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(Theme.roundRect(activity, Theme.PAPER, 20f));
        sheet.setPadding(dp(16), dp(12), dp(16), dp(16));
        sheet.setClickable(true);

        View grabber = new View(activity);
        grabber.setBackground(Theme.roundRect(activity, 0x33000000, 3f));
        LinearLayout.LayoutParams glp = Theme.lp(dp(38), dp(5));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = dp(10);
        sheet.addView(grabber, glp);

        LinearLayout header = Theme.row(activity);
        sheetTitle = Theme.text(activity, "", 19f, Theme.INK, true);
        header.addView(sheetTitle);
        header.addView(Theme.flexSpacer(activity));
        TextView close = Theme.text(activity, "✕", 18f, Theme.INK_SOFT, true);
        close.setPadding(dp(12), dp(4), dp(4), dp(8));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hideSheet(); }
        });
        header.addView(close);
        sheet.addView(header);
        sheet.addView(Theme.spacer(activity, 0, 8));

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        sheetBody = Theme.column(activity);
        scroll.addView(sheetBody, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(scroll, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        root.addView(sheet, lp);
        sheet.setVisibility(View.GONE);
    }

    public boolean isSheetOpen() { return sheetOpen; }

    public void hideSheet() {
        if (!sheetOpen) return;
        sheetOpen = false;
        sheetRefreshers.clear();
        sheet.animate().translationY(sheet.getHeight()).setDuration(160)
                .withEndAction(new Runnable() {
                    @Override public void run() { sheet.setVisibility(View.GONE); }
                }).start();
        sheetScrim.setVisibility(View.GONE);
    }

    private void openSheet(String title) {
        sheetTitle.setText(title);
        sheetRefreshers.clear();
        sheetBody.removeAllViews();
        if (!sheetOpen) {
            sheetOpen = true;
            sheet.setVisibility(View.VISIBLE);
            sheet.setTranslationY(dp(380));
            sheet.animate().translationY(0f).setDuration(200).start();
            sheetScrim.setVisibility(View.VISIBLE);
        }
        sheet.setMaxHeightPx(root.getHeight() * 3 / 4);
    }

    // -------------------------------------------------------- stockroom panel

    /** Opened at the stockroom hatch: pick what to carry out. */
    public void showStockroomSheet() {
        final Shop shop = actions.shop();
        openSheet("Stockroom");
        sheetBody.addView(body("Pick up a crate and carry it to a shelf. You can hold "
                + shop.player.carryCapacity(shop.state) + " units at a time."));
        sheetBody.addView(Theme.spacer(activity, 0, 12));

        boolean any = false;
        for (int i = 0; i < ProductType.ALL.length; i++) {
            final ProductType product = ProductType.ALL[i];
            int units = shop.state.stockOf(product);
            if (units <= 0) continue;
            any = true;
            LinearLayout row = productRow(product, units + " units in the back");
            TextView take = Theme.button(activity, "Carry", Theme.GREEN);
            take.setTextSize(12.5f);
            take.setPadding(dp(16), dp(9), dp(16), dp(9));
            take.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    actions.collectStock(product);
                    hideSheet();
                }
            });
            row.addView(take);
            sheetBody.addView(row, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            sheetBody.addView(Theme.spacer(activity, 0, 8));
        }
        if (!any) {
            sheetBody.addView(body("Nothing back here. Order stock at the office desk "
                    + "in the back corner."));
        }
    }

    private LinearLayout productRow(ProductType product, String detail) {
        LinearLayout row = Theme.row(activity);
        row.setBackground(Theme.roundRect(activity, Theme.PANEL, 14f));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        View dot = new View(activity);
        dot.setBackground(Theme.roundRect(activity, 0xFF000000 | product.color, 8f));
        row.addView(dot, Theme.lp(dp(16), dp(16)));
        row.addView(Theme.spacer(activity, 10, 0));

        LinearLayout info = Theme.column(activity);
        info.addView(Theme.text(activity, product.displayName, 15f, Theme.INK, true));
        info.addView(Theme.text(activity, detail, 11.5f, Theme.INK_SOFT, false));
        row.addView(info, Theme.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    // --------------------------------------------------------- terminal panel

    /** Opened at the back-office desk: ordering and upgrades. */
    public void showTerminalSheet() {
        openSheet("Back office");
        buildTerminalTabs(0);
    }

    private void buildTerminalTabs(final int tab) {
        sheetRefreshers.clear();
        sheetBody.removeAllViews();

        LinearLayout tabs = Theme.row(activity);
        tabs.addView(tabButton("Order stock", tab == 0, 0),
                Theme.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabs.addView(Theme.spacer(activity, 8, 0));
        tabs.addView(tabButton("Upgrades", tab == 1, 1),
                Theme.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabs.addView(Theme.spacer(activity, 8, 0));
        tabs.addView(tabButton("Shop", tab == 2, 2),
                Theme.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sheetBody.addView(tabs, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        sheetBody.addView(Theme.spacer(activity, 0, 12));

        if (tab == 0) buildOrderList();
        else if (tab == 1) buildUpgradeList();
        else buildShopStats();
    }

    private TextView tabButton(String label, boolean selected, final int index) {
        TextView tv = Theme.button(activity, label, selected ? Theme.INK : Theme.DISABLED);
        tv.setTextSize(13f);
        tv.setPadding(dp(10), dp(9), dp(10), dp(9));
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { buildTerminalTabs(index); }
        });
        return tv;
    }

    private void buildOrderList() {
        final Shop shop = actions.shop();
        sheetBody.addView(body("Ordered stock is delivered to the stockroom. Carry it "
                + "out yourself, or hire a stocker to do the walking."));
        sheetBody.addView(Theme.spacer(activity, 0, 10));

        for (int i = 0; i < ProductType.ALL.length; i++) {
            final ProductType product = ProductType.ALL[i];
            if (!product.isUnlocked(shop.state.level)) {
                LinearLayout locked = Theme.row(activity);
                locked.setBackground(Theme.roundRect(activity, Theme.PANEL_SUNKEN, 14f));
                locked.setPadding(dp(12), dp(10), dp(12), dp(10));
                locked.addView(Theme.text(activity, product.displayName, 14f, Theme.INK_SOFT, true));
                locked.addView(Theme.flexSpacer(activity));
                locked.addView(Theme.text(activity, "Level " + product.unlockLevel,
                        11.5f, Theme.INK_SOFT, false));
                sheetBody.addView(locked, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                sheetBody.addView(Theme.spacer(activity, 0, 8));
                continue;
            }
            LinearLayout row = productRow(product, "In back: " + shop.state.stockOf(product)
                    + "  ·  " + Money.exact(product.wholesaleCost) + " each");
            row.addView(orderButton(product, 20));
            row.addView(Theme.spacer(activity, 6, 0));
            row.addView(orderButton(product, 60));
            sheetBody.addView(row, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            sheetBody.addView(Theme.spacer(activity, 0, 8));
        }
    }

    private TextView orderButton(final ProductType product, final int quantity) {
        final float cost = product.orderCost(quantity);
        final TextView tv = Theme.button(activity, "+" + quantity + "\n" + Money.format(cost),
                Theme.BLUE);
        tv.setTextSize(11.5f);
        tv.setPadding(dp(11), dp(7), dp(11), dp(7));
        tv.setEnabled(actions.shop().state.money >= cost);
        sheetRefreshers.add(new Runnable() {
            @Override public void run() {
                tv.setEnabled(actions.shop().state.money >= cost);
            }
        });
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                actions.orderStock(product, quantity);
                buildTerminalTabs(0);
            }
        });
        return tv;
    }

    private void buildUpgradeList() {
        final Shop shop = actions.shop();
        for (int i = 0; i < Upgrade.ALL.length; i++) {
            final Upgrade upgrade = Upgrade.ALL[i];
            final int level = shop.state.upgradeLevel(upgrade);
            final boolean maxed = level >= upgrade.maxLevel;
            final float cost = upgrade.costAt(level);

            LinearLayout row = Theme.row(activity);
            row.setBackground(Theme.roundRect(activity, Theme.PANEL, 14f));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));

            LinearLayout info = Theme.column(activity);
            LinearLayout titleRow = Theme.row(activity);
            titleRow.addView(Theme.text(activity, upgrade.displayName, 15f, Theme.INK, true));
            titleRow.addView(Theme.spacer(activity, 8, 0));
            titleRow.addView(pips(level, upgrade.maxLevel));
            info.addView(titleRow);
            info.addView(Theme.text(activity, upgrade.description, 11.5f, Theme.INK_SOFT, false));
            String effect = upgradeEffect(shop.state, upgrade, level);
            if (effect != null) {
                info.addView(Theme.text(activity, effect, 11.5f, Theme.GREEN_DARK, true));
            }
            row.addView(info, Theme.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final TextView buy = Theme.button(activity,
                    maxed ? "Max" : Money.format(cost), maxed ? Theme.DISABLED : Theme.PURPLE);
            buy.setTextSize(12.5f);
            buy.setPadding(dp(14), dp(9), dp(14), dp(9));
            buy.setEnabled(!maxed && shop.state.money >= cost);
            sheetRefreshers.add(new Runnable() {
                @Override public void run() {
                    buy.setEnabled(!maxed && actions.shop().state.money >= cost);
                }
            });
            buy.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    actions.buyUpgrade(upgrade);
                    buildTerminalTabs(1);
                }
            });
            row.addView(buy);

            sheetBody.addView(row, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            sheetBody.addView(Theme.spacer(activity, 0, 8));
        }
    }

    private String upgradeEffect(GameState state, Upgrade upgrade, int level) {
        switch (upgrade) {
            case REGISTER:
                return String.format("%.2fs per item", state.scanTime());
            case MARKETING:
                return String.format("Footfall x%.2f", state.marketingMultiplier());
            case DECOR:
                return "Price tolerance +" + (int) ((state.priceTolerance() - 1f) * 100) + "%";
            case SHELVING:
                return "Shelf holds " + state.shelfCapacity();
            case CASHIER:
                return level > 0 ? level + " on the till  ·  "
                        + Money.exact(upgrade.dailyWage(level)) + "/day" : "Works the till for you";
            case STOCKER:
                return level > 0 ? level + " restocking  ·  "
                        + Money.exact(upgrade.dailyWage(level)) + "/day"
                        : "Carries stock out for you";
            default:
                return null;
        }
    }

    private View pips(int filled, int total) {
        LinearLayout row = Theme.row(activity);
        for (int i = 0; i < total; i++) {
            View pip = new View(activity);
            pip.setBackground(Theme.roundRect(activity, i < filled ? Theme.GOLD : 0x22000000, 2f));
            LinearLayout.LayoutParams lp = Theme.lp(dp(10), dp(4));
            lp.rightMargin = dp(3);
            row.addView(pip, lp);
        }
        return row;
    }

    private void buildShopStats() {
        final Shop shop = actions.shop();
        final GameState state = shop.state;
        sheetBody.addView(statRow("Lifetime revenue", Money.exact(state.totalRevenue)));
        sheetBody.addView(statRow("Customers served", String.valueOf(state.totalCustomersServed)));
        sheetBody.addView(statRow("Walked out", String.valueOf(state.totalCustomersLost)));
        sheetBody.addView(statRow("Satisfaction", (int) (state.satisfaction * 100) + "%"));
        sheetBody.addView(statRow("Shelves fitted",
                shop.ownedShelfCount() + " / " + shop.shelves.size()));
        sheetBody.addView(statRow("Daily rent",
                Money.exact(state.dailyRent(shop.ownedShelfCount()))));
        sheetBody.addView(statRow("Daily wages", Money.exact(state.dailyWages())));
        ProductType next = state.nextUnlock();
        sheetBody.addView(statRow("Next unlock", next == null ? "All unlocked"
                : next.displayName + " at level " + next.unlockLevel));

        sheetBody.addView(Theme.spacer(activity, 0, 14));
        final TextView autoToggle = Theme.button(activity,
                state.autoRestockEnabled ? "Staff auto-restock: on" : "Staff auto-restock: off",
                state.autoRestockEnabled ? Theme.GREEN : Theme.DISABLED);
        autoToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !actions.shop().state.autoRestockEnabled;
                actions.setAutoRestock(next);
                autoToggle.setText(next ? "Staff auto-restock: on" : "Staff auto-restock: off");
                autoToggle.setBackground(Theme.button(activity,
                        next ? Theme.GREEN : Theme.DISABLED, 12f));
            }
        });
        sheetBody.addView(autoToggle, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        sheetBody.addView(Theme.spacer(activity, 0, 8));
        final TextView soundToggle = Theme.button(activity,
                state.soundEnabled ? "Sound: on" : "Sound: off",
                state.soundEnabled ? Theme.BLUE : Theme.DISABLED);
        soundToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !actions.shop().state.soundEnabled;
                actions.setSound(next);
                soundToggle.setText(next ? "Sound: on" : "Sound: off");
                soundToggle.setBackground(Theme.button(activity,
                        next ? Theme.BLUE : Theme.DISABLED, 12f));
            }
        });
        sheetBody.addView(soundToggle, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        sheetBody.addView(Theme.spacer(activity, 0, 10));
        TextView reset = Theme.button(activity, "Start a new shop", Theme.RED);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmReset(); }
        });
        sheetBody.addView(reset, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ------------------------------------------------------------ shelf panel

    private int sheetShelfIndex = -1;

    /** Opened at an owned shelf: change the product or the price. */
    public void showShelfSheet(int shelfIndex) {
        sheetShelfIndex = shelfIndex;
        openSheet("Shelf " + (shelfIndex + 1));
        rebuildShelfSheet();
    }

    private void rebuildShelfSheet() {
        final Shop shop = actions.shop();
        final Shelf shelf = shop.shelf(sheetShelfIndex);
        if (shelf == null) return;
        sheetRefreshers.clear();
        sheetBody.removeAllViews();

        sheetBody.addView(Theme.label(activity, "Sells"));
        sheetBody.addView(Theme.spacer(activity, 0, 6));
        sheetBody.addView(productChips(shelf));
        sheetBody.addView(Theme.spacer(activity, 0, 14));

        if (shelf.product == null) {
            sheetBody.addView(body("Choose what this shelf sells, then bring a crate "
                    + "over from the stockroom."));
            return;
        }

        LinearLayout stockRow = Theme.row(activity);
        stockRow.addView(Theme.label(activity, "On shelf"));
        stockRow.addView(Theme.flexSpacer(activity));
        stockRow.addView(Theme.text(activity, shelf.stock + " / " + shelf.capacity(shop.state),
                14f, Theme.INK, true));
        sheetBody.addView(stockRow);
        sheetBody.addView(Theme.spacer(activity, 0, 6));
        View bar = meterLight(dp(280), shelf.fillRatio(shop.state) < 0.2f ? Theme.RED : Theme.GREEN);
        sheetBody.addView(bar);
        setMeter(lastMeterFill, shelf.fillRatio(shop.state));
        sheetBody.addView(Theme.spacer(activity, 0, 14));

        final ProductType product = shelf.product;
        final TextView priceValue = Theme.text(activity, Money.exact(shelf.price),
                17f, Theme.GOLD_DARK, true);
        final TextView marginText = Theme.text(activity, "", 12f, Theme.INK_SOFT, false);

        LinearLayout priceRow = Theme.row(activity);
        priceRow.addView(Theme.label(activity, "Price"));
        priceRow.addView(Theme.flexSpacer(activity));
        priceRow.addView(priceValue);
        sheetBody.addView(priceRow);

        final float minPrice = product.wholesaleCost * 0.6f;
        final float maxPrice = product.basePrice * 2.2f;
        SeekBar seek = new SeekBar(activity);
        seek.setMax(1000);
        seek.setProgress((int) ((shelf.price - minPrice) / (maxPrice - minPrice) * 1000));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                actions.setPrice(shelf.index, minPrice + (maxPrice - minPrice) * progress / 1000f);
                priceValue.setText(Money.exact(shelf.price));
                marginText.setText(marginDescription(shelf));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        sheetBody.addView(seek, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        marginText.setText(marginDescription(shelf));
        sheetBody.addView(marginText);
    }

    private String marginDescription(Shelf shelf) {
        if (shelf.product == null) return "";
        float margin = shelf.price - shelf.product.wholesaleCost;
        float ratio = shelf.price / shelf.product.basePrice;
        String appeal;
        if (ratio < 0.85f) appeal = "Bargain — shoppers love it";
        else if (ratio < 1.05f) appeal = "Fair — steady sales";
        else if (ratio < 1.25f) appeal = "Pricey — some will pass";
        else appeal = "Steep — most will walk away";
        return "Cost " + Money.exact(shelf.product.wholesaleCost)
                + "  ·  Margin " + Money.exact(margin) + "  ·  " + appeal;
    }

    private View meterLight(int widthPx, int color) {
        FrameLayout track = new FrameLayout(activity);
        track.setBackground(Theme.roundRect(activity, Theme.PANEL_SUNKEN, 3f));
        View fill = new View(activity);
        fill.setBackground(Theme.roundRect(activity, color, 3f));
        track.addView(fill, new FrameLayout.LayoutParams(widthPx / 2, dp(6)));
        track.setLayoutParams(new LinearLayout.LayoutParams(widthPx, dp(6)));
        fill.setTag(Integer.valueOf(widthPx));
        lastMeterFill = fill;
        return track;
    }

    private View productChips(final Shelf shelf) {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Theme.row(activity);
        Shop shop = actions.shop();
        for (int i = 0; i < ProductType.ALL.length; i++) {
            final ProductType product = ProductType.ALL[i];
            boolean unlocked = product.isUnlocked(shop.state.level);
            boolean selected = shelf.product == product;

            LinearLayout chip = Theme.column(activity);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), dp(9), dp(12), dp(9));
            chip.setBackground(Theme.roundRect(activity,
                    selected ? Theme.INK : Theme.PANEL, 12f,
                    selected ? Theme.INK : 0x22000000, 1.5f));

            View dot = new View(activity);
            dot.setBackground(Theme.roundRect(activity,
                    unlocked ? 0xFF000000 | product.color : Theme.DISABLED, 7f));
            chip.addView(dot, Theme.lp(dp(14), dp(14)));
            chip.addView(Theme.spacer(activity, 0, 5));
            chip.addView(Theme.text(activity, product.displayName, 12f,
                    selected ? Color.WHITE : (unlocked ? Theme.INK : Theme.DISABLED), true));
            chip.addView(Theme.text(activity,
                    unlocked ? Money.exact(product.wholesaleCost) : "Lv " + product.unlockLevel,
                    10.5f, selected ? 0xFFBFC8D4 : Theme.INK_SOFT, false));

            if (unlocked) {
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        actions.assignProduct(shelf.index, product);
                        rebuildShelfSheet();
                    }
                });
            }
            LinearLayout.LayoutParams lp = Theme.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            row.addView(chip, lp);
        }
        scroll.addView(row);
        return scroll;
    }

    private View statRow(String label, String value) {
        LinearLayout row = Theme.row(activity);
        row.setPadding(0, dp(7), 0, dp(7));
        row.addView(Theme.text(activity, label, 13.5f, Theme.INK_SOFT, false));
        row.addView(Theme.flexSpacer(activity));
        row.addView(Theme.text(activity, value, 13.5f, Theme.INK, true));
        return row;
    }

    private View body(String value) {
        TextView tv = Theme.text(activity, value, 13f, Theme.INK_SOFT, false);
        tv.setLineSpacing(dp(3), 1f);
        return tv;
    }

    // --------------------------------------------------------------- dialogs

    private void confirmReset() {
        showDialog("Start over?", "Your shop, money and upgrades will be wiped.",
                "Start again", Theme.RED, new Runnable() {
                    @Override public void run() {
                        hideDialog();
                        hideSheet();
                        actions.resetGame();
                    }
                }, true);
    }

    public void showDaySummary(Shop.DaySummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Takings      ").append(Money.exact(summary.revenue)).append('\n');
        sb.append("Stock & kit  -").append(Money.exact(summary.stockCosts)).append('\n');
        sb.append("Rent         -").append(Money.exact(summary.rent)).append('\n');
        sb.append("Wages        -").append(Money.exact(summary.wages)).append('\n');
        sb.append('\n');
        sb.append("Profit       ").append(Money.exact(summary.profit)).append('\n');
        sb.append('\n');
        sb.append(summary.customersServed).append(" served, ")
                .append(summary.customersLost).append(" walked out\n");
        sb.append("Satisfaction ").append((int) (summary.satisfaction * 100)).append('%');

        showDialog("Day " + summary.day + " closed", sb.toString(), "Open up",
                summary.profit >= 0 ? Theme.GREEN : Theme.GOLD_DARK, new Runnable() {
                    @Override public void run() { hideDialog(); }
                }, false);
    }

    private void showDialog(String title, String message, String confirmLabel, int confirmColor,
                            final Runnable onConfirm, boolean cancellable) {
        dialogLayer.removeAllViews();
        dialogLayer.setVisibility(View.VISIBLE);
        dialogLayer.setBackgroundColor(Theme.SCRIM);
        dialogLayer.setClickable(true);

        LinearLayout card = Theme.column(activity);
        card.setBackground(Theme.roundRect(activity, Theme.PAPER, 20f));
        card.setPadding(dp(22), dp(20), dp(22), dp(18));
        card.setClickable(true);

        card.addView(Theme.text(activity, title, 20f, Theme.INK, true));
        card.addView(Theme.spacer(activity, 0, 12));
        TextView msg = Theme.text(activity, message, 13.5f, Theme.INK_SOFT, false);
        msg.setLineSpacing(dp(4), 1f);
        msg.setTypeface(Typeface.MONOSPACE);
        card.addView(msg);
        card.addView(Theme.spacer(activity, 0, 18));

        LinearLayout buttons = Theme.row(activity);
        if (cancellable) {
            TextView cancel = Theme.button(activity, "Cancel", Theme.DISABLED);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { hideDialog(); }
            });
            buttons.addView(cancel, Theme.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            buttons.addView(Theme.spacer(activity, 10, 0));
        }
        TextView confirm = Theme.button(activity, confirmLabel, confirmColor);
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onConfirm.run(); }
        });
        buttons.addView(confirm, Theme.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(buttons, Theme.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        lp.leftMargin = dp(24);
        lp.rightMargin = dp(24);
        dialogLayer.addView(card, lp);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
    }

    public boolean isDialogOpen() { return dialogLayer.getVisibility() == View.VISIBLE; }

    public void hideDialog() {
        dialogLayer.setVisibility(View.GONE);
        dialogLayer.removeAllViews();
    }

    // ---------------------------------------------------------------- banner

    private void buildBanner() {
        banner = Theme.row(activity);
        banner.setBackground(Theme.roundRect(activity, Theme.withAlpha(Theme.GOLD, 0xF0), 14f));
        banner.setPadding(dp(16), dp(10), dp(16), dp(10));
        bannerText = Theme.text(activity, "", 14f, 0xFF231A08, true);
        banner.addView(bannerText);
        banner.setVisibility(View.GONE);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(108);
        root.addView(banner, lp);
    }

    public void showBanner(String message, int color) {
        bannerText.setText(message);
        banner.setBackground(Theme.roundRect(activity, Theme.withAlpha(color, 0xF0), 14f));
        banner.setVisibility(View.VISIBLE);
        banner.setAlpha(0f);
        banner.setTranslationY(-dp(12));
        banner.animate().alpha(1f).translationY(0f).setDuration(180).start();
        bannerHideAt = System.currentTimeMillis() + 2800L;
    }

    // --------------------------------------------------------------- refresh

    public void refresh() {
        Shop shop = actions.shop();
        GameState state = shop.state;

        moneyText.setText(Money.format(state.money));
        dayText.setText("Day " + state.day);
        clockText.setText("  " + state.clockText());
        levelText.setText("Lv " + state.level);
        satisfactionText.setText("  " + (int) (state.satisfaction * 100) + "%");
        satisfactionText.setTextColor(state.satisfaction > 0.6f ? Theme.GREEN
                : (state.satisfaction > 0.35f ? Theme.GOLD : Theme.RED));
        setMeter(xpBarFill, state.levelProgress());
        setMeter(dayBarFill, state.dayFraction());

        int queued = shop.queueLength();
        if (queued > 0) {
            queueText.setVisibility(View.VISIBLE);
            queueText.setText(queued == 1 ? "1 waiting at the till"
                    : queued + " waiting at the till");
            queueText.setBackground(Theme.roundRect(activity,
                    Theme.withAlpha(queued > 3 ? Theme.RED : Theme.BLUE, 0xE0), 14f));
        } else {
            queueText.setVisibility(View.GONE);
        }

        if (shop.player.isCarrying()) {
            carryText.setVisibility(View.VISIBLE);
            carryText.setText("Carrying  " + shop.player.carryCount + "x "
                    + shop.player.carrying.displayName);
        } else {
            carryText.setVisibility(View.GONE);
        }

        if (bannerHideAt > 0 && System.currentTimeMillis() > bannerHideAt) {
            bannerHideAt = 0;
            banner.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
                @Override public void run() { banner.setVisibility(View.GONE); }
            }).start();
        }

        if (sheetOpen) {
            for (int i = 0; i < sheetRefreshers.size(); i++) sheetRefreshers.get(i).run();
        }
    }

    public boolean onBackPressed() {
        if (isDialogOpen()) { hideDialog(); return true; }
        if (sheetOpen) { hideSheet(); return true; }
        return false;
    }
}
