package com.jerry.sweetcamera;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;


/**
 * @author jerry
 * @date 2016/03/11
 */
public class SweetApplication extends Application {

    public static int mScreenWidth = 0;
    public static int mScreenHeight = 0;

    public static SweetApplication CONTEXT;

    @Override
    public void onCreate() {
        super.onCreate();
        DisplayMetrics mDisplayMetrics = getApplicationContext().getResources()
                .getDisplayMetrics();
        SweetApplication.mScreenWidth = mDisplayMetrics.widthPixels;
        SweetApplication.mScreenHeight = mDisplayMetrics.heightPixels;

        CONTEXT = this;

        //FileUtil.initFolder();
    }

}
