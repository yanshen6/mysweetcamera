package com.jerry.sweetcamera.widget;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import com.dynamsoft.barcode.BarcodeReader;
import com.dynamsoft.barcode.BarcodeReaderException;
import com.dynamsoft.barcode.EnumBarcodeFormat;
import com.dynamsoft.barcode.EnumImagePixelFormat;
import com.dynamsoft.barcode.PublicRuntimeSettings;
import com.dynamsoft.barcode.TextResult;
import com.jerry.sweetcamera.CameraActivity;
import com.jerry.sweetcamera.DBRCache;
import com.jerry.sweetcamera.R;
import com.jerry.sweetcamera.SweetApplication;

/**
 *
 *
 * @author jerry
 * @date 2015-09-16
 */
public class SquareCameraContainer extends FrameLayout implements ICameraOperation, IActivityLifiCycle {
    public static final String TAG = "SquareCameraContainer";

    private Context mContext;

    /**
     * 相机绑定的SurfaceView
     */
    private CameraView mCameraView;
    private SeekBar mZoomSeekBar;

    private CameraActivity mActivity;



    public static final int RESETMASK_DELY = 1000; //一段时间后遮罩层一定要隐藏

    private BarcodeReader reader;
    private Camera.PreviewCallback mPreviewCallback;
    private Handler mParentHandler;

    private int mPreviewHight;
    private int mPreviewWidth;
    private YuvImage mYuvImage;
    private DBRCache mCache;

