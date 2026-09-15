package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.math.MathUtil;

import java.util.ArrayList;
import java.util.Random;

/**
 * The simulated shop: floor layout, navigation, shoppers, staff and the money that
 * moves between them. Rendering reads from here; the UI drives it through the
 * player-action methods near the bottom.
 */
public final class Shop {

    // ------------------------------------------------------------------- layout

    public static final float HALF_WIDTH = 6f;
    public static final float HALF_DEPTH = 8f;
    public static final float WALL_HEIGHT = 3.2f;

    /** Door opening in the front wall (z = +HALF_DEPTH). */
    public static final float DOOR_MIN_X = 3.3f;
    public static final float DOOR_MAX_X = 5.7f;
    public static final float DOOR_CENTER_X = (DOOR_MIN_X + DOOR_MAX_X) * 0.5f;

    /** Checkout counter footprint. */
    public static final float COUNTER_MIN_X = -5.6f;
    public static final float COUNTER_MAX_X = -1.6f;
    public static final float COUNTER_MIN_Z = 5.8f;
    public static final float COUNTER_MAX_Z = 6.8f;
    public static final float COUNTER_HEIGHT = 1.05f;

    /** Where the till sits, and where the cashier stands behind it. */
    public static final float REGISTER_X = -2.2f;
    public static final float REGISTER_Z = 6.3f;
    public static final float CASHIER_X = -2.4f;
    public static final float CASHIER_Z = 5.15f;

    /** Head of the queue; further slots run toward the door. */
    public static final float QUEUE_HEAD_X = -1.9f;
    public static final float QUEUE_Z = 7.35f;
    public static final float QUEUE_SPACING = 0.78f;
    public static final int MAX_QUEUE = 8;

    /** Stockroom hatch in the back wall that stockers collect goods from. */
    public static final float STOCKROOM_X = -5.0f;
    public static final float STOCKROOM_Z = -7.1f;

    public static final int SHELF_SLOTS = 15;
    private static final float SHELF_WIDTH = 2.6f;
    private static final float SHELF_DEPTH = 0.9f;
    private static final float[] SHELF_ROW_Z = {-6.5f, -4.0f, -1.5f, 1.0f, 3.5f};
    private static final float[] SHELF_COL_X = {-3.6f, 0f, 3.6f};

    public static float shelfWidth() { return SHELF_WIDTH; }

    public static float shelfDepth() { return SHELF_DEPTH; }

    // --------------------------------------------------------------------- data

    public final GameState state;
    public final NavGrid nav;
    public final ArrayList<Shelf> shelves = new ArrayList<Shelf>();
    public final ArrayList<Customer> customers = new ArrayList<Customer>();
    public final ArrayList<Staff> staff = new ArrayList<Staff>();
    public final ArrayList<Popup> popups = new ArrayList<Popup>();

    private final Random rng = new Random();
    private final ArrayList<float[]> pathScratch = new ArrayList<float[]>();
    private final float[] pointScratch = new float[2];

    /** Customers currently in the checkout line, head first. */
    private final ArrayList<Customer> queue = new ArrayList<Customer>();
    private Customer atRegister = null;

    private float spawnAccumulator = 0f;
    private float staffThinkTimer = 0f;

    /** Set on the frame a day rolls over so the UI can show the summary. */
    public DaySummary pendingSummary = null;
    /** Levels gained this frame, for the UI to celebrate. */
    public int pendingLevelUps = 0;

    public interface Listener {
        void onSale(float amount, int itemCount);
        void onCustomerLost(String reason);
        void onLevelUp(int newLevel);
    }

    public Listener listener = null;

    public Shop(GameState state) {
        this.state = state;
        nav = new NavGrid(-HALF_WIDTH - 0.6f, -HALF_DEPTH - 0.6f,
                HALF_WIDTH * 2 + 1.2f, HALF_DEPTH * 2 + 2.6f, 0.4f);
        buildShelves();
        rebuildNavigation();
        syncStaff();
    }

