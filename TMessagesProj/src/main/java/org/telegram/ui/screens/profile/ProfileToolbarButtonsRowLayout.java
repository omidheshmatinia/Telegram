package org.telegram.ui.screens.profile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
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
    private final ToolbarButtonClickCallback buttonClickListener;
    private final List<ProfileToolbarButtonItem> childButtons = new ArrayList<>();

    public ProfileToolbarButtonsRowLayout(Context context, SizeNotifierFrameLayout _parentNotifier, Theme.ResourcesProvider _resourcesProvider, ToolbarButtonClickCallback buttonClickListener) {
        super(context);
        this.buttonClickListener = buttonClickListener;
        parentNotifier = _parentNotifier;
        resourceProvider = _resourcesProvider;
        setOrientation(HORIZONTAL);
        setClipToPadding(false);
        setClipChildren(false);
    }

    public void setItems(List<ProfileToolbarButtonItem> items) {
        removeAllViews();
        childButtons.clear();
        childButtons.addAll(items);
        int count = items.size();
        createSpace(HORIZONTAL_SPACING);
        for (int i = 0; i < count; i++) {
            ProfileToolbarButtonItem item = items.get(i);
            ProfileToolbarButtonLayout childLayout = new ProfileToolbarButtonLayout(getContext(), parentNotifier, item);
            LinearLayout.LayoutParams lp = LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f);
            if (i != 0) {
                createSpace(MIDDLE_SPACE);
            }
            lp.bottomMargin = VERTICAL_SPACING;
            lp.topMargin = VERTICAL_SPACING;
            childLayout.setTag(item);
            childLayout.setClickable(true);
            childLayout.setOnClickListener(view -> {
                buttonClickListener.onItemClicked(item);
            });
            addView(childLayout, lp);
        }
        createSpace(HORIZONTAL_SPACING);
    }

    public int[] getMuteUnMuteCoordinates(){
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            Object tag = child.getTag();
            if (tag == ProfileToolbarButtonItem.Mute || tag == ProfileToolbarButtonItem.UnMute) {
                return new int[]{(int) child.getX() - AndroidUtilities.dp(5), (int) (child.getY() + getY()) - AndroidUtilities.dp(5)};
            }
        }
        return null;
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

    public void updateMuteUnMuteButton(boolean isMuteMode) {
        ProfileToolbarButtonItem newItem = isMuteMode ? ProfileToolbarButtonItem.Mute : ProfileToolbarButtonItem.UnMute;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            Object tag = child.getTag();
            if (tag == ProfileToolbarButtonItem.Mute || tag == ProfileToolbarButtonItem.UnMute) {
                // Remove the old view
                removeViewAt(i);

                // Create the new button
                ProfileToolbarButtonLayout newButton = new ProfileToolbarButtonLayout(getContext(), parentNotifier, newItem);
                newButton.setTag(newItem);
                newButton.setClickable(true);
                newButton.setOnClickListener(view -> buttonClickListener.onItemClicked(newItem));

                // Use the same layout params as the old one
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                addView(newButton, i, lp);

                // Update childButtons list
                int btnIndex = childButtons.indexOf(tag);
                if (btnIndex != -1) {
                    childButtons.set(btnIndex, newItem);
                }

                break;
            }
        }
    }


    public interface ToolbarButtonClickCallback {
        void onItemClicked(ProfileToolbarButtonItem item);
    }
}