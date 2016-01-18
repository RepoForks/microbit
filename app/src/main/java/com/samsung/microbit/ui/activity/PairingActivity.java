package com.samsung.microbit.ui.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.R;
import com.samsung.microbit.core.IPCMessageManager;
import com.samsung.microbit.core.Utils;
import com.samsung.microbit.model.ConnectedDevice;
import com.samsung.microbit.model.Constants;
import com.samsung.microbit.service.IPCService;
import com.samsung.microbit.ui.BluetoothSwitch;
import com.samsung.microbit.ui.PopUp;
import com.samsung.microbit.ui.adapter.LEDAdapter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class PairingActivity extends Activity implements View.OnClickListener {

    private static boolean DISABLE_DEVICE_LIST = false;

    private enum PAIRING_STATE {
        PAIRING_STATE_CONNECT_BUTTON,
        PAIRING_STATE_TIP,
        PAIRING_STATE_PATTERN_EMPTY,
        PAIRING_STATE_SEARCHING,
        PAIRING_STATE_ERROR,
    }

    private static PAIRING_STATE mState = PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON;
    private static String mNewDeviceName;
    private static String mNewDeviceCode;
    private static String mNewDeviceAddress;

    // @formatter:off
    private static String deviceCodeArray[] = {
            "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0",
            "0", "0", "0", "0", "0"};

    private String deviceNameMapArray[] = {
            "T", "A", "T", "A", "T",
            "P", "E", "P", "E", "P",
            "G", "I", "G", "I", "G",
            "V", "O", "V", "O", "V",
            "Z", "U", "Z", "U", "Z"};
    // @formatter:on

    LinearLayout mPairButtonView;
    LinearLayout mPairTipView;
    LinearLayout mConnectDeviceView; // new layout
    LinearLayout mNewDeviceView;
    LinearLayout mPairSearchView;
    LinearLayout mBottomPairButton;
    LinearLayout mEnterPinView; // pin view
    private LinearLayout itemSelectorLayout;
    private TextView mConnectedDeviceName;
    private TextView mdeviceConnectionStatus;
    private ImageButton mconnectBtn;
    private ImageButton mdeleteBtn;
    private Handler mHandler;

    // Animation webviews
    WebView searchingMicrobitAnimation;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 15000;

    private static PairingActivity instance;
    private static BluetoothAdapter mBluetoothAdapter = null;
    private static volatile boolean mScanning = false;
    //private Runnable scanFailedCallback;
    private static BluetoothLeScanner mLEScanner = null;


    private enum ACTIVITY_STATE {
        STATE_IDLE,
        STATE_ENABLE_BT_FOR_CONNECT,
        STATE_ENABLE_BT_FOR_PAIRING,
    }


    private static ACTIVITY_STATE mActivityState = ACTIVITY_STATE.STATE_IDLE;
    private int selectedDeviceForConnect = 0;


    private View.OnClickListener mSuccessFulPairingHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("======mSuccessFulPairingHandler======");
            PopUp.hide();
            displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
        }
    };

    private View.OnClickListener mFailedPairingHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("======mFailedPairingHandler======");
            PopUp.hide();
            displayScreen(PAIRING_STATE.PAIRING_STATE_PATTERN_EMPTY);
        }
    };
    private View.OnClickListener mRetryPairing = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("======mRetryPairing======");
            PopUp.hide();
            scanLeDevice(true);
            displayScreen(PAIRING_STATE.PAIRING_STATE_SEARCHING);
        }
    };
    private final BroadcastReceiver mPairReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                logi(" mPairReceiver - state = " + state + " prevState = " + prevState);
                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    ConnectedDevice newDev = new ConnectedDevice(mNewDeviceCode.toUpperCase(), mNewDeviceCode.toUpperCase(), false, mNewDeviceAddress, 0 /*No pair code*/);
                    handlePairingSuccessful(newDev);
                    return;
                } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDING) {
                    scanLeDevice(false);
                    PopUp.show(MBApp.getContext(),
                            getString(R.string.pairing_failed_message), //message
                            getString(R.string.pairing_failed_title), //title
                            R.drawable.error_face, //image icon res id
                            R.drawable.red_btn,
                            PopUp.TYPE_CHOICE, //type of popup.
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    PopUp.hide();
                                    displayScreen(PAIRING_STATE.PAIRING_STATE_TIP);
                                }
                            },//override click listener for ok button
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    PopUp.hide();
                                    displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                                }
                            });
                }

            }
        }
    };
    private BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            int v = intent.getIntExtra(IPCMessageManager.BUNDLE_ERROR_CODE, 0);

            if (Constants.BLE_DISCONNECTED_FOR_FLASH == v) {
                logi("Bluetooth disconnected for flashing. No need to display pop-up");
                handleBLENotification(context, intent, false);
                return;
            }
            handleBLENotification(context, intent, true);
            if (v != 0) {
                logi("localBroadcastReceiver Error code =" + v);
                String message = intent.getStringExtra(IPCMessageManager.BUNDLE_ERROR_MESSAGE);
                logi("localBroadcastReceiver Error message = " + message);
                if (message == null)
                    message = "Error";
                final String displayTitle = message;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PopUp.show(MBApp.getContext(),
                                MBApp.getContext().getString(R.string.micro_bit_reset_msg),
                                displayTitle,
                                R.drawable.error_face, R.drawable.red_btn,
                                PopUp.TYPE_ALERT, null, null);
                    }
                });
            }
        }
    };

    private void handleBLENotification(Context context, Intent intent, boolean popupHide) {

        logi("handleBLENotification() ");
        updatePairedDeviceCard();
        if (popupHide)
            PopUp.hide();
    }

    // *************************************************

    // DEBUG
    protected boolean debug = true;
    protected String TAG = "PairingActivity";

    protected void logi(String message) {
        if (debug)
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
    }

    @Override
    public void onResume() {
        super.onResume();
        MBApp.setContext(this);
        updatePairedDeviceCard();
        // Searching for micro:bit
        searchingMicrobitAnimation.onResume();
    }

    @Override
    public void onPause() {
        logi("onPause() ::");
        super.onPause();
        // Stop searching for micro:bit animation
        searchingMicrobitAnimation.onPause();
    }

    public PairingActivity() {
        logi("PairingActivity() ::");
        instance = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        logi("onCreate() ::");

        super.onCreate(savedInstanceState);

        MBApp.setContext(this);

        // Make sure to call this before any other userActionEvent is sent
        if (MBApp.getApp().getEcho() != null) {
            logi("Page View test for PairingActivity");
            MBApp.getApp().getEcho().viewEvent("com.samsung.microbit.ui.activity.pairingactivity.page", null);
        }

        IntentFilter broadcastIntentFilter = new IntentFilter(IPCService.INTENT_BLE_NOTIFICATION);
        LocalBroadcastManager.getInstance(MBApp.getContext()).registerReceiver(localBroadcastReceiver, broadcastIntentFilter);

        //Register receiver
        IntentFilter intent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mPairReceiver, intent);

        setupBleController();

        // ************************************************
        //Remove title barproject_list
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_connect);

        mHandler = new Handler(Looper.getMainLooper());

        //Layout status indicator
        itemSelectorLayout = (LinearLayout) findViewById(R.id.connected_device_item);

        // Device connection status
        mdeviceConnectionStatus = (TextView) findViewById(R.id.device_status_txt);
        mdeviceConnectionStatus.setTypeface(MBApp.getApp().getTypeface());

        // Name fo the device currently paired or connected
        mConnectedDeviceName = (TextView) findViewById(R.id.pairedDeviceName);
        mConnectedDeviceName.setTypeface(MBApp.getApp().getTypeface());
        mConnectedDeviceName.setFocusable(false);
        mConnectedDeviceName.setFocusableInTouchMode(false);
        mConnectedDeviceName.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        // Connect the device
        mconnectBtn = (ImageButton) findViewById(R.id.connectBtn);
        mconnectBtn.setFocusable(false);
        mconnectBtn.setFocusableInTouchMode(false);
        mconnectBtn.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        // Delete the device
        mdeleteBtn = (ImageButton) findViewById(R.id.deleteBtn);
        mdeleteBtn.setFocusable(false);
        mdeleteBtn.setFocusableInTouchMode(false);
        mdeleteBtn.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        mconnectBtn.setOnClickListener(this);
        mdeleteBtn.setOnClickListener(this);

        updatePairedDeviceCard();

        mBottomPairButton = (LinearLayout) findViewById(R.id.ll_pairing_activity_screen);
        mPairButtonView = (LinearLayout) findViewById(R.id.pairButtonView);
        mPairTipView = (LinearLayout) findViewById(R.id.pairTipView);
        mConnectDeviceView = (LinearLayout) findViewById(R.id.connectDeviceView); // Connect device view
        mNewDeviceView = (LinearLayout) findViewById(R.id.newDeviceView);
        mPairSearchView = (LinearLayout) findViewById(R.id.pairSearchView);
        mEnterPinView = (LinearLayout) findViewById(R.id.enterPinView);

        /* Font type */
        // Connect Screen
        TextView appBarTitle = (TextView) findViewById(R.id.flash_projects_title_txt);
        appBarTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView manageMicrobit = (TextView) findViewById(R.id.title_manage_microbit);
        manageMicrobit.setTypeface(MBApp.getApp().getTypeface());

        TextView descriptionManageMicrobit = (TextView) findViewById(R.id.description_manage_microbit);
        descriptionManageMicrobit.setTypeface(MBApp.getApp().getTypeface());

        Button bluetoothSettings = (Button) findViewById(R.id.go_bluetooth_settings);
        bluetoothSettings.setTypeface(MBApp.getApp().getTypeface());

        Button pairButton = (Button) findViewById(R.id.pairButton);
        pairButton.setTypeface(MBApp.getApp().getTypeface());

        // Step 1 - How to pair your micro:bit
        TextView pairTipTitle = (TextView) findViewById(R.id.pairTipTitle);
        pairTipTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView stepOneTitle = (TextView) findViewById(R.id.pair_tip_step_1_step);
        stepOneTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView stepOneInstructions = (TextView) findViewById(R.id.pair_tip_step_1_instructions);
        stepOneInstructions.setTypeface(MBApp.getApp().getTypeface());

        Button cancelPairButton = (Button) findViewById(R.id.cancel_tip_step_1_btn);
        cancelPairButton.setTypeface(MBApp.getApp().getTypeface());

        Button nextPairButton = (Button) findViewById(R.id.ok_tip_step_1_btn);
        nextPairButton.setTypeface(MBApp.getApp().getTypeface());

        // Step 2 - Enter Pattern
        TextView enterPatternTitle = (TextView) findViewById(R.id.enter_pattern_step_2_title);
        enterPatternTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView stepTwoTitle = (TextView) findViewById(R.id.pair_enter_pattern_step_2);
        stepTwoTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView stepTwoInstructions = (TextView) findViewById(R.id.pair_enter_pattern_step_2_instructions);
        stepTwoInstructions.setTypeface(MBApp.getApp().getTypeface());

        Button cancelEnterPattern = (Button) findViewById(R.id.cancel_enter_pattern_step_2_btn);
        cancelEnterPattern.setTypeface(MBApp.getApp().getTypeface());

        Button okEnterPatternButton = (Button) findViewById(R.id.ok_enter_pattern_step_2_btn);
        okEnterPatternButton.setTypeface(MBApp.getApp().getTypeface());

        // Step 3 - Searching for micro:bit
        TextView searchMicrobitTitle = (TextView) findViewById(R.id.search_microbit_step_3_title);
        searchMicrobitTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView stepThreeTitle = (TextView) findViewById(R.id.searching_microbit_step_3_step);
        stepThreeTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView stepThreeInstructions = (TextView) findViewById(R.id.searching_microbit_step_3_instructions);
        stepThreeInstructions.setTypeface(MBApp.getApp().getTypeface());

        Button cancelSearchMicroBit = (Button) findViewById(R.id.cancel_search_microbit_step_3_btn);
        cancelSearchMicroBit.setTypeface(MBApp.getApp().getTypeface());

        // Step 4 - Enter Pin
        TextView enterPinTitle = (TextView) findViewById(R.id.enter_pin_step_4_title);
        enterPinTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView stepFourTitle = (TextView) findViewById(R.id.enter_pin_step_4_step);
        stepFourTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView stepFourInstructions = (TextView) findViewById(R.id.enter_pin_step_4_instructions);
        stepFourInstructions.setTypeface(MBApp.getApp().getTypeface());

        Button cancelEnterPin = (Button) findViewById(R.id.cancel_enter_pin_step_4_btn);
        cancelEnterPin.setTypeface(MBApp.getApp().getTypeface());

        // pin view
        displayScreen(mState);
        findViewById(R.id.pairButton).setOnClickListener(this);
        findViewById(R.id.cancel_tip_step_1_btn).setOnClickListener(this);
        findViewById(R.id.ok_enter_pattern_step_2_btn).setOnClickListener(this);
        findViewById(R.id.cancel_enter_pattern_step_2_btn).setOnClickListener(this);
        findViewById(R.id.cancel_search_microbit_step_3_btn).setOnClickListener(this);
        findViewById(R.id.go_bluetooth_settings).setOnClickListener(this);
        findViewById(R.id.cancel_enter_pin_step_4_btn).setOnClickListener(this);

        // Step 1: How to Pair (animation)
