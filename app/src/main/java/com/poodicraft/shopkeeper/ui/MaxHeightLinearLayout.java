package com.poodicraft.shopkeeper.ui;

import android.content.Context;
import android.widget.LinearLayout;

/** LinearLayout that refuses to grow past a pixel ceiling, used for the bottom sheet. */
public final class MaxHeightLinearLayout extends LinearLayout {
    private int maxHeightPx = 0;

    public MaxHeightLinearLayout(Context context) {
        super(context);
    }

    public void setMaxHeightPx(int px) {
        if (maxHeightPx != px) {
            maxHeightPx = px;
            requestLayout();
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (maxHeightPx > 0) {
            int mode = MeasureSpec.getMode(heightSpec);
            int size = MeasureSpec.getSize(heightSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > maxHeightPx) {
                heightSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
