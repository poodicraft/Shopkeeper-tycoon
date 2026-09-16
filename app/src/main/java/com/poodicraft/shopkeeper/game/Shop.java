package com.poodicraft.shopkeeper.game;

import com.poodicraft.shopkeeper.math.MathUtil;
import com.poodicraft.shopkeeper.world.ShopLayout;
import com.poodicraft.shopkeeper.world.WorldBuilder;

import java.util.ArrayList;
import java.util.Random;

/**
 * The shop as a place you work in.
 *
 * <p>Stock does not teleport onto shelves: it is ordered at the back-office
 * terminal, arrives in the stockroom, and has to be carried out and put away by
 * hand - by the player, or by a stocker once one is hired. Customers queue at the
 * till and stay queued until somebody scans their basket.
 */
public final class Shop {

    public final GameState state;
    public final Player player = new Player();
    public final NavGrid nav;
    public final Collision collision = new Collision();

    public final ArrayList<Shelf> shelves = new ArrayList<Shelf>();
    public final ArrayList<Customer> customers = new ArrayList<Customer>();
    public final ArrayList<Staff> staff = new ArrayList<Staff>();
    public final ArrayList<Popup> popups = new ArrayList<Popup>();

    public final Interaction interaction = new Interaction();

    /** Path clearance, comfortably wider than the widest character radius. */
    private static final float NAV_CLEARANCE = 0.36f;

    private final Random rng = new Random();
    private final ArrayList<float[]> pathScratch = new ArrayList<float[]>();
    private final boolean[] availability = new boolean[ProductType.ALL.length];

    private final ArrayList<Customer> queue = new ArrayList<Customer>();
    private Customer servingCustomer = null;
    /** True when a hired cashier, rather than the player, is working the till. */
    private boolean servedByStaff = false;

    private float spawnAccumulator = 0f;
    private float staffThinkTimer = 0f;
    private float scanTimer = 0f;

    public DaySummary pendingSummary = null;
    public int pendingLevelUps = 0;

    public interface Listener {
        void onSale(float amount, int itemCount);
        void onScanBeep();
        void onCustomerLost(String reason);
        void onLevelUp(int newLevel);
        void onStockPlaced(int units);
    }

    public Listener listener = null;

    public Shop(GameState state) {
        this.state = state;
        nav = new NavGrid(-ShopLayout.HALF_WIDTH - 0.5f, -ShopLayout.HALF_DEPTH - 0.5f,
                ShopLayout.HALF_WIDTH * 2 + 1f, ShopLayout.HALF_DEPTH * 2 + 3.5f, 0.35f);

        for (int i = 0; i < ShopLayout.SHELF_SLOTS; i++) shelves.add(new Shelf(i));
        // The lease comes with two units already fitted, so there is something to sell.
        shelves.get(0).owned = true;
        shelves.get(0).assign(ProductType.BREAD);
        shelves.get(1).owned = true;
        shelves.get(1).assign(ProductType.MILK);

        player.teleport(ShopLayout.SERVE_X + 1.2f, ShopLayout.SERVE_Z - 0.8f);
        player.heading = (float) Math.PI;

        rebuildObstacles();
        syncStaff();
    }

    // ------------------------------------------------------------- navigation

