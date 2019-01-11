package tgi.com.btlibrarydemo.base;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import tgi.com.btlibrarydemo.R;
import tgi.com.btlibrarydemo.activities.BaseActionBarActivity;
import tgi.com.btlibrarydemo.utils.LogUtils;
import tgi.com.librarybtmanager.TgiBleManager;
import tgi.com.librarybtmanager.TgiBleScanCallback;
import tgi.com.librarybtmanager.TgiDeviceParingStateListener;

import static tgi.com.btlibrarydemo.utils.LogUtils.showLog;

public class ScanDeviceActivity extends BaseActionBarActivity {
    private ListView mListView;
    private ScanDeviceActivity mThis;
    private ArrayList<BluetoothDevice> mDevices = new ArrayList<>();
    private ArrayAdapter<BluetoothDevice> mAdapter;
    TgiDeviceParingStateListener mParingStateListener = new TgiDeviceParingStateListener() {
        @Override
        public void onParingSessionBegin() {
            super.onParingSessionBegin();
            showLog("开始绑定");
            ArrayList<BluetoothDevice> devices = TgiBleManager.getInstance().getBondedDevices();
            for (BluetoothDevice temp : devices) {
                showLog("已经绑定的设备有：" + temp.getName() + " " + temp.getAddress());
            }
        }

        @Override
        public void onParingSessionEnd(int endState) {
            super.onParingSessionEnd(endState);
            if (endState == BluetoothDevice.BOND_NONE) {
                showLog("设备绑定失败：" + mBluetoothDevice.getName() + " " + mBluetoothDevice.getAddress());
                TgiBleManager.getInstance().removePairedDeviceWithoutUserConsent(mBluetoothDevice);

            } else if (endState == BluetoothDevice.BOND_BONDED) {
                ConnectDeviceActivity.start(mThis, mBluetoothDevice.getAddress());
            }
        }

        @Override
        public void onDevicePairingStateChanged(BluetoothDevice device, int previousState, int currentState) {
            super.onDevicePairingStateChanged(device, previousState, currentState);
            showLog("绑定状态更新：pre=" + TgiBleManager.getInstance().getBondSateDescription(previousState) +
                    " post=" + TgiBleManager.getInstance().getBondSateDescription(currentState));
        }

        @Override
        public void onError(String errorMsg) {
            super.onError(errorMsg);
            showLog("绑定发生了错误：" + errorMsg);
        }
    };
    private BluetoothDevice mBluetoothDevice;

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
                TgiBleManager.getInstance().stopScanBtDevice();
                mBluetoothDevice = mDevices.get(position);
                if (mBluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                    ConnectDeviceActivity.start(mThis, mBluetoothDevice.getAddress());
                } else {
                    pairWithOutConsent(mBluetoothDevice);
                    //                    pairWithUserConsent(mBluetoothDevice);
                }

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
                if (TextUtils.isEmpty(device.getName())) {
                    return;
                }
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

    private void pairWithUserConsent(BluetoothDevice device) {
        TgiBleManager.getInstance().pairDevice(device, mParingStateListener);
    }

    private void pairWithOutConsent(final BluetoothDevice device) {
        TgiBleManager.getInstance().pairDeviceWithoutUserConsent(device, mParingStateListener);
    }

}
