package com.tangoplus.tangoface.ui.info

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentIModelScanBinding
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/**
 * 3D 스캔(SCAN) 결과 GLB 뷰어 프래그먼트
 * - InformationViewModel.scanResultInfo (3D 모델 파일 + 측정 날짜) 관찰
 * - 로컬 GLB 파일을 WebView(model-viewer)로 스트리밍 서빙하여 회전/줌 지원
 */
class IModelScanFragment : Fragment() {

    private var _binding: FragmentIModelScanBinding? = null
    private val bd get() = _binding!!

    private val ivm: InformationViewModel by activityViewModels()
    private val mvm: MainViewModel by activityViewModels()

    private var activeGlbFile: File? = null
    private var activeGlbUri: Uri? = null
    private var lastLoadedKey: String? = null
    private var isClayMode = false

    companion object {
        private const val VIRTUAL_ORIGIN = "https://appassets.androidplatform.net"
        private const val VIRTUAL_MODEL_PATH = "/local-model/scan.glb"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIModelScanBinding.inflate(inflater, container, false)
        return bd.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWebView()
        setupToggleButton()

        // MainViewModel에 현재 결과가 있고 InformationViewModel이 아직 비어있다면 동기화
        if (ivm.scanResultInfo.value == null && mvm.currentResult.value != null) {
            ivm.setScanResultFromFaceResult(mvm.currentResult.value)
        }

        observeScanResult()
    }

    private fun setupToggleButton() {
        updateToggleButtonUi()
        bd.btnToggleClayMode.setOnClickListener {
            isClayMode = !isClayMode
            updateToggleButtonUi()
            bd.wvIModelScanViewer.evaluateJavascript(
                "window.setMeshMode && window.setMeshMode($isClayMode);",
                null
            )
        }
    }

    private fun updateToggleButtonUi() {
        val ctx = context ?: return
        if (isClayMode) {
            bd.btnToggleClayMode.text = "원본 색상"
            bd.btnToggleClayMode.setBackgroundResource(R.drawable.bg_glass_card)
            bd.btnToggleClayMode.setTextColor(ContextCompat.getColor(ctx, R.color.accent300))
        } else {
            bd.btnToggleClayMode.text = "입체 모형"
            bd.btnToggleClayMode.setBackgroundResource(R.drawable.bg_scan_card)
            bd.btnToggleClayMode.setTextColor(ContextCompat.getColor(ctx, R.color.subColor100))
        }
    }

