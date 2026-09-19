package com.handar.browser;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ_CAMERA = 701;
    private static final String TAG = "HandARBrowser";
    private static final String HOME_URL = "https://www.google.com/";

    private FrameLayout root;
    private PreviewView previewLeft, previewRight;
    private HandOverlayView overlay;
    private FrameLayout browserGroup;
    private WebView webLeft, webRight;
    private EditText addressBar;
    private GridLayout keyboard;
    private TextView statusText;
    private Button centerButton;
    private Button leftButton;
    private Button rightButton;
    private final List<View> interactiveViews = new ArrayList<>();

    private ExecutorService cameraExecutor;
    private HandLandmarker handLandmarker;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private boolean sensorReady = false;
    private float centerYaw = 0f;
    private float centerPitch = 0f;
    private boolean centered = false;

    private long lastHandTimestamp = 0L;
    private float cursorX = -1f;
    private float cursorY = -1f;
    private boolean pinched = false;
    private boolean lastPinch = false;
    private long lastClickAt = 0L;
    private float lastCursorX = -1f;
    private float lastCursorY = -1f;

    private boolean keyboardVisible = false;
    private boolean keyboardRussian = false;
    private boolean focusInWebPage = false;

    private final String[] EN_ROWS = new String[]{
            "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"
    };
    private final String[] RU_ROWS = new String[]{
            "ЙЦУКЕНГШЩЗХ", "ФЫВАПРОЛДЖЭ", "ЯЧСМИТЬБЮ"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemBars();
        getWindow().setNavigationBarColor(0x00000000);
        cameraExecutor = Executors.newSingleThreadExecutor();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) : null;
        if (rotationSensor == null && sensorManager != null) rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        buildUi();
        configureSensors();
        if (hasCameraPermission()) startCamera(); else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    private void hideSystemBars() {
        Window w = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            w.getInsetsController().hide(WindowInsets.Type.systemBars());
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);

        previewLeft = new PreviewView(this);
        previewRight = new PreviewView(this);
        previewLeft.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewRight.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        addHalf(previewLeft, 0);
        addHalf(previewRight, 1);

        browserGroup = new FrameLayout(this);
        FrameLayout.LayoutParams bg = new FrameLayout.LayoutParams(-1, -1);
        browserGroup.setLayoutParams(bg);
        root.addView(browserGroup);
        setupBrowserPair();

        overlay = new HandOverlayView(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        root.addView(overlay);

        addTopHud();
        addBottomControls();
        addKeyboard();
        overlay.bringToFront();
    }

    private void addHalf(View view, int side) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, -1);
        lp.gravity = side == 0 ? Gravity.START : Gravity.END;
        root.addView(view, lp);
        view.setAlpha(0.98f);
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int half = Math.max(1, (right - left) / 2);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            params.width = half;
            params.height = Math.max(1, bottom - top);
            params.leftMargin = side == 0 ? 0 : half;
            params.rightMargin = 0;
            params.gravity = Gravity.TOP | Gravity.START;
            view.setLayoutParams(params);
        });
    }

    private void setupBrowserPair() {
        webLeft = createWebView();
        webRight = createWebView();
        addStereoWeb(webLeft, 0);
        addStereoWeb(webRight, 1);
        webLeft.loadUrl(HOME_URL);
        webRight.loadUrl(HOME_URL);
    }

    private WebView createWebView() {
        WebView v = new WebView(this);
        v.setBackgroundColor(0xEE101318);
        WebSettings s = v.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setUserAgentString(s.getUserAgentString() + " HandARBrowser/1.0");
        v.setWebChromeClient(new WebChromeClient());
        v.addJavascriptInterface(new WebBridge(), "HandARBridge");
        v.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                installPageHooks(view);
                runOnUiThread(() -> statusTextSafe("Браузер: " + url));
            }
        });
        return v;
    }

    private void addStereoWeb(View v, int side) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        lp.gravity = Gravity.TOP | Gravity.START;
        browserGroup.addView(v, lp);
        v.setAlpha(0.94f);
        browserGroup.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int fullW = Math.max(1, right - left);
            int fullH = Math.max(1, bottom - top);
            int half = fullW / 2;
            int marginX = Math.round(half * 0.07f);
            int webW = Math.max(1, half - marginX * 2);
            int webH = Math.round(fullH * 0.56f);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) v.getLayoutParams();
            params.width = webW;
            params.height = webH;
            params.leftMargin = side == 0 ? marginX : half + marginX;
            params.topMargin = Math.round(fullH * 0.13f);
            params.gravity = Gravity.TOP | Gravity.START;
            v.setLayoutParams(params);
        });
    }

    private void addTopHud() {
        LinearLayout bar = glassPanel(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(58));
        lp.leftMargin = dp(24); lp.rightMargin = dp(24); lp.topMargin = dp(18);
        root.addView(bar, lp);

        TextView title = text("● HandARBrowser Android", 17, 0xFFFFFFFF);
        bar.addView(title, new LinearLayout.LayoutParams(dp(240), -1));
        statusText = text("Камера запускается...", 14, 0xFFBFC7D5);
        LinearLayout.LayoutParams st = new LinearLayout.LayoutParams(0, -1, 1f);
        st.gravity = Gravity.CENTER_VERTICAL;
        bar.addView(statusText, st);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setText(HOME_URL);
        addressBar.setTextColor(0xFFFFFFFF);
        addressBar.setHintTextColor(0xFF8F98A8);
        addressBar.setTextSize(13);
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setPadding(dp(12), 0, dp(12), 0);
        addressBar.setBackgroundColor(0x551A212B);
        if (android.os.Build.VERSION.SDK_INT >= 21) addressBar.setShowSoftInputOnFocus(false);
        bar.addView(addressBar, new LinearLayout.LayoutParams(dp(460), dp(42)));

        Button go = smallButton("GO");
        go.setOnClickListener(v -> loadAddress());
        bar.addView(go, new LinearLayout.LayoutParams(dp(64), dp(42)));
        interactiveViews.add(go);
        interactiveViews.add(addressBar);
    }

    private void addBottomControls() {
        LinearLayout controls = glassPanel(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dp(64));
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(24);
        root.addView(controls, lp);

        Button back = smallButton("←");
        Button forward = smallButton("→");
        Button reload = smallButton("↻");
        leftButton = smallButton("LEFT");
        centerButton = smallButton("CENTER");
        rightButton = smallButton("RIGHT");
        Button key = smallButton("⌨");

        back.setOnClickListener(v -> bothWeb("history.back();"));
        forward.setOnClickListener(v -> bothWeb("history.forward();"));
        reload.setOnClickListener(v -> { webLeft.reload(); webRight.reload(); });
        centerButton.setOnClickListener(v -> centerSensors());
        leftButton.setOnClickListener(v -> nudgeBrowser(-35f));
        rightButton.setOnClickListener(v -> nudgeBrowser(35f));
        key.setOnClickListener(v -> setKeyboardVisible(!keyboardVisible));

        for (Button b : new Button[]{back, forward, reload, leftButton, centerButton, rightButton, key}) {
            controls.addView(b, new LinearLayout.LayoutParams(dp(74), dp(50)));
            interactiveViews.add(b);
        }
    }

    private void addKeyboard() {
        keyboard = new GridLayout(this);
        keyboard.setColumnCount(10);
        keyboard.setRowCount(5);
        keyboard.setPadding(dp(12), dp(10), dp(12), dp(10));
        keyboard.setBackgroundColor(0xEE11161E);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(250));
        lp.gravity = Gravity.BOTTOM;
        lp.leftMargin = dp(20); lp.rightMargin = dp(20); lp.bottomMargin = dp(10);
        root.addView(keyboard, lp);
        keyboard.setVisibility(View.GONE);
        rebuildKeyboard();
    }

    private void rebuildKeyboard() {
        if (keyboard == null) return;
        keyboard.removeAllViews();
        String[] rows = keyboardRussian ? RU_ROWS : EN_ROWS;
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int i = 0; i < row.length(); i++) {
                final String key = String.valueOf(row.charAt(i));
                Button b = keyboardButton(key);
                GridLayout.Spec rs = GridLayout.spec(r);
                GridLayout.Spec cs = GridLayout.spec(i, 1f);
                GridLayout.LayoutParams p = new GridLayout.LayoutParams(rs, cs);
                p.width = 0; p.height = dp(55); p.setMargins(dp(3), dp(3), dp(3), dp(3));
                keyboard.addView(b, p);
                interactiveViews.add(b);
            }
        }
        Button lang = keyboardButton(keyboardRussian ? "EN" : "RU");
        lang.setOnClickListener(v -> { keyboardRussian = !keyboardRussian; rebuildKeyboard(); });
        GridLayout.LayoutParams p1 = new GridLayout.LayoutParams(GridLayout.spec(3), GridLayout.spec(0, 1f));
        p1.width = 0; p1.height = dp(55); p1.setMargins(dp(3), dp(3), dp(3), dp(3));
        keyboard.addView(lang, p1);

        Button space = keyboardButton("SPACE");
        space.setOnClickListener(v -> sendTextToTarget(" "));
        GridLayout.LayoutParams p2 = new GridLayout.LayoutParams(GridLayout.spec(3), GridLayout.spec(1, 7f));
        p2.width = 0; p2.height = dp(55); p2.setMargins(dp(3), dp(3), dp(3), dp(3));
        keyboard.addView(space, p2);

        Button back = keyboardButton("⌫");
        back.setOnClickListener(v -> sendBackspace());
        GridLayout.LayoutParams p3 = new GridLayout.LayoutParams(GridLayout.spec(3), GridLayout.spec(8, 1f));
        p3.width = 0; p3.height = dp(55); p3.setMargins(dp(3), dp(3), dp(3), dp(3));
        keyboard.addView(back, p3);

        Button enter = keyboardButton("ENTER");
        enter.setOnClickListener(v -> handleEnter());
        GridLayout.LayoutParams p4 = new GridLayout.LayoutParams(GridLayout.spec(4), GridLayout.spec(0, 10f));
        p4.width = 0; p4.height = dp(55); p4.setMargins(dp(3), dp(3), dp(3), dp(3));
        keyboard.addView(enter, p4);
    }

    private Button keyboardButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(label.length() > 3 ? 11 : 14);
        b.setAllCaps(false);
        b.setBackgroundColor(0xFF26303D);
        if (label.length() == 1 && !label.equals("⌫")) b.setOnClickListener(v -> sendTextToTarget(label));
        return b;
    }

    private LinearLayout glassPanel(int orientation) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(orientation);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(10), dp(8), dp(10), dp(8));
        p.setBackgroundColor(0xB911151C);
        return p;
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(label.length() > 5 ? 11 : 13);
        b.setAllCaps(false);
        b.setBackgroundColor(0x99212A36);
        return b;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void setupMediaPipe() {
        try {
            BaseOptions baseOptions = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build();
            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(0.50f)
                    .setMinHandPresenceConfidence(0.50f)
                    .setMinTrackingConfidence(0.50f)
                    .setNumHands(2)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::onHandResult)
                    .setErrorListener(error -> Log.e(TAG, "MediaPipe: " + error.getMessage()))
                    .build();
            handLandmarker = HandLandmarker.createFromOptions(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize hand tracking", e);
            runOnUiThread(() -> statusTextSafe("Руки: ошибка загрузки модели"));
        }
    }

    private void startCamera() {
        statusTextSafe("Камера запускается...");
        setupMediaPipe();
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera provider failed", e);
                statusTextSafe("Камера: ошибка запуска");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null) return;
        cameraProvider.unbindAll();
        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;

        Preview p1 = new Preview.Builder().build();
        Preview p2 = new Preview.Builder().build();
        p1.setSurfaceProvider(previewLeft.getSurfaceProvider());
        p2.setSurfaceProvider(previewRight.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setImageQueueDepth(2)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        try {
            camera = cameraProvider.bindToLifecycle(this, selector, p1, p2, analysis);
            statusTextSafe("Камера: OK • руки: поиск...");
        } catch (Exception e) {
            Log.e(TAG, "Stereo camera bind failed; using one preview", e);
            try {
                cameraProvider.unbindAll();
                Preview fallback = new Preview.Builder().build();
                fallback.setSurfaceProvider(previewLeft.getSurfaceProvider());
                camera = cameraProvider.bindToLifecycle(this, selector, fallback, analysis);
                previewRight.setVisibility(View.GONE);
                statusTextSafe("Камера: OK • режим совместимости");
            } catch (Exception second) {
                Log.e(TAG, "Fallback camera bind failed", second);
                statusTextSafe("Камера: не удалось запустить");
            }
        }
    }

    private void analyzeFrame(ImageProxy proxy) {
        try {
            if (handLandmarker == null) return;
            int w = proxy.getWidth();
            int h = proxy.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            proxy.getPlanes()[0].getBuffer().rewind();
            bitmap.copyPixelsFromBuffer(proxy.getPlanes()[0].getBuffer());
            int rotation = proxy.getImageInfo().getRotationDegrees();
            Bitmap rotated = rotateBitmap(bitmap, rotation);
            if (rotated != bitmap) bitmap.recycle();
            MPImage mpImage = new BitmapImageBuilder(rotated).build();
            long timestamp = SystemClock.uptimeMillis();
            handLandmarker.detectAsync(mpImage, timestamp);
        } catch (Throwable t) {
            Log.e(TAG, "Frame analysis failed", t);
        } finally {
            proxy.close();
        }
    }

    private Bitmap rotateBitmap(Bitmap source, int degrees) {
        if (degrees == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void onHandResult(HandLandmarkerResult result, MPImage input) {
        if (result == null || result.landmarks() == null) return;
        List<List<PointF>> mappedHands = new ArrayList<>();
        PointF bestCursor = null;
        boolean bestPinch = false;
        float bestScore = Float.MAX_VALUE;

        for (List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> hand : result.landmarks()) {
            List<PointF> mapped = new ArrayList<>();
            for (com.google.mediapipe.tasks.components.containers.NormalizedLandmark lm : hand) {
                mapped.add(mapNormalizedToScreen(lm.x(), lm.y(), input.width(), input.height()));
            }
            mappedHands.add(mapped);
            if (hand.size() >= 9) {
                PointF index = mapNormalizedToScreen(hand.get(HandLandmark.INDEX_FINGER_TIP).x(), hand.get(HandLandmark.INDEX_FINGER_TIP).y(), input.width(), input.height());
                com.google.mediapipe.tasks.components.containers.NormalizedLandmark thumbLm = hand.get(HandLandmark.THUMB_TIP);
                float pinchDistance = distance(index.x, index.y,
                        mapNormalizedToScreen(thumbLm.x(), thumbLm.y(), input.width(), input.height()).x,
                        mapNormalizedToScreen(thumbLm.x(), thumbLm.y(), input.width(), input.height()).y);
                float distToCenter = distance(index.x, index.y, getScreenWidth() / 2f, getScreenHeight() / 2f);
                if (distToCenter < bestScore) {
                    bestScore = distToCenter;
                    bestCursor = index;
                    bestPinch = pinchDistance < Math.min(getScreenWidth(), getScreenHeight()) * 0.055f;
                }
            }
        }

        final PointF cursor = bestCursor;
        final boolean pinch = bestPinch;
        runOnUiThread(() -> {
            overlay.setHands(mappedHands);
            if (cursor != null) {
                cursorX = cursor.x; cursorY = cursor.y; pinched = pinch;
                overlay.setCursor(cursorX, cursorY, pinched);
                if (pinched && !lastPinch && SystemClock.uptimeMillis() - lastClickAt > 220) {
                    lastClickAt = SystemClock.uptimeMillis();
                    performHandClick(cursorX, cursorY);
                }
                if (lastCursorX >= 0f) {
                    float dy = cursorY - lastCursorY;
                    if (lastPinch && pinch && Math.abs(dy) > 10f && focusInWebPage) {
                        performScroll(-dy * 3f);
                    }
                }
                lastCursorX = cursorX; lastCursorY = cursorY;
            } else {
                cursorX = cursorY = -1f;
                pinched = false;
                overlay.setCursor(-1f, -1f, false);
                lastCursorX = lastCursorY = -1f;
            }
            lastPinch = pinch;
        });
    }

    private PointF mapNormalizedToScreen(float nx, float ny, int imageW, int imageH) {
        float vw = getScreenWidth();
        float vh = getScreenHeight();
        float scale = Math.max(vw / imageW, vh / imageH);
        float dw = imageW * scale;
        float dh = imageH * scale;
        float ox = (vw - dw) / 2f;
        float oy = (vh - dh) / 2f;
        return new PointF(ox + nx * dw, oy + ny * dh);
    }

    private void performHandClick(float x, float y) {
        if (keyboardVisible && isPointInside(keyboard, x, y)) {
            View target = findChildAt(keyboard, x, y);
            if (target != null) { target.performClick(); return; }
        }
        if (clickNativeIfHit(addressBar, x, y)) return;
        for (View v : interactiveViews) {
            if (v == addressBar) continue;
            if (v.getVisibility() == View.VISIBLE && clickNativeIfHit(v, x, y)) return;
        }
        if (y >= dp(105) && y <= dp(525)) {
            if (x < getScreenWidth() / 2f) clickWeb(webLeft, x, y, 0);
            else clickWeb(webRight, x, y, 1);
        }
    }

    private boolean clickNativeIfHit(View view, float x, float y) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isShown()) return false;
        int[] loc = new int[2]; view.getLocationOnScreen(loc);
        if (x >= loc[0] && x <= loc[0] + view.getWidth() && y >= loc[1] && y <= loc[1] + view.getHeight()) {
            view.performClick();
            if (view == addressBar) {
                focusInWebPage = false;
                setKeyboardVisible(true);
            }
            return true;
        }
        return false;
    }

    private boolean isPointInside(View v, float x, float y) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2]; v.getLocationOnScreen(loc);
        return x >= loc[0] && x <= loc[0] + v.getWidth() && y >= loc[1] && y <= loc[1] + v.getHeight();
    }

    private View findChildAt(ViewGroup parent, float x, float y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && isPointInside(child, x, y)) return child;
        }
        return null;
    }

    private void clickWeb(WebView web, float screenX, float screenY, int side) {
        if (web == null) return;
        int[] loc = new int[2]; web.getLocationOnScreen(loc);
        float localX = screenX - loc[0];
        float localY = screenY - loc[1];
        if (localX < 0 || localY < 0 || localX > web.getWidth() || localY > web.getHeight()) return;
        String js = String.format(Locale.US,
                "(function(){var e=document.elementFromPoint(%f,%f); if(e){e.focus(); e.click(); e.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));}})();",
                localX / density(web), localY / density(web));
        web.evaluateJavascript(js, null);
        WebView other = side == 0 ? webRight : webLeft;
        other.evaluateJavascript(js, null);
    }

    private float density(View v) { return getResources().getDisplayMetrics().density; }

    private void performScroll(float delta) {
        String js = "window.scrollBy({top:" + delta + ",left:0,behavior:'auto'});";
        bothWeb(js);
    }

    private void bothWeb(String js) {
        webLeft.evaluateJavascript(js, null);
        webRight.evaluateJavascript(js, null);
    }

    private void installPageHooks(WebView web) {
        String js = "(function(){if(window.__handarInstalled)return;window.__handarInstalled=true;document.addEventListener('focusin',function(){HandARBridge.focused(true);});document.addEventListener('focusout',function(){HandARBridge.focused(false);});})();";
        web.evaluateJavascript(js, null);
    }

    private void loadAddress() {
        String url = addressBar.getText().toString().trim();
        if (url.isEmpty()) return;
        if (!url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) url = "https://" + url;
        webLeft.loadUrl(url); webRight.loadUrl(url); addressBar.setText(url);
    }

    private void handleEnter() {
        if (focusInWebPage) sendTextToTarget("\n");
        else loadAddress();
    }

    private void sendTextToTarget(String text) {
        if (!focusInWebPage) {
            int p = addressBar.getSelectionStart();
            if (p < 0) p = addressBar.getText().length();
            addressBar.getText().insert(p, text);
            return;
        }
        String escaped = android.text.TextUtils.htmlEncode(text).replace("\\", "\\\\").replace("'", "\\'");
        String js = "(function(){var e=document.activeElement;if(!e)return; if(e.isContentEditable){document.execCommand('insertText',false,'" + escaped + "');} else {var s=e.selectionStart,t=e.selectionEnd,v=e.value||'';e.value=v.slice(0,s)+'" + escaped + "'+v.slice(t);e.dispatchEvent(new Event('input',{bubbles:true}));}})();";
        bothWeb(js);
    }

    private void sendBackspace() {
        if (!focusInWebPage) {
            int p = addressBar.getSelectionStart();
            if (p > 0) addressBar.getText().delete(p - 1, p);
            return;
        }
        bothWeb("(function(){var e=document.activeElement;if(!e)return;if(e.isContentEditable){document.execCommand('delete');}else{var p=e.selectionStart||0;e.value=(e.value||'').slice(0,Math.max(0,p-1))+(e.value||'').slice(e.selectionEnd||p);e.selectionStart=e.selectionEnd=Math.max(0,p-1);e.dispatchEvent(new Event('input',{bubbles:true}));}})();");
    }

    private void setKeyboardVisible(boolean visible) {
        keyboardVisible = visible;
        if (keyboard != null) keyboard.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) keyboard.bringToFront(); else overlay.bringToFront();
    }

    private void centerSensors() {
        centered = false;
        centerYaw = centerPitch = 0f;
        centered = true;
        browserGroup.setTranslationX(0f);
        browserGroup.setTranslationY(0f);
        statusTextSafe("Центр: калибровка установлена");
    }

    private void nudgeBrowser(float amount) { browserGroup.setTranslationX(browserGroup.getTranslationX() + amount); }

    private void configureSensors() {
        if (sensorManager == null || rotationSensor == null) return;
        sensorReady = true;
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (!centered || event.sensor != rotationSensor) return;
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);
        if (!centered) { centerYaw = orientation[0]; centerPitch = orientation[1]; centered = true; }
        float yawDelta = wrapAngle(orientation[0] - centerYaw);
        float pitchDelta = orientation[1] - centerPitch;
        final float tx = Math.max(-360f, Math.min(360f, -yawDelta * 420f));
        final float ty = Math.max(-220f, Math.min(220f, pitchDelta * 260f));
        runOnUiThread(() -> browserGroup.setTranslationX(tx));
        runOnUiThread(() -> browserGroup.setTranslationY(ty));
    }

    private float wrapAngle(float a) {
        while (a > Math.PI) a -= (float)(2 * Math.PI);
        while (a < -Math.PI) a += (float)(2 * Math.PI);
        return a;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    private int getScreenWidth() { return root != null && root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels; }
    private int getScreenHeight() { return root != null && root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels; }

    private void statusTextSafe(String text) {
        if (statusText != null) statusText.setText(text);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else Toast.makeText(this, "Нужен доступ к камере", Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (sensorReady && sensorManager != null && rotationSensor != null) sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override protected void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override protected void onDestroy() {
        try { if (handLandmarker != null) handLandmarker.close(); } catch (Exception ignored) {}
        if (cameraExecutor != null) cameraExecutor.shutdownNow();
        if (webLeft != null) webLeft.destroy();
        if (webRight != null) webRight.destroy();
        super.onDestroy();
    }

    public class WebBridge {
        @JavascriptInterface public void focused(boolean focused) {
            runOnUiThread(() -> {
                focusInWebPage = focused;
                if (focused) setKeyboardVisible(true);
            });
        }
    }
}
