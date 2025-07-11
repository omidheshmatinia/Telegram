package org.telegram.ui.screens.profile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BluredView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

public class ProfileAvatarContainer extends FrameLayout {

    private final CircularForegroundView blackForegroundView;
    private final AvatarCircularBlurForeground blurredView;
    private final CurvedAnimationLayout curvedAnimationLayout;
    private final ProfileActivity.AvatarImageView imageView;

    public ProfileAvatarContainer(@NonNull Context context, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourceProvider) {
        super(context);
        this.blackForegroundView = new CircularForegroundView(context, Color.BLACK);
        this.imageView =avatarImage;
        blackForegroundView.setAlpha(0f);
        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        lp.setMargins(1, 1, 1, 1); // to prevent anti alias issue at corners
        addView(avatarImage, lp);
        this.curvedAnimationLayout = new CurvedAnimationLayout(getContext(), this);
        blurredView = new AvatarCircularBlurForeground(context, avatarImage, resourceProvider);
        blurredView.setAlpha(0f);
        addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(blackForegroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void updateProgress(float progress) {
        final float START_HIDING_BLACK_WHEN_EXPANDING_THRESHOLD = 0.6f;
        final float START_SHOWING_BLACK_WHEN_COLLAPSING_THRESHOLD = 0.3f;

        imageView.setRoundRadius(ProfileToolbarHelper.MAX_PROFILE_IMAGE_CIRCLE_SIZE);

        float alphaProgress = Math.max(0f, (progress - START_HIDING_BLACK_WHEN_EXPANDING_THRESHOLD)) / (1 - START_HIDING_BLACK_WHEN_EXPANDING_THRESHOLD); //start removing black foreground when progress > 0.4
        alphaProgress = Math.min(1f, alphaProgress * (1 + START_SHOWING_BLACK_WHEN_COLLAPSING_THRESHOLD));  //start showing black foreground when progress < 0.91
        blackForegroundView.setAlpha(1 - alphaProgress);

        float blurredAlpha;
        final float START_SHOWING_BLUR_EFFECT_THRESHOLD = 0.75f;
        if (progress > START_SHOWING_BLUR_EFFECT_THRESHOLD) {
            blurredAlpha = AndroidUtilities.lerp(1f, 0f, (progress - START_SHOWING_BLUR_EFFECT_THRESHOLD) / (1 - START_SHOWING_BLUR_EFFECT_THRESHOLD));
        } else {
            blurredAlpha = 1f;
        }
        blurredView.setAlpha(blurredAlpha);

        invalidate();
        if(progress == 0f || progress == 1f){
            curvedAnimationLayout.setVisibility(View.GONE);
        } else {
            curvedAnimationLayout.setVisibility(View.VISIBLE);
            curvedAnimationLayout.update();
        }
    }


    private boolean isWindowVisible;
    private boolean isCurvedAnimationAttached = false;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        repeatDrawing();
        ViewParent parent = getParent();
        if (parent instanceof FrameLayout && !isCurvedAnimationAttached) {
            isCurvedAnimationAttached = true;
            ((FrameLayout) parent).addView(curvedAnimationLayout, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT)); // it should be above background profile emojis
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        isWindowVisible = visibility == 0;
        if (isWindowVisible) {
            repeatDrawing();
        }
    }

    private void repeatDrawing() {
        if (!isWindowVisible) return;
        blurredView.invalidate();
    }

    private static class CircularForegroundView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public CircularForegroundView(Context context, int color) {
            super(context);
            paint.setColor(color);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = Math.min(getWidth(), getHeight()) / 2f;
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);
        }

