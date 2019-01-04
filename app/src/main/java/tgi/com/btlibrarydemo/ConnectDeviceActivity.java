package tgi.com.btlibrarydemo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import tgi.com.librarybtmanager.BtDeviceConnectListener;
import tgi.com.librarybtmanager.DeviceParingStateListener;
import tgi.com.librarybtmanager.TgiBleManager;
import tgi.com.librarybtmanager.TgiBleScanCallback;
import tgi.com.librarybtmanager.TgiReadCharCallback;
import tgi.com.librarybtmanager.TgiToggleNotificationCallback;
import tgi.com.librarybtmanager.TgiWriteCharCallback;

public class ConnectDeviceActivity extends BaseActionBarActivity {
    private static final String BT_DEVICE_ADDRESS = "BT_DEVICE_ADDRESS";
    private ListView mListView;
    private ArrayList<String> mLogs = new ArrayList<>();
    private ArrayAdapter<String> mAdapter;
    private Handler mHandler;
    private Semaphore mSemaphore = new Semaphore(1);
    private volatile boolean isNotificationOn = false;
    private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<>();
    private PairedDevicesAdapter mPairedDevicesAdapter;
    private RecyclerView mRecyclerView;
    private String mConnectAddress;


    public static void start(Context context, String btDeviceAddr) {
        Intent starter = new Intent(context, ConnectDeviceActivity.class);
        starter.putExtra(BT_DEVICE_ADDRESS, btDeviceAddr);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_device);
        mListView = findViewById(R.id.activity_connect_device_list_view);
        mRecyclerView = findViewById(R.id.activity_connect_device_recycler_view);
        mHandler = new Handler();
        mConnectAddress = getIntent().getStringExtra(BT_DEVICE_ADDRESS);
        mAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                mLogs
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(mLogs.get(position));
                return view;
            }
        };
        mListView.setAdapter(mAdapter);

        mPairedDevicesAdapter = new PairedDevicesAdapter(mDeviceList, this);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mRecyclerView.setAdapter(mPairedDevicesAdapter);
        mPairedDevicesAdapter.setOnItemClickListener(new PairedDevicesAdapter.ItemClickListener() {
            @Override
            public void onItemClick(int position) {
                final BluetoothDevice device = mDeviceList.get(position);
                if (TgiBleManager.getInstance().checkIfDeviceBonded(device)) {
                    boolean b = TgiBleManager.getInstance().removePairedDeviceWithoutUserConsent(device);
                    showLog("解除绑定：" + b);
                } else {
                    boolean b = TgiBleManager.getInstance().pairDeviceWithoutUserConsent(device);
                    showLog("新增绑定：" + b);
                    if (b) {
                        TgiBleManager.getInstance().swapDevice(device);
                    }
//                    TgiBleManager.getInstance().pairDevice(device, new DeviceParingStateListener() {
//                        int state = -1;
//
//                        @Override
//                        public void onParingSessionBegin() {
//                            super.onParingSessionBegin();
//                            showLog("开始配对");
//                        }
//
//                        @Override
//                        public void onDevicePairingStateChanged(BluetoothDevice device, int previousState, int currentState) {
//                            super.onDevicePairingStateChanged(device, previousState, currentState);
//                            switch (currentState) {
//                                case BluetoothDevice.BOND_NONE:
//                                    showLog("配对：断开");
//                                    break;
//                                case BluetoothDevice.BOND_BONDED:
//                                    showLog("配对：配对成功");
//                                    break;
//                                case BluetoothDevice.BOND_BONDING:
//                                    showLog("配对：配对中。。。");
//                                    break;
//                            }
//                            state = currentState;
//                            if (state == BluetoothDevice.BOND_BONDED) {
//                                TgiBleManager.getInstance().swapDevice(device);
//                            }
//                        }
//
//                        @Override
//                        public void onParingSessionEnd() {
//                            super.onParingSessionEnd();
//                            showLog("结束配对");
//                        }
//
//                        @Override
//                        public void onError(String errorMsg) {
//                            super.onError(errorMsg);
//                            showLog("配对出错："+errorMsg);
//                        }
//                    });
                }
                mPairedDevicesAdapter.updateBondedListAndNotifyDataSetChanged();

            }
        });

        scanDevices();

    }

    private void scanDevices() {
        TgiBleManager.getInstance().startScanBtDevices(new TgiBleScanCallback() {
            @Override
            public void onPreScan() {
                super.onPreScan();
                showLog("scan starts");
                mDeviceList.clear();
                mDeviceList.addAll(TgiBleManager.getInstance().getBondedDevices());
                mPairedDevicesAdapter.notifyDataSetChanged();
            }

            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                super.onLeScan(device, rssi, scanRecord);
                if (TextUtils.isEmpty(device.getName())) {
                    return;
                }
                if (!mDeviceList.contains(device)) {
                    mDeviceList.add(device);
                    mPairedDevicesAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onPostScan() {
                super.onPostScan();
                showLog("scan stops");
                connectDevice(mConnectAddress);
            }

            @Override
            public void onError(String errorMsg) {
                super.onError(errorMsg);
                showLog(errorMsg);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectDevice();
    }

    private void disconnectDevice() {
        TgiBleManager.getInstance().disConnectDevice();
    }

    private void connectDevice(String deviceAddress) {
        mLogs.clear();
        mAdapter.notifyDataSetChanged();
        TgiBleManager.getInstance().connectDevice(deviceAddress, new BtDeviceConnectListener() {
            @Override
            public void onConnectSuccess(final BluetoothGatt gatt) {
                super.onConnectSuccess(gatt);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TgiBleManager.getInstance().toggleNotification(
                                Constants.MASTER_SERVICE_UUID,
                                Constants.STATUS_CHAR_UUID,
                                Constants.STATUS_DESCRIPTOR_UUID,
                                true,
                                new TgiToggleNotificationCallback() {
                                    @Override
                                    public void onToggleNotificationSuccess(BluetoothGattDescriptor descriptor) {
                                        super.onToggleNotificationSuccess(descriptor);
                                        showLog("onToggleNotificationSuccess");
                                        isNotificationOn = true;
                                    }

                                    @Override
                                    public void onError(String errorMsg) {
                                        super.onError(errorMsg);
                                        showLog("onError:" + errorMsg);
                                    }

                                    @Override
                                    public void onCharChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                                        super.onCharChanged(gatt, characteristic);
                                        byte[] value = characteristic.getValue();
                                        String temp = convertToHexString(value);
                                        showLog("onCharChanged: " + temp);
                                    }
                                }
                        );
                    }
                });

            }

        });
    }

    private String convertToHexString(byte[] value) {
        StringBuilder sb = new StringBuilder();
        for (byte temp : value) {
            sb.append("0x");
            String s = Integer.toHexString(temp);
            if (s.length() < 2) {
                sb.append("0");
            }
            sb.append(s).append(" ");
        }
        return sb.toString();
    }

    private synchronized void showLog(final String msg) {
        if (mSemaphore.tryAcquire()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.e(getClass().getSimpleName(), msg);
                    mLogs.add(msg);
                    if (mLogs.size() > 50) {
                        mLogs.remove(0);
                    }
                    mAdapter.notifyDataSetChanged();
                    mListView.smoothScrollToPosition(Integer.MAX_VALUE);
                    mSemaphore.release();
                }
            });
        }


    }

    public void toggleNotification(View view) {
        final boolean isToTurnOn = !isNotificationOn;
        TgiBleManager.getInstance().toggleNotification(
                Constants.MASTER_SERVICE_UUID,
                Constants.STATUS_CHAR_UUID,
                Constants.STATUS_DESCRIPTOR_UUID,
                isToTurnOn,
                new TgiToggleNotificationCallback() {
                    @Override
                    public void onToggleNotificationSuccess(BluetoothGattDescriptor descriptor) {
                        super.onToggleNotificationSuccess(descriptor);
                        isNotificationOn = isToTurnOn;
                        showLog(isNotificationOn ? "通知重新打开了" : "通知关闭了");
                    }

                    @Override
                    public void onCharChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                        super.onCharChanged(gatt, characteristic);
                        byte[] value = characteristic.getValue();
                        String temp = convertToHexString(value);
                        showLog("onCharChanged: " + temp);
                    }

                    @Override
                    public void onError(String errorMsg) {
                        super.onError(errorMsg);
                        showLog(errorMsg);
                    }
                }
        );
    }

    public void write(View view) {
        TgiBleManager.getInstance().writeCharacteristic(
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20},
                Constants.MASTER_SERVICE_UUID,
                Constants.FUNCTION_CHAR_UUID,
                new TgiWriteCharCallback() {
                    @Override
                    public void onWriteSuccess(BluetoothGattCharacteristic characteristic) {
                        super.onWriteSuccess(characteristic);
                        showLog("char被写入了：" + convertToHexString(characteristic.getValue()));
                    }

                    @Override
                    public void onWriteFailed(String errorMsg) {
                        super.onWriteFailed(errorMsg);
                        showLog("char 写入失败:" + errorMsg);
                    }
                }
        );
    }

    public void read(View view) {
        TgiBleManager.getInstance().readCharacteristic(
                Constants.MASTER_SERVICE_UUID,
                Constants.FUNCTION_CHAR_UUID,
                new TgiReadCharCallback() {
                    @Override
                    public void onCharRead(BluetoothGattCharacteristic btChar, byte[] value) {
                        super.onCharRead(btChar, value);
                        showLog("char被读取了：" + convertToHexString(value));
                    }

                    @Override
                    public void onError(String errorMsg) {
                        super.onError(errorMsg);
                        showLog("onError:" + errorMsg);
                    }

                }
        );

    }
}
