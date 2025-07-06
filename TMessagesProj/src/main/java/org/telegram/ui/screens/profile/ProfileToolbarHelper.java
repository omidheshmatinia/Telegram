package org.telegram.ui.screens.profile;

import static org.telegram.ui.ProfileActivity.add_photo;
import static org.telegram.ui.ProfileActivity.delete_avatar;
import static org.telegram.ui.ProfileActivity.edit_avatar;
import static org.telegram.ui.ProfileActivity.gallery_menu_save;
import static org.telegram.ui.ProfileActivity.logout;
import static org.telegram.ui.ProfileActivity.set_as_main;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NestedSizeNotifierLayout;
import org.telegram.ui.Components.ProfileGalleryView;
import org.telegram.ui.Components.Rect;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScamDrawable;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.VectorAvatarThumbDrawable;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.ProfileGiftsView;
import org.telegram.ui.Stories.ProfileStoriesView;

import java.util.ArrayList;

public class ProfileToolbarHelper {
    public static final int MAX_PROFILE_IMAGE_CIRCLE_SIZE = AndroidUtilities.dp(84);
    public static final int MIN_PROFILE_IMAGE_CIRCLE_SIZE = AndroidUtilities.dp(42);
    public static final int FIRST_EXPANSION_HEIGHT_THRESH_HOLD = AndroidUtilities.dp(180f);
    public static final int GAP_BETWEEN_NAME_AND_ONLINE_TEXT = AndroidUtilities.dp(26.7f);
    public static final float TOOLBAR_TEXT_INITIAL_START_MARGIN = 64;
    public static final float NAME_SCALE_FIRST_EXPANSION = 1.12f;
    public static final float NAME_SCALE_FULL_EXPANSION = 1.67f;

    public static final float THRESH_HOLD_FOR_AUTO_EXPAND = 0.25f;
    public static final float THRESH_HOLD_FOR_AUTO_COLLAPSE = 0.75f;

    private ToolbarLayoutUpdateCallback toolbarLayoutUpdateCallback;
    private ProfileToolbarButtonsRowLayout toolbarButtonsLayout;
    private SimpleTextView[] nameTextView = new SimpleTextView[2];
    private SimpleTextView[] onlineTextView = new SimpleTextView[4];

    private float nameX = 0f;
    private float nameY = 0f;
    private float onlineX = 0f;
    private float onlineY = 0f;
    private float avatarY = 0f;
    private final int screenWidth = AndroidUtilities.displaySize.x;
    public boolean isVideoCallItemVisible = false;
    public boolean isCallItemVisible = false;

    private final ProfileActivityReferenceCallback referenceCallback;

    public ProfileToolbarHelper(ProfileActivityReferenceCallback callback) {
        this.referenceCallback = callback;
    }

    public void checkVideoCallAndCallVisibility(TLRPC.UserFull userInfo) {
        if (userInfo != null && userInfo.phone_calls_available) {
            isCallItemVisible = true;
            isVideoCallItemVisible = userInfo.video_calls_available;
        }
    }

    public void setupGifts(){
        final Rect avatarRect = new Rect(0, 0, screenWidth , getNameEndYForPhase1());
        referenceCallback.getGiftsView().update();
        referenceCallback.getGiftsView().setGiftsViewBounds(avatarRect, screenWidth / 2, MAX_PROFILE_IMAGE_CIRCLE_SIZE / 2 + AndroidUtilities.statusBarHeight, MAX_PROFILE_IMAGE_CIRCLE_SIZE / 2);
    }

