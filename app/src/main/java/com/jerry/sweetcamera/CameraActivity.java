package com.jerry.sweetcamera;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.TextView;

import com.jerry.sweetcamera.widget.CameraManager;
import com.jerry.sweetcamera.widget.SquareCameraContainer;

/**
 * 自定义相机的activity
 *
 * @author jerry
 * @date 2015-09-01
 */
public class CameraActivity extends Activity {
    public static final String TAG = "CameraActivity";

    private CameraManager mCameraManager;

    private TextView m_tvFormatSetting;
    private SquareCameraContainer mCameraContainer;

    private Camera.PreviewCallback  previewCallback;


    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };
    private TextView mTextResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mCameraManager = CameraManager.getInstance(this);
        requestPermissions();
        initView();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0x02:
                    mTextResult.setText((String) msg.obj);
                    break;
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCameraContainer.updateBarcodeReaderSetting();
    }

    void initView() {
        m_tvFormatSetting =  findViewById(R.id.tv_format_settings);
        mCameraContainer =  findViewById(R.id.cameraContainer);
        mTextResult = findViewById(R.id.tv_result);
        mCameraContainer.setPreviewCallback(previewCallback);
        mCameraContainer.setParentHandler(handler);
        m_tvFormatSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_tvFormatSetting.setClickable(false);


                Intent intent = new Intent(CameraActivity.this, SettingActivity.class);
                startActivityForResult(intent, 0);

                //500ms后才能再次点击
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        m_tvFormatSetting.setClickable(true);
                    }
                }, 500);
            }
        });
        mCameraContainer.bindActivity(this);
    }



    @Override
    protected void onStart() {
        super.onStart();
        if (mCameraContainer != null) {
            mCameraContainer.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCameraContainer != null) {
            mCameraContainer.onStop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraManager.unbinding();
        mCameraManager.releaseActivityCamera();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }



    private void requestPermissions(){
        if (Build.VERSION.SDK_INT>22){
            try {
                if (ContextCompat.checkSelfPermission(CameraActivity.this,"android.permission.WRITE_EXTERNAL_STORAGE")!= PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(CameraActivity.this, PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
                }
                ActivityCompat.requestPermissions(CameraActivity.this,
                        new String[]{android.Manifest.permission.CAMERA},0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            // do nothing
        }
    }
}
