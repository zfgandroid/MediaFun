package com.zfg.common.utils;

import java.nio.ByteBuffer;

/**
 * 图像格式转换工具类
 *
 * @author zhongfanggui
 * @version 3.5.0
 * @since 2023/5/30
 */
public class ImageFormatUtils {

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
}