    public void handleExpansionInFirstStage(
            ImageView timeItem,
            ImageView starBgItem,
            ImageView starFgItem,
            float progress,
            ValueAnimator expandAnimator,
            float toolbarHeight
    ) {
        final float textFirstMoveThreshHold = 0.4f; // first we need to wait buttons scale then texts start moving
        final ProfileStoriesView storyView = referenceCallback.getStoryView();
        final ProfileGiftsView giftsView = referenceCallback.getGiftsView();
        final ProfileActivity.AvatarImageView avatarImageView = referenceCallback.getAvatarImage();
        final ProfileActivity.ShowDrawable showStatusButton = referenceCallback.getShowStatusButton();
        final AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterTextView = referenceCallback.getMediaCounterTextView();
        final FrameLayout avatarContainer = referenceCallback.getAvatarContainer();
        final ProfileActivity.TopView topView = referenceCallback.getTopView();
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        params.width = params.height = MIN_PROFILE_IMAGE_CIRCLE_SIZE;
        avatarImageView.setRoundRadius(referenceCallback.requestGetSmallAvatarRoundRadius());

        // gift animation and toolbar emojis animation should start after image become visible and text start moving
        float normalized = (progress - textFirstMoveThreshHold) / (1f - textFirstMoveThreshHold);
        normalized = Math.max(0f, Math.min(1f, normalized));

        if (topView != null) {
            topView.setExpandProgress(normalized);
            topView.invalidate();
        }
        if (storyView != null) {
            storyView.invalidate();
        }
        if (giftsView != null) {
            giftsView.setExpandCoords(toolbarHeight);
            giftsView.setExpandProgress(normalized);
        }

        if (toolbarButtonsLayout != null) {
            toolbarButtonsLayout.handleAnimation(progress, toolbarHeight, textFirstMoveThreshHold);
        }
        float diff = 0f;
        // text should move after button is expanded
        if (progress > textFirstMoveThreshHold) {
            float mapped = (progress - textFirstMoveThreshHold) / (1 - textFirstMoveThreshHold);
            mapped = Math.max(0f, Math.min(1f, mapped));
            diff = AndroidUtilities.lerp(0f, 1f, mapped);
        }
        float avatarScale = (float) AndroidUtilities.lerp(MIN_PROFILE_IMAGE_CIRCLE_SIZE, MAX_PROFILE_IMAGE_CIRCLE_SIZE, diff) / MIN_PROFILE_IMAGE_CIRCLE_SIZE;
        float imageSize = (MIN_PROFILE_IMAGE_CIRCLE_SIZE * avatarScale);
        float nameStartingY = getNameStartingY();
        avatarY = AndroidUtilities.lerp(-MIN_PROFILE_IMAGE_CIRCLE_SIZE, getAvatarYAfterFirstExpansion(), diff);
        float nameScale = AndroidUtilities.lerp(1f, NAME_SCALE_FIRST_EXPANSION, diff);
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
        float nameEndY = Math.max(getNameEndYForPhase1(), nameStartingY);
        nameY = AndroidUtilities.lerp(nameStartingY, nameEndY, diff);
        onlineY = nameY + GAP_BETWEEN_NAME_AND_ONLINE_TEXT;
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
                    nameX = diff * getTextCenterX(nameTextView[a]);
                }
                nameTextView[a].setTranslationX(nameX);
                nameTextView[a].setTranslationY(nameY);

                if (onlineTextView[a].getTextWidth() == 0) {
                    onlineX = AndroidUtilities.dp(16 - TOOLBAR_TEXT_INITIAL_START_MARGIN);
                    onlineTextView[a].setX(AndroidUtilities.dp(0));
                } else {
                    onlineX = diff * getTextCenterX(onlineTextView[a]);
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
        if (toolbarLayoutUpdateCallback != null) {
            toolbarLayoutUpdateCallback.onTextPositionUpdate(nameX, nameY, onlineX, onlineY);
            toolbarLayoutUpdateCallback.onAvatarScaleUpdate(avatarScale, avatarY);
        }
    }

    public void setTextLayoutUpdateCallback(ToolbarLayoutUpdateCallback callback) {
        toolbarLayoutUpdateCallback = callback;
    }

    public void setReferences(SimpleTextView[] _nameTextView, SimpleTextView[] _onlineTextView) {
        nameTextView = _nameTextView;
        onlineTextView = _onlineTextView;
    }

