package com.poodicraft.shopkeeper.art;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.poodicraft.shopkeeper.game.ProductType;

/**
 * Prints product names onto the packaging layers.
 *
 * <p>{@link TextureFactory} generates the blank label - paper grain, a colour band
 * and a barcode - with nothing but arithmetic. Real lettering needs a font, so it
 * is painted on afterwards with {@link Canvas}, which is the one part of the art
 * pipeline that touches an Android type.
 */
public final class LabelPainter {
    private LabelPainter() { }

    /**
     * Overlays text on each of the label layers, in place.
     *
     * <p>Safe to call from the asset worker thread; {@link Bitmap} and {@link Canvas}
     * are not tied to the UI thread.
     */
    public static void paintLabels(int[][] albedo) {
        int size = TextureFactory.SIZE;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);

        for (int layer = 0; layer < Materials.LABEL_COUNT; layer++) {
            int index = Materials.LABEL_BASE + layer;
            if (index >= albedo.length || albedo[index] == null) continue;

            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(albedo[index], 0, size, 0, 0, size, size);
            Canvas canvas = new Canvas(bitmap);

            // Each layer serves two product tiers, so pair up the names.
            String primary = nameForLayer(layer, 0);
            String secondary = nameForLayer(layer, 1);

            paint.setColor(Color.argb(255, 250, 248, 242));
            paint.setTextSize(size * 0.125f);
            canvas.drawText(primary.toUpperCase(java.util.Locale.US), size * 0.5f, size * 0.50f, paint);

            paint.setColor(Color.argb(210, 52, 62, 70));
            paint.setTextSize(size * 0.062f);
            canvas.drawText(secondary, size * 0.5f, size * 0.66f, paint);

            paint.setColor(Color.argb(170, 60, 72, 80));
            paint.setTextSize(size * 0.045f);
            canvas.drawText("NET 500g", size * 0.5f, size * 0.245f, paint);

            bitmap.getPixels(albedo[index], 0, size, 0, 0, size, size);
            bitmap.recycle();
        }
    }

    private static String nameForLayer(int layer, int offset) {
        int ordinal = layer + offset * Materials.LABEL_COUNT;
        if (ordinal < ProductType.ALL.length) return ProductType.ALL[ordinal].displayName;
        return "Own Brand";
    }
}
