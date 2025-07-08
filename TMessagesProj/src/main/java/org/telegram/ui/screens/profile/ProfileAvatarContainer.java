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

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BluredView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

public class ProfileAvatarContainer extends FrameLayout {

    private final CircularForegroundView blackForegroundView;
    private final AvatarCircularBlurForeground blurredView;
    private final CurvedAnimationLayout curvedAnimationLayout;

    public ProfileAvatarContainer(@NonNull Context context, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourceProvider) {
        super(context);
        this.blackForegroundView = new CircularForegroundView(context, Color.BLACK);
        blackForegroundView.setAlpha(0f);
        addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        this.curvedAnimationLayout = new CurvedAnimationLayout(getContext(), this);
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
        curvedAnimationLayout.update();
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
            ((FrameLayout) parent).addView(curvedAnimationLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
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
        blurQueue.postRunnable(this::repeatDrawing, 50);
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

    public static class CurvedAnimationLayout extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final View avatarView; // Reference to the avatar image or container

        public CurvedAnimationLayout(Context context, View avatarView) {
            super(context);
            this.avatarView = avatarView;
            paint.setColor(Color.CYAN);
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
//            float y = cy - radius + bottomOffset;
            float dx = (float) Math.sqrt(2 * radius * bottomOffset - bottomOffset * bottomOffset);
//            float xLeft = cx - dx;
//            float xRight = cx + dx; // rightmost point at this y
            return dx;
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
            float avatarCenter = avatarTop + newSize / 2;
            float circleSideDistanceToCenter = getCirclePointComparedToCenterWhenItCutScreen(avatarRadius, avatarBottom);
            if (avatarBottom <= 0) {
                path.reset();
                return; // no need to draw anything
            }

            // STEP ONE FROM OUT OF THE WINDOW TILL HALF INSIDE
            path.reset();
            if (avatarCenter < THRESH_HOLD_FOR_MAX_HEIGHT) {
                paint.setColor(Color.CYAN);
//                final float progress = -avatarCenter / (avatarStartSize / 2);
//                float screenSidePointsGap = AndroidUtilities.lerp(circleSideDistanceToCenter + dp(8), MIN_DROP_WIDTH, progress);
                float screenSidePointsGap = circleSideDistanceToCenter + dp(16);
                final float startPointX = centerScreenX - screenSidePointsGap;
                final float endPointX = centerScreenX + screenSidePointsGap;
                path.moveTo(startPointX, 0);

                path.quadTo(startPointX + screenSidePointsGap / 4,0, centerScreenX - circleSideDistanceToCenter, avatarBottom/2);
                path.quadTo((centerScreenX - circleSideDistanceToCenter) + screenSidePointsGap / 6 , avatarBottom, centerScreenX, avatarBottom);
//                path.lineTo(centerScreenX, 0);

//                path.quadTo(centerScreenX + circleSideDistanceToCenter*3 /4,avatarBottom, centerScreenX + circleSideDistanceToCenter, avatarBottom/2);
//                path.quadTo((centerScreenX + circleSideDistanceToCenter) + screenSidePointsGap / 6 , 0, endPointX, 0);
                path.lineTo(centerScreenX,0);
                path.lineTo(startPointX,0);

                path.moveTo(endPointX , 0);
                path.quadTo(endPointX - screenSidePointsGap / 4,0, centerScreenX + circleSideDistanceToCenter, avatarBottom/2);
                path.quadTo((centerScreenX + circleSideDistanceToCenter) - screenSidePointsGap / 6 , avatarBottom, centerScreenX, avatarBottom);
                path.lineTo(centerScreenX,0);
                path.lineTo(endPointX , 0);

                // Draw right side (mirrored)
//                float mirror = centerScreenX;
//                float mirroredControl1X = 2 * mirror - (startPointX + screenSidePointsGap / 4);
//                float mirroredEnd1X = 2 * mirror - (centerScreenX - circleSideDistanceToCenter);
//
//                float mirroredControl2X = 2 * mirror - ((centerScreenX - circleSideDistanceToCenter) + screenSidePointsGap / 6);
//                float mirroredEnd2X = 2 * mirror - centerScreenX; // which is centerScreenX
//
//                path.quadTo(mirroredControl1X, 0, mirroredEnd1X, avatarBottom / 2);
//                path.quadTo(mirroredControl2X, avatarBottom, endPointX, 0);




//                path.cubicTo(startPointX + screenSidePointsGap / 4, 0,
//                        startPointX + screenSidePointsGap / 4, avatarBottom / 4,
//                        startPointX + screenSidePointsGap / 2, avatarBottom/2);
//
//                path.cubicTo(startPointX + (screenSidePointsGap * 3 / 4), avatarBottom,
//                        centerScreenX - (screenSidePointsGap / 4), avatarBottom,
//                        centerScreenX, avatarBottom);
//
//                path.cubicTo(centerScreenX + screenSidePointsGap / 2, (avatarBottom * 3 / 4),
//                        endPointX - screenSidePointsGap / 2, (avatarBottom * 1 / 4),
//                        endPointX, 0);
//                path.quadTo();
//
//                final float progress = -avatarCenter / (avatarStartSize / 2);
//                float screenSidePointsGap = AndroidUtilities.lerp(newSize / 2 + dp(32), MIN_DROP_WIDTH, progress); // this one should change based on distance
//                Log.e("OMID", "centerX=" + centerScreenX + "  circleSideDistanceToCenter=" + circleSideDistanceToCenter + "   screenSidePointsGap=" + screenSidePointsGap + "   avatarCenter=" + avatarCenter + "   avatarTop=" + avatarTop + "   progress=" + progress + "   screenSidePointsGap=" + screenSidePointsGap + "   newSize=" + newSize + "  avatarBottom=" + avatarBottom + "  ProfileToolbarHelper.MAX_PROFILE_IMAGE_CIRCLE_SIZE=" + ProfileToolbarHelper.MAX_PROFILE_IMAGE_CIRCLE_SIZE);
//                path.reset();
//                final float startPointX = centerScreenX - screenSidePointsGap;
//                final float endPointX = centerScreenX + screenSidePointsGap;
//                path.moveTo(startPointX, 0);
//
//                path.cubicTo(startPointX, avatarBottom,
//                        centerScreenX, avatarBottom, //todo here there should be a degree which change the second point, from \ to |
//                        centerScreenX, avatarBottom);
//
//                path.cubicTo(endPointX, avatarBottom,
//                        endPointX, 0,
//                        endPointX, 0);
//                path.lineTo(centerScreenX, 0);
//                path.lineTo(startPointX, 0);
            } else if(THRESH_HOLD_FOR_MAX_HEIGHT < avatarCenter && avatarTop < THRESH_HOLD_FOR_MAX_HEIGHT) {
                paint.setColor(Color.RED);
                final float progress = -avatarCenter / (avatarStartSize / 2);
                float screenSidePointsGap = AndroidUtilities.lerp((newSize / 2) + dp(8), MIN_DROP_WIDTH, progress);
                final float startPointX = centerScreenX - screenSidePointsGap;
                final float endPointX = centerScreenX + screenSidePointsGap;
                path.moveTo(startPointX, 0);

                path.cubicTo(startPointX + screenSidePointsGap / 2, (avatarBottom * 1 / 4),
                        centerScreenX - screenSidePointsGap / 2, (avatarBottom * 3 / 4),
                        centerScreenX, avatarBottom);

                path.cubicTo(centerScreenX + screenSidePointsGap / 2, (avatarBottom * 3 / 4),
                        endPointX - screenSidePointsGap / 2, (avatarBottom * 1 / 4),
                        endPointX, 0);

            } else if (avatarTop < THRESH_HOLD_FOR_MAX_HEIGHT) {
                final float progress = Math.min(1f, avatarTop / THRESH_HOLD_FOR_MAX_HEIGHT);
                float screenSidePointsGap = AndroidUtilities.lerp(newSize / 2 + dp(8), MIN_DROP_WIDTH, progress); // this one should change based on distance
                Log.e("OMID", "centerX=" + centerScreenX + "  circleSideDistanceToCenter=" + circleSideDistanceToCenter + "   screenSidePointsGap=" + screenSidePointsGap + "   avatarCenter=" + avatarCenter + "   avatarTop=" + avatarTop + "   progress=" + progress + "   screenSidePointsGap=" + screenSidePointsGap + "   newSize=" + newSize + "  avatarBottom=" + avatarBottom + "  ProfileToolbarHelper.MAX_PROFILE_IMAGE_CIRCLE_SIZE=" + ProfileToolbarHelper.MAX_PROFILE_IMAGE_CIRCLE_SIZE);
                final float startPointX = centerScreenX - screenSidePointsGap;
                final float endPointX = centerScreenX + screenSidePointsGap;
                path.moveTo(startPointX, 0);

                path.cubicTo(startPointX, 0,
                        centerScreenX, avatarBottom, //todo here there should be a degree which change the second point, from \ to |
                        centerScreenX, avatarBottom);

                path.cubicTo(endPointX, avatarBottom,
                        endPointX, 0,
                        endPointX, 0);
            } else if (avatarTop > THRESH_HOLD_FOR_MAX_HEIGHT && avatarTop <= THRESH_HOLD_FOR_MAX_HEIGHT * 2) {
                paint.setColor(Color.BLACK);
                final float progress = Math.min(1f, (avatarTop - THRESH_HOLD_FOR_MAX_HEIGHT) / THRESH_HOLD_FOR_MAX_HEIGHT);
                float screenSidePointsGap = AndroidUtilities.lerp(dp(32), MIN_DROP_WIDTH, progress);
                final float startPointX = centerScreenX - screenSidePointsGap;
                final float endPointX = centerScreenX + screenSidePointsGap;
                final float centerY = Math.max(0, THRESH_HOLD_FOR_MAX_HEIGHT - (avatarTop - THRESH_HOLD_FOR_MAX_HEIGHT));
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
