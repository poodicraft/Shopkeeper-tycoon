package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.math.MathUtil;

import java.util.ArrayList;
import java.util.Random;

/**
 * A shopper: comes in, finds what they came for, queues at the till and waits for
 * the shopkeeper to serve them.
 *
 * <p>Unlike the fittings, a customer will not wait forever. Patience drains while
 * they queue, and running out costs the shop's reputation - which is the pressure
 * that makes standing at the till matter.
 */
public final class Customer extends Actor {

    public enum State {
        ENTERING, TO_SHELF, BROWSING, TO_QUEUE, QUEUEING, BEING_SERVED, LEAVING, LEAVING_UPSET
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

    /** An item in the basket, remembered with its shelf so it can be put back. */
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

    public float patience = 40f;
    public float patienceLeft = 40f;
    /** 0..1, shown above their head and folded into the satisfaction score. */
    public float mood = 1f;

    /** Personal price ceiling as a multiple of a product's base price. */
    public float willingness = 1f;

    /** How many basket items have been scanned so far. */
    public int scanned = 0;

    public float walkSpeed = 1.35f;
    public float fadeIn = 0f;
    public float fadeOut = 1f;
    public boolean finished = false;
    /** Seconds left to show a thought bubble over their head. */
    public float bubbleTimer = 0f;
    public String bubbleText = null;

    public void init(Random rng, GameState state, float toleranceMultiplier, boolean[] onShelvesNow) {
        appearance = Appearance.randomShopper(rng);
        radius = 0.27f;
        animator.personalPhase = rng.nextFloat() * 6.28f;
        walkSpeed = 1.15f + rng.nextFloat() * 0.55f;
        willingness = toleranceMultiplier * (0.86f + rng.nextFloat() * 0.34f);
        patience = 30f + rng.nextFloat() * 30f + 5f * state.upgradeLevel(Upgrade.DECOR);
        patienceLeft = patience;
        mood = 1f;
        scanned = 0;
        this.state = State.ENTERING;
        wants.clear();
        basket.clear();
        buildShoppingList(rng, state, onShelvesNow);
    }

    /**
     * Picks a shopping list, weighted heavily toward what is actually on a shelf.
     *
     * <p>Without that bias a shop that only stocks one line turns away most of its
     * customers, which makes the opening hours unwinnable rather than difficult.
     */
    private void buildShoppingList(Random rng, GameState gs, boolean[] onShelvesNow) {
        ArrayList<ProductType> pool = new ArrayList<ProductType>();
        ArrayList<Float> weights = new ArrayList<Float>();
        float total = 0f;
        for (int i = 0; i < ProductType.ALL.length; i++) {
            ProductType p = ProductType.ALL[i];
            if (!p.isUnlocked(gs.level)) continue;
            boolean stocked = onShelvesNow != null && onShelvesNow[i];
            float weight = p.demandWeight * (stocked ? 3.2f : 0.55f);
            pool.add(p);
            weights.add(Float.valueOf(weight));
            total += weight;
        }
        if (pool.isEmpty()) return;

        int lines = 1 + rng.nextInt(Math.min(3, pool.size()));
        for (int i = 0; i < lines; i++) {
            float roll = rng.nextFloat() * total;
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

    public float basketTotal() {
        float total = 0f;
        for (int i = 0; i < basket.size(); i++) total += basket.get(i).pricePaid;
        return total;
    }

    /** Value of the items scanned so far, for the running total on the till. */
    public float scannedTotal() {
        float total = 0f;
        for (int i = 0; i < Math.min(scanned, basket.size()); i++) {
            total += basket.get(i).pricePaid;
        }
        return total;
    }

    public int basketCount() { return basket.size(); }

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

    public void say(String text, float seconds) {
        bubbleText = text;
        bubbleTimer = seconds;
    }

    /** Keeps the animation layers in step with what the shopper is doing. */
    public void updateAnimation(float dt, boolean walking) {
        animator.locomotion = MathUtil.approach(animator.locomotion, walking ? 1f : 0f, dt * 6f);
        animator.carry = MathUtil.approach(animator.carry, basket.isEmpty() ? 0f : 1f, dt * 3f);
        boolean reaching = state == State.BROWSING && stateTimer > 0.45f;
        animator.reach = MathUtil.approach(animator.reach, reaching ? 1f : 0f, dt * 4.5f);
        animator.crouch = MathUtil.approach(animator.crouch,
                reaching && animator.reachHeight < 0.75f ? 0.8f : 0f, dt * 3.5f);
        bubbleTimer = Math.max(0f, bubbleTimer - dt);
    }
}