    public void setupToolbarButtons(Context context, Theme.ResourcesProvider resourcesProvider, SizeNotifierFrameLayout masterView) {
        toolbarButtonsLayout = new ProfileToolbarButtonsRowLayout(context, masterView, resourcesProvider);
        masterView.addView(toolbarButtonsLayout, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, ProfileToolbarButtonsRowLayout.FULL_HEIGHT));
        ArrayList<ProfileToolbarButtonItem> items = new ArrayList<>();
        items.add(new ProfileToolbarButtonItem(R.drawable.filled_message, "Message"));
        items.add(new ProfileToolbarButtonItem(R.drawable.filled_unmute, "Unmute"));
        items.add(new ProfileToolbarButtonItem(R.drawable.filled_call, "Call"));
        items.add(new ProfileToolbarButtonItem(R.drawable.filled_video, "Video")); //todo Omid it can be enum
        toolbarButtonsLayout.setItems(items);
        toolbarButtonsLayout.setAlpha(0f);
        masterView.blurBehindViews.add(toolbarButtonsLayout);
    }

    private float _nameStartingY = 0;

    private float getNameStartingY() {
        if (_nameStartingY == 0) {
            _nameStartingY = (referenceCallback.getActionBar().getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2.0f - (MIN_PROFILE_IMAGE_CIRCLE_SIZE / 2) + referenceCallback.getActionBar().getTranslationY();
        }
        return _nameStartingY;
    }

    private float getNameEndYForPhase1(){
        return FIRST_EXPANSION_HEIGHT_THRESH_HOLD - ProfileToolbarButtonsRowLayout.FULL_HEIGHT + MIN_PROFILE_IMAGE_CIRCLE_SIZE;
    }

    private float getNameYForPhase2And3(float extraHeight){
        return extraHeight - ProfileToolbarButtonsRowLayout.FULL_HEIGHT + MIN_PROFILE_IMAGE_CIRCLE_SIZE;
    }

    private float getAvatarYAfterFirstExpansion() {
        return getNameStartingY() + (float) MIN_PROFILE_IMAGE_CIRCLE_SIZE / 2;
    }

    private float getTextCenterX(SimpleTextView textView) {
        return ((float) (screenWidth - textView.getTextWidth()) / 2 - AndroidUtilities.dp(TOOLBAR_TEXT_INITIAL_START_MARGIN));
    }

    public boolean handleExpansionInSecondStage(
            LinearLayoutManager layoutManager,
            float expandProgress,
            boolean allowPullingDown,
            boolean isPulledDown,
            ValueAnimator expandAnimator,
            View topView,
            boolean openingAvatar,
            boolean doNotSetForeground,
            boolean isInLandscapeMode,
            boolean scrolling,
            boolean openAnimationInProgress,
            int playProfileAnimation,
            ActionBarMenuItem otherItem,
            ActionBarMenuItem searchItem,
            float avatarAnimationProgress,
            float h,
            boolean isChatNoForward,
            int newTop,
            ImageUpdater imageUpdater,
            float[] expandAnimatorValues,
            float extraHeight
    ) {
        final ProfileStoriesView storyView = referenceCallback.getStoryView();
        final ProfileGiftsView giftsView = referenceCallback.getGiftsView();
        float startingAvatarScale = (float) MAX_PROFILE_IMAGE_CIRCLE_SIZE / MIN_PROFILE_IMAGE_CIRCLE_SIZE;
        float avatarScale = AndroidUtilities.lerp(startingAvatarScale, startingAvatarScale + 0.3f, Math.min(1f, expandProgress * 3f));
        if (storyView != null) {
            storyView.invalidate();
        }
        if (giftsView != null) {
            giftsView.setTranslationY(extraHeight - FIRST_EXPANSION_HEIGHT_THRESH_HOLD);
            giftsView.invalidate();
        }

        final ProfileGalleryView avatarsViewPager = referenceCallback.getAvatarsViewPager();
        final AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterTextView = referenceCallback.getMediaCounterTextView();
        final FrameLayout avatarContainer = referenceCallback.getAvatarContainer();
        final RecyclerListView listView = referenceCallback.getListView();
        if (allowPullingDown && (openingAvatar || expandProgress >= THRESH_HOLD_FOR_AUTO_EXPAND)) {
             if (!expandAnimator.isRunning()) {
                if (!isPulledDown) {
                    isPulledDown = startAutoExpand(otherItem, searchItem, expandAnimator, topView, expandAnimatorValues, imageUpdater, isChatNoForward);
                }
            }
            ViewGroup.LayoutParams params = avatarsViewPager.getLayoutParams();
            params.width = listView.getMeasuredWidth();
            params.height = (int) (h + newTop);
            avatarsViewPager.requestLayout();
            if (!expandAnimator.isRunning()) {
                float additionalTranslationY = 0; //todo extra can be removed
                if (openAnimationInProgress && playProfileAnimation == 2) {
                    additionalTranslationY = -(1.0f - avatarAnimationProgress) * AndroidUtilities.dp(50);
                }
                nameY = getNameYForPhase2And3(extraHeight);
                onlineY = nameY+GAP_BETWEEN_NAME_AND_ONLINE_TEXT;
                nameTextView[1].setTranslationX(nameX);
                nameTextView[1].setTranslationY(nameY);
                onlineTextView[1].setTranslationX(onlineX);
                onlineTextView[1].setTranslationY(onlineY);
                if (mediaCounterTextView != null) {
                    mediaCounterTextView.setTranslationX(onlineX);
                    mediaCounterTextView.setTranslationY(onlineTextView[1].getTranslationY());
                }
                if (toolbarButtonsLayout != null) {
                    toolbarButtonsLayout.setTranslationY(extraHeight);
                }
                fireUpdateCollectibleHintCallback();
            }
        } else {
            if (isPulledDown) {
                isPulledDown= startAutoCollapse(otherItem, searchItem, expandAnimator, topView, imageUpdater, expandAnimatorValues, scrolling, isInLandscapeMode, doNotSetForeground);
            }

            avatarContainer.setScaleX(avatarScale);
            avatarContainer.setScaleY(avatarScale);

            if (expandAnimator == null || !expandAnimator.isRunning()) {
                float avatarSizeDifferenceComparedToFirstState = (avatarScale * MIN_PROFILE_IMAGE_CIRCLE_SIZE) - startingAvatarScale * MIN_PROFILE_IMAGE_CIRCLE_SIZE;
                avatarY = getAvatarYAfterFirstExpansion() + avatarSizeDifferenceComparedToFirstState;
                avatarContainer.setTranslationY(avatarY);
                nameY = getNameYForPhase2And3(extraHeight);
                nameX = getTextCenterX(nameTextView[1]);
                onlineX = getTextCenterX(onlineTextView[1]);
                onlineY = nameY + GAP_BETWEEN_NAME_AND_ONLINE_TEXT;
                nameTextView[1].setTranslationX(nameX);
                nameTextView[1].setTranslationY(nameY);
                onlineTextView[1].setTranslationX(onlineX);
                onlineTextView[1].setTranslationY(onlineY);
                if (mediaCounterTextView != null) {
                    mediaCounterTextView.setTranslationX(onlineX);
                    mediaCounterTextView.setTranslationY(onlineY);
                }
                fireUpdateCollectibleHintCallback();
            }

            if (toolbarButtonsLayout != null) {
                toolbarButtonsLayout.setTranslationY(extraHeight);
            }
        }
        if (toolbarLayoutUpdateCallback != null) {
            toolbarLayoutUpdateCallback.onTextPositionUpdate(nameX, nameY, onlineX, onlineY);
            toolbarLayoutUpdateCallback.onAvatarScaleUpdate(avatarScale, avatarY);
        }
        return isPulledDown;
    }

    public Boolean startAutoCollapse(
            ActionBarMenuItem otherItem,
            ActionBarMenuItem searchItem,
            ValueAnimator expandAnimator,
            View topView,
            ImageUpdater imageUpdater,
            float[] expandAnimatorValues,
            boolean scrolling,
            boolean isInLandscapeMode,
            boolean doNotSetForeground
    ) {
        final ProfileOverlaysView overlaysView = referenceCallback.getOverlaysView();
        final FrameLayout avatarContainer = referenceCallback.getAvatarContainer();
        final ProfileGalleryView avatarsViewPager = referenceCallback.getAvatarsViewPager();
        final ProfileActivity.AvatarImageView avatarImage = referenceCallback.getAvatarImage();
        final ProfileActivity.PagerIndicatorView avatarsViewPagerIndicatorView = referenceCallback.getIndicatorView();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
        if (otherItem != null) {
            otherItem.hideSubItem(gallery_menu_save);
            if (imageUpdater != null) {
                otherItem.hideSubItem(set_as_main);
                otherItem.hideSubItem(edit_avatar);
                otherItem.hideSubItem(delete_avatar);
                otherItem.showSubItem(add_photo);
                otherItem.showSubItem(logout);
//                                otherItem.showSubItem(edit_name);
            }
        }
        if (searchItem != null) {
            searchItem.setEnabled(!scrolling);
        }
        overlaysView.setOverlaysVisible(false, 1f);
        avatarsViewPagerIndicatorView.refreshVisibility(1f);
        expandAnimator.cancel();
        avatarImage.getImageReceiver().setAllowStartAnimation(true);
        avatarImage.getImageReceiver().startAnimation();

        expandAnimatorValues[0] = 1f;
        expandAnimatorValues[1] = 0f;
        expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        topView.setBackgroundColor(referenceCallback.getPageThemedColor(Theme.key_avatar_backgroundActionBarBlue));

        if (!doNotSetForeground) {
            BackupImageView imageView = avatarsViewPager.getCurrentItemView();
            if (imageView != null) {
                if (imageView.getImageReceiver().getDrawable() instanceof VectorAvatarThumbDrawable) {
                    avatarImage.drawForeground(false);
                } else {
                    avatarImage.drawForeground(true);
                    avatarImage.setForegroundImageDrawable(imageView.getImageReceiver().getDrawableSafe());
                }
            }
        }
        avatarImage.setForegroundAlpha(1f);
        avatarContainer.setVisibility(View.VISIBLE);
        avatarsViewPager.setVisibility(View.GONE);
        expandAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                referenceCallback.getGiftsView().setAlpha(1f);
                isThirdPhaseRunning = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isThirdPhaseRunning = false;
                expandAnimator.removeListener(this);
            }
        });
        expandAnimator.start();
        return false;
    }

    public boolean startAutoExpand(
            ActionBarMenuItem otherItem,
            ActionBarMenuItem searchItem,
            ValueAnimator expandAnimator,
            View topView,
            float[] expandAnimatorValues,
            ImageUpdater imageUpdater,
            boolean isChatNoForward
            ) {
        final ProfileOverlaysView overlaysView = referenceCallback.getOverlaysView();
        final FrameLayout avatarContainer = referenceCallback.getAvatarContainer();
        final ProfileGalleryView avatarsViewPager = referenceCallback.getAvatarsViewPager();
        final ProfileActivity.AvatarImageView avatarImage = referenceCallback.getAvatarImage();
        final ProfileActivity.PagerIndicatorView avatarsViewPagerIndicatorView = referenceCallback.getIndicatorView();
        final ProfileStoriesView storyView = referenceCallback.getStoryView();
        final ProfileGiftsView giftsView = referenceCallback.getGiftsView();
        if (otherItem != null) {
            if (!isChatNoForward) {
                otherItem.showSubItem(gallery_menu_save);
            } else {
                otherItem.hideSubItem(gallery_menu_save);
            }
            if (imageUpdater != null) {
                otherItem.showSubItem(add_photo);
                otherItem.showSubItem(edit_avatar);
                otherItem.showSubItem(delete_avatar);
                otherItem.hideSubItem(set_as_main);
                otherItem.hideSubItem(logout);
            }
        }
        if (searchItem != null) {
            searchItem.setEnabled(false);
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
        overlaysView.setOverlaysVisible(true, 0f);
        avatarsViewPagerIndicatorView.refreshVisibility(0f);
        avatarsViewPager.setCreateThumbFromParent(true);
        if (avatarsViewPager.getAdapter() != null) {
            avatarsViewPager.getAdapter().notifyDataSetChanged();
        }
        expandAnimator.cancel();
        expandAnimatorValues[0] = 0f;
        expandAnimatorValues[1] = 1f;
        if (storyView != null && !storyView.isEmpty()) {
            expandAnimator.setInterpolator(new FastOutSlowInInterpolator());
        } else {
            expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        }
        expandAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                isThirdPhaseRunning = true;
                setAvatarForegroundImage(false);
                avatarsViewPager.setAnimatedFileMaybe(avatarImage.getImageReceiver().getAnimation());
                avatarsViewPager.resetCurrentItem();
                giftsView.setAlpha(0f);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isThirdPhaseRunning = false;
                expandAnimator.removeListener(this);
                topView.setBackgroundColor(Color.BLACK);
                avatarContainer.setVisibility(View.GONE);
                avatarsViewPager.setVisibility(View.VISIBLE);
            }
        });
        expandAnimator.start();
        return true;
    }

    /**
     * Use it to auto scroll list to snapping points and update extra height
     * @param lm
     * @param isFullyExpanded
     */
    public void autoScrollListForAutoExpandingPhase(LinearLayoutManager lm, boolean isFullyExpanded){
        final View view = lm.findViewByPosition(0);
        final RecyclerListView listView = referenceCallback.getListView();
        if(view != null){
            BotWebViewVibrationEffect.IMPACT_RIGID.vibrate();
            if (isFullyExpanded) {
                listView.smoothScrollBy(0, getFullExpandSnappingPointForListView(lm), CubicBezierInterpolator.EASE_OUT_QUINT);
            } else {
                listView.smoothScrollBy(0, view.getTop() - ProfileToolbarHelper.FIRST_EXPANSION_HEIGHT_THRESH_HOLD, CubicBezierInterpolator.EASE_OUT_QUINT);
            }
        }
    }

    public int getFullExpandSnappingPointForListView(LinearLayoutManager lm){
        final View view = lm.findViewByPosition(0);
        final RecyclerListView listView = referenceCallback.getListView();
        if(view != null) {
            final ActionBar actionBar = referenceCallback.getActionBar();
            final int actionBarHeight = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
            return view.getTop() - listView.getMeasuredWidth() + actionBarHeight;
        } else {
            return 0;
        }
    }

    public boolean isThirdPhaseRunning = false;
    public void handleAutoExpandInThirdPhase(
            float value,
            float extraHeight,
            float expandProgress,
            float avatarScale,
            float mediaHeaderAnimationProgress,
            int actionBarBackgroundColor,
            ValueAnimator expandAnimator,
            MessagesController.PeerColor peerColor,
            ActionBarMenuItem searchItem,
            ScamDrawable scamDrawable,
            Drawable lockIconDrawable
    ) {
        isThirdPhaseRunning = true;
        if(extraHeight < FIRST_EXPANSION_HEIGHT_THRESH_HOLD && expandAnimator.isRunning()){
            expandAnimator.cancel();
            return;
        }
        final View avatarContainer = referenceCallback.getAvatarContainer();
        final ProfileStoriesView storyView = referenceCallback.getStoryView();
        final ProfileGiftsView giftsView = referenceCallback.getGiftsView();
        final ActionBar actionBar = referenceCallback.getActionBar();
        final RecyclerListView listView = referenceCallback.getListView();
        final ProfileActivity.AvatarImageView avatarImage = referenceCallback.getAvatarImage();
        final AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterTextView = referenceCallback.getMediaCounterTextView();
        final ProfileActivity.ShowDrawable showStatusButton = referenceCallback.getShowStatusButton();
        final CrossfadeDrawable[] verifiedCrossfadeDrawable = referenceCallback.getVerifiedCrossfadeDrawable();
        final CrossfadeDrawable[] premiumCrossfadeDrawable = referenceCallback.getPremiumCrossfadeDrawable();

        final int newTop = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
        referenceCallback.requestCheckPhotoDescriptionAlpha();
        avatarContainer.setScaleX(avatarScale);
        avatarContainer.setScaleY(avatarScale);
        avatarContainer.setTranslationY(AndroidUtilities.lerp(avatarY, avatarY + MIN_PROFILE_IMAGE_CIRCLE_SIZE, value));
        avatarImage.setRoundRadius((int) AndroidUtilities.lerp(referenceCallback.requestGetSmallAvatarRoundRadius(), 0f, value));
        if (storyView != null) {
            storyView.setExpandProgress(value);
        }
        if (searchItem != null) {
            searchItem.setAlpha(1.0f - value);
            searchItem.setScaleY(1.0f - value);
            searchItem.setVisibility(View.VISIBLE);
            searchItem.setClickable(searchItem.getAlpha() > .5f);
        }

        if (extraHeight > ProfileToolbarHelper.FIRST_EXPANSION_HEIGHT_THRESH_HOLD && expandProgress < THRESH_HOLD_FOR_AUTO_EXPAND) {
//            float startingAvatarScale = (float) MAX_PROFILE_IMAGE_CIRCLE_SIZE / MIN_PROFILE_IMAGE_CIRCLE_SIZE;
//            float avatarSizeDifferenceComparedToFirstState = (avatarScale * MIN_PROFILE_IMAGE_CIRCLE_SIZE) - startingAvatarScale * MIN_PROFILE_IMAGE_CIRCLE_SIZE;
//            nameY = getNameYAfterFirstExpansion() + avatarSizeDifferenceComparedToFirstState;
//            nameX = getTextCenterX(nameTextView[1]);
//            onlineX = getTextCenterX(onlineTextView[1]);
//            onlineY = nameY + GEP_BETWEEN_NAME_AND_ONLINE_TEXT;
        }

        if (scamDrawable != null) {
            scamDrawable.setColor(ColorUtils.blendARGB(getThemedColor(Theme.key_avatar_subtitleInProfileBlue), Color.argb(179, 255, 255, 255), value));
        }

        if (lockIconDrawable != null) {
            lockIconDrawable.setColorFilter(ColorUtils.blendARGB(getThemedColor(Theme.key_chat_lockIcon), Color.WHITE, value), PorterDuff.Mode.MULTIPLY);
        }

        if (verifiedCrossfadeDrawable[0] != null) {
            verifiedCrossfadeDrawable[0].setProgress(value);
        }
        if (verifiedCrossfadeDrawable[1] != null) {
            verifiedCrossfadeDrawable[1].setProgress(value);
        }

        if (premiumCrossfadeDrawable[0] != null) {
            premiumCrossfadeDrawable[0].setProgress(value);
        }
        if (premiumCrossfadeDrawable[1] != null) {
            premiumCrossfadeDrawable[1].setProgress(value);
        }

        referenceCallback.requestUpdateEmojiStatusDrawableColor(value);

        final Object onlineTextViewTag = onlineTextView[1].getTag();
        int statusColor;
        boolean online = false;
        if (onlineTextViewTag instanceof Integer) {
            statusColor = referenceCallback.getPageThemedColor((Integer) onlineTextViewTag);
            online = (Integer) onlineTextViewTag == Theme.key_profile_status;
        } else {
            statusColor = referenceCallback.getPageThemedColor(Theme.key_avatar_subtitleInProfileBlue);
        }
        onlineTextView[1].setTextColor(ColorUtils.blendARGB(referenceCallback.requestApplyPeerColor(statusColor, true, online), 0xB3FFFFFF, value));
        if (extraHeight > ProfileToolbarHelper.FIRST_EXPANSION_HEIGHT_THRESH_HOLD) {
            nameTextView[1].setPivotY(AndroidUtilities.lerp(0, nameTextView[1].getMeasuredHeight(), value));
            nameTextView[1].setScaleX(AndroidUtilities.lerp(NAME_SCALE_FIRST_EXPANSION, NAME_SCALE_FULL_EXPANSION, value));
            nameTextView[1].setScaleY(AndroidUtilities.lerp(NAME_SCALE_FIRST_EXPANSION, NAME_SCALE_FULL_EXPANSION, value));
        }

        if (showStatusButton != null) {
            showStatusButton.setBackgroundColor(ColorUtils.blendARGB(Theme.multAlpha(Theme.adaptHSV(actionBarBackgroundColor, +0.18f, -0.1f), 0.5f), 0x23ffffff, value));
        }

        needLayoutText(Math.min(1f, extraHeight / ProfileToolbarHelper.FIRST_EXPANSION_HEIGHT_THRESH_HOLD), extraHeight, mediaHeaderAnimationProgress);

        float textWidth = nameTextView[1].getTextWidth();
        float extraSizeBecauseOfScaling = (textWidth * NAME_SCALE_FULL_EXPANSION - textWidth * NAME_SCALE_FIRST_EXPANSION)/2;
        nameX = AndroidUtilities.lerp(getTextCenterX(nameTextView[1]), AndroidUtilities.dp(-48)+extraSizeBecauseOfScaling, value);
        nameY = AndroidUtilities.lerp(getNameYForPhase2And3(extraHeight), getNameYForPhase2And3(extraHeight), value);
        onlineX = AndroidUtilities.lerp(getTextCenterX(onlineTextView[1]), AndroidUtilities.dp(-48), value);
        onlineY = nameY + GAP_BETWEEN_NAME_AND_ONLINE_TEXT;

        nameTextView[1].setTranslationX(nameX);
        nameTextView[1].setTranslationY(nameY);
        onlineTextView[1].setTranslationX(onlineX);
        onlineTextView[1].setTranslationY(onlineY);
        mediaCounterTextView.setTranslationX(onlineX);
        mediaCounterTextView.setTranslationY(onlineY);

        nameTextView[1].setTextColor(ColorUtils.blendARGB(peerColor != null ? Color.WHITE : getThemedColor(Theme.key_profile_title), Color.WHITE, value));
        actionBar.setItemsColor(ColorUtils.blendARGB(peerColor != null ? Color.WHITE : getThemedColor(Theme.key_actionBarDefaultIcon), Color.WHITE, value), false);
        actionBar.setMenuOffsetSuppressed(true);

        avatarImage.setForegroundAlpha(value);

        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        params.width = (int) AndroidUtilities.lerp(AndroidUtilities.dpf2(42f), listView.getMeasuredWidth() / avatarScale, value);
        params.height = (int) AndroidUtilities.lerp(AndroidUtilities.dpf2(42f), (extraHeight + newTop) / avatarScale, value); //todo what is this 42?
        params.gravity = Gravity.CENTER_HORIZONTAL;
        avatarContainer.requestLayout();

        if (toolbarButtonsLayout != null) {
            toolbarButtonsLayout.setTranslationY(extraHeight);
        }
        if (toolbarLayoutUpdateCallback != null) {
            toolbarLayoutUpdateCallback.onTextPositionUpdate(nameX, nameY, onlineX, onlineY);
        }
        fireUpdateCollectibleHintCallback();
    }

    public void needLayoutText(float diff, float extraHeight, float mediaHeaderAnimationProgress) {
        final AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterTextView = referenceCallback.getMediaCounterTextView();
        final ProfileActivity.PagerIndicatorView avatarsViewPagerIndicatorView = referenceCallback.getIndicatorView();

        FrameLayout.LayoutParams layoutParams;
        float scale = nameTextView[1].getScaleX();
        float maxScale = extraHeight > ProfileToolbarHelper.FIRST_EXPANSION_HEIGHT_THRESH_HOLD ? 1.67f : 1.12f;

        if (extraHeight > ProfileToolbarHelper.FIRST_EXPANSION_HEIGHT_THRESH_HOLD && scale != maxScale) {
            return;
        }

        int viewWidth = AndroidUtilities.isTablet() ? AndroidUtilities.dp(490) : AndroidUtilities.displaySize.x;
        ActionBarMenuItem item = avatarsViewPagerIndicatorView.getSecondaryMenuItem();
        int extra = 0;
//        if (editItemVisible) { //todo can be removed later
//            extra += 48;
//        }
//        if (searchItem != null) {
//            extra += 48;
//        }
        int buttonsWidth = AndroidUtilities.dp(ProfileToolbarHelper.TOOLBAR_TEXT_INITIAL_START_MARGIN + 8 + (40 + extra * (1.0f - mediaHeaderAnimationProgress)));
        int minWidth = viewWidth - buttonsWidth;

        int width = (int) (viewWidth - buttonsWidth * Math.max(0.0f, 1.0f - (diff != 1.0f ? diff * 0.15f / (1.0f - diff) : 1.0f)) - nameTextView[1].getTranslationX());
        float width2 = nameTextView[1].getPaint().measureText(nameTextView[1].getText().toString()) * scale + nameTextView[1].getSideDrawablesSize();
        layoutParams = (FrameLayout.LayoutParams) nameTextView[1].getLayoutParams();
        int prevWidth = layoutParams.width;
        if (width < width2) {
            layoutParams.width = Math.max(minWidth, (int) Math.ceil((width - AndroidUtilities.dp(24)) / (scale + ((maxScale - scale) * 7.0f))));
        } else {
            layoutParams.width = (int) Math.ceil(width2);
        }
        layoutParams.width = (int) Math.min((viewWidth - nameTextView[1].getX()) / scale - AndroidUtilities.dp(8), layoutParams.width);
        if (layoutParams.width != prevWidth) {
            nameTextView[1].requestLayout();
        }

        width2 = onlineTextView[1].getPaint().measureText(onlineTextView[1].getText().toString()) + onlineTextView[1].getRightDrawableWidth();
        layoutParams = (FrameLayout.LayoutParams) onlineTextView[1].getLayoutParams();
        FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) mediaCounterTextView.getLayoutParams();
        prevWidth = layoutParams.width;
        layoutParams2.rightMargin = layoutParams.rightMargin = (int) Math.ceil(onlineTextView[1].getTranslationX() + AndroidUtilities.dp(8) + AndroidUtilities.dp(40) * (1.0f - diff));
        if (width < width2) {
            layoutParams2.width = layoutParams.width = (int) Math.ceil(width);
        } else {
            layoutParams2.width = layoutParams.width = LayoutHelper.WRAP_CONTENT;
        }
        if (prevWidth != layoutParams.width) {
            onlineTextView[2].getLayoutParams().width = layoutParams.width;
            onlineTextView[2].requestLayout();
            onlineTextView[3].getLayoutParams().width = layoutParams.width;
            onlineTextView[3].requestLayout();
            onlineTextView[1].requestLayout();
            mediaCounterTextView.requestLayout();
        }
    }

    private int getThemedColor(int key) {
        return referenceCallback.getPageThemedColor(key);
    }

    public void setAvatarForegroundImage(boolean secondParent) {
        final ProfileActivity.AvatarImageView avatarImage = referenceCallback.getAvatarImage();
        final ProfileGalleryView avatarsViewPager = referenceCallback.getAvatarsViewPager();
        if (avatarsViewPager == null || avatarImage == null) return;

        Drawable drawable = avatarImage.getImageReceiver().getDrawable();
        if (drawable instanceof VectorAvatarThumbDrawable) {
            avatarImage.setForegroundImage(null, null, drawable);
        } else if (drawable instanceof AnimatedFileDrawable) {
            AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) drawable;
            avatarImage.setForegroundImage(null, null, fileDrawable);
            if (secondParent) {
                fileDrawable.addSecondParentView(avatarImage);
            }
        } else {
            ImageLocation location = avatarsViewPager.getImageLocation(0);
            String filter;
            if (location != null && location.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
                filter = "avatar";
            } else {
                filter = null;
            }
            avatarImage.setForegroundImage(location, filter, drawable);
        }
    }

    private void fireUpdateCollectibleHintCallback() {
        if (toolbarLayoutUpdateCallback != null) {
            toolbarLayoutUpdateCallback.shouldUpdateCollectibleHint();
        }
    }

    public interface ToolbarLayoutUpdateCallback {
        void onTextPositionUpdate(float nameX, float nameY, float onlineX, float onlineY);

        void onAvatarScaleUpdate(float avatarScale, float avatarY);

        void shouldUpdateCollectibleHint();
    }

    public interface ProfileActivityReferenceCallback {
        ProfileActivity.AvatarImageView getAvatarImage();

        FrameLayout getAvatarContainer();

        NestedSizeNotifierLayout getAvatarContainer2();

        RecyclerListView getListView();

        ProfileStoriesView getStoryView();

        ProfileGiftsView getGiftsView();

        ProfileGalleryView getAvatarsViewPager();

        ProfileOverlaysView getOverlaysView();

        ProfileActivity.PagerIndicatorView getIndicatorView();

        ActionBar getActionBar();

        ProfileActivity.ShowDrawable getShowStatusButton();

        int getPageThemedColor(int key);

        CrossfadeDrawable[] getVerifiedCrossfadeDrawable();

        CrossfadeDrawable[] getPremiumCrossfadeDrawable();

        AudioPlayerAlert.ClippingTextViewSwitcher getMediaCounterTextView();
        void requestUpdateEmojiStatusDrawableColor(float progress);
        void requestCheckPhotoDescriptionAlpha();
        int requestGetSmallAvatarRoundRadius();
        int requestApplyPeerColor(int color, boolean actionBar, Boolean online);
        ProfileActivity.TopView getTopView();
    }
}
