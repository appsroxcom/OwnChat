package com.example.ownchat;

import android.content.Context;
import android.text.TextUtils;

public class CommonUtil {

    private static final String TAG = "CommonUtil";

    public static final long REFRESH_INTERVAL = 1000*30;//30s
    public static final int USERS_LIMIT = 100;
    public static final int CHATS_LIMIT = 50;
    public static final int MESSAGES_LIMIT = 10;

    public static boolean validateText(Context context, String text) {
        return !TextUtils.isEmpty(text);
    }

}
