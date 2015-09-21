package com.samsung.microbit.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Spannable;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.R;
import com.samsung.microbit.core.IPCMessageManager;
import com.samsung.microbit.core.Utils;
import com.samsung.microbit.model.ConnectedDevice;
import com.samsung.microbit.model.Constants;
import com.samsung.microbit.model.Project;
import com.samsung.microbit.service.DfuService;
import com.samsung.microbit.service.IPCService;
import com.samsung.microbit.ui.BluetoothSwitch;
import com.samsung.microbit.ui.PopUp;
import com.samsung.microbit.ui.adapter.ProjectAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class ProjectActivity extends Activity implements View.OnClickListener {

	List<Project> projectList = new ArrayList<Project>();
	ProjectAdapter projectAdapter;
	private ListView projectListView;
	private HashMap<String, String> prettyFileNameMap = new HashMap<String, String>();

	Project programToSend;

	private DFUResultReceiver dfuResultReceiver;
	private int projectListSortOrder = 0;

	// DEBUG
	protected boolean debug = true;
	protected String TAG = "ProjectActivity";

    private static FLASHING_STATE mFlashState = FLASHING_STATE.FLASH_STATE_NONE;

    private enum FLASHING_STATE {
        FLASH_STATE_NONE,
        FLASH_STATE_FIND_DEVICE,
        FLASH_STATE_VERIFY_DEVICE,
        FLASH_STATE_WAIT_DEVICE_REBOOT,
        FLASH_STATE_INIT_DEVICE,
        FLASH_STATE_PROGRESS
    };



	protected void logi(String message) {
		if (debug) {
			Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
		}
	}

	/* *************************************************
	 * TODO setup to Handle BLE Notiifications
	 */
	IntentFilter broadcastIntentFilter;
	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

            int v = intent.getIntExtra(IPCMessageManager.BUNDLE_ERROR_CODE, 0);

            logi(" broadcastReceiver ---- v= " + v);
            if (Constants.BLE_DISCONNECTED_FOR_FLASH == v){
                logi("Bluetooth disconnected for flashing. No need to display pop-up");
                handleBLENotification(context, intent, false);
                initiateFlashing(programToSend);
                return;
            }
            handleBLENotification(context, intent, true);
			if (v != 0 ) {
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

	private void handleBLENotification(Context context, Intent intent, boolean hide) {

		logi("handleBLENotification()");
        final boolean popupHide = hide;
		runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setConnectedDeviceText();
                if(popupHide)
                PopUp.hide();
            }
        });

		int cause = intent.getIntExtra(IPCService.NOTIFICATION_CAUSE, 0);
		if (cause == IPCMessageManager.IPC_NOTIFICATION_GATT_DISCONNECTED) {
//			if (isDisconnectedForFlash) {
//				startFlashingPhase1();
//			}
		}
	}

    private void setFlashState(FLASHING_STATE newState)
    {
        logi("Flash state old - " + mFlashState + " new - " + newState);
        mFlashState=newState;
    }

	@Override
	public void onResume() {
		super.onResume();
		MBApp.setContext(this);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		logi("onCreate() :: ");
		MBApp.setContext(this);

		//Remove title bar
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_projects);

		boolean showSortMenu = false;
		try {
			showSortMenu = getResources().getBoolean(R.bool.showSortMenu);
		} catch (Exception e) {
		}

		Spinner sortList = (Spinner) findViewById(R.id.sortProjects);
		if (showSortMenu) {

			sortList.setPrompt("Sort by");
			ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(this, R.array.projectListSortOrder,
				android.R.layout.simple_spinner_item);

			sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			sortList.setAdapter(sortAdapter);
			sortList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					projectListSortOrder = position;
					projectListSortOrderChanged();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});
		}

		projectListView = (ListView) findViewById(R.id.projectListView);
		updateProjectsListSortOrder(true);

		/* *************************************************
		 * TODO setup to Handle BLE Notiification
		 */
		if (broadcastIntentFilter == null) {
			broadcastIntentFilter = new IntentFilter(IPCService.INTENT_BLE_NOTIFICATION);
			LocalBroadcastManager.getInstance(MBApp.getContext()).registerReceiver(broadcastReceiver, broadcastIntentFilter);
		}
		setConnectedDeviceText();
		String fileToDownload = getIntent().getStringExtra("download_file");

		if (fileToDownload != null) {
			programToSend = new Project(fileToDownload, Constants.HEX_FILE_DIR + "/" + fileToDownload, 0, null, false);
			adviceOnMicrobitState(programToSend);
        }
	}

	private void setConnectedDeviceText() {

        TextView connectedIndicatorText = (TextView) findViewById(R.id.connectedIndicatorText);
        TextView deviceName1 = (TextView) findViewById(R.id.deviceName);
        TextView deviceName2 = (TextView) findViewById(R.id.deviceName2);
        ImageButton connectedIndicatorIcon = (ImageButton) findViewById(R.id.connectedIndicatorIcon);

        if (connectedIndicatorIcon == null || connectedIndicatorText == null)
            return;

        int startIndex = 0;
        Spannable span = null;
        ConnectedDevice device = Utils.getPairedMicrobit(this);
        if (!device.mStatus) {
            connectedIndicatorIcon.setImageResource(R.drawable.disconnect_device);
            connectedIndicatorIcon.setBackground(MBApp.getContext().getResources().getDrawable(R.drawable.project_disconnect_btn));
            connectedIndicatorText.setText(getString(R.string.not_connected));
            if (deviceName1 != null && deviceName2 != null) {
                //Mobile Device.. 2 lines of display
                if (device.mName != null)
                    deviceName1.setText(device.mName);
                if (device.mPattern != null)
                    deviceName2.setText("(" + device.mPattern + ")");
            } else if (deviceName1 != null) {
                if (device.mName != null)
                    deviceName1.setText(device.mName + " (" + device.mPattern + ")");
            }
        } else {
            connectedIndicatorIcon.setImageResource(R.drawable.device_connected);
            connectedIndicatorIcon.setBackground(MBApp.getContext().getResources().getDrawable(R.drawable.project_connect_btn));
            connectedIndicatorText.setText(getString(R.string.connected_to));
            if (deviceName1 != null && deviceName2 != null) {
                //Mobile Device.. 2 lines of display
                if (device.mName != null)
                    deviceName1.setText(device.mName);
                if (device.mPattern != null)
                    deviceName2.setText("(" + device.mPattern + ")");
            } else if (deviceName1 != null) {
                if (device.mName != null)
                    deviceName1.setText(device.mName + " (" + device.mPattern + ")");
            }
        }
    }

	public void renameFile(String filePath, String newName) {

		int rc = Utils.renameFile(filePath, newName);
		if (rc != 0) {
			AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle("Alert");

			String message = "OOPS!";
			switch (rc) {
				case 1:
					message = "Cannot rename, destination file already exists.";
					break;

				case 2:
					message = "Cannot rename, source file not exist.";
					break;

				case 3:
					message = "Rename opertaion failed.";
					break;
			}

			alertDialog.setMessage(message);
			alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});

			alertDialog.show();
		} else {
			updateProjectsListSortOrder(true);
		}
	}

	void updateProjectsListSortOrder(boolean reReadFS) {

		TextView emptyText = (TextView) findViewById(android.R.id.empty);
		projectListView.setEmptyView(emptyText);
		if (reReadFS) {
			projectList.clear();
			Utils.findProgramsAndPopulate(prettyFileNameMap, projectList);
		}

		projectListSortOrder = Utils.getListOrderPrefs(this);
		int sortBy = (projectListSortOrder >> 1);
		int sortOrder = projectListSortOrder & 0x01;
		Utils.sortProjectList(projectList, sortBy, sortOrder);

		projectAdapter = new ProjectAdapter(this, projectList);
		projectListView.setAdapter(projectAdapter);
		projectListView.setItemsCanFocus(true);
	}

	void projectListSortOrderChanged() {
		Utils.setListOrderPrefs(this, projectListSortOrder);
		updateProjectsListSortOrder(true);
	}

	public void onClick(final View v) {

		int pos;
		Intent intent;

		switch (v.getId()) {
			case R.id.createProject:
				intent = new Intent(this, TouchDevActivity.class);
				intent.putExtra(Constants.URL, getString(R.string.touchDevURLNew));
				startActivity(intent);
				finish();
				break;

			case R.id.backBtn:
				finish();
				break;

			case R.id.sendBtn:
				if (!BluetoothSwitch.getInstance().checkBluetoothAndStart()){
					return;
				}
                if(mFlashState != FLASHING_STATE.FLASH_STATE_NONE)
                {
                     // Another download session is in progress
                    PopUp.show(MBApp.getContext(),
                            getString(R.string.multple_flashing_session_msg),
                            "",
                            R.drawable.flash_face, R.drawable.blue_btn,
                            PopUp.TYPE_ALERT,
                            null, null);
                }  else {
                    pos = (Integer) v.getTag();
                    Project toSend = (Project) projectAdapter.getItem(pos);
                    adviceOnMicrobitState(toSend);
                }
				break;

			case R.id.connectedIndicatorIcon:
                if (!BluetoothSwitch.getInstance().checkBluetoothAndStart()){
                    return;
                }

				ConnectedDevice connectedDevice = Utils.getPairedMicrobit(this);
				if (connectedDevice.mPattern != null) {
					if (connectedDevice.mStatus) {
						IPCService.getInstance().bleDisconnect();
					} else {

						PopUp.show(MBApp.getContext(),
							getString(R.string.init_connection),
							"",
							R.drawable.flash_face, R.drawable.blue_btn,
							PopUp.TYPE_SPINNER,
							null, null);

						IPCService.getInstance().bleConnect();
					}
				}

				break;
		}
	}

	private void adviceOnMicrobitState(final Project toSend) {
        ConnectedDevice currentMicrobit = Utils.getPairedMicrobit(this);
        if (currentMicrobit.mPattern == null ) {
            PopUp.show(MBApp.getContext(),
                    getString(R.string.flashing_failed_no_microbit), //message
                    getString(R.string.flashing_error), //title
                    R.drawable.error_face,//image icon res id
                    R.drawable.red_btn,
                    PopUp.TYPE_ALERT, //type of popup.
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            PopUp.hide();
                        }
                    },//override click listener for ok button
                    null);//pass null to use default listeneronClick
        }
        else {
            PopUp.show(MBApp.getContext(),
                    getString(R.string.flash_start_message, currentMicrobit.mName) , //message
                    getString(R.string.flashing_title), //title
                    R.drawable.flash_face, R.drawable.blue_btn, //image icon res id
                    PopUp.TYPE_CHOICE, //type of popup.
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ConnectedDevice currentMicrobit = Utils.getPairedMicrobit(MBApp.getContext());
                            PopUp.hide();
                            if (currentMicrobit.mStatus == true)
                            {
                                programToSend = toSend;
                                IPCService.getInstance().bleDisconnectForFlash();
                            } else
                            initiateFlashing(toSend);
                        }
                    },//override click listener for ok button
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            PopUp.hide();
                        }
                    });//pass null to use default listeneronClick
	    }
	}

	protected void initiateFlashing(Project toSend) {

		if (dfuResultReceiver != null) {
			LocalBroadcastManager.getInstance(MBApp.getContext()).unregisterReceiver(dfuResultReceiver);
			dfuResultReceiver = null;
		}
		programToSend = toSend;
        setFlashState(FLASHING_STATE.FLASH_STATE_FIND_DEVICE);
        registerCallbacksForFlashing();
        startFlashing();
	}

	protected void startFlashing() {
        logi(">>>>>>>>>>>>>>>>>>> startFlashing called  >>>>>>>>>>>>>>>>>>>  ");
		ConnectedDevice currentMicrobit = Utils.getPairedMicrobit(this);
		final Intent service = new Intent(ProjectActivity.this, DfuService.class);
		service.putExtra(DfuService.EXTRA_DEVICE_ADDRESS, currentMicrobit.mAddress);
		service.putExtra(DfuService.EXTRA_DEVICE_NAME, currentMicrobit.mPattern);
		service.putExtra(DfuService.EXTRA_DEVICE_PAIR_CODE, currentMicrobit.mPairingCode);
		service.putExtra(DfuService.EXTRA_FILE_MIME_TYPE, DfuService.MIME_TYPE_OCTET_STREAM);
		service.putExtra(DfuService.EXTRA_FILE_PATH, programToSend.filePath); // a path or URI must be provided.
		service.putExtra(DfuService.EXTRA_KEEP_BOND, false);
		service.putExtra(DfuService.INTENT_RESULT_RECEIVER, resultReceiver);
		service.putExtra(DfuService.INTENT_REQUESTED_PHASE, 2);
		startService(service);
	}

	private void registerCallbacksForFlashing() {
		IntentFilter filter = new IntentFilter(DfuService.BROADCAST_PROGRESS);
		IntentFilter filter1 = new IntentFilter(DfuService.BROADCAST_ERROR);
		dfuResultReceiver = new DFUResultReceiver();
		LocalBroadcastManager.getInstance(MBApp.getContext()).registerReceiver(dfuResultReceiver, filter);
		LocalBroadcastManager.getInstance(MBApp.getContext()).registerReceiver(dfuResultReceiver, filter1);
	}

	/**
	 *
	 */
	ResultReceiver resultReceiver = new ResultReceiver(new Handler()) {

		@Override
		protected void onReceiveResult(int resultCode, Bundle resultData) {

			int phase = resultCode & 0x0ffff;

            logi("resultReceiver.onReceiveResult() :: Phase = " + phase + " resultCode = " + resultCode);
			super.onReceiveResult(resultCode, resultData);
		}
	};


    View.OnClickListener popupOkHandler = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logi("popupOkHandler");
        }
    };

	class DFUResultReceiver extends BroadcastReceiver {

		private boolean isCompleted = false;
		private boolean inInit = false;
		private boolean inProgress = false;

		@Override
		public void onReceive(Context context, Intent intent) {
			String message = "Broadcast intent detected " + intent.getAction();
			logi("DFUResultReceiver.onReceive :: " + message);
			if (intent.getAction() == DfuService.BROADCAST_PROGRESS) {

				int state = intent.getIntExtra(DfuService.EXTRA_DATA, 0);
				if (state < 0) {
					logi("DFUResultReceiver.onReceive :: state -- " + state);
					switch (state) {
                        case DfuService.PROGRESS_STARTING:
                            setFlashState( FLASHING_STATE.FLASH_STATE_INIT_DEVICE);
                            PopUp.show(MBApp.getContext(),
                                    getString(R.string.dfu_status_starting_msg), //message
                                    getString(R.string.send_project), //title
                                    R.drawable.flash_face, R.drawable.blue_btn,
                                    PopUp.TYPE_SPINNER_NOT_CANCELABLE, //type of popup.
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            //Do nothing. As this is non-cancellable pop-up

                                        }
                                    },//override click listener for ok button
                                    null);//pass null to use default listener
                            break;
						case DfuService.PROGRESS_COMPLETED:
							if (!isCompleted) {
								// todo progress bar dismiss
								PopUp.hide();
                                setFlashState( FLASHING_STATE.FLASH_STATE_NONE);
								LocalBroadcastManager.getInstance(MBApp.getContext()).unregisterReceiver(dfuResultReceiver);
								dfuResultReceiver = null;
								PopUp.show(MBApp.getContext(),
									getString(R.string.flashing_success_message), //message
									getString(R.string.flashing_success_title), //title
									R.drawable.message_face, R.drawable.blue_btn,
									PopUp.TYPE_ALERT, //type of popup.
									popupOkHandler,//override click listener for ok button
                                    popupOkHandler);//pass null to use default listener
							}

							isCompleted = true;
							inInit = false;
							inProgress = false;

							break;

						case DfuService.PROGRESS_DISCONNECTING:
							if ((isCompleted == false) && (inProgress == false))// Disconnecting event because of error
							{
								String error_message = "Error Code - [" + intent.getIntExtra(DfuService.EXTRA_DATA, 0)
									+ "] \n Error Type - [" + intent.getIntExtra(DfuService.EXTRA_ERROR_TYPE, 0) + "]";

								logi(error_message);
                                setFlashState( FLASHING_STATE.FLASH_STATE_NONE);
								//PopUp.hide();
								PopUp.show(MBApp.getContext(),
									error_message, //message
									getString(R.string.flashing_failed_title), //title
									R.drawable.error_face, R.drawable.red_btn,
									PopUp.TYPE_ALERT, //type of popup.
                                    popupOkHandler,//override click listener for ok button
                                    popupOkHandler);//pass null to use default listener

								LocalBroadcastManager.getInstance(MBApp.getContext()).unregisterReceiver(dfuResultReceiver);
							}

							break;

						case DfuService.PROGRESS_CONNECTING:
							if ((!inInit) && (!isCompleted)) {
                                setFlashState(FLASHING_STATE.FLASH_STATE_INIT_DEVICE);
								PopUp.show(MBApp.getContext(),
									getString(R.string.init_connection), //message
									getString(R.string.send_project), //title
									R.drawable.flash_face, R.drawable.blue_btn,
									PopUp.TYPE_SPINNER_NOT_CANCELABLE, //type of popup.
									new View.OnClickListener() {
										@Override
										public void onClick(View v) {
                                            //Do nothing. As this is non-cancellable pop-up
										}
									},//override click listener for ok button
									null);//pass null to use default listener
							}

							inInit = true;
							isCompleted = false;
							break;
                        case DfuService.PROGRESS_VALIDATING:
                            setFlashState(FLASHING_STATE.FLASH_STATE_VERIFY_DEVICE);
                            PopUp.show(MBApp.getContext(),
                                    getString(R.string.validating_microbit), //message
                                    getString(R.string.send_project), //title
                                    R.drawable.flash_face, R.drawable.blue_btn,
                                    PopUp.TYPE_SPINNER_NOT_CANCELABLE, //type of popup.
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            //Do nothing. As this is non-cancellable pop-up

                                        }
                                    },//override click listener for ok button
                                    null);//pass null to use default listener
                            break;
						case DfuService.PROGRESS_WAITING_REBOOT:
                            setFlashState(FLASHING_STATE.FLASH_STATE_WAIT_DEVICE_REBOOT);
                            PopUp.show(MBApp.getContext(),
                                    getString(R.string.waiting_reboot), //message
                                    getString(R.string.send_project), //title
                                    R.drawable.flash_face, R.drawable.blue_btn,
                                    PopUp.TYPE_SPINNER_NOT_CANCELABLE, //type of popup.
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            //Do nothing. As this is non-cancellable pop-up

                                        }
                                    },//override click listener for ok button
                                    null);//pass null to use default listener
							break;
                        case DfuService.PROGRESS_VALIDATION_FAILED:
                        case DfuService.PROGRESS_ABORTED:
                            setFlashState( FLASHING_STATE.FLASH_STATE_NONE);
                            PopUp.show(MBApp.getContext(),
                                    getString(R.string.flashing_failed_message), //message
                                    "Operation Aborted", //title
                                    R.drawable.error_face, R.drawable.red_btn,
                                    PopUp.TYPE_ALERT, //type of popup.
                                    popupOkHandler,//override click listener for ok button
                                    popupOkHandler);//pass null to use default listener

                            LocalBroadcastManager.getInstance(MBApp.getContext()).unregisterReceiver(dfuResultReceiver);
                            dfuResultReceiver = null;
                            break;

					}
				} else if ((state > 0) && (state < 100)) {
					if (!inProgress) {
                        setFlashState(FLASHING_STATE.FLASH_STATE_PROGRESS);
						// TODO Update progress bar check if correct.
						PopUp.show(MBApp.getContext(),
							MBApp.getContext().getString(R.string.flashing_progress_message),
							String.format(MBApp.getContext().getString(R.string.flashing_project), programToSend.name),
							R.drawable.flash_face, R.drawable.blue_btn,
							PopUp.TYPE_PROGRESS_NOT_CANCELABLE, null, null);

						inProgress = true;
					}

					PopUp.updateProgressBar(state);

				}
			} else if (intent.getAction() == DfuService.BROADCAST_ERROR) {
				String error_message = broadcastGetErrorMessage(intent.getIntExtra(DfuService.EXTRA_DATA, 0));
                setFlashState( FLASHING_STATE.FLASH_STATE_NONE);
				logi("DFUResultReceiver.onReceive() :: Flashing ERROR!!  Code - [" + intent.getIntExtra(DfuService.EXTRA_DATA, 0)
					+ "] Error Type - [" + intent.getIntExtra(DfuService.EXTRA_ERROR_TYPE, 0) + "]");

				//todo dismiss progress
				PopUp.hide();

				//TODO popup flashing failed
				PopUp.show(MBApp.getContext(),
					error_message, //message
					getString(R.string.flashing_failed_title), //title
					R.drawable.error_face, R.drawable.red_btn,
					PopUp.TYPE_ALERT, //type of popup.
                    popupOkHandler,//override click listener for ok button
                    popupOkHandler);//pass null to use default listener

				LocalBroadcastManager.getInstance(MBApp.getContext()).unregisterReceiver(dfuResultReceiver);
				dfuResultReceiver = null;

			}
		}

		private String broadcastGetErrorMessage(int errorCode) {
			String errorMessage;

			switch (errorCode) {
				case DfuService.ERROR_DEVICE_DISCONNECTED:
					errorMessage = "micro:bit disconnected";
					break;
				case DfuService.ERROR_FILE_NOT_FOUND:
					errorMessage = "File not found";
					break;
				/**
				 * Thrown if service was unable to open the file ({@link java.io.IOException} has been thrown).
				 */
				case DfuService.ERROR_FILE_ERROR:
					errorMessage = "Unable to open file";
					break;
				/**
				 * Thrown then input file is not a valid HEX or ZIP file.
				 */
				case DfuService.ERROR_FILE_INVALID:
					errorMessage = "File not a valid HEX";
					break;
				/**
				 * Thrown when {@link java.io.IOException} occurred when reading from file.
				 */
				case DfuService.ERROR_FILE_IO_EXCEPTION:
					errorMessage = "Unable to read file";
					break;
				/**
				 * Error thrown then {@code gatt.discoverServices();} returns false.
				 */
				case DfuService.ERROR_SERVICE_DISCOVERY_NOT_STARTED:
					errorMessage = "Bluetooth Discovery not started";
					break;
				/**
				 * Thrown when the service discovery has finished but the DFU service has not been found. The device does not support DFU of is not in DFU mode.
				 */
				case DfuService.ERROR_SERVICE_NOT_FOUND:
					errorMessage = "Dfu Service not found";
					break;
				/**
				 * Thrown when the required DFU service has been found but at least one of the DFU characteristics is absent.
				 */
				case DfuService.ERROR_CHARACTERISTICS_NOT_FOUND:
					errorMessage = "Dfu Characteristics not found";
					break;
				/**
				 * Thrown when unknown response has been obtained from the target. The DFU target must follow specification.
				 */
				case DfuService.ERROR_INVALID_RESPONSE:
					errorMessage = "Invalid response from micro:bit";
					break;

				/**
				 * Thrown when the the service does not support given type or mime-type.
				 */
				case DfuService.ERROR_FILE_TYPE_UNSUPPORTED:
					errorMessage = "Unsupported file type";
					break;

				/**
				 * Thrown when the the Bluetooth adapter is disabled.
				 */
				case DfuService.ERROR_BLUETOOTH_DISABLED:
					errorMessage = "Bluetooth Disabled";
					break;
				default:
					errorMessage = "Unknown Error";
					break;
			}

			return errorMessage;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
}
