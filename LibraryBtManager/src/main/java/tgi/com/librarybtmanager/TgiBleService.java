package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 20/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>BtLibraryDemo</i>
 * <p><b>Description:</b></p>
 */
public class TgiBleService extends Service {
    private BleClientModel mBleClientModel;
    private TgiBtEnableStatesReceiver mBtConnStatesReceiver;
    private int mBtEnableState;
    private Handler mHandler;
    private BtEnableStateListener mBtEnableStateListener;
    private DevicePairingStatesReceiver mPairingStatesReceiver;
    private DeviceParingStateListener mDeviceParingStateListener;
    private BluetoothGatt mBluetoothGatt;
    private Semaphore mConnectSwitch = new Semaphore(1);
    private TgiWriteCharCallback mTgiWriteCharCallback;
    private Semaphore mWriteCharSwitch = new Semaphore(1);


    //bindService 生命流程1
    //startService 生命流程1
    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.showLog("service is created.");
        mBleClientModel = new BleClientModel();
        mBtEnableState = mBleClientModel.isBtEnabled() ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF;
        mHandler = new Handler();
        //1,监听本机蓝牙打开状态
        registerReceivers();
    }

    private void registerReceivers() {
        mBtConnStatesReceiver = new TgiBtEnableStatesReceiver();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBtConnStatesReceiver, filter);
    }

    //startService 生命流程2
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    //startService 生命流程3
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    //bindService 生命流程2
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        LogUtils.showLog("service is bounded.");
        //4,检查蓝牙是否被打开了，如果没有，现在打开。
        //为什么要500毫秒后再执行？这是为了保证mBinder对象被先返回给TgiBleManager
        //如果立刻开始执行，有可能在mBinder被返回且设置好回调前就执行完毕了，这样有些回调会触发不了。
        //至此1-4步完成了本机蓝牙的初始化。
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBtEnableState == BluetoothAdapter.STATE_OFF) {
                    enableBt();
                }
            }
        }, 500);
        //2，先返回binder给TgiBleManager
        return new TgiBleServiceBinder();
    }

    /**
     * 打开蓝牙，相关结果会通过广播接收者{@link TgiBleService#mBtConnStatesReceiver}得知
     */
    private void enableBt() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    //bindService 生命流程3
    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    //bindService 生命流程4
    //startService 生命流程4
    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceivers();
        LogUtils.showLog("service is destroyed.");
    }

    private void unregisterReceivers() {
        if (mBtConnStatesReceiver != null) {
            unregisterReceiver(mBtConnStatesReceiver);
            mBtConnStatesReceiver = null;
        }

        if (mPairingStatesReceiver != null) {
            unregisterReceiver(mPairingStatesReceiver);
            mPairingStatesReceiver = null;
        }
    }


    class TgiBleServiceBinder extends Binder {
        //3，TgiBleManager端会先设置回调，以保证及时收到TgiBtEnableStatesReceiver返回的数据
        void setBtEnableStateListener(BtEnableStateListener callback) {
            mBtEnableStateListener = callback;
        }


        void enableBt() {
            TgiBleService.this.enableBt();
        }

        void startScanDevice(final TgiBleScanCallback callback) throws BtNotEnabledException {
            checkBtEnableBeforeProceed(new Runnable() {
                @Override
                public void run() {
                    mBleClientModel.startScanBtDevices(callback);
                }
            });

        }

        void stopScanDevice(final TgiBleScanCallback callback) throws BtNotEnabledException {
            checkBtEnableBeforeProceed(new Runnable() {
                @Override
                public void run() {
                    mBleClientModel.stopScanBtDevices(callback);
                }
            });
        }

        //蓝牙配对
        void pairDevice(final BluetoothDevice device, final DeviceParingStateListener listener) throws BtNotEnabledException {
            checkBtEnableBeforeProceed(new Runnable() {
                @SuppressLint("MissingPermission")
                @Override
                public void run() {
                    //蓝牙配对第1步:先检查是否已经配对了。如果以前已经配对了，直接返回。
                    int bondState = device.getBondState();
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        listener.onParingSessionBegin();
                        listener.onDevicePaired(device, bondState, bondState);
                        listener.onParingSessionEnd();
                        return;
                    }
                    //蓝牙配对第2步：注册广播接受者监听配对结果
                    mDeviceParingStateListener = listener;
                    IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                    mPairingStatesReceiver = new DevicePairingStatesReceiver();
                    registerReceiver(mPairingStatesReceiver, intentFilter);
                    //蓝牙配对第3步：自定义开始的准备工作（如显示进度圈等）
                    listener.onParingSessionBegin();
                    //蓝牙配对第4步：开始正式配对
                    mBleClientModel.pairDevice(device);
                }
            });
        }

        //连接蓝牙设备
        void connectDevice(final BluetoothDevice device, final BtDeviceConnectStateListener listener)
                throws BtNotBondedException, BtNotEnabledException {
            //连接蓝牙设备第一步：判断是否正在连接，如果是，放弃当前操作。
            if (!mConnectSwitch.tryAcquire()) {
                return;
            }
            //连接蓝牙设备第二步：判断是否已经有连接好的，如果有，检查一下是否就是目标设备，
            //如果是，放弃当前操作；
            //如果不是，关闭之前的连接，重新开始新的连接。
            if (mBluetoothGatt != null) {
                String address = mBluetoothGatt.getDevice().getAddress();
                if (address.equals(device.getAddress())) {
                    mConnectSwitch.release();
                    return;
                } else {
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                }
            }
            checkBtBondedBeforeProceed(device, new Runnable() {
                @Override
                public void run() {
                    //连接蓝牙设备第三步：正式连接
                    device.connectGatt(
                            getApplicationContext(),
                            false,
                            //BluetoothGattCallback很重要，不只是连接时用，BluetoothGatt后续的读写操作也在这里获取相关回调。
                            new BluetoothGattCallback() {
                                @Override
                                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                                    super.onConnectionStateChange(gatt, status, newState);
                                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                                        //连接蓝牙设备第四步：连接成功，读取服务列表，否则后面无法顺利读写特征。
                                        gatt.discoverServices();
                                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                                        //连接失败，退出。
                                        gatt.close();
                                        listener.onConnectFail("Fail to connect target device on connecting stage.");
                                        listener.onConnectSessionEnds();
                                        mConnectSwitch.release();
                                    }
                                }

                                @Override
                                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                                    super.onServicesDiscovered(gatt, status);
                                    if (status == BluetoothGatt.GATT_SUCCESS) {
                                        //连接蓝牙设备第五步：连接成功，读取服务列表成功。至此1-5步，连接流程结束。
                                        mBluetoothGatt = gatt;
                                        listener.onConnect(device);
                                        listener.onConnectSessionEnds();
                                    } else if (status == BluetoothGatt.GATT_FAILURE) {
                                        //连接失败，退出。
                                        gatt.close();
                                        listener.onConnectFail("Target device is connected, but fails to discover services.");
                                        listener.onConnectSessionEnds();
                                        mConnectSwitch.release();
                                    }
                                }

                                @Override
                                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                                    super.onCharacteristicRead(gatt, characteristic, status);
                                }

                                @Override
                                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                                    super.onCharacteristicWrite(gatt, characteristic, status);
                                    //写入特性第四步：判断是否写入成功。
                                    if (mTgiWriteCharCallback != null) {
                                        if (status == BluetoothGatt.GATT_SUCCESS) {
                                            mTgiWriteCharCallback.onWriteComplete(characteristic);
                                        } else {
                                            mTgiWriteCharCallback.onWriteFailed("Target characteristic write fails.");
                                        }
                                        mTgiWriteCharCallback = null;
                                        //写入特性第五步：解锁。至此1-5步，写入特性流程结束。
                                        mWriteCharSwitch.release();
                                    }

                                }

                                @Override
                                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                                    super.onCharacteristicChanged(gatt, characteristic);
                                }

                                @Override
                                public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                                    super.onDescriptorRead(gatt, descriptor, status);
                                }

                                @Override
                                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                                    super.onDescriptorWrite(gatt, descriptor, status);
                                }
                            }
                    );
                }
            });
        }

        //写入特性
        void writeChar(final byte[] data, final String serviceUUID, final String charUUID, final TgiWriteCharCallback callback)
                throws BtNotConnectedYetException, BtNotEnabledException, BtNotBondedException {
            //写入特性第一步：检查蓝牙模块状态是否正常，是否已经连接上
            checkBtConnectionBeforeProceed(new Runnable() {
                @Override
                public void run() {
                    //判断是否正在写入中，如果是，放弃操作。
                    if (mWriteCharSwitch.tryAcquire()) {
                        mTgiWriteCharCallback = callback;
                        //写入特性第二步：检查对应的服务及特性是否存在
                        BluetoothGattService service = mBluetoothGatt.getService(UUID.fromString(serviceUUID));
                        if (service == null) {
                            callback.onWriteFailed("Target service cannot be reached.");
                            mWriteCharSwitch.release();
                            return;
                        }
                        BluetoothGattCharacteristic btChar = service.getCharacteristic(UUID.fromString(charUUID));
                        if (btChar == null) {
                            callback.onWriteFailed("Target characteristic cannot be reached.");
                            mWriteCharSwitch.release();
                            return;
                        }
                        //写入特性第三步：正式写入
                        boolean setValue = btChar.setValue(data);
                        if (!setValue) {
                            callback.onWriteFailed("Request value cannot be stored locally.");
                            mWriteCharSwitch.release();
                        }
                        //后续在BluetoothGattCallback的onCharacteristicWrite回调中进行。
                    }
                }
            });


        }

        //读取特性
        void readChar(String serviceUUID, String charUUID, TgiReadCharCallback callback) throws
                BtNotConnectedYetException, BtNotEnabledException, BtNotBondedException {
            checkBtConnectionBeforeProceed(new Runnable() {
                @Override
                public void run() {

                }
            });
        }
    }

    private void checkBtConnectionBeforeProceed(Runnable onProceed)
            throws BtNotConnectedYetException, BtNotBondedException, BtNotEnabledException {
        if (mBluetoothGatt == null) {
            throw new BtNotConnectedYetException();
        }
        checkBtBondedBeforeProceed(mBluetoothGatt.getDevice(), onProceed);
    }

    @SuppressLint("MissingPermission")
    private void checkBtBondedBeforeProceed(BluetoothDevice device, Runnable onProceed)
            throws BtNotBondedException, BtNotEnabledException {

        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            throw new BtNotBondedException();
        }
        checkBtEnableBeforeProceed(onProceed);
    }

    private void checkBtEnableBeforeProceed(Runnable onProceed) throws BtNotEnabledException {
        if (mBtEnableState != BluetoothAdapter.STATE_ON) {
            throw new BtNotEnabledException();
        } else {
            onProceed.run();
        }
    }

    private class TgiBtEnableStatesReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            //     * {@link #STATE_OFF},
            //     * {@link #STATE_TURNING_ON},
            //     * {@link #STATE_ON},
            //     * {@link #STATE_TURNING_OFF},
            mBtEnableState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            int previous = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
            if (mBtEnableState != -1 && previous != -1) {
                if (mBtEnableStateListener != null) {
                    mBtEnableStateListener.onBtAvailabilityChanged(previous, mBtEnableState);
                }
            }
        }
    }

    private class DevicePairingStatesReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            //Broadcast Action: Indicates a change in the bond state of a remote device. For example, if a device is bonded (paired).
            //Always contains the extra fields EXTRA_DEVICE, EXTRA_BOND_STATE and EXTRA_PREVIOUS_BOND_STATE.
            //Requires BLUETOOTH to receive.
            //Constant Value: "android.bluetooth.device.action.BOND_STATE_CHANGED"

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int currentState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            int previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
            if (mDeviceParingStateListener != null) {
                //蓝牙配对第5步：获取配对结果
                mDeviceParingStateListener.onDevicePaired(device, previousState, currentState);
                //当最新结果为以下两种时，表示配对结果已经确定了，流程结束。
                if (currentState == BluetoothDevice.BOND_BONDED || currentState == BluetoothDevice.BOND_NONE) {
                    mDeviceParingStateListener.onParingSessionEnd();
                    mDeviceParingStateListener = null;
                    //蓝牙配对第6步：释放资源，流程结束。
                    if (mPairingStatesReceiver != null) {
                        unregisterReceiver(mPairingStatesReceiver);
                        mPairingStatesReceiver = null;
                    }
                }
            }
        }
    }


}
