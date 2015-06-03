package com.samsung.microbit.ui.fragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.lang.Integer;
import java.util.HashMap;
import java.util.UUID;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.Toast;

import com.samsung.microbit.R;
import com.samsung.microbit.ui.DeviceScanActivity;
import com.samsung.microbit.ui.LEDGridActivity;

public class FlashSectionFragment extends Fragment implements OnItemClickListener {

	private static final int SUCCESS = 0;
	private static final int FILE_NOT_FOUND = -1;
	private static final int FILE_IO_ERROR = -2;
	private static final int FAILED = -3;
	private View rootView;
	private Button flashSearchButton = null;

	private ListView programList = null;
	final ArrayList<String> list = new ArrayList<String>();
	private Boolean isBLEAvailable = true;
	private Boolean isBLuetoothEnabled = false;
	private BluetoothAdapter mBluetoothAdapter = null;
	private String fileNameToFlash = null;

    private static SharedPreferences preferences;
    private static final String PREFERENCES_NAME_KEY = "PairedDeviceName";
    private static final String PREFERENCES_ADDRESS_KEY = "PairedDeviceAddress";
    private HashMap<String, String> prettyFileNameMap = new HashMap<String, String>();

	final public static String BINARY_FILE_NAME = "/sdcard/output.bin";

