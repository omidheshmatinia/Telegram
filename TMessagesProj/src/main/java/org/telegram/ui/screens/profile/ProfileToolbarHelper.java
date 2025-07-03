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
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
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
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NestedSizeNotifierLayout;
import org.telegram.ui.Components.ProfileGalleryView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.VectorAvatarThumbDrawable;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.ProfileGiftsView;
import org.telegram.ui.Stories.ProfileStoriesView;

import java.util.ArrayList;

public class ProfileToolbarHelper {
    public static int MAX_PROFILE_IMAGE_CIRCLE_SIZE = AndroidUtilities.dp(84);
    public static int MIN_PROFILE_IMAGE_CIRCLE_SIZE = AndroidUtilities.dp(42);
    public static int FIRST_EXPANSION_HEIGHT_THRESH_HOLD = AndroidUtilities.dp(196f);
    public static final float TOOLBAR_TEXT_INITIAL_START_MARGIN = 64;
    public static float NAME_SCALE_FIRST_EXPANSION = 1.12f;

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

    public void handleExpansionInFirstStage(
            ImageView timeItem,
            ImageView starBgItem,
            ImageView starFgItem,
            ProfileActivity.ShowDrawable showStatusButton,
            AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterTextView,
            float progress,
            ValueAnimator expandAnimator,
            ActionBar actionBar,
            float toolbarHeight
    ) {
        final float textFirstMoveThreshHold = 0.4f; // first we need to wait buttons scale then texts start moving
        final ProfileStoriesView storyView = referenceCallback.getStoryView();
        final ProfileGiftsView giftsView = referenceCallback.getGiftsView();
        if (storyView != null) {
            storyView.invalidate();
        }
        if (giftsView != null) {
            giftsView.invalidate();
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
        float avatarYExpandedHeight = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2.0f - (MIN_PROFILE_IMAGE_CIRCLE_SIZE / 2) + actionBar.getTranslationY();
        avatarY = AndroidUtilities.lerp(-MIN_PROFILE_IMAGE_CIRCLE_SIZE, avatarYExpandedHeight + (MIN_PROFILE_IMAGE_CIRCLE_SIZE / 2), diff);
        float nameScale = AndroidUtilities.lerp(1f, NAME_SCALE_FIRST_EXPANSION, diff);
        if (expandAnimator == null || !expandAnimator.isRunning()) {
            final FrameLayout avatarContainer = referenceCallback.getAvatarContainer();
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
        nameY = AndroidUtilities.lerp(avatarYExpandedHeight, avatarYExpandedHeight + imageSize + AndroidUtilities.dp(10), diff);
        onlineY = nameY + AndroidUtilities.dp(26.7f); // todo OMID: can improve by reading from a unified function
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

    public boolean handleExpansionInSecondStage(
            float expandProgress,
            boolean allowPullingDown,
            boolean isPulledDown,
            ValueAnimator expandAnimator,
            View topView,
            boolean openingAvatar,
            boolean doNotSetForeground,
            boolean isInLandscapeMode,
            float currentExpandAnimatorFracture,
            float customPhotoOffset,
            boolean scrolling,
            boolean openAnimationInProgress,
            int playProfileAnimation,
            ActionBarMenuItem otherItem,
            ActionBarMenuItem searchItem,
            float avatarAnimationProgress,
            float h,
            float listViewVelocityY,
            boolean isChatNoForward,
            int newTop,
            ImageUpdater imageUpdater,
            float[] expandAnimatorValues
    ) {
        final ProfileStoriesView storyView = referenceCallback.getStoryView();
        final ProfileGiftsView giftsView = referenceCallback.getGiftsView();
        float avatarScale = AndroidUtilities.lerp((42f + 18f) / 42f, (42f + 42f + 18f) / 42f, Math.min(1f, expandProgress * 3f)); //todo scales are wrong should use image size
        if (storyView != null) {
            storyView.invalidate();
        }
        if (giftsView != null) {
            giftsView.invalidate();
        }

        final float durationFactor = Math.min(AndroidUtilities.dpf2(2000f), Math.max(AndroidUtilities.dpf2(1100f), Math.abs(listViewVelocityY))) / AndroidUtilities.dpf2(1100f);
        final ProfileGalleryView avatarsViewPager = referenceCallback.getAvatarsViewPager();
        final ProfileOverlaysView overlaysView = referenceCallback.getOverlaysView();
        final ProfileActivity.AvatarImageView avatarImage = referenceCallback.getAvatarImage();
        final AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterTextView = referenceCallback.getMediaCounterTextView();
        final ProfileActivity.PagerIndicatorView avatarsViewPagerIndicatorView = referenceCallback.getIndicatorView();
        final FrameLayout avatarContainer = referenceCallback.getAvatarContainer();
        final RecyclerListView listView = referenceCallback.getListView();
        if (allowPullingDown && (openingAvatar || expandProgress >= 0.33f)) {
            if (!isPulledDown) {
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
                isPulledDown = true;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
                overlaysView.setOverlaysVisible(true, durationFactor);
                avatarsViewPagerIndicatorView.refreshVisibility(durationFactor);
                avatarsViewPager.setCreateThumbFromParent(true);
                if (avatarsViewPager.getAdapter() != null) {
                    avatarsViewPager.getAdapter().notifyDataSetChanged();
                }
                expandAnimator.cancel();
                float value = AndroidUtilities.lerp(expandAnimatorValues, currentExpandAnimatorFracture);
                expandAnimatorValues[0] = value;
                expandAnimatorValues[1] = 1f;
                if (storyView != null && !storyView.isEmpty()) {
                    expandAnimator.setInterpolator(new FastOutSlowInInterpolator());
                    expandAnimator.setDuration((long) ((1f - value) * 1.3f * 250f / durationFactor));
                } else {
                    expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    expandAnimator.setDuration((long) ((1f - value) * 250f / durationFactor));
                }
                expandAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        setAvatarForegroundImage(false);
                        avatarsViewPager.setAnimatedFileMaybe(avatarImage.getImageReceiver().getAnimation());
                        avatarsViewPager.resetCurrentItem();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        expandAnimator.removeListener(this);
                        topView.setBackgroundColor(Color.BLACK);
                        avatarContainer.setVisibility(View.GONE);
                        avatarsViewPager.setVisibility(View.VISIBLE);
                    }
                });
                expandAnimator.start();
            }
            ViewGroup.LayoutParams params = avatarsViewPager.getLayoutParams();
            params.width = listView.getMeasuredWidth();
            params.height = (int) (h + newTop);
            avatarsViewPager.requestLayout();
            if (!expandAnimator.isRunning()) {
                float additionalTranslationY = 0;
                if (openAnimationInProgress && playProfileAnimation == 2) {
                    additionalTranslationY = -(1.0f - avatarAnimationProgress) * AndroidUtilities.dp(50);
                }
                onlineX = AndroidUtilities.dpf2(16f) - onlineTextView[1].getLeft();
                nameTextView[1].setTranslationX(AndroidUtilities.dpf2(18f) - nameTextView[1].getLeft());
                nameTextView[1].setTranslationY(newTop + h - AndroidUtilities.dpf2(38f) - nameTextView[1].getBottom() + additionalTranslationY);
                onlineTextView[1].setTranslationX(onlineX + customPhotoOffset);
                onlineTextView[1].setTranslationY(newTop + h - AndroidUtilities.dpf2(18f) - onlineTextView[1].getBottom() + additionalTranslationY);
                if (mediaCounterTextView != null) {
                    mediaCounterTextView.setTranslationX(onlineTextView[1].getTranslationX());
                    mediaCounterTextView.setTranslationY(onlineTextView[1].getTranslationY());
                }
                fireUpdateCollectibleHintCallback();
            }
        } else {
            if (isPulledDown) {
                isPulledDown = false;
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
                overlaysView.setOverlaysVisible(false, durationFactor);
                avatarsViewPagerIndicatorView.refreshVisibility(durationFactor);
                expandAnimator.cancel();
                avatarImage.getImageReceiver().setAllowStartAnimation(true);
                avatarImage.getImageReceiver().startAnimation();

                float value = AndroidUtilities.lerp(expandAnimatorValues, currentExpandAnimatorFracture);
                expandAnimatorValues[0] = value;
                expandAnimatorValues[1] = 0f;
                expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                if (!isInLandscapeMode) {
                    expandAnimator.setDuration((long) (value * 250f / durationFactor));
                } else {
                    expandAnimator.setDuration(0);
                }
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
                expandAnimator.start();
            }

            avatarContainer.setScaleX(avatarScale);
            avatarContainer.setScaleY(avatarScale);

            if (expandAnimator == null || !expandAnimator.isRunning()) {
                refreshNameAndOnlineXY(avatarScale);
                nameTextView[1].setTranslationX(nameX);
                nameTextView[1].setTranslationY(nameY);
                onlineTextView[1].setTranslationX(onlineX + customPhotoOffset);
                onlineTextView[1].setTranslationY(onlineY);
                if (mediaCounterTextView != null) {
                    mediaCounterTextView.setTranslationX(onlineX);
                    mediaCounterTextView.setTranslationY(onlineY);
                }
                fireUpdateCollectibleHintCallback();
            }
        }
        if (toolbarLayoutUpdateCallback != null) {
            toolbarLayoutUpdateCallback.onTextPositionUpdate(nameX, nameY, onlineX, onlineY);
        }
        return isPulledDown;
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

    private void refreshNameAndOnlineXY(float avatarScale) {
        nameX = 0;
        nameY = (float) Math.floor(avatarY) + referenceCallback.getAvatarContainer().getHeight() + AndroidUtilities.dp(8.3f) + referenceCallback.getAvatarContainer().getMeasuredHeight() * (avatarScale - (42f + 18f) / 42f) / 2f;
        onlineX = 0;
        onlineY = nameY + AndroidUtilities.dp(26.7f); // todo: can improve by reading from a unified function
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

        int getPageThemedColor(int key);
        AudioPlayerAlert.ClippingTextViewSwitcher getMediaCounterTextView();
    }
}
