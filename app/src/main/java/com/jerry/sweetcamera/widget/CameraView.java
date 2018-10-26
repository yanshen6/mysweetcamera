package com.jerry.sweetcamera.widget;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.Toast;


import com.jerry.sweetcamera.CameraActivity;
import com.jerry.sweetcamera.R;
import com.jerry.sweetcamera.SweetApplication;

import java.io.IOException;
import java.util.List;

/**
 *
 * @author jerry
 * @date 2015-09-24
 */
public class CameraView extends SurfaceView implements SurfaceHolder.Callback, ICameraOperation, IActivityLifiCycle {
    public static final String TAG = "CameraView";

    private CameraManager.CameraDirection mCameraId; //0后置  1前置
    private Camera mCamera;
    public Camera.Parameters parameters = null;
    private CameraManager mCameraManager;
    private Context mContext;
    private SwitchCameraCallBack mSwitchCameraCallBack;

    private int mDisplayOrientation;
    private int mLayoutOrientation;
    private CameraOrientationListener mOrientationListener;

    private int mZoom;
    private int mOrientation = 0;

    private int mRotation;
    private OnCameraPrepareListener onCameraPrepareListener;
    private Camera.PictureCallback callback;

    private CameraActivity mActivity;
    private Camera.PreviewCallback mPreviewCallback;
    private Camera.AutoFocusMoveCallback mAutoFocusMoveCallback;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;
        mCameraManager = CameraManager.getInstance(context);
        mCameraId = mCameraManager.getCameraDirection();

        setFocusable(true);
        getHolder().addCallback(this);//为SurfaceView的句柄添加一个回调函数

