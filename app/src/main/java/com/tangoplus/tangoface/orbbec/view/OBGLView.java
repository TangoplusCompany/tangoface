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
	private int mRotationDegrees = 0;
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

	public void setRotation(int degrees) {
		mRotationDegrees = degrees;
		if (mOBRender != null) {
			mOBRender.setRotation(degrees);
		}
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
		int effectiveW = (mRotationDegrees == 90 || mRotationDegrees == 270) ? h : w;
		int effectiveH = (mRotationDegrees == 90 || mRotationDegrees == 270) ? w : h;

		if (checkViewSize(effectiveW, effectiveH) && null != mOBRender) {
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
		int effectiveW = (mRotationDegrees == 90 || mRotationDegrees == 270) ? h : w;
		int effectiveH = (mRotationDegrees == 90 || mRotationDegrees == 270) ? w : h;

		if (checkViewSize(effectiveW, effectiveH) && null != mOBRender) {
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
			if (h1 <= parent.getHeight()) {
				targetWidth = w1;
				targetHeight = h1;
			} else {
				targetWidth = w2;
				targetHeight = h2;
			}
		} else {
			if (h1 >= parent.getHeight()) {
				targetWidth = w1;
				targetHeight = h1;
			} else {
				targetWidth = w2;
				targetHeight = h2;
			}
		}
		Log.d("DEBUG_SIZE", "Target size = " + targetWidth + "x" + targetHeight);


		ViewGroup.LayoutParams params = this.getLayoutParams();
		params.width = targetWidth;
		params.height = targetHeight;

		if (params instanceof ViewGroup.MarginLayoutParams) {
			ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) params;

			// 💡 핵심: 부모 뷰(화면)를 뚫고 나가는 넘치는 크기(오버플로우) 계산
			int overflowX = targetWidth - parent.getWidth();
			int overflowY = targetHeight - parent.getHeight();

			// 💡 상하좌우를 똑같이 자르기 위해, 넘치는 크기의 '절반'만큼 마이너스(-) 마진을 주어 정중앙에 배치
			mlp.leftMargin = -(overflowX / 2);
			mlp.rightMargin = -(overflowX / 2);
			mlp.topMargin = -(overflowY / 2);
			mlp.bottomMargin = -(overflowY / 2);
		}
		this.setLayoutParams(params);
	}
}
