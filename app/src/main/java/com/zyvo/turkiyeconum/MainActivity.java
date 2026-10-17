package com.zyvo.turkiyeconum;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

/**
 * WebView kabugu: HTML haritayi yukler, Android konum servisinden
 * gelen canli GPS guncellemelerini sayfaya evaluateJavascript ile iteler.
 * JS -> Android koprusu: Android.start() / .stop() / .permission() / .hasPermission()
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_LOC = 41;

    private WebView web;
    private LocationManager lm;
    private boolean tracking = false;
    private long lastSatsPush = 0;

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location loc) { push(loc); }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        @Override public void onProviderEnabled(@NonNull String provider) {
            if (tracking) sendState("tracking");
        }
        @Override public void onProviderDisabled(@NonNull String provider) {
            if (tracking) sendState("gps_off");
        }
    };

    private final GnssStatus.Callback gnssCb = new GnssStatus.Callback() {
        @Override public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
            long now = System.currentTimeMillis();
            if (now - lastSatsPush < 2000) return;
            lastSatsPush = now;
            int used = 0;
            for (int i = 0; i < status.getSatelliteCount(); i++) {
                if (status.usedInFix(i)) used++;
            }
            eval("window.__onState&&window.__onState('sats'," + used + ");");
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return false; // hepsi uygulama icinde kalsin
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");

        web.loadUrl("file:///android_asset/index.html");

        if (granted()) {
            eval("window.__onPermission&&window.__onPermission(true);");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        }
    }

    // ---------- izin sonucu ----------
    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code != REQ_LOC) return;
        boolean ok = granted();
        eval("window.__onPermission&&window.__onPermission(" + ok + ");");
        if (ok) startTracking();
        else sendState("permission_denied");
    }

    private boolean granted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ---------- takip ----------
    @SuppressLint("MissingPermission")
    private void startTracking() {
        if (!granted()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        boolean any = false;
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 3, listener);
                any = true;
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, listener);
                any = true;
            }
            Location best = bestLast();
            if (best != null) push(best);
            if (any) {
                tracking = true;
                sendState("tracking");
                try { lm.registerGnssStatusCallback(gnssCb, null); } catch (Exception ignored) { }
            } else {
                sendState("gps_off");
            }
        } catch (SecurityException e) {
            sendState("permission_denied");
        }
    }

    @SuppressLint("MissingPermission")
    private Location bestLast() {
        Location g = null, n = null;
        try { g = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) { }
        try { n = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) { }
        if (g == null) return n;
        if (n == null) return g;
        return (g.getTime() >= n.getTime()) ? g : n;
    }

    private void stopTracking() {
        tracking = false;
        try { lm.removeUpdates(listener); lm.unregisterGnssStatusCallback(gnssCb); } catch (Exception ignored) { }
        sendState("stopped");
    }

    // ---------- JS'e iteleme ----------
    private void push(Location loc) {
        try {
            JSONObject o = new JSONObject();
            o.put("lat", loc.getLatitude());
            o.put("lng", loc.getLongitude());
            o.put("acc", loc.hasAccuracy() ? Math.round(loc.getAccuracy()) : -1);
            o.put("speed", loc.hasSpeed() ? Math.round(loc.getSpeed() * 36f) / 10.0 : -1); // km/h
            o.put("bearing", loc.hasBearing() ? Math.round(loc.getBearing()) : -1);
            o.put("time", loc.getTime());
            o.put("provider", LocationManager.GPS_PROVIDER.equals(loc.getProvider()) ? "GPS" : "AG");
            eval("window.__onLocation&&window.__onLocation(" + o + ");");
        } catch (Exception ignored) { }
    }

    private void sendState(String st) {
        eval("window.__onState&&window.__onState('" + st + "');");
    }

    private void eval(final String js) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(js, null);
        });
    }

    // ---------- JS koprusu ----------
    public class Bridge {
        @JavascriptInterface public void start() { runOnUiThread(() -> startTracking()); }
        @JavascriptInterface public void stop() { runOnUiThread(MainActivity.this::stopTracking); }
        @JavascriptInterface public void permission() {
            runOnUiThread(() -> ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC));
        }
        @JavascriptInterface public boolean hasPermission() { return granted(); }
    }

    @Override
    protected void onDestroy() {
        try { lm.removeUpdates(listener); lm.unregisterGnssStatusCallback(gnssCb); } catch (Exception ignored) { }
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
