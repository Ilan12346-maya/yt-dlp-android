package com.example.ytdlp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;
import android.provider.MediaStore;
import android.content.ContentValues;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class MainActivity extends Activity {

    private FrameLayout contentFrame;
    private SharedPreferences prefs;
    private DownloadReceiver receiver;
    private StringBuilder backendLogs = new StringBuilder("[SYSTEM] Session Started (v0.1.0).\n");
    private String currentTab = "home";
    private String historyType = "all";
    private String pythonHome, pythonBin, ytdlpPath;
    private View homeView, browserView, historyView, logsView;
    private String lastCapturedUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());

        // UI Setup (Keep modern layout)
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        }

        setContentView(R.layout.activity_main);
        contentFrame = findViewById(R.id.content_frame);
        prefs = getSharedPreferences("ytdlp_prefs", MODE_PRIVATE);

        // Dynamic Insets Handling (Fix statusbar/navbar/keyboard overlap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            findViewById(R.id.main_root).setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                    View nav = findViewById(R.id.nav_bar);
                    
                    int top = 0;
                    int bottom = 0;
                    if (Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets systemInsets = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
                        top = systemInsets.top;
                        bottom = systemInsets.bottom;
                    } else {
                        top = insets.getSystemWindowInsetTop();
                        bottom = insets.getSystemWindowInsetBottom();
                    }
                    
                    // Add top inset to the nav bar margin to keep it floating below status bar
                    android.widget.RelativeLayout.LayoutParams lp = (android.widget.RelativeLayout.LayoutParams) nav.getLayoutParams();
                    int totalTopOffset = top + (int)(16 * getResources().getDisplayMetrics().density);
                    lp.topMargin = totalTopOffset;
                    nav.setLayoutParams(lp);
                    
                    // Push content_frame below the top bar
                    int barHeight = (int)(72 * getResources().getDisplayMetrics().density);
                    int contentTopPadding = totalTopOffset + barHeight + (int)(8 * getResources().getDisplayMetrics().density);
                    contentFrame.setPadding(0, contentTopPadding, 0, 0);

                    // Update browser panel if visible
                    if (browserView != null && browserView.getVisibility() == View.VISIBLE) {
                        View island = browserView.findViewById(R.id.dynamic_island);
                        if (island != null) {
                            android.widget.RelativeLayout.LayoutParams islandLp = (android.widget.RelativeLayout.LayoutParams) island.getLayoutParams();
                            // If keyboard is open (bottom > 0), stick to it with 10dp margin
                            // If keyboard is closed, use original base margin (160dp)
                            int baseMargin = (int)(160 * getResources().getDisplayMetrics().density);
                            int keyboardMargin = (int)(10 * getResources().getDisplayMetrics().density);
                            
                            islandLp.bottomMargin = (bottom > 0) ? (bottom + keyboardMargin) : baseMargin;
                            island.setLayoutParams(islandLp);
                            
                            // Adjust WebView padding so it's not covered by the island
                            WebView wv = browserView.findViewById(R.id.webView);
                            if (wv != null) {
                                int islandHeight = island.getHeight() > 0 ? island.getHeight() : (int)(56 * getResources().getDisplayMetrics().density);
                                wv.setPadding(0, 0, 0, islandLp.bottomMargin + islandHeight + (int)(16 * getResources().getDisplayMetrics().density));
                            }
                        }
                    }
                    
                    return insets;
                }
            });
        }

        // Paths from inc_19
        pythonHome = getFilesDir().getAbsolutePath() + "/python";
        pythonBin = getApplicationInfo().nativeLibraryDir + "/libpython_exe.so";
        ytdlpPath = getFilesDir().getAbsolutePath() + "/yt-dlp";

        setupNavigation();
        checkPermissions();
        if (prefs.getBoolean("first_start", true)) {
            showDisclaimer();
        } else {
            switchTab("home");
            // Self-Test Thread (Logic 1:1 from inc_19)
            new Thread(() -> {
                prepareEnvironment();
                setupLibrarySymlinks(); // Always run this to ensure links are fresh
                logToBackend("[SYSTEM] Environment initialized.");
                runTest("python3 --version");
                runTest("yt-dlp --version");
                runTest("ffmpeg -version | head -n 1");
                startLogcatReader();
            }).start();
        }

        receiver = new DownloadReceiver();
        registerReceiver(receiver, new IntentFilter(DownloadService.ACTION_PROGRESS));
        
        // Apply initial visibility for terminal tab
        boolean debug = prefs.getBoolean("debug_mode", true);
        findViewById(R.id.nav_terminal).setVisibility(debug ? View.VISIBLE : View.GONE);
        ((LinearLayout)findViewById(R.id.nav_bar)).setWeightSum(debug ? 4 : 3);
    }

    private void startLogcatReader() {
        new Thread(() -> {
            try {
                // Clear logcat first
                Runtime.getRuntime().exec("logcat -c").waitFor();
                // Filter for this app or errors
                Process p = Runtime.getRuntime().exec("logcat *:E"); 
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = r.readLine()) != null) {
                    if (currentTab.equals("logs")) {
                        logToBackend("[LOGCAT] " + line);
                    } else {
                        backendLogs.append("[LOGCAT] ").append(line).append("\n");
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    private void runTest(String cmd) {
        logToBackend("$ " + cmd);
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.directory(getFilesDir());
            setupEnvForProcess(pb.environment());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) logToBackend(line);
            p.waitFor();
        } catch (Exception e) {}
    }

    private void setupEnvForProcess(Map<String, String> env) {
        String libDir = getApplicationInfo().nativeLibraryDir;
        String internalLibDir = getFilesDir().getAbsolutePath() + "/lib";
        String internalBinDir = getFilesDir().getAbsolutePath() + "/bin";
        env.put("PYTHONHOME", pythonHome);
        env.put("LD_LIBRARY_PATH", internalLibDir + ":" + libDir);
        env.put("PATH", internalBinDir + ":" + (env.get("PATH") != null ? env.get("PATH") : ""));
    }

    private void setupNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(v -> switchTab("home"));
        findViewById(R.id.nav_browser).setOnClickListener(v -> switchTab("browser"));
        findViewById(R.id.nav_history).setOnClickListener(v -> switchTab("history"));
        findViewById(R.id.nav_terminal).setOnClickListener(v -> switchTab("logs"));
    }

    private void switchTab(String tab) {
        currentTab = tab;
        resetNavColors();
        if (homeView != null) homeView.setVisibility(View.GONE);
        if (browserView != null) browserView.setVisibility(View.GONE);
        if (historyView != null) historyView.setVisibility(View.GONE);
        if (logsView != null) logsView.setVisibility(View.GONE);

        switch (tab) {
            case "home":
                loadHomeView();
                if (browserView != null) {
                    WebView wv = browserView.findViewById(R.id.webView);
                    if (wv != null && wv.getUrl() != null && wv.getUrl().startsWith("http")) {
                        String currentUrl = wv.getUrl();
                        ((EditText)homeView.findViewById(R.id.home_url_input)).setText(currentUrl);
                        prefs.edit().putString("last_browser_url", currentUrl).apply();
                    }
                }
                setNavActive(R.id.nav_home);
                break;
            case "browser": 
                loadBrowserView(); 
                setNavActive(R.id.nav_browser); 
                break;
            case "history": 
                loadHistoryView(); 
                setNavActive(R.id.nav_history); 
                break;
            case "logs": 
                loadLogsView(); 
                setNavActive(R.id.nav_terminal); 
                break;
        }
    }

    private void setNavActive(int id) { ((TextView)((LinearLayout)findViewById(id)).getChildAt(0)).setTextColor(0xFF3EA6FF); }
    private void resetNavColors() {
        int inactive = 0xFFB0B0B0;
        ((TextView)((LinearLayout)findViewById(R.id.nav_home)).getChildAt(0)).setTextColor(inactive);
        ((TextView)((LinearLayout)findViewById(R.id.nav_browser)).getChildAt(0)).setTextColor(inactive);
        ((TextView)((LinearLayout)findViewById(R.id.nav_history)).getChildAt(0)).setTextColor(inactive);
        ((TextView)((LinearLayout)findViewById(R.id.nav_terminal)).getChildAt(0)).setTextColor(inactive);
    }

    private void loadHomeView() {
        if (homeView == null) {
            homeView = LayoutInflater.from(this).inflate(R.layout.layout_home, contentFrame, false);
            contentFrame.addView(homeView);
            homeView.findViewById(R.id.btn_fetch_info).setOnClickListener(v -> {
                String url = ((EditText)homeView.findViewById(R.id.home_url_input)).getText().toString().trim();
                if (!url.isEmpty()) fetchMetadata(url);
            });
            homeView.findViewById(R.id.btn_quick_download).setOnClickListener(v -> {
                String url = ((EditText)homeView.findViewById(R.id.home_url_input)).getText().toString().trim();
                if (!url.isEmpty()) startQuickDownload(url);
            });
            homeView.findViewById(R.id.home_download_btn).setOnClickListener(v -> startSelectedDownload());
            homeView.findViewById(R.id.home_mp3_btn).setOnClickListener(v -> startMp3Download());
        }
        homeView.setVisibility(View.VISIBLE);
    }

    private void startMp3Download() {
        String url = ((EditText)homeView.findViewById(R.id.home_url_input)).getText().toString().trim();
        homeView.findViewById(R.id.progress_card).setVisibility(View.VISIBLE);
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra("url", url);
        intent.putExtra("audio_only", true);
        intent.putExtra("bitrate", "320"); // High quality
        startService(intent);
    }

    private interface FetchCallback {
        void onStart();
        void onSuccess(String title, Bitmap thumb, List<String> formats);
        void onError(String error);
    }

    private void performFetch(String url, FetchCallback callback) {
        logToBackend("[SYSTEM] Fetching details for: " + url);
        new Thread(() -> {
            try {
                runOnUiThread(callback::onStart);

                String ytdlpCmd = getFilesDir().getAbsolutePath() + "/bin/yt-dlp";
                ProcessBuilder pb = new ProcessBuilder(ytdlpCmd, 
                    "--no-check-certificate", 
                    "--dump-json", 
                    "--no-playlist", 
                    "--no-warnings",
                    "--format-sort", "res,vcodec:h264",
                    url);
                pb.directory(getFilesDir());
                setupEnvForProcess(pb.environment());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("{") || sb.length() > 0) {
                        sb.append(line);
                    } else {
                        logToBackend(line);
                    }
                }
                p.waitFor();

                String jsonStr = sb.toString();
                int start = jsonStr.indexOf("{");
                int end = jsonStr.lastIndexOf("}");
                if (start == -1 || end == -1) throw new Exception("No JSON found");
                JSONObject json = new JSONObject(jsonStr.substring(start, end + 1));
                
                String title = json.optString("title", "Unknown");
                String thumbUrl = json.optString("thumbnail", "");
                
                List<String> formats = new ArrayList<>();
                Map<String, String> formatMap = new java.util.TreeMap<>((a, b) -> {
                    int v1 = Integer.parseInt(a.replaceAll("\\D", ""));
                    int v2 = Integer.parseInt(b.replaceAll("\\D", ""));
                    return Integer.compare(v2, v1);
                });

                JSONArray fmts = json.optJSONArray("formats");
                if (fmts != null) {
                    for(int i=0; i<fmts.length(); i++) {
                        JSONObject f = fmts.getJSONObject(i);
                        if (f.has("height") && !f.isNull("height") && f.optInt("height") > 0) {
                            String ext = f.optString("ext", "");
                            String vcodec = f.optString("vcodec", "");
                            
                            // Prefer h264/mp4 but allow others since we have ffmpeg
                            int h = f.getInt("height");
                            int fps = f.optInt("fps", 0);
                            String resKey = h + "p";
                            if (fps > 30) resKey += "60";
                            
                            String sizeStr = "";
                            if (f.has("filesize") && !f.isNull("filesize")) {
                                sizeStr = String.format(" ~%.1f MB", f.getDouble("filesize")/(1024*1024));
                            }
                            
                            String label = resKey + " [" + ext + "]" + sizeStr;
                            // Only add if it's a video+audio format or a high-quality video format
                            if (!formatMap.containsKey(resKey) || ext.equals("mp4")) {
                                formatMap.put(resKey, label);
                            }
                        }
                    }
                }
                final List<String> spinnerList = new ArrayList<>(formatMap.values());
                final Bitmap bmp = thumbUrl.isEmpty() ? null : BitmapFactory.decodeStream(new URL(thumbUrl).openStream());

                runOnUiThread(() -> callback.onSuccess(title, bmp, spinnerList));
            } catch (Exception e) {
                runOnUiThread(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    private void fetchMetadata(String url) {
        performFetch(url, new FetchCallback() {
            @Override public void onStart() {
                homeView.findViewById(R.id.loading_spinner).setVisibility(View.VISIBLE);
                homeView.findViewById(R.id.preview_card).setVisibility(View.GONE);
            }
            @Override public void onSuccess(String title, Bitmap bmp, List<String> formats) {
                homeView.findViewById(R.id.loading_spinner).setVisibility(View.GONE);
                homeView.findViewById(R.id.preview_card).setVisibility(View.VISIBLE);
                ((TextView)homeView.findViewById(R.id.txt_preview_title)).setText(title);
                if (bmp != null) ((ImageView)homeView.findViewById(R.id.img_thumbnail)).setImageBitmap(bmp);
                Spinner s = homeView.findViewById(R.id.quality_spinner);
                s.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, formats));
            }
            @Override public void onError(String error) {
                homeView.findViewById(R.id.loading_spinner).setVisibility(View.GONE);
                toastAndLog("Fetch failed: " + error);
            }
        });
    }

    private void startQuickDownload(String url) {
        homeView.findViewById(R.id.progress_card).setVisibility(View.VISIBLE);
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra("url", url);
        intent.putExtra("format", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");
        intent.putExtra("merge_format", "mp4");
        startService(intent);
    }

    private void startSelectedDownload() {
        String url = ((EditText)homeView.findViewById(R.id.home_url_input)).getText().toString().trim();
        Spinner s = homeView.findViewById(R.id.quality_spinner);
        if (s.getSelectedItem() == null) return;
        
        String selected = s.getSelectedItem().toString();
        String res = selected.split(" ")[0]; // e.g. "720p60" or "720p"
        String height = res.replaceAll("\\D", "");
        boolean is60 = res.contains("60");
        
        homeView.findViewById(R.id.progress_card).setVisibility(View.VISIBLE);
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra("url", url);
        
        // Exact format selection logic for mp4
        String f;
        if (is60) {
            f = "bestvideo[height<=" + height + "][fps>30][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4][height<=" + height + "]/best";
        } else {
            f = "bestvideo[height<=" + height + "][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4][height<=" + height + "]/best";
        }
        
        intent.putExtra("format", f);
        intent.putExtra("merge_format", "mp4");
        intent.putExtra("resolution", res); // Pass resolution string (e.g. 720p)
        startService(intent);
    }

    // Dynamic Island States
    private void setBrowserState(int state) {
        if (browserView == null) return;
        
        ViewGroup islandContent = browserView.findViewById(R.id.island_content);
        View stateBrowser = browserView.findViewById(R.id.state_browser);
        View stateLoading = browserView.findViewById(R.id.state_loading);
        View stateSelection = browserView.findViewById(R.id.state_selection);
        View stateSettings = browserView.findViewById(R.id.state_settings);
        
        android.transition.TransitionManager.beginDelayedTransition(islandContent);
        
        stateBrowser.setVisibility(state == 0 ? View.VISIBLE : View.GONE);
        stateLoading.setVisibility(state == 1 ? View.VISIBLE : View.GONE);
        stateSelection.setVisibility(state == 2 ? View.VISIBLE : View.GONE);
        stateSettings.setVisibility(state == 3 ? View.VISIBLE : View.GONE);
    }

    private void triggerBrowserDownload(String url) {
        setBrowserState(1); // Loading
        animateLoadingText();
        
        performFetch(url, new FetchCallback() {
            @Override public void onStart() {} // Already handled
            
            @Override public void onSuccess(String title, Bitmap bmp, List<String> formats) {
                setBrowserState(2); // Selection
                
                ((TextView)browserView.findViewById(R.id.sel_title)).setText(title);
                if (bmp != null) ((ImageView)browserView.findViewById(R.id.sel_thumbnail)).setImageBitmap(bmp);
                
                Spinner s = browserView.findViewById(R.id.sel_quality_spinner);
                s.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, formats));

                browserView.findViewById(R.id.sel_btn_close).setOnClickListener(v -> setBrowserState(0));

                browserView.findViewById(R.id.sel_btn_mp4).setOnClickListener(v -> {
                    if (s.getSelectedItem() == null) return;
                    startDownloadFromBrowser(url, s.getSelectedItem().toString(), false);
                });

                browserView.findViewById(R.id.sel_btn_mp3).setOnClickListener(v -> {
                    startDownloadFromBrowser(url, null, true);
                });
            }
            
            @Override public void onError(String error) {
                setBrowserState(0);
                toastAndLog("Fetch failed: " + error);
            }
        });
    }

    private void startDownloadFromBrowser(String url, String selectedFormat, boolean audioOnly) {
        setBrowserState(0); // Back to normal but show progress
        browserView.findViewById(R.id.browser_url_input).setVisibility(View.GONE);
        browserView.findViewById(R.id.browser_progress_container).setVisibility(View.VISIBLE);
        
        Intent intent = new Intent(MainActivity.this, DownloadService.class);
        intent.putExtra("url", url);
        
        if (audioOnly) {
            intent.putExtra("audio_only", true);
            intent.putExtra("bitrate", "320");
        } else {
            String res = selectedFormat.split(" ")[0];
            String height = res.replaceAll("\\D", "");
            boolean is60 = res.contains("60");
            String f = is60 ? 
                "bestvideo[height<=" + height + "][fps>30][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4][height<=" + height + "]/best" :
                "bestvideo[height<=" + height + "][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4][height<=" + height + "]/best";
            intent.putExtra("format", f);
            intent.putExtra("merge_format", "mp4");
            intent.putExtra("resolution", res);
        }
        startService(intent);
    }

    private void animateLoadingText() {
        new Thread(() -> {
            String[] dots = {".", "..", "..."};
            int i = 0;
            while (browserView != null && browserView.findViewById(R.id.state_loading).getVisibility() == View.VISIBLE) {
                final String text = "Get informations " + dots[i % dots.length];
                runOnUiThread(() -> ((TextView)browserView.findViewById(R.id.txt_loading_anim)).setText(text));
                i++;
                try { Thread.sleep(500); } catch (Exception e) {}
            }
        }).start();
    }

    private void loadBrowserView() {
        if (browserView == null) {
            browserView = LayoutInflater.from(this).inflate(R.layout.layout_browser, contentFrame, false);
            contentFrame.addView(browserView);
            WebView wv = browserView.findViewById(R.id.webView);
            EditText urlInput = browserView.findViewById(R.id.browser_url_input);
            
            wv.getSettings().setJavaScriptEnabled(true);
            wv.getSettings().setDomStorageEnabled(true);
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    urlInput.setText(url);
                }
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    urlInput.setText(url);
                    prefs.edit().putString("last_browser_url", url).apply();
                }
            });

            urlInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    String url = urlInput.getText().toString().trim();
                    if (!url.startsWith("http")) url = "https://www.google.com/search?q=" + url;
                    wv.loadUrl(url);
                    return true;
                }
                return false;
            });

            browserView.findViewById(R.id.btn_browser_back).setOnClickListener(v -> { if(wv.canGoBack()) wv.goBack(); });
            browserView.findViewById(R.id.btn_browser_refresh).setOnClickListener(v -> wv.reload());
            browserView.findViewById(R.id.btn_browser_home).setOnClickListener(v -> wv.loadUrl(prefs.getString("home_url", "https://www.google.com")));
            browserView.findViewById(R.id.btn_browser_download).setOnClickListener(v -> triggerBrowserDownload(wv.getUrl()));
            
            browserView.findViewById(R.id.btn_browser_settings).setOnClickListener(v -> {
                setBrowserState(3);
                ((EditText)browserView.findViewById(R.id.set_home_url)).setText(prefs.getString("home_url", "https://www.google.com"));
                ((android.widget.CheckBox)browserView.findViewById(R.id.set_debug_mode)).setChecked(prefs.getBoolean("debug_mode", true));
            });

            browserView.findViewById(R.id.set_btn_close).setOnClickListener(v -> setBrowserState(0));

            browserView.findViewById(R.id.set_btn_save).setOnClickListener(v -> {
                String newHome = ((EditText)browserView.findViewById(R.id.set_home_url)).getText().toString().trim();
                boolean debug = ((android.widget.CheckBox)browserView.findViewById(R.id.set_debug_mode)).isChecked();
                prefs.edit().putString("home_url", newHome).putBoolean("debug_mode", debug).apply();
                
                // Update tabs based on debug setting
                findViewById(R.id.nav_terminal).setVisibility(debug ? View.VISIBLE : View.GONE);
                ((LinearLayout)findViewById(R.id.nav_bar)).setWeightSum(debug ? 4 : 3);
                
                setBrowserState(0);
            });

            String lastUrl = prefs.getString("last_browser_url", "https://www.google.com");
            wv.loadUrl(lastUrl);
        }
        
        // Ensure backend tab visibility is correct on load
        boolean debug = prefs.getBoolean("debug_mode", true);
        findViewById(R.id.nav_terminal).setVisibility(debug ? View.VISIBLE : View.GONE);
        ((LinearLayout)findViewById(R.id.nav_bar)).setWeightSum(debug ? 4 : 3);
        
        browserView.setVisibility(View.VISIBLE);
    }

    private void loadHistoryView() {
        if (historyView == null) {
            historyView = LayoutInflater.from(this).inflate(R.layout.layout_history, contentFrame, false);
            contentFrame.addView(historyView);
            
            // Adjust tab bar height for SOTA feel
            View tabContainer = historyView.findViewById(R.id.btn_history_all).getParent() instanceof LinearLayout ? (View)historyView.findViewById(R.id.btn_history_all).getParent() : null;
            if (tabContainer != null) {
                ViewGroup.LayoutParams lp = tabContainer.getLayoutParams();
                lp.height = (int)(48 * getResources().getDisplayMetrics().density);
                tabContainer.setLayoutParams(lp);
            }

            historyView.findViewById(R.id.btn_history_all).setOnClickListener(v -> {
                historyType = "all";
                updateHistoryUI();
            });
            historyView.findViewById(R.id.btn_history_video).setOnClickListener(v -> {
                historyType = "video";
                updateHistoryUI();
            });
            historyView.findViewById(R.id.btn_history_audio).setOnClickListener(v -> {
                historyType = "audio";
                updateHistoryUI();
            });
        }
        historyView.setVisibility(View.VISIBLE);
        updateHistoryUI();
    }

    private void updateHistoryUI() {
        if (historyView == null) return;
        
        Button btnAll = historyView.findViewById(R.id.btn_history_all);
        Button btnVideo = historyView.findViewById(R.id.btn_history_video);
        Button btnAudio = historyView.findViewById(R.id.btn_history_audio);
        
        int activeColor = 0xFF3EA6FF;
        int inactiveColor = 0x00000000;
        int activeText = 0xFF3EA6FF;
        int inactiveText = 0xFFB0B0B0;

        btnAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf("all".equals(historyType) ? 0x203EA6FF : inactiveColor));
        btnAll.setTextColor("all".equals(historyType) ? activeText : inactiveText);
        
        btnVideo.setBackgroundTintList(android.content.res.ColorStateList.valueOf("video".equals(historyType) ? 0x203EA6FF : inactiveColor));
        btnVideo.setTextColor("video".equals(historyType) ? activeText : inactiveText);
        
        btnAudio.setBackgroundTintList(android.content.res.ColorStateList.valueOf("audio".equals(historyType) ? 0x203EA6FF : inactiveColor));
        btnAudio.setTextColor("audio".equals(historyType) ? activeText : inactiveText);

        checkAndSanitizeLocalFiles();

        File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        List<File> files = new ArrayList<>();
        if (downloadDir != null && downloadDir.exists()) {
            for (File f : downloadDir.listFiles()) {
                if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".jpg") || name.endsWith(".webp") || name.endsWith(".extracted") || name.endsWith(".json")) continue;
                    
                    boolean isVideo = name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm");
                    boolean isAudio = name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav") || name.endsWith(".opus");

                    if ("all".equals(historyType)) {
                        if (isVideo || isAudio) files.add(f);
                    } else if ("video".equals(historyType)) {
                        if (isVideo) files.add(f);
                    } else {
                        if (isAudio) files.add(f);
                    }
                }
            }
        }
        ((GridView)historyView.findViewById(R.id.history_grid)).setAdapter(new HistoryAdapter(this, files));
    }

    private class HistoryAdapter extends ArrayAdapter<File> {
        public HistoryAdapter(Context context, List<File> files) { super(context, 0, files); }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_history, parent, false);
            File file = getItem(position);
            String name = file.getName();
            ((TextView)convertView.findViewById(R.id.item_title)).setText(name);
            
            // File size/info
            long size = file.length() / (1024 * 1024);
            ((TextView)convertView.findViewById(R.id.item_details)).setText(size + " MB");

            // Type Badge
            TextView badge = convertView.findViewById(R.id.item_type_badge);
            boolean isVideo = name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm");
            badge.setText(isVideo ? "VIDEO" : "AUDIO");
            badge.setBackgroundColor(isVideo ? 0xCC3EA6FF : 0xCCBB86FC);

            // Try to find thumbnail
            String abs = file.getAbsolutePath();
            String baseName = abs.substring(0, abs.lastIndexOf("."));
            File thumb = new File(baseName + ".jpg");
            if (!thumb.exists()) thumb = new File(baseName + ".webp");

            ImageView thumbView = convertView.findViewById(R.id.item_thumbnail);
            if (thumb.exists()) {
                thumbView.setImageBitmap(BitmapFactory.decodeFile(thumb.getAbsolutePath()));
            } else {
                thumbView.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            thumbView.setOnClickListener(v -> playMedia(file));
            convertView.setOnClickListener(v -> playMedia(file));

            convertView.findViewById(R.id.btn_play).setOnClickListener(v -> playMedia(file));

            convertView.findViewById(R.id.btn_export).setOnClickListener(v -> {
                if (checkPermissions()) {
                    exportFile(file);
                }
            });

            convertView.findViewById(R.id.btn_share).setOnClickListener(v -> {
                try {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType(isVideo ? "video/*" : "audio/*");
                    Uri contentUri = Uri.parse("content://com.example.ytdlp.fileprovider" + file.getAbsolutePath());
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "Share Media"));
                } catch (Exception e) {
                    toastAndLog("Share failed: " + e.getMessage());
                }
            });

            convertView.findViewById(R.id.btn_delete).setOnClickListener(v -> { 
                file.delete(); 
                File t = new File(baseName + ".jpg"); if (t.exists()) t.delete();
                File tw = new File(baseName + ".webp"); if (tw.exists()) tw.delete();
                updateHistoryUI(); 
            });
            return convertView;
        }

        private void playMedia(File file) {
            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putExtra("path", file.getAbsolutePath());
            startActivity(intent);
        }
    }

    private void toastAndLog(String msg) {
        logToBackend("[TOAST] " + msg);
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private void exportFile(File srcFile) {
        String sanitizedName = sanitizeFilename(srcFile.getName());
        logToBackend("[SYSTEM] Exporting: " + srcFile.getName() + " -> " + sanitizedName);
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, srcFile.getName().toLowerCase().endsWith(".mp3") ? "audio/mpeg" : "video/mp4");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                android.content.ContentResolver resolver = getContentResolver();
                Uri externalUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri insertUri = resolver.insert(externalUri, values);

                if (insertUri != null) {
                    try (InputStream in = new java.io.FileInputStream(srcFile);
                         OutputStream out = resolver.openOutputStream(insertUri)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                        toastAndLog("Exported to Downloads");
                    }
                } else {
                    throw new Exception("Could not create MediaStore entry");
                }
            } else {
                File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File destFile = new File(publicDir, sanitizedName);
                try (InputStream in = new java.io.FileInputStream(srcFile);
                     OutputStream out = new FileOutputStream(destFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                toastAndLog("Exported to " + destFile.getAbsolutePath());
                android.media.MediaScannerConnection.scanFile(this, new String[]{destFile.toString()}, null, null);
            }
        } catch (Exception e) {
            toastAndLog("Export failed: " + e.getMessage());
            logToBackend("[ERROR] Export error: " + e.toString());
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                logToBackend("[SYSTEM] Requesting storage permissions...");
                requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, 100);
                return false;
            }
        }
        return true;
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "unknown";
        // Replace all non-alphanumeric (except . and -) with _
        return name.replaceAll("[^a-zA-Z0-9.-]", "_").replaceAll("__+", "_");
    }

    private void checkAndSanitizeLocalFiles() {
        File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && downloadDir.exists()) {
            for (File f : downloadDir.listFiles()) {
                if (f.isFile()) {
                    String originalName = f.getName();
                    int dot = originalName.lastIndexOf(".");
                    if (dot > 0) {
                        String ext = originalName.substring(dot);
                        String base = originalName.substring(0, dot);
                        String sanitizedBase = sanitizeFilename(base);
                        if (!base.equals(sanitizedBase)) {
                            File newFile = new File(downloadDir, sanitizedBase + ext);
                            if (f.renameTo(newFile)) {
                                logToBackend("[SYSTEM] Sanitized: " + originalName + " -> " + newFile.getName());
                                // Also rename thumbnails
                                for (String tExt : new String[]{".jpg", ".webp"}) {
                                    File oldT = new File(downloadDir, base + tExt);
                                    if (oldT.exists()) {
                                        oldT.renameTo(new File(downloadDir, sanitizedBase + tExt));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void loadLogsView() {
        if (logsView == null) {
            logsView = LayoutInflater.from(this).inflate(R.layout.layout_logs, contentFrame, false);
            contentFrame.addView(logsView);
            logsView.findViewById(R.id.send_btn).setOnClickListener(v -> {
                EditText input = logsView.findViewById(R.id.manual_input);
                String cmd = input.getText().toString().trim();
                if (!cmd.isEmpty()) { input.setText(""); logToBackend("$ " + cmd); Intent i = new Intent(this, DownloadService.class); i.putExtra("custom_cmd", cmd); startService(i); }
            });
            logsView.findViewById(R.id.clear_btn).setOnClickListener(v -> { backendLogs.setLength(0); updateLogUI(); });
            logsView.findViewById(R.id.kill_btn).setOnClickListener(v -> {
                Intent i = new Intent(this, DownloadService.class);
                i.setAction(DownloadService.ACTION_KILL);
                startService(i);
            });
            logsView.findViewById(R.id.copy_btn).setOnClickListener(v -> {
                android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(android.content.ClipData.newPlainText("Logs", backendLogs.toString()));
                toastAndLog("Copied");
            });
        }
        logsView.setVisibility(View.VISIBLE);
        updateLogUI();
    }

    private void logToBackend(String line) { backendLogs.append(line).append("\n"); runOnUiThread(this::updateLogUI); }
    private void updateLogUI() { if (logsView != null && logsView.getVisibility() == View.VISIBLE) ((TextView)logsView.findViewById(R.id.logView)).setText(backendLogs.toString()); }

    private void prepareEnvironment() {
        File pyDir = new File(pythonHome);
        File marker = new File(pyDir, ".extracted");
        if (!marker.exists()) {
            logToBackend("[SYSTEM] Extracting Python environment...");
            pyDir.mkdirs();
            String tarPath = getFilesDir().getAbsolutePath() + "/python.tar.gz";
            extractZipAsset("python.tar.gz", tarPath);
            try {
                logToBackend("[SYSTEM] Using system tar for extraction...");
                
                // Use system tar via shell for extraction
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "tar -xzf " + tarPath + " -C " + pythonHome});
                int res = p.waitFor();
                
                if (res == 0) {
                    marker.createNewFile();
                    logToBackend("[SYSTEM] Python extracted successfully.");
                    // Debug check
                    File enc = new File(pythonHome, "lib/python3.12/encodings/__init__.py");
                    if (enc.exists()) logToBackend("[DEBUG] encodings module found.");
                    else logToBackend("[DEBUG] encodings module MISSING at " + enc.getAbsolutePath());
                } else {
                    logToBackend("[ERROR] Extraction failed with code " + res);
                }
                
                // Cleanup tar file
                new File(tarPath).delete();
            } catch (Exception e) {
                logToBackend("[ERROR] Extraction exception: " + e.getMessage());
            }
        }
        File ytdlpFile = new File(ytdlpPath);
        if (!ytdlpFile.exists()) {
            logToBackend("[SYSTEM] yt-dlp missing. Waiting for download...");
        }
    }

    private void setupLibrarySymlinks() {
        String libDir = getApplicationInfo().nativeLibraryDir;
        String internalLibDir = getFilesDir().getAbsolutePath() + "/lib";
        String internalBinDir = getFilesDir().getAbsolutePath() + "/bin";
        new File(internalLibDir).mkdirs(); new File(internalBinDir).mkdirs();
        
        String[][] mappings = {
            {"libpython3.12.so.1.0", "libpython3_12.so"},
            {"libssl.so.3", "libssl.so"},
            {"libcrypto.so.3", "libcrypto.so"},
            {"libz.so.1", "libz_1.so"},
            {"libbz2.so.1.0", "libbz2.so"},
            {"liblzma.so.5", "liblzma.so"},
            {"libx264.so.164", "libx264_164.so"},
            {"libexpat.so.1", "libexpat_1.so"}
        };
        
        for (String[] map : mappings) { 
            try { 
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "ln -sf " + libDir + "/" + map[1] + " " + internalLibDir + "/" + map[0]}).waitFor(); 
            } catch (Exception e) {} 
        }
        
        // Also link all .so files from libDir to internalLibDir with their own names
        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "for f in " + libDir + "/*.so; do ln -sf $f " + internalLibDir + "/$(basename $f); done"}).waitFor();
        } catch (Exception e) {}
        
        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "ln -sf " + libDir + "/libpython_exe.so " + internalBinDir + "/python3"}).waitFor();
            File ytdlpWrapper = new File(internalBinDir, "yt-dlp");
            String wrapperContent = "#!/system/bin/sh\n" +
                "export PYTHONHOME=\"" + pythonHome + "\"\n" +
                "export PYTHONPATH=\"" + pythonHome + "/lib/python3.12:" + pythonHome + "/lib/python3.12/site-packages\"\n" +
                "export LD_LIBRARY_PATH=\"" + internalLibDir + ":" + libDir + ":/system/lib64:/system/lib\"\n" +
                "export PATH=\"" + internalBinDir + ":$PATH\"\n" +
                "exec " + internalBinDir + "/python3 " + ytdlpPath + " \"$@\"\n";
            FileOutputStream fos = new FileOutputStream(ytdlpWrapper);
            fos.write(wrapperContent.getBytes());
            fos.close();
            ytdlpWrapper.setExecutable(true);
            Runtime.getRuntime().exec(new String[]{"chmod", "755", ytdlpWrapper.getAbsolutePath()}).waitFor();

            // Create ffmpeg wrapper (Now points to the static binary)
            File ffmpegWrapper = new File(internalBinDir, "ffmpeg");
            String ffmpegContent = "#!/system/bin/sh\n" +
                "export LD_LIBRARY_PATH=\"" + internalLibDir + ":" + libDir + ":/system/lib64:/system/lib\"\n" +
                "exec " + libDir + "/libffmpeg_exe.so \"$@\"\n";
            FileOutputStream fos2 = new FileOutputStream(ffmpegWrapper);
            fos2.write(ffmpegContent.getBytes());
            fos2.close();
            ffmpegWrapper.setExecutable(true);
            Runtime.getRuntime().exec(new String[]{"chmod", "755", ffmpegWrapper.getAbsolutePath()}).waitFor();

            logToBackend("[SYSTEM] Shell wrappers initialized.");
        } catch (Exception e) {
            logToBackend("[ERROR] Wrapper setup failed: " + e.getMessage());
        }
    }

    private void showDisclaimer() {
        new AlertDialog.Builder(this)
            .setTitle("Legal Disclaimer & Usage Policy")
            .setMessage("This application is a powerful tool utilizing yt-dlp, which supports over 1,000+ websites. By proceeding, you acknowledge and agree to the following:\n\n" +
                       "1. Liability: You are solely responsible for any content you download. The developer assumes no liability for misuse, copyright infringement, or violations of any platform's Terms of Service.\n\n" +
                       "2. Permitted Use: You should only download media for which you have explicit permission from the copyright holder or which is your own property.\n\n" +
                       "3. Copyright: Always respect international copyright laws. This tool is intended for personal and educational use only.\n\n" +
                       "Use this software at your own risk. By clicking 'AGREE', you confirm your compliance with these terms.")
            .setPositiveButton("AGREE", (d, w) -> {
                prefs.edit().putBoolean("first_start", false).apply();
                downloadYtDlp();
            })
            .setNegativeButton("EXIT", (d, w) -> finish())
            .setCancelable(false)
            .show();
    }

    private void downloadYtDlp() {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("Initializing")
            .setMessage("Downloading latest yt-dlp...")
            .setCancelable(false)
            .create();
        progressDialog.show();

        new Thread(() -> {
            try {
                // First ensure environment is prepared (python etc)
                prepareEnvironment();
                setupLibrarySymlinks();

                // Now download latest yt-dlp
                URL url = new URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setInstanceFollowRedirects(true);
                InputStream is = conn.getInputStream();
                FileOutputStream os = new FileOutputStream(new File(ytdlpPath));
                byte[] b = new byte[8192];
                int l;
                while ((l = is.read(b)) > 0) os.write(b, 0, l);
                os.flush(); os.close(); is.close();

                new File(ytdlpPath).setExecutable(true);
                Runtime.getRuntime().exec(new String[]{"chmod", "755", ytdlpPath}).waitFor();

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    switchTab("home");
                    logToBackend("[SYSTEM] yt-dlp updated to latest version.");
                });
                runTest("python3 --version");
                runTest("yt-dlp --version");
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    toastAndLog("Download failed: " + e.getMessage());
                    switchTab("home"); // Fallback to asset version
                });
            }
        }).start();
    }

    private void extractZipAsset(String n, String o) {
        try {
            InputStream is = getAssets().open(n);
            FileOutputStream os = new FileOutputStream(new File(o));
            byte[] b = new byte[16384];
            int l;
            while((l=is.read(b))>0) os.write(b,0,l);
            os.flush(); os.close(); is.close();
            logToBackend("[SYSTEM] Asset extracted to " + o);
        } catch(Exception e){
            logToBackend("[ERROR] Failed to extract asset " + n + ": " + e.getMessage());
        }
    }
    @Override protected void onDestroy() { super.onDestroy(); unregisterReceiver(receiver); }

    private class DownloadReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            String raw = intent.getStringExtra("raw");
            if (raw != null) {
                logToBackend(raw);
                if (raw.contains("[download]") && raw.contains("%")) {
                    try {
                        String[] parts = raw.split("\\s+");
                        String percent = "";
                        for (String p : parts) if (p.contains("%")) percent = p.replace("%", "");
                        if (!percent.isEmpty()) {
                            final float pVal = Float.parseFloat(percent);
                            runOnUiThread(() -> {
                                if (homeView != null) {
                                    ((ProgressBar)homeView.findViewById(R.id.home_progress_bar)).setProgress((int) pVal);
                                    ((TextView)homeView.findViewById(R.id.progress_percent)).setText((int) pVal + "%");
                                }
                                if (browserView != null && browserView.findViewById(R.id.browser_progress_container).getVisibility() == View.VISIBLE) {
                                    ((ProgressBar)browserView.findViewById(R.id.browser_progress_bar)).setProgress((int) pVal);
                                    ((TextView)browserView.findViewById(R.id.browser_progress_text)).setText((int) pVal + "%");
                                    if (pVal >= 100) {
                                        // Reset after delay
                                        new android.os.Handler().postDelayed(() -> {
                                            browserView.findViewById(R.id.browser_progress_container).setVisibility(View.GONE);
                                            browserView.findViewById(R.id.browser_url_input).setVisibility(View.VISIBLE);
                                        }, 2000);
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {}
                }
            }
            if ("finished".equals(intent.getStringExtra("status"))) toastAndLog("Done!");
        }
    }
}
