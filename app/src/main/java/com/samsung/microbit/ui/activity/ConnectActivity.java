package com.samsung.microbit.ui.activity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.R;
import com.samsung.microbit.core.IPCMessageManager;
import com.samsung.microbit.core.PreviousDeviceList;
import com.samsung.microbit.core.Utils;
import com.samsung.microbit.model.ConnectedDevice;
import com.samsung.microbit.model.Constants;
import com.samsung.microbit.service.DfuService;
import com.samsung.microbit.service.IPCService;
import com.samsung.microbit.ui.BluetoothSwitch;
import com.samsung.microbit.ui.PopUp;
import com.samsung.microbit.ui.adapter.ConnectedDeviceAdapter;
import com.samsung.microbit.ui.adapter.LEDAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ConnectActivity extends Activity implements View.OnClickListener {

	private static boolean DISABLE_DEVICE_LIST = false;

	ConnectedDevice[]  mPrevDeviceArray;
	PreviousDeviceList mPrevDevList;
    ConnectedDevice    mCurrentDevice;

	private enum PAIRING_STATE {
		PAIRING_STATE_CONNECT_BUTTON,
		PAIRING_STATE_TIP,
		PAIRING_STATE_PATTERN_EMPTY,
		PAIRING_STATE_SEARCHING,
		PAIRING_STATE_ERROR,
		PAIRING_STATE_NEW_NAME
	};

	private static PAIRING_STATE mState = PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON;
	private static String mNewDeviceName;
	private static String mNewDeviceCode;
    private static String mNewDeviceAddress;

	// @formatter:off
    private static String deviceCodeArray[] = {
            "0","0","0","0","0",
            "0","0","0","0","0",
            "0","0","0","0","0",
            "0","0","0","0","0",
            "0","0","0","0","0"};

    private String deviceNameMapArray[] = {
            "T","A","T","A","T",
            "P","E","P","E","P",
            "G","I","G","I","G",
            "V","O","V","O","V",
            "Z","U","Z","U","Z"};
    // @formatter:on


	RelativeLayout mConnectButtonView;
	RelativeLayout mConnectTipView;
	RelativeLayout mNewDeviceView;
	RelativeLayout mConnectSearchView;
	RelativeLayout mBottomConnectButton;
	RelativeLayout mPrevDeviceView;

	List<ConnectedDevice> connectedDeviceList = new ArrayList<ConnectedDevice>();
	ConnectedDeviceAdapter connectedDeviceAdapter;
	private ListView lvConnectedDevice;

	private Handler mHandler;

	// Stops scanning after 10 seconds.
	private static final long SCAN_PERIOD = 15000;
	private Boolean isBLuetoothEnabled = false;

	final private int REQUEST_BT_ENABLE = 1;

	/*
	 * TODO : HACK 20150729
	 * A bit of a hack to make sure the scan finishes properly.  Needs top be done properly
	 * =================================================================
	 */
	private static ConnectActivity instance;
	private static BluetoothAdapter mBluetoothAdapter = null;
	private static volatile boolean mScanning = false;
	private static volatile boolean mPairing = false;
	//private Runnable scanFailedCallback;

	/*
	 * =================================================================
	 */

	/* *************************************************
	 * TODO setup to Handle BLE Notiifications
	 */
	static IntentFilter broadcastIntentFilter;

    ResultReceiver resultReceiver = new ResultReceiver(new Handler()) {

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            int phase = resultCode & 0x0ffff;

            if (phase == Constants.FLASHING_PAIRING_CODE_CHARACTERISTIC_RECIEVED) {
                logi("resultReceiver.onReceiveResult() :: FLASHING_PAIRING_CODE_CHARACTERISTIC_RECIEVED ");
				if (mPairing) {
					int pairing_code = resultData.getInt("pairing_code");
					logi("-----------> Pairing Code is " + pairing_code + " for device " + mNewDeviceCode.toUpperCase());
					PopUp.hide();
					ConnectedDevice newDev = new ConnectedDevice(mNewDeviceCode.toUpperCase(), mNewDeviceCode.toUpperCase(), false, mNewDeviceAddress, pairing_code);
					handlePairingSuccessful(newDev);
				}

            } else if ((phase & Constants.PAIRING_CONTROL_CODE_REQUESTED) != 0) {
                if ((phase & 0x0ff00) == 0) {
                    logi("resultReceiver.onReceiveResult() :: PAIRING_CONTROL_CODE_REQUESTED ");
                    if (mPairing) {
						PopUp.show(MBApp.getContext(),
								getString(R.string.pairing_phase2_msg), //message
								getString(R.string.pairing_title), //title
								R.drawable.flash_face, //image icon res id
								R.drawable.blue_btn,
								PopUp.TYPE_SPINNER_NOT_CANCELABLE, //type of popup.
								null,//override click listener for ok button
								null);//pass null to use default listener
					}
                } else {
                    logi("resultReceiver.onReceiveResult() :: Phase 1 not complete recieved ");
					if (mPairing) {
						cancelPairing();

						PopUp.show(MBApp.getContext(),
								getString(R.string.pairing_failed_message), //message
								getString(R.string.pairing_failed_title), //title
								R.drawable.error_face, //image icon res id
								R.drawable.red_btn,
								PopUp.TYPE_ALERT, //type of popup.
								null,//override click listener for ok button
								new View.OnClickListener() {
									@Override
									public void onClick(View v) {
										PopUp.hide();
										displayConnectScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
									}
								});//pass null to use default listener
					}
				}
            }
            super.onReceiveResult(resultCode, resultData);
        }
    };

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			int v = intent.getIntExtra(IPCMessageManager.BUNDLE_ERROR_CODE, 0);
			if (Constants.BLE_DISCONNECTED_FOR_FLASH == v){
				logi("Bluetooth disconnected for flashing. No need to display pop-up");
                handleBLENotification(context, intent, false);
				return;
			}
            handleBLENotification(context, intent, true);
			if (v != 0) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						PopUp.show(MBApp.getContext(),
							MBApp.getContext().getString(R.string.micro_bit_reset_msg),
							"",
							R.drawable.error_face, R.drawable.red_btn,
							PopUp.TYPE_ALERT, null, null);
					}
				});
			}
		}
	};

	private void handleBLENotification(Context context, Intent intent, boolean popupHide) {

        mCurrentDevice = Utils.getPairedMicrobit(this);
        logi("handleBLENotification() "+ mCurrentDevice.mPattern + "[" + mCurrentDevice.mStatus + "]");
		if (mPrevDevList == null) {
            mPrevDevList = PreviousDeviceList.getInstance(this);
		}
        mPrevDeviceArray = mPrevDevList.loadPrevMicrobits();

		if (mCurrentDevice.mPattern != null && mCurrentDevice.mPattern.equals(mPrevDeviceArray[0].mPattern)) {
            mPrevDeviceArray[0].mStatus = mCurrentDevice.mStatus;
            mPrevDevList.changeMicrobitState(0, mPrevDeviceArray[0], mPrevDeviceArray[0].mStatus, true);
			populateConnectedDeviceList(false);

		}

        if(popupHide)
		    PopUp.hide();
	}

	// *************************************************

	// DEBUG
	protected boolean debug = true;
	protected String TAG = "ConnectActivity";

	protected void logi(String message) {
        Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
	}

	@Override
	public void onResume() {
		super.onResume();
		MBApp.setContext(this);
        populateConnectedDeviceList(false);
	}

	public ConnectActivity() {
		logi("ConnectActivity() ::");
		instance = this;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		logi("onCreate() ::");

		super.onCreate(savedInstanceState);

		MBApp.setContext(this);

		if (broadcastIntentFilter == null) {
			broadcastIntentFilter = new IntentFilter(IPCService.INTENT_BLE_NOTIFICATION);
			LocalBroadcastManager.getInstance(MBApp.getContext()).registerReceiver(broadcastReceiver, broadcastIntentFilter);
		}

		// ************************************************
		//Remove title barproject_list
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.activity_connect);

		/*
	 	* TODO : Part of HACK 20150729
	 	* =================================================================
	 	*/

		if (mBluetoothAdapter == null) {
			final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			logi("onCreate() :: mBluetoothAdapter == null");
			mBluetoothAdapter = bluetoothManager.getAdapter();
			// Checks if Bluetooth is supported on the device.
			if (mBluetoothAdapter == null) {
				Toast.makeText(this.getApplicationContext(), R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
				finish();
				return;
			}
		}

		/*
		 * =================================================================
		 */

		mHandler = new Handler(Looper.getMainLooper());
		if (mPrevDevList == null) {
            mPrevDevList = PreviousDeviceList.getInstance(this);
            mPrevDeviceArray = mPrevDevList.loadPrevMicrobits();
		}
		//mPrevDeviceArray = new ConnectedDevice[PREVIOUS_DEVICES_MAX];
		lvConnectedDevice = (ListView) findViewById(R.id.connectedDeviceList);
		populateConnectedDeviceList(false);

        mBottomConnectButton = (RelativeLayout) findViewById(R.id.bottomConnectButton);
        mPrevDeviceView = (RelativeLayout) findViewById(R.id.prevDeviceView);

        mConnectButtonView = (RelativeLayout) findViewById(R.id.connectButtonView);
        mConnectTipView = (RelativeLayout) findViewById(R.id.connectTipView);
        mNewDeviceView = (RelativeLayout) findViewById(R.id.newDeviceView);
		mConnectSearchView = (RelativeLayout) findViewById(R.id.connectSearchView);

		displayConnectScreen(mState);
		findViewById(R.id.connectButton).setOnClickListener(this);
		findViewById(R.id.cancel_tip_button).setOnClickListener(this);
		findViewById(R.id.ok_name_button).setOnClickListener(this);
		findViewById(R.id.cancel_name_button).setOnClickListener(this);
		findViewById(R.id.cancel_search_button).setOnClickListener(this);

		//Animation
		WebView animation = (WebView) findViewById(R.id.animationwebView);
		animation.setBackgroundColor(Color.TRANSPARENT);
		animation.loadUrl("file:///android_asset/htmls/animation.html");
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.d("Microbit", "onActivityResult");

		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_BT_ENABLE && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, "You must enable Bluetooth to continue", Toast.LENGTH_LONG).show();
		} else {
			isBLuetoothEnabled = true;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void displayLedGrid() {
		GridView gridview = (GridView) findViewById(R.id.gridview);
		gridview.setAdapter(new LEDAdapter(this, deviceCodeArray));
		gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v,
									int position, long id) {
				if (mState != PAIRING_STATE.PAIRING_STATE_NEW_NAME) {

					if ((findViewById(R.id.ok_name_button).getVisibility() != View.VISIBLE)) {
						findViewById(R.id.ok_name_button).setVisibility(View.VISIBLE);
					}

					boolean isOn = toggleLED((ImageView) v, position);
					setCol(parent, position, isOn);
					//Toast.makeText(MBApp.getContext(), "LED Clicked: " + position, Toast.LENGTH_SHORT).show();

					if (!Arrays.asList(deviceCodeArray).contains("1")) {
						findViewById(R.id.ok_name_button).setVisibility(View.INVISIBLE);
					}
				}
				//TODO KEEP TRACK OF ALL LED STATUS AND TOGGLE COLOR

			}
        });

		if (!Arrays.asList(deviceCodeArray).contains("1")) {
			findViewById(R.id.ok_name_button).setVisibility(View.INVISIBLE);
		} else
			findViewById(R.id.ok_name_button).setVisibility(View.VISIBLE);
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

	private void populateConnectedDeviceList(boolean isupdate) {
		connectedDeviceList.clear();
		int numOfPreviousItems = 0;
		/* Get Previous connected devices */
        mPrevDeviceArray = mPrevDevList.loadPrevMicrobits();
		if (mPrevDevList != null)
			numOfPreviousItems = mPrevDevList.size();

        if((numOfPreviousItems > 0) && (mCurrentDevice!=null) &&
                (mPrevDeviceArray[0].mPattern.equals(mCurrentDevice.mPattern)))
        {
            mPrevDeviceArray[0].mStatus=mCurrentDevice.mStatus;
        }

		for (int i = 0; i < numOfPreviousItems; i++) {
			connectedDeviceList.add(mPrevDeviceArray[i]);
		}
		for (int i = numOfPreviousItems; i < 1; i++) {
			connectedDeviceList.add(new ConnectedDevice(null, null, false, null,0));
		}


		if (isupdate) {
			connectedDeviceAdapter.updateAdapter(connectedDeviceList);

		} else {
			connectedDeviceAdapter = new ConnectedDeviceAdapter(this, connectedDeviceList);
			lvConnectedDevice.setAdapter(connectedDeviceAdapter);
		}

	}

	public static boolean disableListView() {
		return DISABLE_DEVICE_LIST;
	}

    private boolean isPortraitMode() {
        return (mBottomConnectButton != null);
	}

	private void displayConnectScreen(PAIRING_STATE gotoState) {
        mConnectButtonView.setVisibility(View.GONE);
        mConnectTipView.setVisibility(View.GONE);
        mNewDeviceView.setVisibility(View.GONE);
        mConnectSearchView.setVisibility(View.GONE);

		Log.d("Microbit", "********** Connect: state from " + mState + " to " + gotoState);
        mState = gotoState;

		if ((gotoState == PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON) ||
                (gotoState == PAIRING_STATE.PAIRING_STATE_ERROR))
			DISABLE_DEVICE_LIST = false;
		else
			DISABLE_DEVICE_LIST = true;

		if(isPortraitMode() && (disableListView()))
            mPrevDeviceView.setVisibility(View.GONE);
		else {
			populateConnectedDeviceList(true);
            mPrevDeviceView.setVisibility(View.VISIBLE);
		}

		switch (gotoState) {
			case PAIRING_STATE_CONNECT_BUTTON:
			case PAIRING_STATE_ERROR:
                mConnectButtonView.setVisibility(View.VISIBLE);
				lvConnectedDevice.setEnabled(true);
				Arrays.fill(deviceCodeArray, "0");
				findViewById(R.id.gridview).setEnabled(true);
                mNewDeviceName = "";
                mNewDeviceCode = "";
				break;

			case PAIRING_STATE_TIP:
                mConnectTipView.setVisibility(View.VISIBLE);
				findViewById(R.id.ok_connect_button).setOnClickListener(this);
				break;

			case PAIRING_STATE_PATTERN_EMPTY:
				findViewById(R.id.gridview).setEnabled(true);
				findViewById(R.id.connectedDeviceList).setClickable(true);
                mNewDeviceView.setVisibility(View.VISIBLE);
				findViewById(R.id.cancel_name_button).setVisibility(View.VISIBLE);
				findViewById(R.id.newDeviceTxt).setVisibility(View.VISIBLE);
				findViewById(R.id.ok_name_button).setVisibility(View.GONE);
                findViewById(R.id.nameNewButton).setVisibility(View.GONE);
                findViewById(R.id.nameNewEdit).setVisibility(View.GONE);
				displayLedGrid();
				break;

			case PAIRING_STATE_NEW_NAME:
				findViewById(R.id.gridview).setEnabled(false);
				findViewById(R.id.connectedDeviceList).setClickable(false);
                mNewDeviceView.setVisibility(View.VISIBLE);
				Button newNameButton = (Button) findViewById(R.id.nameNewButton);
                EditText newNameEdit = (EditText) findViewById(R.id.nameNewEdit);
                newNameButton.setTag(R.id.textEdit, newNameEdit);
                newNameButton.setOnClickListener(microbitRenameClickListener);
                newNameEdit.setTag(R.id.editbutton, newNameButton);
                newNameEdit.setOnEditorActionListener(editorOnActionListener);
                if((mPrevDeviceArray == null) || (mPrevDeviceArray[0].mName == null) || (mPrevDeviceArray[0].mName.equals(""))) {
                    newNameButton.setText(mNewDeviceCode);
                    newNameEdit.setText(mNewDeviceCode);
                }else {
                    newNameButton.setText(mPrevDeviceArray[0].mName);
                    newNameEdit.setText(mPrevDeviceArray[0].mName);
                }
                newNameButton.setVisibility(View.VISIBLE);
                newNameEdit.setVisibility(View.INVISIBLE);
				findViewById(R.id.ok_name_button).setVisibility(View.VISIBLE);
				findViewById(R.id.cancel_name_button).setVisibility(View.VISIBLE);
				displayLedGrid();
				break;

			case PAIRING_STATE_SEARCHING:
                mConnectSearchView.setVisibility(View.VISIBLE);
				break;
		}
	}

    private TextView.OnEditorActionListener editorOnActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            boolean handled = true;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dismissKeyBoard(v, true, true);
            } else if (actionId == -1) {
                dismissKeyBoard(v, true, false);
            }
            return handled;
        }
    };
    private void dismissKeyBoard(View v, boolean hide,boolean done) {
        if (done) {
            EditText ed = (EditText) v;
            String newName = ed.getText().toString().trim();
			if (newName.isEmpty()) {
				ed.setText("");
				ed.setError(getString(R.string.name_empty_error));
            } else {
				hideKeyboard(v);
                mPrevDeviceArray[0].mName = newName;
                mPrevDevList.changeMicrobitName(0, mPrevDeviceArray[0]);
                populateConnectedDeviceList(true);
                Button newNameButton = (Button) findViewById(R.id.nameNewButton);
                newNameButton.setText(newName);
                newNameButton.setVisibility(View.VISIBLE);
                ed.setVisibility(View.INVISIBLE);
            }
        }
    }
    private View.OnClickListener microbitRenameClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Button newNameButton = (Button) findViewById(R.id.nameNewButton);
            EditText newNameEdit = (EditText) findViewById(R.id.nameNewEdit);
            newNameEdit.setVisibility(View.VISIBLE);
            newNameButton.setVisibility(View.INVISIBLE);
            newNameEdit.setText(mNewDeviceCode);
            newNameEdit.setSelection(mNewDeviceCode.length());
            newNameEdit.requestFocus();
            showKeyboard();
        }
    };

	public void onClick(final View v) {
		int pos;

		switch (v.getId()) {
			case R.id.connectButton:
				if (debug) logi("onClick() :: connectButton");
                if (!BluetoothSwitch.getInstance().checkBluetoothAndStart()){
                    return;
                }

				if (mBottomConnectButton != null) {
                    mPrevDeviceView.setVisibility(View.GONE);
				}

				if (mConnectButtonView != null) {
					displayConnectScreen(PAIRING_STATE.PAIRING_STATE_TIP);
				}
				break;

			case R.id.ok_connect_button:
				if (debug) logi("onClick() :: ok_connect_button");
				displayConnectScreen(PAIRING_STATE.PAIRING_STATE_PATTERN_EMPTY);
				break;

			case R.id.ok_name_button:
				if (debug) logi("onClick() :: ok_name_button");
				if (mState == PAIRING_STATE.PAIRING_STATE_PATTERN_EMPTY) {
					generateName();
					scanLeDevice(true);
					displayConnectScreen(PAIRING_STATE.PAIRING_STATE_SEARCHING);
					break;
				}

				EditText editText = (EditText) findViewById(R.id.nameNewEdit);
				String newname = editText.getText().toString().trim();
				if (newname.isEmpty()) {
					editText.setText("");
					editText.setError(getString(R.string.name_empty_error));
				} else {
					hideKeyboard(editText);
                    mPrevDeviceArray[0].mName = newname;
                    mPrevDevList.changeMicrobitName(0, mPrevDeviceArray[0]);
					displayConnectScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
				}
				break;

			case R.id.cancel_tip_button:
				if (debug) logi("onClick() :: cancel_tip_button");
				displayConnectScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
				break;

			case R.id.cancel_name_button:
				if (debug) logi("onClick() :: cancel_name_button");
				cancelPairing();
				displayConnectScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
				break;

			case R.id.cancel_search_button:
				if (debug) logi("onClick() :: cancel_search_button");
                scanLeDevice(false);
				cancelPairing();
				displayConnectScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
				break;

			case R.id.connectBtn:
				if (debug) logi("onClick() :: connectBtn");
                if (!BluetoothSwitch.getInstance().checkBluetoothAndStart()){
                    return;
                }

				pos = (Integer) v.getTag();
				boolean currentState = mPrevDeviceArray[pos].mStatus;
				if (!currentState) {
					PopUp.show(MBApp.getContext(),
						getString(R.string.init_connection),
						"",
						R.drawable.message_face, R.drawable.blue_btn,
						PopUp.TYPE_SPINNER,
						null, null);
                    mPrevDevList.changeMicrobitState(pos, mPrevDeviceArray[pos], true, false);
                    IPCService.getInstance().bleConnect();
				} else {
                    mPrevDeviceArray[pos].mStatus = !currentState;
                    mPrevDevList.changeMicrobitState(pos, mPrevDeviceArray[pos], false, false);
                    populateConnectedDeviceList(true);
                }
				break;

			case R.id.deleteBtn:
				if (debug) logi("onClick() :: deleteBtn");
				pos = (Integer) v.getTag();
				handleDeleteMicrobit(pos);
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

    public void showKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
	}

	private void handleDeleteMicrobit(final int pos) {
		PopUp.show(this,
				getString(R.string.deleteMicrobitMessage), //message
				getString(R.string.deleteMicrobitTitle), //title
				R.drawable.delete_project, R.drawable.red_btn,
				PopUp.TYPE_CHOICE, //type of popup.
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						PopUp.hide();
						mPrevDevList.removeMicrobit(pos);
						populateConnectedDeviceList(true);
					}
				},//override click listener for ok button
				null);//pass null to use default listener

	}

	private void handleResetAll() {
		Arrays.fill(deviceCodeArray,"0");
		scanLeDevice(false);
		cancelPairing();

		if (!isPortraitMode()) {
			mState = PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON;
			finish();
		} else if  (isPortraitMode() && mState == PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON) {
			finish();
		} else {
			displayConnectScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
		}
	}

	private void handlePairingFailed() {

		if (debug) logi("handlePairingFailed() :: Start");
		mPairing = false;

		displayConnectScreen(PAIRING_STATE.PAIRING_STATE_ERROR);

        PopUp.show(this,
                getString(R.string.pairingErrorMessage), //message
                getString(R.string.pairingErrorTitle), //title
                R.drawable.error_face, //image icon res id
                R.drawable.red_btn,
                PopUp.TYPE_ALERT, //type of popup.
                null,//override click listener for ok button
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopUp.hide();
                        displayConnectScreen(PAIRING_STATE.PAIRING_STATE_CONNECT_BUTTON);
                    }
				});//pass null to use default listener

	}

    private void handlePairingSuccessful(final ConnectedDevice newDev) {
		mPairing = false;

		final Runnable task = new Runnable() {

			@Override
			public void run() {

				getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
				//= new ConnectedDevice(null, mNewDeviceCode, true, device.getAddress() );
				int oldId = mPrevDevList.checkDuplicateMicrobit(newDev);
                mPrevDevList.addMicrobit(newDev, oldId);
				populateConnectedDeviceList(true);

				if (debug) logi("handlePairingSuccessful() :: sending intent to BLEService.class");

				displayConnectScreen(PAIRING_STATE.PAIRING_STATE_NEW_NAME);
            }
		};

        new Handler(Looper.getMainLooper()).post(task);
    }

    private void startPairing(String deviceAddress) {

        logi("###>>>>>>>>>>>>>>>>>>>>> startPairing");
		mPairing = true;
		final Intent service = new Intent(this, DfuService.class);
        service.putExtra(DfuService.EXTRA_DEVICE_ADDRESS, deviceAddress);
        service.putExtra(DfuService.EXTRA_KEEP_BOND, false);
        service.putExtra(DfuService.INTENT_RESULT_RECEIVER, resultReceiver);
        service.putExtra(DfuService.INTENT_REQUESTED_PHASE, 1);
        startService(service);
    }

    private void cancelPairing() {
        logi("###>>>>>>>>>>>>>>>>>>>>> cancelPairing");
        scanLeDevice(false);//TODO: is it really needed?

		if (mPairing) {
            final Intent abortIntent = new Intent(this, DfuService.class);
            abortIntent.putExtra(DfuService.EXTRA_ACTION, DfuService.ACTION_ABORT);
			startService(abortIntent);
			mPairing = false;
        }
    }

	/*
 	* TODO : Part of HACK 20150729
 	* =================================================================
 	*/
	private void scanLeDevice(final boolean enable) {

		if (debug) logi("scanLeDevice() :: enable = " + enable);

		if (enable) {
			if (!mScanning && !mPairing) {
				// Stops scanning after a pre-defined scan period.
				mScanning = true;
				mHandler.postDelayed(scanFailedCallback, SCAN_PERIOD);
				mBluetoothAdapter.startLeScan(mLeScanCallback);
			}
		} else {
			if (mScanning) {
				mScanning = false;
				mHandler.removeCallbacks(scanFailedCallback);
				mBluetoothAdapter.stopLeScan(mLeScanCallback);
			}
		}
	}

	private static Runnable scanFailedCallback = new Runnable() {
		@Override
		public void run() {
			ConnectActivity.instance.scanFailedCallbackImpl();
		}
	};

	private void scanFailedCallbackImpl() {
		if (mPairing) {
			return;
		}

		scanLeDevice(false);
	}

	// Device scan callback.
	private static BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
			ConnectActivity.instance.onLeScan(device, rssi, scanRecord);
		}
	};

	/*
	 * =================================================================
	 */

	public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

		if (debug) logi("mLeScanCallback.onLeScan() :: Start");

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
			if (debug) logi("mLeScanCallback.onLeScan() ::   Cannot Compare "+device.getAddress() +" " + rssi + " "+scanRecord.toString());
		} else {
			String s = device.getName().toLowerCase();
			if (mNewDeviceName.toLowerCase().equals(s)) {

				if (debug) logi("mLeScanCallback.onLeScan() ::   device.getName() == " + device.getName().toLowerCase() + " " + device.getAddress());
				// Stop scanning as device is found.
				scanLeDevice(false);
                mNewDeviceAddress = device.getAddress();
                startPairing(mNewDeviceAddress);
			} else {
				if (debug) logi("mLeScanCallback.onLeScan() ::   non-matching - deviceName == " + mNewDeviceName.toLowerCase());
				if (debug)
					logi("mLeScanCallback.onLeScan() ::   non-matching found - device.getName() == " + device.getName().toLowerCase());
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
        mConnectButtonView.setVisibility(View.GONE);
		mConnectTipView.setVisibility(View.GONE);
		mNewDeviceView.setVisibility(View.GONE);
        mConnectSearchView.setVisibility(View.GONE);
	}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
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
	}}
