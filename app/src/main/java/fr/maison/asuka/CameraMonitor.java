package fr.maison.asuka;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Size;
import android.view.Surface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Images stay in RAM, never pass through the UI, disk or network. */
final class CameraMonitor {
    interface Listener {
        void observed(boolean face, boolean teddy);
        void unknown();
        void enrolled(float[] feature);
        void failed(String message);
        void running(boolean active);
        void progress(int count);
    }
    private final Activity activity;
    private final Listener listener;
    private final HandlerThread thread = new HandlerThread("AsukaCamera");
    private final Handler handler;
    private volatile boolean closed, trusted, enrolling;
    private volatile float threshold = .50f;
    private volatile float[] reference;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private FaceDetectorYN detector;
    private FaceRecognizerSF recognizer;
    private Mat previous;
    private long lastFrame, lastSearch;
    private int rotation;
    private final List<float[]> enrollment = new ArrayList<>();
    CameraMonitor(Activity a, Listener l, float[] signature, float threshold, boolean enroll) {
        activity=a; listener=l; reference=signature; this.threshold=threshold; enrolling=enroll;
        thread.start(); handler=new Handler(thread.getLooper());
    }
    void trusted(boolean value) { trusted=value; }
    void start() { handler.post(() -> {
        try {
            if (closed) return;
            if (!OpenCVLoader.initLocal()) throw new IOException("OpenCV indisponible");
            Core.setNumThreads(1);
            detector=FaceDetectorYN.create(model("yunet.onnx"), "", new org.opencv.core.Size(320,240), .85f, .3f, 1000);
            recognizer=FaceRecognizerSF.create(model("sface.onnx"), "");
            if (closed) return;
            CameraManager manager=(CameraManager)activity.getSystemService(Activity.CAMERA_SERVICE);
            String front=null;
            for (String id:manager.getCameraIdList()) {
                CameraCharacteristics c=manager.getCameraCharacteristics(id);
                if (Objects.equals(c.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_FRONT)) { front=id; break; }
            }
            if (front==null) throw new IOException("Pas de caméra avant");
            CameraCharacteristics c=manager.getCameraCharacteristics(front);
            int display=activity.getWindowManager().getDefaultDisplay().getRotation();
            int degrees=display==Surface.ROTATION_90?90:display==Surface.ROTATION_180?180:display==Surface.ROTATION_270?270:0;
            Integer sensor=c.get(CameraCharacteristics.SENSOR_ORIENTATION); rotation=((sensor==null?0:sensor)+degrees)%360;
            Size[] sizes=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.YUV_420_888);
            Size chosen=Arrays.stream(sizes).filter(s -> s.getWidth()>=320 && s.getWidth()<=640 && s.getHeight()<=640)
                .min(Comparator.comparingInt(s -> s.getWidth()*s.getHeight())).orElse(sizes[0]);
            reader=ImageReader.newInstance(chosen.getWidth(),chosen.getHeight(),ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(r -> {
                try (Image image=r.acquireLatestImage()) {
                    if (image==null || closed) return;
                    long now=SystemClock.elapsedRealtime();
                    long cadence=trusted&&!enrolling ? 5000 : 500; // 2 fps; presence-only check every 5 s when already identified.
                    if (now-lastFrame<cadence) return; lastFrame=now;
                    process(image, now);
                } catch (Exception e) { error("La caméra ne peut plus analyser. Déverrouille avec ton code."); }
            },handler);
            if (activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) throw new SecurityException();
            manager.openCamera(front,new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    if (closed) { device.close(); return; } camera=device;
                    try {
                        device.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession s) {
                                if (closed) { s.close(); return; } session=s;
                                try {
                                    CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                    request.addTarget(reader.getSurface());
                                    android.util.Range<Integer>[] ranges=c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                                    if (ranges!=null && ranges.length>0) {
                                        android.util.Range<Integer> lowest=ranges[0];
                                        for(android.util.Range<Integer> range:ranges) { if(range.getUpper().intValue()<lowest.getUpper().intValue()) lowest=range; }
                                        request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,lowest);
                                    }
                                    session.setRepeatingRequest(request.build(),null,handler);
                                    emit(() -> listener.running(true));
                                } catch (Exception e) { error("Impossible de démarrer la caméra."); }
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession s) { error("Caméra incompatible."); }
                        },handler);
                    } catch (Exception e) { error("Impossible d'ouvrir la caméra."); }
                }
                @Override public void onDisconnected(CameraDevice d) { d.close(); error("Caméra déconnectée."); }
                @Override public void onError(CameraDevice d,int e) { d.close(); error("Caméra indisponible."); }
            },handler);
        } catch (Exception e) { error("Caméra ou modèles indisponibles. Utilise ton code."); }
    }); }
    private void emit(Runnable r) { activity.runOnUiThread(() -> { if (!closed) r.run(); }); }
    private void error(String text) { emit(() -> listener.failed(text)); }
    private String model(String name) throws IOException {
        // Only public model weights are extracted, never camera frames or embeddings.
        File directory=new File(activity.getNoBackupFilesDir(),"models"); directory.mkdirs();
        File output=new File(directory,name);
        if (!output.isFile()) {
            File temp=new File(directory,name+".tmp");
            try (InputStream in=activity.getAssets().open("models/"+name); OutputStream out=new FileOutputStream(temp)) { byte[] b=new byte[16384]; int n; while ((n=in.read(b))!=-1) out.write(b,0,n); }
            if (!temp.renameTo(output)) throw new IOException("Model extraction");
        }
        return output.toString();
    }
    private Mat bgr(Image image) {
        int width=image.getWidth(), height=image.getHeight();
        // Downsample before conversion even if a device exposes only larger YUV sizes.
        int step=Math.max(1,(Math.max(width,height)+639)/640), w=width/step, h=height/step;
        byte[] pixels=new byte[w*h*3]; Image.Plane[] planes=image.getPlanes();
        ByteBuffer y=planes[0].getBuffer(), u=planes[1].getBuffer(), v=planes[2].getBuffer();
        int y0=y.position(),u0=u.position(),v0=v.position();
        for(int row=0;row<h;row++) for(int col=0;col<w;col++) {
            int xx=col*step, yy=row*step;
            int lum=(y.get(y0+yy*planes[0].getRowStride()+xx*planes[0].getPixelStride())&255)-16;
            int cb=(u.get(u0+(yy/2)*planes[1].getRowStride()+(xx/2)*planes[1].getPixelStride())&255)-128;
            int cr=(v.get(v0+(yy/2)*planes[2].getRowStride()+(xx/2)*planes[2].getPixelStride())&255)-128;
            int i=(row*w+col)*3;
            pixels[i]=(byte)clamp((298*lum+516*cb+128)>>8);
            pixels[i+1]=(byte)clamp((298*lum-100*cb-208*cr+128)>>8);
            pixels[i+2]=(byte)clamp((298*lum+409*cr+128)>>8);
        }
        Mat raw=new Mat(h,w,CvType.CV_8UC3); raw.put(0,0,pixels); Arrays.fill(pixels,(byte)0);
        if(rotation==0) return raw;
        Mat rotated=new Mat(); Core.rotate(raw,rotated,rotation==90?Core.ROTATE_90_CLOCKWISE:rotation==180?Core.ROTATE_180:Core.ROTATE_90_COUNTERCLOCKWISE); raw.release(); return rotated;
    }
    private static int clamp(int v) { return Math.max(0,Math.min(255,v)); }
    private void process(Image image,long now) {
        Mat color=bgr(image),gray=new Mat(),tiny=new Mat(),diff=new Mat(),faces=new Mat();
        try {
            Imgproc.cvtColor(color,gray,Imgproc.COLOR_BGR2GRAY); Imgproc.resize(gray,tiny,new org.opencv.core.Size(64,48));
            boolean motion=true;
            if(previous!=null) { Core.absdiff(tiny,previous,diff); motion=Core.mean(diff).val[0]>5.0; previous.release(); }
            previous=tiny.clone();
            // Movement triggers immediately; a slow fallback finds a person already still at startup.
            if(!enrolling && !trusted && !motion && now-lastSearch<5000) return;
            lastSearch=now;
            detector.setInputSize(color.size()); detector.detect(color,faces);
            boolean one=faces.rows()==1;
            if (!one) {
                for(float[] f:enrollment) Arrays.fill(f,0); enrollment.clear();
                if(faces.rows()>1) emit(() -> listener.unknown());
                else emit(() -> listener.observed(false,false));
                return;
            }
            if(trusted&&!enrolling) { emit(() -> listener.observed(true,false)); return; }
            Mat aligned=new Mat(),feature=new Mat(),face=faces.row(0);
            try {
                recognizer.alignCrop(color,face,aligned); recognizer.feature(aligned,feature);
                float[] vector=new float[(int)feature.total()]; feature.get(0,0,vector);
                if(enrolling) {
                    if(!enrollment.isEmpty() && PresencePolicy.cosine(enrollment.get(0),vector)<.50) enrollment.clear();
                    enrollment.add(vector); int count=enrollment.size(); emit(() -> listener.progress(count));
                    if(count>=5) {
                        float[] average=new float[vector.length];
                        for(float[] f:enrollment) { for(int i=0;i<f.length;i++) average[i]+=f[i]/count; Arrays.fill(f,0); }
                        enrollment.clear(); enrolling=false; reference=average.clone(); emit(() -> listener.enrolled(average));
                    }
                } else {
                    boolean match=reference!=null && PresencePolicy.cosine(reference,vector)>=threshold;
                    Arrays.fill(vector,0); emit(() -> listener.observed(true,match));
                }
            } finally { face.release(); aligned.release(); feature.release(); }
        } finally { color.release(); gray.release(); tiny.release(); diff.release(); faces.release(); }
    }
    void close() {
        closed=true;
        handler.post(() -> {
            if(session!=null) { session.close(); session=null; }
            if(camera!=null) { camera.close(); camera=null; }
            if(reader!=null) { reader.close(); reader=null; }
            if(previous!=null) { previous.release(); previous=null; }
            for(float[] f:enrollment) Arrays.fill(f,0); enrollment.clear();
            if(reference!=null) Arrays.fill(reference,0);
            detector=null; recognizer=null; thread.quitSafely();
        });
    }
}
