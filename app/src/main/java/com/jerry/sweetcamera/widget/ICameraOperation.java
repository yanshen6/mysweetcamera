package com.jerry.sweetcamera.widget;


/**
 * 相机操作的接口
 * @author jerry
 * @date 2015-09-24
 */
public interface ICameraOperation {

    public  void  switchCamera();

    public void switchFlashMode();

    public boolean takePicture();

    public int getMaxZoom();

    public void setZoom(int zoom);

    public int getZoom();

    public void releaseCamera();
}
