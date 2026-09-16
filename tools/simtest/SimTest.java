import com.poodicraft.shopkeeper.game.Collision;
import com.poodicraft.shopkeeper.game.Customer;
import com.poodicraft.shopkeeper.game.GameState;
import com.poodicraft.shopkeeper.game.Interaction;
import com.poodicraft.shopkeeper.game.Money;
import com.poodicraft.shopkeeper.game.Player;
import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.game.Shelf;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.game.Staff;
import com.poodicraft.shopkeeper.game.Upgrade;
import com.poodicraft.shopkeeper.world.ShopLayout;

import java.util.ArrayList;

/**
 * Headless play-through.
 *
 * <p>The game package has no Android imports, so the whole shop can be driven on a
 * plain JVM. The player here is not teleported: an autopilot paths across the floor
 * and walks with the same collision the real controls use, so if a fitting boxes the
 * player in or an interaction never triggers, this catches it.
 */
public final class SimTest {

    private static int failures = 0;
    private static int checks = 0;
    private static final float STEP = 1f / 45f;

    public static void main(String[] args) {
        testPlayerCanReachEverything();
        testInteractionsAppear();
        testFullShopkeeperLoop();
        testStockIsConserved();
        testTradingDays();

        System.out.println();
        if (failures == 0) {
            System.out.println("All " + checks + " checks passed.");
        } else {
            System.out.println(failures + " of " + checks + " checks FAILED.");
            System.exit(1);
        }
    }

    // --------------------------------------------------------------- walking

    private static void testPlayerCanReachEverything() {
        section("Getting around the shop");
        Shop shop = new Shop(new GameState());
        for (int i = 0; i < shop.shelves.size(); i++) shop.shelves.get(i).owned = true;
        shop.rebuildObstacles();

        check("the player starts somewhere walkable",
                !shop.collision.blocked(shop.player.position.x, shop.player.position.z,
                        shop.player.radius));

        check("can walk to the till", walkTo(shop, ShopLayout.SERVE_X, ShopLayout.SERVE_Z, 22f));
        check("can walk to the stockroom",
                walkTo(shop, ShopLayout.STOCKROOM_STAND_X, ShopLayout.STOCKROOM_STAND_Z, 22f));
        check("can walk to the back office",
                walkTo(shop, ShopLayout.TERMINAL_STAND_X, ShopLayout.TERMINAL_STAND_Z, 22f));

        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            check("can walk to shelf " + (i + 1),
                    walkTo(shop, shelf.approachX, shelf.approachZ, 22f));
        }
        check("can walk back out of the door",
                walkTo(shop, ShopLayout.DOOR_CENTER_X, ShopLayout.HALF_DEPTH - 0.9f, 22f));

