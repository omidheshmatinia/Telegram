package org.telegram.ui.screens.profile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BluredView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

public class ProfileToolbarButtonsLayout extends LinearLayout {

    private Theme.ResourcesProvider resourcesProvider;

    public ProfileToolbarButtonsLayout(Context context) {
        super(context);
        init();
    }

    public ProfileToolbarButtonsLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setClipToPadding(false);
        setClipChildren(false);
    }

    public void setResourceProvider(Theme.ResourcesProvider _resourcesProvider){
        resourcesProvider = _resourcesProvider;
    }

    public void setItems(List<ProfileToolbarButton> items) {
        removeAllViews();
        if (items == null || items.size() < 2 || items.size() > 5) return;
        int count = items.size();
        for (int i = 0; i < count; i++) {
            ProfileToolbarButton item = items.get(i);
            FrameLayout frame = new FrameLayout(getContext());
            // Add blurred background
            if(resourcesProvider != null) {
                BluredView blur = new BluredView(getContext(), this, resourcesProvider);
                frame.addView(blur, new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                // Padding as border
                int border = AndroidUtilities.dp(4);
                frame.setPadding(border, border, border, border);
            }
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

    public void handleAnimation(float progress){
        ViewGroup.LayoutParams params = getLayoutParams();
        params.height = AndroidUtilities.lerp(0 , AndroidUtilities.dp(80), progress);
        setAlpha(progress);
    }
}