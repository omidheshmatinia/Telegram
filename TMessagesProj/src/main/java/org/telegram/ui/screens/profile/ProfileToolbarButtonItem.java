package org.telegram.ui.screens.profile;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.telegram.messenger.R;

public enum ProfileToolbarButtonItem {
    Message(R.drawable.filled_message, R.string.Message),
    Call(R.drawable.filled_call, R.string.Call),
    Video(R.drawable.filled_video, R.string.GroupCallCreateVideo),
    Leave(R.drawable.msg_leave, R.string.VoipGroupLeave),
    VoiceChat(R.drawable.live_stream, R.string.StartVoipChatTitle),
    Join(R.drawable.join, R.string.VoipChatJoin),
    Report(R.drawable.report, R.string.ReportBot),
    Share(R.drawable.filled_share, R.string.VoipChatShare),
    Discuss(R.drawable.filled_message, R.string.Discuss),
    Stop(R.drawable.block, R.string.Stop),
    Gift(R.drawable.filled_gift, R.string.ActionStarGift),
    AddStory(R.drawable.story, R.string.AddStory),
    LiveStream(R.drawable.live_stream, R.string.VoipChannelVoiceChat),
    Mute(R.drawable.filled_mute, R.string.cast_mute),
    UnMute(R.drawable.filled_unmute, R.string.ChatsUnmute);

    public final @DrawableRes  int icon;
    public final @StringRes  int label;

    ProfileToolbarButtonItem(int icon, int label) {
        this.icon = icon;
        this.label = label;
    }
}