    /** Rebuilds collision and pathfinding. Call after a shelf is bought. */
    public void rebuildObstacles() {
        collision.clear();
        for (int i = 0; i < shelves.size(); i++) {
            Shelf shelf = shelves.get(i);
            if (!shelf.owned) continue;
            collision.addCentred(shelf.x, shelf.z,
                    ShopLayout.SHELF_WIDTH, ShopLayout.SHELF_DEPTH);
        }
        collision.addBox(ShopLayout.COUNTER_MIN_X, ShopLayout.COUNTER_MIN_Z,
                ShopLayout.COUNTER_MAX_X, ShopLayout.COUNTER_MAX_Z);
        collision.addCentred(ShopLayout.CHILLER_X, ShopLayout.CHILLER_Z,
                ShopLayout.CHILLER_WIDTH, ShopLayout.CHILLER_LENGTH);
        collision.addCentred(ShopLayout.TERMINAL_X, ShopLayout.TERMINAL_Z + 0.10f, 1.70f, 0.70f);
        // Pallet of crates beside the stockroom door.
        collision.addCentred(ShopLayout.STOCKROOM_X + 1.55f, ShopLayout.STOCKROOM_Z + 0.75f,
                1.40f, 1.30f);
        // Window display plinths along the front wall.
        collision.addCentred(-4.30f, ShopLayout.HALF_DEPTH - 0.34f, 2.90f, 0.45f);
        collision.addCentred(0.95f, ShopLayout.HALF_DEPTH - 0.34f, 2.40f, 0.45f);

        nav.clearObstacles();
        for (int r = 0; r < nav.rows; r++) {
            for (int c = 0; c < nav.cols; c++) {
                float x = nav.cellCenterX(c);
                float z = nav.cellCenterZ(r);
                boolean walkable;
                if (z > ShopLayout.HALF_DEPTH - 0.30f) {
                    walkable = x > ShopLayout.DOOR_MIN_X + 0.25f
                            && x < ShopLayout.DOOR_MAX_X - 0.25f
                            && z < ShopLayout.HALF_DEPTH + 2.0f;
                } else {
                    // Clear by more than a character's radius: a path that hugs a
                    // shelf corner is one nobody can actually walk down.
                    walkable = ShopLayout.insideRoom(x, z, 0.45f)
                            && !collision.blocked(x, z, NAV_CLEARANCE);
                }
                if (!walkable) nav.blockRect(x, z, x, z, 0f);
            }
        }
    }

    private boolean pathTo(Actor actor, float x, float z) {
        if (nav.findPath(actor.position.x, actor.position.z, x, z, pathScratch)) {
            actor.setPath(pathScratch);
            return true;
        }
        pathScratch.clear();
        pathScratch.add(new float[]{x, z});
        actor.setPath(pathScratch);
        return false;
    }

