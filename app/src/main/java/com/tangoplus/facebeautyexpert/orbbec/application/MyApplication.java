package com.tangoplus.facebeautyexpert.orbbec.application;

import android.app.Application;

import com.tangoplus.facebeautyexpert.R;
import com.tangoplus.facebeautyexpert.orbbec.utils.MyCrashHandler;


public class MyApplication extends Application {
	private static MyApplication mInstance;
	private static boolean isTablet;

	static {
		// 네이티브 라이브러리 로드
		System.loadLibrary("facebeautyexpert");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Thread.setDefaultUncaughtExceptionHandler(new MyCrashHandler());
		mInstance = this;

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

	public static boolean isTablet() {
		return isTablet;
	}
}