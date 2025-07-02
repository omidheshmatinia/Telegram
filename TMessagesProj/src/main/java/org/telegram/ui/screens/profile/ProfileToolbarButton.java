package org.telegram.ui.screens.profile;

import androidx.annotation.DrawableRes;

public class ProfileToolbarButton {
    public final @DrawableRes int icon;
    public final String label;
    public ProfileToolbarButton( @DrawableRes int icon, String label) {
        this.icon = icon;
        this.label = label;
    }
}
