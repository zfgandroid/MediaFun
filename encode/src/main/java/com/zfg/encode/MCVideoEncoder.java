package com.zfg.encode;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;

import com.zfg.common.Constants;
import com.zfg.common.utils.DateUtils;
import com.zfg.common.utils.LogUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 视频硬编码
 *
 * @author zhongfanggui
 * @version 1.0.0
 * @since 2023/5/24
 */
public class MCVideoEncoder extends Thread {

    private String mEncodeType;
    private int mRotation;
    // 分辨率
    private int mWidth;
    private int mHeight;
    // 每秒显示帧数 fps
    private int mFrameRate;
    // 视频码率
    private int mBitrate;
    // I帧间隔
    private int mGOP;

    private FileOutputStream mFileOutputStream;
    private MediaFormat mediaFormat;
    private MediaCodecInfo mCodecInfo;
    private MediaCodec mMediaCodec;

    // MediaCodec是否准备好了
    private volatile boolean isPrepared = false;
    // 是否停止编码
    private volatile boolean isStopEncode = false;

    public MCVideoEncoder(String encodeType, int rotation, int width, int height, int frameRate,
                          int bitrate, int gop) {
        mEncodeType = encodeType;
        mRotation = rotation;
        mWidth = width;
        mHeight = height;
        mFrameRate = frameRate;
        mBitrate = bitrate;
        mGOP = gop;

        createFile();

        initVideoEncoder();
    }

    private void createFile() {
        // 创建文件夹
        File dir = new File(Constants.PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 创建文件
        String fileName = DateUtils.getStringDate() + ".mp4";
        File file = new File(Constants.PATH, fileName);
        if (file.exists()) {
            file.delete();
        }
        try {
            mFileOutputStream = new FileOutputStream(file);
        } catch (Exception e) {
            LogUtils.e("createFile error = " + e);
        }
    }

    private void initVideoEncoder() {
        // 设置编码参数
        if ((mRotation == 90 || mRotation == 270)) {
            mediaFormat = MediaFormat.createVideoFormat(mEncodeType, mHeight, mWidth);
        } else {
            mediaFormat = MediaFormat.createVideoFormat(mEncodeType, mWidth, mHeight);
        }

        mCodecInfo = selectMediaCodec(mEncodeType);
        if (null == mCodecInfo) {
            LogUtils.e("codecInfo is null");
            return;
        }

        // 设置颜色格式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //COLOR_FormatYUV420Flexible 包含多个yuv格式
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        } else {
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, selectColorFormat(mCodecInfo, mEncodeType));
        }
        // 设置比特率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        // 设置帧率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        // 设置关键帧的时间
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mGOP);
    }

    private MediaCodecInfo selectMediaCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    String encoderName = codecInfo.getName();
                    LogUtils.i("encoderName = " + encoderName);
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0;
    }

    private boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    public boolean startMediaEncoder() {
        try {
            mMediaCodec = MediaCodec.createByCodecName(mCodecInfo.getName());
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
            LogUtils.i("Start mediacodec");
            isPrepared = true;
            return true;
        } catch (NullPointerException | IOException
                | IllegalStateException | IllegalArgumentException e) {

            if (mMediaCodec != null) {
                mMediaCodec.stop();
                mMediaCodec.release();
            }
        }
        return false;
    }

    @Override
    public void run() {

    }
}