    private void buildShelves() {
        int index = 0;
        for (int row = 0; row < SHELF_ROW_Z.length; row++) {
            for (int col = 0; col < SHELF_COL_X.length; col++) {
                float x = SHELF_COL_X[col];
                float z = SHELF_ROW_Z[row];
                int cost = (int) (140 * Math.pow(1.52, index));
                Shelf shelf = new Shelf(index, x, z, 0f, x, z + 1.05f, cost);
                shelves.add(shelf);
                index++;
            }
        }
        // Two units come with the lease so there is something to sell on day one.
        shelves.get(0).owned = true;
        shelves.get(0).assign(ProductType.BREAD);
        shelves.get(1).owned = true;
        shelves.get(1).assign(ProductType.MILK);
    }

    /** Rebuilds walkability. Call after shelves are bought or the layout changes. */
    public void rebuildNavigation() {
        nav.clearObstacles();
        for (int r = 0; r < nav.rows; r++) {
            for (int c = 0; c < nav.cols; c++) {
                float x = nav.cellCenterX(c);
                float z = nav.cellCenterZ(r);
                boolean walkable;
                if (z > HALF_DEPTH - 0.25f) {
                    // Only the doorway pierces the front wall.
                    walkable = x > DOOR_MIN_X + 0.15f && x < DOOR_MAX_X - 0.15f && z < HALF_DEPTH + 1.6f;
                } else {
                    walkable = x > -HALF_WIDTH + 0.3f && x < HALF_WIDTH - 0.3f
                            && z > -HALF_DEPTH + 0.3f;
                }
                if (!walkable) nav.blockRect(x, z, x, z, 0f);
            }
        }
        for (int i = 0; i < shelves.size(); i++) {
            Shelf s = shelves.get(i);
            if (!s.owned) continue;
            nav.blockRect(s.x - SHELF_WIDTH * 0.5f, s.z - SHELF_DEPTH * 0.5f,
                    s.x + SHELF_WIDTH * 0.5f, s.z + SHELF_DEPTH * 0.5f, 0.18f);
        }
        nav.blockRect(COUNTER_MIN_X, COUNTER_MIN_Z, COUNTER_MAX_X, COUNTER_MAX_Z, 0.12f);
    }

    /** Creates or removes staff so the crowd matches the purchased upgrades. */
    public void syncStaff() {
        int wantCashiers = state.cashierCount();
        int wantStockers = state.stockerCount();
        int haveCashiers = 0, haveStockers = 0;
        for (int i = 0; i < staff.size(); i++) {
            if (staff.get(i).role == Staff.Role.CASHIER) haveCashiers++;
            else haveStockers++;
        }
        while (haveCashiers < wantCashiers) {
            Staff s = new Staff(Staff.Role.CASHIER, rng);
            float offset = haveCashiers * 1.35f;
            s.setHome(CASHIER_X - offset, CASHIER_Z, 0f);
            s.teleport(s.homeX, s.homeZ);
            s.heading = 0f;
            staff.add(s);
            haveCashiers++;
        }
        while (haveStockers < wantStockers) {
            Staff s = new Staff(Staff.Role.STOCKER, rng);
            s.setHome(STOCKROOM_X + 1.2f + haveStockers * 0.9f, STOCKROOM_Z + 1.1f, (float) Math.PI);
            s.teleport(s.homeX, s.homeZ);
            staff.add(s);
            haveStockers++;
        }
    }

    // ------------------------------------------------------------------ helpers

    public int ownedShelfCount() {
        int n = 0;
        for (int i = 0; i < shelves.size(); i++) if (shelves.get(i).owned) n++;
        return n;
    }

    public Shelf shelf(int index) {
        return index >= 0 && index < shelves.size() ? shelves.get(index) : null;
    }

    public int queueLength() { return queue.size(); }

    public Customer customerAtRegister() { return atRegister; }

    public float queueSlotX(int slot) { return QUEUE_HEAD_X + slot * QUEUE_SPACING; }

    private boolean pathTo(Agent agent, float x, float z) {
        if (nav.findPath(agent.pos.x, agent.pos.z, x, z, pathScratch)) {
            agent.setPath(pathScratch);
            return true;
        }
        // Fall back to a straight line so an agent never freezes on a bad target.
        pathScratch.clear();
        pathScratch.add(new float[]{x, z});
        agent.setPath(pathScratch);
        return false;
    }

    public void addPopup(float x, float y, float z, String text, int color) {
        Popup p = new Popup();
        p.x = x; p.y = y; p.z = z;
        p.text = text;
        p.color = color;
        p.life = 1.5f;
        popups.add(p);
    }

    // ------------------------------------------------------------------- update

