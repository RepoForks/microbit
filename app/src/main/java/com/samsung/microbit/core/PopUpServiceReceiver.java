package com.samsung.microbit.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.data.constants.EventCategories;
import com.samsung.microbit.data.constants.IPCConstants;
import com.samsung.microbit.data.constants.ServiceIds;
import com.samsung.microbit.service.PluginServiceNew;
import com.samsung.microbit.ui.PopUp;
import com.samsung.microbit.ui.activity.PopUpActivity;
import com.samsung.microbit.utils.ServiceUtils;

import static com.samsung.microbit.BuildConfig.DEBUG;

/**
 * This broadcastreceiver intercepts "PopUp.showFromService" requests from background Service
 * like PluginService. Note that custom OnClickListener are not supported because
 * Service and App run in different process.
 * Support for custom OnClickListener may require RPC implementation.
 * PopUp requested from PluginService do not currently need custom OnClickListener.
 */
public class PopUpServiceReceiver extends BroadcastReceiver {
    private static final String TAG = PopUpServiceReceiver.class.getSimpleName();

    public static void logi(String message) {
        if (DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        int popupExtraOkType = intent.getIntExtra(PopUp.INTENT_EXTRA_OK_ACTION, PopUp.OK_ACTION_NONE);

        final View.OnClickListener okListener;

        if (popupExtraOkType == PopUp.OK_ACTION_STOP_SERVICE_PLAYING) {
            okListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    ServiceUtils.IMessengerFinder messengerFinder = MBApp.getApp().getMessengerFinder();

                    if (messengerFinder != null) {
                        Messenger bleMessenger = messengerFinder.getMessengerForService(PluginServiceNew.class
                                 .getName());

                        if (bleMessenger != null) {
                            Message message = ServiceUtils.composeMessage(IPCConstants.MESSAGE_ANDROID,
                                    EventCategories.IPC_PLUGIN_STOP_PLAYING, ServiceIds.SERVICE_NONE, null, null);
                            if (message != null) {
                                try {
                                    bleMessenger.send(message);
                                } catch (RemoteException e) {
                                    Log.e(TAG, e.toString());
                                }
                            }
                        }
                    }
                    PopUp.hide();
                }
            };
        } else {
            okListener = null;
        }

        PopUp.show(intent.getStringExtra(PopUpActivity.INTENT_EXTRA_MESSAGE),
                intent.getStringExtra(PopUpActivity.INTENT_EXTRA_TITLE),
                intent.getIntExtra(PopUpActivity.INTENT_EXTRA_ICON, 0),
                intent.getIntExtra(PopUpActivity.INTENT_EXTRA_ICONBG, 0),
                intent.getIntExtra(PopUpActivity.INTENT_GIFF_ANIMATION_CODE, 0), /* Default 0 */
                intent.getIntExtra(PopUpActivity.INTENT_EXTRA_TYPE, PopUp.TYPE_MAX),
                okListener, okListener);
    }
}