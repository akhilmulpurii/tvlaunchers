package com.akhilmulpuri.zunesthetic;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;

/**
 * Vertical list that keeps the focused item at a fixed height (the anchor) and glides under it,
 * like the Zune menu. The stock "scroll just enough to show focus" behaviour is turned off.
 */
public class AnchorScrollView extends ScrollView {
    private float anchor = 0.36f;   // fraction of the height where the focused item's top sits
    private ObjectAnimator glide;

    public AnchorScrollView(Context c, AttributeSet a) {
        super(c, a);
    }

    int anchorY() {
        return Math.round(getHeight() * anchor);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // Room above and below so the first and last items can reach the anchor.
        setPadding(getPaddingLeft(), anchorY(), getPaddingRight(), h - anchorY());
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }

    /** Glides so {@code item} (a direct child of the list) sits at the anchor. */
    void glideTo(View item, boolean animate) {
        int target = item.getTop();
        if (glide != null) glide.cancel();
        if (!animate) {
            scrollTo(0, target);
            return;
        }
        glide = ObjectAnimator.ofInt(this, "scrollY", getScrollY(), target);
        glide.setDuration(280);
        glide.setInterpolator(new DecelerateInterpolator(1.6f));
        glide.start();
    }
}
