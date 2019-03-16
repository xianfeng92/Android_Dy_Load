package com.xforg.android_dy_load;

import android.app.Application;

import com.xforg.android_dy_load.util.HookUtil;


/**
 * Created By apple on 2019/3/16
 * github: https://github.com/xianfeng92
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        HookUtil hookUtil = new HookUtil(ProxyActivity.class,this);
        hookUtil.hookSystemHandler();
        hookUtil.hookAMS();
    }
}
