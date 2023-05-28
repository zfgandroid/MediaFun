package com.zfg.encode;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;

import com.zfg.common.Constants;
import com.zfg.common.utils.DateUtil;
import com.zfg.common.utils.LogUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.core.app.ActivityCompat;

/**
 * 音频硬编码
 *
 * @author zhongfanggui
 * @version 1.0.0
 * @since 2023/5/24
 */
public class MCAudioEncoder extends Thread {

    /**
     * AAC格式
     */
    private final String MINE_TYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC;

    /**
     * 比特率（码率，即编码器每秒输出的数据大小）
     */
    private static final int BIT_RATE = 128 * 1024;

    /**
     * 采样率
     */
    private static final int SAMPLE_RATE_HZ = 44100;

    /**
     * 声道数
     */
    private static final int CHANNEL_COUNT = 1;

    /**
     * 缓存大小
     */
    private static final int MAX_BUFFER_SIZE = 10 * 1024;

    /**
     * 录音声道数，CHANNEL_IN_MONO and CHANNEL_IN_STEREO. 其中CHANNEL_IN_MONO是可以保证在所有设备能够使用的。
     */
    private static final int CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    /**
     * 返回的音频数据的格式，ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, and ENCODING_PCM_FLOAT.
     */
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private Context mContext;
    private MediaCodec mMediaCodec;
    private MediaFormat mediaFormat;
    private MediaCodec.BufferInfo mBufferInfo;
    private FileOutputStream mFileOutputStream;
    private AudioRecord mAudioRecord;
    private int minBufferSize;

    // MediaCodec和AudioRecord是否准备好了
    private volatile boolean isPrepared = false;
    // 是否停止编码
    private volatile boolean isStopEncode = false;

    public MCAudioEncoder(Context context) {
        this.mContext = context;
        createFile();
        initEncoder();
    }

    private void createFile() {
        // 创建文件夹
        File dir = new File(Constants.PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 创建文件
        String fileName = DateUtil.getStringDate() + ".aac";
        File file = new File(Constants.PATH, fileName);
        if (file.exists()) {
            file.delete();
        }
        try {
            mFileOutputStream = new FileOutputStream(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initEncoder() {
        // 设置编码参数
        mediaFormat = MediaFormat.createAudioFormat(MINE_TYPE_AAC, SAMPLE_RATE_HZ, CHANNEL_COUNT);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_BUFFER_SIZE);

        // 解码后保存数据的类型，包含每一个buffer的元数据信息
        mBufferInfo = new MediaCodec.BufferInfo();
    }

    private boolean startMediaCodec() {
        if (null != mMediaCodec) {
            LogUtils.i("MediaCodec is not null");
            stopMediaCodec();
        }

        // 根据类型实例化一个编码器
        try {
            mMediaCodec = MediaCodec.createEncoderByType(MINE_TYPE_AAC);
        } catch (IOException e) {
            LogUtils.e("createEncoderByType exception = " + e);
        }

        if (mMediaCodec == null) {
            LogUtils.e("Create media encoder failed");
            return false;
        }

        // MediaCodec.CONFIGURE_FLAG_ENCODE 表示需要配置一个编码器，而不是解码器
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        LogUtils.i("Start mediacodec");

        // 开始录音
        return startRecord();
    }

    private boolean startRecord() {
        // 先检查是否有录音权限
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            LogUtils.e("initAudioRecord no record permission");
            return false;
        }

        stopRecord();

        // 创建AudioRecord对象所需的最小缓冲区大小
        minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_IN_CONFIG,
                AUDIO_FORMAT);
        // 创建AudioRecord对象
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_HZ,
                CHANNEL_IN_CONFIG, AUDIO_FORMAT, minBufferSize);
        isPrepared = true;
        mAudioRecord.startRecording();
        LogUtils.i("Start record");
        return true;
    }

    private void stopRecord() {
        if (mAudioRecord != null) {
            LogUtils.i("Stop record");
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                LogUtils.e("stopRecord interrupted exception");
            }
        }
    }

    private void stopMediaCodec() {

        stopRecord();

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

    public synchronized void setRestart() {
        isPrepared = false;
        //isMuxerReady = false;
    }

    @Override
    public void run() {
        LogUtils.i("Start MCAudioEncoder thread...");
        // 初始化一个buffer，存放录音数据
        byte[] bufferData = new byte[minBufferSize];
        // 获取到的录音大小
        int readBytes;
        while (!isStopEncode) {
            // 启动或重启
            if (!isPrepared) {
                LogUtils.i("startMediaCodec run...");
                startMediaCodec();
            } else if (null != mAudioRecord) {
                readBytes = mAudioRecord.read(bufferData, 0, minBufferSize);
                LogUtils.i("read record data run...readBytes = " + readBytes);
                // 如果读取音频数据没有出现错误，就将数据写入到文件
                if (AudioRecord.ERROR_INVALID_OPERATION != readBytes) {
                    // 将PCM编码成AAC
                    encodeData(bufferData, readBytes);
                }
            }
        }

        if (null != mFileOutputStream) {
            try {
                mFileOutputStream.close();
                LogUtils.i("停止写入");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        stopMediaCodec();
        LogUtils.i("Stop MCAudioEncoder thread...");
    }

    private void encodeData(byte[] bufferData, int readBytes) {
        // dequeueInputBuffer（time）需要传入一个时间值，-1表示一直等待，0表示不等待有可能会丢帧，其他表示等待多少毫秒
        // 获取输入缓存的index
        int inputIndex = mMediaCodec.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer inputByteBuffer = mMediaCodec.getInputBuffer(inputIndex);
            inputByteBuffer.clear();
            // 添加数据
            inputByteBuffer.put(bufferData);
            // 限制ByteBuffer的访问长度
            inputByteBuffer.limit(bufferData.length);
            // 把输入缓存塞回去给MediaCodec
            mMediaCodec.queueInputBuffer(inputIndex, 0, bufferData.length, 0, 0);
        } else {
            LogUtils.e("encodeData error inputIndex = " + inputIndex + ", readBytes = " + readBytes);
        }

        // 获取输出缓存的index
        int outputIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);
        while (outputIndex >= 0) {
            // 获取缓存信息的长度
            int byteBufSize = mBufferInfo.size;
            // 添加ADTS头部后的长度
            int bytePacketSize = byteBufSize + 7;

            ByteBuffer outByteBuffer = mMediaCodec.getOutputBuffer(outputIndex);
            outByteBuffer.position(mBufferInfo.offset);
            outByteBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

            byte[] targetByte = new byte[bytePacketSize];
            // 添加ADTS头部
            addADTStoPacket(targetByte, bytePacketSize);
            // 将编码得到的AAC数据 取出到byte[]中 偏移量offset=7
            outByteBuffer.get(targetByte, 7, byteBufSize);

            outByteBuffer.position(mBufferInfo.offset);

            try {
                mFileOutputStream.write(targetByte);
                LogUtils.i("save data");
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 释放
            mMediaCodec.releaseOutputBuffer(outputIndex, false);
            outputIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);
        }
    }

    /**
     * 给编码出的aac裸流添加adts头字段
     *
     * @param packet    要空出前7个字节，否则会搞乱数据
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}