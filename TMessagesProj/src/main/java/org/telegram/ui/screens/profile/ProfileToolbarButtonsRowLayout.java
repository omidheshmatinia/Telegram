package org.telegram.ui.screens.profile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.List;

@SuppressLint("ViewConstructor")
public class ProfileToolbarButtonsRowLayout extends LinearLayout {
    private SizeNotifierFrameLayout parentNotifier;
    private Theme.ResourcesProvider resourceProvider;
    private final int HORIZONTAL_SPACING = 12;
    private final int MIDDLE_SPACE = 6;
    private static final int BUTTON_HEIGHT = AndroidUtilities.dp(64);
    private static final int VERTICAL_SPACING = AndroidUtilities.dp(12);
    public static final int FULL_HEIGHT = BUTTON_HEIGHT + VERTICAL_SPACING * 2;
    private final Paint backgroundPaint = new Paint();
    private final Rect blurBounds = new Rect();

    public ProfileToolbarButtonsRowLayout(Context context, SizeNotifierFrameLayout _parentNotifier, Theme.ResourcesProvider _resourcesProvider) {
        super(context);
        parentNotifier = _parentNotifier;
        resourceProvider = _resourcesProvider;
        setOrientation(HORIZONTAL);
        setClipToPadding(false);
        setClipChildren(false);
    }

    public void setItems(List<ProfileToolbarButtonItem> items) {
        removeAllViews();
        int count = items.size();
        createSpace(HORIZONTAL_SPACING);
        for (int i = 0; i < count; i++) {
            ProfileToolbarButtonLayout childLayout = new ProfileToolbarButtonLayout(getContext(), parentNotifier, items.get(i));
            LinearLayout.LayoutParams lp = LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f);
            if (i != 0) {
                createSpace(MIDDLE_SPACE);
            }
            lp.bottomMargin = VERTICAL_SPACING;
            lp.topMargin = VERTICAL_SPACING;
            addView(childLayout, lp);
        }
        createSpace(HORIZONTAL_SPACING);
    }

    private void createSpace(int space) {
        View view = new View(getContext());
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(space, LayoutHelper.MATCH_PARENT);
        view.setLayoutParams(lp);
        addView(view, lp);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        blurBounds.set(0, 0, getWidth(), getHeight());
        parentNotifier.drawBlurRect(canvas, 0, blurBounds, backgroundPaint, true);
    }

    public void handleAnimation(float progress, float toolbarHeight, float threshold) {
        final float threshHoldGap = threshold + 0.03f; //text should start earlier before this scale.   after this thresh hold, it should not scale anymore or alpha change
        float mapped = progress / threshHoldGap;
        mapped = Math.max(0f, Math.min(1f, mapped));
        float diff = AndroidUtilities.lerp(0f, 1f, mapped);

        setScaleY(diff);
        float scaledHeight = FULL_HEIGHT * diff;
        setTranslationY(toolbarHeight + (FULL_HEIGHT - scaledHeight) / 2);
        setAlpha(diff);

        //todo OMID : child animation can be better. it is better that we change the height of bigger button and jsut scale the childs. now the childs skewed
        scaleChildren(diff);
    }

    /**
     * handle scale animation for each child button
     * @param progress (between 0 and 1)
     */
    private void scaleChildren(float progress) {
       int childCount = getChildCount();
        for (int a = 0; a < childCount; a++) {
            View child = getChildAt(a);
            if(child instanceof ProfileToolbarButtonLayout){
                ((ProfileToolbarButtonLayout) child).handleAnimation(progress);
            }
        }
    }
}