    public void update(float dt) {
        pendingLevelUps = 0;
        advanceClock(dt);
        updateSpawning(dt);
        updateCustomers(dt);
        updateStaff(dt);
        updateShelfEffects(dt);
        updatePopups(dt);
    }

    private void advanceClock(float dt) {
        state.dayTime += dt;
        if (state.dayTime >= GameState.DAY_LENGTH) {
            state.dayTime -= GameState.DAY_LENGTH;
            closeOutDay();
        }
    }

    private void closeOutDay() {
        DaySummary summary = new DaySummary();
        summary.day = state.day;
        summary.revenue = state.dayRevenue;
        summary.stockCosts = state.dayCosts;
        summary.customersServed = state.dayServed;
        summary.customersLost = state.dayLost;
        summary.rent = state.dailyRent(ownedShelfCount());
        summary.wages = state.dailyWages();
        summary.satisfaction = state.satisfaction;

        state.money -= summary.rent + summary.wages;
        summary.profit = summary.revenue - summary.stockCosts - summary.rent - summary.wages;

        state.day++;
        state.dayRevenue = 0;
        state.dayCosts = 0;
        state.dayServed = 0;
        state.dayLost = 0;
        // Reputation drifts back toward neutral so a bad day is recoverable.
        state.adjustSatisfaction((0.72f - state.satisfaction) * 0.18f);
        pendingSummary = summary;
    }

    private void updateSpawning(float dt) {
        if (!state.isOpen()) return;
        if (customers.size() >= 26) return;

        float rate = 0.17f * state.marketingMultiplier() * state.trafficCurve()
                * (0.55f + state.satisfaction * 0.85f);
        // Nobody comes in if there is nothing on the shelves.
        if (!anyShelfStocked()) rate *= 0.12f;
        // People passing a long queue keep walking, which is kinder than letting
        // them come in and storm out again.
        int waiting = queue.size();
        if (waiting >= 3) rate *= Math.max(0.12f, 1f - (waiting - 2) * 0.22f);
        spawnAccumulator += rate * dt;
        while (spawnAccumulator >= 1f) {
            spawnAccumulator -= 1f;
            spawnCustomer();
        }
    }

    private boolean anyShelfStocked() {
        for (int i = 0; i < shelves.size(); i++) {
            if (shelves.get(i).isStocked()) return true;
        }
        return false;
    }

    private final boolean[] availability = new boolean[ProductType.ALL.length];

    private void spawnCustomer() {
        for (int i = 0; i < availability.length; i++) availability[i] = false;
        for (int i = 0; i < shelves.size(); i++) {
            Shelf s = shelves.get(i);
            if (s.owned && s.product != null && s.stock > 0) availability[s.product.ordinal()] = true;
        }
        Customer c = new Customer();
        c.init(rng, state, state.priceTolerance(), availability);
        float spawnX = DOOR_CENTER_X + (rng.nextFloat() - 0.5f) * 0.8f;
        c.teleport(spawnX, HALF_DEPTH + 1.4f);
        c.heading = (float) Math.PI;
        customers.add(c);
        chooseNextGoal(c);
    }

    /** Sends a shopper to the best shelf for their list, or to the till, or home. */
    private void chooseNextGoal(Customer c) {
        Shelf best = null;
        // Scores are routinely negative once distance is subtracted, so the seed
        // has to be lower than any real candidate.
        float bestScore = -Float.MAX_VALUE;
        for (int w = 0; w < c.wants.size(); w++) {
            Customer.Want want = c.wants.get(w);
            if (want.quantity <= 0) continue;
            for (int i = 0; i < shelves.size(); i++) {
                Shelf s = shelves.get(i);
                if (!s.owned || s.product != want.product || s.stock <= 0) continue;
                if (s.price > c.acceptablePrice(want.product)) continue;
                // Prefer close shelves, and cheap ones when several carry the item.
                float dist = (float) Math.hypot(s.approachX - c.pos.x, s.approachZ - c.pos.z);
                float valueScore = 1f - MathUtil.clamp(s.price / Math.max(0.01f, c.acceptablePrice(want.product)), 0f, 1f);
                float score = valueScore * 6f - dist * 0.25f;
                if (score > bestScore) {
                    bestScore = score;
                    best = s;
                }
            }
        }

        if (best != null) {
            c.targetShelf = best.index;
            c.enterState(Customer.State.TO_SHELF);
            pathTo(c, best.approachX + (rng.nextFloat() - 0.5f) * 0.6f, best.approachZ);
            return;
        }

        if (!c.basket.isEmpty()) {
            joinQueue(c);
        } else {
            leaveUpset(c, "Nothing to buy", 0.006f);
        }
    }

