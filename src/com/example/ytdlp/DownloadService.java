package com.example.ytdlp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

public class DownloadService extends Service {

    public static final String ACTION_PROGRESS = "com.example.ytdlp.PROGRESS";
    public static final String ACTION_KILL = "com.example.ytdlp.KILL";
    private String pythonHome, ytdlpPath, pythonBin;
    private Process currentProcess;

    @Override
    public void onCreate() {
        super.onCreate();
        pythonHome = getFilesDir().getAbsolutePath() + "/python";
        pythonBin = getApplicationInfo().nativeLibraryDir + "/libpython_exe.so";
        ytdlpPath = getFilesDir().getAbsolutePath() + "/yt-dlp";
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        
        if (ACTION_KILL.equals(intent.getAction())) {
            killProcess();
            return START_NOT_STICKY;
        }

        String url = intent.getStringExtra("url");
        String format = intent.getStringExtra("format");
        String mergeFormat = intent.getStringExtra("merge_format");
        String resolution = intent.getStringExtra("resolution");
        String customCmd = intent.getStringExtra("custom_cmd");
        boolean audioOnly = intent.getBooleanExtra("audio_only", false);
        String bitrate = intent.getStringExtra("bitrate");
        
        if (customCmd != null) {
            new Thread(() -> execute(new String[]{"sh", "-c", customCmd}, false)).start();
        } else if (url != null) {
            startForeground(1, createNotification("Starting download..."));
            new Thread(() -> {
                File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                String outFileName = "%(title)s" + (resolution != null ? "-" + resolution : "") + ".%(ext)s";
                String outPath = downloadDir.getAbsolutePath() + "/" + outFileName;
                String ytdlpCmd = getFilesDir().getAbsolutePath() + "/bin/yt-dlp";
                
                java.util.List<String> command = new java.util.ArrayList<>();
                command.add(ytdlpCmd);
                command.add("--no-check-certificate");
                command.add("--newline");
                command.add("--restrict-filenames");
                command.add("--write-thumbnail");
                command.add("--convert-thumbnails");
                command.add("jpg");
                command.add("--no-part");
                command.add("--no-playlist");
                command.add("-o");
                command.add(outPath);
                
                broadcastRaw("[DEBUG] Command: " + String.join(" ", command));
                broadcastRaw("[DEBUG] Target: " + outPath);
                
                if (mergeFormat != null) {
                    command.add("--merge-output-format");
                    command.add(mergeFormat);
                }

                if (audioOnly) {
                    command.add("-x");
                    command.add("--audio-format");
                    command.add("mp3");
                    command.add("--audio-quality");
                    command.add(bitrate != null ? bitrate : "128");
                } else if (format != null) {
                    command.add("-f");
                    command.add(format);
                }
                
                command.add(url);
                
                execute(command.toArray(new String[0]), true);
                stopForeground(true);
                stopSelf();
            }).start();
        }
        return START_NOT_STICKY;
    }

    private void killProcess() {
        if (currentProcess != null) {
            try {
                currentProcess.destroy();
                // On modern Android we might need to kill the process group
                // but destroy() should work for the immediate child.
                broadcastRaw("[SYSTEM] Process killed by user.");
            } catch (Exception e) {
                broadcastRaw("[ERROR] Failed to kill process: " + e.getMessage());
            }
            currentProcess = null;
        }
    }

    private void execute(String[] cmd, boolean isDownload) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(getFilesDir());
            setupEnv(pb.environment());
            pb.redirectErrorStream(true);
            currentProcess = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) broadcastRaw(line);
            
            currentProcess.waitFor();
            currentProcess = null;
            if (isDownload) broadcastStatus("finished");
        } catch (Exception e) {
            broadcastRaw("[ERROR] " + e.getMessage());
            currentProcess = null;
        }
    }

    private void setupEnv(Map<String, String> env) {
        String libDir = getApplicationInfo().nativeLibraryDir;
        String internalLibDir = getFilesDir().getAbsolutePath() + "/lib";
        String internalBinDir = getFilesDir().getAbsolutePath() + "/bin";
        env.put("PYTHONHOME", pythonHome);
        env.put("PYTHONPATH", pythonHome + "/lib/python3.12:" + pythonHome + "/lib/python3.12/site-packages");
        env.put("LD_LIBRARY_PATH", internalLibDir + ":" + libDir + ":/system/lib64:/system/lib");
        env.put("PATH", internalBinDir + ":" + (env.get("PATH") != null ? env.get("PATH") : ""));
    }

    private void broadcastRaw(String line) { Intent i = new Intent(ACTION_PROGRESS); i.putExtra("status", "running"); i.putExtra("raw", line); sendBroadcast(i); }
    private void broadcastStatus(String s) { Intent i = new Intent(ACTION_PROGRESS); i.putExtra("status", s); sendBroadcast(i); }
    private void createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { NotificationChannel channel = new NotificationChannel("dl", "Downloads", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(channel); } }
    private Notification createNotification(String text) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return new Notification.Builder(this, "dl").setContentTitle("YT-DLP").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_download).build(); return new Notification(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}