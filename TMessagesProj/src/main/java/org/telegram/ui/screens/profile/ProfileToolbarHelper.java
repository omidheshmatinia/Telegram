package org.telegram.ui.screens.profile;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.ui.ProfileActivity.add_photo;
import static org.telegram.ui.ProfileActivity.delete_avatar;
import static org.telegram.ui.ProfileActivity.edit_avatar;
import static org.telegram.ui.ProfileActivity.gallery_menu_save;
import static org.telegram.ui.ProfileActivity.gift_premium;
import static org.telegram.ui.ProfileActivity.leave_group;
import static org.telegram.ui.ProfileActivity.logout;
import static org.telegram.ui.ProfileActivity.report;
import static org.telegram.ui.ProfileActivity.set_as_main;
import static org.telegram.ui.ProfileActivity.view_discussion;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.contest.omid.R;
import org.telegram.tgnet.TLRPC;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BlurBehindDrawable;
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
import java.util.Locale;

public class ProfileToolbarHelper {
    public static final int MAX_PROFILE_IMAGE_CIRCLE_SIZE = dp(84);
    public static final int MIN_PROFILE_IMAGE_CIRCLE_SIZE = dp(42);
    public static final int FIRST_EXPANSION_HEIGHT_THRESH_HOLD = dp(180f);
    public static final int GAP_BETWEEN_NAME_AND_ONLINE_TEXT = dp(26.7f);
    public static final float TOOLBAR_TEXT_INITIAL_START_MARGIN = 64;
    public static final float NAME_SCALE_FIRST_EXPANSION = 1.12f;
    public static final float NAME_SCALE_FULL_EXPANSION = 1.12f;

    public static final float THRESH_HOLD_FOR_AUTO_EXPAND = 0.25f;
    public static final float THRESH_HOLD_FOR_AUTO_COLLAPSE = 0.30f;

    private ToolbarLayoutUpdateCallback toolbarLayoutUpdateCallback;
    public ProfileToolbarButtonsRowLayout toolbarButtonsLayout;
    private SimpleTextView[] nameTextView = new SimpleTextView[2];
    private SimpleTextView[] onlineTextView = new SimpleTextView[4];

    private float nameX = 0f;
    private float nameY = 0f;
    private float onlineX = 0f;
    private float onlineY = 0f;
    private float avatarY = 0f;
    public boolean isVideoCallItemVisible = false;
    public boolean isCallItemVisible = false;
    public boolean isMyProfile = false;
    public boolean isMuteMode = false;
    public boolean isMsgVisible = true;

    private final ProfileActivityReferenceCallback referenceCallback;

    public ProfileToolbarHelper(ProfileActivityReferenceCallback callback) {
        this.referenceCallback = callback;
    }

    public void checkVideoCallAndCallVisibility(TLRPC.UserFull userInfo, boolean myProfile, boolean isMuteMode) {
        if (userInfo != null && userInfo.phone_calls_available) {
            isCallItemVisible = true;
            isVideoCallItemVisible = userInfo.video_calls_available;
        }
        isMyProfile = myProfile;
        this.isMuteMode=isMuteMode;
    }

    public void setupGifts(){
        int screenWidth = AndroidUtilities.displaySize.x;
        boolean isLandscape = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
        Rect avatarRect;
        if(isLandscape || AndroidUtilities.isTablet()){
            float maxWidth = dp(420);
            float x= (AndroidUtilities.displaySize.x - maxWidth)/2;
            avatarRect = new Rect(x, 0, maxWidth , getNameEndYForPhase1());
        } else {
            avatarRect = new Rect(0, 0, screenWidth , getNameEndYForPhase1());
        }
        referenceCallback.getGiftsView().update();
        referenceCallback.getGiftsView().setGiftsViewBounds(avatarRect, screenWidth / 2, MAX_PROFILE_IMAGE_CIRCLE_SIZE / 2 + AndroidUtilities.statusBarHeight, MAX_PROFILE_IMAGE_CIRCLE_SIZE / 2);
    }

