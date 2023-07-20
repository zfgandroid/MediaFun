package com.zfg.mediafun;

import android.app.Application;

import com.zfg.common.utils.LogUtils;

/**
 * @author zhongfanggui
 * @version 1.0.0
 * @since 2023/5/25
 */
public class MediaFunApplication extends Application {

    private static Application mApplication;

    @Override
    public void onCreate() {
        super.onCreate();

        mApplication = this;

        if (BuildConfig.DEBUG) {
            LogUtils.setLevel(LogUtils.DEBUG);
        } else {
            LogUtils.setLevel(LogUtils.INFO);
        }
        LogUtils.i("Show log...");
    }

    public static Application getApplication() {
        return mApplication;
    }
}
