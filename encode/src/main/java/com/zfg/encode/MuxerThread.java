package com.zfg.encode;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Size;
import android.view.Surface;

import com.zfg.common.Constants;
import com.zfg.common.utils.DateUtils;
import com.zfg.common.utils.LogUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

/**
 * 音视频混合类
 *
 * @author zhongfanggui
 * @version 3.5.0
 * @since 2023/6/15
 */
public class MuxerThread extends Thread {

    // 编码相关参数
    public static final String MIME_TYPE = "video/avc"; // H.264
    public static final int ROTATION = Surface.ROTATION_0;
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    public static final int FRAME_RATE = 25;
    public static final int GOP = 10;
    private static final int COMPRESS_RATIO = 256;
    public static final int BIT_RATE = WIDTH * HEIGHT * 3 * 8 * FRAME_RATE / COMPRESS_RATIO;
    public static Size SIZE = new Size(WIDTH, HEIGHT);

    public static final int TRACK_VIDEO = 0;
    public static final int TRACK_AUDIO = 1;
    private final Object lock = new Object();

    private static MuxerThread muxerThread;
    private MCAudioEncoder mAudioThread;
    private MCVideoEncoder mVideoThread;
    private Vector<MuxerData> muxerDataList;
    private MediaMuxer mediaMuxer;

    private volatile boolean isVideoTrackAdd;
    private volatile boolean isAudioTrackAdd;
    private volatile boolean isExit = false;

    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;

    private MuxerThread() {

    }

    public static void startMuxer() {
        if (null == muxerThread) {
            synchronized (MuxerThread.class) {
                if (null == muxerThread) {
                    muxerThread = new MuxerThread();
                    muxerThread.start();
                }
            }
        }
    }

    public static void stopMuxer() {
        if (null != muxerThread) {
            muxerThread.exit();
            try {
                muxerThread.join();
            } catch (InterruptedException e) {
                LogUtils.e("stopMuxer interruptedException");
            }
        }
        muxerThread = null;
    }

    private void exit() {
        if (mVideoThread != null) {
            mVideoThread.stopEncodeVideo();
            try {
                mVideoThread.join();
            } catch (InterruptedException e) {
                LogUtils.e("exit video interruptedException");
            }
        }
        if (mAudioThread != null) {
            mAudioThread.stopEncodeAudio();
            try {
                mAudioThread.join();
            } catch (InterruptedException e) {
                LogUtils.e("exit audio interruptedException");
            }
        }

        isExit = true;

        synchronized (lock) {
            lock.notify();
        }
    }

    private void initMuxer() {
        muxerDataList = new Vector<>();
        mAudioThread = new MCAudioEncoder((new WeakReference<>(this)));
        mVideoThread = new MCVideoEncoder(MIME_TYPE, ROTATION, WIDTH, HEIGHT,
                FRAME_RATE, BIT_RATE, GOP, new WeakReference<>(this));
        mAudioThread.start();
        mVideoThread.start();
        try {
            readyStart();
        } catch (IOException e) {
            LogUtils.e("initMuxer IOException = " + e);
        }
    }

    private void readyStart() throws IOException {
        isExit = false;
        isVideoTrackAdd = false;
        isAudioTrackAdd = false;
        muxerDataList.clear();

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

        String filePath = file.getAbsolutePath();
        mediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        if (mAudioThread != null) {
            mAudioThread.setMuxerReady(true);
        }
        if (mVideoThread != null) {
            mVideoThread.setMuxerReady(true);
        }
        LogUtils.i("readyStart filePath  = " + filePath);
    }

    public boolean isMuxerStart() {
        return isAudioTrackAdd && isVideoTrackAdd;
    }

    public static void addVideoPreviewData(byte[] data) {
        if (muxerThread != null) {
            muxerThread.addVideoData(data);
        }
    }

    private void addVideoData(byte[] data) {
        if (mVideoThread != null) {
            mVideoThread.add(data);
        }
    }

    public void addMuxerData(MuxerData data) {
        if (!isMuxerStart()) {
            return;
        }

        muxerDataList.add(data);

        synchronized (lock) {
            lock.notify();
        }
    }

    public synchronized void addMediaTrack(int index, MediaFormat mediaFormat) {
        if (isMuxerStart()) {
            return;
        }

        // 已添加
        if ((index == TRACK_AUDIO && isAudioTrackAdd) || (index == TRACK_VIDEO && isVideoTrackAdd)) {
            return;
        }

        if (mediaMuxer != null) {
            int track;
            try {
                track = mediaMuxer.addTrack(mediaFormat);
            } catch (Exception e) {
                LogUtils.e("addMediaTrack exception = " + e);
                return;
            }

            if (index == TRACK_VIDEO) {
                mVideoTrackIndex = track;
                isVideoTrackAdd = true;
                LogUtils.i("添加视频轨完成");
            } else {
                mAudioTrackIndex = track;
                isAudioTrackAdd = true;
                LogUtils.i("添加音轨完成");
            }

            mediaMuxerStart();
        }
    }

    private void mediaMuxerStart() {
        synchronized (lock) {
            if (isMuxerStart()) {
                mediaMuxer.start();
                LogUtils.i("mediaMuxerStart");
                lock.notify();
            }
        }
    }

    private void mediaMuxerStop() {
        if (mediaMuxer != null) {
            try {
                mediaMuxer.stop();
            } catch (Exception e) {
                LogUtils.e("mediaMuxerStop stop exception = " + e);
            }
            try {
                mediaMuxer.release();
            } catch (Exception e) {
                LogUtils.e("mediaMuxerStop release exception = " + e);

            }
            mediaMuxer = null;
        }
    }

    @Override
    public void run() {
        LogUtils.i("MuxerThread start");

        initMuxer();

        while (!isExit) {
            if (isMuxerStart()) {
                if (muxerDataList.isEmpty()) {
                    synchronized (lock) {
                        try {
                            LogUtils.i("等待混合数据...");
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    MuxerData data = muxerDataList.remove(0);
                    int track;
                    if (data.trackIndex == TRACK_VIDEO) {
                        track = mVideoTrackIndex;
                    } else {
                        track = mAudioTrackIndex;
                    }
                    LogUtils.i("写入混合数据 size = " + data.bufferInfo.size);
                    try {
                        mediaMuxer.writeSampleData(track, data.byteBuf, data.bufferInfo);
                    } catch (Exception e) {
                        LogUtils.e("写入混合数据失败, exception = " + e);
                    }
                }
            } else {
                synchronized (lock) {
                    try {
                        LogUtils.i("等待音视轨添加...");
                        lock.wait();
                    } catch (InterruptedException e) {
                        LogUtils.e("addMediaTrack exception = " + e);
                    }
                }
            }
        }

        mediaMuxerStop();
        LogUtils.i("MuxerThread exit");
    }

    public static class MuxerData {
        int trackIndex;
        ByteBuffer byteBuf;
        MediaCodec.BufferInfo bufferInfo;

        public MuxerData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
            this.trackIndex = trackIndex;
            this.byteBuf = byteBuf;
            this.bufferInfo = bufferInfo;
            byteBuf.flip();
        }
    }
}
