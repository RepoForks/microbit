package com.samsung.microbit.core.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import no.nordicsemi.android.error.GattError;

import static com.samsung.microbit.BuildConfig.DEBUG;

public class BLEManager {
    public static final String TAG = BLEManager.class.getSimpleName();

    public static final int BLE_DISCONNECTED = 0x0000;
    public static final int BLE_CONNECTED = 0x0001;
    public static final int BLE_SERVICES_DISCOVERED = 0x0002;

    public static final int BLE_ERROR_OK = 0x00000000;
    public static final int BLE_ERROR_FAIL = 0x00010000;
    public static final int BLE_ERROR_TIMEOUT = 0x00020000;

    public static final int BLE_ERROR_NOOP = 0xFFFF0000;
    public static final int BLE_ERROR_NOGATT = -2 & 0xFFFF0000;

    public static final long BLE_WAIT_TIMEOUT = 10000;

    public static final int OP_NOOP = 0;
    public static final int OP_CONNECT = 1;
    public static final int OP_DISCOVER_SERVICES = 2;
    public static final int OP_READ_CHARACTERISTIC = 3;
    public static final int OP_WRITE_CHARACTERISTIC = 4;
    public static final int OP_READ_DESCRIPTOR = 5;
    public static final int OP_WRITE_DESCRIPTOR = 6;
    public static final int OP_CHARACTERISTIC_CHANGED = 7;
    public static final int OP_RELIABLE_WRITE_COMPLETED = 8;
    public static final int OP_READ_REMOTE_RSSI = 9;
    public static final int OP_MTU_CHANGED = 10;

    private volatile int bleState = 0;
    private volatile int error = 0;

    private volatile int inBleOp = 0;
    private volatile boolean callbackCompleted = false;

    private volatile int rssi;
    private volatile BluetoothGattCharacteristic lastCharacteristic;
    private volatile BluetoothGattDescriptor lastDescriptor;

    private Context context;
    private BluetoothGatt gatt;
    private BluetoothDevice bluetoothDevice;

    private final Object locker = new Object();
    private CharacteristicChangeListener characteristicChangeListener;
    private UnexpectedConnectionEventListener unexpectedDisconnectionListener;

    private int extendedError = 0;

    void logi(String message) {
        Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
    }

    public BLEManager(Context context, BluetoothDevice bluetoothDevice, UnexpectedConnectionEventListener
            unexpectedDisconnectionListener) {
        if (DEBUG) {
            logi("start");
        }

        this.context = context;
        this.bluetoothDevice = bluetoothDevice;
        this.unexpectedDisconnectionListener = unexpectedDisconnectionListener;
    }

    public BLEManager(Context context, BluetoothDevice bluetoothDevice, CharacteristicChangeListener
            characteristicChangeListener, UnexpectedConnectionEventListener unexpectedDisconnectionListener) {
        if (DEBUG) {
            logi("start1");
        }

        this.context = context;
        this.bluetoothDevice = bluetoothDevice;
        this.characteristicChangeListener = characteristicChangeListener;
        this.unexpectedDisconnectionListener = unexpectedDisconnectionListener;
    }

    public void setBluetoothDevice(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
    }

    public void setCharacteristicChangeListener(CharacteristicChangeListener characteristicChangeListener) {
        this.characteristicChangeListener = characteristicChangeListener;
    }

    public int getError() {
        return error;
    }

    public int getBleState() {
        return bleState;
    }

    public int getInBleOp() {
        return inBleOp;
    }

    public BluetoothGattService getService(UUID uuid) {
        if ((bleState & BLE_SERVICES_DISCOVERED) != 0) {
            return gatt.getService(uuid);
        }

        return null;
    }

    public List<BluetoothGattService> getServices() {
        if ((bleState & BLE_SERVICES_DISCOVERED) != 0) {
            return gatt.getServices();
        }

        return null;
    }

    public boolean reset() {
        if (DEBUG) {
            logi("reset()");
        }

        synchronized (locker) {
            if (bleState != 0) {
                disconnect();
            }

            if (bleState != 0) {
                return false;
            }

            lastCharacteristic = null;
            lastDescriptor = null;
            rssi = 0;
            error = 0;
            inBleOp = OP_NOOP;
            callbackCompleted = false;
            if (gatt != null) {
                if (DEBUG) {
                    logi("reset() :: gatt != null : closing gatt");
                }

                gatt.close();
            }

            gatt = null;
            return true;
        }
    }