    public void handleExpansionInFirstStage(
            ImageView timeItem,
            ImageView starBgItem,
            ImageView starFgItem,
            float progress,
            ValueAnimator expandAnimator,
            float toolbarHeight,
            boolean isFirstAutoExpandAfterClickOnToolbar
    ) {
        final float textFirstMoveThreshHold = 0.4f; // first we need to wait buttons scale then texts start moving
        final ProfileStoriesView storyView = referenceCallback.getStoryView();
        final ProfileGiftsView giftsView = referenceCallback.getGiftsView();
        final ProfileActivity.AvatarImageView avatarImageView = referenceCallback.getAvatarImage();
        final ProfileActivity.ShowDrawable showStatusButton = referenceCallback.getShowStatusButton();
        final AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterTextView = referenceCallback.getMediaCounterTextView();
        final ProfileAvatarContainer avatarContainer = referenceCallback.getAvatarContainer();
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
        if (avatarContainer != null && !isFirstAutoExpandAfterClickOnToolbar) {
            avatarContainer.updateProgress(progress);
        }
        if (viewPagerBlurredBottom != null) {
            viewPagerBlurredBottom.setAlpha(0f);
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
            timeItem.setTranslationX(avatarContainer.getX() + dp(16) + extra);
            timeItem.setTranslationY(avatarContainer.getY() + dp(15) + extra);
            starBgItem.setTranslationX(avatarContainer.getX() + dp(28) + extra);
            starBgItem.setTranslationY(avatarContainer.getY() + dp(24) + extra);
            starFgItem.setTranslationX(avatarContainer.getX() + dp(28) + extra);
            starFgItem.setTranslationY(avatarContainer.getY() + dp(24) + extra);
        }
        float nameEndY = Math.max(getNameEndYForPhase1(), nameStartingY);
        nameY = AndroidUtilities.lerp(nameStartingY, nameEndY, diff);
        onlineY = nameY + GAP_BETWEEN_NAME_AND_ONLINE_TEXT;
        if (showStatusButton != null) {
            showStatusButton.setAlpha((int) (0xFF * diff));
        }
        for (int a = 0; a < nameTextView.length; a++) {
            if (nameTextView[a] == null || onlineTextView[a] == null) {
                continue;
            }
            if(diff == 1f){
                needLayoutText(diff, FIRST_EXPANSION_HEIGHT_THRESH_HOLD, 0);
            }
            nameTextView[a].setScaleX(nameScale);
            nameTextView[a].setScaleY(nameScale);

            if (expandAnimator == null || !expandAnimator.isRunning()) {
                // when user click on profile icon, it expand to full state directly and the widths are still zero
                if (nameTextView[a].getTextWidth() == 0) {
                    nameX = dp(-48);
                    nameTextView[a].setX(dp(16 - TOOLBAR_TEXT_INITIAL_START_MARGIN));
                } else {
                    nameX = diff * getTextCenterX(nameTextView[a], true);
                }
                nameTextView[a].setTranslationX(nameX);
                nameTextView[a].setTranslationY(nameY);


                nameTextView[a].requestLayout();
                if (onlineTextView[a].getTextWidth() == 0) {
                    onlineX = dp(16 - TOOLBAR_TEXT_INITIAL_START_MARGIN);
                    onlineTextView[a].setX(dp(0));
                } else {
                    onlineX = diff * getTextCenterX(onlineTextView[a], false);
                }
                onlineTextView[a].setTranslationX(onlineX);
                onlineTextView[a].setTranslationY(onlineY);
                if (a == 1) {
                    mediaCounterTextView.setTranslationX(onlineX);
                    mediaCounterTextView.setTranslationY(onlineY);
                }
            }
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

    public class ViewPagerBlurredBottom extends View {

        public final BlurBehindDrawable drawable;
        public ViewPagerBlurredBottom(Context context, View behindView, View parentView, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            drawable = new BlurBehindDrawable(behindView, parentView, 1, resourcesProvider);
            drawable.setAnimateAlpha(true);
            drawable.show(true);
        }


        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            drawable.onPanTranslationUpdate(- translationY);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawable.draw(canvas);
            drawable.invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            drawable.checkSizes();
        }

        public void update() {
            drawable.invalidate();
        }

        public boolean fullyDrawing() {
            return drawable.isFullyDrawing() && getVisibility() == View.VISIBLE;
        }
    }

    private ViewPagerBlurredBottom viewPagerBlurredBottom;
    public void setupToolbarButtons(Context context, Theme.ResourcesProvider resourcesProvider, SizeNotifierFrameLayout masterView, ActionBarMenuItem otherItem, ProfileToolbarButtonsRowLayout.ToolbarButtonClickCallback clickListener) {

        viewPagerBlurredBottom = new ViewPagerBlurredBottom(context, referenceCallback.getAvatarsViewPager(), masterView, resourcesProvider);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, ProfileToolbarButtonsRowLayout.FULL_HEIGHT);
        masterView.addView(viewPagerBlurredBottom, lp);
        masterView.blurBehindViews.add(viewPagerBlurredBottom);

        toolbarButtonsLayout = new ProfileToolbarButtonsRowLayout(context, masterView, resourcesProvider, clickListener);
        masterView.addView(toolbarButtonsLayout, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, ProfileToolbarButtonsRowLayout.FULL_HEIGHT));
        ArrayList<ProfileToolbarButtonItem> items = new ArrayList<>();
        if(isMsgVisible){
            items.add(ProfileToolbarButtonItem.Message);
        }
        if(otherItem.hasSubItem(view_discussion)){
            items.add(ProfileToolbarButtonItem.Discuss);
        }
        if(isMuteMode){
            items.add(ProfileToolbarButtonItem.UnMute);
        } else {
            items.add(ProfileToolbarButtonItem.Mute);
        }
        if(otherItem.hasSubItem(gift_premium)){
            items.add(ProfileToolbarButtonItem.Gift);
        }
        if(isCallItemVisible) {
            items.add(ProfileToolbarButtonItem.Call);
        }
        if(isVideoCallItemVisible) {
            items.add(ProfileToolbarButtonItem.Video);
        }
        if(otherItem.hasSubItem(leave_group)){
            items.add(ProfileToolbarButtonItem.Leave);
        }
        if(isMyProfile) {
            items.add(ProfileToolbarButtonItem.AddStory);
        }
        if(otherItem.hasSubItem(report)){
            items.add(ProfileToolbarButtonItem.Report);
        }
        toolbarButtonsLayout.setItems(items);
        toolbarButtonsLayout.setAlpha(0f);
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

