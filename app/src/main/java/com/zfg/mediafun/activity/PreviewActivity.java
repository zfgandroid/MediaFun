package com.zfg.mediafun.activity;

import static com.zfg.encode.MuxerThread.BIT_RATE;
import static com.zfg.encode.MuxerThread.FRAME_RATE;
import static com.zfg.encode.MuxerThread.GOP;
import static com.zfg.encode.MuxerThread.HEIGHT;
import static com.zfg.encode.MuxerThread.MIME_TYPE;
import static com.zfg.encode.MuxerThread.ROTATION;
import static com.zfg.encode.MuxerThread.SIZE;
import static com.zfg.encode.MuxerThread.WIDTH;

import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.zfg.common.Constants;
import com.zfg.common.utils.DateUtils;
import com.zfg.common.utils.ImageFormatUtils;
import com.zfg.common.utils.LogUtils;
import com.zfg.encode.MCVideoEncoder;
import com.zfg.encode.MuxerThread;
import com.zfg.mediafun.R;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用CameraX预览并获取数据
 *
 * @author zhongfanggui
 * @version 1.0.0
 * @since 2023/5/30
 */
public class PreviewActivity extends BaseActivity {


    private Button mEncodeBtn;
    private PreviewView mPreviewView;
    private ListenableFuture<ProcessCameraProvider> mCameraProviderFuture;
    private ImageCapture mImageCapture;
    private ImageAnalysis mImageAnalyzer;
    // 默认选择后置摄像头
    private int mFacing = CameraSelector.LENS_FACING_BACK;
    private final ExecutorService mCameraExecutor = Executors.newSingleThreadExecutor();

    private MCVideoEncoder mcVideoEncoder;
    private boolean isStartEncode;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_preview);

        mEncodeBtn = findViewById(R.id.btn_encode);
        mPreviewView = findViewById(R.id.preview);

        initVideoEncoder();

        startCamera();
    }

    private void initVideoEncoder() {
//        mcVideoEncoder = new MCVideoEncoder(MIME_TYPE, ROTATION, WIDTH, HEIGHT,
//                FRAME_RATE, BIT_RATE, GOP);
    }

    private void startCamera() {
        mCameraProviderFuture = ProcessCameraProvider.getInstance(this);
        // 检查ProcessCameraProvider可用性
        mCameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = mCameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        // 预览
        Preview preview = new Preview.Builder().build();
        // 选择摄像头，默认用后置摄像头
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(mFacing)
                .build();

        // 创建图片的capture
        mImageCapture = new ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build();
        // 设置预览控件
        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        // 图像分析
        mImageAnalyzer = new ImageAnalysis.Builder()
                .setTargetResolution(SIZE)  // 设置分辨率
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER) // 阻塞模式
                .setTargetRotation(Surface.ROTATION_90)
                .build();

        byte[] nv12 = new byte[SIZE.getWidth() * SIZE.getHeight() * 3 / 2];

        mImageAnalyzer.setAnalyzer(mCameraExecutor, image -> {
            // CameraX默认出图格式：YUV_420_888  YYYY UV VU，数据分别在image.getPlanes()[0]，
            // image.getPlanes()[1]，image.getPlanes()[2]中
            ByteBuffer yPlane = image.getPlanes()[0].getBuffer();
            ByteBuffer uPlane = image.getPlanes()[1].getBuffer();
            ByteBuffer vPlane = image.getPlanes()[2].getBuffer();

            // 图片分析得到的数据是不会自动处理旋转的，所以我们需要再处理旋转角度，
            // setTargetRotation(Surface.ROTATION_90)是目标角度，不代表输出数据角度。
            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            //  格式 0x23即35 YUV_420_888，See Also:android.graphics.ImageFormat
            int format = image.getFormat();
            int width = image.getWidth();
            int height = image.getHeight();
            // 如果不支持默认设置的分辨率，则使用自动选择的分辨率，否则编码器会报错
            if (width != SIZE.getWidth() || height != SIZE.getHeight()) {
                LogUtils.e("Automatic selection resolution");
                SIZE = new Size(width, height);
            }

            // 由于得到的图片格式是YUV_420_888的，这里我采用先转为NV21再转为NV12然后编码H264
            // 角度转换
            if (isStartEncode) {
                byte[] nv21 = ImageFormatUtils.yuv420888ToNV21(yPlane, uPlane, vPlane,
                        SIZE.getWidth(), SIZE.getHeight());
                ImageFormatUtils.NV21ToNV12(nv21, nv12, SIZE.getWidth(), SIZE.getHeight());
//                mcVideoEncoder.add(nv21);
                MuxerThread.addVideoPreviewData(nv12);
            }

            image.close();
        });

        // 绑定前先解绑
        cameraProvider.unbindAll();
        // 参数中如果有mImageCapture才能拍照，有mImageAnalyzer才能获取YUV数据
        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector,
                preview, mImageCapture, mImageAnalyzer);
    }

    public void switchCameraClick(View view) {
        mFacing = mFacing == CameraSelector.LENS_FACING_BACK ?
                CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        startCamera();
    }

    public void takePhotoClick(View view) {
        if (mImageCapture != null) {
            File dir = new File(Constants.PATH);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // 创建文件
            String fileName = DateUtils.getStringDate() + ".jpg";
            File file = new File(Constants.PATH, fileName);
            if (file.exists()) {
                file.delete();
            }
            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions
                    .Builder(file).build();

            // 开始拍照
            mImageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Toast.makeText(PreviewActivity.this, "保存成功: "
                                    + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Toast.makeText(PreviewActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    public void encodeClick(View view) {
        String label = mEncodeBtn.getText().toString();
        LogUtils.i("encoderClick label = " + label);
        if ("开始编码".equals(label)) {
            mEncodeBtn.setText("停止编码");
            mcVideoEncoder.start();
            isStartEncode = true;
        } else {
            mEncodeBtn.setText("开始编码");
            isStartEncode = false;
            mcVideoEncoder.stopEncodeVideo();
        }
    }

    private void stopRecording() {
        LogUtils.i("stopRecording");
        // stop encode
        if (null != mImageAnalyzer) {
            mImageAnalyzer.clearAnalyzer();
        }

        isStartEncode = false;
        mcVideoEncoder.stopEncodeVideo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
    }
}
