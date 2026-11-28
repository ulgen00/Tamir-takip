package com.cepatolyesi.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Cep Atölyesi: GitHub Pages'teki web uygulamasını açan Android kabuğu.
 * Uygulama her açılışta sitenin güncel halini yükler; kayıtlar WebView'ın
 * kendi depolamasında (localStorage) telefonda kalır.
 */
public class MainActivity extends Activity {

    static final String APP_URL = "https://ulgen00.github.io/Tamir-takip/";
    static final String APP_HOST = "ulgen00.github.io";
    static final String OFFLINE_URL = "file:///android_asset/offline.html";

    private static final int REQ_FILE = 1;
    private static final int REQ_CAMERA = 2;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bg));
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        applyInsets(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);           // localStorage: kayıtlar burada
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // barkod kamerası görüntüsü
        s.setAllowFileAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " CepAtolyesiApp/1.0");

        web.setWebViewClient(new AppClient());
        web.setWebChromeClient(new AppChrome());

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(APP_URL);
    }

    /** Durum çubuğu, gezinme çubuğu ve klavye için kenar boşluğu (Android 15 kenardan kenara düzeni). */
    private void applyInsets(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft(); t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight(); b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return insets;
        });
    }

    private class AppClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme() == null ? "" : u.getScheme();
            // Uygulamanın kendi sayfaları WebView'da açılır
            if ((scheme.equals("https") && APP_HOST.equals(u.getHost())) || u.toString().startsWith(OFFLINE_URL)) return false;
            // tel:, diğer siteler vb. telefonun ilgili uygulamasında açılır
            try {
                Intent i = scheme.equals("tel") ? new Intent(Intent.ACTION_DIAL, u) : new Intent(Intent.ACTION_VIEW, u);
                startActivity(i);
            } catch (ActivityNotFoundException ignored) { }
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            // İnternet yoksa ana sayfa yerine "bağlantı yok" ekranı
            if (req.isForMainFrame()) view.loadUrl(OFFLINE_URL);
        }
    }

    private class AppChrome extends WebChromeClient {
        // Barkod tarama: sayfa kamera isteyince Android kamera iznini sor
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                boolean wantsCamera = false;
                for (String r : request.getResources())
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
                if (!wantsCamera) { request.deny(); return; }
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    pendingPermission = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                }
            });
        }

        // "Galeriden seç" ve fotoğrafla okuma: dosya seçiciyi aç
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            try {
                startActivityForResult(Intent.createChooser(i, "Fotoğraf seç"), REQ_FILE);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_CAMERA && pendingPermission != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                pendingPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else pendingPermission.deny();
            pendingPermission = null;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == REQ_FILE && fileCallback != null) {
            Uri[] uris = null;
            if (result == RESULT_OK && data != null) {
                if (data.getData() != null) uris = new Uri[]{data.getData()};
                else if (data.getClipData() != null && data.getClipData().getItemCount() > 0)
                    uris = new Uri[]{data.getClipData().getItemAt(0).getUri()};
            }
            fileCallback.onReceiveValue(uris);
            fileCallback = null;
            return;
        }
        super.onActivityResult(code, result, data);
    }

    // Geri tuşu: önce açık ekranı/menüyü kapat, yoksa uygulamadan çık
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript(
            "(function(){try{if(document.querySelector('#scan')){stopScan();return 1}" +
            "if(document.querySelector('#sh')){closeSheet();return 1}" +
            "if(typeof closeMenu=='function'&&closeMenu())return 1}catch(e){}return 0})()",
            v -> { if (!"1".equals(v)) { if (web.canGoBack()) web.goBack(); else finish(); } });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
