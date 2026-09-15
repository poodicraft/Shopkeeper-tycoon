package com.poodicraft.shopkeeper.scene;

/** Colours for the 3D shop, kept in one place so the look stays coherent. */
public final class Palette {
    private Palette() { }

    public static final int FLOOR_LIGHT   = 0xE9E2D4;
    public static final int FLOOR_DARK    = 0xD3C9B6;
    public static final int FLOOR_BORDER  = 0x9C8A70;
    public static final int RUG           = 0x2E6F63;
    public static final int RUG_EDGE      = 0x255B52;

    public static final int WALL          = 0xF3EDE1;
    public static final int WALL_TOP      = 0xE3DACB;
    public static final int WAINSCOT      = 0x8A6444;
    public static final int SKIRTING      = 0x6D4C32;

    public static final int WOOD_LIGHT    = 0xC08B54;
    public static final int WOOD          = 0xA3703F;
    public static final int WOOD_DARK     = 0x7B5330;
    public static final int METAL         = 0xB4BCC4;
    public static final int METAL_DARK    = 0x79838D;

    public static final int COUNTER_BODY  = 0x8A5A38;
    public static final int COUNTER_TOP   = 0x4A5561;
    public static final int REGISTER_BODY = 0xE7E9EC;
    public static final int REGISTER_DARK = 0x39414B;
    public static final int SCREEN        = 0x59D2B0;

    public static final int ACCENT        = 0x2E8B6F;
    public static final int ACCENT_WARM   = 0xE9A13B;
    public static final int SIGN_RED      = 0xC8503F;

    public static final int PLANT         = 0x3F8C57;
    public static final int PLANT_DARK    = 0x2F6B41;
    public static final int POT           = 0xC1663B;

    public static final int GLASS         = 0xBFE3F0;
    public static final int LAMP_SHADE    = 0xF2E6C8;
    public static final int LAMP_GLOW     = 0xFFE9A8;

    public static final int SHADOW        = 0x14100C;
    public static final int GHOST         = 0x7FD6C2;

    public static float red(int c) { return ((c >> 16) & 0xFF) / 255f; }

    public static float green(int c) { return ((c >> 8) & 0xFF) / 255f; }

    public static float blue(int c) { return (c & 0xFF) / 255f; }
}
