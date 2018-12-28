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
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private DevicePairingStatesReceiver mPairingStatesReceiver;
    private DeviceParingStateListener mDeviceParingStateListener;
    private BluetoothGatt mBtGatt;
    private Semaphore mConnectSwitch = new Semaphore(1);
    private TgiBtGattCallback mTgiBtGattCallback;
    private AtomicBoolean mIsConnectingDeviceByUser = new AtomicBoolean(false);


    //bindService 生命流程1
    //startService 生命流程1
    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.showLog("service is created.");
        mBleClientModel = new BleClientModel();
        mBtEnableState = mBleClientModel.isBtEnabled() ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF;
        //本机蓝牙初始化第一步：监听本机蓝牙打开状态
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
        LogUtils.showLog("service is bonded.");
        //本机蓝牙初始化第二步：检查蓝牙是否被打开了，如果没有，现在打开。
        if (mBtEnableState == BluetoothAdapter.STATE_OFF) {
            enableBt();
        }
        //本机蓝牙初始化第三步：返回binder给TgiBleManager
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
        if (mBtGatt != null) {
            mBtGatt.close();
            mBtGatt = null;
        }
        if (mTgiBtGattCallback != null) {
            mTgiBtGattCallback.clear();
            mTgiBtGattCallback = null;
        }
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


    protected class TgiBleServiceBinder extends Binder {

        public void startScanDevice(final TgiBleScanCallback callback)  {
            try {
                checkBtEnableBeforeProceed(new Runnable() {
                    @Override
                    public void run() {
                        mBleClientModel.startScanBtDevices(callback);
                    }
                });
            } catch (BtNotEnabledException e) {
                callback.onError(e.getMessage());
            }

        }

        public void stopScanDevice(final TgiBleScanCallback callback)  {
            try {
                checkBtEnableBeforeProceed(new Runnable() {
                    @Override
                    public void run() {
                        mBleClientModel.stopScanBtDevices(callback);
                    }
                });
            } catch (BtNotEnabledException e) {
                callback.onError(e.getMessage());
            }
        }

        //蓝牙配对
        public void pairDevice(final BluetoothDevice device, final DeviceParingStateListener listener) {
            try {
                checkBtEnableBeforeProceed(new Runnable() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void run() {
                        //蓝牙配对第1步:先检查是否已经配对了。如果以前已经配对了，直接返回。
                        int bondState = device.getBondState();
                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            listener.onParingSessionBegin();
                            listener.onDevicePairingStateChanged(device, bondState, bondState);
                            listener.onParingSessionEnd();
                            return;
                        }

                        //蓝牙配对第2步：自定义开始的准备工作（如显示进度圈等）
                        listener.onParingSessionBegin();
                        //蓝牙配对第3步：注册广播接受者监听配对结果
                        mDeviceParingStateListener = listener;
                        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                        mPairingStatesReceiver = new DevicePairingStatesReceiver();
                        registerReceiver(mPairingStatesReceiver, intentFilter);
                        //蓝牙配对第4步：开始正式配对,配对结果通过广播接受者知悉。
                        boolean isInitSuccess = mBleClientModel.pairDevice(device);
                        if (!isInitSuccess) {
                            //如果启动配对失败，这里报错，并且释放资源。
                            listener.onError("Fail to initiate device pairing.");
                            listener.onParingSessionEnd();
                            mDeviceParingStateListener = null;
                            if (mPairingStatesReceiver != null) {
                                unregisterReceiver(mPairingStatesReceiver);
                                mPairingStatesReceiver = null;
                            }
                        }
                    }
                });
            } catch (BtNotEnabledException e) {
                listener.onError(e.getMessage());
            }
        }

        //已知蓝牙设备地址，连接蓝牙设备
        public void connectDevice(String deviceAddress, final BtDeviceConnectListener listener) {
            connectDevice(
                    mBleClientModel.getDeviceByAddress(deviceAddress),
                    listener
            );
        }

        //已知蓝牙设备对象，连接蓝牙设备
        public void connectDevice(final BluetoothDevice device, final BtDeviceConnectListener listener) {
            //连接蓝牙设备第一步：判断是否正在连接，如果是，放弃当前操作。
            if (!mConnectSwitch.tryAcquire()) {
                return;
            }

            if (mIsConnectingDeviceByUser.compareAndSet(false, true)) {
                //连接蓝牙设备第二步：判断是否已经有连接好的，如果有，检查一下是否就是目标设备，
                //如果是，直接走回调，返回成功；
                //如果不是，关闭之前的连接，重新开始新的连接。
                if (mBtGatt != null) {
                    String address = mBtGatt.getDevice().getAddress();
                    if (address.equals(device.getAddress())) {
                        // 因为当前已经连接上了目标设备，不需要重新连接，这里跑一下listener的成功回调即可。
                        // 为什么要走一遍？因为调用方发起连接请求时期待回调里能返回东西，否则回调里的逻辑可能触发不了。
                        mConnectSwitch.release();
                        mIsConnectingDeviceByUser.set(false);
                        listener.onConnectSuccess(mBtGatt);
                        return;
                    } else {
                        mBtGatt.close();
                        mBtGatt = null;
                    }
                }
                //这个回调用来监听蓝牙设备连接后的各种情况
                BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        super.onConnectionStateChange(gatt, status, newState);
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            //连接蓝牙设备第四步：连接成功，读取服务列表，否则后面无法顺利读写特性。
                            gatt.discoverServices();
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            //连接失败，退出。
                            //这是启动连接时失败的情况。关闭流程，返回错误。
                            if (mIsConnectingDeviceByUser.get()) {
                                gatt.close();
                                mConnectSwitch.release();
                                mIsConnectingDeviceByUser.set(false);
                                listener.onConnectFail("Fail to connect target device on connecting stage.");
                            } else {
                                //这是由于不可预知的原因造成连接中断，自动重连。
                                //自动重连只需一步：重新连接
                                if (mBtGatt != null) {
                                    mBtGatt.connect();
                                }
                            }
                        }
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        super.onServicesDiscovered(gatt, status);
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            //连接蓝牙设备第五步：连接成功，读取服务列表成功。至此1-5步，连接流程结束。
                            mBtGatt = gatt;
                            mConnectSwitch.release();
                            mIsConnectingDeviceByUser.set(false);
                            listener.onConnectSuccess(gatt);
                        } else if (status == BluetoothGatt.GATT_FAILURE) {
                            //连接失败，退出。
                            gatt.close();
                            mConnectSwitch.release();
                            mIsConnectingDeviceByUser.set(false);
                            listener.onConnectFail("Target device is connected, but fails to discover services.");
                        }
                    }
                };
                mTgiBtGattCallback = new TgiBtGattCallback(bluetoothGattCallback);
                try {
                    checkBtBondedBeforeProceed(device, new Runnable() {
                        @Override
                        public void run() {
                            //连接蓝牙设备第三步：正式连接
                            device.connectGatt(
                                    getApplicationContext(),
                                    false,
                                    //mTgiBtGattCallback，不只是连接时用，BluetoothGatt后续的读写操作也在这里获取相关回调。
                                    mTgiBtGattCallback);
                        }
                    });
                } catch (Exception e) {
                    //把异常都放到回调中去。
                    e.printStackTrace();
                    mConnectSwitch.release();
                    mIsConnectingDeviceByUser.set(false);
                    listener.onConnectFail(e.getMessage());
                }
            } else {
                mConnectSwitch.release();
            }

        }

        //写入特性
        public void writeChar(final byte[] data, final String serviceUUID,
                              final String charUUID, final TgiWriteCharCallback callback) {
            //写入特性第一步：检查蓝牙模块状态是否正常，是否已经连接上
            try {
                checkBtConnectionBeforeProceed(new Runnable() {
                    @Override
                    public void run() {
                        //写入特性第二步：检查对应的服务及特性是否存在
                        BluetoothGattService service = mBtGatt.getService(UUID.fromString(serviceUUID));
                        if (service == null) {
                            callback.onWriteFailed("Target service cannot be reached.");
                            return;
                        }
                        BluetoothGattCharacteristic btChar = service.getCharacteristic(UUID.fromString(charUUID));
                        if (btChar == null) {
                            callback.onWriteFailed("Target characteristic cannot be reached.");
                            return;
                        }
                        //写入特性第三步：正式写入
                        TgiWriteCharSession session = new TgiWriteCharSession(
                                mBtGatt,
                                btChar,
                                mTgiBtGattCallback);
                        session.write(data, callback);
                        //后续在TgiBtGattCallback的onCharacteristicWrite回调中进行。
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                //把所有异常的信息都返回到回调中。
                callback.onWriteFailed(e.getMessage());
            }


        }

        //读取特性
        public void readChar(final String serviceUUID, final String charUUID,
                             final TgiReadCharCallback callback) {
            //读取特性第一步：检查蓝牙状态是否正常，是否已经连接上
            try {
                checkBtConnectionBeforeProceed(new Runnable() {
                    @Override
                    public void run() {
                        //读取特性第二步：检查对应的服务及特性是否存在
                        BluetoothGattService service = mBtGatt.getService(UUID.fromString(serviceUUID));
                        if (service == null) {
                            callback.onError("Target service cannot be reached.");
                            return;
                        }
                        BluetoothGattCharacteristic btChar = service.getCharacteristic(UUID.fromString(charUUID));
                        if (btChar == null) {
                            callback.onError("Target characteristic cannot be reached.");
                            return;
                        }
                        //读取特性第三步：正式开始读取
                        TgiReadCharSession session = new TgiReadCharSession(
                                mBtGatt,
                                btChar,
                                mTgiBtGattCallback);
                        session.read(callback);
                        //后续在TgiBtGattCallback的onCharacteristicRead回调中进行。

                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
            }
        }

        //注册/取消注册通知
        public void toggleNotification(final String serviceUUID, final String charUUID, final String descUUID,
                                       final boolean isToTurnOn, final TgiToggleNotificationCallback callback) {
            //注册/取消注册通知第一步：检查蓝牙状态是否正常，是否已经连接上
            try {
                checkBtConnectionBeforeProceed(new Runnable() {
                    @Override
                    public void run() {
                        //注册/取消注册通知第二步：检查对应的服务，特性和描述是否存在
                        BluetoothGattService service = mBtGatt.getService(UUID.fromString(serviceUUID));
                        if (service == null) {
                            callback.onError("Target service cannot be reached.");
                            return;
                        }

                        BluetoothGattCharacteristic btChar = service.getCharacteristic(UUID.fromString(charUUID));
                        if (btChar == null) {
                            callback.onError("Target characteristic cannot be reached.");
                            return;
                        }

                        BluetoothGattDescriptor btDesc = btChar.getDescriptor(UUID.fromString(descUUID));
                        if (btDesc == null) {
                            callback.onError("Target descriptor cannot be reached.");
                            return;
                        }

                        //注册/取消注册通知第三步：正式操作注册/取消注册，后续在TgiBtGattCallback的onDescriptorWrite回调中进行。
                        //至此1-3步完成流程。
                        TgiToggleNotificationSession session = new TgiToggleNotificationSession(
                                mBtGatt,
                                btDesc,
                                isToTurnOn,
                                mTgiBtGattCallback);
                        session.start(callback);

                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
            }
        }

        //断开连接
        public void disConnectDevice() {
            if (mBtGatt != null) {
                mBtGatt.close();
                mBtGatt = null;
            }
        }
    }

    private void checkBtConnectionBeforeProceed(Runnable onProceed)
            throws BtNotConnectedYetException, BtNotBondedException, BtNotEnabledException {
        if (mBtGatt == null) {
            throw new BtNotConnectedYetException();
        }
        checkBtBondedBeforeProceed(mBtGatt.getDevice(), onProceed);
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
            //本机蓝牙 初始化第四步：实时监听蓝牙启动状态，如果发现被关闭了，将重新打开。
            //至此1-4步完成了本机蓝牙的初始化。
            mBtEnableState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            if (mBtEnableState == BluetoothAdapter.STATE_OFF) {
                enableBt();
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
                mDeviceParingStateListener.onDevicePairingStateChanged(device, previousState, currentState);
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
