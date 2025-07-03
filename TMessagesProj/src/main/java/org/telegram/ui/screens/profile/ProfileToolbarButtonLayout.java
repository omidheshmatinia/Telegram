package org.telegram.ui.screens.profile;

import static androidx.recyclerview.widget.LinearLayoutManager.VERTICAL;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.BlurredFrameLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

@SuppressLint("ViewConstructor")
public class ProfileToolbarButtonLayout extends BlurredFrameLayout {
    private final ProfileToolbarButtonItem buttonInfo;
    private final int CORNER_RADIUS = AndroidUtilities.dp(12);
    private final int ICON_SIZE = AndroidUtilities.dp(32);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect blurBounds = new Rect();

    private SimpleTextView label;
    private ImageView icon;

    public ProfileToolbarButtonLayout(@NonNull Context context, SizeNotifierFrameLayout sizeNotifierFrameLayout, ProfileToolbarButtonItem item) {
        super(context, sizeNotifierFrameLayout);
        setClipChildren(false);
        setClipToPadding(false);
        backgroundPaint.setColor(0x50ffffff);
        this.buttonInfo = item;

        View infoLayout = createLayout();
        int border = AndroidUtilities.dp(4);
        setPadding(border, border, border, border);
        addView(infoLayout);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        blurBounds.set(0, 0, w, h);
        canvas.save();
        canvas.clipRect(blurBounds);
        canvas.clipPath(createRoundedRectPath(0, 0, w, h, CORNER_RADIUS));
        sizeNotifierFrameLayout.drawBlurRect(canvas, getY(), blurBounds, backgroundPaint, false);
        canvas.restore();
        super.dispatchDraw(canvas);
    }

    public static Path createRoundedRectPath(float left, float top, float right, float bottom, int radius) {
        Path path = new Path();
        RectF rect = new RectF(left, top, right, bottom);
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        path.close();
        return path;
    }

    private LinearLayout createLayout() {
        LinearLayout vertical = new LinearLayout(getContext());
        vertical.setOrientation(VERTICAL);
        vertical.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams vParams = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        vParams.topMargin = AndroidUtilities.dp(6);
        vParams.bottomMargin = AndroidUtilities.dp(6);
        vertical.setLayoutParams(vParams);
        icon = new ImageView(getContext());
        icon.setImageResource(buttonInfo.icon);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(ICON_SIZE, ICON_SIZE);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        vertical.addView(icon, iconParams);
        label = new SimpleTextView(getContext());
        label.setText(buttonInfo.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        label.setMaxLines(1);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        textParams.gravity = Gravity.CENTER_HORIZONTAL;
        textParams.topMargin = AndroidUtilities.dp(4);
        vertical.addView(label, textParams);
        return vertical;
    }

    public void handleAnimation(float progress) {
        if(label != null && icon != null){
            icon.setScaleX(progress);
            icon.setScaleY(progress);
            label.setScaleX(progress);
            label.setScaleY(progress);
        }
    }
}