        mOrientationListener = new CameraOrientationListener(mContext);
        mOrientationListener.enable();
    }

    public void bindActivity(CameraActivity activity) {
        this.mActivity = activity;
    }

    public void setOnCameraPrepareListener(OnCameraPrepareListener onCameraPrepareListener) {
        this.onCameraPrepareListener = onCameraPrepareListener;
    }



    public void setSwitchCameraCallBack(SwitchCameraCallBack mSwitchCameraCallBack) {
        this.mSwitchCameraCallBack = mSwitchCameraCallBack;
    }

    public void setAutoFocusMoveCallback(Camera.AutoFocusMoveCallback autoFocusMoveCallback){
        mAutoFocusMoveCallback = autoFocusMoveCallback;
    }

    private void adjustView(Camera.Size adapterSize ){
        int width = SweetApplication.mScreenWidth;
        int height = width * adapterSize.width / adapterSize.height;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        params.topMargin = -(height - width) / 2;
        params.width = width;
        params.height = height;
        setLayoutParams(params);
    }


    private void initCamera() {
        parameters = mCamera.getParameters();
        parameters.setPictureFormat(ImageFormat.JPEG);
        parameters.setPreviewFormat(ImageFormat.NV21);
        List<String> focusModes = parameters.getSupportedFocusModes();

        List<Camera.Size> sizeList =  mCamera.getParameters().getSupportedPreviewSizes();
        for (Camera.Size size:sizeList
             ) {
            if(size.height*size.width>1500000)
                continue;
            else{
                parameters.setPreviewSize(size.width,size.height);
                break;
            }

        }

        try {
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)){
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            mCamera.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }


        mCameraManager.setFitPreSize(mCamera);
        Camera.Size adapterSize = mCamera.getParameters().getPreviewSize();

        mCameraManager.setFitPicSize(mCamera, (float) adapterSize.width / adapterSize.height);
        adapterSize = mCamera.getParameters().getPictureSize();

        adjustView(adapterSize);

        determineDisplayOrientation();
        mCamera.startPreview();
        if(mAutoFocusMoveCallback!=null){
            mCamera.setAutoFocusMoveCallback(mAutoFocusMoveCallback);
        }else {
            mCamera.setAutoFocusMoveCallback(new Camera.AutoFocusMoveCallback() {
                @Override
                public void onAutoFocusMoving(boolean b, Camera camera) {
                    if (b) {
                        Log.d("CameraView", "Auto focus start");
                    } else {
                        Log.d("CameraView", "Auto focus end");
                    }
                }
            });
        }
        turnLight(mCameraManager.getLightStatus());  //设置闪光灯
        mCameraManager.setActivityCamera(mCamera);
    }
    public void releaseCamera() {
        mCameraManager.releaseCamera(mCamera);
        mCamera = null;
    }

    @Override
    public void switchCamera() {
        mCameraId = mCameraId.next();
        releaseCamera();

        setUpCamera(mCameraId, mCameraId == CameraManager.CameraDirection.CAMERA_BACK);
    }

    @Override
    public void switchFlashMode() {
        turnLight(mCameraManager.getLightStatus().next());
    }

    public boolean isBackCamera() {
        return mCameraId == CameraManager.CameraDirection.CAMERA_BACK;
    }

    @Override
    public boolean takePicture() {
        try {
            mCamera.takePicture(null, null, callback);
            mOrientationListener.rememberOrientation();


        } catch (Throwable t) {
            t.printStackTrace();
            Log.e(TAG, "photo fail after Photo Clicked");
            Toast.makeText(mContext, R.string.topic_camera_takephoto_failure, Toast.LENGTH_SHORT).show();

            try {
                mCamera.startPreview();
            } catch (Throwable e) {
                e.printStackTrace();
            }

            return false;
        }

        try {
            mCamera.startPreview();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public int getMaxZoom() {
        if (mCamera == null) return -1;
        Camera.Parameters parameters = mCamera.getParameters();
        if (!parameters.isZoomSupported()) return -1;
        return parameters.getMaxZoom() > 40 ? 40 : parameters.getMaxZoom();
    }

    @Override
    public void setZoom(int zoom) {
        if (mCamera == null) return;
        Camera.Parameters parameters;
        parameters = mCamera.getParameters();

        if (!parameters.isZoomSupported()) return;
        parameters.setZoom(zoom);
        mCamera.setParameters(parameters);
        mZoom = zoom;
    }

    @Override
    public int getZoom() {
        return mZoom;
    }


    private void turnLight(CameraManager.FlashLigthStatus ligthStatus) {
        if (CameraManager.mFlashLightNotSupport.contains(ligthStatus)) {
            turnLight(ligthStatus.next());
            return;
        }

        if (mCamera == null || mCamera.getParameters() == null
                || mCamera.getParameters().getSupportedFlashModes() == null) {
            return;
        }
        Camera.Parameters parameters = mCamera.getParameters();
        List<String> supportedModes = mCamera.getParameters().getSupportedFlashModes();

        switch (ligthStatus) {
            case LIGHT_AUTO:
                if (supportedModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                }
                break;
            case LIGTH_OFF:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                break;
            case LIGHT_ON:
                if (supportedModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                } else if (supportedModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                } else if (supportedModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                }
                break;
        }
        mCamera.setParameters(parameters);
        mCameraManager.setLightStatus(ligthStatus);
    }


    public void setPreviewCallback( Camera.PreviewCallback previewCallback){
        mPreviewCallback = previewCallback;
    }


    /**
     * setup
     *
     * @param mCameraId
     */
    private void setUpCamera(CameraManager.CameraDirection mCameraId, boolean isSwitchFromFront) {
        int facing = mCameraId.ordinal();
        try {
            mCamera = mCameraManager.openCameraFacing(facing);
        } catch (Exception e) {
            Toast.makeText(mContext, R.string.tips_camera_forbidden, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(getHolder());
                initCamera();

                mCameraManager.setCameraDirection(mCameraId);


                mCamera.setPreviewCallback(mPreviewCallback);

            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
//            toast("切换失败，请重试！", Toast.LENGTH_LONG);
        }

        if (mSwitchCameraCallBack != null) {
            mSwitchCameraCallBack.switchCamera(isSwitchFromFront);
        }
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");

        mCameraManager.releaseStartTakePhotoCamera();

        if (null == mCamera) {
            //打开默认的摄像头
            setUpCamera(mCameraId, false);
            if (onCameraPrepareListener != null) {
                onCameraPrepareListener.onPrepare(mCameraId);
            }
            if (mCamera != null) {
                startOrientationChangeListener();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surfaceChanged");

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        try {
            releaseCamera();
            if (holder != null) {
                if (Build.VERSION.SDK_INT >= 14) {
                    holder.getSurface().release();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Determine the current display orientation and rotate the camera preview
     * accordingly
     */
    private void determineDisplayOrientation() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId.ordinal(), cameraInfo);

        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0: {
                degrees = 0;
                break;
            }
            case Surface.ROTATION_90: {
                degrees = 90;
                break;
            }
            case Surface.ROTATION_180: {
                degrees = 180;
                break;
            }
            case Surface.ROTATION_270: {
                degrees = 270;
                break;
            }
        }

        int displayOrientation;

        // Camera direction
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (cameraInfo.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (cameraInfo.orientation - degrees + 360) % 360;
        }

        mDisplayOrientation = (cameraInfo.orientation - degrees + 360) % 360;
        mLayoutOrientation = degrees;

        mCamera.setDisplayOrientation(displayOrientation);

        Log.i(TAG, "displayOrientation:" + displayOrientation);
    }

    private void startOrientationChangeListener() {
        OrientationEventListener mOrEventListener = new OrientationEventListener(getContext()) {
            @Override
            public void onOrientationChanged(int rotation) {

                if (((rotation >= 0) && (rotation <= 45)) || (rotation > 315)) {
                    rotation = 0;
                } else if ((rotation > 45) && (rotation <= 135)) {
                    rotation = 90;
                } else if ((rotation > 135) && (rotation <= 225)) {
                    rotation = 180;
                } else if ((rotation > 225) && (rotation <= 315)) {
                    rotation = 270;
                } else {
                    rotation = 0;
                }
                if (rotation == mOrientation)
                    return;
                mOrientation = rotation;

            }
        };
        mOrEventListener.enable();
    }

    /**
     * When orientation changes, onOrientationChanged(int) of the listener will be called
     */
    private class CameraOrientationListener extends OrientationEventListener {

        private int mCurrentNormalizedOrientation;
        private int mRememberedNormalOrientation;

        public CameraOrientationListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation != ORIENTATION_UNKNOWN) {
                mCurrentNormalizedOrientation = normalize(orientation);
            }
        }

        private int normalize(int degrees) {
            if (degrees > 315 || degrees <= 45) {
                return 0;
            }

            if (degrees > 45 && degrees <= 135) {
                return 90;
            }

            if (degrees > 135 && degrees <= 225) {
                return 180;
            }

            if (degrees > 225 && degrees <= 315) {
                return 270;
            }

            throw new RuntimeException("The physics as we know them are no more. Watch out for anomalies.");
        }

        public void rememberOrientation() {
            mRememberedNormalOrientation = mCurrentNormalizedOrientation;
        }

    }

    @Override
    public void onStart() {
        mOrientationListener.enable();
    }

    @Override
    public void onStop() {
        mOrientationListener.disable();
    }

    public interface OnCameraPrepareListener {
        void onPrepare(CameraManager.CameraDirection cameraDirection);
    }

    public interface SwitchCameraCallBack {
        public void switchCamera(boolean isSwitchFromFront);
    }
}
