package org.telegram.ui.screens.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BluredView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.List;

public class ProfileToolbarButtonsLayout extends LinearLayout {

    private SizeNotifierFrameLayout parentNotifier;
    private Theme.ResourcesProvider resourceProvider;


    public ProfileToolbarButtonsLayout(Context context, SizeNotifierFrameLayout _parentNotifier,Theme.ResourcesProvider _resourcesProvider) {
        super(context);
        parentNotifier = _parentNotifier;
        resourceProvider= _resourcesProvider;

        setOrientation(HORIZONTAL);
        setClipToPadding(false);
        setClipChildren(false);
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider);
        setBackgroundColor(0XFFFFFFFF);
    }

    public void setItems(List<ProfileToolbarButtonItem> items) {
        removeAllViews();
        int count = items.size();
        for (int i = 0; i < count; i++) {
            ProfileToolbarButtonItem item = items.get(i);
            FrameLayout frame = new FrameLayout(getContext());
            // Vertical layout for icon and text
            LinearLayout vertical = new LinearLayout(getContext());
            vertical.setOrientation(VERTICAL);
            vertical.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams vParams = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            vertical.setLayoutParams(vParams);
            // SVG icon
            ImageView icon = new ImageView(getContext());
            icon.setImageResource(item.icon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(32), AndroidUtilities.dp(32));
            iconParams.gravity = Gravity.CENTER_HORIZONTAL;
            vertical.addView(icon, iconParams);
            // Text label
            SimpleTextView label = new SimpleTextView(getContext());
            label.setText(item.label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(16);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            textParams.gravity = Gravity.CENTER_HORIZONTAL;
            textParams.topMargin = AndroidUtilities.dp(4);
            vertical.addView(label, textParams);
            // Add vertical layout to frame
            frame.addView(vertical);
            // Add frame to this layout
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            params.leftMargin = i == 0 ? 0 : AndroidUtilities.dp(4);
            addView(frame, params);
        }
    }


    private Paint paint = new Paint();
    private Rect blurBounds = new Rect();

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
//        int color = Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider);
//        paint.setColor(color);
        blurBounds.set(0, 0, getWidth(), getHeight());
        parentNotifier.drawBlurRect(canvas, 0, blurBounds, paint, true);
    }

    public void handleAnimation(float progress){
        ViewGroup.LayoutParams params = getLayoutParams();
        params.height = AndroidUtilities.lerp(0 , AndroidUtilities.dp(80), progress);
        setAlpha(progress);
    }
}