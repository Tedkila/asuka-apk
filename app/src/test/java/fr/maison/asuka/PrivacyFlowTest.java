package fr.maison.asuka;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class PrivacyFlowTest {
    ActivityController<MainActivity> controller;
    MainActivity activity;
    @Before public void prepare() {
        Application app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("asuka",Context.MODE_PRIVATE).edit().clear().putString("pin","configured-for-ui-test")
            .putString("sleepStart","00:00").putString("sleepEnd","00:00").commit();
        controller=Robolectric.buildActivity(MainActivity.class).setup(); activity=controller.get();
    }
    @After public void finish() { controller.pause().stop().destroy(); }
    @Test public void coldLaunchNeverExposesHouseAndRequestsNoMicrophone() {
        assertNull(activity.web); assertFalse(activity.personal);
        assertEquals(MainActivity.Mode.LOCKED,activity.mode);
        assertEquals(PackageManager.PERMISSION_DENIED,activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        assertNull(shadowOf(activity).getLastRequestedPermission());
    }
    @Test public void identityLossDestroysPersonalWebView() {
        activity.unlock(); assertNotNull(activity.web); assertTrue(activity.personal);
        activity.locked("Bonjour !",MainActivity.Mode.UNKNOWN);
        assertNull(activity.web); assertFalse(activity.personal);
    }
    @Test public void guestModeCutsCameraAndHidesContent() {
        activity.unlock(); activity.settings.p.edit().putBoolean("guest",true).commit(); activity.reconcile();
        assertNull(activity.web); assertNull(activity.camera); assertEquals(MainActivity.Mode.GUEST,activity.mode);
        activity.unlock(); assertNull(activity.web);
    }
    @Test public void leavingTheAppRemovesContentAndProtectedDialogs() {
        activity.unlock(); activity.openSettings(); assertTrue(activity.settingsOpen);
        controller.pause();
        assertNull(activity.web); assertFalse(activity.settingsOpen); assertTrue(activity.dialogs.isEmpty());
        controller.resume(); assertNull(activity.web);
    }
    @Test public void unresponsiveServerBecomesRetryScreen() {
        activity.unlock();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(19));
        assertNull(activity.web);
        assertTrue(activity.personal);
        assertEquals(MainActivity.Mode.PERSONAL,activity.mode);
    }
    @Test public void inactivityRevokesThePersonalSession() {
        activity.unlock();
        activity.lastSeen=android.os.SystemClock.elapsedRealtime()-181000;
        activity.reconcile();
        assertNull(activity.web); assertFalse(activity.personal);
        assertEquals(MainActivity.Mode.STANDBY,activity.mode);
    }
    @Test public void authenticationRemainsReadableDuringSleep() {
        java.time.LocalTime now=java.time.LocalTime.now();
        java.time.format.DateTimeFormatter format=java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        activity.settings.p.edit().putString("sleepStart",now.minusMinutes(1).format(format))
            .putString("sleepEnd",now.plusMinutes(1).format(format)).commit();
        activity.authenticate(() -> {}); activity.reconcile();
        assertTrue(activity.pinDialogOpen);
        assertTrue(activity.getWindow().getAttributes().screenBrightness>.4f);
        assertNull(activity.camera);
    }
    @Test public void httpIsReservedToTheOneHouseAddress() {
        assertEquals("http://192.168.1.26:8765/",MainActivity.normalizeAddress("http://192.168.1.26:8765"));
        assertNull(MainActivity.normalizeAddress("http://192.168.1.27:8765/"));
        assertNull(MainActivity.normalizeAddress("http://192.168.1.26.evil.test/"));
        assertNull(MainActivity.normalizeAddress("https://name:password@home.test/"));
        assertNull(MainActivity.normalizeAddress("javascript:alert(1)"));
        assertNotNull(MainActivity.normalizeAddress("https://home.test/"));
    }
}
