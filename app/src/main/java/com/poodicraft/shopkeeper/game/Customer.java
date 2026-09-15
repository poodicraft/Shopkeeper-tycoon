package com.poodicraft.shopkeeper.game;

import java.util.ArrayList;
import java.util.Random;

/** A shopper walking the state machine from door, to shelves, to till, to door. */
public final class Customer extends Agent {

    public enum State {
        ENTERING, TO_SHELF, BROWSING, TO_QUEUE, QUEUEING, PAYING, LEAVING, LEAVING_UPSET
    }

    /** One line of the shopping list. */
    public static final class Want {
        public ProductType product;
        public int quantity;

        Want(ProductType product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }
    }

    /** An item already in the basket, remembered with its shelf so it can be put back. */
    public static final class BasketItem {
        public final ProductType product;
        public final int shelfIndex;
        public final float pricePaid;

        BasketItem(ProductType product, int shelfIndex, float pricePaid) {
            this.product = product;
            this.shelfIndex = shelfIndex;
            this.pricePaid = pricePaid;
        }
    }

    public State state = State.ENTERING;
    public final ArrayList<Want> wants = new ArrayList<Want>();
    public final ArrayList<BasketItem> basket = new ArrayList<BasketItem>();

    public int targetShelf = -1;
    public int queueSlot = -1;
    public float stateTimer = 0f;
    public float serviceProgress = 0f;
    public float serviceDuration = 1f;

    /** Seconds of queueing the shopper will tolerate before walking out. */
    public float patience = 30f;
    public float patienceLeft = 30f;
    /** 0..1, shown as the mood bubble and folded into the satisfaction score. */
    public float mood = 1f;

    /** Multiplies base price to give this shopper's personal ceiling. */
    public float willingness = 1f;
    /** Ticks up while an item is being lifted onto the shoulder, for the pick animation. */
    public float carryBlend = 0f;
    public float bubbleTimer = 0f;
    public boolean servedByStaff = false;
    /** Set when the player taps this shopper at the till to speed the transaction along. */
    public float rushBoost = 0f;

    public float fadeIn = 0f;
    public float fadeOut = 1f;
    public boolean finished = false;

    public void init(Random rng, GameState state, float toleranceMultiplier,
                     boolean[] onShelvesNow) {
        randomizeLook(rng);
        speed = 1.25f + rng.nextFloat() * 0.5f;
        willingness = toleranceMultiplier * (0.86f + rng.nextFloat() * 0.34f);
        patience = 22f + rng.nextFloat() * 26f + 4f * state.upgradeLevel(Upgrade.DECOR);
        patienceLeft = patience;
        mood = 1f;
        this.state = State.ENTERING;
        wants.clear();
        basket.clear();
        buildShoppingList(rng, state, onShelvesNow);
    }

    /**
     * Picks a shopping list from the unlocked catalogue.
     *
     * <p>Products actually sitting on a shelf are weighted up: people mostly come in
     * for what the shop is known to sell, while still occasionally asking for
     * something that is out of stock.
     */
    private void buildShoppingList(Random rng, GameState gs, boolean[] onShelvesNow) {
        ArrayList<ProductType> pool = new ArrayList<ProductType>();
        ArrayList<Float> weights = new ArrayList<Float>();
        float totalWeight = 0f;
        for (int i = 0; i < ProductType.ALL.length; i++) {
            ProductType p = ProductType.ALL[i];
            if (!p.isUnlocked(gs.level)) continue;
            boolean stocked = onShelvesNow != null && onShelvesNow[i];
            float weight = p.demandWeight * (stocked ? 3.2f : 0.55f);
            pool.add(p);
            weights.add(Float.valueOf(weight));
            totalWeight += weight;
        }
        if (pool.isEmpty()) return;

        int lines = 1 + rng.nextInt(Math.min(3, pool.size()));
        for (int i = 0; i < lines; i++) {
            float roll = rng.nextFloat() * totalWeight;
            ProductType chosen = pool.get(pool.size() - 1);
            for (int j = 0; j < pool.size(); j++) {
                roll -= weights.get(j).floatValue();
                if (roll <= 0f) { chosen = pool.get(j); break; }
            }
            if (containsWant(chosen)) continue;
            wants.add(new Want(chosen, 1 + rng.nextInt(3)));
        }
        if (wants.isEmpty()) wants.add(new Want(pool.get(0), 1));
    }

    private boolean containsWant(ProductType p) {
        for (int i = 0; i < wants.size(); i++) {
            if (wants.get(i).product == p) return true;
        }
        return false;
    }

    public boolean wantsAnything() {
        for (int i = 0; i < wants.size(); i++) {
            if (wants.get(i).quantity > 0) return true;
        }
        return false;
    }

    public float basketTotal() {
        float total = 0f;
        for (int i = 0; i < basket.size(); i++) total += basket.get(i).pricePaid;
        return total;
    }

    public int basketCount() { return basket.size(); }

    /** The price this shopper will pay for a product before walking away from it. */
    public float acceptablePrice(ProductType product) {
        return product.basePrice * willingness;
    }

    public void satisfyWant(ProductType product, int taken) {
        for (int i = 0; i < wants.size(); i++) {
            Want w = wants.get(i);
            if (w.product == product) {
                w.quantity = Math.max(0, w.quantity - taken);
                return;
            }
        }
    }

    public void enterState(State next) {
        state = next;
        stateTimer = 0f;
    }
}
