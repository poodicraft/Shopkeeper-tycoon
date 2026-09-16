package com.poodicraft.shopkeeper.world;

/**
 * Every dimension of the shop, in metres.
 *
 * <p>Geometry, navigation and gameplay all read from here, so a fixture can never
 * end up drawn in one place and collided with in another.
 */
public final class ShopLayout {
    private ShopLayout() { }

    // Room ------------------------------------------------------------------

    public static final float HALF_WIDTH = 7.5f;
    public static final float HALF_DEPTH = 9.0f;
    public static final float CEILING_HEIGHT = 3.40f;
    public static final float WALL_THICKNESS = 0.25f;

    /** Sliding entrance in the front wall (z = +HALF_DEPTH). */
    public static final float DOOR_MIN_X = 4.30f;
    public static final float DOOR_MAX_X = 6.60f;
    public static final float DOOR_CENTER_X = (DOOR_MIN_X + DOOR_MAX_X) * 0.5f;
    public static final float DOOR_HEIGHT = 2.30f;

    // Checkout --------------------------------------------------------------

    public static final float COUNTER_MIN_X = -6.60f;
    public static final float COUNTER_MAX_X = -1.60f;
    public static final float COUNTER_MIN_Z = 5.20f;
    public static final float COUNTER_MAX_Z = 6.45f;
    public static final float COUNTER_HEIGHT = 0.96f;

    /** Where the till sits on the counter. */
    public static final float TILL_X = -2.55f;
    public static final float TILL_Z = 5.60f;

    /** Where the shopkeeper stands to serve, on the staff side of the counter. */
    public static final float SERVE_X = -2.60f;
    public static final float SERVE_Z = 4.55f;

    /** Head of the customer queue, on the shop-floor side. */
    public static final float QUEUE_HEAD_X = -1.95f;
    public static final float QUEUE_Z = 7.05f;
    public static final float QUEUE_SPACING = 0.82f;
    public static final int MAX_QUEUE = 7;

    // Stockroom -------------------------------------------------------------

    /** Hatch in the back wall where crates are collected. */
    public static final float STOCKROOM_X = -5.40f;
    public static final float STOCKROOM_Z = -HALF_DEPTH;
    /** Standing spot in front of the hatch. */
    public static final float STOCKROOM_STAND_X = -5.40f;
    public static final float STOCKROOM_STAND_Z = -7.70f;

    /** Back-office terminal used for ordering stock and buying upgrades. */
    public static final float TERMINAL_X = 3.60f;
    public static final float TERMINAL_Z = -8.35f;
    public static final float TERMINAL_STAND_X = 3.60f;
    public static final float TERMINAL_STAND_Z = -7.55f;

    // Shelving --------------------------------------------------------------

    public static final float SHELF_WIDTH = 3.00f;
    public static final float SHELF_DEPTH = 0.95f;
    public static final float SHELF_HEIGHT = 1.85f;

    /** Rows run left to right; aisles sit between them. */
    public static final float[] ROW_Z = {-6.50f, -3.50f, -0.50f, 2.50f};
    public static final float[] COLUMN_X = {-4.40f, 0.00f, 4.40f};

    public static final int SHELF_SLOTS = 12;

    /** How far in front of a shelf the player and customers stand to use it. */
    public static final float SHELF_APPROACH = 1.05f;

    public static float shelfX(int index) { return COLUMN_X[index % COLUMN_X.length]; }

    public static float shelfZ(int index) { return ROW_Z[index / COLUMN_X.length]; }

    /** Cost of bringing shelf slot {@code index} into service. */
    public static int shelfCost(int index) {
        return (int) (180 * Math.pow(1.48, index));
    }

    // Chiller ---------------------------------------------------------------

    public static final float CHILLER_X = 6.55f;
    public static final float CHILLER_Z = 1.60f;
    public static final float CHILLER_WIDTH = 1.10f;
    public static final float CHILLER_LENGTH = 3.60f;
    public static final float CHILLER_HEIGHT = 2.05f;

    // Lighting --------------------------------------------------------------

    /** Positions of the ceiling lamps, which also become point lights. */
    public static final float[][] LAMPS = {
            {-4.40f, -5.00f}, {4.40f, -5.00f},
            {-4.40f, 0.00f}, {4.40f, 0.00f},
            {0.00f, -2.50f}, {0.00f, 3.20f},
            {-4.20f, 6.00f}, {4.60f, 6.40f},
    };

    /** True when a point is inside the shop floor, allowing for the wall thickness. */
    public static boolean insideRoom(float x, float z, float margin) {
        return x > -HALF_WIDTH + margin && x < HALF_WIDTH - margin
                && z > -HALF_DEPTH + margin && z < HALF_DEPTH - margin;
    }
}
