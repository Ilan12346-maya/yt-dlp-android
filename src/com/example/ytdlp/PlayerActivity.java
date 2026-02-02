package com.example.ytdlp;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.File;

public class PlayerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        String filePath = getIntent().getStringExtra("path");
        if (filePath == null) { finish(); return; }

        VideoView videoView = findViewById(R.id.videoView);
        TextView titleView = findViewById(R.id.player_title);
        ProgressBar loader = findViewById(R.id.player_loader);

        File file = new File(filePath);
        titleView.setText(file.getName());
        
        boolean isAudio = filePath.toLowerCase().endsWith(".mp3") || filePath.toLowerCase().endsWith(".m4a") || filePath.toLowerCase().endsWith(".wav") || filePath.toLowerCase().endsWith(".opus");
        if (isAudio) {
            videoView.setBackgroundColor(0xFF121212); // Dark background for audio
            // Optional: You could add an icon or album art here
        }

        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
        videoView.setVideoURI(Uri.fromFile(file));

        videoView.setOnPreparedListener(mp -> {
            loader.setVisibility(View.GONE);
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            titleView.setText("Error playing video.");
            loader.setVisibility(View.GONE);
            return true;
        });
    }
}
