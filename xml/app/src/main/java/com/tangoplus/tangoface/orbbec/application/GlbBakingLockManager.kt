package com.tangoplus.tangoface.orbbec.application

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/**
 * Application 전역에서 3D GLB 모델 생성(굽기) 및 다운로드 중
 * - 앱 내부 터치/뒤로가기 차단 (시스템 홈 이동 제외)
 * - 화면 회전 잠금 (SCREEN_ORIENTATION_LOCKED)
 * - 차단된 터치 시 "조금만 기다려주세요." 토스트 안내
 * - 100초 이후 노출되는 취소 버튼 등 예외 View 1개만 선택적 터치 허용
 */
object GlbBakingLockManager {

	private const val TOAST_THROTTLE_MS = 2000L
	private const val WAIT_MESSAGE = "3D 모델을 생성 중입니다. 조금만 기다려주세요."

	private data class ActivityRecord(
		var originalOrientation: Int
	)

	@Volatile
	private var appContext: Application? = null

	@Volatile
	private var isLockedState: Boolean = false

	@Volatile
	private var allowedViewRef: WeakReference<View>? = null

	private val _isLockedFlow = MutableStateFlow(false)
	val isLockedFlow: StateFlow<Boolean> = _isLockedFlow.asStateFlow()

	private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
	private val activityRecords = WeakHashMap<Activity, ActivityRecord>()
	private var lastToastElapsedMs: Long = 0L
	private var currentToast: Toast? = null
	private var initialized = false

	@JvmStatic
	fun init(application: Application) {
		if (initialized) return
		initialized = true
		appContext = application

		application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
				val currentOri = activity.requestedOrientation
				activityRecords.putIfAbsent(
					activity,
					ActivityRecord(
						originalOrientation = if (currentOri == ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
							ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
						} else {
							currentOri
						}
					)
				)
				syncActivity(activity)
			}

			override fun onActivityStarted(activity: Activity) {
				syncActivity(activity)
			}

			override fun onActivityResumed(activity: Activity) {
				syncActivity(activity)
			}

			override fun onActivityPaused(activity: Activity) = Unit
			override fun onActivityStopped(activity: Activity) = Unit
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

			override fun onActivityDestroyed(activity: Activity) {
				activityRecords.remove(activity)
			}
		})
	}

	@JvmStatic
	fun isLocked(): Boolean = isLockedState

	@JvmStatic
	fun setLocked(locked: Boolean) {
		if (isLockedState == locked) return
		isLockedState = locked
		_isLockedFlow.value = locked
		if (!locked) {
			allowedViewRef = null
		}
		if (initialized) {
			runOnMain {
				applyLockToAllActivities()
			}
		}
	}

	/**
	 * 잠금 상태에서도 예외적으로 터치를 허용할 View 지정 (예: 100초 후 표시되는 대기 취소 버튼)
	 */
	@JvmStatic
	fun setAllowedTouchView(view: View?) {
		allowedViewRef = view?.let { WeakReference(it) }
	}

	@JvmStatic
	fun notifyWaitingToast() {
		val ctx = appContext ?: return
		runOnMain {
			val now = SystemClock.elapsedRealtime()
			if (now - lastToastElapsedMs < TOAST_THROTTLE_MS) return@runOnMain
			lastToastElapsedMs = now
			currentToast?.cancel()
			currentToast = Toast.makeText(ctx, WAIT_MESSAGE, Toast.LENGTH_SHORT).also { it.show() }
		}
	}

	private fun runOnMain(block: () -> Unit) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			block()
		} else {
			mainHandler.post(block)
		}
	}

	private fun applyLockToAllActivities() {
		// 순회 중 변경 방지용 스냅샷
		val activities = activityRecords.keys.toList()
		activities.forEach { activity ->
			if (!activity.isFinishing && !activity.isDestroyed) {
				syncActivity(activity)
			}
		}
	}

	private fun syncActivity(activity: Activity) {
		ensureWindowCallbackWrapped(activity)
		val record = activityRecords.getOrPut(activity) {
			val ori = activity.requestedOrientation
			ActivityRecord(
				if (ori == ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
					ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
				} else {
					ori
				}
			)
		}
		if (isLockedState) {
			val currentOri = activity.requestedOrientation
			if (currentOri != ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
				record.originalOrientation = currentOri
				activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
			}
		} else {
			if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
				activity.requestedOrientation = record.originalOrientation
			}
		}
	}

	private fun ensureWindowCallbackWrapped(activity: Activity) {
		val window = activity.window ?: return
		val current = window.callback ?: return
		if (Proxy.isProxyClass(current.javaClass) &&
			runCatching { Proxy.getInvocationHandler(current) is BakingWindowCallbackHandler }.getOrDefault(false)
		) {
			return
		}
		val wrapped = Proxy.newProxyInstance(
			Window.Callback::class.java.classLoader,
			arrayOf(Window.Callback::class.java),
			BakingWindowCallbackHandler(current)
		) as Window.Callback
		window.callback = wrapped
	}

	private fun isTouchInsideAllowedView(ev: MotionEvent): Boolean {
		val view = allowedViewRef?.get() ?: return false
		if (!view.isAttachedToWindow || !view.isShown) return false
		val loc = IntArray(2)
		view.getLocationOnScreen(loc)
		val x = ev.rawX
		val y = ev.rawY
		return x >= loc[0] && x <= loc[0] + view.width && y >= loc[1] && y <= loc[1] + view.height
	}

	private class BakingWindowCallbackHandler(
		private val delegate: Window.Callback
	) : InvocationHandler {

		private var allowCurrentTouchGesture = false

		override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
			when (method.name) {
				"dispatchTouchEvent" -> {
					val ev = args?.getOrNull(0) as? MotionEvent
					if (ev != null) {
						if (isLockedState) {
							val action = ev.actionMasked
							if (action == MotionEvent.ACTION_DOWN) {
								allowCurrentTouchGesture = isTouchInsideAllowedView(ev)
								if (!allowCurrentTouchGesture) {
									notifyWaitingToast()
									return true
								}
								return invokeDelegate(method, args)
							}
							if (allowCurrentTouchGesture) {
								val handled = invokeDelegate(method, args)
								if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
									allowCurrentTouchGesture = false
								}
								return handled
							}
							return true
						} else {
							allowCurrentTouchGesture = false
						}
					}
				}

				"dispatchKeyEvent" -> {
					val ev = args?.getOrNull(0) as? KeyEvent
					if (ev != null && isLockedState &&
						(ev.keyCode == KeyEvent.KEYCODE_BACK || ev.keyCode == KeyEvent.KEYCODE_ESCAPE)
					) {
						if (ev.action == KeyEvent.ACTION_UP) {
							notifyWaitingToast()
						}
						return true
					}
				}
			}
			return invokeDelegate(method, args)
		}

		private fun invokeDelegate(method: Method, args: Array<out Any?>?): Any? {
			return try {
				if (args == null) method.invoke(delegate) else method.invoke(delegate, *args)
			} catch (e: InvocationTargetException) {
				throw (e.targetException ?: e)
			}
		}
	}
}
