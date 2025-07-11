package org.telegram.ui.screens.profile;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.telegram.messenger.R;

public enum ProfileToolbarButtonItem {
    Message(R.drawable.filled_message, R.string.Message),
    Call(R.drawable.filled_call, R.string.Call),
    Video(R.drawable.filled_video, R.string.GroupCallCreateVideo),
    UnMute(R.drawable.filled_unmute, R.string.ChatsUnmute);

    public final @DrawableRes  int icon;
    public final @StringRes  int label;

    ProfileToolbarButtonItem(int icon, int label) {
        this.icon = icon;
        this.label = label;
    }
}