        // Collision must not let the player through the fittings.
        check("the counter is solid",
                shop.collision.blocked(ShopLayout.TILL_X, ShopLayout.TILL_Z, 0.3f));
        check("shelves are solid",
                shop.collision.blocked(shop.shelves.get(0).x, shop.shelves.get(0).z, 0.3f));
        check("the spot in front of a shelf is clear",
                !shop.collision.blocked(shop.shelves.get(0).approachX,
                        shop.shelves.get(0).approachZ, 0.3f));
        check("walls hold the player in",
                shop.collision.blocked(0f, -ShopLayout.HALF_DEPTH - 0.5f, 0.3f));
    }

    private static void testInteractionsAppear() {
        section("Interaction prompts");
        Shop shop = new Shop(new GameState());
        shop.orderStock(ProductType.BREAD, 40);

        walkTo(shop, ShopLayout.TERMINAL_STAND_X, ShopLayout.TERMINAL_STAND_Z, 22f);
        settle(shop, 0.3f);
        check("the office desk offers the terminal",
                shop.interaction.kind == Interaction.Kind.TERMINAL);

        walkTo(shop, ShopLayout.STOCKROOM_STAND_X, ShopLayout.STOCKROOM_STAND_Z, 22f);
        settle(shop, 0.3f);
        check("the stockroom offers a crate",
                shop.interaction.kind == Interaction.Kind.COLLECT_STOCK
                        && shop.interaction.enabled);

        shop.collectStock(ProductType.BREAD);
        check("picking up a crate fills the player's hands", shop.player.isCarrying());

        Shelf shelf = shop.shelves.get(0);
        walkTo(shop, shelf.approachX, shelf.approachZ, 22f);
        settle(shop, 0.3f);
        check("a shelf offers stocking while carrying",
                shop.interaction.kind == Interaction.Kind.STOCK_SHELF
                        && shop.interaction.enabled);

        Shelf empty = null;
        for (int i = 0; i < shop.shelves.size(); i++) {
            if (!shop.shelves.get(i).owned) { empty = shop.shelves.get(i); break; }
        }
        shop.returnCrate();
        walkTo(shop, empty.approachX, empty.approachZ, 22f);
        settle(shop, 0.3f);
        check("an empty slot offers shelving",
                shop.interaction.kind == Interaction.Kind.BUY_SHELF);

        walkTo(shop, ShopLayout.SERVE_X, ShopLayout.SERVE_Z, 22f);
        settle(shop, 0.3f);
        check("the till offers serving",
                shop.interaction.kind == Interaction.Kind.SERVE);
    }

    // ------------------------------------------------------------- full loop

    private static void testFullShopkeeperLoop() {
        section("A shift behind the counter");
        Shop shop = new Shop(new GameState());
        GameState state = shop.state;
        state.money = 900;
        state.dayTime = GameState.DAY_LENGTH * 0.40f;

        final int[] sales = new int[1];
        final int[] beeps = new int[1];
        final int[] placed = new int[1];
        shop.listener = new Shop.Listener() {
            @Override public void onSale(float amount, int itemCount) { sales[0]++; }
            @Override public void onScanBeep() { beeps[0]++; }
            @Override public void onCustomerLost(String reason) { }
            @Override public void onLevelUp(int newLevel) { }
            @Override public void onStockPlaced(int units) { placed[0] += units; }
        };

        // Order at the desk.
        walkTo(shop, ShopLayout.TERMINAL_STAND_X, ShopLayout.TERMINAL_STAND_Z, 25f);
        check("ordering bread succeeds", shop.orderStock(ProductType.BREAD, 60));
        check("ordering milk succeeds", shop.orderStock(ProductType.MILK, 60));

        // Fill both shelves by hand.
        for (int trip = 0; trip < 4; trip++) {
            ProductType product = trip % 2 == 0 ? ProductType.BREAD : ProductType.MILK;
            int shelfIndex = trip % 2;
            walkTo(shop, ShopLayout.STOCKROOM_STAND_X, ShopLayout.STOCKROOM_STAND_Z, 25f);
            shop.collectStock(product);
            Shelf shelf = shop.shelves.get(shelfIndex);
            walkTo(shop, shelf.approachX, shelf.approachZ, 25f);
            shop.stockShelf(shelfIndex);
            settle(shop, 3.5f);
        }
        check("bread made it onto the shelf (" + shop.shelves.get(0).stock + ")",
                shop.shelves.get(0).stock > 0);
        check("milk made it onto the shelf (" + shop.shelves.get(1).stock + ")",
                shop.shelves.get(1).stock > 0);
        check("stocking reported units placed (" + placed[0] + ")", placed[0] > 0);

        // Work the till for the rest of the morning.
        walkTo(shop, ShopLayout.SERVE_X, ShopLayout.SERVE_Z, 25f);
        double before = state.money;
        float elapsed = 0f;
        while (elapsed < 80f) {
            stepIdle(shop);
            elapsed += STEP;
            if (shop.player.task == Player.Task.FREE
                    && shop.interaction.kind == Interaction.Kind.SERVE
                    && shop.interaction.enabled) {
                shop.serveCustomer();
            }
        }

        check("shoppers came in", state.totalCustomersServed + state.totalCustomersLost > 0);
        check("baskets were scanned item by item (" + beeps[0] + " beeps)", beeps[0] > 0);
        check("sales completed (" + sales[0] + ")", sales[0] > 0);
        check("takings went up (" + Money.exact(state.money - before) + ")", state.money > before);
    }

    // ----------------------------------------------------------- conservation

    private static void testStockIsConserved() {
        section("Stock conservation");
        Shop shop = new Shop(new GameState());
        GameState state = shop.state;
        state.money = 100000;
        state.upgradeLevels[Upgrade.MARKETING.ordinal()] = 4;
        state.upgradeLevels[Upgrade.CASHIER.ordinal()] = 1;
        state.upgradeLevels[Upgrade.STOCKER.ordinal()] = 1;
        shop.syncStaff();

        // Only bread is ever on a shelf, so every unit that moves is a loaf.
        shop.shelves.get(1).assign(ProductType.BREAD);
        final int[] sold = new int[1];
        shop.listener = new Shop.Listener() {
            @Override public void onSale(float amount, int itemCount) { sold[0] += itemCount; }
            @Override public void onScanBeep() { }
            @Override public void onCustomerLost(String reason) { }
            @Override public void onLevelUp(int newLevel) { }
            @Override public void onStockPlaced(int units) { }
        };

        shop.orderStock(ProductType.BREAD, 400);
        int start = state.stockOf(ProductType.BREAD);

        state.dayTime = GameState.DAY_LENGTH * 0.4f;
        for (int i = 0; i < 45 * 200; i++) stepIdle(shop);

        int onShelves = 0;
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (shelf.product == ProductType.BREAD) onShelves += shelf.stock;
        }
        int inBaskets = 0;
        for (int i = 0; i < shop.customers.size(); i++) {
            Customer c = shop.customers.get(i);
            for (int j = 0; j < c.basket.size(); j++) {
                if (c.basket.get(j).product == ProductType.BREAD) inBaskets++;
            }
        }
        int inStaffHands = 0;
        for (int i = 0; i < shop.staff.size(); i++) {
            Staff s = shop.staff.get(i);
            if (s.carrying == ProductType.BREAD) inStaffHands += s.carryCount;
        }
        int inPlayerHands = shop.player.carrying == ProductType.BREAD ? shop.player.carryCount : 0;

        int accounted = state.stockOf(ProductType.BREAD) + onShelves + inBaskets
                + inStaffHands + inPlayerHands + sold[0];
        check("every loaf is accounted for (" + accounted + " of " + start + ")",
                accounted == start);
        check("some bread actually sold (" + sold[0] + ")", sold[0] > 0);
        check("no shelf exceeds its capacity", noShelfOverfull(shop));
    }

    private static boolean noShelfOverfull(Shop shop) {
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (shelf.stock < 0 || shelf.stock > shelf.capacity(shop.state)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------- economy

    private static void testTradingDays() {
        section("Five days with a working shopkeeper");
        Shop shop = new Shop(new GameState());
        GameState state = shop.state;

        int summaries = 0;
        double lowest = state.money;
        float elapsed = 0f;
        float total = GameState.DAY_LENGTH * 5;

        while (elapsed < total) {
            runAutopilot(shop);
            elapsed += STEP;
            lowest = Math.min(lowest, state.money);
            if (shop.pendingSummary != null) {
                Shop.DaySummary summary = shop.pendingSummary;
                shop.pendingSummary = null;
                summaries++;
                System.out.printf("    day %d: revenue %s, profit %s, served %d, lost %d%n",
                        summary.day, Money.exact(summary.revenue), Money.exact(summary.profit),
                        summary.customersServed, summary.customersLost);
            }
            if (Double.isNaN(state.money)) break;
        }

        check("five days closed out", summaries == 5);
        check("money stayed a real number",
                !Double.isNaN(state.money) && !Double.isInfinite(state.money));
        check("satisfaction stayed in range",
                state.satisfaction >= 0f && state.satisfaction <= 1f);
        check("the shop levelled up (level " + state.level + ")", state.level > 1);
        check("customers were served (" + state.totalCustomersServed + ")",
                state.totalCustomersServed > 25);
        check("the shop turned a profit (" + Money.exact(state.money) + ")", state.money > 400);
        check("no shelf exceeds its capacity", noShelfOverfull(shop));
        for (int i = 0; i < ProductType.ALL.length; i++) {
            check("stockroom count for " + ProductType.ALL[i].displayName + " is not negative",
                    state.stockOf(ProductType.ALL[i]) >= 0);
        }
    }

    // ----------------------------------------------------------- autopilot

    private static int autopilotStage = 0;
    private static ProductType autopilotProduct = ProductType.BREAD;
    private static int autopilotShelf = 0;

    /**
     * Plays the game the way a person would: order stock, carry it out, fill a
     * shelf, then stand at the till until the queue clears.
     */
    private static void runAutopilot(Shop shop) {
        GameState state = shop.state;
        Player player = shop.player;

        if (player.task != Player.Task.FREE) {
            stepIdle(shop);
            return;
        }

        switch (autopilotStage) {
            case 0: // Order whatever the shop is short of.
                if (stepToward(shop, ShopLayout.TERMINAL_STAND_X, ShopLayout.TERMINAL_STAND_Z)) {
                    autopilotProduct = neediestProduct(shop);
                    if (state.stockOf(autopilotProduct) < 25
                            && state.money > autopilotProduct.orderCost(30) * 2.2f) {
                        shop.orderStock(autopilotProduct, 30);
                    }
                    buyWhatWeCanAfford(shop);
                    autopilotStage = 1;
                }
                break;

            case 1: // Fetch a crate.
                if (stepToward(shop, ShopLayout.STOCKROOM_STAND_X, ShopLayout.STOCKROOM_STAND_Z)) {
                    autopilotShelf = shelfNeeding(shop);
                    if (autopilotShelf >= 0) {
                        Shelf shelf = shop.shelves.get(autopilotShelf);
                        ProductType want = shelf.product == null
                                ? firstUnlocked(state) : shelf.product;
                        if (shelf.product == null) shop.assignProduct(autopilotShelf, want);
                        if (state.stockOf(want) > 0) shop.collectStock(want);
                    }
                    autopilotStage = shop.player.isCarrying() ? 2 : 3;
                }
                break;

            case 2: { // Put it out.
                Shelf shelf = shop.shelf(autopilotShelf);
                if (shelf == null) { autopilotStage = 3; break; }
                if (stepToward(shop, shelf.approachX, shelf.approachZ)) {
                    if (!shop.stockShelf(autopilotShelf)) shop.returnCrate();
                    autopilotStage = 3;
                }
                break;
            }

            default: { // Work the till until the queue is empty.
                boolean atTill = stepToward(shop, ShopLayout.SERVE_X, ShopLayout.SERVE_Z);
                if (atTill && shop.interaction.kind == Interaction.Kind.SERVE
                        && shop.interaction.enabled) {
                    shop.serveCustomer();
                }
                if (atTill && shop.queueLength() == 0 && shelfNeeding(shop) >= 0) {
                    autopilotStage = 0;
                }
                break;
            }
        }
    }

    private static ProductType neediestProduct(Shop shop) {
        ProductType best = firstUnlocked(shop.state);
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (!shelf.owned || shelf.product == null) continue;
            int have = shop.state.stockOf(shelf.product);
            if (have < lowest) {
                lowest = have;
                best = shelf.product;
            }
        }
        return best;
    }

    private static ProductType firstUnlocked(GameState state) {
        for (int i = 0; i < ProductType.ALL.length; i++) {
            if (ProductType.ALL[i].isUnlocked(state.level)) return ProductType.ALL[i];
        }
        return ProductType.BREAD;
    }

    private static int shelfNeeding(Shop shop) {
        int best = -1;
        float worst = 0.55f;
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (!shelf.owned) continue;
            ProductType product = shelf.product;
            if (product != null && shop.state.stockOf(product) <= 0) continue;
            float fill = shelf.fillRatio(shop.state);
            if (fill < worst) {
                worst = fill;
                best = i;
            }
        }
        return best;
    }

    private static void buyWhatWeCanAfford(Shop shop) {
        GameState state = shop.state;
        if (state.money > 2600 && state.upgradeLevel(Upgrade.CASHIER) == 0) {
            if (state.buyUpgrade(Upgrade.CASHIER)) shop.syncStaff();
        }
        if (state.money > 3000 && state.upgradeLevel(Upgrade.STOCKER) == 0) {
            if (state.buyUpgrade(Upgrade.STOCKER)) shop.syncStaff();
        }
        if (state.money > 2200) state.buyUpgrade(Upgrade.REGISTER);
        if (state.money > 2200) state.buyUpgrade(Upgrade.MARKETING);
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (!shelf.owned && state.money > shelf.purchaseCost * 3.5f) {
                shop.buyShelf(i);
                shop.assignProduct(i, firstUnlocked(state));
                break;
            }
        }
    }

    // ------------------------------------------------------------- movement

    private static final ArrayList<float[]> pathBuffer = new ArrayList<float[]>();
    private static float[] currentGoal = null;
    private static int currentWaypoint = 0;

    /** One frame of walking toward a goal; true once the player is standing there. */
    private static boolean stepToward(Shop shop, float x, float z) {
        Player player = shop.player;
        float dx = x - player.position.x;
        float dz = z - player.position.z;
        if (dx * dx + dz * dz < 0.55f * 0.55f) {
            stepIdle(shop);
            currentGoal = null;
            return true;
        }
        if (currentGoal == null || currentGoal[0] != x || currentGoal[1] != z) {
            currentGoal = new float[]{x, z};
            shop.nav.findPath(player.position.x, player.position.z, x, z, pathBuffer);
            currentWaypoint = 0;
        }
        float targetX = x, targetZ = z;
        if (currentWaypoint < pathBuffer.size()) {
            float[] wp = pathBuffer.get(currentWaypoint);
            float wdx = wp[0] - player.position.x, wdz = wp[1] - player.position.z;
            if (wdx * wdx + wdz * wdz < 0.30f * 0.30f) currentWaypoint++;
            else { targetX = wp[0]; targetZ = wp[1]; }
        }
        float ndx = targetX - player.position.x, ndz = targetZ - player.position.z;
        float length = (float) Math.sqrt(ndx * ndx + ndz * ndz);
        if (length > 1e-4f) { ndx /= length; ndz /= length; }

        float beforeX = player.position.x, beforeZ = player.position.z;
        player.move(ndx, ndz, true, STEP, shop.collision);
        shop.update(STEP);

        // Snagged on a fitting: drop the waypoint rather than grinding on it, which
        // is what the simulated characters do too.
        float movedX = player.position.x - beforeX, movedZ = player.position.z - beforeZ;
        if (movedX * movedX + movedZ * movedZ < 1e-6f
                && currentWaypoint < pathBuffer.size()) {
            currentWaypoint++;
        }
        return false;
    }

    private static void stepIdle(Shop shop) {
        shop.player.move(0f, 0f, false, STEP, shop.collision);
        shop.update(STEP);
    }

    private static void settle(Shop shop, float seconds) {
        for (float t = 0; t < seconds; t += STEP) stepIdle(shop);
    }

    /** Walks the player to a spot, giving up after {@code maxSeconds}. */
    private static boolean walkTo(Shop shop, float x, float z, float maxSeconds) {
        currentGoal = null;
        for (float t = 0; t < maxSeconds; t += STEP) {
            if (stepToward(shop, x, z)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------- plumbing

    private static void section(String name) {
        System.out.println();
        System.out.println("== " + name);
    }

    private static void check(String description, boolean condition) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL  " + description);
        } else {
            System.out.println("  ok    " + description);
        }
    }
}
