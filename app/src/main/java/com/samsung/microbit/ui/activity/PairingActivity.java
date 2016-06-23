package com.samsung.microbit.ui.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.PermissionChecker;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.samsung.microbit.BuildConfig;
import com.samsung.microbit.MBApp;
import com.samsung.microbit.R;
import com.samsung.microbit.core.IPCMessageManager;
import com.samsung.microbit.core.bluetooth.BluetoothUtils;
import com.samsung.microbit.data.constants.EventCategories;
import com.samsung.microbit.data.model.ConnectedDevice;
import com.samsung.microbit.data.constants.Constants;
import com.samsung.microbit.data.constants.PermissionCodes;
import com.samsung.microbit.data.constants.RequestCodes;
import com.samsung.microbit.service.IPCService;
import com.samsung.microbit.ui.BluetoothSwitch;
import com.samsung.microbit.ui.PopUp;
import com.samsung.microbit.ui.adapter.LEDAdapter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class PairingActivity extends Activity implements View.OnClickListener, BluetoothAdapter.LeScanCallback {

    private static boolean DISABLE_DEVICE_LIST = false;

    private enum PAIRING_STATE {
        PAIRING_STATE_CONNECT_BUTTON,
        PAIRING_STATE_TIP,
        PAIRING_STATE_PATTERN_EMPTY,
        PAIRING_STATE_SEARCHING,
        PAIRING_STATE_HOW_TO_PAIR_TWO,
        PAIRING_STATE_ERROR
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
    LinearLayout mPairTipViewScreenTwo;
    View mConnectDeviceView;
    LinearLayout mNewDeviceView;
    LinearLayout mPairSearchView;
    LinearLayout mBottomPairButton;

    // Connected Device Status
    Button deviceConnectionStatusBtn;

    private Handler mHandler;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 15000;

    private BluetoothAdapter mBluetoothAdapter = null;
    private volatile boolean mScanning = false;

    private int mCurrentOrientation;
    private BluetoothLeScanner mLEScanner = null;

    private enum ACTIVITY_STATE {
        STATE_IDLE,
        STATE_ENABLE_BT_FOR_CONNECT,
        STATE_ENABLE_BT_FOR_PAIRING,
        STATE_CONNECTING,
        STATE_DISCONNECTING
    }

    private List<Integer> mRequestPermission = new ArrayList<>();

    private int mRequestingPermission = -1;

    private static ACTIVITY_STATE mActivityState = ACTIVITY_STATE.STATE_IDLE;

    private ScanCallback newScanCallback;

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
                    ConnectedDevice newDev = new ConnectedDevice(mNewDeviceCode.toUpperCase(), mNewDeviceCode.toUpperCase(), false, mNewDeviceAddress, 0, null, System.currentTimeMillis());
                    handlePairingSuccessful(newDev);
                } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDING) {
                    scanLeDevice(false);
                    MBApp.getApp().getEchoClientManager().sendPairingStats(false, null);
                    PopUp.show(MBApp.getApp(),
                            getString(R.string.pairing_failed_message), //message
                            getString(R.string.pairing_failed_title), //title
                            R.drawable.error_face, //image icon res id
                            R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
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
            int error = intent.getIntExtra(IPCMessageManager.BUNDLE_ERROR_CODE, 0);
            String firmware = intent.getStringExtra(IPCMessageManager.BUNDLE_MICROBIT_FIRMWARE);
            int getNotification = intent.getIntExtra(IPCMessageManager.BUNDLE_MICROBIT_REQUESTS, -1);
            if (firmware != null && !firmware.isEmpty()) {
                BluetoothUtils.updateFirmwareMicrobit(context, firmware);
                return;
            }
            updatePairedDeviceCard();
            if (mActivityState == ACTIVITY_STATE.STATE_DISCONNECTING || mActivityState == ACTIVITY_STATE.STATE_CONNECTING) {

                if (getNotification == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL ||
                        getNotification == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_SMS) {
                    logi("micro:bit application needs more permissions");
                    mRequestPermission.add(getNotification);
                    return;
                }
                ConnectedDevice device = BluetoothUtils.getPairedMicrobit(context);
                if (mActivityState == ACTIVITY_STATE.STATE_CONNECTING) {
                    if (error == 0) {
                        MBApp.getApp().getEchoClientManager().sendConnectStats(Constants.ConnectionState.SUCCESS, device.mfirmware_version, null);
                        BluetoothUtils.updateConnectionStartTime(context, System.currentTimeMillis());
                        //Check if more permissions were needed and request in the Application
                        if (!mRequestPermission.isEmpty()) {
                            mActivityState = ACTIVITY_STATE.STATE_IDLE;
                            PopUp.hide();
                            checkTelephonyPermissions();
                            return;
                        }
                    } else {
                        MBApp.getApp().getEchoClientManager().sendConnectStats(Constants.ConnectionState.FAIL, null, null);
                    }
                }
                if (error == 0 && mActivityState == ACTIVITY_STATE.STATE_DISCONNECTING) {
                    long now = System.currentTimeMillis();
                    long connectionTime = (now - device.mlast_connection_time) / 1000; //Time in seconds
                    MBApp.getApp().getEchoClientManager().sendConnectStats(Constants.ConnectionState.DISCONNECT, device.mfirmware_version, Long.toString(connectionTime));
                }
                PopUp.hide();
                mActivityState = ACTIVITY_STATE.STATE_IDLE;

                if (error != 0) {
                    logi("localBroadcastReceiver Error code =" + error);
                    String message = intent.getStringExtra(IPCMessageManager.BUNDLE_ERROR_MESSAGE);
                    logi("localBroadcastReceiver Error message = " + message);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MBApp application = MBApp.getApp();
                            PopUp.show(application,
                                    application.getString(R.string.micro_bit_reset_msg),
                                    application.getString(R.string.general_error_title),
                                    R.drawable.error_face, R.drawable.red_btn,
                                    PopUp.GIFF_ANIMATION_ERROR,
                                    PopUp.TYPE_ALERT, null, null);
                        }
                    });
                }
            }

        }
    };
    View.OnClickListener notificationOKHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("notificationOKHandler");
            PopUp.hide();
            if (mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL) {
                String[] permissionsNeeded = {Manifest.permission.READ_PHONE_STATE};
                requetPermission(permissionsNeeded, PermissionCodes.INCOMING_CALL_PERMISSIONS_REQUESTED);
            }
            if (mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_SMS) {
                String[] permissionsNeeded = {Manifest.permission.RECEIVE_SMS};
                requetPermission(permissionsNeeded, PermissionCodes.INCOMING_SMS_PERMISSIONS_REQUESTED);
            }
        }
    };

    View.OnClickListener checkMorePermissionsNeeded = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!mRequestPermission.isEmpty()) {
                checkTelephonyPermissions();
            } else {
                PopUp.hide();
            }
        }
    };

    View.OnClickListener notificationCancelHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("notificationCancelHandler");
            String msg = "Your program might not run properly";
            if (mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL) {
                msg = getString(R.string.telephony_permission_error);
            } else if (mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_SMS) {
                msg = getString(R.string.sms_permission_error);
            }
            PopUp.hide();
            PopUp.show(MBApp.getApp(),
                    msg,
                    getString(R.string.permissions_needed_title),
                    R.drawable.error_face, R.drawable.red_btn,
                    PopUp.GIFF_ANIMATION_ERROR,
                    PopUp.TYPE_ALERT,
                    checkMorePermissionsNeeded, checkMorePermissionsNeeded);
        }
    };

    private void checkTelephonyPermissions() {
        if (!mRequestPermission.isEmpty()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PermissionChecker.PERMISSION_GRANTED ||
                    (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PermissionChecker.PERMISSION_GRANTED)) {
                mRequestingPermission = mRequestPermission.get(0);
                mRequestPermission.remove(0);
                PopUp.show(MBApp.getApp(),
                        (mRequestingPermission == EventCategories.IPC_BLE_NOTIFICATION_INCOMING_CALL) ? getString(R.string
                                .telephony_permission) : getString(R.string.sms_permission),
                        getString(R.string.permissions_needed_title),
                        R.drawable.message_face, R.drawable.blue_btn, PopUp.GIFF_ANIMATION_NONE,
                        PopUp.TYPE_CHOICE,
                        notificationOKHandler,
                        notificationCancelHandler);
            }
        }
    }
    // *************************************************

    // DEBUG
    protected boolean debug = BuildConfig.DEBUG;
    protected String TAG = PairingActivity.class.getSimpleName();

    protected void logi(String message) {
        if (debug)
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setContentView(R.layout.activity_connect);
        initViews();
        mCurrentOrientation = getResources().getConfiguration().orientation;
        displayScreen(mState);
    }

    /**
     * Setup font styles by setting an appropriate typefaces.
     */
    private void setupFontStyle() {
        deviceConnectionStatusBtn.setTypeface(MBApp.getApp().getTypeface());
        // Connect Screen
        TextView appBarTitle = (TextView) findViewById(R.id.flash_projects_title_txt);
        appBarTitle.setTypeface(MBApp.getApp().getTypeface());

        TextView manageMicrobit = (TextView) findViewById(R.id.title_manage_microbit);
        manageMicrobit.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView manageMicorbitStatus = (TextView) findViewById(R.id.device_status_txt);
        manageMicorbitStatus.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView descriptionManageMicrobit = (TextView) findViewById(R.id.description_manage_microbit);
        descriptionManageMicrobit.setTypeface(MBApp.getApp().getRobotoTypeface());

        TextView pairBtnText = (TextView) findViewById(R.id.custom_pair_button_text);
        pairBtnText.setTypeface(MBApp.getApp().getTypeface());

        TextView problemsMicrobit = (TextView) findViewById(R.id.connect_microbit_problems_message);
        problemsMicrobit.setTypeface(MBApp.getApp().getRobotoTypeface());

        // How to pair your micro:bit - Screen #1
        TextView pairTipTitle = (TextView) findViewById(R.id.pairTipTitle);
        pairTipTitle.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView stepOneTitle = (TextView) findViewById(R.id.pair_tip_step_1_step);
        stepOneTitle.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView stepOneInstructions = (TextView) findViewById(R.id.pair_tip_step_1_instructions);
        stepOneInstructions.setTypeface(MBApp.getApp().getRobotoTypeface());

        Button cancelPairButton = (Button) findViewById(R.id.cancel_tip_step_1_btn);
        cancelPairButton.setTypeface(MBApp.getApp().getRobotoTypeface());

        Button nextPairButton = (Button) findViewById(R.id.ok_tip_step_1_btn);
        nextPairButton.setTypeface(MBApp.getApp().getRobotoTypeface());

        // How to pair your micro:bit - Screen #2
        TextView howToPairStepThreeTitle = (TextView) findViewById(R.id.how_to_pair_screen_two_title);
        howToPairStepThreeTitle.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView howToPairStepThreeStep = (TextView) findViewById(R.id.pair_tip_step_3_step);
        howToPairStepThreeStep.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView howToPairStepThreeText = (TextView) findViewById(R.id.pair_tip_step_3_instructions);
        howToPairStepThreeText.setTypeface(MBApp.getApp().getRobotoTypeface());

        TextView howToPairStepFourTitle = (TextView) findViewById(R.id.pair_tip_step_4_step);
        howToPairStepFourTitle.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView howToPairStepFourText = (TextView) findViewById(R.id.pair_tip_step_4_instructions);
        howToPairStepFourText.setTypeface(MBApp.getApp().getRobotoTypeface());

        Button cancelPairScreenTwoButton = (Button) findViewById(R.id.cancel_tip_step_3_btn);
        cancelPairScreenTwoButton.setTypeface(MBApp.getApp().getRobotoTypeface());

        Button nextPairScreenTwoButton = (Button) findViewById(R.id.ok_tip_step_3_btn);
        nextPairScreenTwoButton.setTypeface(MBApp.getApp().getRobotoTypeface());

        // Step 2 - Enter Pattern
        TextView enterPatternTitle = (TextView) findViewById(R.id.enter_pattern_step_2_title);
        enterPatternTitle.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView stepTwoTitle = (TextView) findViewById(R.id.pair_enter_pattern_step_2);
        stepTwoTitle.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView stepTwoInstructions = (TextView) findViewById(R.id.pair_enter_pattern_step_2_instructions);
        stepTwoInstructions.setTypeface(MBApp.getApp().getRobotoTypeface());

        ImageView ohPrettyImg = (ImageView) findViewById(R.id.oh_pretty_emoji);
        ohPrettyImg.setVisibility(View.INVISIBLE);

        Button cancelEnterPattern = (Button) findViewById(R.id.cancel_enter_pattern_step_2_btn);
        cancelEnterPattern.setTypeface(MBApp.getApp().getRobotoTypeface());

        Button okEnterPatternButton = (Button) findViewById(R.id.ok_enter_pattern_step_2_btn);
        okEnterPatternButton.setTypeface(MBApp.getApp().getRobotoTypeface());

        // Step 3 - Searching for micro:bit
        TextView searchMicrobitTitle = (TextView) findViewById(R.id.search_microbit_step_3_title);
        searchMicrobitTitle.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView stepThreeTitle = (TextView) findViewById(R.id.searching_microbit_step);
        stepThreeTitle.setTypeface(MBApp.getApp().getTypefaceBold());

        TextView stepThreeInstructions = (TextView) findViewById(R.id.searching_microbit_step_instructions);
        stepThreeInstructions.setTypeface(MBApp.getApp().getRobotoTypeface());

        Button cancelSearchMicroBit = (Button) findViewById(R.id.cancel_search_microbit_step_3_btn);
        cancelSearchMicroBit.setTypeface(MBApp.getApp().getRobotoTypeface());
    }

    private void initViews() {
        deviceConnectionStatusBtn = (Button) findViewById(R.id.connected_device_status_button);
        mBottomPairButton = (LinearLayout) findViewById(R.id.ll_pairing_activity_screen);
        mPairButtonView = (LinearLayout) findViewById(R.id.pairButtonView);
        mPairTipView = (LinearLayout) findViewById(R.id.pairTipView);
        mPairTipViewScreenTwo = (LinearLayout) findViewById(R.id.pair_tip_screen_two);
        mConnectDeviceView = findViewById(R.id.connectDeviceView); // Connect device view
        mNewDeviceView = (LinearLayout) findViewById(R.id.newDeviceView);
        mPairSearchView = (LinearLayout) findViewById(R.id.pairSearchView);

        //Setup on click listeners.
        deviceConnectionStatusBtn.setOnClickListener(this);
        findViewById(R.id.pairButton).setOnClickListener(this);
        findViewById(R.id.cancel_tip_step_1_btn).setOnClickListener(this);
        findViewById(R.id.ok_enter_pattern_step_2_btn).setOnClickListener(this);
        findViewById(R.id.cancel_tip_step_3_btn).setOnClickListener(this);
        findViewById(R.id.ok_tip_step_3_btn).setOnClickListener(this);
        findViewById(R.id.cancel_enter_pattern_step_2_btn).setOnClickListener(this);
        findViewById(R.id.cancel_search_microbit_step_3_btn).setOnClickListener(this);

        setupFontStyle();
    }

    private void releaseViews() {
        deviceConnectionStatusBtn = null;
        mBottomPairButton = null;
        mPairButtonView = null;
        mPairTipView = null;
        mPairTipViewScreenTwo = null;
        mConnectDeviceView = null; // Connect device view
        mNewDeviceView = null;
        mPairSearchView = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePairedDeviceCard();

        // Step 1 - How to pair
        findViewById(R.id.pair_tip_step_1_giff).animate();
        // Step 3 - Searching for micro:bit

    }

    @Override
    public void onPause() {
        logi("onPause() ::");
        super.onPause();

        // Step 1 - How to pair

        // Step 3 - Stop searching for micro:bit animation
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        logi("onCreate() ::");

        super.onCreate(savedInstanceState);

        // Make sure to call this before any other userActionEvent is sent
        MBApp.getApp().getEchoClientManager().sendViewEventStats("pairingactivity");

        IntentFilter broadcastIntentFilter = new IntentFilter(IPCService.INTENT_BLE_NOTIFICATION);
        LocalBroadcastManager.getInstance(MBApp.getApp()).registerReceiver(localBroadcastReceiver, broadcastIntentFilter);

        //Register receiver
        IntentFilter intent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mPairReceiver, intent);

        setupBleController();

        // ************************************************
        //Remove title barproject_list
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_connect);

        mHandler = new Handler(Looper.getMainLooper());

        initViews();

        updatePairedDeviceCard();

        mCurrentOrientation = getResources().getConfiguration().orientation;

        // pin view
        displayScreen(mState);
    }

    boolean setupBleController() {
        boolean retvalue = true;

        if (mBluetoothAdapter == null) {
            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
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
        if (requestCode == RequestCodes.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                if (mActivityState == ACTIVITY_STATE.STATE_ENABLE_BT_FOR_PAIRING) {
                    startWithPairing();
                } else if (mActivityState == ACTIVITY_STATE.STATE_ENABLE_BT_FOR_CONNECT) {
                    toggleConnection();
                }
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                PopUp.show(MBApp.getApp(),
                        getString(R.string.bluetooth_off_cannot_continue), //message
                        "",
                        R.drawable.error_face, R.drawable.red_btn,
                        PopUp.GIFF_ANIMATION_ERROR,
                        PopUp.TYPE_ALERT,
                        null, null);
            }
            //Change state back to Idle
            mActivityState = ACTIVITY_STATE.STATE_IDLE;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void displayLedGrid() {

        final GridView gridview = (GridView) findViewById(R.id.enter_pattern_step_2_gridview);
        gridview.setAdapter(new LEDAdapter(this, deviceCodeArray));
        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                boolean isOn = toggleLED((ImageView) v, position);
                setCol(parent, position, isOn);

                checkPatternSuccess();
            }

        });

        checkPatternSuccess();
    }

    private void checkPatternSuccess() {
        final ImageView ohPrettyImage = (ImageView) findViewById(R.id.oh_pretty_emoji);
        if (deviceCodeArray[20].equals("1") && deviceCodeArray[21].equals("1")
                && deviceCodeArray[22].equals("1") && deviceCodeArray[23].equals("1")
                && deviceCodeArray[24].equals("1")) {
            findViewById(R.id.ok_enter_pattern_step_2_btn).setVisibility(View.VISIBLE);
            ohPrettyImage.setImageResource(R.drawable.emoji_entering_pattern_valid_pattern);
        } else {
            findViewById(R.id.ok_enter_pattern_step_2_btn).setVisibility(View.INVISIBLE);
            ohPrettyImage.setImageResource(R.drawable.emoji_entering_pattern);
        }
    }

    private void generateName() {

        mNewDeviceName = "";
        mNewDeviceCode = "";
        //Columns
        for (int col = 0; col < 5; col++) {
            //Rows
            for (int row = 0; row < 5; row++) {
                if (deviceCodeArray[(col + (5 * row))].equals("1")) {
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
            v.setTag(R.id.ledState, 0);
            v.setSelected(false);
            deviceCodeArray[index] = "0";
            int position = (Integer) v.getTag(R.id.position);
            v.setContentDescription("" + position + getLEDStatus(index)); // TODO - calculate correct position
            index -= 5;
        }
        index = pos + 5;
        while (index < 25) {
            v = (ImageView) parent.getChildAt(index);
            v.setBackground(getApplication().getResources().getDrawable(R.drawable.red_white_led_btn));
            v.setTag(R.id.ledState, 1);
            v.setSelected(false);
            deviceCodeArray[index] = "1";
            int position = (Integer) v.getTag(R.id.position);
            v.setContentDescription("" + position + getLEDStatus(index));
            index += 5;
        }

    }

    private boolean toggleLED(ImageView image, int pos) {
        boolean isOn;
        //Toast.makeText(this, "Pos :" +  pos, Toast.LENGTH_SHORT).show();
        int state = (Integer) image.getTag(R.id.ledState);
        if (state != 1) {
            deviceCodeArray[pos] = "1";
            image.setBackground(getApplication().getResources().getDrawable(R.drawable.red_white_led_btn));
            image.setTag(R.id.ledState, 1);
            isOn = true;

        } else {
            deviceCodeArray[pos] = "0";
            image.setBackground(getApplication().getResources().getDrawable(R.drawable.white_red_led_btn));
            image.setTag(R.id.ledState, 0);
            isOn = false;
            // Update the code to consider the still ON LED below the toggled one
            if (pos < 20) {
                deviceCodeArray[pos + 5] = "1";
            }
        }

        image.setSelected(false);
        int position = (Integer) image.getTag(R.id.position);
        image.setContentDescription("" + position + getLEDStatus(pos));
        return isOn;
    }

    // To read out the status of the currently selected LED at a given position
    private String getLEDStatus(int position) {
        String statusRead;
        if (deviceCodeArray[position].equals("1")) {
            statusRead = "on";
        } else {
            statusRead = "off";
        }
        return statusRead;
    }

    // Get the drawable for the device connection status
    private Drawable getDrawableResource(int resID) {
        return ContextCompat.getDrawable(this, resID);
    }

    private void updateConnectionStatus() {
        ConnectedDevice connectedDevice = BluetoothUtils.getPairedMicrobit(this);
        Drawable mDeviceDisconnectedImg;
        Drawable mDeviceConnectedImg;

        mDeviceDisconnectedImg = getDrawableResource(R.drawable.device_status_disconnected);
        mDeviceConnectedImg = getDrawableResource(R.drawable.device_status_connected);

        if (!connectedDevice.mStatus) {
            // Device is not connected
            deviceConnectionStatusBtn.setBackgroundResource(R.drawable.grey_btn);
            deviceConnectionStatusBtn.setTextColor(Color.WHITE);
            deviceConnectionStatusBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, mDeviceDisconnectedImg, null);
            deviceConnectionStatusBtn.setContentDescription("Micro:bit not connected " + connectedDevice.mName + "is " + getMicrobitStatusForAccessibility(connectedDevice.mStatus));

        } else {
            // Device is connected
            deviceConnectionStatusBtn.setBackgroundResource(R.drawable.white_btn_devices_status_connected);
            deviceConnectionStatusBtn.setTextColor(Color.BLACK);
            deviceConnectionStatusBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, mDeviceConnectedImg, null);
            deviceConnectionStatusBtn.setContentDescription("Currently connected Micro:bit " + connectedDevice.mName + "is " + getMicrobitStatusForAccessibility(connectedDevice.mStatus));
        }
    }

    // Retrieve Micro:bit accessibility state
    public String getMicrobitStatusForAccessibility(boolean status) {
        String statusRead;
        if (status) {
            statusRead = "on";
        } else {
            statusRead = "off";
        }
        return statusRead;
    }

    private void updatePairedDeviceCard() {
        if (deviceConnectionStatusBtn != null) {
            ConnectedDevice connectedDevice = BluetoothUtils.getPairedMicrobit(this);
            if (connectedDevice.mName == null) {
                // No device is Paired
                deviceConnectionStatusBtn.setBackgroundResource(R.drawable.grey_btn);
                deviceConnectionStatusBtn.setText("-");
                deviceConnectionStatusBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            } else {
                deviceConnectionStatusBtn.setText(connectedDevice.mName);
                updateConnectionStatus();
            }
        }
    }

    public static boolean disableListView() {
        return DISABLE_DEVICE_LIST;
    }

    private void displayScreen(PAIRING_STATE gotoState) {
        //Reset all screens first
        mPairTipView.setVisibility(View.GONE);
        mPairTipViewScreenTwo.setVisibility(View.GONE);
        mNewDeviceView.setVisibility(View.GONE);
        mPairSearchView.setVisibility(View.GONE);
        mConnectDeviceView.setVisibility(View.GONE);

        logi("********** Connect: state from " + mState + " to " + gotoState);
        mState = gotoState;

        DISABLE_DEVICE_LIST = !((gotoState == PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON) ||
                (gotoState == PAIRING_STATE.PAIRING_STATE_ERROR));

        if (!disableListView()) {
            updatePairedDeviceCard();
            mConnectDeviceView.setVisibility(View.VISIBLE);
        }

        switch (gotoState) {
            case PAIRING_STATE_CONNECT_BUTTON:
                break;

            case PAIRING_STATE_HOW_TO_PAIR_TWO:
                mPairTipViewScreenTwo.setVisibility(View.VISIBLE);
                break;

            case PAIRING_STATE_ERROR:
                Arrays.fill(deviceCodeArray, "0");
                findViewById(R.id.enter_pattern_step_2_gridview).setEnabled(true);
                mNewDeviceName = "";
                mNewDeviceCode = "";
                break;

            case PAIRING_STATE_TIP:
                mPairTipView.setVisibility(View.VISIBLE);
                findViewById(R.id.ok_tip_step_1_btn).setOnClickListener(this);
                break;

            case PAIRING_STATE_PATTERN_EMPTY:
                mNewDeviceView.setVisibility(View.VISIBLE);
                findViewById(R.id.cancel_enter_pattern_step_2_btn).setVisibility(View.VISIBLE);
                findViewById(R.id.enter_pattern_step_2_title).setVisibility(View.VISIBLE);
                findViewById(R.id.oh_pretty_emoji).setVisibility(View.VISIBLE);

                displayLedGrid();
                break;

            case PAIRING_STATE_SEARCHING:
                if (mPairSearchView != null) {
                    mPairSearchView.setVisibility(View.VISIBLE);
                    TextView tvTitle = (TextView) findViewById(R.id.search_microbit_step_3_title);
                    TextView tvSearchingStep = (TextView) findViewById(R.id.searching_microbit_step);
                    tvSearchingStep.setContentDescription(tvSearchingStep.getText());
                    TextView tvSearchingInstructions = (TextView) findViewById(R.id.searching_microbit_step_instructions);
                    if (tvTitle != null) {
                        tvTitle.setText(R.string.searchingTitle);
                        findViewById(R.id.searching_progress_spinner).setVisibility(View.VISIBLE);
                        findViewById(R.id.searching_microbit_found_giffview).setVisibility(View.GONE);
                        if(mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                            tvSearchingStep.setText(R.string.searching_tip_step_text_one_line);
                        } else {
                            tvSearchingStep.setText(R.string.searching_tip_step_text);
                        }
                        tvSearchingInstructions.setText(R.string.searching_tip_text_instructions);
                    }
                }
                break;
        }
    }

    private void startBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, RequestCodes.REQUEST_ENABLE_BT);
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
        ConnectedDevice currentDevice = BluetoothUtils.getPairedMicrobit(this);
        if (currentDevice.mAddress != null) {
            boolean currentState = currentDevice.mStatus;
            if (!currentState) {
                mActivityState = ACTIVITY_STATE.STATE_CONNECTING;
                mRequestPermission.clear();
                PopUp.show(MBApp.getApp(),
                        getString(R.string.init_connection),
                        "",
                        R.drawable.message_face, R.drawable.blue_btn,
                        PopUp.GIFF_ANIMATION_NONE,
                        PopUp.TYPE_SPINNER_NOT_CANCELABLE,
                        null, null);
                IPCService.bleConnect();
            } else {
                mActivityState = ACTIVITY_STATE.STATE_DISCONNECTING;
                PopUp.show(MBApp.getApp(),
                        getString(R.string.disconnecting),
                        "",
                        R.drawable.message_face, R.drawable.blue_btn,
                        PopUp.GIFF_ANIMATION_NONE,
                        PopUp.TYPE_SPINNER_NOT_CANCELABLE,
                        null, null);
                IPCService.bleDisconnect();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    proceedAfterBlePermissionGranted();
                } else {
                    PopUp.show(MBApp.getApp(),
                            getString(R.string.location_permission_error),
                            getString(R.string.permissions_needed_title),
                            R.drawable.error_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
                            PopUp.TYPE_ALERT,
                            null, null);
                }
            }
            break;
            case PermissionCodes.INCOMING_CALL_PERMISSIONS_REQUESTED: {
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    PopUp.show(MBApp.getApp(),
                            getString(R.string.telephony_permission_error),
                            getString(R.string.permissions_needed_title),
                            R.drawable.error_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
                            PopUp.TYPE_ALERT,
                            checkMorePermissionsNeeded, checkMorePermissionsNeeded);
                } else {
                    if (!mRequestPermission.isEmpty()) {
                        checkTelephonyPermissions();
                    }
                }
            }
            break;
            case PermissionCodes.INCOMING_SMS_PERMISSIONS_REQUESTED: {
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    PopUp.show(MBApp.getApp(),
                            getString(R.string.sms_permission_error),
                            getString(R.string.permissions_needed_title),
                            R.drawable.error_face, R.drawable.red_btn,
                            PopUp.GIFF_ANIMATION_ERROR,
                            PopUp.TYPE_ALERT,
                            checkMorePermissionsNeeded, checkMorePermissionsNeeded);
                } else {
                    if (!mRequestPermission.isEmpty()) {
                        checkTelephonyPermissions();
                    }
                }
            }
            break;
        }
    }

    private void proceedAfterBlePermissionGranted() {
        if (!BluetoothSwitch.getInstance().isBluetoothON()) {
            mActivityState = ACTIVITY_STATE.STATE_ENABLE_BT_FOR_PAIRING;
            startBluetooth();
            return;
        }
        startWithPairing();
    }

    private void requetPermission(String[] permissions, final int requestCode) {
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    View.OnClickListener bluetoothPermissionOKHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("bluetoothPermissionOKHandler");
            PopUp.hide();
            String[] permissionsNeeded = {Manifest.permission.ACCESS_COARSE_LOCATION};
            requetPermission(permissionsNeeded, PermissionCodes.BLUETOOTH_PERMISSIONS_REQUESTED);
        }
    };
    View.OnClickListener bluetoothPermissionCancelHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("bluetoothPermissionCancelHandler");
            PopUp.hide();
            PopUp.show(MBApp.getApp(),
                    getString(R.string.location_permission_error),
                    getString(R.string.permissions_needed_title),
                    R.drawable.error_face, R.drawable.red_btn,
                    PopUp.GIFF_ANIMATION_ERROR,
                    PopUp.TYPE_ALERT,
                    null, null);
        }
    };

    private void checkBluetoothPermissions() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PermissionChecker.PERMISSION_GRANTED) {
            PopUp.show(MBApp.getApp(),
                    getString(R.string.location_permission_pairing),
                    getString(R.string.permissions_needed_title),
                    R.drawable.message_face, R.drawable.blue_btn, PopUp.GIFF_ANIMATION_NONE,
                    PopUp.TYPE_CHOICE,
                    bluetoothPermissionOKHandler,
                    bluetoothPermissionCancelHandler);
        } else {
            proceedAfterBlePermissionGranted();
        }
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            // Pair a micro:bit
            case R.id.pairButton:
                logi("onClick() :: pairButton");
                checkBluetoothPermissions();
                break;
            // Proceed to Enter Pattern
            case R.id.ok_tip_step_1_btn:
                logi("onClick() :: ok_tip_screen_one_button");
                displayScreen(PAIRING_STATE.PAIRING_STATE_PATTERN_EMPTY);
                break;
            // Confirm pattern and begin searching for micro:bit
            case R.id.ok_enter_pattern_step_2_btn:
                logi("onClick() :: ok_tip_screen_one_button");
                displayScreen(PAIRING_STATE.PAIRING_STATE_HOW_TO_PAIR_TWO);
                break;

            case R.id.cancel_tip_step_1_btn:
                logi("onClick() :: cancel_tip_button");
                displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                break;

            case R.id.cancel_tip_step_3_btn:
                logi("onClick() :: cancel_tip_screen_two_button");
                displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                break;
            case R.id.ok_tip_step_3_btn:
                logi("onClick() :: ok_tip_screen_two_button");
                if (mState == PAIRING_STATE.PAIRING_STATE_HOW_TO_PAIR_TWO) {
                    generateName();
                    if (!BluetoothSwitch.getInstance().checkBluetoothAndStart()) {
                        return;
                    }
                    scanLeDevice(true);
                    displayScreen(PAIRING_STATE.PAIRING_STATE_SEARCHING);
                }
                break;
            case R.id.cancel_enter_pattern_step_2_btn:
                logi("onClick() :: cancel_name_button");
                cancelPairing();
                displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                break;
            case R.id.cancel_search_microbit_step_3_btn:
                logi("onClick() :: cancel_search_button");
                scanLeDevice(false);
                cancelPairing();
                displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                break;
            case R.id.connected_device_status_button:
                logi("onClick() :: connectBtn");
                if (!BluetoothSwitch.getInstance().isBluetoothON()) {
                    mActivityState = ACTIVITY_STATE.STATE_ENABLE_BT_FOR_CONNECT;
                    startBluetooth();
                    return;
                }
                toggleConnection();
                break;
            // Delete Microbit
            case R.id.deleteBtn:
                logi("onClick() :: deleteBtn");
                handleDeleteMicrobit();
                break;
            case R.id.backBtn:
                logi("onClick() :: backBtn");
                handleResetAll();
                break;

            default:
                Toast.makeText(MBApp.getApp(), "Default Item Clicked: " + v.getId(), Toast.LENGTH_SHORT).show();
                break;

        }
    }

    private void handleDeleteMicrobit() {
        PopUp.show(this,
                getString(R.string.deleteMicrobitMessage), //message
                getString(R.string.deleteMicrobitTitle), //title
                R.drawable.ic_trash, R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_NONE,
                PopUp.TYPE_CHOICE, //type of popup.
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopUp.hide();
                        //Unpair the device for secure BLE
                        unpairDeivce();
                        BluetoothUtils.setPairedMicroBit(MBApp.getApp(), null);
                        updatePairedDeviceCard();
                    }
                },//override click listener for ok button
                null);//pass null to use default listener
    }

    private void unpairDeivce() {
        ConnectedDevice connectedDevice = BluetoothUtils.getPairedMicrobit(this);
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
                } catch (NoSuchMethodException | IllegalAccessException
                        | InvocationTargetException e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    }

    private void handleResetAll() {
        Arrays.fill(deviceCodeArray, "0");
        scanLeDevice(false);
        cancelPairing();

        if (mState == PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON) {
            finish();
        } else {
            displayScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
        }
    }

    private void handlePairingFailed() {

        logi("handlePairingFailed() :: Start");
        MBApp.getApp().getEchoClientManager().sendPairingStats(false, null);
        PopUp.show(this,
                getString(R.string.pairingErrorMessage), //message
                getString(R.string.timeOut), //title
                R.drawable.error_face, //image icon res id
                R.drawable.red_btn,
                PopUp.GIFF_ANIMATION_ERROR,
                PopUp.TYPE_CHOICE, //type of popup.
                mRetryPairing,//override click listener for ok button
                mFailedPairingHandler);
    }

    private void handlePairingSuccessful(final ConnectedDevice newDev) {
        logi("handlePairingSuccessful()");
        MBApp.getApp().getEchoClientManager().sendPairingStats(true, newDev.mfirmware_version);
        BluetoothUtils.setPairedMicroBit(MBApp.getApp(), newDev);
        updatePairedDeviceCard();
        // Pop up to show pairing successful
        PopUp.show(MBApp.getApp(),
                " micro:bit paired successfully", // message
                getString(R.string.pairing_success_message_1), //title
                R.drawable.message_face, //image icon res id
                R.drawable.green_btn,
                PopUp.GIFF_ANIMATION_NONE,
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

        logi("scanLeDevice() :: enable = " + enable);
        if (enable) {
            if (!setupBleController()) {
                logi("scanLeDevice() :: FAILED ");
                return;
            }
            if (!mScanning) {

                boolean hasBle = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);

                logi("scanLeDevice ::   Searching For " + mNewDeviceName.toLowerCase());
                // Stops scanning after a pre-defined scan period.
                mScanning = true;
                TextView textView = (TextView) findViewById(R.id.search_microbit_step_3_title);
                if (textView != null)
                    textView.setText(getString(R.string.searchingTitle));
                mHandler.postDelayed(scanTimedOut, SCAN_PERIOD);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { //Lollipop
                    mBluetoothAdapter.startLeScan(getOldScanCallback());
                } else {
                    List<ScanFilter> filters = new ArrayList<ScanFilter>();
                    // TODO: play with ScanSettings further to ensure the Kit kat devices connectMaybeInit with higher success rate
                    ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
                    mLEScanner.startScan(filters, settings, getNewScanCallback());
                }
            }
        } else {
            if (mScanning) {
                mScanning = false;
                mHandler.removeCallbacks(scanTimedOut);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    mBluetoothAdapter.stopLeScan(getOldScanCallback());
                } else {
                    mLEScanner.stopScan(getNewScanCallback());
                }
            }
        }
    }

    private ScanCallback getNewScanCallback() {
        if(newScanCallback == null) {
            newScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    Log.i("callbackType = ", String.valueOf(callbackType));
                    Log.i("result = ", result.toString());
                    BluetoothDevice btDevice = result.getDevice();
                    final ScanRecord scanRecord = result.getScanRecord();
                    if (scanRecord != null) {
                        onLeScan(btDevice, result.getRssi(), scanRecord.getBytes());
                    }
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
        }

        return newScanCallback;
    }

    private BluetoothAdapter.LeScanCallback getOldScanCallback() {
        return this;
    }

    private Runnable scanTimedOut = new Runnable() {
        @Override
        public void run() {
            scanFailedCallbackImpl();
        }
    };

    private void scanFailedCallbackImpl() {
        boolean scanning = mScanning;
        scanLeDevice(false);

        if (scanning) { // was scanning
            handlePairingFailed();
        }
    }

    @Override
    public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

        logi("mLeScanCallback.onLeScan() [+]");

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
            logi("mLeScanCallback.onLeScan() ::   Cannot Compare " + device.getAddress() + " " + rssi + " " + Arrays.toString(scanRecord));
        } else {
            String s = device.getName().toLowerCase();
            //Replace all : to blank - Fix for #64
            //TODO Use pattern recognition instead
            s = s.replaceAll(":", "");
            if (mNewDeviceName.toLowerCase().equals(s)) {
                logi("mLeScanCallback.onLeScan() ::   Found micro:bit -" + device.getName().toLowerCase() + " " + device.getAddress());
                // Stop scanning as device is found.
                scanLeDevice(false);
                mNewDeviceAddress = device.getAddress();
                //Rohit : Do Not Call the TextView.setText directly. It doesn't work on 4.4.4
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView textView = (TextView) findViewById(R.id.search_microbit_step_3_title);
                        TextView tvSearchingStep = (TextView) findViewById(R.id.searching_microbit_step);
                        TextView tvSearchingInstructions = (TextView) findViewById(R.id.searching_microbit_step_instructions);
                        if (textView != null) {
                            textView.setText(getString(R.string.searchingTitle));
                            findViewById(R.id.searching_progress_spinner).setVisibility(View.GONE);
                            findViewById(R.id.searching_microbit_found_giffview).setVisibility(View.VISIBLE);
                            if(mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                                tvSearchingStep.setText(R.string.searching_microbit_found_message_one_line);
                            } else {
                                tvSearchingStep.setText(R.string.searching_microbit_found_message);
                            }
                            tvSearchingInstructions.setText(R.string.searching_tip_text_instructions);
                            startPairingSecureBle(device);
                        }
                    }
                });
            } else {
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
            ConnectedDevice newDev = new ConnectedDevice(mNewDeviceCode.toUpperCase(),
                    mNewDeviceCode.toUpperCase(), false, mNewDeviceAddress, 0, null,
                    System.currentTimeMillis());
            handlePairingSuccessful(newDev);
            return;
        }
        logi("device.createBond returns " + device.createBond());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPairTipView.setVisibility(View.GONE);
        mPairTipViewScreenTwo.setVisibility(View.GONE);
        mNewDeviceView.setVisibility(View.GONE);
        mPairSearchView.setVisibility(View.GONE);
        mConnectDeviceView.setVisibility(View.GONE);

        releaseViews();

        unbindDrawables(findViewById(R.id.connected_device_status_button));
        unbindDrawables(findViewById(R.id.pairButtonView));
        unbindDrawables(findViewById(R.id.pairTipView));
        unbindDrawables(findViewById(R.id.pair_tip_screen_two));
        unbindDrawables(findViewById(R.id.connectDeviceView)); // Connect device view
