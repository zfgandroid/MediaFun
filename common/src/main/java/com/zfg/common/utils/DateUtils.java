package com.zfg.common.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author zhongfanggui
 * @version 1.0.0
 * @since 2023/5/28
 */
public class DateUtils {

    private static final String LONG_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static String getStringDate() {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat(LONG_FORMAT, Locale.getDefault());
        return formatter.format(date);
    }
}
