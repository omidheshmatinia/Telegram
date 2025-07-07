package org.telegram.ui.screens.profile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BlurSettingsBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

public class ProfileAvatarContainer extends FrameLayout {

    private final float DOWN_SCALE = 12f;
    private final int TOP_CLIP_OFFSET = (int) (10 + DOWN_SCALE * 2);
    BlurBitmap currentBitmap, prevBitmap;
    Matrix matrix = new Matrix();
    Matrix matrix2 = new Matrix();
    public Paint blurPaintTop = new Paint();
    private Paint selectedBlurPaint; //todo
    public float blurCrossfadeProgress; //todo
    ValueAnimator blurCrossfade;
    public boolean blurIsRunning, needBlur, blurGeneratingTuskIsRunning;
    boolean attached, invalidateBlur;
    int count, times, count2, times2;
    private static DispatchQueue blurQueue;
    private boolean[] blurNodeInvalidated = new boolean[2];

    private final ArrayList<SizeNotifierFrameLayout.IViewWithInvalidateCallback> lastViews = new ArrayList<>();
    private final ArrayList<SizeNotifierFrameLayout.IViewWithInvalidateCallback> views = new ArrayList<>();

    final BlurBackgroundTask blurBackgroundTask = new BlurBackgroundTask();

    public ArrayList<BlurBitmap> unusedBitmaps = new ArrayList<>(10);
    public ArrayList<View> blurBehindViews = new ArrayList<>();

    private ProfileActivity.AvatarImageView avatarImage;
    private final View blackForegroundView;
    public ProfileAvatarContainer(@NonNull Context context, ProfileActivity.AvatarImageView avatarImageView) {
        super(context);
        this.avatarImage = avatarImageView;
        this.blackForegroundView = new View(context);
        blackForegroundView.setAlpha(0f);
        blackForegroundView.setBackgroundColor(Color.BLACK); //todo set bg color
        addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(blackForegroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void updateProgress(float progress){
        // todo alpha black foreground
        if (progress <= 0.8f) {
            float alphaProgress= Math.max(0f, (progress - 0.2f) / 0.8f);
            blackForegroundView.setAlpha(alphaProgress);

        }

        // todo blur background
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (needBlur && !blurIsRunning) {
            blurIsRunning = true;
            invalidateBlur = true;
        }
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        blurPaintTop.setShader(null);
//        blurPaintTop2.setShader(null);
//        blurPaintBottom.setShader(null);
//        blurPaintBottom2.setShader(null);
        if (blurCrossfade != null) {
            blurCrossfade.cancel();
        }
        if (currentBitmap != null) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        for (int i = 0; i < unusedBitmaps.size(); i++) {
            if (unusedBitmaps.get(i) != null) {
                unusedBitmaps.get(i).recycle();
            }
        }
        unusedBitmaps.clear();
        blurIsRunning = false;

//        if (backgroundDrawable instanceof ChatBackgroundDrawable) {
//            ((ChatBackgroundDrawable) backgroundDrawable).onDetachedFromWindow(backgroundView);
//        }
//        if (oldBackgroundDrawable instanceof ChatBackgroundDrawable) {
//            ((ChatBackgroundDrawable) oldBackgroundDrawable).onDetachedFromWindow(backgroundView);
//        }
    }

    public boolean DRAW_USING_RENDERNODE() {
        return Build.VERSION.SDK_INT >= 31 && SharedConfig.useNewBlur;
    }

    private float getRenderNodeScale() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return AndroidUtilities.density;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return dp(12);
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return dp(15);
        }
    }

