package org.telegram.ui.screens.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BluredView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

public class ProfileAvatarContainer extends FrameLayout {

    private final CircularForegroundView blackForegroundView;
    private final AvatarCircularBlurForeground blurredView;

    public ProfileAvatarContainer(@NonNull Context context, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourceProvider) {
        super(context);
        this.blackForegroundView = new CircularForegroundView(context, Color.BLACK);
        blackForegroundView.setAlpha(0f);
        addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        blurredView = new AvatarCircularBlurForeground(context, avatarImage, resourceProvider);

        addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(blackForegroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void updateProgress(float progress) {
        final float START_HIDING_BLACK_WHEN_EXPANDING_THRESHOLD = 0.45f;
        final float START_SHOWING_BLACK_WHEN_COLLAPSING_THRESHOLD = 0.5f;

        float alphaProgress = Math.max(0f, (progress - START_HIDING_BLACK_WHEN_EXPANDING_THRESHOLD)) / (1 - START_HIDING_BLACK_WHEN_EXPANDING_THRESHOLD); //start removing black foreground when progress > 0.4
        alphaProgress = Math.min(1f, alphaProgress * (1 + START_SHOWING_BLACK_WHEN_COLLAPSING_THRESHOLD));  //start showing black foreground when progress < 0.91
        blackForegroundView.setAlpha(1 - alphaProgress);

        float blurredAlpha;
        if (progress > 0.6) {
            blurredAlpha = AndroidUtilities.lerp(1f, 0f, (float) (progress - 0.6) / 0.4f);
        } else {
            blurredAlpha = 1f;
        }
        blurredView.setAlpha(blurredAlpha);

        invalidate();
    }


    private boolean isWindowVisible;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        repeatDrawing();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        isWindowVisible = visibility == 0;
        if (!isWindowVisible) {
            blurQueue.cleanupQueue();
        } else {
            repeatDrawing();
        }
    }

    private final DispatchQueue blurQueue = new DispatchQueue("ProfileAvatarBlurQueue");

    private void repeatDrawing() {
        if (!isWindowVisible) return;
        blurredView.invalidate();
        blurQueue.postRunnable(this::repeatDrawing, 16);
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
            int circleSize = Math.min(backgroundView.getWidth(), ProfileToolbarHelper.MAX_PROFILE_IMAGE_CIRCLE_SIZE / 2);
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
}
