package org.telegram.ui.screens.profile;

import androidx.annotation.DrawableRes;

public class ProfileToolbarButtonItem {
    public final @DrawableRes int icon;
    public final String label;
    public ProfileToolbarButtonItem(@DrawableRes int icon, String label) {
        this.icon = icon;
        this.label = label;
    }
}
