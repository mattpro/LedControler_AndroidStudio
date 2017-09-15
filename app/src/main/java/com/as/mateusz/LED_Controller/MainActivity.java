package com.as.mateusz.LED_Controller;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;



@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity implements BluetoothAdapter.LeScanCallback {

    private static final String TAG = "BLUE";

    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION};

    private static final String DEVICE_NAME = "LED Controller";
    private static final UUID RGBWCOLOR_SERVICE = UUID.fromString("9E02BB87-81A6-4FE1-BB87-6137EBD80902");
    private static final UUID RGBWCOLOR_CHAR = UUID.fromString("9E022160-81A6-4FE1-BB87-6137EBD80902");

    private BluetoothAdapter mBluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;
    Map<String, String> mDeviceName = new HashMap<String, String>();
    private BluetoothGatt mConnectedGatt;

    private ProgressDialog mProgress;

    private static SeekBar seekBar_red;
    private static SeekBar seekBar_green;
    private static SeekBar seekBar_blue;
    private static SeekBar seekBar_white;
    private static SeekBar seekBar_brightness;

    private static TextView text_red;
    private static TextView text_green;
    private static TextView text_blue;
    private static TextView text_white;
    private static TextView text_brightness;
    private static TextView text_connection;

    private static Button buttonColorPicker;
    private static Spinner spinnerEffectName;

    ArrayAdapter<CharSequence> adapterEffectsName;

    int redValue;
    int greenValue;
    int blueValue;
    int whiteValue;
    byte speed;

    int redValueProcenty;
    int greenValueProcenty;
    int blueValueProcenty;
    int whiteValueProcenty;
    int brightness;

    byte settingsName = 0x00;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setSubtitle(R.string.sub_name);
        setSupportActionBar(toolbar);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Bluetooth is disabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }

        mDevices = new SparseArray<BluetoothDevice>();

        mProgress = new ProgressDialog(this);
        mProgress.setIndeterminate(true);
        mProgress.setCancelable(false);
        text_connection = (TextView) findViewById(R.id.textViewConnection);

        OnClickButtonColorPicker();
        seekBarColorChange();
        seekBarBrightnessChange();
        OnEffectChange();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * We need to enforce that Bluetooth is first enabled, and take the
         * user to settings to enable it if they have not done so.
         */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Bluetooth is disabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }
        /*
         * Check for Bluetooth LE Support.  In production, our manifest entry will keep this
         * from installing on these devices, but this will allow test devices or other
         * sideloads to report whether or not the feature exists.
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        clearDisplayValues();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Make sure dialog is hidden
        mProgress.dismiss();
        //Cancel any scans in progress
        mHandler.removeCallbacks(mStopRunnable);
        mHandler.removeCallbacks(mStartRunnable);
        mBluetoothAdapter.stopLeScan(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mConnectedGatt != null) {
            mConnectedGatt.disconnect();
            mConnectedGatt = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add the "scan" option to the menu
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // Add any device elements we've discovered to the overflow menu
        for (int i = 0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            if (mDeviceName.containsKey(device.getAddress())) {
                menu.add(0, mDevices.keyAt(i), 0, mDeviceName.get(device.getAddress()));
            } else {
                menu.add(0, mDevices.keyAt(i), 0, device.getName() + "\r\n" + device.getAddress() );
            }
        }
        if (mDevices.size() != 0) menu.add(0, 1234, 0, "Disconnect");

        return true;
    }

    public void OnClickButtonColorPicker() {

        buttonColorPicker = (Button) findViewById(R.id.buttonColorPicker);

        buttonColorPicker.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ColorPickerDialogBuilder
                                .with(MainActivity.this)
                                .setTitle("Choose color")
                                .showColorPreview(true)
                                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                                .density(15)
                                .noSliders()
                                .setOnColorSelectedListener(new OnColorSelectedListener() {
                                    @Override
                                    public void onColorSelected(int selectedColor) {
                                        blueValue = (byte) (selectedColor >> 0) & 0xFF;
                                        greenValue = (byte) (selectedColor >> 8) & 0xFF;
                                        redValue = (byte) (selectedColor >> 16) & 0xFF;

                                        seekBar_red.setProgress(redValue);
                                        seekBar_green.setProgress(greenValue);
                                        seekBar_blue.setProgress(blueValue);

                                        if (mConnectedGatt != null) {
                                            byte[] colors = {0x00, 0x00, (byte) redValue, (byte) greenValue, (byte) blueValue, (byte) whiteValue, (byte)brightness,(byte)settingsName};
                                            BluetoothGattCharacteristic characteristic;
                                            characteristic = mConnectedGatt.getService(RGBWCOLOR_SERVICE)
                                                    .getCharacteristic(RGBWCOLOR_CHAR);
                                            characteristic.setValue(colors);
                                            mConnectedGatt.writeCharacteristic(characteristic);
                                        }
                                    }
                                })
                                .setPositiveButton("ok", new ColorPickerClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                    }
                                })
                                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                })
                                .build()
                                .show();
                    }
                }
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                startScan();
                mDevices.clear();
                return true;
            case 1234:
                if (mConnectedGatt != null) mConnectedGatt.disconnect();
                return true;
            default:
                //Obtain the discovered device to connect with
                BluetoothDevice device = mDevices.get(item.getItemId());
                Log.i(TAG, "Connecting to " + device.getName());
                /*
                 * Make a connection with the device using the special LE-specific
                 * connectGatt() method, passing in a callback for GATT events
                 */
                mConnectedGatt = device.connectGatt(this, false, mGattCallback);
                //Display progress UI
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to " + device.getName() + "..."));
                return super.onOptionsItemSelected(item);
        }
    }

    public void OnEffectChange() {
        spinnerEffectName = (Spinner) findViewById(R.id.spinner);
        adapterEffectsName = ArrayAdapter.createFromResource(this, R.array.EffectsName, android.R.layout.simple_spinner_item);
        adapterEffectsName.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEffectName.setAdapter(adapterEffectsName);

        spinnerEffectName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (id == 0 )       settingsName = (byte) 0;
                else if ( id == 1 ) {
                    settingsName = (byte) 7;
                    speed = (byte)200;
                }
                else {
                    settingsName = (byte) 11;
                    speed = (byte)255;
                }

                if (mConnectedGatt != null) {

                    BluetoothGattCharacteristic characteristic;
                    byte[] colors = { 0x00, 0x00, (byte) redValue, (byte) greenValue, (byte) blueValue, (byte) whiteValue, (byte)brightness, (byte)settingsName, (byte)speed};
                    characteristic = mConnectedGatt.getService(RGBWCOLOR_SERVICE)
                            .getCharacteristic(RGBWCOLOR_CHAR);
                    characteristic.setValue(colors);
                    mConnectedGatt.writeCharacteristic(characteristic);

                    Toast.makeText(getBaseContext(), parent.getItemAtPosition(position).toString() + " effects selected", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }

    private void clearDisplayValues() {
        seekBar_red.setProgress(0);
        seekBar_green.setProgress(0);
        seekBar_blue.setProgress(0);
        seekBar_white.setProgress(0);
    }

    public void seekBarColorChange() {
        seekBar_red = (SeekBar) findViewById(R.id.seekBarRed);
        text_red = (TextView) findViewById(R.id.textViewRed);
        text_red.setText(0 + "%");

        seekBar_green = (SeekBar) findViewById(R.id.seekBarGreen);
        text_green = (TextView) findViewById(R.id.textViewGreen);
        text_green.setText(0 + "%");

        seekBar_blue = (SeekBar) findViewById(R.id.seekBarBlue);
        text_blue = (TextView) findViewById(R.id.textViewBlue);
        text_blue.setText(0 + "%");

        seekBar_white = (SeekBar) findViewById(R.id.seekBarWhite);
        text_white = (TextView) findViewById(R.id.textViewWhite);
        text_white.setText(0 + "%");

        seekBar_red.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        redValueProcenty = Math.round(redValue * 100 / 255);
                        text_red.setText(redValueProcenty + "%");
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        redValue = progress;
                        redValueProcenty = Math.round(redValue * 100 / 255);
                        text_red.setText(redValueProcenty + "%");

                        if (mConnectedGatt != null) {
                            BluetoothGattCharacteristic characteristic;
                            byte[] colors = { 0x00, 0x00, (byte) redValue, (byte) greenValue, (byte) blueValue, (byte) whiteValue, (byte)brightness, (byte)settingsName, (byte)speed};
                            characteristic = mConnectedGatt.getService(RGBWCOLOR_SERVICE)
                                    .getCharacteristic(RGBWCOLOR_CHAR);
                            characteristic.setValue(colors);
                            mConnectedGatt.writeCharacteristic(characteristic);
                        }
                    }
                }
        );

        seekBar_green.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        greenValueProcenty = Math.round(greenValue * 100 / 255);
                        text_green.setText(greenValueProcenty + "%");
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        greenValue = progress;
                        greenValueProcenty = Math.round(greenValue * 100 / 255);
                        text_green.setText(greenValueProcenty + "%");

                        if (mConnectedGatt != null) {
                            BluetoothGattCharacteristic characteristic;
                            byte[] colors = { 0x00, 0x00, (byte) redValue, (byte) greenValue, (byte) blueValue, (byte) whiteValue, (byte)brightness, (byte)settingsName, (byte)speed};
                            characteristic = mConnectedGatt.getService(RGBWCOLOR_SERVICE)
                                    .getCharacteristic(RGBWCOLOR_CHAR);
                            characteristic.setValue(colors);
                            mConnectedGatt.writeCharacteristic(characteristic);
                        }
                    }
                }
        );

        seekBar_blue.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        blueValueProcenty = Math.round(blueValue * 100 / 255);
                        text_blue.setText(blueValueProcenty + "%");
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        blueValue = progress;
                        blueValueProcenty = Math.round(blueValue * 100 / 255);
                        text_blue.setText(blueValueProcenty + "%");

                        if (mConnectedGatt != null) {
                            BluetoothGattCharacteristic characteristic;
                            byte[] colors = { 0x00, 0x00, (byte) redValue, (byte) greenValue, (byte) blueValue, (byte) whiteValue, (byte)brightness, (byte)settingsName, (byte)speed};
                            characteristic = mConnectedGatt.getService(RGBWCOLOR_SERVICE)
                                    .getCharacteristic(RGBWCOLOR_CHAR);
                            characteristic.setValue(colors);
                            mConnectedGatt.writeCharacteristic(characteristic);
                        }
                    }
                }
        );

        seekBar_white.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        whiteValueProcenty = Math.round(whiteValue * 100 / 255);
                        text_white.setText(whiteValueProcenty + "%");
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        whiteValue = progress;
                        whiteValueProcenty = Math.round(whiteValue * 100 / 255);
                        text_white.setText(whiteValueProcenty + "%");

                        if (mConnectedGatt != null) {
                            BluetoothGattCharacteristic characteristic;
                            byte[] colors = { 0x00, 0x00, (byte) redValue, (byte) greenValue, (byte) blueValue, (byte) whiteValue, (byte)brightness, (byte)settingsName, (byte)speed};

                            characteristic = mConnectedGatt.getService(RGBWCOLOR_SERVICE)
                                    .getCharacteristic(RGBWCOLOR_CHAR);
                            characteristic.setValue(colors);
                            mConnectedGatt.writeCharacteristic(characteristic);
                        }
                    }
                }
        );
    }

    public void seekBarBrightnessChange() {
        seekBar_brightness = (SeekBar) findViewById(R.id.seekBarSpeed);
        text_brightness = (TextView) findViewById(R.id.textViewBrightness);
        text_brightness.setText(0 + "%");

        seekBar_brightness.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        Toast.makeText(MainActivity.this, "Brightness set to " + brightness + "/16", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        brightness = progress;
                        text_brightness.setText(brightness + "/16");
                        if (mConnectedGatt != null) {
                            BluetoothGattCharacteristic characteristic;
                            byte[] colors = { 0x00, 0x00, (byte) redValue, (byte) greenValue, (byte) blueValue, (byte) whiteValue, (byte)brightness, (byte)settingsName, (byte)speed};
                            characteristic = mConnectedGatt.getService(RGBWCOLOR_SERVICE)
                                    .getCharacteristic(RGBWCOLOR_CHAR);
                            characteristic.setValue(colors);
                            mConnectedGatt.writeCharacteristic(characteristic);
                        }
                    }
                }
        );
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };

    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            startScan();
        }
    };

    private void startScan() {
        int apiVersion = android.os.Build.VERSION.SDK_INT;
        if (apiVersion > android.os.Build.VERSION_CODES.KITKAT) {
            BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            scanner.startScan(mScanCallback);
            setProgressBarIndeterminateVisibility(true);
            mHandler.postDelayed(mStopRunnable, 2500);
        } else {
            mBluetoothAdapter.startLeScan(this);
            setProgressBarIndeterminateVisibility(true);
            mHandler.postDelayed(mStopRunnable, 2500);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.i(TAG, "New LE NEW API Device: " + result.getScanRecord().getDeviceName() + " @ " + result.getRssi());

            if (DEVICE_NAME.equals(result.getScanRecord().getDeviceName())) {
                mDevices.put(result.getDevice().hashCode(), result.getDevice());
                invalidateOptionsMenu();
            }
        }
    };

    private void stopScan() {
        int apiVersion = android.os.Build.VERSION.SDK_INT;
        if (apiVersion > android.os.Build.VERSION_CODES.KITKAT) {
            BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            scanner.stopScan(mScanCallback);
            setProgressBarIndeterminateVisibility(false);
        } else {
            mBluetoothAdapter.stopLeScan(this);
            setProgressBarIndeterminateVisibility(false);
        }
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);

        if (DEVICE_NAME.equals(device.getName())) {
            mDevices.put(device.hashCode(), device);
            invalidateOptionsMenu();
        }

    }



    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /*
         * Send an enable command to each sensor by writing a configuration
         * characteristic.  This is specific to the SensorTag to keep power
         * low by disabling sensors you aren't using.
         */
        private void enableNextSensor(BluetoothGatt gatt) {
            mHandler.sendEmptyMessage(MSG_DISMISS);
        }

        /*
         * Read the data characteristic's value for each sensor explicitly
         */
        private void readNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
        }

        /*
         * Enable notification of changes on the data characteristic for each sensor
         * by writing the ENABLE_NOTIFICATION_VALUE flag to that characteristic's
         * configuration descriptor.
         */
        private void setNotifyNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection State Change: " + status + " -> " + connectionState(newState));
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                /*
                 * Once successfully connected, we must next discover all the services on the
                 * device before we can read and write their characteristics.
                 */
                gatt.discoverServices();
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                /*
                 * If at any point we disconnect, send a message to clear the weather values
                 * out of the UI
                 */
                mHandler.sendEmptyMessage(MSG_CLEAR);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                /*
                 * If there is a failure at any stage, simply disconnect
                 */
                gatt.disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services Discovered: " + status);
            /*
             * With services discovered, we are going to reset our state machine and start
             * working through the sensors we need to enable
             */
            enableNextSensor(gatt);
            mHandler.sendMessage(Message.obtain(null, MSG_READSETTINGS));

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //For each read, pass the data up to the UI thread to update the display
            if (RGBWCOLOR_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_RGBWCOLORS, characteristic));
                Log.d(TAG, "Read characteristic RGBWCOLOR");
            }

            Log.d(TAG, "Read characteristic");
            setNotifyNextSensor(gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            /*
             * After notifications are enabled, all updates from the device on characteristic
             * value changes will be posted here.  Similar to read, we hand these up to the
             * UI thread to update the display.
             */
            if (RGBWCOLOR_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_RGBWCOLORS, characteristic));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "onDescriptorWrite");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "RSSI: " + rssi);
        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return String.valueOf(status);
            }
        }
    };

    /*
     * We have a Handler to process event results on the main thread
     */
    private static final int MSG_RGBWCOLORS = 101;
    private static final int MSG_SETTINGS = 102;
    private static final int MSG_PROGRESS = 201;
    private static final int MSG_DISMISS = 202;
    private static final int MSG_CLEAR = 301;
    private static final int MSG_READSETTINGS = 302;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothGattCharacteristic characteristic;

            switch (msg.what) {
                case MSG_PROGRESS:
                    mProgress.setMessage((String) msg.obj);
                    if (mDeviceName.containsKey(mConnectedGatt.getDevice().getAddress())) {
                        text_connection.setText("Connected to: " + mDeviceName.get(mConnectedGatt.getDevice().getAddress()));
                    } else {
                        text_connection.setText("Connected to: " + mConnectedGatt.getDevice().getName() + "\n\r" + mConnectedGatt.getDevice().getAddress());
                    }
                    if (!mProgress.isShowing()) {
                        mProgress.show();
                    }
                    break;
                case MSG_SETTINGS:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining pressure value");
                        return;
                    }
                    Log.d(TAG, "in MSG_SETTINGS");
                    Toast.makeText(MainActivity.this, "Read settings", Toast.LENGTH_LONG).show();
                    break;
                case MSG_READSETTINGS:
                    if (mConnectedGatt != null) {
                    }
                    break;
                case MSG_DISMISS:
                    mProgress.hide();
                    break;
                case MSG_CLEAR:
                    text_connection.setText("Disconnected");
                    clearDisplayValues();
                    break;
            }
        }
    };



}
 
