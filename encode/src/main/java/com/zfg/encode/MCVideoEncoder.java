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
import java.lang.ref.WeakReference;
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
    private WeakReference<MuxerThread> muxerThread;

    private final Object lock = new Object();
    private FileOutputStream mFileOutputStream;
    private MediaFormat mediaFormat;
    private MediaCodecInfo mCodecInfo;
    private MediaCodec mMediaCodec;
    private MediaCodec.BufferInfo mBufferInfo;

    // MediaCodec是否准备好了
    private volatile boolean isPrepared = false;
    // 是否停止编码
    private volatile boolean isExit = false;
    // 混合器是否准备好
    private volatile boolean isMuxerReady = false;

    // 存储每一帧的数据 Vector 自增数组
    public ArrayBlockingQueue<byte[]> mFrameBytes = new ArrayBlockingQueue<>(10);

    private long pts;
    private long generateIndex = 0;
    // 超时时间，单位：微秒，1微秒=0.001毫秒 0.012秒
    private final static int TIMEOUT = 12000;
    public byte[] configByte;
    // 是否单独保存H264文件
    private boolean isSaveH264;

    public MCVideoEncoder(String encodeType, int rotation, int width, int height, int frameRate,
                          int bitrate, int gop, WeakReference<MuxerThread> muxerThread) {
        mEncodeType = encodeType;
        mRotation = rotation;
        mWidth = width;
        mHeight = height;
        mFrameRate = frameRate;
        mBitrate = bitrate;
        mGOP = gop;
        this.muxerThread = muxerThread;

        if (isSaveH264) {
            createFile();
        }

        initVideoEncoder();
    }

    private void createFile() {
        // 创建文件夹
        File dir = new File(Constants.PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 创建文件
        String fileName = DateUtils.getStringDate() + ".h264";
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
        mBufferInfo = new MediaCodec.BufferInfo();
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

    public void stopEncodeVideo() {
        isExit = true;
    }

    public void add(byte[] data) {
        if (null != mFrameBytes && isMuxerReady) {
            if (mFrameBytes.size() >= 10) {
                mFrameBytes.poll();
            }
            mFrameBytes.add(data);
        }
    }

    public synchronized void restart() {
        isPrepared = false;
        isMuxerReady = false;
        mFrameBytes.clear();
    }

    public void setMuxerReady(boolean muxerReady) {
        synchronized (lock) {
            LogUtils.i("Audio setMuxerReady: " + muxerReady);
            isMuxerReady = muxerReady;
            lock.notifyAll();
        }
    }

    @Override
    public void run() {
        LogUtils.i("Start MCVideoEncoder thread...");
        while (!isExit) {

            if (!isMuxerReady) {
                synchronized (lock) {
                    try {
                        LogUtils.i("Video wait muxer...");
                        lock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }

            if (isMuxerReady && !isPrepared) {
                startMediaCodec();
            } else if (mFrameBytes.size() > 0) {
                byte[] bytes = mFrameBytes.poll();
                LogUtils.i("encode...");
                encodeFrame(bytes);
            }
        }

        if (isSaveH264 && null != mFileOutputStream) {
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
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
//            pts = computePresentationTime(generateIndex);
            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            // inputBuffer.remaining()要大于或等于input.length否则报错
            // inputBuffer.remaining()大小与编码时设置的参数有关，如宽高和帧率等
            inputBuffer.put(input);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length,
                    System.nanoTime() / 1000, 0);
//            generateIndex += 1;
        }

        MuxerThread muxer = muxerThread.get();
        if (muxer == null) {
            LogUtils.e("MuxerThread is null");
            return;
        }
        MediaFormat format = mMediaCodec.getOutputFormat();
        muxer.addMediaTrack(MuxerThread.TRACK_VIDEO, format);

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT);
        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
            if (mBufferInfo.size != 0) {

                // adjust the ByteBuffer values to match BufferInfo (not needed?)
                outputBuffer.position(mBufferInfo.offset);
                outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                if (muxer.isMuxerStart()) {
                    muxer.addMuxerData(new MuxerThread.MuxerData(MuxerThread.TRACK_VIDEO,
                            outputBuffer, mBufferInfo));
                }
            }

            // 单独保存编码后的文件
            if (isSaveH264 && null != mFileOutputStream) {
                byte[] outData = new byte[mBufferInfo.size];
                outputBuffer.get(outData);
                try {
                    mFileOutputStream.write(outData, 0, outData.length);
                } catch (IOException e) {
                    LogUtils.e("Save h264 exception = " + e);
                }
            }

            // 释放
            mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT);
            LogUtils.i("编码中...");
        }

    }

    /**
     * 根据帧数生成时间戳
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / mFrameRate;
    }
}
