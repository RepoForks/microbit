package com.samsung.microbit.plugin;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.samsung.microbit.core.Utils;
import com.samsung.microbit.model.CmdArg;
import com.samsung.microbit.model.Constants;
import com.samsung.microbit.service.PluginService;
import com.samsung.microbit.ui.activity.CameraActivity_OldAPI;

public class CameraPlugin {

	private static Context context = null;

	private static final String TAG = "CameraPlugin";

    private static int m_NextState = -1 ;


    private static MediaPlayer.OnCompletionListener m_OnCompletionListener = new MediaPlayer.OnCompletionListener()
    {
        @Override
        public void onCompletion(MediaPlayer mp) {
            performOnEnd();
        }
    };

	public static void pluginEntry(Context ctx, CmdArg cmd) {

		context = ctx;
		switch (cmd.getCMD()) {
			case Constants.SAMSUNG_CAMERA_EVT_LAUNCH_PHOTO_MODE:
                m_NextState = Constants.SAMSUNG_CAMERA_EVT_LAUNCH_PHOTO_MODE ;
                Utils.playAudio(Utils.getLaunchCameraAudio() , m_OnCompletionListener);
				break;

			case Constants.SAMSUNG_CAMERA_EVT_LAUNCH_VIDEO_MODE:
                m_NextState = Constants.SAMSUNG_CAMERA_EVT_LAUNCH_VIDEO_MODE ;
                Utils.playAudio(Utils.getLaunchCameraAudio(), m_OnCompletionListener);
				break;

			case Constants.SAMSUNG_CAMERA_EVT_TAKE_PHOTO:
                m_NextState = Constants.SAMSUNG_CAMERA_EVT_TAKE_PHOTO ;
                Utils.playAudio(Utils.getPictureTakenAudio(), m_OnCompletionListener);
				break;

			case Constants.SAMSUNG_CAMERA_EVT_START_VIDEO_CAPTURE:
                recVideoStart();
				break;

			case Constants.SAMSUNG_CAMERA_EVT_STOP_VIDEO_CAPTURE:
                recVideoStop();
				break;

			case Constants.SAMSUNG_CAMERA_EVT_STOP_PHOTO_MODE:
			case Constants.SAMSUNG_CAMERA_EVT_STOP_VIDEO_MODE:
                closeCamera();
				break;

			case Constants.SAMSUNG_CAMERA_EVT_TOGGLE_FRONT_REAR:
			default:
				break;
		}
	}

	public static void sendReplyCommand(int mbsService, CmdArg cmd) {
		if (PluginService.mClientMessenger != null) {
			Message msg = Message.obtain(null, mbsService);
			Bundle bundle = new Bundle();
			bundle.putInt("cmd", cmd.getCMD());
			bundle.putString("value", cmd.getValue());
			msg.setData(bundle);

			try {
				PluginService.mClientMessenger.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}

    private static void performOnEnd()
    {
        Log.d("CameraPlugin" , "Next state - " + m_NextState);
        switch (m_NextState)
        {
            case Constants.SAMSUNG_CAMERA_EVT_LAUNCH_PHOTO_MODE:
                launchCameraForPic();
                break;
            case Constants.SAMSUNG_CAMERA_EVT_LAUNCH_VIDEO_MODE:
                launchCameraForVideo();
                break;
            case Constants.SAMSUNG_CAMERA_EVT_TAKE_PHOTO:
                takePic();
                break;
        }
    }
	//This function should trigger an Activity that would be responsible of starting the camera app to take a picture.
	//The same activity should also store the result of the camera app, if valid
	private static void launchCameraForPic() {
		Intent mIntent = new Intent(context, CameraActivity_OldAPI.class);
		mIntent.setAction("com.samsung.microbit.activity.CameraActivity.action.OPEN_FOR_PIC");
		mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(mIntent);
	}

	private static void takePic() {
		Intent intent = new Intent("TAKE_PIC");
		context.sendBroadcast(intent);
	}

	private static void launchCameraForVideo() {
		Intent mIntent = new Intent(context, CameraActivity_OldAPI.class);
		mIntent.setAction("com.samsung.microbit.activity.CameraActivity.action.OPEN_FOR_VIDEO");
		mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(mIntent);
	}

	private static void recVideoStart() {
		Intent intent = new Intent("START_VIDEO");
		context.sendBroadcast(intent);
	}

	private static void recVideoStop() {
		Intent intent = new Intent("STOP_VIDEO");
		context.sendBroadcast(intent);
	}

	private static void closeCamera() {
		Intent intent = new Intent("CLOSE");
		context.sendBroadcast(intent);
	}
}
