package fr.maison.asuka;

import android.app.*;
import android.Manifest;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import java.nio.*;
import java.time.LocalTime;
import java.util.Arrays;
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
    enum Mode { LOCKED, STANDBY, PERSONAL, UNKNOWN, SLEEP, GUEST, ENROLLING, FAULT }
    Mode mode = Mode.LOCKED;
    final java.util.List<Dialog> dialogs=new java.util.ArrayList<>();
    boolean pendingEnrollment;
    CameraMonitor camera;
    PresenceReporter reporter;
    long lastSeen;
    int matches;
    boolean cameraActive, settingsOpen, pinDialogOpen, manualNight;
    Runnable enrollmentTimeout;
    ValueAnimator fade;
    private final Runnable clock = new Runnable() {
        @Override public void run() { if (foreground) { reconcile(); ui.postDelayed(this, 1000); } }
    };
    private long pinBlockedUntil;
    private int pinAttempts;
    private Runnable loadTimeout;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new Settings(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        reporter = new PresenceReporter(settings);
        locked("Bienvenue chez Asuka", Mode.LOCKED);
        if (!settings.hasPin()) ui.post(() -> choosePin(this::openSettings));
    }
    @Override protected void onResume() {
        super.onResume(); foreground = true; lastSeen=SystemClock.elapsedRealtime();
        if (web != null) web.onResume();
        ui.removeCallbacks(clock); ui.post(clock);
        if(pendingEnrollment) { pendingEnrollment=false; ui.post(this::beginEnrollment); }
    }
    @Override protected void onPause() {
        foreground=false; manualNight=false;
        for(Dialog dialog:new java.util.ArrayList<>(dialogs)) dialog.dismiss(); dialogs.clear(); settingsOpen=false; pinDialogOpen=false; settingsOpen=false; stopCamera(); cancelEnrollment(); ui.removeCallbacks(clock);
        if (web != null) web.onPause();
        locked("Bonjour !", Mode.LOCKED); super.onPause();
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config); stopCamera(); cancelEnrollment();
        locked("Bonjour !",Mode.LOCKED); reconcile();
    }
    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null); stopCamera(); clearWeb(); if (fade != null) fade.cancel();
        if (reporter != null) reporter.close(); super.onDestroy();
    }
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
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int)(56*getResources().getDisplayMetrics().density), (int)(56*getResources().getDisplayMetrics().density), Gravity.TOP | Gravity.END);
        root.addView(corner, lp); corner.setContentDescription("Réglages : appui long"); corner.setOnLongClickListener(v -> { authenticate(this::openSettings); return true; });
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
        if (mode != Mode.PERSONAL) { locked("Bonjour !", Mode.UNKNOWN); return; }
        personal = true; frame();
        web = new WebView(this); root.addView(web);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(false); s.setGeolocationEnabled(false);
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
        corner(); indicator();
        WebView loading = web;
        loadTimeout = () -> { if (web == loading) showOffline(); }; ui.postDelayed(loadTimeout, 18000);
        web.loadUrl(settings.server());
    }
    void showOffline() {
        frame(); LinearLayout box = column("Asuka n'est pas joignable — vérifie que tu es sur le Wi-Fi de la maison");
        button(box, "Réessayer", this::showPage); root.addView(box); corner(); indicator();
    }
    EditText input(LinearLayout box, String label, String value, boolean secret) {
        TextView caption = new TextView(this); caption.setText(label); caption.setTextColor(Color.LTGRAY); box.addView(caption);
        EditText edit = new EditText(this); edit.setTextColor(Color.WHITE); edit.setText(value); edit.setSingleLine(true);
        edit.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        box.addView(edit); return edit;
    }
    void choosePin(Runnable done) {
        pinDialogOpen=true; brightness(.5f,false);
        LinearLayout box = column("Choisis ton code personnel (6 chiffres minimum)");
        EditText first = input(box, "Code", "", true), second = input(box, "Encore une fois", "", true);
        first.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        second.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).setPositiveButton("Enregistrer", null).setCancelable(false).create();
        dialog.setOnDismissListener(d -> pinDialogOpen=false);
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            String pin = first.getText().toString();
            if (!pin.matches("[0-9]{6,12}") || !pin.equals(second.getText().toString())) { toast("Entre deux fois le même code, de 6 à 12 chiffres."); return; }
            try { settings.setPin(pin); dialog.dismiss(); done.run(); } catch (Exception e) { toast("Impossible de protéger le code."); }
        })); showDialog(dialog);
    }
    void authenticate(Runnable done) {
        if (!settings.hasPin()) { choosePin(done); return; }
        if (SystemClock.elapsedRealtime() < pinBlockedUntil) { toast("Patiente une minute avant de réessayer."); return; }
        pinDialogOpen=true; brightness(.5f,false);
        LinearLayout box = column("Code de Teddy"); EditText code = input(box, "Code", "", true);
        code.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).setPositiveButton("Ouvrir", null).setNegativeButton("Annuler", null).create();
        dialog.setOnDismissListener(d -> { pinDialogOpen=false; if(mode==Mode.STANDBY || mode==Mode.SLEEP) brightness(.001f,false); });
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (SystemClock.elapsedRealtime() < pinBlockedUntil) return;
            if (settings.checkPin(code.getText().toString())) { pinAttempts = 0; dialog.dismiss(); done.run(); }
            else { pinAttempts++; code.setText(""); toast("Code incorrect."); if (pinAttempts >= 5) { pinAttempts = 0; pinBlockedUntil = SystemClock.elapsedRealtime() + 60000; dialog.dismiss(); } }
        })); showDialog(dialog);
    }
    void openSettings() {
        settingsOpen=true; stopCamera(); cancelEnrollment(); locked("Réglages protégés", Mode.LOCKED);
        brightness(.6f, false);
        LinearLayout box = column("Réglages d'Asuka");
        EditText address = input(box, "Adresse du serveur (HTTP : 192.168.1.26 uniquement)", settings.server(), false);
        EditText room = input(box, "Pièce", settings.room(), false);
        EditText token = input(box, "Jeton de la maison (laisser vide pour conserver)", "", true);
        EditText delay = input(box, "Retour en veille : minutes (1 à 60)", String.valueOf(settings.p.getInt("idle",3)), false);
        EditText threshold = input(box, "Seuil visage : 0.30 à 0.90 (défaut 0.50)", String.valueOf(settings.p.getFloat("threshold",.50f)), false);
        EditText start = input(box, "Sommeil : début HH:MM", settings.p.getString("sleepStart","01:30"), false);
        EditText end = input(box, "Sommeil : fin HH:MM (même heure = désactivé)", settings.p.getString("sleepEnd","09:00"), false);
        Switch guest = new Switch(this); guest.setText("Mode invité — caméra coupée, contenu masqué"); guest.setChecked(settings.p.getBoolean("guest",false)); box.addView(guest);
        TextView privacy = new TextView(this); privacy.setText("Images en mémoire uniquement. Rien n'est photographié ni envoyé. Seule une signature chiffrée est conservée. La page n'a jamais accès à la caméra ou au micro."); privacy.setTextColor(Color.LTGRAY); box.addView(privacy);
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(scroll).setPositiveButton("Enregistrer", null).setNegativeButton("Fermer", null).create();
        button(box, "Enregistrer le visage de Teddy", () -> {
            if (settings.p.contains("face")) { toast("Efface d'abord le visage existant pour le remplacer."); return; }
            dialog.dismiss(); beginEnrollment();
        });
        button(box, "Effacer mon visage", () -> showDialog(new AlertDialog.Builder(this).setMessage("Effacer la signature du visage de cette tablette ?")
            .setNegativeButton("Annuler",null).setPositiveButton("Effacer",(d,w) -> {
                settings.p.edit().remove("face").commit(); stopCamera(); toast("Signature effacée.");
            }).create()));
        button(box, "Effacer le jeton", () -> { settings.p.edit().remove("token").apply(); toast("Jeton effacé."); });
        button(box, "Changer le code", () -> choosePin(() -> toast("Code modifié")));
        button(box, "C'est moi — ouvrir Asuka", () -> { dialog.dismiss(); if (settings.p.getBoolean("guest",false)) toast("Désactive d'abord le mode invité."); else unlock(); });
        dialog.setOnDismissListener(d -> { settingsOpen=false; if (foreground) reconcile(); });
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            try {
                String url=normalizeAddress(address.getText().toString());
                if (url==null) throw new IllegalArgumentException("HTTP réservé à 192.168.1.26 ; sinon HTTPS, sans chemin.");
                int minutes=Integer.parseInt(delay.getText().toString()); float limit=Float.parseFloat(threshold.getText().toString().replace(',','.'));
                if (minutes<1 || minutes>60 || !Float.isFinite(limit) || limit<.30f || limit>.90f) throw new IllegalArgumentException("Vérifie la durée et le seuil.");
                String a=start.getText().toString(), b=end.getText().toString(); PresencePolicy.time(a); PresencePolicy.time(b);
                String t=token.getText().toString().trim(); if (t.contains("\r") || t.contains("\n")) throw new IllegalArgumentException("Jeton invalide.");
                if (!t.isEmpty()) settings.putSecret("token",t);
                String name=room.getText().toString().trim(); if (name.isEmpty()) throw new IllegalArgumentException("Donne un nom à la pièce.");
                settings.p.edit().putString("server",url).putString("room",name).putInt("idle",minutes).putFloat("threshold",limit)
                    .putString("sleepStart",a).putString("sleepEnd",b).putBoolean("guest",guest.isChecked()).apply();
                dialog.dismiss();
            } catch (Exception e) { toast(e instanceof IllegalArgumentException ? e.getMessage() : "Impossible d'enregistrer les réglages protégés."); }
        }));
        showDialog(dialog);
    }
    boolean night() {
        LocalTime now=LocalTime.now();
        return PresencePolicy.sleeping(now.getHour()*60+now.getMinute(),PresencePolicy.time(settings.p.getString("sleepStart","01:30")),PresencePolicy.time(settings.p.getString("sleepEnd","09:00")));
    }
    void reconcile() {
        if (!foreground) return;
        if (settingsOpen || pinDialogOpen) { if(night() && camera!=null) stopCamera(); return; }
        if (mode==Mode.ENROLLING) {
            if(night() || settings.p.getBoolean("guest",false)) {
                stopCamera(); cancelEnrollment(); locked("",night()?Mode.SLEEP:Mode.GUEST);
            }
            return;
        }
        if (settings.p.getBoolean("guest",false)) {
            stopCamera(); if(mode!=Mode.GUEST) locked("Bonjour !\nMode invité — caméra coupée",Mode.GUEST); return;
        }
        if (night()) {
            stopCamera();
            if(mode!=Mode.PERSONAL || !manualNight || SystemClock.elapsedRealtime()-lastSeen>settings.p.getInt("idle",3)*60000L) {
                if(mode!=Mode.SLEEP) locked("",Mode.SLEEP);
            }
            return;
        }
        if (mode==Mode.PERSONAL && SystemClock.elapsedRealtime()-lastSeen>settings.p.getInt("idle",3)*60000L) idle();
        if (mode==Mode.SLEEP || mode==Mode.GUEST) idle();
        if (!settings.p.contains("face") || checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) return;
        if (mode==Mode.LOCKED) idle();
        if (camera==null && mode!=Mode.FAULT) startCamera(false);
    }
    void unlock() {
        if (settings.p.getBoolean("guest",false)) return;
        cancelEnrollment(); manualNight=night(); lastSeen=SystemClock.elapsedRealtime(); matches=0;
        mode=Mode.PERSONAL; personal=true;
        if(camera!=null) camera.trusted(true);
        reporter.changed("teddy",true); brightness(.8f,true); showPage();
    }
    void idle() { manualNight=false; matches=0; if(camera!=null) camera.trusted(false); locked("",Mode.STANDBY); }
    void locked(String text, Mode value) {
        mode=value; personal=false; frame();
        boolean dark=value==Mode.STANDBY || value==Mode.SLEEP;
        if (!dark) {
            LinearLayout box=column(text);
            button(box,"C'est moi",() -> authenticate(() -> { if(mode==Mode.GUEST) openSettings(); else unlock(); }));
            root.addView(box);
        } else {
            root.setOnLongClickListener(v -> { authenticate(this::openSettings); return true; });
        }
        corner(); indicator(); brightness(dark ? .001f : .45f,false);
        if(reporter!=null) reporter.changed(value.name().toLowerCase(java.util.Locale.ROOT),false);
    }
    void indicator() {
        if(root==null) return;
        if(cameraLight!=null && cameraLight.getParent()==root) root.removeView(cameraLight);
        cameraLight=new TextView(this); cameraLight.setText(cameraActive ? "● Caméra locale" : "");
        cameraLight.setTextColor(0xff4dbe78); cameraLight.setTextSize(10); cameraLight.setPadding(8,4,8,4);
        root.addView(cameraLight,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START));
    }
    void brightness(float target,boolean gradual) {
        if(fade!=null) fade.cancel();
        if(!gradual) { WindowManager.LayoutParams lp=getWindow().getAttributes(); lp.screenBrightness=target; getWindow().setAttributes(lp); return; }
        float current=getWindow().getAttributes().screenBrightness;
        fade=ValueAnimator.ofFloat(Math.max(.001f,current),target); fade.setDuration(1400);
        fade.addUpdateListener(a -> { WindowManager.LayoutParams lp=getWindow().getAttributes(); lp.screenBrightness=(float)a.getAnimatedValue(); getWindow().setAttributes(lp); }); fade.start();
    }
    float[] readFace() throws Exception {
        String text=settings.secret("face"); if(text.isEmpty()) return null;
        byte[] bytes=Settings.dec(text); if(bytes.length!=128*4) throw new IllegalStateException("Signature invalide");
        FloatBuffer buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer(); float[] result=new float[buffer.remaining()]; buffer.get(result); Arrays.fill(bytes,(byte)0); return result;
    }
    void startCamera(boolean enroll) {
        if(camera!=null || !foreground) return;
        try {
            float[] reference=readFace();
            camera=new CameraMonitor(this,new CameraMonitor.Listener() {
                public void running(boolean active) { cameraActive=active; indicator(); }
                public void failed(String message) { stopCamera(); cancelEnrollment(); locked(message,Mode.FAULT); }
                public void progress(int count) { if(mode==Mode.ENROLLING) toast("Reste face à la tablette : " + count + "/5"); }
                public void enrolled(float[] feature) {
                    try {
                        if(feature.length!=128) throw new IllegalStateException();
                        ByteBuffer buffer=ByteBuffer.allocate(feature.length*4).order(ByteOrder.LITTLE_ENDIAN); for(float f:feature) buffer.putFloat(f);
                        settings.putSecret("face",Settings.enc(buffer.array())); Arrays.fill(buffer.array(),(byte)0); Arrays.fill(feature,0);
                        cancelEnrollment(); toast("Visage enregistré. Aucune photo conservée."); unlock();
                    } catch(Exception e) { Arrays.fill(feature,0); stopCamera(); cancelEnrollment(); locked("Impossible de protéger le visage. Réessaie dans les réglages.",Mode.FAULT); }
                }
                public void unknown() {
                    matches=0; lastSeen=SystemClock.elapsedRealtime();
                    if(mode!=Mode.ENROLLING) { if(camera!=null) camera.trusted(false); locked("Bonjour !",Mode.UNKNOWN); }
                }
                public void observed(boolean face,boolean teddy) {
                    if(mode==Mode.ENROLLING) return;
                    long now=SystemClock.elapsedRealtime();
                    if(mode==Mode.PERSONAL) { if(face) lastSeen=now; return; }
                    if(teddy) { if(++matches>=2) unlock(); }
                    else { matches=0; if(face && mode!=Mode.UNKNOWN) locked("Bonjour !",Mode.UNKNOWN); }
                    if(face) lastSeen=now;
                    else if(mode==Mode.UNKNOWN && now-lastSeen>settings.p.getInt("idle",3)*60000L) idle();
                }
            },reference,settings.p.getFloat("threshold",.50f),enroll);
            camera.trusted(mode==Mode.PERSONAL); camera.start();
        } catch(Exception e) { stopCamera(); locked("Signature illisible. Efface-la puis enregistre ton visage à nouveau.",Mode.FAULT); }
    }
    void stopCamera() { if(camera!=null) { camera.close(); camera=null; } cameraActive=false; indicator(); }
    void cancelEnrollment() { if(enrollmentTimeout!=null) ui.removeCallbacks(enrollmentTimeout); enrollmentTimeout=null; }
    void beginEnrollment() {
        if(settings.p.getBoolean("guest",false) || night()) { toast("Désactive le mode invité et les heures de sommeil avant l'enregistrement."); return; }
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            showDialog(new AlertDialog.Builder(this).setMessage("La caméra sert seulement à reconnaître ton visage sur cette tablette. Aucune image n'est enregistrée ni envoyée. Autoriser la caméra ?")
                .setNegativeButton("Annuler",null).setPositiveButton("Continuer",(d,w) -> requestPermissions(new String[]{Manifest.permission.CAMERA},42)).create()); return;
        }
        stopCamera(); locked("Enregistrement du visage\nReste seul, bien éclairé, face à la tablette.\n5 signatures sont réunies ; aucune photo n'est gardée.",Mode.ENROLLING);
        LinearLayout actions=column(""); button(actions,"Annuler",() -> { stopCamera(); cancelEnrollment(); locked("Bonjour !",Mode.LOCKED); });
        root.addView(actions,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM)); startCamera(true);
        enrollmentTimeout=() -> { stopCamera(); cancelEnrollment(); locked("Enregistrement non terminé. Réessaie avec une meilleure lumière.",Mode.FAULT); }; ui.postDelayed(enrollmentTimeout,60000);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==42 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) { if(foreground) beginEnrollment(); else pendingEnrollment=true; }
        else if(request==42) { toast("Caméra refusée. Le code reste utilisable."); locked("Bonjour !",Mode.LOCKED); }
    }
    void showDialog(AlertDialog dialog) { dialogs.removeIf(d -> !d.isShowing()); dialogs.add(dialog); dialog.show(); }
    void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); }
}