    public SquareCameraContainer(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public SquareCameraContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public void setParentHandler(Handler handler){
        mParentHandler = handler;
    }

    void init() {
        inflate(mContext, R.layout.custom_camera_container, this);

        mCameraView = findViewById(R.id.cameraView);
        mZoomSeekBar = findViewById(R.id.zoomSeekBar);
        try {
            reader = new BarcodeReader("");
        } catch (Exception e) {
            e.printStackTrace();
        }


        mCameraView.setOnCameraPrepareListener(new CameraView.OnCameraPrepareListener() {
            @Override
            public void onPrepare(CameraManager.CameraDirection cameraDirection) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {

                    }
                }, RESETMASK_DELY);
                mZoomSeekBar.setMax(mCameraView.getMaxZoom());
                if (cameraDirection == CameraManager.CameraDirection.CAMERA_BACK) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            int screenWidth = SweetApplication.mScreenWidth;
                            Point point = new Point(screenWidth / 2, screenWidth / 2);
                            //onCameraFocus(point);
                        }
                    }, 500);
                }
            }
        });
        mCameraView.setSwitchCameraCallBack(new CameraView.SwitchCameraCallBack() {
            @Override
            public void switchCamera(boolean isSwitchFromFront) {
                if (isSwitchFromFront) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            int screenWidth = SweetApplication.mScreenWidth;
                            Point point = new Point(screenWidth / 2, screenWidth / 2);
                            //onCameraFocus(point);
                        }
                    }, 300);
                }
            }
        });

        mZoomSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);


        final SurfaceHolder holder = mCameraView.getHolder();
        holder.setFormat( ImageFormat.NV21);
        holder.setKeepScreenOn(true);
       

        mCameraView.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] bytes, Camera camera) {
                try {

                        mPreviewHight = mCameraView.parameters.getPreviewSize().height;
                        mPreviewWidth =mCameraView.parameters.getPreviewSize().width;
                        
                        mYuvImage = new YuvImage(bytes, ImageFormat.NV21,
                                mPreviewWidth, mPreviewHight,null);
                    //new ReaderTask().execute();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        mCameraView.setAutoFocusMoveCallback(new Camera.AutoFocusMoveCallback() {
            @Override
            public void onAutoFocusMoving(boolean b, Camera camera) {

                //Log.d("decodeBuffer", "onAutoFocusMoving:"+b);
                if(!b)
                {
                    new ReaderTask().execute();

                }
            }
        });

        mCache = DBRCache.get(mContext);
        mCache.put("linear", "1");
        mCache.put("qrcode", "1");
        mCache.put("pdf417", "1");
        mCache.put("matrix", "1");
        mCache.put("aztec", "0");
    }




    public void bindActivity(CameraActivity activity) {
        this.mActivity = activity;
        if (mCameraView != null) {
            mCameraView.bindActivity(activity);
        }
    }


    private static final int MODE_INIT = 0;

    private static final int MODE_ZOOM = 1;
    private int mode = MODE_INIT;// 初始状态

    private float startDis;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:
                mode = MODE_INIT;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:

                if (mZoomSeekBar == null) return true;
                mHandler.removeCallbacksAndMessages(mZoomSeekBar);
                mZoomSeekBar.setVisibility(View.GONE);
                mode = MODE_ZOOM;
                startDis = spacing(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == MODE_ZOOM) {

                    if (event.getPointerCount() < 2) return true;
                    float endDis = spacing(event);

                    int scale = (int) ((endDis - startDis) / 10f);
                    if (scale >= 1 || scale <= -1) {
                        int zoom = mCameraView.getZoom() + scale;
                        if (zoom > mCameraView.getMaxZoom()) zoom = mCameraView.getMaxZoom();
                        if (zoom < 0) zoom = 0;
                        mCameraView.setZoom(zoom);
                        mZoomSeekBar.setProgress(zoom);
                        startDis = endDis;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mode != MODE_ZOOM) {

                    Point point = new Point((int) event.getX(), (int) event.getY());
                    //onCameraFocus(point);
                } else {
                    mHandler.postAtTime(new Runnable() {

                        @Override
                        public void run() {
                            // TODO Auto-generated method stub
                            mZoomSeekBar.setVisibility(View.GONE);
                        }
                    }, mZoomSeekBar, SystemClock.uptimeMillis() + 2000);
                }
                break;
        }
        return true;
    }


    private float spacing(MotionEvent event) {
        if (event == null) {
            return 0;
        }
        double x = event.getX(0) - event.getX(1);
        double y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }




    @Override
    public void switchCamera() {
        mCameraView.switchCamera();
    }

    @Override
    public void switchFlashMode() {
        mCameraView.switchFlashMode();
    }

    @Override
    public boolean takePicture() {
        setMaskOn();
        boolean flag = mCameraView.takePicture();
        setMaskOff();
        return flag;
    }

    @Override
    public int getMaxZoom() {
        return mCameraView.getMaxZoom();
    }

    @Override
    public void setZoom(int zoom) {
        mCameraView.setZoom(zoom);
    }

    @Override
    public int getZoom() {
        return mCameraView.getZoom();
    }

    @Override
    public void releaseCamera() {
        if (mCameraView != null) {
            mCameraView.releaseCamera();
        }
    }


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

        }
    };




    private final SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
            // TODO Auto-generated method stub
            mCameraView.setZoom(progress);
            mHandler.removeCallbacksAndMessages(mZoomSeekBar);
            mHandler.postAtTime(new Runnable() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    mZoomSeekBar.setVisibility(View.GONE);
                }
            }, mZoomSeekBar, SystemClock.uptimeMillis() + 2000);
        }


        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub

        }


        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub

        }
    };

    @Override
    public void onStart() {

        if (mCameraView != null) {
            mCameraView.onStart();
        }


    }

    @Override
    public void onStop() {


        if (mCameraView != null) {
            mCameraView.onStop();
        }


    }

    public void setMaskOn() {

    }

    public void setMaskOff() {

    }


    public void setPreviewCallback(Camera.PreviewCallback  previewCallback) {
        mPreviewCallback = previewCallback;
    }

    class ReaderTask extends AsyncTask{

        @Override
        protected Object doInBackground(Object[] objects) {



            if (mParentHandler!=null &&mYuvImage!=null) {
                int width =mPreviewWidth ;
                int height = mPreviewHight;
                int[] strides = mYuvImage.getStrides();

                Log.d("decodeBuffer", "begin.....");
                try {
                    reader.decodeBuffer(mYuvImage.getYuvData(), width, height, strides[0], EnumImagePixelFormat.IPF_NV21, "");
                    Log.d("decodeBuffer", "finish:");
                    TextResult[] result = reader.getAllTextResults();
                    if (result != null && result.length > 0) {
                        Log.d("decodeBuffer", "success");
                        Message message = mParentHandler.obtainMessage();
                        message.what = 0x02;
                        String str = "";
                        for (int i = 0; i < result.length; i++) {
                            if (i == 0)
                                str = result[i].barcodeText;
                            else
                                str = str + "\n\n" + result[i].barcodeText;
                        }
                        message.obj = str;
                        mParentHandler.sendMessage(message);
                    }
                } catch (BarcodeReaderException e) {
                    e.printStackTrace();
                    Log.d("BarcodeReaderException", e.getMessage());
                }

            }
            else{
                if(mParentHandler!=null) {
                    Message message = mParentHandler.obtainMessage();
                    message.what = 0x02;
                    message.obj = "";
                    mParentHandler.sendMessage(message);
                }
            }
            return null;
        }
    }

    public void updateBarcodeReaderSetting(){

            try {
                int nBarcodeFormat =0;
                if (mCache.getAsString("linear").equals("1")) {
                    nBarcodeFormat = nBarcodeFormat| EnumBarcodeFormat.BF_OneD;
                }
                if (mCache.getAsString("qrcode").equals("1")) {
                    nBarcodeFormat = nBarcodeFormat|EnumBarcodeFormat.BF_QR_CODE;
                }
                if (mCache.getAsString("pdf417").equals("1")) {
                    nBarcodeFormat = nBarcodeFormat|EnumBarcodeFormat.BF_PDF417;
                }
                if (mCache.getAsString("matrix").equals("1")) {
                    nBarcodeFormat = nBarcodeFormat|EnumBarcodeFormat.BF_DATAMATRIX;
                }
                if (mCache.getAsString("aztec").equals("1")) {
                    nBarcodeFormat = nBarcodeFormat|EnumBarcodeFormat.BF_AZTEC;
                }

                PublicRuntimeSettings runtimeSettings =  reader.getRuntimeSettings();
                runtimeSettings.mBarcodeFormatIds = nBarcodeFormat;
                reader.updateRuntimeSettings(runtimeSettings);

            } catch (Exception e) {
                e.printStackTrace();
            }


    }
}
