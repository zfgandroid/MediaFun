package com.zfg.mediafun;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.zfg.encode.MCAudioEncoder;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * @author zhongfanggui
 * @version 1.0.0
 * @since 2023/5/24
 */
public class MainActivity extends BaseActivity implements View.OnClickListener {

    private static final int PERMISSIONS_REQUEST = 101;

    /**
     * 需要申请的运行时权限
     */
    private String[] mPermissions = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * 被用户拒绝的权限列表
     */
    private List<String> mPermissionList = new ArrayList<>();

    private Button mStartRecordBtn;
    private Button mStopRecordBtn;

    private MCAudioEncoder mcAudioEncoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        checkPermissions();
    }

    private void initView() {
        mStartRecordBtn = findViewById(R.id.btn_start_record);
        mStopRecordBtn = findViewById(R.id.btn_stop_record);

        mStartRecordBtn.setOnClickListener(this);
        mStopRecordBtn.setOnClickListener(this);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (int i = 0; i < mPermissions.length; i++) {
                if (ContextCompat.checkSelfPermission(this, mPermissions[i]) !=
                        PackageManager.PERMISSION_GRANTED) {
                    mPermissionList.add(mPermissions[i]);
                }
            }
            if (!mPermissionList.isEmpty()) {
                String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);
                ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, permissions[i] +
                            " 权限被禁止了", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_start_record:
                startRecord();
                break;
            case R.id.btn_stop_record:
                stopRecord();
                break;
            default:
                break;
        }
    }

    private void startRecord() {
        mcAudioEncoder = new MCAudioEncoder(this);
        mcAudioEncoder.start();
    }

    private void stopRecord() {
        if (null != mcAudioEncoder) {
            mcAudioEncoder.stopEncode();
        }
    }
}