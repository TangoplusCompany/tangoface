package com.tangoplus.tangoface.orbbec.view;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;

import com.orbbec.obsensor.types.Format;
import com.orbbec.obsensor.types.StreamType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;

public class OBGLView extends GLSurfaceView {
	private static final String TAG = "OBGLView";

	private OBRender mOBRender;
	private Handler mHandler;
	private boolean mCenterCrop = false;

	public void setCenterCrop(boolean enable) {
		mCenterCrop = enable;
	}

	public OBGLView(Context context) {
		super(context);
		init();
	}

	public OBGLView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		setEGLContextClientVersion(2);
		mOBRender = new OBRender(this);
		setRenderer(mOBRender);
		setRenderMode(RENDERMODE_WHEN_DIRTY);
		mHandler = new Handler(Looper.getMainLooper());
	}

	/**
	 * Render frame
	 *
	 * @param w      width
	 * @param h      height
	 * @param type   Stream type {@link StreamType#COLOR}/{@link StreamType#DEPTH}/{@link StreamType#IR}
	 * @param format Frame Format {@link Format}
	 * @param data   Frame data
	 */
	public void update(int w, int h, StreamType type, Format format, byte[] data, float scale) {
		if (checkViewSize(w, h) && null != mOBRender) {
			mOBRender.update(w, h, type, format, data, scale);
		}
	}

	/**
	 * Render frame
	 *
	 * @param w      width
	 * @param h      height
	 * @param type   Stream type {@link StreamType#COLOR}/{@link StreamType#DEPTH}/{@link StreamType#IR}
	 * @param format Frame Format{@link Format}
	 * @param buffer Frame data
	 */
	public void update(int w, int h, StreamType type, Format format, ByteBuffer buffer, float scale) {
		if (checkViewSize(w, h) && null != mOBRender) {
			mOBRender.update(w, h, type, format, buffer, scale);
		}
	}

	/**
	 * Clear window
	 */
	public void clearWindow() {
		if (null != mOBRender) {
			mOBRender.clearWindow();
		}
	}

	/**
	 * Check whether the aspect ratio of the view is consistent with the rendered frame
	 *
	 * @param frameW frame width
	 * @param frameH frame height
	 * @return true: same size, false: frame size not equal.
	 */
	private boolean checkViewSize(int frameW, int frameH) {
		int viewW = this.getWidth();
		int viewH = this.getHeight();
		float viewScale = 0;
		if (0 != viewW && 0 != viewH) {
			viewScale = new BigDecimal(viewW * 1.0f / viewH).setScale(2, RoundingMode.HALF_UP).floatValue();
		}
		float frameScale = new BigDecimal(frameW * 1.0f / frameH).setScale(2, RoundingMode.HALF_UP).floatValue();
		if (Math.abs(viewScale - frameScale) >= 0.05f) {
			Log.i(TAG, "checkViewSize: view size:" + viewW + "x" + viewH + " view scale:" + viewScale
				+ " frameSize:" + frameW + "x" + frameH + " frameScale:" + frameScale);
			if (null != mHandler) {
				mHandler.post(() -> updateGLSurfaceViewSize(frameW, frameH));
			}
			return false;
		}
		return true;
	}

	/**
	 * Update the size of the gl view window in real time according to the frame resolution to ensure
	 * proportional rendering
	 *
	 * @param frameW frame width
	 * @param frameH frame height
	 */
	private void updateGLSurfaceViewSize(int frameW, int frameH) {
		ViewGroup parent = (ViewGroup) this.getParent();
		if (null == parent || parent.getWidth() <= 0 || parent.getHeight() <= 0
			|| frameH <= 0 || frameW <= 0) {
			Log.w(TAG, "Invalid layout parameters - parent:" + parent
				+ " parentSize:[" + (parent != null ? parent.getWidth() : 0) + "x" + (parent != null ? parent.getHeight() : 0) + "]"
				+ " frameSize:[" + frameW + "x" + frameH + "]");
			return;
		}

		// Fit/FIll 계산에 쓸 두 가지 후보
		// w1 x h1 : 부모의 너비에 맞추고, 높이는 비율로 산출
		int w1 = parent.getWidth();
		int h1 = parent.getWidth() * frameH / frameW;

		// w2 x h2 : 부모의 높이에 맞추고, 너비는 비율로 산출
		int h2 = parent.getHeight();
		int w2 = parent.getHeight() * frameW / frameH;

		int targetWidth, targetHeight;
		Log.d("DEBUG_SIZE", "Parent size = " + parent.getWidth() + "x" + parent.getHeight());
		Log.d("DEBUG_SIZE", "Candidate 1 (Fit width): " + w1 + "x" + h1);
		Log.d("DEBUG_SIZE", "Candidate 2 (Fit height): " + w2 + "x" + h2);
		Log.d("DEBUG_SIZE", "mCenterCrop = " + mCenterCrop);

		if (!mCenterCrop) {
			// 기존 로직(맞춰 넣기, Fit Center) — 부모 안에 "들어가도록" 선택
			if (h1 <= parent.getHeight()) {
				targetWidth = w1;
				targetHeight = h1;
			} else {
				targetWidth = w2;
				targetHeight = h2;
			}
		} else {
			// Center-Crop(Aspect Fill) — 부모를 "가득 채우도록" 선택
			// => 한쪽 변은 부모보다 커져서 넘침(잘림)
			if (h1 >= parent.getHeight()) {
				// 너비 기준으로 맞추면 높이가 더 커짐 → 세로로 넘쳐 크롭
				targetWidth = w1;
				targetHeight = h1;
			} else {
				// 높이 기준으로 맞추면 너비가 더 커짐 → 가로로 넘쳐 크롭(원하시는 상황)
				targetWidth = w2;
				targetHeight = h2;
			}
		}
		Log.d("DEBUG_SIZE", "Target size = " + targetWidth + "x" + targetHeight);
		ViewGroup.LayoutParams params = this.getLayoutParams();
		params.width = targetWidth;
		params.height = targetHeight;
		this.setLayoutParams(params);

		// 부모 중앙에 놓여야 예쁘게 “센터 크롭”이 됩니다.
		// 부모가 FrameLayout이면 layout_gravity를 쓰고, 아니면 아래와 같이 마진으로 중앙 정렬:
		if (params instanceof ViewGroup.MarginLayoutParams) {
			ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) params;
			int dx = (parent.getWidth() - targetWidth) / 2;
			int dy = (parent.getHeight() - targetHeight) / 2;
			mlp.leftMargin = Math.abs(dx / 2);
			mlp.rightMargin = Math.abs(dx / 2);
			mlp.topMargin = dy;
			this.setLayoutParams(mlp);
			Log.d("DEBUG_SIZE", "Margins = left:" + dx + ", top:" + dy);
		}
	}

}