    public int getExtendedError() {
        return extendedError;
    }

    public int connect(boolean autoReconnect) {
        int rc = BLE_ERROR_NOOP;

        if (gatt == null) {
            if (DEBUG) {
                logi("connectMaybeInit() :: gatt == null");
            }

            synchronized (locker) {
                if (inBleOp == OP_NOOP) {
                    inBleOp = OP_CONNECT;
                    try {
                        if (DEBUG) {
                            logi("connectMaybeInit() :: bluetoothDevice.connectGatt(context, autoReconnect, bluetoothGattCallback)");
                        }

                        gatt = bluetoothDevice.connectGatt(context, false, bluetoothGattCallback);

                        if (gatt == null) {
                            if (DEBUG)
                                logi("connectGatt failed with AutoReconnect = false. Trying again.. autoReconnect=" + autoReconnect);
                            gatt = bluetoothDevice.connectGatt(context, autoReconnect, bluetoothGattCallback);
                        }

                        if (gatt != null) {
                            error = 0;
                            locker.wait(BLE_WAIT_TIMEOUT);

                            if (DEBUG) {
                                logi("connectMaybeInit() :: remote device = " + gatt.getDevice().getAddress());
                            }

                            if (!callbackCompleted) {
                                error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                            }

                            rc = error | bleState;
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.toString());
                    }

                    inBleOp = OP_NOOP;
                }
            }
        } else {
            rc = gattConnect();
        }

        if (DEBUG) {
            logi("connectMaybeInit() :: rc = " + rc);
        }
        return rc;
    }

