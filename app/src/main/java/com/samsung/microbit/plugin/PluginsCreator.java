package com.samsung.microbit.plugin;

import android.util.Log;
import android.util.SparseArray;

import com.samsung.microbit.data.constants.EventCategories;

import static com.samsung.microbit.BuildConfig.DEBUG;

public class PluginsCreator {
    private static final String TAG = PluginsCreator.class.getSimpleName();

    private static final PluginsCreator instance = new PluginsCreator();

    public static PluginsCreator getInstance() {
        return instance;
    }

    private static void logi(String message) {
        Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
    }

    private SparseArray<AbstractPlugin> cachedPlugins = new SparseArray<>();

    public AbstractPlugin createPlugin(int eventCategory) {
        if (DEBUG) {
            logi("handleMessage() ##  " + eventCategory);
        }

        AbstractPlugin cachedPlugin = cachedPlugins.get(eventCategory);

        if (cachedPlugin != null) {
            return cachedPlugin;
        }

        final AbstractPlugin abstractPlugin;

        switch (eventCategory) {
            case EventCategories.SAMSUNG_REMOTE_CONTROL_ID:
                abstractPlugin = new RemoteControlPlugin();
                break;

            case EventCategories.SAMSUNG_ALERTS_ID:
                abstractPlugin = new AlertPlugin();
                break;

            case EventCategories.SAMSUNG_AUDIO_RECORDER_ID:
                abstractPlugin = new AudioRecordPlugin();
                break;

            case EventCategories.SAMSUNG_CAMERA_ID:
                abstractPlugin = new CameraPlugin();
                break;

            case EventCategories.SAMSUNG_SIGNAL_STRENGTH_ID:
                abstractPlugin = new InformationPlugin();
                break;

            case EventCategories.SAMSUNG_DEVICE_INFO_ID:
                abstractPlugin = new InformationPlugin();
                break;

            case EventCategories.SAMSUNG_TELEPHONY_ID:
                abstractPlugin = new TelephonyPlugin();
                break;
            default:
                abstractPlugin = null;
        }

        if (abstractPlugin == null) {
            Log.e(TAG, "Plugin not initialized");
        } else {
            cachedPlugins.put(eventCategory, abstractPlugin);
        }
        return abstractPlugin;
    }

    public void destroy() {
        for (int i = 0; i < cachedPlugins.size(); i++) {
            cachedPlugins.valueAt(i).destroy();
        }
        cachedPlugins.clear();
    }
}