    private void joinQueue(Customer c) {
        if (queue.size() >= MAX_QUEUE) {
            leaveUpset(c, "Queue too long", 0.020f);
            return;
        }
        c.queueSlot = queue.size();
        queue.add(c);
        c.enterState(Customer.State.TO_QUEUE);
        pathTo(c, queueSlotX(c.queueSlot), QUEUE_Z);
    }

    private void leaveUpset(Customer c, String reason, float satisfactionHit) {
        returnBasket(c);
        c.enterState(Customer.State.LEAVING_UPSET);
        c.mood = 0.15f;
        removeFromQueue(c);
        pathTo(c, DOOR_CENTER_X, HALF_DEPTH + 1.8f);
        state.adjustSatisfaction(-satisfactionHit);
        state.dayLost++;
        state.totalCustomersLost++;
        addPopup(c.pos.x, 1.9f, c.pos.z, reason, 0xFFE05A5A);
        if (listener != null) listener.onCustomerLost(reason);
    }

    /** Puts abandoned items back where they came from so stock is never destroyed. */
    private void returnBasket(Customer c) {
        for (int i = 0; i < c.basket.size(); i++) {
            Customer.BasketItem item = c.basket.get(i);
            Shelf s = shelf(item.shelfIndex);
            if (s != null && s.product == item.product && s.stock < s.capacity(state)) {
                s.stock++;
            } else {
                // The shelf was refilled while they shopped, so it goes out the back.
                state.stock[item.product.ordinal()]++;
            }
        }
        c.basket.clear();
    }

    private void removeFromQueue(Customer c) {
        int at = queue.indexOf(c);
        if (at >= 0) {
            queue.remove(at);
            resequenceQueue();
        }
        if (atRegister == c) atRegister = null;
        c.queueSlot = -1;
    }

    private void resequenceQueue() {
        for (int i = 0; i < queue.size(); i++) {
            Customer c = queue.get(i);
            if (c.queueSlot == i) continue;
            c.queueSlot = i;
            if (c.state == Customer.State.QUEUEING || c.state == Customer.State.TO_QUEUE) {
                c.enterState(Customer.State.TO_QUEUE);
                pathTo(c, queueSlotX(i), QUEUE_Z);
            }
        }
    }

    private void updateCustomers(float dt) {
        for (int i = customers.size() - 1; i >= 0; i--) {
            Customer c = customers.get(i);
            c.stateTimer += dt;
            c.fadeIn = MathUtil.approach(c.fadeIn, 1f, dt * 2.5f);
            c.bubbleTimer = Math.max(0f, c.bubbleTimer - dt);
            c.carryBlend = MathUtil.approach(c.carryBlend, c.basket.isEmpty() ? 0f : 1f, dt * 3f);
            c.rushBoost = Math.max(0f, c.rushBoost - dt);

            switch (c.state) {
                case ENTERING:
                case TO_SHELF:
                    stepToShelf(c, dt);
                    break;
                case BROWSING:
                    stepBrowsing(c, dt);
                    break;
                case TO_QUEUE:
                    stepToQueue(c, dt);
                    break;
                case QUEUEING:
                    stepQueueing(c, dt);
                    break;
                case PAYING:
                    stepPaying(c, dt);
                    break;
                case LEAVING:
                case LEAVING_UPSET:
                    stepLeaving(c, dt);
                    break;
            }

            if (c.finished) {
                removeFromQueue(c);
                customers.remove(i);
            }
        }
        promoteQueue();
    }

    private void stepToShelf(Customer c, float dt) {
        boolean reached = c.advance(dt);
        if (!reached && c.hasPath()) return;
        Shelf s = shelf(c.targetShelf);
        if (s == null || !s.isStocked()) {
            chooseNextGoal(c);
            return;
        }
        c.enterState(Customer.State.BROWSING);
    }

