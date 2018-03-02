package com.clj.blesample;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * site:istarvip.cn
 *
 * @author：istarvip on 2018/3/2 14:39
 * 邮箱：917017530@qq.com
 * FIXME
 */

public class MyApplication extends Application {
    public static boolean isConnecting = false;

    public static boolean BLE_ON = false;
    @Override
    public void onCreate() {
        super.onCreate();
        bindBleService();
    }

    private void bindBleService() {
        MyBleUtils.getInstance().init(this,bleListener);
        BLE_ON=MyBleUtils.getInstance().isBlueEnable();
    }

    BroadcastReceiver bleListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

        }
    };

}
