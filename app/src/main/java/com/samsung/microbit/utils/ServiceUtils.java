package com.samsung.microbit.utils;

import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.samsung.microbit.core.IPCMessageManager;
import com.samsung.microbit.core.PopUpServiceReceiver;
import com.samsung.microbit.data.model.CmdArg;
import com.samsung.microbit.data.model.NameValuePair;
import com.samsung.microbit.service.BLEService;
import com.samsung.microbit.service.IPCService;
import com.samsung.microbit.service.PluginService;

import static com.samsung.microbit.BuildConfig.DEBUG;

public class ServiceUtils {
    private static final String TAG = ServiceUtils.class.getSimpleName();

    private ServiceUtils() {
    }

    private static void logi(Class serviceClass, String message) {
        if(serviceClass.equals(IPCService.class)) {
            IPCService.logi(message);
        } else if(serviceClass.equals(PluginService.class)) {
            PluginService.logi(message);
        } else if(serviceClass.equals(BLEService.class)) {
            BLEService.logi(message);
        } else if(serviceClass.equals(PopUpServiceReceiver.class)) {
            PopUpServiceReceiver.logi(message);
        }
    }

    public static void sendtoIPCService(Class serviceClass, int messageType, int eventCategory, CmdArg cmd,
                                        NameValuePair[] args) {
        if (DEBUG) {
            if(cmd != null) {
                logi(serviceClass, serviceClass.getSimpleName() + ": sendtoIPCService(), " + messageType + "," +
                        eventCategory + "," +
                        "(" + cmd.getValue() + "," + cmd.getCMD() + "");
            } else {
                logi(serviceClass, serviceClass.getSimpleName() + ": sendtoIPCService(), " + messageType + "," + eventCategory);
            }
        }

        IPCMessageManager.sendIPCMessage(IPCService.class, messageType, eventCategory, cmd, args);
    }

    public static void sendtoPluginService(Class serviceClass, int messageType, int eventCategory, CmdArg cmd,
                                        NameValuePair[] args) {
        if (DEBUG) {
            if(cmd != null) {
                logi(serviceClass, serviceClass.getSimpleName() + ": sendtoPluginService(), " + messageType + "," +
                        eventCategory + "," +
                        "(" + cmd.getValue() + "," + cmd.getCMD() + "");
            } else {
                logi(serviceClass, serviceClass.getSimpleName() + ": sendtoPluginService(), " + messageType + "," +
                        eventCategory);
            }
        }

        IPCMessageManager.sendIPCMessage(PluginService.class, messageType, eventCategory, cmd, args);
    }

    public static void sendtoBLEService(Class serviceClass, int messageType, int eventCategory, CmdArg cmd,
                                        NameValuePair[] args) {
        if (DEBUG) {
            if(cmd != null) {
                logi(serviceClass, serviceClass.getSimpleName() + ": sendtoBLEService(), " + messageType + "," + eventCategory +
                        "," + cmd
                        .getValue() + "," + cmd.getCMD() + "");
            } else {
                logi(serviceClass, serviceClass.getSimpleName() + ": sendtoBLEService(), " + messageType + "," + eventCategory);
            }
        }

        IPCMessageManager.sendIPCMessage(BLEService.class, messageType, eventCategory, cmd, args);
    }

    public static void sendReplyCommand(int mbsService, CmdArg cmd) {
        if (IPCMessageManager.getInstance().getClientMessenger() != null) {
            Message msg = Message.obtain(null, mbsService);
            Bundle bundle = new Bundle();
            bundle.putInt("cmd", cmd.getCMD());
            bundle.putString("value", cmd.getValue());
            msg.setData(bundle);

            try {
                IPCMessageManager.getInstance().getClientMessenger().send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        }
    }
}
