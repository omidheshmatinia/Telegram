package org.telegram.ui.screens.profile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.ProfileGiftsView;
import org.telegram.ui.Stories.ProfileStoriesView;

import java.util.ArrayList;

public class ProfileToolbarHelper {
    public static int MAX_PROFILE_IMAGE_CIRCLE_SIZE = AndroidUtilities.dp(84);
    public static int MIN_PROFILE_IMAGE_CIRCLE_SIZE = AndroidUtilities.dp(42);
    public static int FIRST_EXPANSION_HEIGHT_THRESH_HOLD = AndroidUtilities.dp(170f);
    public static final float TOOLBAR_TEXT_INITIAL_START_MARGIN = 64;

    private TextLayoutUpdateCallback textLayoutUpdateCallback;
    private ProfileStoriesView storyView;
    private ProfileGiftsView giftsView;
    private FrameLayout avatarContainer;
    private FrameLayout avatarContainer2;
    private ProfileToolbarButtonsRowLayout toolbarButtonsLayout;
    private SimpleTextView[] nameTextView = new SimpleTextView[2];
    private SimpleTextView[] onlineTextView = new SimpleTextView[4];

    private final int screenWidth = AndroidUtilities.displaySize.x;
    public boolean isVideoCallItemVisible = false;
    public boolean isCallItemVisible = false;

    public void checkVideoCallAndCallVisibility(TLRPC.UserFull userInfo) {
        if (userInfo != null && userInfo.phone_calls_available) {
            isCallItemVisible = true;
            isVideoCallItemVisible = userInfo.video_calls_available;
        }
    }

    public void handleExpansionInFirstStage(
            ImageView timeItem,
            ImageView starBgItem,
            ImageView starFgItem,
            ProfileActivity.ShowDrawable showStatusButton,
            AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterTextView,
            float diff,
            ValueAnimator expandAnimator,
            ActionBar actionBar
    ) {
        float nameX = 0;
        float onlineX = 0;
        if (storyView != null) {
            storyView.invalidate();
        }
        if (giftsView != null) {
            giftsView.invalidate();
        }
        float avatarScale = (float) AndroidUtilities.lerp(MIN_PROFILE_IMAGE_CIRCLE_SIZE, MAX_PROFILE_IMAGE_CIRCLE_SIZE, diff) / MIN_PROFILE_IMAGE_CIRCLE_SIZE;
        float imageSize = (MIN_PROFILE_IMAGE_CIRCLE_SIZE * avatarScale);
        float avatarYExpandedHeight = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2.0f - (MIN_PROFILE_IMAGE_CIRCLE_SIZE / 2) + actionBar.getTranslationY();
        float avatarY = AndroidUtilities.lerp(-MIN_PROFILE_IMAGE_CIRCLE_SIZE, avatarYExpandedHeight + (MIN_PROFILE_IMAGE_CIRCLE_SIZE / 2), diff);
        float nameScale = 1.0f + 0.12f * diff;
        if (expandAnimator == null || !expandAnimator.isRunning()) {
            avatarContainer.setScaleX(avatarScale);
            avatarContainer.setScaleY(avatarScale);
            avatarContainer.setTranslationY((float) Math.ceil(avatarY));
            float extra = imageSize - MIN_PROFILE_IMAGE_CIRCLE_SIZE;
            timeItem.setTranslationX(avatarContainer.getX() + AndroidUtilities.dp(16) + extra);
            timeItem.setTranslationY(avatarContainer.getY() + AndroidUtilities.dp(15) + extra);
            starBgItem.setTranslationX(avatarContainer.getX() + AndroidUtilities.dp(28) + extra);
            starBgItem.setTranslationY(avatarContainer.getY() + AndroidUtilities.dp(24) + extra);
            starFgItem.setTranslationX(avatarContainer.getX() + AndroidUtilities.dp(28) + extra);
            starFgItem.setTranslationY(avatarContainer.getY() + AndroidUtilities.dp(24) + extra);
        }
        float nameY = AndroidUtilities.lerp(avatarYExpandedHeight, avatarYExpandedHeight + imageSize + AndroidUtilities.dp(10), diff);
        float onlineY = nameY + AndroidUtilities.dp(26.7f); // todo: can improve by reading from a unified function
        if (showStatusButton != null) {
            showStatusButton.setAlpha((int) (0xFF * diff));
        }
        for (int a = 0; a < nameTextView.length; a++) {
            if (nameTextView[a] == null) {
                continue;
            }
            if (expandAnimator == null || !expandAnimator.isRunning()) {
                // when user click on profile icon, it expand to full state directly and the widths are still zero
                if (nameTextView[a].getTextWidth() == 0) {
                    nameX = AndroidUtilities.dp(-48);
                    nameTextView[a].setX(AndroidUtilities.dp(16 - TOOLBAR_TEXT_INITIAL_START_MARGIN));
                } else {
                    nameX = diff * ((screenWidth - nameTextView[a].getTextWidth()) / 2 - AndroidUtilities.dp(TOOLBAR_TEXT_INITIAL_START_MARGIN));
                }
                nameTextView[a].setTranslationX(nameX);
                nameTextView[a].setTranslationY(nameY);

                if (onlineTextView[a].getTextWidth() == 0) {
                    onlineX = AndroidUtilities.dp(16 - TOOLBAR_TEXT_INITIAL_START_MARGIN);
                    onlineTextView[a].setX(AndroidUtilities.dp(0));
                } else {
                    onlineX = diff * ((screenWidth - onlineTextView[a].getTextWidth()) / 2 - AndroidUtilities.dp(TOOLBAR_TEXT_INITIAL_START_MARGIN));
                }
                onlineTextView[a].setTranslationX(onlineX);
                onlineTextView[a].setTranslationY(onlineY);
                if (a == 1) {
                    mediaCounterTextView.setTranslationX(onlineX);
                    mediaCounterTextView.setTranslationY(onlineY);
                }
            }
            nameTextView[a].setScaleX(nameScale);
            nameTextView[a].setScaleY(nameScale);
        }
        if(toolbarButtonsLayout != null){
            toolbarButtonsLayout.setY(AndroidUtilities.dp(100));
            toolbarButtonsLayout.handleAnimation(diff);
            Log.e("OMIDOMID","toolbarButtonsLayout > diff="+diff+"  height="+toolbarButtonsLayout.getHeight()+"  alpha="+toolbarButtonsLayout.getAlpha()+"   transY="+toolbarButtonsLayout.getTranslationY()+"    y="+toolbarButtonsLayout.getY());
        }
        if (textLayoutUpdateCallback != null) {
            textLayoutUpdateCallback.onTextPositionUpdate(nameX, nameY, onlineX, onlineY);
            textLayoutUpdateCallback.onAvatarScaleUpdate(avatarScale, avatarY);
        }
    }

