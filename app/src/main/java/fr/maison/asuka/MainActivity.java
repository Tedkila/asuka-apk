package fr.maison.asuka;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.text.InputType;
import java.net.URI;
import java.io.ByteArrayInputStream;

public class MainActivity extends Activity {
    Settings settings;
    FrameLayout root;
    WebView web;
    TextView cameraLight;
    final Handler ui = new Handler(Looper.getMainLooper());
    boolean foreground, personal;
    private long pinBlockedUntil;
    private int pinAttempts;
    private Runnable loadTimeout;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new Settings(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        showPage();
        if (!settings.hasPin()) ui.post(() -> choosePin(this::openSettings));
    }
    @Override protected void onResume() { super.onResume(); foreground = true; }
    @Override protected void onPause() { foreground = false; super.onPause(); }
    @Override protected void onDestroy() { clearWeb(); super.onDestroy(); }
    void frame() {
        clearWeb(); root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK); setContentView(root);
    }
    void clearWeb() {
        if (loadTimeout != null) ui.removeCallbacks(loadTimeout);
        if (web != null) { web.stopLoading(); web.loadUrl("about:blank"); web.clearHistory(); web.clearCache(true); web.destroy(); web = null; }
    }
    LinearLayout column(String title) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER); box.setPadding(32, 32, 32, 32);
        TextView text = new TextView(this); text.setText(title); text.setTextColor(Color.WHITE); text.setTextSize(24); text.setGravity(Gravity.CENTER);
        box.addView(text); return box;
    }
    Button button(LinearLayout box, String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setOnClickListener(v -> action.run()); box.addView(b); return b;
    }
    void corner() {
        TextView corner = new TextView(this); corner.setText("⚙"); corner.setTextColor(0xff777777); corner.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(64, 64, Gravity.TOP | Gravity.END);
        root.addView(corner, lp); corner.setOnLongClickListener(v -> { authenticate(this::openSettings); return true; });
    }
    static String normalizeAddress(String text) {
        try {
            URI uri = new URI(text.trim());
            if (uri.getUserInfo() != null || uri.getHost() == null || uri.getFragment() != null || uri.getQuery() != null) return null;
            if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && "192.168.1.26".equals(uri.getHost()))) return null;
            String path = uri.getPath(); if (path != null && !path.isEmpty() && !"/".equals(path)) return null;
            return uri.toString().replaceAll("/+$", "") + "/";
        } catch (Exception e) { return null; }
    }
    boolean sameOrigin(String address) {
        try { URI a = new URI(settings.server()), b = new URI(address);
            return a.getScheme().equals(b.getScheme()) && a.getHost().equals(b.getHost()) && a.getPort() == b.getPort();
        } catch (Exception e) { return false; }
    }
    void showPage() {
        personal = true; frame();
        web = new WebView(this); root.addView(web);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE); s.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) { callback.invoke(origin, false, false); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) { return !sameOrigin(req.getUrl().toString()); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                if (sameOrigin(req.getUrl().toString())) return null;
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view, String url) { if (web == view && loadTimeout != null) ui.removeCallbacks(loadTimeout); }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) { if (req.isForMainFrame()) ui.post(() -> { if (web == view) showOffline(); }); }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse response) { if (req.isForMainFrame()) ui.post(() -> { if (web == view) showOffline(); }); }
        });
        corner();
        WebView loading = web;
        loadTimeout = () -> { if (web == loading) showOffline(); }; ui.postDelayed(loadTimeout, 18000);
        web.loadUrl(settings.server());
    }
    void showOffline() {
        frame(); LinearLayout box = column("Asuka n'est pas joignable — vérifie que tu es sur le Wi-Fi de la maison");
        button(box, "Réessayer", this::showPage); root.addView(box); corner();
    }
    EditText input(LinearLayout box, String label, String value, boolean secret) {
        TextView caption = new TextView(this); caption.setText(label); caption.setTextColor(Color.LTGRAY); box.addView(caption);
        EditText edit = new EditText(this); edit.setTextColor(Color.WHITE); edit.setText(value); edit.setSingleLine(true);
        edit.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        box.addView(edit); return edit;
    }
    void choosePin(Runnable done) {
        LinearLayout box = column("Choisis ton code personnel (6 chiffres minimum)");
        EditText first = input(box, "Code", "", true), second = input(box, "Encore une fois", "", true);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).setPositiveButton("Enregistrer", null).setCancelable(false).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            String pin = first.getText().toString();
            if (!pin.matches("[0-9]{6,12}") || !pin.equals(second.getText().toString())) { toast("Entre deux fois le même code, de 6 à 12 chiffres."); return; }
            try { settings.setPin(pin); dialog.dismiss(); done.run(); } catch (Exception e) { toast("Impossible de protéger le code."); }
        })); dialog.show();
    }
    void authenticate(Runnable done) {
        if (!settings.hasPin()) { choosePin(done); return; }
        if (SystemClock.elapsedRealtime() < pinBlockedUntil) { toast("Patiente une minute avant de réessayer."); return; }
        LinearLayout box = column("Code de Teddy"); EditText code = input(box, "Code", "", true);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).setPositiveButton("Ouvrir", null).setNegativeButton("Annuler", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (SystemClock.elapsedRealtime() < pinBlockedUntil) return;
            if (settings.checkPin(code.getText().toString())) { pinAttempts = 0; dialog.dismiss(); done.run(); }
            else { pinAttempts++; code.setText(""); toast("Code incorrect."); if (pinAttempts >= 5) { pinAttempts = 0; pinBlockedUntil = SystemClock.elapsedRealtime() + 60000; dialog.dismiss(); } }
        })); dialog.show();
    }
    void openSettings() {
        LinearLayout box = column("Réglages d'Asuka");
        EditText address = input(box, "Adresse du serveur", settings.server(), false);
        EditText room = input(box, "Pièce", settings.room(), false);
        button(box, "Changer le code", () -> choosePin(() -> toast("Code modifié")));
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        new AlertDialog.Builder(this).setView(scroll).setPositiveButton("Enregistrer", (d,w) -> {
            String url = normalizeAddress(address.getText().toString());
            if (url == null) { toast("Adresse invalide : HTTP réservé à 192.168.1.26, sinon HTTPS."); return; }
            settings.p.edit().putString("server", url).putString("room", room.getText().toString().trim()).apply(); showPage();
        }).setNegativeButton("Fermer", null).show();
    }
    void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); }
}