//        WebView howToPairMicrobit = (WebView) findViewById(R.id.how_to_pair_microbit_webview);
//        howToPairMicrobit.setBackgroundColor(Color.TRANSPARENT);
//        howToPairMicrobit.loadUrl("file:///android_asset/htmls/how_to_pair_microbit.html");

        // Step 3: Searching for Microbit (animation)
        searchingMicrobitAnimation = (WebView) findViewById(R.id.searching_microbit_webview);
        searchingMicrobitAnimation.setBackgroundColor(Color.TRANSPARENT);
        searchingMicrobitAnimation.loadUrl("file:///android_asset/htmls/loading_search_microbit.html");
    }

    boolean setupBleController() {
        boolean retvalue = true;


        if (mBluetoothAdapter == null) {
            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (mBluetoothAdapter == null) {
            retvalue = false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mLEScanner == null) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if (mLEScanner == null)
                retvalue = false;
        }
        return retvalue;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        logi("onActivityResult");
        if (requestCode == Constants.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                if (mActivityState == ACTIVITY_STATE.STATE_ENABLE_BT_FOR_PAIRING) {
                    startWithPairing();
                } else if (mActivityState == ACTIVITY_STATE.STATE_ENABLE_BT_FOR_CONNECT) {
                    toggleConnection();
                }
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                PopUp.show(MBApp.getContext(),
                        getString(R.string.bluetooth_off_cannot_continue), //message
                        "",
                        R.drawable.error_face, R.drawable.red_btn,
                        PopUp.TYPE_ALERT,
                        null, null);
            }
            //Change state back to Idle
            mActivityState = ACTIVITY_STATE.STATE_IDLE;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void displayLedGrid() {
        GridView gridview = (GridView) findViewById(R.id.enter_pattern_step_2_gridview);
        gridview.setAdapter(new LEDAdapter(this, deviceCodeArray));
        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                if ((findViewById(R.id.ok_enter_pattern_step_2_btn).getVisibility() != View.VISIBLE)) {
                    findViewById(R.id.ok_enter_pattern_step_2_btn).setVisibility(View.VISIBLE);
                }

                boolean isOn = toggleLED((ImageView) v, position);
                setCol(parent, position, isOn);
                //Toast.makeText(MBApp.getContext(), "LED Clicked: " + position, Toast.LENGTH_SHORT).show();

                if (!Arrays.asList(deviceCodeArray).contains("1")) {
                    findViewById(R.id.ok_enter_pattern_step_2_btn).setVisibility(View.INVISIBLE);
                }
            }
        });

        if (!Arrays.asList(deviceCodeArray).contains("1")) {
            findViewById(R.id.ok_enter_pattern_step_2_btn).setVisibility(View.INVISIBLE);
        } else
            findViewById(R.id.ok_enter_pattern_step_2_btn).setVisibility(View.VISIBLE);
    }

    private void generateName() {

        mNewDeviceName = "";
        mNewDeviceCode = "";
        //Columns
        for (int col = 0; col < 5; col++) {
            //Rows
            for (int row = 0; row < 5; row++) {
                if (deviceCodeArray[(col + (5 * row))] == "1") {
                    mNewDeviceName += deviceNameMapArray[(col + (5 * row))];
                    break;
                }
            }
        }
        mNewDeviceCode = mNewDeviceName;
        mNewDeviceName = "BBC microbit [" + mNewDeviceName + "]";
        //Toast.makeText(this, "Pattern :"+mNewDeviceCode, Toast.LENGTH_SHORT).show();
    }

    private void setCol(AdapterView<?> parent, int pos, boolean enabledlandscape) {
        int index = pos - 5;
        ImageView v;

        while (index >= 0) {
            v = (ImageView) parent.getChildAt(index);
            v.setBackground(getApplication().getResources().getDrawable(R.drawable.white_red_led_btn));
            v.setTag("0");
            deviceCodeArray[index] = "0";
            index -= 5;
        }
        index = pos + 5;
        while (index < 25) {
            v = (ImageView) parent.getChildAt(index);
            v.setBackground(getApplication().getResources().getDrawable(R.drawable.red_white_led_btn));
            v.setTag("1");
            index += 5;
        }

    }

    private boolean toggleLED(ImageView image, int pos) {
        boolean isOn;
        //Toast.makeText(this, "Pos :" +  pos, Toast.LENGTH_SHORT).show();
        if (image.getTag() != "1") {
            deviceCodeArray[pos] = "1";
            image.setBackground(getApplication().getResources().getDrawable(R.drawable.red_white_led_btn));
            image.setTag("1");
            isOn = true;
        } else {
            deviceCodeArray[pos] = "0";
            image.setBackground(getApplication().getResources().getDrawable(R.drawable.white_red_led_btn));
            image.setTag("0");
            isOn = false;
            // Update the code to consider the still ON LED below the toggled one
            if (pos < 20)
                deviceCodeArray[pos + 5] = "1";
        }
        return isOn;
    }

    private void updateConnectionStatus() {
        ConnectedDevice connectedDevice = Utils.getPairedMicrobit(this);

        if (!connectedDevice.mStatus) {
            // Device is not connected
            mconnectBtn.setImageResource(R.drawable.device_status_disconnected);
            itemSelectorLayout.setBackgroundResource(R.drawable.grey_btn);
            itemSelectorLayout.setAlpha(1.0f);
            mConnectedDeviceName.setTextColor(Color.WHITE);
            mdeviceConnectionStatus.setText(R.string.most_recent_device_status);
            mdeviceConnectionStatus.setAlpha(1.0f);
        } else {
            // Device is connected
            mconnectBtn.setImageResource(R.drawable.device_connected);
            itemSelectorLayout.setBackgroundResource(R.drawable.white_btn);
            itemSelectorLayout.setAlpha(1.0f);
            mConnectedDeviceName.setTextColor(Color.BLACK);
            mdeviceConnectionStatus.setText(R.string.device_connected_device_status);
            mdeviceConnectionStatus.setAlpha(1.0f);
        }

    }

    private void updatePairedDeviceCard() {
        ConnectedDevice connectedDevice = Utils.getPairedMicrobit(this);
        if (connectedDevice.mName == null) {
            //No device is Paired
            mconnectBtn.setVisibility(View.INVISIBLE);
            mdeleteBtn.setVisibility(View.INVISIBLE);
            itemSelectorLayout.setBackgroundResource(R.drawable.grey_btn);
            itemSelectorLayout.setAlpha(0.25f);
            mConnectedDeviceName.setText("-");
            mConnectedDeviceName.setEnabled(false);
            mdeviceConnectionStatus.setAlpha(0.25f);
        } else {
            mConnectedDeviceName.setText(connectedDevice.mName);
            mconnectBtn.setVisibility(View.VISIBLE);
            mdeleteBtn.setVisibility(View.VISIBLE);
            updateConnectionStatus();
        }
    }

    public static boolean disableListView() {
        return DISABLE_DEVICE_LIST;
    }

    private boolean isPortraitMode() {
        return (mBottomPairButton != null);
    }

    private void displayScreen(PAIRING_STATE gotoState) {
        //Reset all screens first
        mPairTipView.setVisibility(View.GONE);
        mNewDeviceView.setVisibility(View.GONE);
        mPairSearchView.setVisibility(View.GONE);
        mEnterPinView.setVisibility(View.GONE);

        logi("********** Connect: state from " + mState + " to " + gotoState);
        mState = gotoState;

        if ((gotoState == PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON) ||
                (gotoState == PAIRING_STATE.PAIRING_STATE_ERROR))
            DISABLE_DEVICE_LIST = false;
        else
            DISABLE_DEVICE_LIST = true;

        if (isPortraitMode() && (disableListView())) {
            //
        } else {
            updatePairedDeviceCard();
            mConnectDeviceView.setVisibility(View.VISIBLE);
        }

        switch (gotoState) {
            case PAIRING_STATE_CONNECT_BUTTON:
                break;

            case PAIRING_STATE_ERROR:
                Arrays.fill(deviceCodeArray, "0");
                findViewById(R.id.enter_pattern_step_2_gridview).setEnabled(true);
                mNewDeviceName = "";
                mNewDeviceCode = "";
                break;

            case PAIRING_STATE_TIP:
                mPairTipView.setVisibility(View.VISIBLE);
                mConnectDeviceView.setVisibility(View.GONE);
                findViewById(R.id.ok_tip_step_1_btn).setOnClickListener(this);
                break;

            case PAIRING_STATE_PATTERN_EMPTY:
                findViewById(R.id.enter_pattern_step_2_gridview).setEnabled(true);
                mNewDeviceView.setVisibility(View.VISIBLE);
                findViewById(R.id.cancel_enter_pattern_step_2_btn).setVisibility(View.VISIBLE);
                findViewById(R.id.enter_pattern_step_2_title).setVisibility(View.VISIBLE);

                // test
                findViewById(R.id.ok_enter_pattern_step_2_btn).setVisibility(View.GONE);

                displayLedGrid();
                break;

            case PAIRING_STATE_SEARCHING:
                mPairSearchView.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void startBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
    }

    public void startWithPairing() {
        if (mBottomPairButton != null) {
            mConnectDeviceView.setVisibility(View.GONE);
        }

        if (mPairButtonView != null) {
            displayScreen(PAIRING_STATE.PAIRING_STATE_TIP);
        }
    }

    public void toggleConnection() {
        ConnectedDevice currentDevice = Utils.getPairedMicrobit(this);
        if (currentDevice.mAddress != null) {
            boolean currentState = currentDevice.mStatus;
            if (!currentState) {
                PopUp.show(MBApp.getContext(),
                        getString(R.string.init_connection),
                        "",
                        R.drawable.message_face, R.drawable.blue_btn,
                        PopUp.TYPE_SPINNER,
                        null, null);
                IPCService.getInstance().bleConnect();
            } else {
                IPCService.getInstance().bleDisconnect();
                currentDevice.mStatus = !currentState;
                Utils.setPairedMicrobit(this, currentDevice);
                updatePairedDeviceCard();
                updateConnectionStatus();
            }
        }
    }

    public void onClick(final View v) {
        int pos;

        switch (v.getId()) {
            case R.id.pairButton:
                if (debug) logi("onClick() :: pairButton");
                if (!BluetoothSwitch.getInstance().isBluetoothON()) {
                    mActivityState = ACTIVITY_STATE.STATE_ENABLE_BT_FOR_PAIRING;
                    startBluetooth();
                    return;
                }
                startWithPairing();
                break;
            case R.id.go_bluetooth_settings: //Bluetooth
                Intent goToBlueToothIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(goToBlueToothIntent);
                break;
            case R.id.ok_tip_step_1_btn:
                if (debug) logi("onClick() :: ok_pair_button");
                displayScreen(PAIRING_STATE.PAIRING_STATE_PATTERN_EMPTY);
                break;

            case R.id.ok_enter_pattern_step_2_btn:
                if (debug) logi("onClick() :: ok_name_button");
                if (mState == PAIRING_STATE.PAIRING_STATE_PATTERN_EMPTY) {
                    generateName();
                    if (!BluetoothSwitch.getInstance().checkBluetoothAndStart()) {
                        return;
                    }
                    scanLeDevice(true);
                    displayScreen(PAIRING_STATE.PAIRING_STATE_SEARCHING);
                    break;
                }
                break;

            case R.id.cancel_tip_step_1_btn:
                if (debug) logi("onClick() :: cancel_tip_button");
                displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                break;

            case R.id.cancel_enter_pattern_step_2_btn:
                if (debug) logi("onClick() :: cancel_name_button");
                cancelPairing();
                displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                break;

            case R.id.cancel_search_microbit_step_3_btn:
                if (debug) logi("onClick() :: cancel_search_button");
                scanLeDevice(false);
                cancelPairing();
                displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                break;

            case R.id.connectBtn:
                if (debug) logi("onClick() :: connectBtn");
                if (!BluetoothSwitch.getInstance().isBluetoothON()) {
                    mActivityState = ACTIVITY_STATE.STATE_ENABLE_BT_FOR_CONNECT;
                    startBluetooth();
                    return;
                }
                toggleConnection();
                break;

            case R.id.deleteBtn:
                if (debug) logi("onClick() :: deleteBtn");
                handleDeleteMicrobit();
                break;
            case R.id.cancel_enter_pin_step_4_btn:
                displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                break;
            case R.id.backBtn:
                if (debug) logi("onClick() :: backBtn");
                handleResetAll();
                break;

            default:
                Toast.makeText(MBApp.getContext(), "Default Item Clicked: " + v.getId(), Toast.LENGTH_SHORT).show();
                break;

        }
    }

    public void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getApplicationWindowToken(), 0);
    }

    private void handleDeleteMicrobit() {
        PopUp.show(this,
                getString(R.string.deleteMicrobitMessage), //message
                getString(R.string.deleteMicrobitTitle), //title
                R.drawable.delete_project, R.drawable.red_btn,
                PopUp.TYPE_CHOICE, //type of popup.
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopUp.hide();
                        //Unpair the device for secure BLE
                        unpairDeivce();
                        Utils.setPairedMicrobit(MBApp.getContext(), null);
                        updatePairedDeviceCard();
                    }
                },//override click listener for ok button
                null);//pass null to use default listener
    }

    private void unpairDeivce() {
        ConnectedDevice connectedDevice = Utils.getPairedMicrobit(this);
        String addressToDelete = connectedDevice.mAddress;
        // Get the paired devices and put them in a Set
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice bt : pairedDevices) {
            logi("Paired device " + bt.getName());
            if (bt.getAddress().equals(addressToDelete)) {
                try {
                    Method m = bt.getClass().getMethod("removeBond", (Class[]) null);
                    m.invoke(bt, (Object[]) null);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    private void handleResetAll() {
        Arrays.fill(deviceCodeArray, "0");
        scanLeDevice(false);
        cancelPairing();

        if (!isPortraitMode()) {
            mState = PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON;
            finish();
        } else if (isPortraitMode() && mState == PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON) {
            finish();
        } else {
            displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
        }
    }

    private void handlePairingFailed() {

        if (debug) logi("handlePairingFailed() :: Start");
        PopUp.show(this,
                getString(R.string.pairingErrorMessage), //message
                getString(R.string.timeOut), //title
                R.drawable.error_face, //image icon res id
                R.drawable.red_btn,
                PopUp.TYPE_CHOICE, //type of popup.
                mRetryPairing,//override click listener for ok button
                mFailedPairingHandler);
    }

    private void handlePairingSuccessful(final ConnectedDevice newDev) {
        logi("handlePairingSuccessful()");
        Utils.setPairedMicrobit(MBApp.getContext(), newDev);
        updatePairedDeviceCard();
        // Pop up to show pairing successful
        PopUp.show(MBApp.getContext(),
                " micro:bit paired successfully", // message
                getString(R.string.pairing_success_message_1), //title
                R.drawable.message_face, //image icon res id
                R.drawable.green_btn,
                PopUp.TYPE_ALERT, //type of popup.
                mSuccessFulPairingHandler,
                mSuccessFulPairingHandler);
    }

    private void cancelPairing() {
        logi("###>>>>>>>>>>>>>>>>>>>>> cancelPairing");
        scanLeDevice(false);//TODO: is it really needed?
    }

    /*
     * TODO : Part of HACK 20150729
     * =================================================================
     */
    private void scanLeDevice(final boolean enable) {

        if (debug) logi("scanLeDevice() :: enable = " + enable);
        if (enable) {
            if (!setupBleController()) {
                if (debug) logi("scanLeDevice() :: FAILED ");
                return;
            }
            if (!mScanning) {
                if (debug) logi("scanLeDevice ::   Searching For " + mNewDeviceName.toLowerCase());
                // Stops scanning after a pre-defined scan period.
                mScanning = true;
                TextView textView = (TextView) findViewById(R.id.search_microbit_step_3_title);
                if (textView != null)
                    textView.setText(getString(R.string.searchingTitle));

                mHandler.postDelayed(scanTimedOut, SCAN_PERIOD);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { //Lollipop
                    mBluetoothAdapter.startLeScan((BluetoothAdapter.LeScanCallback) getBlueToothCallBack());
                } else {
                    List<ScanFilter> filters = new ArrayList<ScanFilter>();
                    // TODO: play with ScanSettings further to ensure the Kit kat devices connect with higher success rate
                    ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                    mLEScanner.startScan(filters, settings, (ScanCallback) getBlueToothCallBack());
                }
            }
        } else {
            if (mScanning) {
                mScanning = false;
                mHandler.removeCallbacks(scanTimedOut);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    mBluetoothAdapter.stopLeScan((BluetoothAdapter.LeScanCallback) getBlueToothCallBack());
                } else {
                    mLEScanner.stopScan((ScanCallback) getBlueToothCallBack());
                }
            }
        }
    }

    private static Runnable scanTimedOut = new Runnable() {
        @Override
        public void run() {
            PairingActivity.instance.scanFailedCallbackImpl();
        }
    };

    private void scanFailedCallbackImpl() {
        boolean scanning = mScanning;
        scanLeDevice(false);

        if (scanning) { // was scanning
            handlePairingFailed();
        }
    }

    private static Object mBluetoothScanCallBack = null;

    private static Object getBlueToothCallBack() {
        if (mBluetoothScanCallBack == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBluetoothScanCallBack = (ScanCallback) new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        super.onScanResult(callbackType, result);
                        Log.i("callbackType = ", String.valueOf(callbackType));
                        Log.i("result = ", result.toString());
                        BluetoothDevice btDevice = result.getDevice();
                        PairingActivity.instance.onLeScan(btDevice, result.getRssi(), result.getScanRecord().getBytes());
                    }

                    @Override
                    public void onBatchScanResults(List<ScanResult> results) {
                        super.onBatchScanResults(results);
                        for (ScanResult sr : results) {
                            Log.i("Scan result - Results ", sr.toString());
                        }
                    }

                    @Override
                    public void onScanFailed(int errorCode) {
                        super.onScanFailed(errorCode);
                        Log.i("Scan failed", "Error Code : " + errorCode);
                    }

                };

                return (mBluetoothScanCallBack);
            } else {
                mBluetoothScanCallBack = new BluetoothAdapter.LeScanCallback() {
                    @Override
                    public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                        PairingActivity.instance.onLeScan(device, rssi, scanRecord);
                    }
                };
            }
        }
        return mBluetoothScanCallBack;
    }

    public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

        if (debug) logi("mLeScanCallback.onLeScan() [+]");

		/*
         * TODO : Part of HACK 20150729
	 	* =================================================================
	 	*/
        if (!mScanning) {
            return;
        }
        /*
         * =================================================================
		 */

        if (device == null) {
            return;
        }

        if ((mNewDeviceName.isEmpty()) || (device.getName() == null)) {
            if (debug)
                logi("mLeScanCallback.onLeScan() ::   Cannot Compare " + device.getAddress() + " " + rssi + " " + Arrays.toString(scanRecord));
        } else {
            String s = device.getName().toLowerCase();
            //Replace all : to blank - Fix for #64
            //TODO Use pattern recognition instead
            s = s.replaceAll(":", "");
            if (mNewDeviceName.toLowerCase().equals(s)) {

                if (debug)
                    logi("mLeScanCallback.onLeScan() ::   Found micro:bit -" + device.getName().toLowerCase() + " " + device.getAddress());
                // Stop scanning as device is found.
                scanLeDevice(false);
                mNewDeviceAddress = device.getAddress();
                //Rohit : Do Not Call the TextView.setText directly. It doesn't work on 4.4.4
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView textView = (TextView) findViewById(R.id.search_microbit_step_3_title);
                        if (textView != null)
                            textView.setText(getString(R.string.pairing_msg_1));
                        //startPairing(mNewDeviceAddress);
                        startPairingSecureBle(device);
                    }
                });
            } else {

                if (debug)
                    logi("mLeScanCallback.onLeScan() ::   Found - device.getName() == " + device.getName().toLowerCase());
            }
        }
    }

    private void startPairingSecureBle(BluetoothDevice device) {
        logi("###>>>>>>>>>>>>>>>>>>>>> startPairingSecureBle");
        //Check if the device is already bonded
        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            logi("Device is already bonded.");
            cancelPairing();
            //Get device name from the System settings if present and add to our list
            ConnectedDevice newDev = new ConnectedDevice(mNewDeviceCode.toUpperCase(), mNewDeviceCode.toUpperCase(), false, mNewDeviceAddress, 0);
            handlePairingSuccessful(newDev);
            return;
        }
        try {
            boolean retValue = device.createBond();
            logi("device.createBond returns " + retValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPairTipView.setVisibility(View.GONE);
        mNewDeviceView.setVisibility(View.GONE);
        mPairSearchView.setVisibility(View.GONE);
        mConnectDeviceView.setVisibility(View.GONE); // TODO check this
        mEnterPinView.setVisibility(View.GONE); // TODO check this
        unregisterReceiver(mPairReceiver);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (debug) logi("onKeyDown() :: Cancel");
            handleResetAll();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_launcher, menu);
        return true;
    }
}