        public void setColor(int color) {
            paint.setColor(color);
            invalidate();
        }
    }

    private static class AvatarCircularBlurForeground extends BluredView {

        final View backgroundView;

        public AvatarCircularBlurForeground(Context context, View bgView, Theme.ResourcesProvider resourcesProvider) {
            super(context, bgView, resourcesProvider);
            backgroundView = bgView;
            drawable.setAnimateAlpha(false);
        }


        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            drawable.checkSizes();
            // Create a circular path
            int circleSize = Math.min(((FrameLayout) backgroundView.getParent()).getWidth(), ProfileToolbarHelper.MAX_PROFILE_IMAGE_CIRCLE_SIZE / 2); // using the parent for width calculation to prevent alias issue and padding
            float radius = circleSize / 2f;
            circlePath.addCircle(circleSize / 2f, circleSize / 2f, radius, Path.Direction.CW);
        }

        Path circlePath = new Path();

        @Override
        protected void onDraw(Canvas canvas) {
            // Clip the canvas to the circle
            canvas.clipPath(circlePath);
            // Draw the blurred content as usual
            super.onDraw(canvas);
        }
    }

    private static class CurvedAnimationLayout extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final View avatarView; // Reference to the avatar image or container

        public CurvedAnimationLayout(Context context, View avatarView) {
            super(context);
            this.avatarView = avatarView;
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(path, paint);
        }

        final float THRESH_HOLD_FOR_MAX_HEIGHT = dp(12);
        float avatarStartSize = ProfileToolbarHelper.MIN_PROFILE_IMAGE_CIRCLE_SIZE;

        private float getCirclePointComparedToCenterWhenItCutScreen(float radius, float bottomOffset) {
            return (float) Math.sqrt(2 * radius * bottomOffset - bottomOffset * bottomOffset);
        }

        final float MIN_DROP_WIDTH = dp(16);

        public void update() {
            float centerScreenX = avatarView.getX() + (float) avatarView.getWidth() / 2;
            float scale = avatarView.getScaleY();
            float newSize = scale * avatarView.getHeight();
            float sizeDifference = avatarStartSize - newSize;
            float avatarY = avatarView.getY();
            float avatarRadius = newSize / 2f;
            final float avatarTop = avatarY + sizeDifference / 2;
            final float avatarBottom = avatarTop + newSize;
            float avatarCenterY = avatarTop + newSize / 2;
            float circleSideDistanceToCenter = getCirclePointComparedToCenterWhenItCutScreen(avatarRadius, avatarBottom);
            if (avatarBottom <= 0) {
                path.reset();
                return; // no need to draw anything
            }

            // STEP ONE FROM OUT OF THE WINDOW TILL HALF INSIDE
            path.reset();
            if (avatarCenterY < THRESH_HOLD_FOR_MAX_HEIGHT) {
                float screenSidePointsGap = circleSideDistanceToCenter + dp(16);
                final float startPointX = centerScreenX - screenSidePointsGap;
                final float endPointX = centerScreenX + screenSidePointsGap;
                // draw left side
                path.moveTo(startPointX, 0);
                path.quadTo(startPointX + screenSidePointsGap / 4, 0, centerScreenX - circleSideDistanceToCenter, avatarBottom / 2);
                path.quadTo((centerScreenX - circleSideDistanceToCenter) + screenSidePointsGap / 6, avatarBottom, centerScreenX, avatarBottom);
                path.lineTo(centerScreenX, 0);
                path.lineTo(startPointX, 0);

                // draw right side
                path.moveTo(endPointX, 0);
                path.quadTo(endPointX - screenSidePointsGap / 4, 0, centerScreenX + circleSideDistanceToCenter, avatarBottom / 2);
                path.quadTo((centerScreenX + circleSideDistanceToCenter) - screenSidePointsGap / 6, avatarBottom, centerScreenX, avatarBottom);
                path.lineTo(centerScreenX, 0);
                path.lineTo(endPointX, 0);

            } else if (avatarTop < THRESH_HOLD_FOR_MAX_HEIGHT / 2) {
                // this part to make the width smaller
                circleSideDistanceToCenter = getCirclePointComparedToCenterWhenItCutScreen(avatarRadius, avatarRadius - avatarCenterY + THRESH_HOLD_FOR_MAX_HEIGHT / 2);

                final float progress = Math.min(1f, (THRESH_HOLD_FOR_MAX_HEIGHT - avatarTop) / THRESH_HOLD_FOR_MAX_HEIGHT);
                float screenSidePointsGap = AndroidUtilities.lerp(dp(32), circleSideDistanceToCenter + dp(16), progress);
                float circleSideDistanceToCenterThreshHold = getCirclePointComparedToCenterWhenItCutScreen(avatarRadius, avatarRadius + avatarCenterY - THRESH_HOLD_FOR_MAX_HEIGHT);
                final float startPointX = centerScreenX - screenSidePointsGap;
                final float endPointX = centerScreenX + screenSidePointsGap;

                // draw left side
                path.moveTo(startPointX, 0);
                path.quadTo(centerScreenX - (circleSideDistanceToCenterThreshHold + circleSideDistanceToCenter) / 2, THRESH_HOLD_FOR_MAX_HEIGHT / 2, centerScreenX - circleSideDistanceToCenterThreshHold, THRESH_HOLD_FOR_MAX_HEIGHT);
                path.lineTo(centerScreenX, THRESH_HOLD_FOR_MAX_HEIGHT);
                path.lineTo(centerScreenX, 0);
                path.lineTo(startPointX, 0);

                // draw right side
                path.moveTo(endPointX, 0);
                path.quadTo(centerScreenX + (circleSideDistanceToCenterThreshHold + circleSideDistanceToCenter) / 2, THRESH_HOLD_FOR_MAX_HEIGHT / 2, centerScreenX + circleSideDistanceToCenterThreshHold, THRESH_HOLD_FOR_MAX_HEIGHT);
                path.lineTo(centerScreenX, THRESH_HOLD_FOR_MAX_HEIGHT);
                path.lineTo(centerScreenX, 0);
                path.lineTo(endPointX, 0);

            } else {
                final float progress = Math.min(1f, (avatarTop - THRESH_HOLD_FOR_MAX_HEIGHT / 2) / THRESH_HOLD_FOR_MAX_HEIGHT / 2);
                float screenSidePointsGap = AndroidUtilities.lerp(dp(32), MIN_DROP_WIDTH, progress);
                final float startPointX = centerScreenX - screenSidePointsGap;
                final float endPointX = centerScreenX + screenSidePointsGap;
                final float centerY = Math.max(0, THRESH_HOLD_FOR_MAX_HEIGHT / 2 - (avatarTop - THRESH_HOLD_FOR_MAX_HEIGHT / 2));

                path.moveTo(startPointX, 0);
                path.cubicTo(startPointX + screenSidePointsGap / 2, (centerY * 1 / 4),
                        centerScreenX - screenSidePointsGap / 2, (centerY * 3 / 4),
                        centerScreenX, centerY);
                path.cubicTo(centerScreenX + screenSidePointsGap / 2, (centerY * 3 / 4),
                        endPointX - screenSidePointsGap / 2, (centerY * 1 / 4),
                        endPointX, 0);
            }
            path.close();
            invalidate();
        }
    }
}
