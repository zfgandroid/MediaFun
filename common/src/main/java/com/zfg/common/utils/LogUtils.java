package com.zfg.common.utils;

import android.util.Log;

import com.zfg.common.BuildConfig;

/**
 * 日志工具类
 * 1）release版本不打印verbose和debug级别日志
 * 2）在Application初始化时可以直接设置打印级别，低于设置值不打印，默认debug
 *
 * @author zhongfanggui
 * @version 1.0.0
 * @since 2023/5/24
 */
public class LogUtils {
    private static final String TAG = "MediaFun";
    private static boolean isDebug = BuildConfig.DEBUG;
    public static final int VERBOSE = 1;
    public static final int DEBUG = 2;
    public static final int INFO = 3;
    public static final int WARN = 4;
    public static final int ERROR = 5;
    public static int LEVEL = DEBUG;

    private LogUtils() {

    }

    public static void setLevel(int level) {
        LEVEL = level;
    }

    public static void v(String msg) {
        if (!isDebug) return;

        if (LEVEL <= VERBOSE) {
            Log.v(TAG, msg);
        }
    }

    public static void v(String tag, String msg) {
        if (!isDebug) return;

        if (LEVEL <= VERBOSE) {
            Log.v(tag, msg);
        }
    }

    public static void d(String msg) {
        if (!isDebug) return;

        if (LEVEL <= DEBUG) {
            Log.d(TAG, msg);
        }
    }

    public static void d(String tag, String msg) {
        if (!isDebug) return;

        if (LEVEL <= DEBUG) {
            Log.d(tag, msg);
        }
    }

    public static void i(String msg) {
        if (LEVEL <= INFO) {
            Log.i(TAG, msg);
        }
    }

    public static void i(String tag, String msg) {
        if (LEVEL <= INFO) {
            Log.i(tag, msg);
        }
    }

    public static void w(String msg) {
        if (LEVEL <= WARN) {
            Log.w(TAG, msg);
        }
    }

    public static void w(String tag, String msg) {
        if (LEVEL <= WARN) {
            Log.w(tag, msg);
        }
    }

    public static void e(String msg) {
        if (LEVEL <= ERROR) {
            Log.e(TAG, msg);
        }
    }

    public static void e(String tag, String msg) {
        if (LEVEL <= ERROR) {
            Log.e(tag, msg);
        }
    }
}
