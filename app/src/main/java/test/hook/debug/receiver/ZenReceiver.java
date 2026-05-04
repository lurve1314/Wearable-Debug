package test.hook.debug.receiver;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import test.hook.debug.R;

public class ZenReceiver extends BroadcastReceiver {
    private static final String TAG = "ZenReceiver";
    public static final String ACTION_SET_ZEN = "test.hook.debug.ACTION_SET_ZEN";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive:收到唤醒广播");

        if (intent == null || intent.getAction() == null) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager获取失败");
            return;
        }
        switch (intent.getAction()) {

            case ACTION_SET_ZEN:
                //必须执行权限校验，否则直接调用会抛出SecurityException
                if (notificationManager.isNotificationPolicyAccessGranted()) {
                    try {
                        int filter = intent.getIntExtra("filter", -114514);
                        if (filter == -114514) return;
                        notificationManager.setInterruptionFilter(filter);
                        Log.d(TAG, "免打扰状态修改成功");
                        setResultCode(android.app.Activity.RESULT_OK);
                    } catch (Exception e) {
                        Log.e(TAG, "执行setInterruptionFilter时发生异常", e);
                    }
                } else {
                    Log.w(TAG, "未获取免打扰权限，操作被拒绝");
                    //注意：由于此时处于Receiver的后台执行环境，不建议在此处直接startActivity去拉起授权界面
                    //正确的做法是由AppA在Hook端判断权限并引导授权，或者在此处发送一个系统通知引导用户点击后跳转授权
                }

                break;
        }
    }
}