	public FlashSectionFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//Search BLE devices here with proper flash support
		checkMicroBitAttached();
		//Populate program list for later use
		findProgramsAndPopulate();
	}

	private void checkMicroBitAttached() {
		//Open Bluetooth connection and check if MicroBit it attached
		// Use this check to determine whether BLE is supported on the device. Then
		// you can selectively disable BLE-related features.
		if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			isBLEAvailable = false;
			return;
		}

		final BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
		if (mBluetoothAdapter.isEnabled()) {
			isBLuetoothEnabled = true;
		}
	}

	private void findProgramsAndPopulate() {
		File sdcardDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		Log.d("MicroBit", "Searching files in " + sdcardDownloads.getAbsolutePath());

		int iTotalPrograms = 0;
		if (sdcardDownloads.exists()) {
			File files[] = sdcardDownloads.listFiles();
			for (int i = 0; i < files.length; i++) {
				String fileName = files[i].getName();
				if (fileName.endsWith(".hex")) {

                    //Beautify the filename
                    String parsedFileName;

                    int dot = fileName.lastIndexOf(".");
                    parsedFileName = fileName.substring(0, dot);
                    parsedFileName = parsedFileName.replace('_',' ');

                    prettyFileNameMap.put(parsedFileName, fileName);

					list.add(parsedFileName);
					++iTotalPrograms;
				}
			}
		}

		if (iTotalPrograms == 0) {
			list.add("No programs found !");
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		rootView  = inflater.inflate(R.layout.fragment_section_flash, container, false);
		//flashSearchButton = (Button) rootView.findViewById(R.id.searchButton);
		programList = (ListView) rootView.findViewById(R.id.programList);

        /*
		//Default Values
		if (!isBLEAvailable) {
			flashSearchButton.setEnabled(false);
			flashSearchButton.setText("BLE not supported. Cannot load code.");
		} else {
			flashSearchButton.setEnabled(true);
			flashSearchButton.setText("Search");
			if (isBLuetoothEnabled) {
				flashSearchButton.setText(R.string.run_code_button_name);
			} else {
				flashSearchButton.setText(R.string.error_bluetooth_not_enabled);
			}
		}
        */

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, list);
		programList.setAdapter(adapter);
		//flashSearchButton.setOnClickListener(this);
		programList.setOnItemClickListener(this);
		return rootView;
	}

    public void flashCode()
    {
        File file = new File(BINARY_FILE_NAME);
        if (file.exists()) {
            Intent intent = new Intent(getActivity(), DeviceScanActivity.class);
            startActivity(intent);
        } else {
            Toast.makeText(getActivity(), "Create the binary file first by clicking one file below", Toast.LENGTH_LONG).show();
        }
    }

    /*
	@Override
	public void onClick(View v) {

		boolean test = true;
		Log.d("FlashSectionFragment", "onClick");
		switch (v.getId()) {
			case R.id.searchButton:
				File file = new File(BINARY_FILE_NAME);
				if (file.exists()) {
					// Intent intent = new Intent(getActivity(), DeviceScanActivity.class);
			 		Intent intent = new Intent(getActivity(), LEDGridActivity.class); //DeviceScanActivity.class);
					intent.putExtra("download_file", fileNameToFlash);
					startActivity(intent);
				} else {
					Toast.makeText(getActivity(), "Create the binary file first by clicking one file below", Toast.LENGTH_LONG).show();
				}

				break;
		}
	}
    */

    private void prepareToFlash(boolean useExistingDevice)
    {
        int retValue = PrepareFile(fileNameToFlash);

        switch (retValue) {
            case SUCCESS:
                preferences = rootView.getContext().getSharedPreferences("Microbit_PairedDevices", Context.MODE_PRIVATE);
                String pairedDeviceName = preferences.getString(PREFERENCES_NAME_KEY, "None");
                String pairedDeviceAddress = preferences.getString(PREFERENCES_ADDRESS_KEY, "None");

                Toast.makeText(getActivity(), "Name == " + pairedDeviceName + " Address == " + pairedDeviceAddress, Toast.LENGTH_LONG).show();

                Intent intent = new Intent(getActivity(), LEDGridActivity.class); //DeviceScanActivity.class);
                intent.putExtra("download_file", fileNameToFlash);
                intent.putExtra("use_existing_device", useExistingDevice);
                intent.putExtra("existing_device_name", pairedDeviceName);
                intent.putExtra("existing_device_address", pairedDeviceAddress);
                startActivity(intent);
                //Toast.makeText(getActivity(), "Binary file ready for flashing", Toast.LENGTH_LONG).show();
                break;

            case FILE_NOT_FOUND:
            case FILE_IO_ERROR:
            case FAILED:
                handle_flashing_failed(retValue);
                Toast.makeText(getActivity(), "Failed to create binary file", Toast.LENGTH_LONG).show();
                break;
        }
    }

    private void findNewDevice()
    {
        prepareToFlash(false);
    }

    private void useExistingDevice()
    {
        prepareToFlash(true);
    }

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {

		View v1 = programList.getChildAt(position);
        /*
		CheckedTextView check = (CheckedTextView) v1;
		check.setChecked(!check.isChecked());
        */

		fileNameToFlash = (String) adapter.getItemAtPosition(position);
        fileNameToFlash = prettyFileNameMap.get(fileNameToFlash);

		//Toast.makeText(getActivity(), "Preparing " + fileNameToFlash + " ...", Toast.LENGTH_LONG).show();
        //int retValue = PrepareFile(fileNameToFlash);

        preferences = rootView.getContext().getSharedPreferences("Microbit_PairedDevices", Context.MODE_PRIVATE);


        String pairedDeviceName = preferences.getString(PREFERENCES_NAME_KEY, "None");


        Log.d("Microbit", "Preferences - PairedDevice" + pairedDeviceName);
        if (!pairedDeviceName.equals("None")) {

            AlertDialog.Builder dialog = new AlertDialog.Builder(rootView.getContext());

            dialog.setTitle(R.string.microbit_found)
                    .setMessage("If you want to flash your code to the last micro:bit that you used, click 'Yes'.\nIf you'd prefer to use a different micro:bit instead, click 'No'.")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialoginterface, int i) {
                            useExistingDevice();
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialoginterface, int i) {
                            findNewDevice();
                        }
                    })
                    .show();
        }
        else {
            findNewDevice();
        }
	}

    private void alertView( String message, int title_id ) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(rootView.getContext());

        dialog.setTitle(getString(title_id))
                .setIcon(R.drawable.ic_launcher)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialoginterface, int i) {
                    }
                }).show();
    }
    private void  handle_flashing_failed(int failureCode)
    {
        int messageId;
      //  progressDialog.dismiss();
        switch(failureCode)
        {
            case FILE_NOT_FOUND:
                messageId = R.string.flashing_failed_not_found;
                break;
            case FILE_IO_ERROR:
                messageId = R.string.flashing_failed_io_error;
                break;
            case FAILED:
                default:
                messageId = R.string.flashing_failed_message;
                break;
        }
        alertView(getString(messageId), R.string.flashing_failed_title);
       // devicesButton.setText(R.string.devices_find_microbit);
    }
    private void  handle_flashing_successful()
    {
    //    devicesButton.setText("Connected to " + deviceName);
      //  mHandler.removeCallbacks(scanFailedCallback);
       // progressDialog.dismiss();
        alertView(getString(R.string.flashing_success_message),
                R.string.flashing_success_title);
    }
	// Creates binary buffer from ONLY the data specified by hex, spec here:
	// http://en.wikipedia.org/wiki/Intel_HEX
	private int PrepareFile(String fileName) {
		Log.d("MicroBit", "PrepareFile [+]");
		FileInputStream is = null;
		BufferedReader reader = null;
		byte[] buffer = null;

		File sdcardDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		if (sdcardDownloads.exists()) {
			File hexFile = new File(sdcardDownloads, fileName);
			if (hexFile.exists()) {
				Log.d("MicroBit", "Found the Hex file");

				try {
					is = new FileInputStream(hexFile);
					reader = new BufferedReader(new InputStreamReader(is));
					String line = null;
					try {
						int byteCount = 0;
						while ((line = reader.readLine()) != null) {
							//Calculate size of output file
							if (line.substring(7, 9).equals("00")) { // type == data
								byteCount += Integer.parseInt(line.substring(1, 3), 16);
							}
						}

						Log.d("MicroBit", "Size of outputfile = " + byteCount);

						// "reset" to beginning of file (discard old buffered reader)
						is.getChannel().position(1);
						reader = new BufferedReader(new InputStreamReader(is));

						int pointer = 0;
						buffer = new byte[byteCount];
						while ((line = reader.readLine()) != null) {
							if (line.substring(7, 9).equals("00")) { // type == data
								int length = Integer.parseInt(line.substring(1, 3), 16);
								String data = line.substring(9, 9 + length * 2);
								for (int i = 0; i < length * 2; i += 2) {
									buffer[pointer] = (byte) Integer.parseInt(data.substring(i, i + 2), 16);
									pointer++;
								}
							}
						}

						if (reader != null)
							reader.close();

						//Write file
						Log.d("MicroBit", "Writing output file");
						FileOutputStream f = new FileOutputStream(new File(BINARY_FILE_NAME));
						f.write(buffer);
						f.flush();
						f.close();
						return SUCCESS;
					} catch (IOException e) {
						Log.d("MicroBit", "Cannot read the file");
						e.printStackTrace();
						return FILE_IO_ERROR;
					}
				} catch (FileNotFoundException e) {
					Log.d("MicroBit", "Cannot find the file");
					return FILE_NOT_FOUND;
				}
			}
		}

		Log.d("MicroBit", "Failed conversion");
		return FAILED;
	}
}
