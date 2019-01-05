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

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
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
    private volatile BluetoothGatt mBtGatt;
    private Semaphore mConnectSwitch = new Semaphore(1);
    private TgiBtGattCallback mTgiBtGattCallback;
    private AtomicBoolean mIsConnecting = new AtomicBoolean(false);
    private TgiBleServiceBinder mTgiBleServiceBinder;
    private Handler mHandler;


    //bindService 生命流程1
    //startService 生命流程1
    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.showLog("service is created.");
        mHandler = new Handler();
        mBleClientModel = new BleClientModel();
        mBtEnableState = mBleClientModel.isBtEnabled() ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF;
        //本机蓝牙初始化第一步：监听蓝牙各种状态
        registerReceivers();
    }

    private void registerReceivers() {
        //监听蓝牙模块开关
        mBtConnStatesReceiver = new TgiBtEnableStatesReceiver();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBtConnStatesReceiver, filter);

        //监听蓝牙配对变更情况
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mPairingStatesReceiver = new DevicePairingStatesReceiver();
        registerReceiver(mPairingStatesReceiver, intentFilter);
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
        mTgiBleServiceBinder = new TgiBleServiceBinder();
        return mTgiBleServiceBinder;
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
        if (mTgiBleServiceBinder != null) {
            mTgiBleServiceBinder.disConnectDevice();
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

        public void startScanDevice(TgiBleScanCallback callback) {
            try {
                if (checkBtEnableBeforeProceed()) {
                    mBleClientModel.startScanBtDevices(callback);
                }
            } catch (BtNotEnabledException e) {
                callback.onError(e.getMessage());
            }
        }

        public void stopScanDevice(TgiBleScanCallback callback) {
            try {
                if (checkBtEnableBeforeProceed()) {
                    mBleClientModel.stopScanBtDevices(callback);
                }
            } catch (BtNotEnabledException e) {
                callback.onError(e.getMessage());
            }
        }

        public ArrayList<BluetoothDevice> getBondedDevices() {
            return mBleClientModel.getBondedDevices();
        }

        //蓝牙配对
        public void pairDevice(final BluetoothDevice device, final DeviceParingStateListener listener) {
            try {
                if (checkBtEnableBeforeProceed()) {
                    //蓝牙配对第1步:先检查是否已经配对了。如果以前已经配对了，直接返回。
                    @SuppressLint("MissingPermission")
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
            } catch (BtNotEnabledException e) {
                listener.onError(e.getMessage());
            }
        }

        //利用反射配对蓝牙
        public boolean pairDeviceWithoutUserConsent(BluetoothDevice device) {
            return mBleClientModel.pairDeviceWithoutUserConsent(device);
        }

        public boolean pairDeviceWithoutUserConsent(String deviceAddress) {
            return pairDeviceWithoutUserConsent(mBleClientModel.getDeviceByAddress(deviceAddress));
        }

        //利用反射取消配对了的蓝牙
        public boolean removePairedDeviceWithoutUserConsent(BluetoothDevice device) {
            return mBleClientModel.removePairedDeviceWithoutUserConsent(device);
        }

        public boolean removePairedDeviceWithoutUserConsent(String deviceAddress) {
            return removePairedDeviceWithoutUserConsent(mBleClientModel.getDeviceByAddress(deviceAddress));
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
                showLog("正在连接，放弃本次操作。");
                return;
            }

            if (mIsConnecting.compareAndSet(false, true)) {
                //连接蓝牙设备第二步：判断是否已经有连接好的，如果有，检查一下是否就是目标设备，
                //如果是，直接走回调，返回成功；
                //如果不是，关闭之前的连接，重新开始新的连接。
                if (mBtGatt != null) {
                    String address = mBtGatt.getDevice().getAddress();
                    if (address.equals(device.getAddress())) {
                        // 因为当前已经连接上了目标设备，不需要重新连接，这里跑一下listener的成功回调即可。
                        // 为什么要走一遍？因为调用方发起连接请求时期待回调能被触发，否则回调里的逻辑可能触发不了。
                        mConnectSwitch.release();
                        mIsConnecting.set(false);
                        listener.onConnectSuccess(mBtGatt);
                        return;
                    } else {
                        disConnectDevice();
                    }
                }
                //这个回调用来监听蓝牙设备连接后的各种情况
                BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        super.onConnectionStateChange(gatt, status, newState);
                        try {
                            //连接状态发生变化时，第一时间检查配对是否正常。
                            if (checkBtBondedBeforeProceed(device)) {
                                if (newState == BluetoothGatt.STATE_CONNECTED) {
                                    showLog("连接上蓝牙了，开始读取服务列表。");
                                    //连接蓝牙设备第四步：连接成功，读取服务列表，否则后面无法顺利读写特性。
                                    gatt.discoverServices();
                                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                                    //连接失败。
                                    final BluetoothDevice device = gatt.getDevice();
                                    gatt.close();
                                    if (mIsConnecting.get()) {
                                        showLog("连接失败。");
                                        mConnectSwitch.release();
                                        mIsConnecting.set(false);
                                        showLog("自动重连...");
                                        //连接失败1000毫秒后重新连接，这里不管是连接中断导致的自动重连还是首次连接，都是同样的逻辑。
                                        mHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (mTgiBleServiceBinder != null) {
                                                    mTgiBleServiceBinder.connectDevice(device, listener);
                                                }
                                            }
                                        }, 1000);

                                    } else {
                                        showLog("连接中断。");
                                        //这是由于信号不良或对方蓝牙设备重启造成连接中断，自动重连。
                                        if (mBtGatt != null && mTgiBtGattCallback != null) {
                                            showLog("信号差或者远程蓝牙设备被关闭了。");
                                            Set<Map.Entry<String, TgiToggleNotificationSession>> notifications
                                                    = mTgiBtGattCallback.getCurrentNotificationCallbacks().entrySet();
                                            showLog("当前注册通知数量：" + notifications.size());
                                            showLog("开始重新连接");
                                            reconnect(device, notifications);
                                        }
                                    }

                                }
                            }
                        } catch (BtNotBondedException e) {
                            e.printStackTrace();
                            //这里只可能是连接成功后，在传输数据的过程中用户取消了配对，这时要终止连接。重复连接已经没有意义。
                            showLog("传输数据到一半的时候，被取消配对了，中断连接等待进一步指示。");
                            disConnectDevice();
                        } catch (BtNotEnabledException e) {
                            e.printStackTrace();
                            //蓝牙模块默认服务启动时会打开，且在广播接收者那里已经监听蓝牙模块开关了，
                            // 当关闭时会有自动重连逻辑，这里不用考虑重连的情况。
                            showLog("蓝牙模块关闭了，无法继续连接。");
                        }

                    }

                    @Override
                    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                        super.onServicesDiscovered(gatt, status);
                        mConnectSwitch.release();
                        mIsConnecting.set(false);
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            //连接蓝牙设备第五步：连接成功，读取服务列表成功。至此1-5步，连接流程结束。
                            mBtGatt = gatt;
                            listener.onConnectSuccess(gatt);

                        } else if (status == BluetoothGatt.GATT_FAILURE) {
                            //连接失败，一秒后重新连接
                            showLog("连接时无法读取服务列表，连接失败。开始重新连接......");
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    gatt.discoverServices();
                                }
                            }, 1000);

                        }
                    }
                };
                mTgiBtGattCallback = new TgiBtGattCallback(bluetoothGattCallback);
                try {
                    if (checkBtBondedBeforeProceed(device)) {
                        //连接蓝牙设备第三步：正式连接
                        device.connectGatt(
                                getApplicationContext(),
                                false,
                                mTgiBtGattCallback);//mTgiBtGattCallback不只是连接时用，BluetoothGatt后续的读写操作也在这里获取相关回调。
                    }
                } catch (Exception e) {
                    //把异常都放到回调中去。
                    e.printStackTrace();
                    mConnectSwitch.release();
                    mIsConnecting.set(false);
                    showLog(e.getMessage());
                    //Exception包含了以下两种情况：
                    //1，蓝牙模块没有打开：如果在连接开始的时候，蓝牙模块已经是打开状态，那在连接成功后，如果蓝牙模块被关闭了，
                    // 本库会重新打开蓝牙，并重启连接和重设通知，这部分逻辑已经在onConnectionStateChange（）回调中写好了，在这里不需要做任何处理；
                    // 但如果一开始蓝牙就没启动将不再进行操作，这是因为本来这个后台服务在绑定的时候，就会检查蓝牙是否启动，如果没启动，会启动蓝牙。
                    // 也就是说，不会有蓝牙模块一开始就没启动的情况。如果有，那是意料之外的情况，这里终止连接是合理的，否则一直死循环，上层也无法开始新的
                    // 连接。
                    //2，远程蓝牙配对不上：配对和连接是两个独立的过程，必须先配对好再连接，这是上层应用应当处理的逻辑，因此不会有未配对好就连接的情况；
                    //这里抛出这个异常的情况还有一种：原先是配对好的，且连接上了，但后来因为某种原因取消配对了，连接中断，重连，发现配对不上，回到这里。
                    //这种情况下如果还是强行配对之前的设备，再强行连接之前的设备和重新设置通知，明显不合理。应当中断连接，待上层应用处理配对更新后的逻辑。
                    //综上所述，这里应该抛出异常，中断连接
                    listener.onConnectFail(e.getMessage());

                }
            } else {
                mConnectSwitch.release();
                showLog("mIsConnecting的值不符合逻辑，退出连接。");
            }
        }

        /**
         * 换一个相同型号的远程设备重新连接，之前的通知原封不动地自动重设。
         */
        public void swapDevice(String newDeviceAddress) {
            swapDevice(mBleClientModel.getDeviceByAddress(newDeviceAddress));
        }

        /**
         * 换一个相同型号的远程设备重新连接，之前的通知原封不动地自动重设。
         *
         * @param newDevice
         */
        public void swapDevice(BluetoothDevice newDevice) {
            try {
                if (checkBtEnableBeforeProceed() && mTgiBtGattCallback != null) {
                    showLog("开始更改设备。");
                    Set<Map.Entry<String, TgiToggleNotificationSession>> notifications
                            = mTgiBtGattCallback.getCurrentNotificationCallbacks().entrySet();
                    disConnectDevice();
                    reconnect(newDevice, notifications);
                }
            } catch (BtNotEnabledException e) {
                e.printStackTrace();
                showLog("蓝牙模块没打开，无法更改设备。");
            }
        }

        //写入特性
        public void writeChar(final byte[] data, final String serviceUUID,
                              final String charUUID, final TgiWriteCharCallback callback) {
            //写入特性第一步：检查蓝牙模块状态是否正常，是否已经连接上
            try {
                if (checkBtConnectionBeforeProceed()) {
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
            } catch (Exception e) {
                e.printStackTrace();
                //把所有异常的信息都返回到回调中。
                callback.onWriteFailed(e.getMessage());
                showLog("写入失败：" + e.getMessage());
            }

        }

        //读取特性
        public void readChar(final String serviceUUID, final String charUUID,
                             final TgiReadCharCallback callback) {
            //读取特性第一步：检查蓝牙状态是否正常，是否已经连接上
            try {
                if (checkBtConnectionBeforeProceed()) {
                    //读取特性第二步：检查对应的服务及特性是否存在
                    BluetoothGattService service = mBtGatt.getService(UUID.fromString(serviceUUID));
                    if (service == null) {
                        callback.onError("Target service cannot be reached.");
                        showLog("写入失败，无法找到服务：" + serviceUUID);
                        return;
                    }
                    BluetoothGattCharacteristic btChar = service.getCharacteristic(UUID.fromString(charUUID));
                    if (btChar == null) {
                        callback.onError("Target characteristic cannot be reached.");
                        showLog("写入失败，无法找到特征：" + charUUID);
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
                if (checkBtConnectionBeforeProceed()) {
                    //注册/取消注册通知第二步：检查对应的服务，特性和描述是否存在
                    BluetoothGattService service = mBtGatt.getService(UUID.fromString(serviceUUID));
                    if (service == null) {
                        callback.onError("Target service cannot be reached.");
                        showLog("无法找到指定服务" + serviceUUID);
                        return;
                    }

                    BluetoothGattCharacteristic btChar = service.getCharacteristic(UUID.fromString(charUUID));
                    if (btChar == null) {
                        callback.onError("Target characteristic cannot be reached.");
                        showLog("无法找到指定特性" + charUUID);
                        return;
                    }

                    BluetoothGattDescriptor btDesc = btChar.getDescriptor(UUID.fromString(descUUID));
                    if (btDesc == null) {
                        callback.onError("Target descriptor cannot be reached.");
                        showLog("无法找到指定描述" + descUUID);
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
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
                showLog("设置通知时发现蓝牙状态发生变化：" + e.getMessage());
            }
        }

        //断开连接
        public void disConnectDevice() {
            mConnectSwitch.release();
            mIsConnecting.set(false);
            try {
                if (mBtGatt != null) {
                    mBtGatt.close();
                }
            } catch (Exception e) {
                //考虑到远程蓝牙设备电源被关闭后，重新连接不上时如果退出程序会抛出
                // DeadObjectException，这里做一下处理。
                e.printStackTrace();
            } finally {
                mBtGatt = null;
            }
        }
    }

    private boolean checkBtConnectionBeforeProceed()
            throws BtNotConnectedYetException, BtNotBondedException, BtNotEnabledException {
        if (mBtGatt == null) {
            throw new BtNotConnectedYetException();
        }
        return checkBtBondedBeforeProceed(mBtGatt.getDevice());
    }

    @SuppressLint("MissingPermission")
    private boolean checkBtBondedBeforeProceed(BluetoothDevice device)
            throws BtNotBondedException, BtNotEnabledException {
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            throw new BtNotBondedException();
        }
        return checkBtEnableBeforeProceed();
    }

    private boolean checkBtEnableBeforeProceed() throws BtNotEnabledException {
        if (mBtEnableState != BluetoothAdapter.STATE_ON) {
            throw new BtNotEnabledException();
        } else {
            return true;
        }
    }

    private class TgiBtEnableStatesReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            //本机蓝牙 初始化第四步：实时监听蓝牙启动状态，如果发现被关闭了，将重新打开。
            //至此1-4步完成了本机蓝牙的初始化。
            //以下是本机蓝牙模块被关闭后，程序自动打开模块的逻辑：
            int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
            mBtEnableState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

            if (mBtEnableState == BluetoothAdapter.STATE_OFF) {
                //蓝牙自动打开步骤1：如果当前状态是"关闭"，说明某些不可预知的意外导致蓝牙模块关闭了，这里申请重新打开
                enableBt();
            } else if (previousState == BluetoothAdapter.STATE_TURNING_ON && mBtEnableState == BluetoothAdapter.STATE_ON
                    || previousState == BluetoothAdapter.STATE_OFF && mBtEnableState == BluetoothAdapter.STATE_ON) {
                //蓝牙自动打开骤2：如果状态是从"正在打开"到"打开"或者从"关闭"到"打开"，说明蓝牙模块刚被打开。
                //这里需要做一下判断：是否属于蓝牙模块被认用户关闭后又被这个库强制打开的情况？
                //如果是，尝试重新连接
                if (mBtGatt != null) {
                    //先把通知清单提取出来，因为待会mTgiBtGattCallback将被重新初始化,这里需要事先备份。
                    showLog("蓝牙模块被重新打开了");
                    Set<Map.Entry<String, TgiToggleNotificationSession>> notifications =
                            mTgiBtGattCallback.getCurrentNotificationCallbacks().entrySet();
                    showLog("当前注册通知数：" + notifications.size());
                    BluetoothDevice device = mBtGatt.getDevice();
                    reconnect(device, notifications);
                }
                //如果不是，什么也不用做。
            }
        }
    }

    private void reconnect(final BluetoothDevice device, final Set<Map.Entry<String, TgiToggleNotificationSession>> notifications) {
        //蓝牙自动打开步骤3：尝试重新连接之前的蓝牙设备。
        //这种情况下如果直接调用mBtGatt的connect()会报android.os.DeadObjectException。
        //需要重新初始化adapter，重新连接蓝牙
        showLog("开始重连。");
        if (mTgiBleServiceBinder != null) {
            //这里mBtGatt直接设为null就可以了，如果调用mBtGatt.close()的话会报android.os.DeadObjectException异常。
            mBtGatt = null;
            //蓝牙自动打开骤4：重新连接蓝牙
            mTgiBleServiceBinder.connectDevice(
                    device,
                    new BtDeviceConnectListener() {
                        @Override
                        public void onConnectSuccess(BluetoothGatt gatt) {
                            super.onConnectSuccess(gatt);
                            //蓝牙自动打开步骤5：蓝牙重新连接后，重新设置通知
                            showLog("重新连接成功，开始设置通知。。。");
                            restoreNotifications(notifications);
                        }

                        @Override
                        public void onConnectFail(String errorMsg) {
                            super.onConnectFail(errorMsg);
                            //如果连接失败，会自动重连，
                            // 这里不需要写重新连接的逻辑，因为自动重连的逻辑已经在onConnectionStateChange()回调中写好了。
                        }
                    }
            );
        }
    }

    private void restoreNotifications(Set<Map.Entry<String, TgiToggleNotificationSession>> notifications) {
        //蓝牙自动打开步骤6：遍历之前的通知清单，重新设置通知
        showLog("开始重新设置通知，当前注册通知数：" + notifications.size());
        for (Map.Entry<String, TgiToggleNotificationSession> entry : notifications) {
            String key = entry.getKey();
            final TgiToggleNotificationSession value = entry.getValue();
            //根据NotificationSessionUUID反推出各项uuid
            String[] params = SessionUUIDGenerator.decryptNotificationSessionUUID(key);
            if (params != null && mTgiBleServiceBinder != null) {
                //重新设置通知
                mTgiBleServiceBinder.toggleNotification(
                        params[1],
                        params[2],
                        params[3],
                        true,
                        value.getTgiToggleNotificationCallback()
                );
            }
        }
        //蓝牙自动打开步骤7：通知设置完毕，流程结束
    }

    protected void showLog(String msg) {
        LogUtils.showLog(msg);
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
            showLog("先前配对状态：" + getBondStateDescription(previousState));
            showLog("当前配对状态：" + getBondStateDescription(currentState));
            if (mDeviceParingStateListener != null) {
                //蓝牙配对第5步：获取配对结果
                mDeviceParingStateListener.onDevicePairingStateChanged(device, previousState, currentState);
            }
            //当最新结果为以下两种时，表示配对结果已经确定了。
            if (previousState == BluetoothDevice.BOND_BONDING) {
                if (currentState == BluetoothDevice.BOND_BONDED || currentState == BluetoothDevice.BOND_NONE) {
                    //这里要分两种情况判断:
                    //1,是否通过前面的pairDevice()函数发起的配对结果？
                    if (mDeviceParingStateListener != null) {
                        //如果是，蓝牙配对第6步：释放资源，流程结束。
                        mDeviceParingStateListener.onParingSessionEnd();
                        mDeviceParingStateListener = null;
                    } else {
                        //2,非通过本库操作的状况：是否有新的远程设备被配对了，而当前连接的蓝牙设备配对被取消了？
                        //暂不考虑这种情况，因为新的设备不一定和之前设备型号一样（如电子秤和电饭煲），通知的UUID也不一定一样，这里不适合
                        //做过多的处理。
                    }
                }
            }

        }
    }

    private String getBondStateDescription(int state) {
        String desc = "unknown";
        switch (state) {
            case BluetoothDevice.BOND_NONE:
                desc = "BOND_NONE";
                break;
            case BluetoothDevice.BOND_BONDED:
                desc = "BOND_BONDED";
                break;
            case BluetoothDevice.BOND_BONDING:
                desc = "BOND_BONDING";
                break;
        }
        return desc;
    }


}
