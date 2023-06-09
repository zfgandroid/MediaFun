package com.zfg.common.utils;

import android.media.Image;

import java.nio.ByteBuffer;

/**
 * 图像格式转换工具类
 *
 * @author zhongfanggui
 * @version 3.5.0
 * @since 2023/5/30
 */
public class ImageFormatUtils {

    static {
        System.loadLibrary("mediafun");
    }

    public static native String stringFromJNI();

    /**
     * YUV_420_888转NV21
     *
     * @param yPlane y分量
     * @param uPlane u分量
     * @param vPlane v分量
     * @param width  宽
     * @param height 高
     * @return ImageFormat.NV21
     */
    public static byte[] yuv420888ToNV21(ByteBuffer yPlane, ByteBuffer uPlane, ByteBuffer vPlane,
                                         int width, int height) {
        int frameSize = width * height * 3 / 2;
        byte[] nv21 = new byte[frameSize];
        int len = yPlane.capacity();
        yPlane.get(nv21, 0, len);
        vPlane.get(nv21, len, vPlane.capacity());
        byte lastValue = uPlane.get(uPlane.capacity() - 1);
        nv21[frameSize - 1] = lastValue;
        return nv21;
    }

    /**
     * NV21转NV12
     *
     * @param nv21   ImageFormat.NV21
     * @param nv12   ImageFormat.NV12
     * @param width  宽
     * @param height 高
     */
    public static void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int frameSize = width * height;
        int i, j;
        System.arraycopy(nv21, 0, nv12, 0, frameSize);
        for (i = 0; i < frameSize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j - 1] = nv21[j + frameSize];
        }
        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j] = nv21[j + frameSize - 1];
        }
    }

    /**
     * 检查 YUV_420_888 图像的 UV 平面缓冲区是否为 NV21 格式。
     * 1.YYYYYYYYYYYYYYYYY(...)UUUUU(...)VVVVV(...) 平面存储，常用
     * <p>
     * 2.YYYYYYYYYYYYYYYYY(...)VUVUVUVUVUVU(...) 交叉存储，不常用
     */
    public static boolean areUVPlanesNV21(ByteBuffer uPlane, ByteBuffer vPlane,
                                          int width, int height) {
        int imageSize = width * height;

        // 备份缓冲区属性。
        int vBufferPosition = vPlane.position();
        int uBufferLimit = uPlane.limit();

        // 将 V 缓冲区推进 1 个字节，因为 U 缓冲区将不包含第一个 V 值。
        vPlane.position(vBufferPosition + 1);
        // 切掉 U 缓冲区的最后一个字节，因为 V 缓冲区将不包含最后一个 U 值。
        uPlane.limit(uBufferLimit - 1);

        // 检查缓冲区是否相等并具有预期的元素数量。
        boolean areNV21 = (vPlane.remaining() == (2 * imageSize / 4 - 2))
                && (vPlane.compareTo(uPlane) == 0);

        // 将缓冲区恢复到初始状态。
        vPlane.position(vBufferPosition);
        uPlane.limit(uBufferLimit);

        return areNV21;
    }
}