    private float getBlurRadius() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return 60;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return 4;
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return 3;
        }
    }

    public void drawBlurCircle(Canvas canvas, float viewY, float cx, float cy, float radius, Paint blurScrimPaint) {
        int blurAlpha = Color.alpha(Theme.getColor(DRAW_USING_RENDERNODE() ? Theme.key_chat_BlurAlpha : Theme.key_chat_BlurAlphaSlow));
        if (currentBitmap == null || !SharedConfig.chatBlurEnabled()) {
            canvas.drawCircle(cx, cy, radius, blurScrimPaint);
            return;
        }
        updateBlurShaderPosition(viewY);
        blurScrimPaint.setAlpha(255);
//        if (blurCrossfadeProgress != 1f && selectedBlurPaint2.getShader() != null) {
//            canvas.drawCircle(cx, cy, radius, blurScrimPaint);
//            canvas.drawCircle(cx, cy, radius, selectedBlurPaint2);
//            canvas.saveLayerAlpha(cx - radius, cy - radius, cx + radius, cy + radius, (int) (blurCrossfadeProgress * 255), Canvas.ALL_SAVE_FLAG);
//            canvas.drawCircle(cx, cy, radius, blurScrimPaint);
//            canvas.drawCircle(cx, cy, radius, selectedBlurPaint);
//            canvas.restore();
//        } else {
            canvas.drawCircle(cx, cy, radius, blurScrimPaint);
            canvas.drawCircle(cx, cy, radius, selectedBlurPaint);
//        }

        blurScrimPaint.setAlpha(blurAlpha);
        canvas.drawCircle(cx, cy, radius, blurScrimPaint);
    }


    private void updateBlurShaderPosition(float viewY) {
        selectedBlurPaint = blurPaintTop;

        viewY += getTranslationY(); //todo

        if (selectedBlurPaint.getShader() != null) {
            matrix.reset();
            matrix2.reset();
//            if (!top) {
                float y1 = -viewY + currentBitmap.bottomOffset - currentBitmap.pixelFixOffset - TOP_CLIP_OFFSET - (currentBitmap.drawnListTranslationY - getMeasuredHeight());
                matrix.setTranslate(0, y1);
                matrix.preScale(currentBitmap.bottomScaleX, currentBitmap.bottomScaleY);

                if (prevBitmap != null) {
                    y1 = -viewY + prevBitmap.bottomOffset - prevBitmap.pixelFixOffset - TOP_CLIP_OFFSET - (prevBitmap.drawnListTranslationY - getMeasuredHeight());
                    matrix2.setTranslate(0, y1);
                    matrix2.preScale(prevBitmap.bottomScaleX, prevBitmap.bottomScaleY);
                }
//            } else {
//                matrix.setTranslate(0, -viewY - currentBitmap.pixelFixOffset - TOP_CLIP_OFFSET);
//                matrix.preScale(currentBitmap.topScaleX, currentBitmap.topScaleY);
//
//                if (prevBitmap != null) {
//                    matrix2.setTranslate(0, -viewY - prevBitmap.pixelFixOffset - TOP_CLIP_OFFSET);
//                    matrix2.preScale(prevBitmap.topScaleX, prevBitmap.topScaleY);
//                }
//            }

            selectedBlurPaint.getShader().setLocalMatrix(matrix);
        }
    }


    public void updateBlurContent() {
        if (DRAW_USING_RENDERNODE()) {
            invalidateBlurredViews();
        }
    }

    public void startBlur() {
        if (!blurIsRunning || blurGeneratingTuskIsRunning || !invalidateBlur || !SharedConfig.chatBlurEnabled() || DRAW_USING_RENDERNODE()) {
            return;
        }

        int blurAlpha = Color.alpha(Theme.getColor(Theme.key_chat_BlurAlphaSlow));
        if (blurAlpha == 255) {
            return;
        }
        int lastW = getMeasuredWidth();
        int lastH = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + dp(100);
        if (lastW == 0 || lastH == 0) {
            return;
        }
// TODO uncomment for support saturation in blur
//        if (this.saturation != BlurSettingsBottomSheet.saturation) {
//            this.saturation = BlurSettingsBottomSheet.saturation;
//            ColorMatrix colorMatrix = new ColorMatrix();
//            colorMatrix.setSaturation(saturation * 5);
//            blurPaintTop.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
//            blurPaintTop2.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
//            blurPaintBottom.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
//            blurPaintBottom2.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
//        }

        invalidateBlur = false;
        blurGeneratingTuskIsRunning = true;

        int bitmapH = (int) (lastH / DOWN_SCALE) + TOP_CLIP_OFFSET;
        int bitmapW = (int) (lastW / DOWN_SCALE);

        long time = System.currentTimeMillis();
        BlurBitmap bitmap = null;
        if (unusedBitmaps.size() > 0) {
            bitmap = unusedBitmaps.remove(unusedBitmaps.size() - 1);
        }

        if (bitmap == null) {
            bitmap = new BlurBitmap();
            bitmap.topBitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
            bitmap.topCanvas = new SizeNotifierFrameLayout.SimplerCanvas(bitmap.topBitmap);

//            if (needBlurBottom) {
//                bitmap.bottomBitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
//                bitmap.bottomCanvas = new SizeNotifierFrameLayout.SimplerCanvas(bitmap.bottomBitmap);
//            }
        } else {
            bitmap.topBitmap.eraseColor(Color.TRANSPARENT);
            if (bitmap.bottomBitmap != null) {
                bitmap.bottomBitmap.eraseColor(Color.TRANSPARENT);
            }
        }

        BlurBitmap finalBitmap = bitmap;

        float sX = (float) finalBitmap.topBitmap.getWidth() / (float) lastW;
        float sY = (float) (finalBitmap.topBitmap.getHeight() - TOP_CLIP_OFFSET) / (float) lastH;
        int saveCount = finalBitmap.topCanvas.save();
        finalBitmap.pixelFixOffset = 0; //todo

        finalBitmap.topCanvas.clipRect(1, 10 * sY, finalBitmap.topBitmap.getWidth(), finalBitmap.topBitmap.getHeight() - 1);
        finalBitmap.topCanvas.scale(sX, sY);
        finalBitmap.topCanvas.translate(0, 10 * sY + finalBitmap.pixelFixOffset);

        finalBitmap.topScaleX = 1f / sX;
        finalBitmap.topScaleY = 1f / sY;

        draw(finalBitmap.topCanvas); //todo
        try {
            finalBitmap.topCanvas.restoreToCount(saveCount);
        } catch (Exception e) {
            FileLog.e(e);
        }

//        if (needBlurBottom) {
//            sX = (float) finalBitmap.bottomBitmap.getWidth() / (float) lastW;
//            sY = (float) (finalBitmap.bottomBitmap.getHeight() - TOP_CLIP_OFFSET) / (float) lastH;
//            finalBitmap.needBlurBottom = true;
//            finalBitmap.bottomOffset = getBottomOffset() - lastH;
//            finalBitmap.drawnListTranslationY = getBottomOffset();
//            finalBitmap.bottomCanvas.save();
//            finalBitmap.bottomCanvas.clipRect(1, 10 * sY, finalBitmap.bottomBitmap.getWidth(), finalBitmap.bottomBitmap.getHeight() - 1);
//            finalBitmap.bottomCanvas.scale(sX, sY);
//            finalBitmap.bottomCanvas.translate(0, 10 * sY - finalBitmap.bottomOffset + finalBitmap.pixelFixOffset);
//            finalBitmap.bottomScaleX = 1f / sX;
//            finalBitmap.bottomScaleY = 1f / sY;
//
//            drawList(finalBitmap.bottomCanvas, false, null);
//            finalBitmap.bottomCanvas.restore();
//        } else {
            finalBitmap.needBlurBottom = false;
//        }


        times2 += System.currentTimeMillis() - time;
        count2++;
        if (count2 >= 20) {
            count2 = 0;
            times2 = 0;
        }

        if (blurQueue == null) {
            blurQueue = new DispatchQueue("BlurQueue");
        }
        blurBackgroundTask.radius = (int) ((int) (Math.max(6, Math.max(lastH, lastW) / 180) * 2.5f) * BlurSettingsBottomSheet.blurRadius);
        blurBackgroundTask.finalBitmap = finalBitmap;
        blurQueue.postRunnable(blurBackgroundTask);
    }


    boolean invalidateOptimized() {
        return false;
    }

