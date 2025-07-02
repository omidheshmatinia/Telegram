package org.telegram.ui.screens.profile;

import org.telegram.tgnet.TLRPC;

import org.telegram.messenger.AndroidUtilities;

public class ProfileToolbarHelper {
    public static int MAX_PROFILE_IMAGE_CIRCLE_SIZE = AndroidUtilities.dp(128);
    public static int MIN_PROFILE_IMAGE_CIRCLE_SIZE = AndroidUtilities.dp(64);

    public boolean isVideoCallItemVisible = false;
    public boolean isCallItemVisible = false;

    public void checkVideoCallAndCallVisibility(TLRPC.UserFull userInfo){
        if (userInfo != null && userInfo.phone_calls_available) {
            isCallItemVisible = true;
//            isVideoCallItemVisible = Build.VERSION.SDK_INT >= 18 && userInfo.video_calls_available; //todo we can remove the sdk check
            isVideoCallItemVisible = userInfo.video_calls_available;
        }
    }
}
