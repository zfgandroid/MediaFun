package com.zfg.common.utils;

import android.text.TextUtils;

import com.zfg.common.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 保存视频流工具类
 *
 * @author zhongfanggui
 * @version 3.5.0
 * @since 2023/5/30
 */
public class FileUtils {
    private static FileUtils instance;
    private long beginTime;
    private long delayTime;

    private FileUtils(int delayTime) {
        beginTime = System.currentTimeMillis();
        this.delayTime = delayTime;
    }

    /**
     * 获取实例
     *
     * @param delayTime 录像时间，单位毫秒
     * @return FileUtils
     */
    public static FileUtils getInstance(int delayTime) {
        if (null == instance) {
            synchronized (FileUtils.class) {
                if (null == instance) {
                    instance = new FileUtils(delayTime);
                }
            }
        }

        return instance;
    }

    /**
     * 判断是否超时了
     *
     * @return true 超时，false 未超时
     */
    private boolean overTime() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - beginTime > delayTime) {
            LogUtils.i("over time, currentTime = " + currentTime + ", beginTime = " + beginTime
                    + ", delayTime = " + delayTime);
            return true;
        }

        return false;
    }

    private String createFile(String fileName) {
        // 创建文件夹
        File dir = new File(Constants.PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 创建文件
        File file = new File(Constants.PATH, fileName);
        try {
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e("createFile exception = " + e);
        }

        return null;
    }

    /**
     * 保存视频流数据
     *
     * @param array    h264或yuv
     * @param fileName 如 abc.h264 / abc.yuv
     */
    public void saveVideoStream(byte[] array, String fileName) {
        FileOutputStream fileOutputStream = null;
        if (!overTime()) {
            try {
                String path = createFile(fileName);
                if (TextUtils.isEmpty(path)) {
                    return;
                }
                // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
                fileOutputStream = new FileOutputStream(path, true);
                fileOutputStream.write(array);
                fileOutputStream.write('\n');
            } catch (IOException e) {
                LogUtils.e("saveVideoStream write exception = " + e.getMessage());
            } finally {
                try {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                } catch (IOException e) {
                    LogUtils.e("saveVideoStream close exception 1 = " + e.getMessage());
                }
            }
        } else {
            LogUtils.i("saveVideoStream over time");
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                    LogUtils.i("over time close");
                }
            } catch (IOException e) {
                LogUtils.e("saveVideoStream close exception 2 = " + e.getMessage());
            }
        }
    }
}