    private void stepBrowsing(Customer c, float dt) {
        c.advance(dt);
        Shelf s = shelf(c.targetShelf);
        if (s == null) {
            chooseNextGoal(c);
            return;
        }
        c.faceTowards(s.x, s.z, dt);
        s.highlight = 1f;
        float browseTime = 1.1f + (c.heightScale - 0.9f) * 2f;
        if (c.stateTimer < browseTime) return;

        if (s.stock <= 0 || s.price > c.acceptablePrice(s.product)) {
            chooseNextGoal(c);
            return;
        }
        int wanted = 1;
        for (int i = 0; i < c.wants.size(); i++) {
            if (c.wants.get(i).product == s.product) {
                wanted = Math.max(1, c.wants.get(i).quantity);
                break;
            }
        }
        int taken = Math.min(wanted, s.stock);
        s.stock -= taken;
        for (int i = 0; i < taken; i++) {
            c.basket.add(new Customer.BasketItem(s.product, s.index, s.price));
        }
        c.satisfyWant(s.product, taken);
        c.bubbleTimer = 1.4f;
        c.targetShelf = -1;
        chooseNextGoal(c);
    }

    private void stepToQueue(Customer c, float dt) {
        boolean reached = c.advance(dt);
        c.patienceLeft -= dt * 0.4f;
        if (reached || !c.hasPath()) {
            c.enterState(Customer.State.QUEUEING);
        }
    }

    private void stepQueueing(Customer c, float dt) {
        // Keep drifting toward the assigned slot as the line shuffles forward.
        float slotX = queueSlotX(c.queueSlot);
        float dx = slotX - c.pos.x;
        float dz = QUEUE_Z - c.pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.08f) {
            float step = Math.min(c.speed * dt, dist);
            c.pos.x += dx / dist * step;
            c.pos.z += dz / dist * step;
            c.walkPhase += step * 4.4f;
            c.walkBlend = MathUtil.approach(c.walkBlend, 1f, dt * 6f);
        } else {
            c.walkBlend = MathUtil.approach(c.walkBlend, 0f, dt * 6f);
        }
        c.faceTowards(REGISTER_X, REGISTER_Z, dt);

