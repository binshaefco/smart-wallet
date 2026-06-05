package net.adenweb.smartwallet;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private SharedPreferences sharedPreferences;

    // الرابط الأساسي للمحفظة السحابية
    private static final String BASE_WALLET_URL = "https://wallet.adenweb.net/";
    private static final String DEFAULT_SLOGAN = "default";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        sharedPreferences = getSharedPreferences("SmartWalletPrefs", Context.MODE_PRIVATE);

        // إعدادات الـ WebView
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        
        // دعم حفظ كلمات المرور والملفات المؤقتة
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        // 1. تحميل آخر سلوجان شبكة تم قراءته محلياً لتشغيل التطبيق بسرعة
        String lastSlogan = sharedPreferences.getString("network_slogan", DEFAULT_SLOGAN);
        webView.loadUrl(BASE_WALLET_URL + lastSlogan);

        // 2. محاولة تحديث اسم الشبكة عبر الاستعلام العكسي (Reverse DNS) إذا كان متصلاً بالواي فاي
        if (isWifiConnected()) {
            detectNetworkSlogan();
        }
    }

    private boolean isWifiConnected() {
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi != null && mWifi.isConnected();
    }

    private String getGatewayIp() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        return Formatter.formatIpAddress(dhcpInfo.gateway);
    }

    private void detectNetworkSlogan() {
        executorService.execute(() -> {
            try {
                String gatewayIp = getGatewayIp();
                
                // التأكد من أن الآي بي صالح وليس فارغاً
                if (gatewayIp != null && !gatewayIp.equals("0.0.0.0")) {
                    InetAddress addr = InetAddress.getByName(gatewayIp);
                    
                    // استدعاء المضيف (Reverse DNS Lookup)
                    String hostName = addr.getHostName(); // مثلاً: aden_net.local
                    
                    if (hostName != null && hostName.contains(".local")) {
                        // استخلاص اسم الشبكة فقط
                        final String networkSlogan = hostName.replace(".local", "").trim();
                        
                        mainHandler.post(() -> {
                            String cachedSlogan = sharedPreferences.getString("network_slogan", "");
                            
                            // تحديث الرابط فقط إذا كان السلوجان الجديد مختلفاً
                            if (!networkSlogan.equals(cachedSlogan)) {
                                sharedPreferences.edit().putString("network_slogan", networkSlogan).apply();
                                webView.loadUrl(BASE_WALLET_URL + networkSlogan);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown(); // إغلاق منفذ خيوط الخلفية
    }
}
