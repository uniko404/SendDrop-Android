package com.senddrop.android;

import android.app.Application;
import android.os.Environment;

public class App extends Application {
    private static App instance;
    public static String FILES_DIR;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        FILES_DIR = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
        ).getAbsolutePath() + "/SendDrop/";
    }

    public static App getInstance() {
        return instance;
    }
}
