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
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

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

    // 存储每一帧的数据 Vector 自增数组
    public ArrayBlockingQueue<byte[]> mFrameBytes = new ArrayBlockingQueue<>(10);

    private long pts;
    private long generateIndex = 0;
    // 超时时间，单位：微秒，1微秒=0.001毫秒 0.012秒
    private final static int TIMEOUT = 12000;
    public byte[] configByte;

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

    private boolean startMediaCodec() {
        try {
            isPrepared = true;
            mMediaCodec = MediaCodec.createByCodecName(mCodecInfo.getName());
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
            LogUtils.i("Start mediacodec");
            return true;
        } catch (NullPointerException | IOException
                | IllegalStateException | IllegalArgumentException e) {

            if (mMediaCodec != null) {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            }
        }
        return false;
    }

    private void stopMediaCodec() {
        if (mMediaCodec != null) {
            LogUtils.i("Stop mediacodec");
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }

        isPrepared = false;
    }

    public void stopEncode() {
        isStopEncode = true;
    }

    public void add(byte[] data) {
        if (mFrameBytes.size() >= 10) {
            mFrameBytes.poll();
        }
        mFrameBytes.add(data);
    }

    public synchronized void restart() {
        isPrepared = false;
//        isMuxerReady = false;
        mFrameBytes.clear();
    }

    @Override
    public void run() {
        LogUtils.i("Start MCVideoEncoder thread...");
        while (!isStopEncode) {
            // 启动或重启
            if (!isPrepared) {
                startMediaCodec();
            } else if (mFrameBytes.size() > 0) {
                byte[] bytes = mFrameBytes.poll();
                LogUtils.i("encode...");
                encodeFrame(bytes);
            }
        }

        // 关闭数据流
        if (null != mFileOutputStream) {
            try {
                mFileOutputStream.flush();
                mFileOutputStream.close();
                LogUtils.i("Stream close");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        stopMediaCodec();
        LogUtils.i("Stop MCVideoEncoder thread...");
    }

    private void encodeFrame(byte[] input) {
        try {
            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
            int inputBufferIndex = mMediaCodec.dequeueInputBuffer(10000);
            LogUtils.i("inputBufferIndex, = " + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                pts = computePresentationTime(generateIndex);
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                if (inputBuffer.remaining() >= input.length) {
                    LogUtils.i("put data");
                    inputBuffer.put(input);
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
                    generateIndex += 1;
                }
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT);
            LogUtils.i("outputBufferIndex = " + outputBufferIndex);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);
                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    configByte = new byte[bufferInfo.size];
                    configByte = outData;
                } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_SYNC_FRAME) {
                    byte[] keyframe = new byte[bufferInfo.size + configByte.length];
                    System.arraycopy(configByte, 0, keyframe, 0, configByte.length);
                    System.arraycopy(outData, 0, keyframe, configByte.length, outData.length);
                    mFileOutputStream.write(keyframe, 0, keyframe.length);
                } else {
                    mFileOutputStream.write(outData, 0, outData.length);
                }

                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT);
                LogUtils.i("编码中...");
            }

        } catch (IOException e) {
            e.printStackTrace();
            LogUtils.e("编码异常, exception = " + e);
        }
    }

    /**
     * 根据帧数生成时间戳
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / mFrameRate;
    }
}
