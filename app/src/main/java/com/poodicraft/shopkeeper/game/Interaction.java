package com.poodicraft.shopkeeper.game;

/**
 * What the player can do where they are standing.
 *
 * <p>Recomputed every frame from proximity, and drives both the action button's
 * label and the prompt that floats over the thing in the world.
 */
public final class Interaction {

    public enum Kind {
        NONE,
        /** At the stockroom hatch, with stock waiting to be carried out. */
        COLLECT_STOCK,
        /** At a shelf, holding a crate that belongs on it. */
        STOCK_SHELF,
        /** Behind the till with someone waiting. */
        SERVE,
        /** At an empty slot that can be bought. */
        BUY_SHELF,
        /** At the back-office desk. */
        TERMINAL,
        /** At an owned shelf, to change its product or price. */
        EDIT_SHELF,
        /** Holding a crate with nowhere sensible to put it. */
        RETURN_CRATE,
    }

    public Kind kind = Kind.NONE;
    public int shelfIndex = -1;
    /** Button text, e.g. "Stock shelf". */
    public String label = "";
    /** Secondary line, e.g. "12 x Bread". */
    public String detail = null;
    /** False when the action is visible but not currently possible. */
    public boolean enabled = false;
    /** World anchor for the floating prompt. */
    public float anchorX, anchorY, anchorZ;

    public void clear() {
        kind = Kind.NONE;
        shelfIndex = -1;
        label = "";
        detail = null;
        enabled = false;
    }

    public void set(Kind kind, String label, String detail, boolean enabled,
                    float x, float y, float z) {
        this.kind = kind;
        this.label = label;
        this.detail = detail;
        this.enabled = enabled;
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
    }

    public boolean isAvailable() { return kind != Kind.NONE; }
}