//        unbindDrawables(findViewById(R.id.newDeviceView));
        unbindDrawables(findViewById(R.id.pairSearchView));
        unbindDrawables(findViewById(R.id.flash_projects_title_txt));
        unbindDrawables(findViewById(R.id.title_manage_microbit));
        unbindDrawables(findViewById(R.id.device_status_txt));
        unbindDrawables(findViewById(R.id.description_manage_microbit));
        unbindDrawables(findViewById(R.id.pairButton));
        unbindDrawables(findViewById(R.id.connect_microbit_problems_message));
        unbindDrawables(findViewById(R.id.pairTipTitle));
        unbindDrawables(findViewById(R.id.pair_tip_step_1_step));
        unbindDrawables(findViewById(R.id.pair_tip_step_1_instructions));

        unbindDrawables(findViewById(R.id.cancel_tip_step_1_btn));
        unbindDrawables(findViewById(R.id.ok_tip_step_1_btn));
        unbindDrawables(findViewById(R.id.how_to_pair_screen_two_title));
        unbindDrawables(findViewById(R.id.pair_tip_step_3_step));
        unbindDrawables(findViewById(R.id.pair_tip_step_3_instructions));
        unbindDrawables(findViewById(R.id.pair_tip_step_4_step));
        unbindDrawables(findViewById(R.id.pair_tip_step_4_instructions));
        unbindDrawables(findViewById(R.id.cancel_tip_step_3_btn));
        unbindDrawables(findViewById(R.id.ok_tip_step_3_btn));
        unbindDrawables(findViewById(R.id.enter_pattern_step_2_title));
        unbindDrawables(findViewById(R.id.pair_enter_pattern_step_2_instructions));
        unbindDrawables(findViewById(R.id.oh_pretty_emoji));
        unbindDrawables(findViewById(R.id.cancel_enter_pattern_step_2_btn));
        unbindDrawables(findViewById(R.id.ok_enter_pattern_step_2_btn));
        unbindDrawables(findViewById(R.id.search_microbit_step_3_title));
        unbindDrawables(findViewById(R.id.searching_microbit_step));
        unbindDrawables(findViewById(R.id.searching_microbit_step_instructions));
        unbindDrawables(findViewById(R.id.cancel_search_microbit_step_3_btn));
        unbindDrawables(findViewById(R.id.searching_progress_spinner));

        unregisterReceiver(mPairReceiver);
    }

    private void unbindDrawables(View view) {

        if(view == null)
            return;

        if (view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
            view.setBackgroundResource(0);
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            logi("onKeyDown() :: Cancel");
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
