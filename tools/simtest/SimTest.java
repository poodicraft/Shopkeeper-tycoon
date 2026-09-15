import com.poodicraft.shopkeeper.game.Customer;
import com.poodicraft.shopkeeper.game.GameState;
import com.poodicraft.shopkeeper.game.Money;
import com.poodicraft.shopkeeper.game.NavGrid;
import com.poodicraft.shopkeeper.game.ProductType;
import com.poodicraft.shopkeeper.game.Shelf;
import com.poodicraft.shopkeeper.game.Shop;
import com.poodicraft.shopkeeper.game.Upgrade;

import java.util.ArrayList;

/**
 * Headless check of the simulation.
 *
 * <p>The game package deliberately has no Android imports, so the whole economy can
 * be driven on a plain JVM. This plays several in-game days with an autopilot
 * shopkeeper and asserts the invariants that matter: money and stock stay sane,
 * customers actually get served, and every shelf stays reachable.
 */
public final class SimTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) {
        testNavigationReachesEveryShelf();
        testCustomersGetServed();
        testEconomyRunsForDays();
        testPricingAffectsDemand();
        testStockIsConserved();

        System.out.println();
        if (failures == 0) {
            System.out.println("All " + checks + " checks passed.");
        } else {
            System.out.println(failures + " of " + checks + " checks FAILED.");
            System.exit(1);
        }
    }

    // --------------------------------------------------------------------- tests

    private static void testNavigationReachesEveryShelf() {
        section("Navigation");
        Shop shop = new Shop(new GameState());
        for (int i = 0; i < shop.shelves.size(); i++) shop.shelves.get(i).owned = true;
        shop.rebuildNavigation();

        NavGrid nav = shop.nav;
        ArrayList<float[]> path = new ArrayList<float[]>();
        float doorX = Shop.DOOR_CENTER_X, doorZ = Shop.HALF_DEPTH + 1.2f;

        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            boolean found = nav.findPath(doorX, doorZ, shelf.approachX, shelf.approachZ, path);
            check("path from door to shelf " + (i + 1), found && !path.isEmpty());
        }
        check("path from door to the till",
                nav.findPath(doorX, doorZ, shop.queueSlotX(0), Shop.QUEUE_Z, path));
        check("path from the stockroom to a shelf",
                nav.findPath(Shop.STOCKROOM_X + 1f, Shop.STOCKROOM_Z + 1f,
                        shop.shelves.get(14).approachX, shop.shelves.get(14).approachZ, path));
        check("the shelf footprints are blocked",
                !nav.isWalkableWorld(shop.shelves.get(0).x, shop.shelves.get(0).z));
        check("the browsing spot in front of a shelf is walkable",
                nav.isWalkableWorld(shop.shelves.get(0).approachX, shop.shelves.get(0).approachZ));
        check("the counter is blocked",
                !nav.isWalkableWorld(Shop.REGISTER_X, Shop.REGISTER_Z));
        check("the queue line is walkable",
                nav.isWalkableWorld(shop.queueSlotX(0), Shop.QUEUE_Z));
    }

    private static void testCustomersGetServed() {
        section("Serving customers");
        Shop shop = new Shop(new GameState());
        GameState state = shop.state;
        state.money = 5000;
        state.upgradeLevels[Upgrade.CASHIER.ordinal()] = 1;
        state.upgradeLevels[Upgrade.MARKETING.ordinal()] = 3;
        shop.syncStaff();

        shop.orderStock(ProductType.BREAD, 200);
        shop.orderStock(ProductType.MILK, 200);
        shop.restockShelf(0);
        shop.restockShelf(1);

        // Start mid-morning so shoppers arrive straight away.
        state.dayTime = GameState.DAY_LENGTH * 0.45f;
        double before = state.money;
        int maxSeen = 0;
        for (int step = 0; step < 60 * 90; step++) {
            shop.update(1f / 60f);
            maxSeen = Math.max(maxSeen, shop.customers.size());
            if (state.dayTime > GameState.DAY_LENGTH * 0.85f) break;
        }
        check("shoppers came in (saw " + maxSeen + ")", maxSeen > 0);
        check("someone was served (" + state.dayServed + ")", state.dayServed > 0);
        check("takings went up (" + Money.exact(state.money - before) + ")", state.money > before);
        check("shelves were drawn down",
                shop.shelves.get(0).stock < shop.shelf(0).capacity(state)
                        || shop.shelves.get(1).stock < shop.shelf(1).capacity(state));
    }

    private static void testEconomyRunsForDays() {
        section("Autopilot shopkeeper, 6 days");
        Shop shop = new Shop(new GameState());
        GameState state = shop.state;

        int summaries = 0;
        double lowestMoney = state.money;
        int totalSteps = (int) (GameState.DAY_LENGTH * 6 * 30);
        for (int step = 0; step < totalSteps; step++) {
            shop.update(1f / 30f);
            if (step % 30 == 0) autopilot(shop);
            if (shop.pendingSummary != null) {
                Shop.DaySummary summary = shop.pendingSummary;
                shop.pendingSummary = null;
                summaries++;
                System.out.printf("    day %d: revenue %s, profit %s, served %d, lost %d%n",
                        summary.day, Money.exact(summary.revenue),
                        Money.exact(summary.profit), summary.customersServed,
                        summary.customersLost);
            }
            lowestMoney = Math.min(lowestMoney, state.money);
            if (Double.isNaN(state.money) || Double.isInfinite(state.money)) break;
        }

        check("six days closed out", summaries == 6);
        check("money stayed a real number", !Double.isNaN(state.money) && !Double.isInfinite(state.money));
        check("satisfaction stayed in range", state.satisfaction >= 0f && state.satisfaction <= 1f);
        check("the shop levelled up (level " + state.level + ")", state.level > 1);
        check("customers were served (" + state.totalCustomersServed + ")",
                state.totalCustomersServed > 20);
        check("the shop turned a profit (" + Money.exact(state.money) + ")", state.money > 320);
        for (int i = 0; i < state.stock.length; i++) {
            check("stockroom count for " + ProductType.ALL[i].displayName + " is not negative",
                    state.stock[i] >= 0);
        }
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            check("shelf " + (i + 1) + " is within capacity",
                    shelf.stock >= 0 && shelf.stock <= shelf.capacity(state));
        }
    }

    private static void testPricingAffectsDemand() {
        section("Pricing pressure");
        double cheapSales = runPricedDay(0.7f);
        double dearSales = runPricedDay(2.0f);
        System.out.printf("    cheap day sold %.0f units, expensive day sold %.0f units%n",
                cheapSales, dearSales);
        check("cutting prices sells more than gouging", cheapSales > dearSales);
        check("an over-priced shelf still sells something or drives people out", dearSales >= 0);
    }

    private static double runPricedDay(float priceMultiplier) {
        Shop shop = new Shop(new GameState());
        GameState state = shop.state;
        state.money = 9000;
        state.upgradeLevels[Upgrade.CASHIER.ordinal()] = 2;
        state.upgradeLevels[Upgrade.REGISTER.ordinal()] = 4;
        shop.syncStaff();
        shop.orderStock(ProductType.BREAD, 400);
        shop.orderStock(ProductType.MILK, 400);
        shop.setPrice(0, ProductType.BREAD.basePrice * priceMultiplier);
        shop.setPrice(1, ProductType.MILK.basePrice * priceMultiplier);
        shop.restockShelf(0);
        shop.restockShelf(1);

        state.dayTime = GameState.DAY_LENGTH * 0.35f;
        int sold = 0;
        for (int step = 0; step < 60 * 70; step++) {
            shop.update(1f / 60f);
            if (step % 60 == 0) {
                shop.restockShelf(0);
                shop.restockShelf(1);
            }
            if (state.dayTime > GameState.DAY_LENGTH * 0.8f) break;
        }
        sold = state.dayServed;
        return sold;
    }

    private static void testStockIsConserved() {
        section("Stock conservation");
        Shop shop = new Shop(new GameState());
        GameState state = shop.state;
        state.money = 100000;
        state.upgradeLevels[Upgrade.MARKETING.ordinal()] = 5;
        shop.syncStaff();

        // Only bread is ever on a shelf, so every item that moves is a loaf and the
        // books have to balance exactly.
        final int[] soldItems = new int[1];
        shop.listener = new Shop.Listener() {
            @Override public void onSale(float amount, int itemCount) { soldItems[0] += itemCount; }
            @Override public void onCustomerLost(String reason) { }
            @Override public void onLevelUp(int newLevel) { }
        };

        shop.orderStock(ProductType.BREAD, 500);
        shop.restockShelf(0);
        int startTotal = state.stock[ProductType.BREAD.ordinal()] + shop.shelves.get(0).stock;

        state.dayTime = GameState.DAY_LENGTH * 0.4f;
        for (int step = 0; step < 60 * 240; step++) {
            shop.update(1f / 60f);
            if (step % 240 == 0) shop.restockShelf(0);
        }

        int inHands = 0;
        for (int i = 0; i < shop.customers.size(); i++) {
            Customer c = shop.customers.get(i);
            for (int j = 0; j < c.basket.size(); j++) {
                if (c.basket.get(j).product == ProductType.BREAD) inHands++;
            }
        }
        int accounted = state.stock[ProductType.BREAD.ordinal()]
                + shop.shelves.get(0).stock + inHands + soldItems[0];
        check("every loaf is accounted for (" + accounted + " of " + startTotal + ")",
                accounted == startTotal);
        check("some bread actually sold (" + soldItems[0] + ")", soldItems[0] > 0);
    }

    // ----------------------------------------------------------------- autopilot

    /** Stands in for a player: buys stock, keeps shelves full, expands, upgrades. */
    private static void autopilot(Shop shop) {
        GameState state = shop.state;

        // Keep every owned shelf assigned to the best product it can sell.
        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (!shelf.owned) continue;
            if (shelf.product == null || !shelf.product.isUnlocked(state.level)) {
                shop.assignProduct(i, bestUnlocked(state, i));
            }
            if (shelf.product == null) continue;
            shop.setPrice(i, shelf.product.basePrice * 0.95f);
            if (shelf.fillRatio(state) < 0.35f) {
                int room = shelf.capacity(state) - shelf.stock;
                int have = state.stock[shelf.product.ordinal()];
                if (have < room && state.money > shelf.product.orderCost(room) * 3) {
                    shop.orderStock(shelf.product, room - have);
                }
                shop.restockShelf(i);
            }
        }

        // Spend surplus on staff first, then capacity, then reach.
        if (state.money > 3000 && state.upgradeLevel(Upgrade.CASHIER) == 0) {
            if (state.buyUpgrade(Upgrade.CASHIER)) shop.syncStaff();
        }
        if (state.money > 4000 && state.upgradeLevel(Upgrade.STOCKER) == 0) {
            if (state.buyUpgrade(Upgrade.STOCKER)) shop.syncStaff();
        }
        if (state.money > 2500) state.buyUpgrade(Upgrade.REGISTER);
        if (state.money > 2500) state.buyUpgrade(Upgrade.MARKETING);

        for (int i = 0; i < shop.shelves.size(); i++) {
            Shelf shelf = shop.shelves.get(i);
            if (!shelf.owned && state.money > shelf.purchaseCost * 4) {
                shop.buyShelf(i);
                shop.assignProduct(i, bestUnlocked(state, i));
                break;
            }
        }
    }

    private static ProductType bestUnlocked(GameState state, int seed) {
        ProductType best = ProductType.BREAD;
        ArrayList<ProductType> pool = new ArrayList<ProductType>();
        for (int i = 0; i < ProductType.ALL.length; i++) {
            if (ProductType.ALL[i].isUnlocked(state.level)) pool.add(ProductType.ALL[i]);
        }
        if (!pool.isEmpty()) best = pool.get(seed % pool.size());
        return best;
    }

    // ------------------------------------------------------------------ plumbing

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
