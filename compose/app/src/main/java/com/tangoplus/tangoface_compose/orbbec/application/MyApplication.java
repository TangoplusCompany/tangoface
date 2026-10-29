package com.tangoplus.tangoface_compose.orbbec.application;

import android.app.Application;

import com.tangoplus.tangoface_compose.R;
import com.tangoplus.tangoface_compose.di.AppContainer;
import com.tangoplus.tangoface_compose.di.DefaultAppContainer;
import com.tangoplus.tangoface_compose.orbbec.utils.MyCrashHandler;

public class MyApplication extends Application {
	private static MyApplication mInstance;
	private static boolean isTablet;
	private AppContainer mContainer;

	static {
		try {
			// 네이티브 라이브러리 로드
			System.loadLibrary("tangoface");
		} catch (UnsatisfiedLinkError e) {
			android.util.Log.w("MyApplication", "libtangoface not loaded yet: " + e.getMessage());
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Thread.setDefaultUncaughtExceptionHandler(new MyCrashHandler());
		mInstance = this;

		// DI 컨테이너 초기화
		mContainer = new DefaultAppContainer(this);

		// 태블릿 여부 초기화
		isTablet = getResources().getBoolean(R.bool.is_tablet);

		// OpenCV 초기화
		if (!org.opencv.android.OpenCVLoader.initLocal()) {
			android.util.Log.e("MyApplication", "OpenCV initialization failure!");
		} else {
			android.util.Log.d("MyApplication", "OpenCV Initialization Successful!");
		}
	}

	public static MyApplication getInstance() {
		return mInstance;
	}

	public AppContainer getContainer() {
		return mContainer;
	}

	public static boolean isTablet() {
		return isTablet;
	}
}