    public void setTextLayoutUpdateCallback(TextLayoutUpdateCallback callback) {
        textLayoutUpdateCallback = callback;
    }

    public void setReferences(SimpleTextView[] _nameTextView, SimpleTextView[] _onlineTextView) {
        nameTextView = _nameTextView;
        onlineTextView = _onlineTextView;
    }

    public void setStoryView(ProfileStoriesView _storyView) {
        storyView = _storyView;
    }

    public void setGiftsView(ProfileGiftsView _giftsView) {
        giftsView = _giftsView;
    }

    public void setContainerReferences(FrameLayout _avatarContainer, FrameLayout _avatarContainer2) {
        avatarContainer = _avatarContainer;
        avatarContainer2 = _avatarContainer2;
    }

    public void setupToolbarButtons(Context context, Theme.ResourcesProvider resourcesProvider, SizeNotifierFrameLayout masterView){
        toolbarButtonsLayout = new ProfileToolbarButtonsRowLayout(context, masterView ,resourcesProvider);
        masterView.addView(toolbarButtonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        ArrayList<ProfileToolbarButtonItem> items = new ArrayList<>();
        items.add(new ProfileToolbarButtonItem(R.drawable.filled_message, "Message"));
        items.add(new ProfileToolbarButtonItem(R.drawable.filled_unmute, "Unmute"));
        items.add(new ProfileToolbarButtonItem(R.drawable.filled_call, "Call"));
        items.add(new ProfileToolbarButtonItem(R.drawable.filled_video, "Video"));
        toolbarButtonsLayout.setItems(items);
        toolbarButtonsLayout.setAlpha(0f);
        masterView.blurBehindViews.add(toolbarButtonsLayout);
    }

    public interface TextLayoutUpdateCallback {
        void onTextPositionUpdate(float nameX, float nameY, float onlineX, float onlineY);

        void onAvatarScaleUpdate(float avatarScale, float avatarY);
    }
}
