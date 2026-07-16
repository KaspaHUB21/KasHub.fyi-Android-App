package kaspatokenexplorer.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://kashub.fyi/";
    private static final int EXTRA_SYSTEM_BAR_PADDING_DP = 8;
    private static final int FILE_CHOOSER_REQUEST_CODE = 21;
    private static final int EXPORT_FILE_REQUEST_CODE = 22;
    private static final String EXPORT_PROMPT_PREFIX = "KASHUB_EXPORT:";
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private byte[] pendingExportBytes;
    private String pendingExportFilename;
    private String pendingExportMimeType;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        getWindow().setStatusBarColor(Color.rgb(2, 11, 16));
        getWindow().setNavigationBarColor(Color.rgb(2, 11, 16));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(2, 11, 16));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(2, 11, 16));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);
        applySystemBarPadding(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " KasHubAndroid/1.0.7");

        KasHubBridge bridge = new KasHubBridge();
        webView.addJavascriptInterface(bridge, "KasHubAndroid");
        webView.addJavascriptInterface(bridge, "Android");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                    return true;
                } catch (Exception error) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "File picker unavailable", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                if (message != null && message.startsWith(EXPORT_PROMPT_PREFIX)) {
                    result.confirm("");
                    handleExportPrompt(message.substring(EXPORT_PROMPT_PREFIX.length()));
                    return true;
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) {
                    return false;
                }
                String host = uri.getHost();
                if ("kashub.fyi".equalsIgnoreCase(host) || "kaspatoken.kaslab.space".equalsIgnoreCase(host)) {
                    return false;
                }
                openExternal(uri);
                return true;
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            Uri launchUri = getIntent() != null ? getIntent().getData() : null;
            webView.loadUrl(launchUri != null ? launchUri.toString() : HOME_URL);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_FILE_REQUEST_CODE) {
            handleExportResult(resultCode, data);
            return;
        }
        if (requestCode != FILE_CHOOSER_REQUEST_CODE || filePathCallback == null) {
            return;
        }
        Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private void openExternal(Uri uri) {
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void applySystemBarPadding(View root) {
        final int extraPadding = Math.round(EXTRA_SYSTEM_BAR_PADDING_DP * getResources().getDisplayMetrics().density);
        root.setPadding(0, systemDimension("status_bar_height") + extraPadding, 0, systemDimension("navigation_bar_height") + extraPadding);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(0, systemBars.top + extraPadding, 0, systemBars.bottom + extraPadding);
                return insets;
            });
            root.post(root::requestApplyInsets);
            return;
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop() + extraPadding, 0, insets.getSystemWindowInsetBottom() + extraPadding);
            return insets;
        });
        root.post(root::requestApplyInsets);
    }

    private int systemDimension(String name) {
        int resourceId = getResources().getIdentifier(name, "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private class KasHubBridge {
        @JavascriptInterface
        public void saveTextFile(String filename, String content, String mimeType) {
            runOnUiThread(() -> {
                try {
                    requestExportFile(filename, content.getBytes(StandardCharsets.UTF_8), mimeType);
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Export failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void saveTextFileBase64(String filename, String base64Content, String mimeType) {
            runOnUiThread(() -> {
                try {
                    requestExportFile(filename, Base64.decode(base64Content, Base64.DEFAULT), mimeType);
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Export failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void requestExportFile(String filename, byte[] bytes, String mimeType) throws Exception {
        String safeFilename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String resolvedMimeType = mimeType == null || mimeType.isEmpty() ? "text/plain" : mimeType.split(";")[0];
        pendingExportBytes = bytes;
        pendingExportFilename = safeFilename;
        pendingExportMimeType = resolvedMimeType;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(resolvedMimeType);
        intent.putExtra(Intent.EXTRA_TITLE, safeFilename);
        startActivityForResult(intent, EXPORT_FILE_REQUEST_CODE);
    }

    private void handleExportPrompt(String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            String filename = json.optString("filename", "kashub-export.json");
            String mimeType = json.optString("mimeType", "application/json");
            byte[] bytes = Base64.decode(json.getString("base64Content"), Base64.DEFAULT);
            requestExportFile(filename, bytes, mimeType);
        } catch (Exception error) {
            Toast.makeText(MainActivity.this, "Export failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleExportResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            clearPendingExport();
            return;
        }
        try {
            saveBytesToUri(data.getData(), pendingExportBytes);
            Toast.makeText(MainActivity.this, "Saved " + pendingExportFilename, Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(MainActivity.this, "Export failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            clearPendingExport();
        }
    }

    private void clearPendingExport() {
        pendingExportBytes = null;
        pendingExportFilename = null;
        pendingExportMimeType = null;
    }

    private void saveBytesToUri(Uri uri, byte[] bytes) throws Exception {
        if (bytes == null) {
            throw new IllegalStateException("No export data");
        }
        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
            if (stream == null) {
                throw new IllegalStateException("Could not open file");
            }
            stream.write(bytes);
        }
    }

    private void saveBytesToDownloads(String filename, byte[] bytes, String mimeType) throws Exception {
        String safeFilename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String resolvedMimeType = mimeType == null || mimeType.isEmpty() ? "text/plain" : mimeType.split(";")[0];

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, safeFilename);
            values.put(MediaStore.Downloads.MIME_TYPE, resolvedMimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("Could not create download");
            }
            try (OutputStream stream = resolver.openOutputStream(uri)) {
                if (stream == null) {
                    throw new IllegalStateException("Could not open download");
                }
                stream.write(bytes);
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return;
        }

        File downloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloads == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            downloads = getDataDir();
        }
        if (downloads == null) {
            throw new IllegalStateException("No download directory");
        }
        if (!downloads.exists()) {
            downloads.mkdirs();
        }
        try (OutputStream stream = new FileOutputStream(new File(downloads, safeFilename))) {
            stream.write(bytes);
        }
    }
}
