package tgi.com.btlibrarydemo;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import tgi.com.librarybtmanager.BtNotEnabledException;
import tgi.com.librarybtmanager.DeviceParingStateListener;
import tgi.com.librarybtmanager.TgiBleManager;
import tgi.com.librarybtmanager.TgiBleScanCallback;

public class ScanDeviceActivity extends BaseActionBarActivity {
    private ListView mListView;
    private ScanDeviceActivity mThis;
    private ArrayList<BluetoothDevice> mDevices = new ArrayList<>();
    private ArrayAdapter<BluetoothDevice> mAdapter;

    public static void start(Context context) {
        Intent starter = new Intent(context, ScanDeviceActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_device);
        mThis = this;

        mListView = findViewById(R.id.activity_scan_device_list_view);
        mAdapter = new ArrayAdapter<BluetoothDevice>(
                mThis,
                android.R.layout.simple_list_item_1,
                mDevices
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                BluetoothDevice device = mDevices.get(position);
                StringBuffer sb = new StringBuffer();
                sb.append(device.getName())
                        .append("\r\n")
                        .append(device.getAddress())
                        .append("\r\n");
                view.setText(sb.toString());
                return view;
            }
        };
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TgiBleManager.getInstance().pairDevice(
                        mDevices.get(position),
                        new DeviceParingStateListener() {
                            @Override
                            public void onParingSessionBegin() {
                                super.onParingSessionBegin();
                                showLog("onParingSessionBegin");
                            }

                            @Override
                            public void onDevicePairingStateChanged(BluetoothDevice device, int previousState, int currentState) {
                                super.onDevicePairingStateChanged(device, previousState, currentState);
                                switch (currentState) {
                                    case BluetoothDevice.BOND_NONE:
                                        showLog("device "+device.getAddress()+" is not bonded.");
                                        break;
                                    case BluetoothDevice.BOND_BONDED:
                                        showLog("device "+device.getAddress()+" is bonded.");
                                        int state = device.getBondState();
                                        showLog("have a second check...");
                                        if(state==currentState){
                                            showLog("ya, it is truly bonded.");
                                            showLog("begin to connect device...");
                                            ConnectDeviceActivity.start(mThis,device.getAddress());
                                        }else {
                                            showLog("No, it is actually not bonded, what is wrong?");
                                        }
                                        break;
                                    case BluetoothDevice.BOND_BONDING:
                                        showLog("device "+device.getAddress()+" is bonding.");
                                        break;
                                }
                            }

                            @Override
                            public void onParingSessionEnd(int endState) {
                                super.onParingSessionEnd(endState);
                                showLog("onParingSessionEnd");
                            }
                        }
                );
            }
        });


        TgiBleManager.getInstance().startScanBtDevices(new TgiBleScanCallback() {
            @Override
            public void onPreScan() {
                super.onPreScan();
                mDevices.clear();
                mAdapter.notifyDataSetChanged();
                showLog("Scan Starts.");
            }

            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                super.onLeScan(device, rssi, scanRecord);
                if (!mDevices.contains(device)) {
                    mDevices.add(device);
                    mAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onPostScan() {
                super.onPostScan();
                showLog("Scan Stops.");
            }
        });
    }

    void showLog(String msg){
        Log.e(getClass().getSimpleName(),msg);
    }
}
