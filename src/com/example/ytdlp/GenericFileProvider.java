package com.example.ytdlp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;

public class GenericFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Log.d("FileProvider", "Opening URI: " + uri);
        // The path in the URI is the absolute path to the file
        String path = uri.getPath();
        if (path == null) {
            Log.e("FileProvider", "Path is null");
            throw new FileNotFoundException("No path");
        }
        File file = new File(path);
        if (!file.exists()) {
            Log.e("FileProvider", "File not found: " + path);
            throw new FileNotFoundException("File not found: " + path);
        }
        Log.d("FileProvider", "Serving file: " + path);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
