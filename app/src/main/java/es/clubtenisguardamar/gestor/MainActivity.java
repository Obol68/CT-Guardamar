package es.clubtenisguardamar.gestor;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.webkit.ConsoleMessage;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebSettings;
import android.util.Base64;
import android.util.Log;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int PICK_FILE = 41;
    private static final int SAVE_FILE = 42;
    private byte[] pendingBytes;
    private String pendingMime = "application/octet-stream";

    public class AndroidBridge {
        @JavascriptInterface
        public void saveFile(String name, String dataUrl, String mime) {
            try {
                String encoded = dataUrl.substring(dataUrl.indexOf(',') + 1);
                pendingBytes = Base64.decode(encoded, Base64.DEFAULT);
                pendingMime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(pendingMime);
                    intent.putExtra(Intent.EXTRA_TITLE, name);
                    startActivityForResult(intent, SAVE_FILE);
                });
            } catch (Exception e) { Log.e("CTGuardamar", "No se pudo preparar el archivo", e); }
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try { startActivityForResult(intent, PICK_FILE); }
                catch (Exception e) { fileCallback = null; return false; }
                return true;
            }
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                Log.d("CTGuardamarWeb", message.message() + " -- línea " + message.lineNumber());
                return true;
            }
        });
        // Serve the bundled app under a stable local HTTPS origin. This avoids
        // file:// origin restrictions that can break WebView localStorage.
        try (InputStream in = getAssets().open("index.html");
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = in.read(chunk)) != -1) buffer.write(chunk, 0, count);
            String html = buffer.toString("UTF-8");
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/assets/",
                html, "text/html", "UTF-8", null
            );
        } catch (Exception e) {
            Log.e("CTGuardamar", "No se pudo abrir la aplicación", e);
            webView.loadData("<html><body style='font:18px sans-serif;padding:24px'>" +
                "No se pudo abrir el gestor. Reinicia la aplicación." +
                "</body></html>", "text/html", "UTF-8");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        } else if (requestCode == SAVE_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBytes != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out != null) out.write(pendingBytes);
                } catch (Exception e) { Log.e("CTGuardamar", "Error guardando copia", e); }
            }
            pendingBytes = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