    /**
     * calculate the Center TranslationX for textviews.
     * @param textView
     * @param isNameTextView
     * @return
     */
    private float getTextCenterX(SimpleTextView textView, boolean isNameTextView) {
        float startWidth = getTextCorrectWidth(textView, 1f, isNameTextView);
        float startCenter = startWidth/ 2 + dp(TOOLBAR_TEXT_INITIAL_START_MARGIN);
        float centerScreen = AndroidUtilities.displaySize.x / 2;
        return centerScreen-startCenter;
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
        Log.e("SCROLL","222nd");
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
        float threshHOld = isPulledDown ? THRESH_HOLD_FOR_AUTO_COLLAPSE : THRESH_HOLD_FOR_AUTO_EXPAND;
        if (allowPullingDown && (openingAvatar || expandProgress >= threshHOld)) {
             if (!expandAnimator.isRunning()) {
                if (!isPulledDown) {
                    isPulledDown = startAutoExpand(otherItem, searchItem, expandAnimator, topView, expandAnimatorValues, imageUpdater, isChatNoForward);
                }
            }
            Log.e("SCROLL","2222 >   1");
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
                //todo set height
//                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)avatarContainer.getLayoutParams();
//                lp.height = (int)extraHeight;
                if (mediaCounterTextView != null) {
                    mediaCounterTextView.setTranslationX(onlineX);
                    mediaCounterTextView.setTranslationY(onlineTextView[1].getTranslationY());
                }
                if (toolbarButtonsLayout != null) {
                    toolbarButtonsLayout.setTranslationY(extraHeight);
                    viewPagerBlurredBottom.setTranslationY(extraHeight);
                }
                fireUpdateCollectibleHintCallback();
            }
        } else {
            if (isPulledDown) {
                isPulledDown= startAutoCollapse(otherItem, searchItem, expandAnimator, topView, imageUpdater, expandAnimatorValues, scrolling, isInLandscapeMode, doNotSetForeground);
            }
            Log.e("SCROLL","2222 >   2");

            avatarContainer.setScaleX(avatarScale);
            avatarContainer.setScaleY(avatarScale);

            if (expandAnimator == null || !expandAnimator.isRunning()) {
                Log.e("SCROLL","2222 >   3");
                float avatarSizeDifferenceComparedToFirstState = (avatarScale * MIN_PROFILE_IMAGE_CIRCLE_SIZE) - startingAvatarScale * MIN_PROFILE_IMAGE_CIRCLE_SIZE;
                avatarY = getAvatarYAfterFirstExpansion() + avatarSizeDifferenceComparedToFirstState;
                avatarContainer.setTranslationY(avatarY);
                nameY = getNameYForPhase2And3(extraHeight);
                nameX = getTextCenterX(nameTextView[1], true);
                onlineX = getTextCenterX(onlineTextView[1], false);
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
                viewPagerBlurredBottom.setTranslationY(extraHeight);
            }
        }
        if (toolbarLayoutUpdateCallback != null) {
            toolbarLayoutUpdateCallback.onTextPositionUpdate(nameX, nameY, onlineX, onlineY);
            toolbarLayoutUpdateCallback.onAvatarScaleUpdate(avatarScale, avatarY);
        }
        return isPulledDown;
    }

    public void handleFullExpandAnimationFromClickingOnAvatar(
            float extraHeight,
            float avatarAnimationProgress,
            ScamDrawable scamDrawable,
            ImageView timeItem,
            ImageView starBgItem,
            ImageView starFgItem,
            Drawable lockIconDrawable,
            int newTop,
            MessagesController.PeerColor peerColor
    ){
        Log.e("SCROLL","handleFullExpandAnimationFromClickingOnAvatar");
        if(referenceCallback == null) return;

        final ActionBar actionBar = referenceCallback.getActionBar();
        final ProfileOverlaysView overlaysView = referenceCallback.getOverlaysView();
        final ProfileActivity.AvatarImageView avatarImage = referenceCallback.getAvatarImage();
        final ProfileStoriesView storyView = referenceCallback.getStoryView();
        final ProfileGiftsView giftsView = referenceCallback.getGiftsView();
        final ProfileAvatarContainer avatarContainer = referenceCallback.getAvatarContainer();
        final CrossfadeDrawable[] verifiedCrossfadeDrawable = referenceCallback.getVerifiedCrossfadeDrawable();
        final CrossfadeDrawable[] premiumCrossfadeDrawable = referenceCallback.getPremiumCrossfadeDrawable();

//        needLayoutText(1f, extraHeight, 0);

        float avY = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2.0f - 21 * AndroidUtilities.density + actionBar.getTranslationY();

        nameTextView[0].setTranslationX(0);
        nameTextView[0].setTranslationY((float) Math.floor(avY) + AndroidUtilities.dp(1.3f));
        onlineTextView[0].setTranslationX(0);
        onlineTextView[0].setTranslationY((float) Math.floor(avY) + AndroidUtilities.dp(24));
        nameTextView[0].setScaleX(1.0f);
        nameTextView[0].setScaleY(1.0f);

        nameTextView[1].setScaleX(ProfileToolbarHelper.NAME_SCALE_FULL_EXPANSION);
        nameTextView[1].setScaleY(ProfileToolbarHelper.NAME_SCALE_FULL_EXPANSION);


        float startWidth = getTextCorrectWidth(nameTextView[1], 1f, true);
        float endWidth = getTextCorrectWidth(nameTextView[1], NAME_SCALE_FULL_EXPANSION, true);
        float startCenter = AndroidUtilities.displaySize.x / 2 - (dp(TOOLBAR_TEXT_INITIAL_START_MARGIN) + startWidth / 2);
        float endCenter = AndroidUtilities.displaySize.x / 2 -(dp(16) + endWidth/2);
        nameX = startCenter - endCenter;
//        Log.e("TEXTISSUE2","startWidth="+startWidth+"   endWidth="+endWidth+"    dif="+dif+"    nameX="+nameX+"   x="+nameTextView[1].getX()+"  startCenter="+startCenter+"   endCenter="+endCenter);
////        float textWidthFull = getTextCorrectWidth(nameTextView[1], NAME_SCALE_FULL_EXPANSION, true);
////        float textWidthHalfExpand = getTextCorrectWidth(nameTextView[1], 1f, true);
////        float extraSizeBecauseOfScaling = (endWidth - startWidth);
////        nameX = dp(-48) + extraSizeBecauseOfScaling * 2;
////        nameX = dp(16);
        nameTextView[1].setTranslationX(nameX);
//        nameTextView[1].setX(dp(16));
        nameTextView[1].invalidate();


        float avatarScale = (float) MAX_PROFILE_IMAGE_CIRCLE_SIZE / MIN_PROFILE_IMAGE_CIRCLE_SIZE;
        if (storyView != null) {
            storyView.setExpandProgress(1f);
        }
        if (giftsView != null) {
            giftsView.setExpandProgress(1f);
        }

        avatarContainer.setScaleX(avatarScale);
        avatarContainer.setScaleY(avatarScale);
        avatarContainer.setTranslationY(AndroidUtilities.lerp(avatarY, avatarY + MIN_PROFILE_IMAGE_CIRCLE_SIZE, avatarAnimationProgress));
        avatarImage.setRoundRadius((int) AndroidUtilities.lerp(referenceCallback.requestGetSmallAvatarRoundRadius(), 0f, avatarAnimationProgress));


//        avatarImage.setRoundRadius((int) AndroidUtilities.lerp(referenceCallback.requestGetSmallAvatarRoundRadius(), 0f, avatarAnimationProgress));
//        avatarContainer.setTranslationY(AndroidUtilities.lerp((float) Math.ceil(avY), 0f, avatarAnimationProgress));
        Log.e("avatarContainer>>","#2  y="+AndroidUtilities.lerp((float) Math.ceil(avY), 0f, avatarAnimationProgress));
        float extra = (avatarContainer.getMeasuredWidth() - AndroidUtilities.dp(42)) * avatarScale;
        timeItem.setTranslationX(avatarContainer.getX() + AndroidUtilities.dp(16) + extra);
        timeItem.setTranslationY(avatarContainer.getY() + AndroidUtilities.dp(15) + extra);
        starBgItem.setTranslationX(avatarContainer.getX() + AndroidUtilities.dp(28) + extra);
        starBgItem.setTranslationY(avatarContainer.getY() + AndroidUtilities.dp(24) + extra);
        starFgItem.setTranslationX(avatarContainer.getX() + AndroidUtilities.dp(28) + extra);
        starFgItem.setTranslationY(avatarContainer.getY() + AndroidUtilities.dp(24) + extra);
//        avatarContainer.setScaleX(avatarScale);
//        avatarContainer.setScaleY(avatarScale);

        overlaysView.setAlphaValue(avatarAnimationProgress, false);
        actionBar.setItemsColor(ColorUtils.blendARGB(peerColor != null ? Color.WHITE : getThemedColor(Theme.key_actionBarDefaultIcon), Color.WHITE, avatarAnimationProgress), false);

        if (scamDrawable != null) {
            scamDrawable.setColor(ColorUtils.blendARGB(getThemedColor(Theme.key_avatar_subtitleInProfileBlue), Color.argb(179, 255, 255, 255), avatarAnimationProgress));
        }
        if (lockIconDrawable != null) {
            lockIconDrawable.setColorFilter(ColorUtils.blendARGB(getThemedColor(Theme.key_chat_lockIcon), Color.WHITE, avatarAnimationProgress), PorterDuff.Mode.MULTIPLY);
        }
        if (verifiedCrossfadeDrawable[1] != null) {
            verifiedCrossfadeDrawable[1].setProgress(avatarAnimationProgress);
            nameTextView[1].invalidate();
        }
        if (premiumCrossfadeDrawable[1] != null) {
            premiumCrossfadeDrawable[1].setProgress(avatarAnimationProgress);
            nameTextView[1].invalidate();
        }

        referenceCallback.requestUpdateEmojiStatusDrawableColor(avatarAnimationProgress);

        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        params.width = params.height = (int) AndroidUtilities.lerp(MIN_PROFILE_IMAGE_CIRCLE_SIZE, (extraHeight + newTop) / avatarScale, avatarAnimationProgress);
        avatarContainer.requestLayout();

        if (toolbarLayoutUpdateCallback != null) {
            toolbarLayoutUpdateCallback.onAvatarScaleUpdate(avatarScale, avatarY);
        }
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
        Log.e("EXPAND","startAutoCollapse");
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
        viewPagerBlurredBottom.setAlpha(0f);
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
        Log.e("EXPAND","startAutoExpand");
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
        overlaysView.setOverlaysVisible();
        overlaysView.setAlphaValue(1f, false);
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
                storyView.setVisibility(View.GONE);
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
                viewPagerBlurredBottom.setAlpha(1f);
                viewPagerBlurredBottom.update();
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
        Log.e("SCROLL","3RD");
        isThirdPhaseRunning = true;
        if(extraHeight < FIRST_EXPANSION_HEIGHT_THRESH_HOLD && expandAnimator.isRunning()){
            expandAnimator.cancel();
            return;
        }
        final View avatarContainer = referenceCallback.getAvatarContainer();
        final ProfileStoriesView storyView = referenceCallback.getStoryView();
        final ProfileGiftsView giftsView = referenceCallback.getGiftsView();
        final ProfileAvatarContainer avatarsContainer = referenceCallback.getAvatarContainer();
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

//        if(avatarsContainer != null){
//            avatarsContainer.updateThirdStageAnimationProgress(expandProgress);
//        }

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
            nameTextView[1].setScaleX(AndroidUtilities.lerp(NAME_SCALE_FIRST_EXPANSION, NAME_SCALE_FULL_EXPANSION, value));
            nameTextView[1].setScaleY(AndroidUtilities.lerp(NAME_SCALE_FIRST_EXPANSION, NAME_SCALE_FULL_EXPANSION, value));
        }

        if (showStatusButton != null) {
            showStatusButton.setBackgroundColor(ColorUtils.blendARGB(Theme.multAlpha(Theme.adaptHSV(actionBarBackgroundColor, +0.18f, -0.1f), 0.5f), 0x23ffffff, value));
        }

        needLayoutText(Math.min(1f, extraHeight / ProfileToolbarHelper.FIRST_EXPANSION_HEIGHT_THRESH_HOLD), extraHeight, mediaHeaderAnimationProgress);
        //todo check here about scale
        float textWidthFull = getTextCorrectWidth(nameTextView[1], NAME_SCALE_FULL_EXPANSION, true);
        float textWidthHalfExpand = getTextCorrectWidth(nameTextView[1], 1f, true);
        float extraSizeBecauseOfScaling = (textWidthFull - textWidthHalfExpand)/2;
        nameX = AndroidUtilities.lerp(getTextCenterX(nameTextView[1], true), dp(-48) + extraSizeBecauseOfScaling * 2, value);
        nameY = AndroidUtilities.lerp(getNameYForPhase2And3(extraHeight), getNameYForPhase2And3(extraHeight), value);
        onlineX = AndroidUtilities.lerp(getTextCenterX(onlineTextView[1], false), dp(-48), value);
        onlineY = nameY + GAP_BETWEEN_NAME_AND_ONLINE_TEXT;

        nameTextView[1].setTranslationX(nameX);
        nameTextView[1].setTranslationY(nameY);
        onlineTextView[1].setTranslationX(onlineX);
        onlineTextView[1].setTranslationY(onlineY);
        mediaCounterTextView.setTranslationX(onlineX);
        mediaCounterTextView.setTranslationY(onlineY);

        nameTextView[1].setTextColor(ColorUtils.blendARGB(peerColor != null ? Color.WHITE : getThemedColor(Theme.key_profile_title), Color.WHITE, value));

        nameTextView[1].requestLayout();
        onlineTextView[1].requestLayout();
        mediaCounterTextView.requestLayout();

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
            viewPagerBlurredBottom.setTranslationY(extraHeight);
        }
        if (toolbarLayoutUpdateCallback != null) {
            toolbarLayoutUpdateCallback.onTextPositionUpdate(nameX, nameY, onlineX, onlineY);
        }
        fireUpdateCollectibleHintCallback();
    }

    /**
     * Used to return the correct width ( text, icons at sides )
     * @param textView
     * @return
     */
    public static float getTextCorrectWidth(SimpleTextView textView, float scale, boolean isNameText){
        if(scale == 0f){
            scale = textView.getScaleX();
        }
        Log.e("TextIssue","getTextCorrectWidth  scale="+scale+"  widthbeforeScale"+textView.getPaint().measureText(textView.getText().toString())+"    widthWithScale="+textView.getPaint().measureText(textView.getText().toString()) * scale+"        sideDrawbaleSize = "+textView.getSideDrawablesSize());
        if(isNameText){
            return textView.getPaint().measureText(textView.getText().toString()) * scale + textView.getSideDrawablesSize();
        } else {
            return textView.getPaint().measureText(textView.getText().toString()) * scale + textView.getRightDrawableWidth();
        }
    }

    public void needLayoutText(float diff, float extraHeight, float mediaHeaderAnimationProgress) {
        final AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterTextView = referenceCallback.getMediaCounterTextView();
        final ProfileActivity.PagerIndicatorView avatarsViewPagerIndicatorView = referenceCallback.getIndicatorView();

        FrameLayout.LayoutParams layoutParams;
        float scale = nameTextView[1].getScaleX();
        float maxScale = extraHeight > ProfileToolbarHelper.FIRST_EXPANSION_HEIGHT_THRESH_HOLD ? NAME_SCALE_FULL_EXPANSION : NAME_SCALE_FIRST_EXPANSION;

        if (extraHeight > ProfileToolbarHelper.FIRST_EXPANSION_HEIGHT_THRESH_HOLD && scale != maxScale) {
            return;
        }

        int viewWidth = AndroidUtilities.isTablet() ? dp(490) : AndroidUtilities.displaySize.x;
        ActionBarMenuItem item = avatarsViewPagerIndicatorView.getSecondaryMenuItem();
        int extra = 0;
//        if (editItemVisible) { //todo can be removed later
//            extra += 48;
//        }
//        if (searchItem != null) {
//            extra += 48;
//        }
        if(diff == 1f){
//            Log.e("TextIssue","needLayoutText diff == 1f");
            int availableScreenWidth = viewWidth - dp(32f);
            float objectWidth = getTextCorrectWidth(nameTextView[1], nameTextView[1].getScaleX(), true);
            FrameLayout.LayoutParams nameTextLp = (FrameLayout.LayoutParams) nameTextView[1].getLayoutParams();
            if(objectWidth < availableScreenWidth){
                nameTextLp.width = (int) Math.ceil(objectWidth);
                Log.e("TextIssue","needLayoutText #1 objectWidth="+nameTextLp.width);
            } else {
                Log.e("TextIssue","needLayoutText #2 availableScreenWidth="+availableScreenWidth);
                nameTextLp.width = availableScreenWidth;
            }
            nameTextView[1].setLayoutParams(nameTextLp);
            nameTextView[1].requestLayout();

            objectWidth = getTextCorrectWidth(onlineTextView[1], 1f, false);
            int onlineTextFinalWidth;
            if(objectWidth < availableScreenWidth){
                onlineTextFinalWidth = LayoutHelper.WRAP_CONTENT;
            } else {
                onlineTextFinalWidth = availableScreenWidth;
            }
            onlineTextView[1].getLayoutParams().width = onlineTextFinalWidth;
            onlineTextView[2].getLayoutParams().width = onlineTextFinalWidth;
            onlineTextView[3].getLayoutParams().width = onlineTextFinalWidth;

            onlineTextView[1].requestLayout();
            onlineTextView[2].requestLayout();
            onlineTextView[3].requestLayout();
            return;
        }

        int buttonsWidth = dp(ProfileToolbarHelper.TOOLBAR_TEXT_INITIAL_START_MARGIN + 8 + (40 + extra * (1.0f - mediaHeaderAnimationProgress)));
        int minWidth = viewWidth - buttonsWidth;

        int width = (int) (viewWidth - buttonsWidth * Math.max(0.0f, 1.0f - (diff != 1.0f ? diff * 0.15f / (1.0f - diff) : 1.0f)) - nameTextView[1].getTranslationX());
        float width2 = getTextCorrectWidth(nameTextView[1], nameTextView[1].getScaleX(), true);
        layoutParams = (FrameLayout.LayoutParams) nameTextView[1].getLayoutParams();
        if (width < width2) {
            layoutParams.width = Math.max(minWidth, (int) Math.ceil((width - dp(24)) / (scale + ((maxScale - scale) * 7.0f))));
        } else {
            layoutParams.width = (int) Math.ceil(width2);
        }
        nameTextView[1].setLayoutParams(layoutParams);
        nameTextView[1].requestLayout();

        width2 = onlineTextView[1].getPaint().measureText(onlineTextView[1].getText().toString()) + onlineTextView[1].getRightDrawableWidth();
        layoutParams = (FrameLayout.LayoutParams) onlineTextView[1].getLayoutParams();
        FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) mediaCounterTextView.getLayoutParams();
        int prevWidth = layoutParams.width;
        layoutParams2.rightMargin = layoutParams.rightMargin = (int) Math.ceil(onlineTextView[1].getTranslationX() + dp(8) + dp(40) * (1.0f - diff)); //todo should remove it i guess
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

        ProfileAvatarContainer getAvatarContainer();

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