    private fun observeScanResult() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ivm.scanResultInfo.collect { info ->
                    if (info == null) {
                        bd.tvIModelScanDate.text = ""
                        bd.tvIModelScanStatus.isVisible = true
                        bd.tvIModelScanStatus.text = "표시할 3D 스캔 결과가 없습니다."
                        return@collect
                    }

                    bd.tvIModelScanDate.text = info.measuredDate

                    val filePath = info.modelFilePath ?: info.modelUri?.takeIf { it.scheme == "file" }?.path
                    val glbFile = filePath?.let { File(it) }?.takeIf { it.exists() && it.isFile }
                    val contentUri = if (glbFile == null) info.modelUri else null

                    if (glbFile == null && contentUri == null) {
                        bd.tvIModelScanStatus.isVisible = true
                        bd.tvIModelScanStatus.text = "저장된 GLB 파일을 찾을 수 없습니다."
                        return@collect
                    }

                    val loadKey = glbFile?.absolutePath ?: contentUri.toString()
                    if (lastLoadedKey != loadKey) {
                        lastLoadedKey = loadKey
                        activeGlbFile = glbFile
                        activeGlbUri = contentUri
                        loadGlbIntoViewer()
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        bd.wvIModelScanViewer.apply {
            setBackgroundColor(Color.parseColor("#161214"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(ViewerJsBridge(), "AndroidViewerBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url ?: return null
                    if (reqUrl.path == VIRTUAL_MODEL_PATH) {
                        val corsHeaders = mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Access-Control-Allow-Methods" to "GET, OPTIONS",
                            "Access-Control-Allow-Headers" to "*"
                        )

                        if (request.method.equals("OPTIONS", ignoreCase = true)) {
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                200,
                                "OK",
                                corsHeaders,
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }

                        val file = activeGlbFile
                        if (file != null && file.exists()) {
                            return try {
                                WebResourceResponse(
                                    "model/gltf-binary",
                                    null,
                                    200,
                                    "OK",
                                    corsHeaders + mapOf("Content-Length" to file.length().toString()),
                                    FileInputStream(file)
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }

                        val uri = activeGlbUri
                        if (uri != null) {
                            return try {
                                val stream = context?.contentResolver?.openInputStream(uri)
                                if (stream != null) {
                                    WebResourceResponse(
                                        "model/gltf-binary",
                                        null,
                                        200,
                                        "OK",
                                        corsHeaders,
                                        stream
                                    )
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
        }
    }

    private fun loadGlbIntoViewer() {
        isClayMode = false
        updateToggleButtonUi()
        bd.btnToggleClayMode.isVisible = false
        bd.tvIModelScanStatus.isVisible = true
        bd.tvIModelScanStatus.text = "3D 모델을 불러오는 중..."

        val modelSrc = "$VIRTUAL_ORIGIN$VIRTUAL_MODEL_PATH?t=${System.currentTimeMillis()}"
        val html = """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
              <script type="importmap">
                {
                  "imports": {
                    "three": "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js",
                    "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/"
                  }
                }
              </script>
              <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #161214; overflow: hidden; }
                canvas { display: block; width: 100%; height: 100%; touch-action: none; }
              </style>
            </head>
            <body>
              <script type="module">
                import * as THREE from 'three';
                import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
                import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

                // ================== 조정 가능한 값 ==================
                const AMBIENT   = 0.40;   // 주변광 (올리면 전체가 밝고 음영이 약해짐)
                const HEADLIGHT = 0.60;   // 카메라 방향 조명 (올리면 입체감/대비 증가)
                const BRIGHTNESS = 1.08;   // 최종 밝기 배율 (피부톤을 살짝 올리려면 1.08 ~ 1.15)
                const BG_COLOR  = 0x161214;
                const AZIMUTH_LIMIT_DEG = 80;   // 좌우 회전 한계
                const POLAR_MIN_DEG = 40;       // 위에서 내려다보는 한계 (작을수록 더 위)
                const POLAR_MAX_DEG = 140;      // 아래에서 올려다보는 한계
                const INIT_ZOOM = 1.25;         // 초기 확대 배율 (1.25배 확대 상태로 시작)
                const ZOOM_MIN = 0.55;          // 최대 확대 한계 (기본 거리 대비)
                const ZOOM_MAX = 0.96;          // 최대 축소 한계 (초기 0.80 대비 아주 조금만 축소 허용)
                const FOV = 35;
                // ====================================================

                const renderer = new THREE.WebGLRenderer({ antialias: true });
                renderer.setPixelRatio(window.devicePixelRatio);
                renderer.setSize(window.innerWidth, window.innerHeight);
                // MeshLab 처럼 vertex color 값을 변환 없이 그대로 표시 (sRGB 값 그대로 출력)
                renderer.outputColorSpace = THREE.LinearSRGBColorSpace;
                renderer.toneMapping = THREE.NoToneMapping;
                document.body.appendChild(renderer.domElement);

                const scene = new THREE.Scene();
                scene.background = new THREE.Color(BG_COLOR);

                const camera = new THREE.PerspectiveCamera(FOV, window.innerWidth / window.innerHeight, 0.01, 1000);
                scene.add(camera);

                // three r155+ 는 조명 단위가 π 배 필요
                const ambientLight = new THREE.AmbientLight(0xffffff, AMBIENT * BRIGHTNESS * Math.PI);
                scene.add(ambientLight);

                const headlight = new THREE.DirectionalLight(0xffffff, HEADLIGHT * BRIGHTNESS * Math.PI);
                headlight.position.set(0, 0, 1);
                headlight.target.position.set(0, 0, -1);
                camera.add(headlight);
                camera.add(headlight.target);

                // 단색(수은/입체) 모드용 비스듬한 키 라이트 (평소에는 꺼짐)
                const reliefLight = new THREE.DirectionalLight(0xffffff, 0);
                reliefLight.position.set(0.6, 0.8, 0.7);
                reliefLight.target.position.set(0, 0, -1);
                camera.add(reliefLight);
                camera.add(reliefLight.target);

                const controls = new OrbitControls(camera, renderer.domElement);
                controls.enablePan = false;
                controls.enableDamping = true;
                controls.dampingFactor = 0.08;
                controls.rotateSpeed = 0.8;

                const loadedMeshes = [];
                window.setMeshMode = (isClay) => {
                  loadedMeshes.forEach((o) => {
                    o.material = isClay ? o.userData.clayMat : o.userData.colorMat;
                  });
                  if (isClay) {
                    ambientLight.intensity = 0.25 * Math.PI;
                    headlight.intensity = 0.45 * Math.PI;
                    reliefLight.intensity = 0.50 * Math.PI;
                  } else {
                    ambientLight.intensity = AMBIENT * BRIGHTNESS * Math.PI;
                    headlight.intensity = HEADLIGHT * BRIGHTNESS * Math.PI;
                    reliefLight.intensity = 0;
                  }
                };

                new GLTFLoader().load(
                  "$modelSrc",
                  (gltf) => {
                    const root = gltf.scene;
                    root.traverse((o) => {
                      if (o.isMesh) {
                        const hasColor = !!o.geometry.attributes.color;
                        const colorMat = new THREE.MeshLambertMaterial({
                          vertexColors: hasColor,
                          color: 0xffffff,
                          side: THREE.DoubleSide
                        });
                        const clayMat = new THREE.MeshPhongMaterial({
                          vertexColors: false,
                          color: 0xa2a8b2,
                          specular: 0x7c838e,
                          shininess: 42,
                          side: THREE.DoubleSide
                        });
                        o.userData.colorMat = colorMat;
                        o.userData.clayMat = clayMat;
                        o.material = colorMat;
                        loadedMeshes.push(o);
                      }
                    });
                    scene.add(root);

                    const box = new THREE.Box3().setFromObject(root);
                    const center = box.getCenter(new THREE.Vector3());
                    const size = box.getSize(new THREE.Vector3());
                    root.position.sub(center);

                    const maxDim = Math.max(size.x, size.y, size.z);
                    const dist = (maxDim / 2) / Math.tan(THREE.MathUtils.degToRad(FOV / 2)) * 1.15;
                    const initDist = dist / INIT_ZOOM;
                    camera.position.set(0, 0, initDist);
                    camera.near = dist / 100;
                    camera.far = dist * 10;
                    camera.updateProjectionMatrix();

                    controls.target.set(0, 0, 0);
                    controls.minDistance = dist * ZOOM_MIN;
                    controls.maxDistance = dist * ZOOM_MAX;
                    controls.minAzimuthAngle = -THREE.MathUtils.degToRad(AZIMUTH_LIMIT_DEG);
                    controls.maxAzimuthAngle = THREE.MathUtils.degToRad(AZIMUTH_LIMIT_DEG);
                    controls.minPolarAngle = THREE.MathUtils.degToRad(POLAR_MIN_DEG);
                    controls.maxPolarAngle = THREE.MathUtils.degToRad(POLAR_MAX_DEG);
                    controls.update();

                    if (window.AndroidViewerBridge) { window.AndroidViewerBridge.onModelLoaded(); }
                  },
                  undefined,
                  (err) => {
                    if (window.AndroidViewerBridge) {
                      window.AndroidViewerBridge.onModelError((err && err.message) ? err.message : 'load_error');
                    }
                  }
                );

                window.addEventListener('resize', () => {
                  camera.aspect = window.innerWidth / window.innerHeight;
                  camera.updateProjectionMatrix();
                  renderer.setSize(window.innerWidth, window.innerHeight);
                });

                renderer.setAnimationLoop(() => {
                  controls.update();
                  renderer.render(scene, camera);
                });
              </script>
            </body>
            </html>
        """.trimIndent()
        bd.wvIModelScanViewer.loadDataWithBaseURL(
            "$VIRTUAL_ORIGIN/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private inner class ViewerJsBridge {
        @JavascriptInterface
        fun onModelLoaded() {
            activity?.runOnUiThread {
                _binding?.let { b ->
                    b.tvIModelScanStatus.isVisible = false
                    b.btnToggleClayMode.isVisible = true
                }
            }
        }

        @JavascriptInterface
        fun onModelError(reason: String) {
            activity?.runOnUiThread {
                _binding?.let { b ->
                    b.tvIModelScanStatus.isVisible = true
                    b.tvIModelScanStatus.text = "3D 모델을 표시하지 못했습니다. ($reason)"
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding?.wvIModelScanViewer?.apply {
            stopLoading()
            webChromeClient = null
            destroy()
        }
        _binding = null
        lastLoadedKey = null
        activeGlbFile = null
        activeGlbUri = null
        super.onDestroyView()
    }
}
