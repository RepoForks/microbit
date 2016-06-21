package com.samsung.microbit.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.samsung.microbit.BuildConfig;
import com.samsung.microbit.core.IPCMessageManager;
import com.samsung.microbit.core.bluetooth.BluetoothUtils;
import com.samsung.microbit.model.CmdArg;
import com.samsung.microbit.model.ConnectedDevice;
import com.samsung.microbit.model.NameValuePair;

import java.util.UUID;

public class IPCService extends Service {

    private static IPCService instance;

    public static final String INTENT_MICROBIT_BUTTON_NOTIFICATION = "com.samsung.microbit.service.IPCService.INTENT_MICROBIT_BUTTON_NOTIFICATION";

    public static final String INTENT_BLE_NOTIFICATION = "com.samsung.microbit.service.IPCService.INTENT_BLE_NOTIFICATION";
    public static final String INTENT_MICROBIT_NOTIFICATION = "com.samsung.microbit.service.IPCService.INTENT_MICROBIT_NOTIFICATION";

    public static final String NOTIFICATION_CAUSE = "com.samsung.microbit.service.IPCService.CAUSE";

    static final String TAG = "IPCService";
    private boolean isDebug = BuildConfig.DEBUG;

    void logi(String message) {
        Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
    }

    public IPCService() {
        instance = this;
        startIPCListener();
    }

    public static IPCService getInstance() {
        return instance;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isDebug) {
            logi("onStartCommand()");
        }

        return START_STICKY;
    }

	/*
     * Business method
	 */

    public void bleDisconnect() {
        sendtoBLEService(IPCMessageManager.ANDROID_MESSAGE, IPCMessageManager.IPC_FUNCTION_DISCONNECT, null, null);
    }

    public void bleConnect() {
        sendtoBLEService(IPCMessageManager.ANDROID_MESSAGE, IPCMessageManager.IPC_FUNCTION_CONNECT, null, null);
    }

    public void bleReconnect() {
        sendtoBLEService(IPCMessageManager.ANDROID_MESSAGE, IPCMessageManager.IPC_FUNCTION_RECONNECT, null, null);
    }

    public void writeCharacteristic(UUID service, UUID characteristic, int value, int type) {

        NameValuePair[] args = new NameValuePair[4];
        args[0] = new NameValuePair(IPCMessageManager.BUNDLE_SERVICE_GUID, service.toString());
        args[1] = new NameValuePair(IPCMessageManager.BUNDLE_CHARACTERISTIC_GUID, characteristic.toString());
        args[2] = new NameValuePair(IPCMessageManager.BUNDLE_CHARACTERISTIC_VALUE, value);
        args[3] = new NameValuePair(IPCMessageManager.BUNDLE_CHARACTERISTIC_TYPE, type);
        sendtoBLEService(IPCMessageManager.ANDROID_MESSAGE, IPCMessageManager.IPC_FUNCTION_WRITE_CHARACTERISTIC,
                null, args);
    }

    /*
     * setup IPCMessageManager
     */
    @Override
    public IBinder onBind(Intent intent) {
        return IPCMessageManager.getInstance().getClientMessenger().getBinder();
    }

    public void startIPCListener() {
        if (isDebug) {
            logi("startIPCListener()");
        }

        IPCMessageManager.connectMaybeInit(IPCService.class.getName(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                handleIncomingMessage(msg);
                return true;
            }
        });

			/*
             * Make the initial connection to other processes
			 */
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(IPCMessageManager.STARTUP_DELAY);
                    sendtoBLEService(IPCMessageManager.ANDROID_MESSAGE, IPCMessageManager.IPC_FUNCTION_CODE_INIT, null, null);
                    sendtoPluginService(IPCMessageManager.ANDROID_MESSAGE, IPCMessageManager.IPC_FUNCTION_CODE_INIT, null, null);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void sendtoBLEService(int mbsService, int functionCode, CmdArg cmd, NameValuePair[] args) {
        if(cmd != null) {
            logi("ipcService: sendtoBLEService(), " + mbsService + "," + functionCode + "," + cmd.getValue() + "," +
                    cmd.getCMD() + "");
        } else {
            logi("ipcService: sendtoBLEService(), " + mbsService + "," + functionCode);
        }

        IPCMessageManager.sendIPCMessage(BLEService.class, mbsService, functionCode, cmd, args);
    }

    public void sendtoPluginService(int mbsService, int functionCode, CmdArg cmd, NameValuePair[] args) {
        if (isDebug) {
            if(cmd != null) {
                logi("ipcService: sendtoBLEService(), " + mbsService + "," + functionCode + "," + cmd.getValue() + "," +
                        cmd.getCMD() + "");
            } else {
                logi("ipcService: sendtoBLEService(), " + mbsService + "," + functionCode);
            }
        }

        IPCMessageManager.sendIPCMessage(PluginService.class, mbsService, functionCode, cmd, args);
    }

    private void handleIncomingMessage(Message msg) {
        if (isDebug) {
            logi("handleIncomingMessage() :: Start BLEService");
        }

        if (msg.what == IPCMessageManager.ANDROID_MESSAGE) {
            if (isDebug) {
                logi("handleIncomingMessage() :: IPCMessageManager.ANDROID_MESSAGE msg.arg1 = " + msg.arg1);
            }

            if (msg.arg1 == IPCMessageManager.IPC_NOTIFICATION_GATT_CONNECTED ||
                    msg.arg1 == IPCMessageManager.IPC_NOTIFICATION_GATT_DISCONNECTED) {

                ConnectedDevice cd = BluetoothUtils.getPairedMicrobit(this);
                cd.mStatus = (msg.arg1 == IPCMessageManager.IPC_NOTIFICATION_GATT_CONNECTED);
                BluetoothUtils.setPairedMicroBit(this, cd);
            }

            int errorCode = (int) msg.getData().getSerializable(IPCMessageManager.BUNDLE_ERROR_CODE);

            String error_message = (String) msg.getData().getSerializable(IPCMessageManager.BUNDLE_ERROR_MESSAGE);

            String firmware = (String) msg.getData().getSerializable(IPCMessageManager.BUNDLE_MICROBIT_FIRMWARE);

            int microbitRequest = -1;
            if (msg.getData().getSerializable(IPCMessageManager.BUNDLE_MICROBIT_REQUESTS) != null) {
                microbitRequest = (int) msg.getData().getSerializable(IPCMessageManager.BUNDLE_MICROBIT_REQUESTS);
            }

            Intent intent = new Intent(INTENT_BLE_NOTIFICATION);
            intent.putExtra(NOTIFICATION_CAUSE, msg.arg1);
            intent.putExtra(IPCMessageManager.BUNDLE_ERROR_CODE, errorCode);
            intent.putExtra(IPCMessageManager.BUNDLE_ERROR_MESSAGE, error_message);
            intent.putExtra(IPCMessageManager.BUNDLE_MICROBIT_FIRMWARE, firmware);
            intent.putExtra(IPCMessageManager.BUNDLE_MICROBIT_REQUESTS, microbitRequest);

            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } else if (msg.what == IPCMessageManager.MICROBIT_MESSAGE) {
            if (isDebug) {
                logi("handleIncomingMessage() :: IPCMessageManager.MICROBIT_MESSAGE msg.arg1 = " + msg.arg1);
            }

            Intent intent = new Intent(INTENT_MICROBIT_NOTIFICATION);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }
}
