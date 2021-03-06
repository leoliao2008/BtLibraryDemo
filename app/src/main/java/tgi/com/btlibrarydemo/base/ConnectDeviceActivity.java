package tgi.com.btlibrarydemo.base;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import tgi.com.btlibrarydemo.Constants;
import tgi.com.btlibrarydemo.R;
import tgi.com.btlibrarydemo.activities.BaseActionBarActivity;
import tgi.com.librarybtmanager.TgiBleManager;
import tgi.com.librarybtmanager.TgiBleScanCallback;
import tgi.com.librarybtmanager.TgiBtDeviceConnectListener;
import tgi.com.librarybtmanager.TgiDeviceParingStateListener;
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
    private byte[] mWriteData = new byte[20];


    public static void start(Context context, String btDeviceAddr) {
        Intent starter = new Intent(context, ConnectDeviceActivity.class);
        starter.putExtra(BT_DEVICE_ADDRESS, btDeviceAddr);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Arrays.fill(mWriteData, (byte) 125);
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
                    TgiBleManager.getInstance().removePairedDeviceWithoutUserConsent(mConnectAddress);
                    mConnectAddress = device.getAddress();
                    pairAndConnect();
                }
            }
        });

        scanDevices();
        connectDevice(mConnectAddress);

    }

    private void pairAndConnect() {
        TgiBleManager.getInstance().pairDeviceWithoutUserConsent(
                mConnectAddress,
                new TgiDeviceParingStateListener() {
                    @Override
                    public void onDevicePairingStateChanged(BluetoothDevice device, int previousState, int currentState) {
                        super.onDevicePairingStateChanged(device, previousState, currentState);
                        showLog("pre: "+TgiBleManager.getInstance().getBondSateDescription(previousState));
                        showLog("current: "+TgiBleManager.getInstance().getBondSateDescription(currentState));
                    }

                    @Override
                    public void onParingSessionEnd(int endState) {
                        super.onParingSessionEnd(endState);
                        showLog("onParingSessionEnd: "+TgiBleManager.getInstance().getBondSateDescription(endState));
                        if (endState != BluetoothDevice.BOND_BONDED) {
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    pairAndConnect();
                                }
                            }, 1000);
                        } else {
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    connectDevice(mConnectAddress);
                                    mPairedDevicesAdapter.updateBondedListAndNotifyDataSetChanged();
                                }
                            }, 1000);
                        }

                    }
                });
    }

    private void scanDevices() {
        TgiBleManager.getInstance().startScanBtDevices(new TgiBleScanCallback() {
            @Override
            public void onPreScan() {
                super.onPreScan();
                showLog("scan starts");
                mDeviceList.clear();
                mDeviceList.addAll(TgiBleManager.getInstance().getBondedDevices());
                mPairedDevicesAdapter.updateBondedListAndNotifyDataSetChanged();
            }

            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                super.onLeScan(device, rssi, scanRecord);
                if (TextUtils.isEmpty(device.getName())) {
                    return;
                }
                if (!mDeviceList.contains(device)) {
                    mDeviceList.add(device);
                    mPairedDevicesAdapter.updateBondedListAndNotifyDataSetChanged();
                }
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
        TgiBleManager.getInstance().stopBtService(this);
    }

    private void connectDevice(final String deviceAddress) {
        mLogs.clear();
        mAdapter.notifyDataSetChanged();
        TgiBleManager.getInstance().connectDevice(deviceAddress, new TgiBtDeviceConnectListener() {
            @Override
            public void onConnectSuccess(final BluetoothGatt gatt) {
                super.onConnectSuccess(gatt);
                showLog("蓝牙连接成功，开始设置通知。");
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

            @Override
            public void onDisconnectedBecauseDeviceUnbound(BluetoothDevice device) {
                super.onDisconnectedBecauseDeviceUnbound(device);
                showLog("本地蓝牙地址："+deviceAddress);
                showLog("断掉的蓝牙地址："+device.getAddress());
                //MC2.1自动解绑了
                showLog("MC2.1自动解绑了,开始重新配对");
                pairAndConnect();

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
                mWriteData,
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