    private int gattConnect() {
        if (DEBUG) {
            logi("gattConnect() :: start");
        }

        int rc = BLE_ERROR_NOOP;

        synchronized (locker) {
            if (gatt != null && inBleOp == OP_NOOP) {
                if (DEBUG) {
                    logi("gattConnect() :: gatt != null");
                }

                inBleOp = OP_CONNECT;
                error = 0;
                try {
                    if (bleState == 0) {
                        if (DEBUG) {
                            logi("gattConnect() :: gatt.connectMaybeInit()");
                        }

                        callbackCompleted = false;

                        boolean result = gatt.connect();
                        logi("gatt.connectMaybeInit() returns = " + result);
                        locker.wait(BLE_WAIT_TIMEOUT);

                        if (DEBUG) {
                            logi("gattConnect() :: remote device = " + gatt.getDevice().getAddress());
                        }

                        if (!callbackCompleted) {
                            logi("BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT");
                            error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                        }
                        rc = error | bleState;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                inBleOp = OP_NOOP;
            }
        }

        if (DEBUG) {
            logi("gattConnect() :: rc = " + rc);
        }

        return rc;
    }

    public int disconnect() {
        if (DEBUG) {
            logi("disconnect() :: start");
        }

        int rc = BLE_ERROR_NOOP;

        synchronized (locker) {
            if (gatt != null && inBleOp == OP_NOOP) {

                inBleOp = OP_CONNECT;
                try {
                    error = 0;
                    if (bleState != 0) {
                        callbackCompleted = false;
                        gatt.disconnect();
                        locker.wait(BLE_WAIT_TIMEOUT);
                        if (!callbackCompleted) {
                            error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                        }
                    }

                    rc = error | bleState;
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                inBleOp = OP_NOOP;
            }
        }

        if (DEBUG) {
            logi("disconnect() :: rc = " + rc);
        }

        return rc;
    }

    public int waitDisconnect() {
        if (DEBUG) {
            logi("waitDisconnect() :: start");
        }

        int rc = BLE_ERROR_NOOP;

        synchronized (locker) {
            if (gatt != null && inBleOp == OP_NOOP) {

                inBleOp = OP_CONNECT;
                this.error = 0;
                int bleState = this.bleState;
                try {
                    if (bleState != 0) {
                        callbackCompleted = false;
                        locker.wait(BLE_WAIT_TIMEOUT);
                        if (!callbackCompleted) {
                            error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                        } else {
                            error = this.error;
                            bleState = this.bleState;
                        }
                    }

                    rc = error | bleState;
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                inBleOp = OP_NOOP;
            }
        }

        if (DEBUG) {
            logi("waitDisconnect() :: rc = " + rc);
        }

        return rc;
    }

    public int discoverServices() {
        if (DEBUG) {
            logi("discoverServices() :: start");
        }

        int rc = BLE_ERROR_NOOP;
        synchronized (locker) {
            if (gatt != null && inBleOp == OP_NOOP) {

                inBleOp = OP_DISCOVER_SERVICES;
                error = 0;
                try {
                    callbackCompleted = false;
                    if (gatt.discoverServices()) {
                        locker.wait(BLE_WAIT_TIMEOUT);
                        if (!callbackCompleted) {
                            error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                        }

                        rc = error | bleState;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                inBleOp = OP_NOOP;
            }
        }

        if (DEBUG) {
            logi("discoverServices() :: end : rc = " + rc);
        }

        return rc;
    }

    public boolean isConnected() {
        return bleState == BLE_CONNECTED || bleState == BLE_SERVICES_DISCOVERED || bleState == (BLE_CONNECTED |
                BLE_SERVICES_DISCOVERED);
    }

    public int writeDescriptor(BluetoothGattDescriptor descriptor) {
        if (DEBUG) {
            logi("writeDescriptor() :: start");
        }

        int rc = BLE_ERROR_NOOP;

        synchronized (locker) {
            if (gatt != null && inBleOp == OP_NOOP) {

                inBleOp = OP_WRITE_DESCRIPTOR;
                lastDescriptor = null;
                error = 0;
                try {
                    if (gatt.writeDescriptor(descriptor)) {
                        callbackCompleted = false;
                        locker.wait(BLE_WAIT_TIMEOUT);
                        if (!callbackCompleted) {
                            error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                        }

                        rc = error | bleState;
                    }

                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                inBleOp = OP_NOOP;
            }
        }

        if (DEBUG) {
            logi("writeDescriptor() :: end : rc = " + rc);
        }

        return rc;
    }

    public int readDescriptor(BluetoothGattDescriptor descriptor) {
        if (DEBUG) {
            logi("readDescriptor() :: start");
        }

        int rc = BLE_ERROR_NOOP;

        synchronized (locker) {
            if (gatt != null && inBleOp > OP_NOOP) {

                inBleOp = OP_READ_DESCRIPTOR;
                lastDescriptor = null;
                error = 0;
                try {
                    callbackCompleted = false;
                    if (gatt.readDescriptor(descriptor)) {
                        locker.wait(BLE_WAIT_TIMEOUT);
                        if (!callbackCompleted) {
                            error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                        }

                        rc = error | bleState;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                inBleOp = OP_NOOP;
            }
        }

        if (DEBUG) {
            logi("readDescriptor() :: end : rc = " + rc);
        }

        return rc;
    }

    public int writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (DEBUG) {
            logi("writeCharacteristic() :: start");
        }

        int rc = BLE_ERROR_NOOP;

        synchronized (locker) {
            if (gatt != null && inBleOp == OP_NOOP) {
                inBleOp = OP_WRITE_CHARACTERISTIC;
                lastCharacteristic = null;
                error = 0;
                try {
                    callbackCompleted = false;
                    if (gatt.writeCharacteristic(characteristic)) {
                        locker.wait(BLE_WAIT_TIMEOUT);
                        if (!callbackCompleted) {
                            error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                        }

                        rc = error | bleState;
                    } else {
                        if (DEBUG) {
                            logi("writeCharacteristic() :: failed");
                        }
                    }


                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                inBleOp = OP_NOOP;
            } else {
                logi("Couldn't write to characteristic");
            }

        }

        if (DEBUG) {
            logi("writeCharacteristic() :: end : rc = " + rc);
        }

        return rc;
    }

    public int readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (DEBUG) {
            logi("readCharacteristic() :: start");
        }

        int rc = BLE_ERROR_NOOP;

        synchronized (locker) {
            if (gatt != null && inBleOp == OP_NOOP) {

                inBleOp = OP_READ_CHARACTERISTIC;
                lastCharacteristic = null;
                error = 0;
                int bleState = this.bleState;
                try {
                    callbackCompleted = false;
                    if (gatt.readCharacteristic(characteristic)) {
                        locker.wait(BLE_WAIT_TIMEOUT);
                        if (!callbackCompleted) {
                            error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                        } else {
                            bleState = this.bleState;
                        }

                        rc = error | bleState;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, e.toString());
                }

                inBleOp = OP_NOOP;
            }
        }

        if (DEBUG) {
            logi("readCharacteristic() :: end : rc = " + rc);
        }

        return rc;
    }

    public BluetoothGattCharacteristic getLastCharacteristic() {
        return lastCharacteristic;
    }

    public BluetoothGattDescriptor getLastDescriptor() {
        return lastDescriptor;
    }

    public int enableCharacteristicNotification(BluetoothGattCharacteristic characteristic, BluetoothGattDescriptor
             descriptor, boolean enable) {

        int rc = BLE_ERROR_NOOP;

        synchronized (locker) {
            try {
                error = 0;
                int bleState = this.bleState;
                callbackCompleted = false;

                if (gatt.setCharacteristicNotification(characteristic, enable)) {
                    //TODO why thread not waiting
                    //locker.wait(BLE_WAIT_TIMEOUT);

                    if(false) {
                        throw new InterruptedException();
                    } else {
                        callbackCompleted = true;
                    }

                    if (!callbackCompleted) {
                        error = (BLE_ERROR_FAIL | BLE_ERROR_TIMEOUT);
                    } else {
                        bleState = this.bleState;
                    }

                    rc = error | bleState;

                    descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor
                            .DISABLE_NOTIFICATION_VALUE);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
        }

        return writeDescriptor(descriptor) | rc;
    }

    final protected BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (DEBUG) {
                logi("BluetoothGattCallback.onConnectionStateChange() :: start : status = " + status + " newState = " +
                        "" + newState);
            }

            int state = BLE_DISCONNECTED;
            int error = 0;

            boolean gattForceClosed = false;

            switch (status) {
                case BluetoothGatt.GATT_SUCCESS: {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        state = BLE_CONNECTED;
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        state = BLE_DISCONNECTED;
                        if (gatt != null) {
                            if (DEBUG) {
                                logi("onConnectionStateChange() :: gatt != null : closing gatt");
                            }

                            gatt.disconnect();
                            gatt.close();
                            gattForceClosed = true;
                        }
                    }
                }
                break;
                default:
                    Log.e(TAG, "Connection error: " + GattError.parseConnectionError(status));
                break;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                state = BLE_DISCONNECTED;
                error = BLE_ERROR_FAIL;
            }

            synchronized (locker) {
                if (inBleOp == OP_CONNECT) {
                    if (DEBUG) {
                        logi("BluetoothGattCallback.onConnectionStateChange() :: inBleOp == OP_CONNECT");
                    }

                    if (state != (bleState & BLE_CONNECTED)) {
                        bleState = state;
                    }
                    callbackCompleted = true;
                    BLEManager.this.error = error;
                    extendedError = status;
                    locker.notify();
                } else {
                    if (DEBUG) {
                        logi("onConnectionStateChange() :: inBleOp != OP_CONNECT");
                    }

                    bleState = state;
                    unexpectedDisconnectionListener.handleConnectionEvent(bleState, gattForceClosed);
                }

                if (DEBUG) {
                    logi("BluetoothGattCallback.onConnectionStateChange() :: end");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            int state = BLE_SERVICES_DISCOVERED;

            synchronized (locker) {
                if (DEBUG) {
                    logi("BluetoothGattCallback.onServicesDiscovered() :: start : status = " + status);
                }

                if (inBleOp == OP_DISCOVER_SERVICES) {
                    if (DEBUG) {
                        logi("BluetoothGattCallback.onServicesDiscovered() :: inBleOp == OP_DISCOVER_SERVICES");
                    }

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        bleState |= state;

                    } else {
                        bleState &= (~state);
                    }

                    callbackCompleted = true;
                    locker.notify();
                }

                if (DEBUG) {
                    logi("BluetoothGattCallback.onServicesDiscovered() :: end");
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            synchronized (locker) {
                if (DEBUG) {
                    logi("BluetoothGattCallback.onCharacteristicRead() :: start : status = " + status);
                }

                if (inBleOp == OP_READ_CHARACTERISTIC) {
                    if (DEBUG) {
                        logi("BluetoothGattCallback.onCharacteristicRead() :: inBleOp == OP_READ_CHARACTERISTIC");
                    }

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        error = BLE_ERROR_OK;
                    } else {
                        error = BLE_ERROR_FAIL;
                    }

                    lastCharacteristic = characteristic;
                    callbackCompleted = true;
                    locker.notify();
                }

                if (DEBUG) {
                    logi("BluetoothGattCallback.onCharacteristicRead() :: end");
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            synchronized (locker) {
                if (DEBUG) {
                    logi("BluetoothGattCallback.onCharacteristicWrite() :: start : status = " + status);
                }

                if (inBleOp == OP_WRITE_CHARACTERISTIC) {
                    if (DEBUG) {
                        logi("BluetoothGattCallback.onCharacteristicWrite() :: inBleOp == OP_WRITE_CHARACTERISTIC");
                    }

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        error = BLE_ERROR_OK;
                    } else {
                        error = BLE_ERROR_FAIL;
                    }

                    lastCharacteristic = characteristic;
                    callbackCompleted = true;
                    locker.notify();
                }

                if (DEBUG) {
                    logi("BluetoothGattCallback.onCharacteristicWrite() :: end");
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (DEBUG) {
                logi("BluetoothGattCallback.onCharacteristicChanged() :: start");
            }

            super.onCharacteristicChanged(gatt, characteristic);
            characteristicChangeListener.onCharacteristicChanged(gatt, characteristic);

            if (DEBUG) {
                logi("BluetoothGattCallback.onCharacteristicChanged() :: end");
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);

            synchronized (locker) {
                if (DEBUG) {
                    logi("BluetoothGattCallback.onDescriptorRead() :: start : status = " + status);
                }

                if (inBleOp == OP_READ_DESCRIPTOR) {
                    if (DEBUG) {
                        logi("BluetoothGattCallback.onDescriptorRead() :: inBleOp == OP_READ_DESCRIPTOR");
                    }
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        error = BLE_ERROR_OK;
                    } else {
                        error = BLE_ERROR_FAIL;
                    }

                    lastDescriptor = descriptor;
                    callbackCompleted = true;
                    locker.notify();
                }

                if (DEBUG) {
                    logi("BluetoothGattCallback.onDescriptorRead() :: end");
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            synchronized (locker) {
                if (DEBUG) {
                    logi("BluetoothGattCallback.onDescriptorWrite() :: start : status = " + status);
                }

                if (inBleOp == OP_WRITE_DESCRIPTOR) {
                    if (DEBUG) {
                        logi("BluetoothGattCallback.onDescriptorWrite() :: inBleOp == OP_WRITE_DESCRIPTOR");
                    }

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        error = BLE_ERROR_OK;
                    } else {
                        error = BLE_ERROR_FAIL;
                    }

                    lastDescriptor = descriptor;
                    callbackCompleted = true;
                    locker.notify();
                }

                if (DEBUG) {
                    logi("BluetoothGattCallback.onDescriptorWrite() :: end");
                }
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);

            synchronized (locker) {
                if (DEBUG) {
                    logi("BluetoothGattCallback.onReliableWriteCompleted() :: start");
                }

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    error = BLE_ERROR_OK;
                } else {
                    error = BLE_ERROR_FAIL;
                }

                callbackCompleted = true;
                locker.notify();

                if (DEBUG) {
                    logi("BluetoothGattCallback.onReliableWriteCompleted() :: end");
                }
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);

            synchronized (locker) {
                if (DEBUG) {
                    logi("BluetoothGattCallback.onReadRemoteRssi() :: start");
                }

                if (inBleOp == OP_READ_REMOTE_RSSI) {
                    if (DEBUG) {
                        logi("BluetoothGattCallback.onReadRemoteRssi() :: inBleOp == OP_READ_REMOTE_RSSI");
                    }

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        error = BLE_ERROR_OK;
                    } else {
                        error = BLE_ERROR_FAIL;
                    }

                    BLEManager.this.rssi = rssi;
                    callbackCompleted = true;
                    locker.notify();
                }

                if (DEBUG) {
                    logi("BluetoothGattCallback.onMtuChanged() :: end");
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);

            synchronized (locker) {
                if (DEBUG) {
                    logi("BluetoothGattCallback.onMtuChanged() :: start");
                }

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    error = BLE_ERROR_OK;
                } else {
                    error = BLE_ERROR_FAIL;
                }

                if (DEBUG) {
                    logi("BluetoothGattCallback.onMtuChanged() :: end");
                }
            }
        }
    };
}