        c.patienceLeft -= dt;
        c.mood = MathUtil.clamp(c.patienceLeft / Math.max(1f, c.patience), 0f, 1f);
        if (c.patienceLeft <= 0f) {
            leaveUpset(c, "Waited too long", 0.022f);
        }
    }

    private void promoteQueue() {
        if (atRegister != null) return;
        if (queue.isEmpty()) return;
        Customer head = queue.get(0);
        if (head.state != Customer.State.QUEUEING) return;
        if (Math.abs(head.pos.x - queueSlotX(0)) > 0.4f) return;

        boolean hasCashier = state.cashierCount() > 0;
        atRegister = head;
        head.enterState(Customer.State.PAYING);
        head.serviceProgress = 0f;
        head.servedByStaff = hasCashier;
        head.serviceDuration = state.checkoutDuration() * (hasCashier ? 1f : 1.35f);
    }

    private void stepPaying(Customer c, float dt) {
        float targetX = queueSlotX(0);
        float dx = targetX - c.pos.x;
        float dz = QUEUE_Z - c.pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.06f) {
            float step = Math.min(c.speed * dt, dist);
            c.pos.x += dx / dist * step;
            c.pos.z += dz / dist * step;
        }
        c.faceTowards(REGISTER_X, REGISTER_Z, dt);
        c.walkBlend = MathUtil.approach(c.walkBlend, 0f, dt * 6f);

        // Tapping the shopper adds a burst of service speed; a cashier works steadily.
        float rate = 1f + (c.rushBoost > 0f ? 1.6f : 0f);
        if (!c.servedByStaff && c.rushBoost <= 0f) rate *= 0.5f;
        c.serviceProgress += dt * rate;

        c.patienceLeft -= dt * 0.5f;
        c.mood = MathUtil.clamp(c.patienceLeft / Math.max(1f, c.patience), 0f, 1f);
        if (c.patienceLeft <= 0f) {
            leaveUpset(c, "Gave up at till", 0.030f);
            return;
        }
        if (c.serviceProgress >= c.serviceDuration) {
            completeSale(c);
        }
    }

    private void completeSale(Customer c) {
        float total = c.basketTotal();
        int items = c.basketCount();
        state.money += total;
        state.dayRevenue += total;
        state.totalRevenue += total;
        state.dayServed++;
        state.totalCustomersServed++;

        float valueRatio = 0f;
        for (int i = 0; i < c.basket.size(); i++) {
            Customer.BasketItem item = c.basket.get(i);
            valueRatio += item.pricePaid / Math.max(0.01f, item.product.basePrice);
        }
        valueRatio = items > 0 ? valueRatio / items : 1f;
        // Cheap relative to the base price makes people happy; gouging does not.
        state.adjustSatisfaction(MathUtil.clamp((1.15f - valueRatio) * 0.05f, -0.03f, 0.02f)
                + c.mood * 0.006f);

        int levels = state.addXp(6f + total * 0.08f);
        if (levels > 0) {
            pendingLevelUps += levels;
            if (listener != null) listener.onLevelUp(state.level);
        }

        addPopup(c.pos.x, 1.95f, c.pos.z, "+" + Money.format(total), 0xFF6FE08A);
        if (listener != null) listener.onSale(total, items);

        c.basket.clear();
        c.enterState(Customer.State.LEAVING);
        removeFromQueue(c);
        pathTo(c, DOOR_CENTER_X, HALF_DEPTH + 1.8f);
    }

    private void stepLeaving(Customer c, float dt) {
        c.advance(dt);
        if (!c.hasPath()) {
            c.fadeOut -= dt * 1.6f;
            if (c.fadeOut <= 0f) c.finished = true;
        }
    }

    // -------------------------------------------------------------------- staff

    private void updateStaff(float dt) {
        staffThinkTimer -= dt;
        boolean think = staffThinkTimer <= 0f;
        if (think) staffThinkTimer = 0.4f;

        for (int i = 0; i < staff.size(); i++) {
            Staff s = staff.get(i);
            s.carryBlend = MathUtil.approach(s.carryBlend, s.isCarrying() ? 1f : 0f, dt * 4f);
            if (s.role == Staff.Role.CASHIER) {
                updateCashier(s, dt);
            } else {
                updateStocker(s, dt, think);
            }
        }
    }

    private void updateCashier(Staff s, float dt) {
        float dx = s.homeX - s.pos.x, dz = s.homeZ - s.pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.1f) {
            s.advance(dt);
            if (!s.hasPath()) pathTo(s, s.homeX, s.homeZ);
        } else {
            s.stop();
            s.walkBlend = MathUtil.approach(s.walkBlend, 0f, dt * 6f);
            if (atRegister != null) {
                s.faceTowards(atRegister.pos.x, atRegister.pos.z, dt);
            } else {
                s.heading = MathUtil.approachAngle(s.heading, 0f, dt * 3f);
            }
        }
    }

    private void updateStocker(Staff s, float dt, boolean think) {
        switch (s.task) {
            case IDLE:
                s.advance(dt);
                if (think && state.autoRestockEnabled) {
                    Shelf target = findRestockTarget();
                    if (target != null) {
                        target.restockClaimed = true;
                        s.targetShelf = target.index;
                        s.carrying = target.product;
                        s.task = Staff.Task.TO_STOCKROOM;
                        pathTo(s, STOCKROOM_X + 0.9f, STOCKROOM_Z + 1.0f);
                    } else if (!s.hasPath()) {
                        s.faceTowards(s.pos.x + (float) Math.sin(s.homeHeading),
                                s.pos.z + (float) Math.cos(s.homeHeading), dt);
                    }
                }
                break;

            case TO_STOCKROOM:
                if (s.advance(dt) || !s.hasPath()) {
                    s.task = Staff.Task.LOADING;
                    s.taskTimer = 0.8f;
                }
                break;

            case LOADING: {
                s.taskTimer -= dt;
                s.walkBlend = MathUtil.approach(s.walkBlend, 0f, dt * 6f);
                if (s.taskTimer > 0f) break;
                Shelf target = shelf(s.targetShelf);
                if (target == null || target.product != s.carrying) {
                    abortStockerTask(s);
                    break;
                }
                int room = target.capacity(state) - target.stock;
                int available = state.stock[s.carrying.ordinal()];
                int load = Math.min(Math.min(room, available), 10);
                if (load <= 0) {
                    abortStockerTask(s);
                    break;
                }
                state.stock[s.carrying.ordinal()] -= load;
                s.carryCount = load;
                s.task = Staff.Task.TO_SHELF;
                pathTo(s, target.approachX, target.approachZ);
                break;
            }

            case TO_SHELF: {
                if (s.advance(dt) || !s.hasPath()) {
                    s.task = Staff.Task.STOCKING;
                    s.taskTimer = 1.1f;
                }
                break;
            }

            case STOCKING: {
                s.taskTimer -= dt;
                s.walkBlend = MathUtil.approach(s.walkBlend, 0f, dt * 6f);
                Shelf target = shelf(s.targetShelf);
                if (target != null) {
                    s.faceTowards(target.x, target.z, dt);
                    target.highlight = 1f;
                }
                if (s.taskTimer > 0f) break;
                if (target != null && target.product == s.carrying) {
                    int placed = Math.min(s.carryCount, target.capacity(state) - target.stock);
                    target.stock += placed;
                    s.carryCount -= placed;
                    addPopup(target.x, 2.0f, target.z, "+" + placed, 0xFF7FC4FF);
                }
                // Anything that would not fit goes back to the stockroom.
                if (s.carryCount > 0 && s.carrying != null) {
                    state.stock[s.carrying.ordinal()] += s.carryCount;
                    s.carryCount = 0;
                }
                if (target != null) target.restockClaimed = false;
                s.carrying = null;
                s.targetShelf = -1;
                s.task = Staff.Task.RETURNING;
                pathTo(s, s.homeX, s.homeZ);
                break;
            }

            case RETURNING:
                if (s.advance(dt) || !s.hasPath()) {
                    s.task = Staff.Task.IDLE;
                }
                break;
        }
    }

    private void abortStockerTask(Staff s) {
        Shelf target = shelf(s.targetShelf);
        if (target != null) target.restockClaimed = false;
        if (s.carrying != null && s.carryCount > 0) {
            state.stock[s.carrying.ordinal()] += s.carryCount;
        }
        s.carrying = null;
        s.carryCount = 0;
        s.targetShelf = -1;
        s.task = Staff.Task.RETURNING;
        pathTo(s, s.homeX, s.homeZ);
    }

    /** The emptiest owned shelf whose product is sitting in the stockroom. */
    private Shelf findRestockTarget() {
        Shelf best = null;
        float worstFill = 0.7f;
        for (int i = 0; i < shelves.size(); i++) {
            Shelf s = shelves.get(i);
            if (!s.owned || s.product == null || s.restockClaimed) continue;
            if (state.stock[s.product.ordinal()] <= 0) continue;
            float fill = s.fillRatio(state);
            if (fill < worstFill) {
                worstFill = fill;
                best = s;
            }
        }
        return best;
    }

    private void updateShelfEffects(float dt) {
        for (int i = 0; i < shelves.size(); i++) {
            Shelf s = shelves.get(i);
            s.highlight = Math.max(0f, s.highlight - dt * 1.6f);
        }
    }

    private void updatePopups(float dt) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.get(i);
            p.age += dt;
            p.y += dt * 0.55f;
            if (p.age >= p.life) popups.remove(i);
        }
    }

    // ----------------------------------------------------------- player actions

    public boolean buyShelf(int index) {
        Shelf s = shelf(index);
        if (s == null || s.owned || state.money < s.purchaseCost) return false;
        state.money -= s.purchaseCost;
        state.dayCosts += s.purchaseCost;
        s.owned = true;
        s.assign(firstUnlockedProduct());
        rebuildNavigation();
        addPopup(s.x, 1.6f, s.z, "New shelf!", 0xFFFFD166);
        return true;
    }

    private ProductType firstUnlockedProduct() {
        for (int i = 0; i < ProductType.ALL.length; i++) {
            if (ProductType.ALL[i].isUnlocked(state.level)) return ProductType.ALL[i];
        }
        return ProductType.BREAD;
    }

    /** Buys stock into the stockroom. Returns false when it is unaffordable. */
    public boolean orderStock(ProductType product, int quantity) {
        float cost = product.orderCost(quantity);
        if (state.money < cost) return false;
        state.money -= cost;
        state.dayCosts += cost;
        state.stock[product.ordinal()] += quantity;
        return true;
    }

    /** Moves goods from the stockroom onto a shelf; this is the manual restock tap. */
    public int restockShelf(int index) {
        Shelf s = shelf(index);
        if (s == null || !s.owned || s.product == null) return 0;
        int room = s.capacity(state) - s.stock;
        int available = state.stock[s.product.ordinal()];
        int moved = Math.min(room, available);
        if (moved <= 0) return 0;
        s.stock += moved;
        state.stock[s.product.ordinal()] -= moved;
        s.highlight = 1f;
        addPopup(s.x, 2.0f, s.z, "+" + moved, 0xFF7FC4FF);
        return moved;
    }

    /** Buys stock and puts it straight on the shelf in one action. */
    public int quickRestock(int index) {
        Shelf s = shelf(index);
        if (s == null || !s.owned || s.product == null) return 0;
        int room = s.capacity(state) - s.stock;
        if (room <= 0) return 0;
        int fromStockroom = Math.min(room, state.stock[s.product.ordinal()]);
        int shortfall = room - fromStockroom;
        if (shortfall > 0) {
            int affordable = (int) (state.money / Math.max(0.01f, s.product.wholesaleCost));
            int buy = Math.min(shortfall, affordable);
            if (buy > 0) orderStock(s.product, buy);
        }
        return restockShelf(index);
    }

    /** Player tapped the shopper at the till: hurry the transaction along. */
    public boolean serveAtRegister() {
        if (atRegister == null) return false;
        atRegister.rushBoost = 0.9f;
        atRegister.serviceProgress += atRegister.serviceDuration * 0.22f;
        return true;
    }

    public boolean assignProduct(int shelfIndex, ProductType product) {
        Shelf s = shelf(shelfIndex);
        if (s == null || !s.owned || product == null || !product.isUnlocked(state.level)) return false;
        if (s.product == product) return true;
        // Returning the old stock to the stockroom keeps re-merchandising lossless.
        if (s.product != null && s.stock > 0) {
            state.stock[s.product.ordinal()] += s.stock;
        }
        s.assign(product);
        return true;
    }

    public void setPrice(int shelfIndex, float price) {
        Shelf s = shelf(shelfIndex);
        if (s == null || s.product == null) return;
        s.price = MathUtil.clamp(price, s.product.wholesaleCost * 0.5f, s.product.basePrice * 3f);
    }

    /** Hit-tests a floor position against shelves, the till and shoppers. */
    public Selection pick(float worldX, float worldZ) {
        Selection best = new Selection();
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < customers.size(); i++) {
            Customer c = customers.get(i);
            float d = (float) Math.hypot(c.pos.x - worldX, c.pos.z - worldZ);
            if (d < 0.55f && d < bestDist) {
                bestDist = d;
                best.type = Selection.Type.CUSTOMER;
                best.index = i;
            }
        }

        for (int i = 0; i < shelves.size(); i++) {
            Shelf s = shelves.get(i);
            float halfW = SHELF_WIDTH * 0.5f + 0.35f;
            float halfD = SHELF_DEPTH * 0.5f + 0.35f;
            if (Math.abs(worldX - s.x) > halfW || Math.abs(worldZ - s.z) > halfD) continue;
            float d = (float) Math.hypot(s.x - worldX, s.z - worldZ);
            if (d < bestDist) {
                bestDist = d;
                best.type = Selection.Type.SHELF;
                best.index = i;
            }
        }

        if (worldX > COUNTER_MIN_X - 0.4f && worldX < COUNTER_MAX_X + 0.4f
                && worldZ > COUNTER_MIN_Z - 0.6f && worldZ < COUNTER_MAX_Z + 0.6f) {
            float d = (float) Math.hypot(REGISTER_X - worldX, REGISTER_Z - worldZ);
            if (d < bestDist) {
                best.type = Selection.Type.COUNTER;
                best.index = -1;
            }
        }
        return best;
    }

    /** Result of a tap on the 3D view. */
    public static final class Selection {
        public enum Type { NONE, SHELF, CUSTOMER, COUNTER }

        public Type type = Type.NONE;
        public int index = -1;
    }

    /** Floating world-space label rendered by the 2D overlay. */
    public static final class Popup {
        public float x, y, z;
        public String text;
        public int color;
        public float age, life;

        public float alpha() {
            float t = age / Math.max(0.01f, life);
            return MathUtil.clamp(1f - t * t, 0f, 1f);
        }
    }

    /** End-of-day numbers shown in the summary dialog. */
    public static final class DaySummary {
        public int day;
        public double revenue;
        public double stockCosts;
        public double rent;
        public double wages;
        public double profit;
        public int customersServed;
        public int customersLost;
        public float satisfaction;
    }
}