//    private void drawListWithCallbacks(Canvas canvas) {
//        if (!invalidateOptimized()) {
//            drawList(canvas, top, null);
//        } else {
//            lastViews.clear();
//            lastViews.addAll(views);
//            views.clear();
//            drawList(canvas);
//            for (SizeNotifierFrameLayout.IViewWithInvalidateCallback view : lastViews) {
//                view.listenInvalidate(null);
//            }

//            for (SizeNotifierFrameLayout.IViewWithInvalidateCallback view : views) {
//                view.listenInvalidate(this::updateBlurContent);
//                //todo should invalidate the blured view or only this container
//            }
//        }
//    }

//    void drawList(Canvas blurCanvas) { //todo IMPORTANT
////        if(avatarContainer == null) return;
//        blurCanvas.save();
//        draw(blurCanvas);
//        blurCanvas.restore();
//    }

//    @Override
//    protected void dispatchDraw(Canvas canvas) {
//        super.dispatchDraw(canvas);
//        if (hasFallbackPhoto && photoDescriptionProgress != 0 && customAvatarProgress != 1f) {
//            float cy =  onlineTextView[1].getY() + onlineTextView[1].getMeasuredHeight() / 2f;
//            float size = AndroidUtilities.dp(22);
//            float x = AndroidUtilities.dp(28) - customPhotoOffset + onlineTextView[1].getX() - size;
//
//            fallbackImage.setImageCoords(x, cy - size / 2f, size, size);
//            fallbackImage.setAlpha(photoDescriptionProgress);
//            canvas.save();
//            float s = photoDescriptionProgress;
//            canvas.scale(s, s, fallbackImage.getCenterX(), fallbackImage.getCenterY());
//            fallbackImage.draw(canvas);
//            canvas.restore();
//
//            if (customAvatarProgress == 0) {
//                if (canvasButton == null) {
//                    canvasButton = new CanvasButton(this);
//                    canvasButton.setDelegate(() -> {
//                        if (customAvatarProgress != 1f) {
//                            avatarsViewPager.scrollToLastItem();
//                        }
//                    });
//                }
//                AndroidUtilities.rectTmp.set(x - AndroidUtilities.dp(4), cy - AndroidUtilities.dp(14), x + onlineTextView[2].getTextWidth() + AndroidUtilities.dp(28) * (1f - customAvatarProgress) + AndroidUtilities.dp(4), cy + AndroidUtilities.dp(14));
//                canvasButton.setRect(AndroidUtilities.rectTmp);
//                canvasButton.setRounded(true);
//                canvasButton.setColor(Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 50));
//                canvasButton.draw(canvas);
//            } else {
//                if (canvasButton != null) {
//                    canvasButton.cancelRipple();
//                }
//            }
//        }
//    }


    public void invalidateBlurredViews() {
        blurNodeInvalidated[0] = true;
        blurNodeInvalidated[1] = true;
        for (int i = 0; i < blurBehindViews.size(); i++) {
            blurBehindViews.get(i).invalidate();
        }
    }

    private class BlurBackgroundTask implements Runnable {

        int radius;
        BlurBitmap finalBitmap;

        @Override
        public void run() {
            long time = System.currentTimeMillis();

            Utilities.stackBlurBitmap(finalBitmap.topBitmap, radius);
            if (finalBitmap.needBlurBottom && finalBitmap.bottomBitmap != null) {
                Utilities.stackBlurBitmap(finalBitmap.bottomBitmap, radius);
            }
            times += System.currentTimeMillis() - time;
            count++;
            if (count > 1000) {
                FileLog.d("profile avatar blur generating average time" + (times / (float) count));
                count = 0;
                times = 0;
            }

            AndroidUtilities.runOnUIThread(() -> {
                if (!blurIsRunning) {
                    if (finalBitmap != null) {
                        finalBitmap.recycle();
                    }
                    blurGeneratingTuskIsRunning = false;
                    return;
                }
                prevBitmap = currentBitmap;
                BlurBitmap oldBitmap = currentBitmap;
//                blurPaintTop2.setShader(blurPaintTop.getShader());
//                blurPaintBottom2.setShader(blurPaintBottom.getShader());

                BitmapShader bitmapShader = new BitmapShader(finalBitmap.topBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                blurPaintTop.setShader(bitmapShader);

//                if (finalBitmap.needBlurBottom && finalBitmap.bottomBitmap != null) {
//                    bitmapShader = new BitmapShader(finalBitmap.bottomBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
//                    blurPaintBottom.setShader(bitmapShader);
//                }

                if (blurCrossfade != null) {
                    blurCrossfade.cancel();
                }
                blurCrossfadeProgress = 0;
                blurCrossfade = ValueAnimator.ofFloat(0, 1f);
                blurCrossfade.addUpdateListener(valueAnimator -> {
                    blurCrossfadeProgress = (float) valueAnimator.getAnimatedValue();
                    invalidateBlurredViews();
                });
                blurCrossfade.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        blurCrossfadeProgress = 1f;
                        unusedBitmaps.add(oldBitmap);
//                        blurPaintTop2.setShader(null);
//                        blurPaintBottom2.setShader(null);
                        invalidateBlurredViews();
                        super.onAnimationEnd(animation);
                    }
                });
                blurCrossfade.setDuration(50);
                blurCrossfade.start();
                invalidateBlurredViews();
                currentBitmap = finalBitmap;

                AndroidUtilities.runOnUIThread(() -> {
                    blurGeneratingTuskIsRunning = false;
                    startBlur();
                }, 16);
            });
        }
    }


    private static class BlurBitmap {
        public boolean needBlurBottom;
        int pixelFixOffset;
        Canvas topCanvas;
        Bitmap topBitmap;
        float topScaleX, topScaleY;
        float bottomScaleX, bottomScaleY;
        float bottomOffset;
        float drawnListTranslationY;

        Canvas bottomCanvas;
        Bitmap bottomBitmap;

        public void recycle() {
            topBitmap.recycle();
            if (bottomBitmap != null) {
                bottomBitmap.recycle();
            }
        }
    }
}