    // ------------------------------------------------------------------ staff

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
            s.setHome(ShopLayout.SERVE_X - 1.45f - haveCashiers * 1.2f, ShopLayout.SERVE_Z, 0f);
            s.teleport(s.homeX, s.homeZ);
            staff.add(s);
            haveCashiers++;
        }
        while (haveStockers < wantStockers) {
            Staff s = new Staff(Staff.Role.STOCKER, rng);
            s.setHome(ShopLayout.STOCKROOM_STAND_X + 1.4f + haveStockers * 0.9f,
                    ShopLayout.STOCKROOM_STAND_Z + 0.5f, (float) Math.PI);
            s.teleport(s.homeX, s.homeZ);
            staff.add(s);
            haveStockers++;
        }
    }

    // ----------------------------------------------------------------- update

    public void update(float dt) {
        pendingLevelUps = 0;
        advanceClock(dt);
        updateSpawning(dt);
        updateCustomers(dt);
        updateStaff(dt);
        updatePlayerTask(dt);
        updateInteraction();
        updatePopups(dt);
        player.updatePose(dt);
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
        state.adjustSatisfaction((0.72f - state.satisfaction) * 0.18f);
        pendingSummary = summary;
    }

    private void updateSpawning(float dt) {
        if (!state.isOpen() || customers.size() >= 14) return;

        float rate = 0.16f * state.marketingMultiplier() * state.trafficCurve()
                * (0.55f + state.satisfaction * 0.85f);
        if (!anyShelfStocked()) rate *= 0.10f;
        int waiting = queue.size();
        // People who can see a long queue through the window keep walking, which is
        // kinder than letting them come in and storm out.
        if (waiting >= 3) rate *= Math.max(0.12f, 1f - (waiting - 2) * 0.24f);

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

    private void spawnCustomer() {
        for (int i = 0; i < availability.length; i++) availability[i] = false;
        for (int i = 0; i < shelves.size(); i++) {
            Shelf s = shelves.get(i);
            if (s.owned && s.product != null && s.stock > 0) {
                availability[s.product.ordinal()] = true;
            }
        }
        Customer c = new Customer();
        c.init(rng, state, state.priceTolerance(), availability);
        c.teleport(ShopLayout.DOOR_CENTER_X + (rng.nextFloat() - 0.5f) * 0.9f,
                ShopLayout.HALF_DEPTH + 1.5f);
        c.heading = (float) Math.PI;
        customers.add(c);
        chooseNextGoal(c);
    }

    /** Sends a shopper to the best shelf for their list, or to the till, or home. */
    private void chooseNextGoal(Customer c) {
        Shelf best = null;
        float bestScore = -Float.MAX_VALUE;
        for (int w = 0; w < c.wants.size(); w++) {
            Customer.Want want = c.wants.get(w);
            if (want.quantity <= 0) continue;
            for (int i = 0; i < shelves.size(); i++) {
                Shelf s = shelves.get(i);
                if (!s.owned || s.product != want.product || s.stock <= 0) continue;
                if (s.price > c.acceptablePrice(want.product)) continue;
                float distance = (float) Math.hypot(s.approachX - c.position.x,
                        s.approachZ - c.position.z);
                float value = 1f - MathUtil.clamp(
                        s.price / Math.max(0.01f, c.acceptablePrice(want.product)), 0f, 1f);
                float score = value * 6f - distance * 0.22f;
                if (score > bestScore) {
                    bestScore = score;
                    best = s;
                }
            }
        }

        if (best != null) {
            c.targetShelf = best.index;
            c.enterState(Customer.State.TO_SHELF);
            c.animator.reachHeight = best.reachHeight(state);
            pathTo(c, best.approachX + (rng.nextFloat() - 0.5f) * 0.7f, best.approachZ);
            return;
        }
        if (!c.basket.isEmpty()) joinQueue(c);
        else leaveUpset(c, "Nothing for me", 0.006f);
    }

    private void joinQueue(Customer c) {
        if (queue.size() >= ShopLayout.MAX_QUEUE) {
            leaveUpset(c, "Queue too long", 0.020f);
            return;
        }
        c.queueSlot = queue.size();
        queue.add(c);
        c.enterState(Customer.State.TO_QUEUE);
        pathTo(c, queueSlotX(c.queueSlot), ShopLayout.QUEUE_Z);
    }

    public float queueSlotX(int slot) {
        return ShopLayout.QUEUE_HEAD_X + slot * ShopLayout.QUEUE_SPACING;
    }

    private void leaveUpset(Customer c, String reason, float satisfactionHit) {
        returnBasket(c);
        c.enterState(Customer.State.LEAVING_UPSET);
        c.mood = 0.12f;
        c.say(reason, 2.2f);
        removeFromQueue(c);
        pathTo(c, ShopLayout.DOOR_CENTER_X, ShopLayout.HALF_DEPTH + 1.9f);
        state.adjustSatisfaction(-satisfactionHit);
        state.dayLost++;
        state.totalCustomersLost++;
        addPopup(c.position.x, 1.95f, c.position.z, reason, 0xFFE0655A);
        if (listener != null) listener.onCustomerLost(reason);
    }

    /** Puts abandoned items back, so stock is never destroyed. */
    private void returnBasket(Customer c) {
        for (int i = 0; i < c.basket.size(); i++) {
            Customer.BasketItem item = c.basket.get(i);
            Shelf s = shelf(item.shelfIndex);
            if (s != null && s.product == item.product && s.stock < s.capacity(state)) {
                s.changeStock(1);
            } else {
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
        if (servingCustomer == c) {
            servingCustomer = null;
            if (player.task == Player.Task.SERVING) player.finishTask();
        }
        c.queueSlot = -1;
    }

    private void resequenceQueue() {
        for (int i = 0; i < queue.size(); i++) {
            Customer c = queue.get(i);
            if (c.queueSlot == i) continue;
            c.queueSlot = i;
            if (c.state == Customer.State.QUEUEING || c.state == Customer.State.TO_QUEUE) {
                c.enterState(Customer.State.TO_QUEUE);
                pathTo(c, queueSlotX(i), ShopLayout.QUEUE_Z);
            }
        }
    }

    private void updateCustomers(float dt) {
        for (int i = customers.size() - 1; i >= 0; i--) {
            Customer c = customers.get(i);
            c.stateTimer += dt;
            c.fadeIn = MathUtil.approach(c.fadeIn, 1f, dt * 2.5f);
            boolean walking = false;

            switch (c.state) {
                case ENTERING:
                case TO_SHELF:
                    walking = c.hasPath();
                    if (c.followPath(dt, c.walkSpeed, collision) || !c.hasPath()) {
                        Shelf s = shelf(c.targetShelf);
                        if (s == null || !s.isStocked()) chooseNextGoal(c);
                        else {
                            c.enterState(Customer.State.BROWSING);
                            c.animator.reachHeight = s.reachHeight(state);
                        }
                    }
                    break;

                case BROWSING:
                    stepBrowsing(c, dt);
                    break;

                case TO_QUEUE:
                    walking = c.hasPath();
                    c.patienceLeft -= dt * 0.4f;
                    if (c.followPath(dt, c.walkSpeed, collision) || !c.hasPath()) {
                        c.enterState(Customer.State.QUEUEING);
                    }
                    break;

                case QUEUEING:
                    walking = stepQueueing(c, dt);
                    break;

                case BEING_SERVED:
                    c.faceTowards(ShopLayout.TILL_X, ShopLayout.TILL_Z, dt, 5f);
                    c.patienceLeft -= dt * 0.25f;
                    c.mood = MathUtil.clamp(c.patienceLeft / Math.max(1f, c.patience), 0f, 1f);
                    break;

                case LEAVING:
                case LEAVING_UPSET:
                    walking = c.hasPath();
                    c.followPath(dt, c.walkSpeed * 1.1f, collision);
                    if (!c.hasPath()) {
                        c.fadeOut -= dt * 1.5f;
                        if (c.fadeOut <= 0f) c.finished = true;
                    }
                    break;
            }

            c.updateAnimation(dt, walking);
            c.updatePose(dt);

            if (c.finished) {
                removeFromQueue(c);
                customers.remove(i);
            }
        }
    }

    private void stepBrowsing(Customer c, float dt) {
        Shelf s = shelf(c.targetShelf);
        if (s == null) {
            chooseNextGoal(c);
            return;
        }
        c.faceTowards(s.x, s.z, dt, 6f);
        float browseTime = 1.6f + (c.appearance.height - 0.9f) * 2.2f;
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
        s.changeStock(-taken);
        for (int i = 0; i < taken; i++) {
            c.basket.add(new Customer.BasketItem(s.product, s.index, s.price));
        }
        c.satisfyWant(s.product, taken);
        c.say(taken + "x " + s.product.displayName, 1.6f);
        c.targetShelf = -1;
        chooseNextGoal(c);
    }

    /** @return true when the shopper is still shuffling toward their slot */
    private boolean stepQueueing(Customer c, float dt) {
        float slotX = queueSlotX(c.queueSlot);
        float dx = slotX - c.position.x;
        float dz = ShopLayout.QUEUE_Z - c.position.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        boolean moving = distance > 0.10f;
        if (moving) {
            float step = Math.min(c.walkSpeed * dt, distance);
            c.position.x += dx / distance * step;
            c.position.z += dz / distance * step;
        }
        c.faceTowards(ShopLayout.TILL_X, ShopLayout.TILL_Z, dt, 5f);

        c.patienceLeft -= dt;
        c.mood = MathUtil.clamp(c.patienceLeft / Math.max(1f, c.patience), 0f, 1f);
        if (c.patienceLeft <= 0f) leaveUpset(c, "Waited too long", 0.024f);
        return moving;
    }

    // ------------------------------------------------------------ player work

    private void updatePlayerTask(float dt) {
        player.updateTaskAnimation(dt);
        if (player.task == Player.Task.FREE) return;

        player.actionProgress += dt;

        if (player.task == Player.Task.STOCKING) {
            Shelf s = shelf(player.actionShelf);
            if (s == null || !player.isCarrying()) {
                player.finishTask();
                return;
            }
            player.faceTowards(s.x, s.z, dt, 6f);
            player.animator.reachHeight = s.reachHeight(state);

            float perUnit = player.actionDuration / Math.max(1, stockingUnits);
            while (stockedSoFar < stockingUnits
                    && player.actionProgress >= perUnit * (stockedSoFar + 1)) {
                if (s.stock >= s.capacity(state) || player.carryCount <= 0) break;
                s.changeStock(1);
                player.carryCount--;
                stockedSoFar++;
            }
            if (player.carryCount <= 0) player.carrying = null;
            if (player.actionProgress >= player.actionDuration
                    || stockedSoFar >= stockingUnits) {
                if (listener != null && stockedSoFar > 0) listener.onStockPlaced(stockedSoFar);
                addPopup(s.x, ShopLayout.SHELF_HEIGHT + 0.35f, s.z, "+" + stockedSoFar, 0xFF7FC4FF);
                player.finishTask();
            }
            return;
        }

        if (player.task == Player.Task.SERVING) {
            Customer c = servingCustomer;
            if (c == null || c.state != Customer.State.BEING_SERVED) {
                player.finishTask();
                return;
            }
            player.easeToward(ShopLayout.SERVE_X, ShopLayout.SERVE_Z, dt);
            player.faceTowards(c.position.x, c.position.z, dt, 6f);

            scanTimer += dt;
            float perItem = state.scanTime();
            while (c.scanned < c.basketCount() && scanTimer >= perItem) {
                scanTimer -= perItem;
                c.scanned++;
                if (listener != null) listener.onScanBeep();
            }
            if (c.scanned >= c.basketCount()) {
                completeSale(c, false);
                player.finishTask();
            }
        }
    }

    private int stockingUnits = 0;
    private int stockedSoFar = 0;

    private void completeSale(Customer c, boolean byStaff) {
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
        state.adjustSatisfaction(MathUtil.clamp((1.15f - valueRatio) * 0.05f, -0.03f, 0.025f)
                + c.mood * 0.008f);

        int levels = state.addXp(7f + total * 0.09f);
        if (levels > 0) {
            pendingLevelUps += levels;
            if (listener != null) listener.onLevelUp(state.level);
        }

        addPopup(c.position.x, 1.95f, c.position.z, "+" + Money.format(total), 0xFF6FE08A);
        if (listener != null) listener.onSale(total, items);

        c.basket.clear();
        c.say("Thanks!", 1.8f);
        c.enterState(Customer.State.LEAVING);
        servingCustomer = null;
        servedByStaff = false;
        removeFromQueue(c);
        pathTo(c, ShopLayout.DOOR_CENTER_X, ShopLayout.HALF_DEPTH + 1.9f);
    }

    // ----------------------------------------------------------------- staff

    private void updateStaff(float dt) {
        staffThinkTimer -= dt;
        boolean think = staffThinkTimer <= 0f;
        if (think) staffThinkTimer = 0.4f;

        for (int i = 0; i < staff.size(); i++) {
            Staff s = staff.get(i);
            if (s.role == Staff.Role.CASHIER) updateCashier(s, dt);
            else updateStocker(s, dt, think);
            s.updatePose(dt);
        }
    }

    private void updateCashier(Staff s, float dt) {
        float dx = s.homeX - s.position.x;
        float dz = s.homeZ - s.position.z;
        boolean walking = dx * dx + dz * dz > 0.04f;
        if (walking) {
            if (!s.hasPath()) pathTo(s, s.homeX, s.homeZ);
            s.followPath(dt, s.walkSpeed, collision);
        } else {
            s.clearPath();
        }

        boolean serving = false;
        // A cashier takes the queue whenever the player is not already on it.
        if (!walking && servingCustomer == null && !queue.isEmpty()
                && player.task != Player.Task.SERVING) {
            Customer head = queue.get(0);
            if (head.state == Customer.State.QUEUEING
                    && Math.abs(head.position.x - queueSlotX(0)) < 0.45f) {
                servingCustomer = head;
                servedByStaff = true;
                head.enterState(Customer.State.BEING_SERVED);
                head.scanned = 0;
                scanTimer = 0f;
            }
        }
        if (servedByStaff && servingCustomer != null) {
            serving = true;
            s.faceTowards(servingCustomer.position.x, servingCustomer.position.z, dt, 5f);
            scanTimer += dt;
            // Staff work steadily but a shade slower than an attentive owner.
            float perItem = state.scanTime() * 1.25f;
            while (servingCustomer.scanned < servingCustomer.basketCount() && scanTimer >= perItem) {
                scanTimer -= perItem;
                servingCustomer.scanned++;
                if (listener != null) listener.onScanBeep();
            }
            if (servingCustomer.scanned >= servingCustomer.basketCount()) {
                completeSale(servingCustomer, true);
            }
        } else if (!walking) {
            s.faceTowards(ShopLayout.QUEUE_HEAD_X, ShopLayout.QUEUE_Z, dt, 3f);
        }
        s.updateAnimation(dt, walking, serving, false);
    }

    private void updateStocker(Staff s, float dt, boolean think) {
        boolean walking = false;
        boolean stocking = false;

        switch (s.task) {
            case IDLE:
                if (think && state.autoRestockEnabled) {
                    Shelf target = findRestockTarget();
                    if (target != null) {
                        target.restockClaimed = true;
                        s.targetShelf = target.index;
                        s.carrying = target.product;
                        s.task = Staff.Task.TO_STOCKROOM;
                        pathTo(s, ShopLayout.STOCKROOM_STAND_X, ShopLayout.STOCKROOM_STAND_Z);
                    }
                }
                break;

            case TO_STOCKROOM:
                walking = s.hasPath();
                if (s.followPath(dt, s.walkSpeed, collision) || !s.hasPath()) {
                    s.task = Staff.Task.LOADING;
                    s.taskTimer = 1.0f;
                }
                break;

            case LOADING: {
                s.taskTimer -= dt;
                if (s.taskTimer > 0f) break;
                Shelf target = shelf(s.targetShelf);
                if (target == null || target.product != s.carrying) {
                    abortStockerTask(s);
                    break;
                }
                int room = target.capacity(state) - target.stock;
                int available = state.stock[s.carrying.ordinal()];
                int load = Math.min(Math.min(room, available), 12);
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

            case TO_SHELF:
                walking = s.hasPath();
                if (s.followPath(dt, s.walkSpeed, collision) || !s.hasPath()) {
                    s.task = Staff.Task.STOCKING;
                    s.taskTimer = 1.6f;
                    Shelf target = shelf(s.targetShelf);
                    if (target != null) s.animator.reachHeight = target.reachHeight(state);
                }
                break;

            case STOCKING: {
                stocking = true;
                s.taskTimer -= dt;
                Shelf target = shelf(s.targetShelf);
                if (target != null) {
                    s.faceTowards(target.x, target.z, dt, 5f);
                    s.animator.reachHeight = target.reachHeight(state);
                }
                if (s.taskTimer > 0f) break;
                if (target != null && target.product == s.carrying) {
                    int placed = Math.min(s.carryCount, target.capacity(state) - target.stock);
                    target.changeStock(placed);
                    s.carryCount -= placed;
                    addPopup(target.x, ShopLayout.SHELF_HEIGHT + 0.35f, target.z,
                            "+" + placed, 0xFF7FC4FF);
                }
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
                walking = s.hasPath();
                if (s.followPath(dt, s.walkSpeed, collision) || !s.hasPath()) {
                    s.task = Staff.Task.IDLE;
                }
                break;
        }
        s.updateAnimation(dt, walking, false, stocking);
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

    // ----------------------------------------------------------- interaction

    private void updateInteraction() {
        interaction.clear();
        if (player.task != Player.Task.FREE) return;

        float px = player.position.x, pz = player.position.z;

        // Till takes priority: a waiting customer is the most urgent thing in the shop.
        if (distance(px, pz, ShopLayout.SERVE_X, ShopLayout.SERVE_Z) < 1.30f) {
            Customer head = queue.isEmpty() ? null : queue.get(0);
            boolean ready = head != null
                    && (head.state == Customer.State.QUEUEING || head.state == Customer.State.BEING_SERVED)
                    && Math.abs(head.position.x - queueSlotX(0)) < 0.55f;
            if (ready) {
                interaction.set(Interaction.Kind.SERVE, "Serve customer",
                        head.basketCount() + " items · " + Money.exact(head.basketTotal()),
                        true, ShopLayout.TILL_X, 1.45f, ShopLayout.TILL_Z);
            } else {
                interaction.set(Interaction.Kind.SERVE, "Nobody waiting", null, false,
                        ShopLayout.TILL_X, 1.45f, ShopLayout.TILL_Z);
            }
            return;
        }

        // Stockroom hatch.
        if (distance(px, pz, ShopLayout.STOCKROOM_STAND_X, ShopLayout.STOCKROOM_STAND_Z) < 1.45f) {
            if (player.isCarrying()) {
                interaction.set(Interaction.Kind.RETURN_CRATE, "Put crate back",
                        player.carryCount + "x " + player.carrying.displayName, true,
                        ShopLayout.STOCKROOM_X, 1.45f, ShopLayout.STOCKROOM_Z + 0.3f);
            } else {
                int total = state.totalStock();
                interaction.set(Interaction.Kind.COLLECT_STOCK,
                        total > 0 ? "Collect stock" : "Stockroom empty",
                        total > 0 ? total + " units waiting" : "Order more at the desk",
                        total > 0, ShopLayout.STOCKROOM_X, 1.45f, ShopLayout.STOCKROOM_Z + 0.3f);
            }
            return;
        }

        // Back-office terminal.
        if (distance(px, pz, ShopLayout.TERMINAL_STAND_X, ShopLayout.TERMINAL_STAND_Z) < 1.30f) {
            interaction.set(Interaction.Kind.TERMINAL, "Open terminal",
                    "Order stock · Upgrades", true,
                    ShopLayout.TERMINAL_X, 1.35f, ShopLayout.TERMINAL_Z);
            return;
        }

        // Nearest shelf.
        Shelf nearest = null;
        float nearestDistance = 1.70f;
        for (int i = 0; i < shelves.size(); i++) {
            Shelf s = shelves.get(i);
            float d = distance(px, pz, s.approachX, s.approachZ);
            if (d < nearestDistance) {
                nearestDistance = d;
                nearest = s;
            }
        }
        if (nearest == null) return;

        float anchorY = ShopLayout.SHELF_HEIGHT + 0.30f;
        if (!nearest.owned) {
            boolean affordable = state.money >= nearest.purchaseCost;
            interaction.set(Interaction.Kind.BUY_SHELF, "Fit shelving",
                    Money.exact(nearest.purchaseCost), affordable, nearest.x, 1.1f, nearest.z);
            interaction.shelfIndex = nearest.index;
            return;
        }

        if (player.isCarrying()) {
            boolean matches = nearest.product == null || nearest.product == player.carrying;
            boolean room = nearest.product == null
                    || nearest.stock < nearest.capacity(state);
            interaction.set(Interaction.Kind.STOCK_SHELF,
                    matches && room ? "Stock shelf" : (room ? "Wrong product" : "Shelf is full"),
                    player.carryCount + "x " + player.carrying.displayName,
                    matches && room, nearest.x, anchorY, nearest.z);
            interaction.shelfIndex = nearest.index;
            return;
        }

        interaction.set(Interaction.Kind.EDIT_SHELF, "Shelf settings",
                nearest.product == null ? "Empty" :
                        nearest.product.displayName + " · " + Money.exact(nearest.price)
                                + " · " + nearest.stock + " left",
                true, nearest.x, anchorY, nearest.z);
        interaction.shelfIndex = nearest.index;
    }

    private static float distance(float x0, float z0, float x1, float z1) {
        float dx = x1 - x0, dz = z1 - z0;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    // -------------------------------------------------------- player actions

    /** Picks up a crate of {@code product} from the stockroom. */
    public boolean collectStock(ProductType product) {
        if (product == null || player.isCarrying()) return false;
        int available = state.stock[product.ordinal()];
        if (available <= 0) return false;
        int take = Math.min(available, player.carryCapacity(state));
        state.stock[product.ordinal()] -= take;
        player.carrying = product;
        player.carryCount = take;
        return true;
    }

    /** Puts a carried crate back in the stockroom. */
    public boolean returnCrate() {
        if (!player.isCarrying()) return false;
        state.stock[player.carrying.ordinal()] += player.carryCount;
        player.carrying = null;
        player.carryCount = 0;
        return true;
    }

    /** Starts filling a shelf from the crate the player is holding. */
    public boolean stockShelf(int shelfIndex) {
        Shelf s = shelf(shelfIndex);
        if (s == null || !s.owned || !player.isCarrying()) return false;
        if (s.product == null) s.assign(player.carrying);
        else if (s.product != player.carrying) return false;

        int room = s.capacity(state) - s.stock;
        if (room <= 0) return false;
        stockingUnits = Math.min(room, player.carryCount);
        stockedSoFar = 0;
        if (stockingUnits <= 0) return false;

        player.easeToward(s.approachX, s.approachZ, 1f);
        player.beginStocking(shelfIndex, s.reachHeight(state),
                Math.max(0.9f, stockingUnits * 0.14f));
        return true;
    }

    /** Starts scanning the basket of whoever is at the head of the queue. */
    public boolean serveCustomer() {
        if (queue.isEmpty() || servingCustomer != null) return false;
        Customer head = queue.get(0);
        if (head.state != Customer.State.QUEUEING) return false;
        servingCustomer = head;
        servedByStaff = false;
        head.enterState(Customer.State.BEING_SERVED);
        head.scanned = 0;
        scanTimer = 0f;
        player.beginServing(Math.max(0.6f, head.basketCount() * state.scanTime()));
        return true;
    }

    public boolean buyShelf(int shelfIndex) {
        Shelf s = shelf(shelfIndex);
        if (s == null || s.owned || state.money < s.purchaseCost) return false;
        state.money -= s.purchaseCost;
        state.dayCosts += s.purchaseCost;
        s.owned = true;
        s.goodsDirty = true;
        rebuildObstacles();
        addPopup(s.x, 1.6f, s.z, "Shelving fitted", 0xFFFFD166);
        return true;
    }

    public boolean orderStock(ProductType product, int quantity) {
        float cost = product.orderCost(quantity);
        if (state.money < cost) return false;
        state.money -= cost;
        state.dayCosts += cost;
        state.stock[product.ordinal()] += quantity;
        return true;
    }

    public boolean assignProduct(int shelfIndex, ProductType product) {
        Shelf s = shelf(shelfIndex);
        if (s == null || !s.owned || product == null || !product.isUnlocked(state.level)) return false;
        if (s.product == product) return true;
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

    // --------------------------------------------------------------- queries

    public Shelf shelf(int index) {
        return index >= 0 && index < shelves.size() ? shelves.get(index) : null;
    }

    public int ownedShelfCount() {
        int n = 0;
        for (int i = 0; i < shelves.size(); i++) if (shelves.get(i).owned) n++;
        return n;
    }

    public int queueLength() { return queue.size(); }

    public Customer queueHead() { return queue.isEmpty() ? null : queue.get(0); }

    public Customer servingCustomer() { return servingCustomer; }

    public void addPopup(float x, float y, float z, String text, int color) {
        Popup p = new Popup();
        p.x = x; p.y = y; p.z = z;
        p.text = text;
        p.color = color;
        p.life = 1.6f;
        popups.add(p);
    }

    private void updatePopups(float dt) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.get(i);
            p.age += dt;
            p.y += dt * 0.55f;
            if (p.age >= p.life) popups.remove(i);
        }
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
