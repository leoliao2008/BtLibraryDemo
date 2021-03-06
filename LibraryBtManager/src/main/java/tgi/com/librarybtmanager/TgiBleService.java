package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static tgi.com.librarybtmanager.TgiBtManagerLogUtils.showLog;

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
    private TgiBleClientModel mTgiBleClientModel;
    private TgiBtEnableStatesReceiver mBtConnStatesReceiver;
    private int mBtEnableState;
    private DevicePairingStatesReceiver mPairingStatesReceiver;
    private TgiDeviceParingStateListener mTgiDeviceParingStateListener;
    private volatile BluetoothGatt mBtGatt;
    private volatile TgiBtGattCallback mTgiBtGattCallback;
    private AtomicBoolean mIsConnectingDevice = new AtomicBoolean(false);
    private AtomicBoolean mIsDiscoveringServices = new AtomicBoolean(false);
    private AtomicBoolean mIsPreparingReconnection = new AtomicBoolean(false);
    private Semaphore mConnectStateChangeLock = new Semaphore(1);
    private TgiBleServiceBinder mTgiBleServiceBinder;
    private Handler mHandler;
    //默认蓝牙模块被关闭时会自动打开。可以设置成不打开。
    private AtomicBoolean mIsAutoEnableBt = new AtomicBoolean(true);
    private ExecutorService mSingleThreadExecutor;
    private volatile AtomicBoolean mIsSendingCmd = new AtomicBoolean(false);
    private Set<TgiToggleNotificationSession> mCurrentNotifications = new LinkedHashSet<>();
    private TgiBtDeviceConnectListener mTgiBtDeviceConnectListener;
    private BluetoothDevice mUnboundBtDevice;


    //bindService 生命流程1
    //startService 生命流程1
    @Override
    public void onCreate() {
        super.onCreate();
        showLog("service is created.");
        mHandler = new Handler();
        mTgiBleClientModel = new TgiBleClientModel();
        mBtEnableState = mTgiBleClientModel.isBtEnabled() ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF;
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
        showLog("service is bonded.");
        //本机蓝牙初始化第二步：检查蓝牙是否被打开了，如果没有，现在打开。
        if (mBtEnableState == BluetoothAdapter.STATE_OFF) {
            enableBt();
        }
        //本机蓝牙初始化第三步：返回binder给TgiBleManager
        mTgiBleServiceBinder = new TgiBleServiceBinder();
        //有的机子上bindService不成功，就算这样也要强行把mTgiBleServiceBinder返回给TgiBleManager.
        TgiBleManager.getInstance().setTgiBleServiceBinder(mTgiBleServiceBinder);
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
        if (mSingleThreadExecutor != null && !mSingleThreadExecutor.isTerminated()) {
            mSingleThreadExecutor.shutdownNow();
            mSingleThreadExecutor = null;
        }
        mCurrentNotifications.clear();
        showLog("service is destroyed.");
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

        private Thread mThreadRepeatDiscovering;
        private Semaphore mDiscoveringSwitch = new Semaphore(1);
        ;

        /**
         * 设置是否蓝牙模块被关闭后自动重启以及重连远程设备。默认启动。
         *
         * @param isToAutoEnable
         */
        public void setAutoEnableBt(boolean isToAutoEnable) {
            mIsAutoEnableBt.set(isToAutoEnable);
        }

        public boolean checkIfDeviceConnected(BluetoothDevice device) {
            return mTgiBleClientModel.checkIfDeviceConnected(getApplicationContext(), device);
        }

        /**
         * 开始扫描附近有名字的蓝牙设备，扫描时间5秒
         *
         * @param callback
         */
        public void startScanDevice(TgiBleScanCallback callback) {
            try {
                if (checkBtEnableBeforeProceed()) {
                    mTgiBleClientModel.startScanBtDevices(callback);
                }
            } catch (BtNotEnabledException e) {
                callback.onError(e.getMessage());
            }
        }

        /**
         * 提前终止扫描蓝牙设备。
         *
         * @param callback
         */
        public void stopScanDevice(TgiBleScanCallback callback) {
            try {
                if (checkBtEnableBeforeProceed()) {
                    mTgiBleClientModel.stopScanBtDevices(callback);
                }
            } catch (BtNotEnabledException e) {
                callback.onError(e.getMessage());
            }
        }

        /**
         * 获取蓝牙配对设备清单
         *
         * @return
         */
        public ArrayList<BluetoothDevice> getBondedDevices() {
            return mTgiBleClientModel.getBondedDevices();
        }


        /**
         * 启动蓝牙配对，这是官方推荐的方法，需要用户确认。
         *
         * @param device
         * @param listener
         */
        @SuppressLint("MissingPermission")
        public void pairDevice(final BluetoothDevice device, final TgiDeviceParingStateListener listener) {
            try {
                if (checkBtEnableBeforeProceed()) {
                    //蓝牙配对第1步:先检查是否已经配对了。如果以前已经配对了，直接返回。
                    @SuppressLint("MissingPermission")
                    int bondState = device.getBondState();
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        listener.onParingSessionBegin();
                        listener.onDevicePairingStateChanged(device, bondState, bondState);
                        listener.onParingSessionEnd(bondState);
                        return;
                    }

                    //蓝牙配对第2步：自定义开始的准备工作（如显示进度圈等）
                    listener.onParingSessionBegin();
                    //蓝牙配对第3步：注册广播接受者监听配对结果
                    mTgiDeviceParingStateListener = listener;
                    //蓝牙配对第4步：开始正式配对,配对结果通过广播接受者知悉。
                    boolean isInitSuccess = mTgiBleClientModel.pairDevice(device);
                    if (!isInitSuccess) {
                        //如果启动配对失败，这里报错，并且释放资源。
                        listener.onError("Fail to initiate device pairing.");
                        listener.onParingSessionEnd(device.getBondState());
                        mTgiDeviceParingStateListener = null;
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

        /**
         * 利用反射配对蓝牙设备，不需要用户确认。
         *
         * @param device
         * @return
         */
        public boolean pairDeviceWithoutUserConsent(BluetoothDevice device) {
            return mTgiBleClientModel.pairDeviceWithoutUserConsent(device);
        }

        /**
         * 利用反射配对蓝牙设备，不需要用户确认。
         *
         * @param deviceAddress
         * @return
         */
        public boolean pairDeviceWithoutUserConsent(String deviceAddress) {
            return pairDeviceWithoutUserConsent(mTgiBleClientModel.getDeviceByAddress(deviceAddress));
        }

        public void pairDeviceWithoutUserConsent(String deviceAddress, TgiDeviceParingStateListener listener) {
            pairDeviceWithoutUserConsent(mTgiBleClientModel.getDeviceByAddress(deviceAddress), listener);
        }

        public void pairDeviceWithoutUserConsent(BluetoothDevice device, TgiDeviceParingStateListener listener) {
            mTgiBleClientModel.pairDeviceWithoutUserConsent(device, listener);
        }

        /**
         * 利用反射解除蓝牙配对，不需要用户确认。
         *
         * @param device
         * @return
         */
        public boolean removePairedDeviceWithoutUserConsent(BluetoothDevice device) {
            //记下来，MC21自动解绑时，还原绑定状态时用
            mUnboundBtDevice = device;
            return mTgiBleClientModel.removePairedDeviceWithoutUserConsent(device);
        }

        /**
         * 利用反射解除蓝牙配对，不需要用户确认。
         *
         * @param deviceAddress
         * @return
         */
        public boolean removePairedDeviceWithoutUserConsent(String deviceAddress) {
            return removePairedDeviceWithoutUserConsent(mTgiBleClientModel.getDeviceByAddress(deviceAddress));
        }

        /**
         * 连接已经配对的蓝牙设备
         *
         * @param deviceAddress
         * @param listener
         */
        public void connectDevice(String deviceAddress, final TgiBtDeviceConnectListener listener) {
            connectDevice(
                    mTgiBleClientModel.getDeviceByAddress(deviceAddress),
                    listener
            );
        }

        /**
         * 连接已经配对的蓝牙设备
         *
         * @param device
         * @param listener
         */
        public void connectDevice(final BluetoothDevice device, final TgiBtDeviceConnectListener listener) {
            //连接蓝牙设备第一步：判断是否正在连接，如果是，放弃当前操作。
            if (!mIsConnectingDevice.get()) {
                mIsConnectingDevice.set(true);
                //重连时有用
                mTgiBtDeviceConnectListener = listener;

                //连接蓝牙设备第二步：关闭之前的连接，重新开始新的连接。
                if (mBtGatt != null) {
                    disConnectDevice();
                }

                //这个回调用来监听蓝牙设备连接后的各种情况
                BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
                        super.onConnectionStateChange(gatt, status, newState);
                        //保证连续的状态变更会依次执行。这是针对MC2.1会连续返回两次成功或失败的回调调整的。
                        //handleConnectionStateChange(gatt,status,newState,listener)函数中会处理连续收到两次重复状态时的逻辑。
                        try {
                            if (mConnectStateChangeLock.tryAcquire(500, TimeUnit.MILLISECONDS)) {
                                handleConnectionStateChange(gatt, status, newState, listener);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            mConnectStateChangeLock.release();
                        }
                    }

                    @Override
                    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                        super.onServicesDiscovered(gatt, status);
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            //连接蓝牙设备第五步：连接成功，读取服务列表成功。至此1-5步，连接流程结束。
                            showLog("服务列表读取成功，蓝牙连接成功了");
                            mBtGatt = gatt;
                            mIsConnectingDevice.set(false);
                            mIsDiscoveringServices.set(false);
                            mIsPreparingReconnection.set(false);
                            mSingleThreadExecutor = Executors.newSingleThreadExecutor();
                            listener.onConnectSuccess(gatt);
                            //                            //todo 这里测试用
                            //                            boolean requestMtu = mBtGatt.requestMtu(100);
                            //                            if (requestMtu) {
                            //                                showLog("MTU 修改请求成功。");
                            //                            } else {
                            //                                showLog("MTU 修改请求失败。");
                            //                            }

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

                    @Override
                    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                        super.onMtuChanged(gatt, mtu, status);
                        //todo 用来测试，可能会删
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            showLog("MTU的值设置成功，最新MTU容量：" + mtu);
                        } else {
                            showLog("MTU的值设置失败，最新MTU容量：" + mtu);
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
                    mIsConnectingDevice.set(false);
                    showLog(e.getMessage());
                    //Exception包含了以下两种情况：
                    //1，蓝牙模块没有打开：如果在连接开始的时候，蓝牙模块已经是打开状态，那在连接成功后，如果蓝牙模块被关闭了，
                    // 本库会重新打开蓝牙，并重启连接和重设通知，这部分逻辑已经在广播接受者中写好了，在这里不需要做任何处理；
                    // 但如果一开始蓝牙就没启动将不再进行操作，这是因为本来这个后台服务在绑定的时候，就会检查蓝牙是否启动，如果没启动，会启动蓝牙。
                    // 也就是说，不会有蓝牙模块一开始就没启动的情况。如果有，那是意料之外的情况，这里终止连接是合理的，否则一直死循环，上层也无法开始新的
                    // 连接。
                    //2，远程蓝牙配对不上：配对和连接是两个独立的过程，必须先配对好再连接，这是上层应用应当处理的逻辑，因此不会有未配对好就连接的情况；
                    //这里抛出这个异常的情况还有一种：原先是配对好的，且连接上了，但后来因为某种原因取消配对了，连接中断，重连，发现配对不上，回到这里。
                    //这种情况下如果还是强行配对之前的设备，再强行连接之前的设备和重新设置通知，明显不合理。应当中断连接，让上层应用处理配对更新后的逻辑。
                    //综上所述，这里应该抛出异常，中断连接
                    listener.onConnectFail(e.getMessage());
                }
            } else {
                showLog("正在连接，放弃本次操作。");
            }
        }

        @SuppressLint("MissingPermission")
        private void handleConnectionStateChange(final BluetoothGatt gatt, int status, int newState,
                                                 final TgiBtDeviceConnectListener listener) {
            if (status == BluetoothGatt.GATT_FAILURE) {
                showLog("连接失败");
                //todo 这里要不要考虑下一步？没试过这种结果
                return;
            }
            showLog("蓝牙连接状态有更新：" + gatt.getDevice().getName()
                    + " " + gatt.getDevice().getAddress() + " "
                    + mTgiBleClientModel.getBtDeviceConnectionStateDescription(newState));

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                //实测MC2.1中发现会返回两次连接成功的回调，这里忽略多余的回调。
                //正在读取服务列表中或者连接已经成功的时候，不用进一步操作
                if (mIsDiscoveringServices.get() || mBtGatt != null) {
                    showLog("已经在读取服务列表或者已经成功连接了，不用做其他事情");
                    return;
                }

                mIsDiscoveringServices.set(true);
                boolean discoverServices = gatt.discoverServices();
                //连接蓝牙设备第四步：连接成功，读取服务列表，否则后面无法顺利读写特性。
                if (discoverServices) {
                    showLog("连接上蓝牙了，开始读取服务列表。");
                    repeatDiscoveringUntilServiceDiscovered(gatt);
                } else {
                    showLog("连接上蓝牙了，开始读取服务列表,然而读取失败....500毫秒后重试。。。");
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showLog("重新读取服务列表中...");
                            gatt.discoverServices();
                        }
                    }, 500);
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //MC2.1会连续返回两次连接失败的广播
                //连接失败。
                final BluetoothDevice device = gatt.getDevice();
                gatt.disconnect();
                gatt.close();
                //这是针对MC21重新连接后会断开，之后连续发两次连接失败的现象修正。这里忽略重复的事件。
                if (mIsPreparingReconnection.get()) {
                    return;
                }
                //正在连接时发现连不上,开始自动重连
                if (mBtGatt == null && mIsConnectingDevice.get()) {
                    mIsPreparingReconnection.set(true);
                    showLog("正在连接时发现连不上,开始自动重连...");
                    //连接失败1000毫秒后重新连接，之后连接不上时都会持续重连。
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mTgiBleServiceBinder != null) {
                                //把以下两个标识符恢复默认，方可重新连接设备
                                mIsPreparingReconnection.set(false);
                                mIsConnectingDevice.set(false);
                                mTgiBleServiceBinder.connectDevice(device, listener);
                            }
                        }
                    }, 1000);

                } else if (mBtGatt != null && !mIsConnectingDevice.get()) {//已经连接上，在数据传输途中，突然连接中断。
                    mIsPreparingReconnection.set(true);
                    showLog("已经连接上，在数据传输途中，突然连接中断。");
                    //这是信号不良、对方蓝牙设备断电或取消配对这三种情况导致的连接中断，本库自动重连的逻辑：
                    //这里延迟一秒是因为设备在取消配对后需要一定的缓冲时间才能同步状态，否则程序会得到仍配对的状态。
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (checkBtBondedBeforeProceed(device)) {
                                    reconnect(device, listener);
                                }
                            } catch (BtNotBondedException e) {
                                e.printStackTrace();
                                //这里只可能是连接成功后，在传输数据的过程中用户取消了配对，或者MC21自动解绑了设备，
                                // 这时要终止连接。重复连接已经没有意义，
                                // 更糟糕的是会阻塞蓝牙，这时如果利用反射配对其它设备将会失败。
                                showLog("传输数据到一半的时候，被取消配对了，中断连接等待进一步指示。");
                                disConnectDevice();
                                //这里返回一个回调给调用方处理后续的事情
                                listener.onDisconnectedBecauseDeviceUnbound(device);
                            } catch (BtNotEnabledException e) {
                                e.printStackTrace();
                                //蓝牙模块默认服务启动时会打开，且在广播接收者那里已经监听蓝牙模块开关了，
                                // 当关闭时会有自动重连逻辑，因此这里不用做任何处理。
                                showLog("蓝牙模块关闭了，无法继续连接。等待蓝牙模块重新启动。");
                            } finally {
                                mIsPreparingReconnection.set(false);
                            }
                        }
                    }, 1000);
                }
            }
        }

        /**
         * 启动一个子线程，监听服务列表是否及时读取，如果未能及时读取，每隔2500毫秒重复一次读取请求，
         * 直到读取成功为止。
         *
         * @param gatt
         */
        private void repeatDiscoveringUntilServiceDiscovered(final BluetoothGatt gatt) {
            if (mDiscoveringSwitch.tryAcquire()) {
                mThreadRepeatDiscovering = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (true) {
                            try {
                                Thread.sleep(2500);
                                if (mThreadRepeatDiscovering.isInterrupted()) {
                                    break;
                                }
                                if (mBtGatt == null && mIsConnectingDevice.get() && mIsDiscoveringServices.get()) {
                                    showLog("2500毫秒后未能返回服务列表，判断为服务列表读取失败，再次读取...");
                                    gatt.discoverServices();
                                } else {
                                    break;
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                break;
                            }
                        }
                        mDiscoveringSwitch.release();
                    }
                });
                mThreadRepeatDiscovering.start();
            }

        }

        /**
         * 取消配对当前连接的设备，重新配对一台新的同型号的设备，并把之前的通知原封不动地自动重设。
         */
        public void pairAndConnectToAnotherDeviceOfTheSameType(String newDeviceAddress) {
            pairAndConnectToAnotherDeviceOfTheSameType(mTgiBleClientModel.getDeviceByAddress(newDeviceAddress));
        }

        /**
         * 取消配对当前连接的设备，重新配对一台新的同型号的设备，并把之前的通知原封不动地自动重设。
         *
         * @param newDevice
         */
        public void pairAndConnectToAnotherDeviceOfTheSameType(final BluetoothDevice newDevice) {
            try {
                if (checkBtEnableBeforeProceed() && mTgiBtGattCallback != null) {
                    //切换同型号设备第一步：设置回调，通过广播接受者监听配对状况更新
                    mTgiDeviceParingStateListener = new TgiDeviceParingStateListener() {
                        @Override
                        public void onDevicePairingStateChanged(BluetoothDevice device, int previousState, int currentState) {
                            super.onDevicePairingStateChanged(device, previousState, currentState);
                            if (newDevice.getAddress().equals(device.getAddress())) {
                                //切换同型号设备第四步：配对成功后重新连接，并把之前的通知重新设置一遍。至此1-4步切换同型号设备流程结束。
                                if (currentState == BluetoothDevice.BOND_BONDED) {
                                    swapToAnotherPairedDevice(newDevice);
                                    mTgiDeviceParingStateListener = null;
                                }
                            }
                        }
                    };
                    //切换同型号设备第二步：取消当前连接设备的配对
                    if (mBtGatt != null) {
                        mTgiBleClientModel.removePairedDeviceWithoutUserConsent(mBtGatt.getDevice());
                    }
                    //切换同型号设备第三步：500毫秒后配对新设备，在广播接受者中进行后面的逻辑。
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mTgiBleClientModel.pairDeviceWithoutUserConsent(newDevice);
                        }
                    }, 500);
                }
            } catch (BtNotEnabledException e) {
                e.printStackTrace();
                showLog("蓝牙模块没打开，无法更改设备。");
            }
        }

        /**
         * 换一个相同型号的远程设备重新连接，之前的通知原封不动地自动重设。新设备要已经配对成功且已经同步到底层蓝牙设配器中。否则连接失败。
         *
         * @param newDevice
         */
        public void swapToAnotherPairedDevice(BluetoothDevice newDevice) {
            showLog("开始更改设备。");
            disConnectDevice();
            reconnect(newDevice, mTgiBtDeviceConnectListener);
        }

        /**
         * 换一个相同型号的远程设备重新连接，之前的通知原封不动地自动重设。新设备要已经配对成功且已经同步到底层蓝牙设配器中。否则连接失败。
         *
         * @param newDevice
         */
        public void swapToAnotherPairedDevice(String newDevice) {
            swapToAnotherPairedDevice(mTgiBleClientModel.getDeviceByAddress(newDevice));
        }

        //写入特性
        public void writeChar(final byte[] data, final String serviceUUID,
                              final String charUUID, final TgiWriteCharCallback callback) {
            //把命令放入线程池中按FIFO顺序执行
            try {
                if (mSingleThreadExecutor == null || mSingleThreadExecutor.isShutdown()) {
                    showLog("线程池还没启动或已经关闭了，需要连接成功后方生成。");
                    return;
                }
                mSingleThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //检查是否上一个命令执行完毕了，如果没有一直阻塞，直到上一个命令执行完毕。
                            while (true) {
                                Thread.sleep(200);
                                if (!mIsSendingCmd.get()) {
                                    break;
                                }
                            }
                            mIsSendingCmd.compareAndSet(false, true);
                            //检查蓝牙模块状态是否正常，是否已经连接上
                            if (checkBtConnectionBeforeProceed()) {
                                TgiWriteCharSession session = new TgiWriteCharSession(
                                        mBtGatt.getDevice().getAddress(),
                                        charUUID,
                                        serviceUUID,
                                        mTgiBtGattCallback);
                                //正式写入，多嵌套一层TgiWriteCharCallback是为了在这个Service中更新mIsSendingCmd的值。
                                session.write(mBtGatt, data, new TgiWriteCharCallback() {
                                    @Override
                                    public void onWriteFailed(String errorMsg) {
                                        mIsSendingCmd.set(false);
                                        super.onWriteFailed(errorMsg);
                                        callback.onWriteFailed(errorMsg);
                                    }

                                    @Override
                                    public void onWriteSuccess(BluetoothGattCharacteristic characteristic) {
                                        mIsSendingCmd.set(false);
                                        super.onWriteSuccess(characteristic);
                                        callback.onWriteSuccess(characteristic);
                                    }
                                });
                                //后续在TgiBtGattCallback的onCharacteristicWrite回调中进行。
                            }
                        } catch (Exception e) {
                            mIsSendingCmd.compareAndSet(true, false);
                            e.printStackTrace();
                            //把所有异常的信息都返回到回调中。
                            callback.onWriteFailed(e.getMessage());
                            showLog("写入失败：" + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                showLog(e.getMessage());
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
                            mBtGatt.getDevice().getAddress(),
                            btChar,
                            mTgiBtGattCallback);
                    session.read(mBtGatt, callback);
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
            //注册/取消注册通知第1步：检查蓝牙状态是否正常，是否已经连接上
            showLog("开始设置通知。");
            try {
                if (checkBtConnectionBeforeProceed()) {
                    //注册/取消注册通知第2步：正式操作注册/取消注册，后续在TgiBtGattCallback的onDescriptorWrite回调中进行。
                    //至此1-2步完成流程。
                    final TgiToggleNotificationSession session = new TgiToggleNotificationSession(
                            mBtGatt.getDevice().getAddress(),
                            serviceUUID,
                            charUUID,
                            descUUID,
                            isToTurnOn,
                            mTgiBtGattCallback);
                    //把通知内容同步到独立内存中，今后意外断开导致自动重连时可以用来恢复通知。
                    if (isToTurnOn) {
                        mCurrentNotifications.add(session);
                    } else {
                        mCurrentNotifications.remove(session);
                    }
                    session.start(mBtGatt, callback);
                }
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
                showLog("设置通知时发现蓝牙状态发生变化：" + e.getMessage());
            }
        }

        //断开连接
        public void disConnectDevice() {
            if (mThreadRepeatDiscovering != null) {
                mThreadRepeatDiscovering.interrupt();
                mThreadRepeatDiscovering = null;
            }
            mIsConnectingDevice.set(false);
            //把命令队列清空
            if (mSingleThreadExecutor != null && !mSingleThreadExecutor.isShutdown()) {
                mSingleThreadExecutor.shutdownNow();
                mSingleThreadExecutor = null;
            }
            mIsSendingCmd.set(false);
            try {
                if (mBtGatt != null) {
                    mBtGatt.disconnect();
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
        if (!mTgiBleClientModel.checkIfDeviceConnected(getApplicationContext(), mBtGatt.getDevice())) {
            showLog("虽然GATT不为空，但其实设备连接已经断掉了");
            throw new BtNotConnectedYetException();
        }
        return checkBtBondedBeforeProceed(mBtGatt.getDevice());
    }

    @SuppressLint("MissingPermission")
    private boolean checkBtBondedBeforeProceed(BluetoothDevice device)
            throws BtNotBondedException, BtNotEnabledException {
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            String name = device.getName();
            if (TextUtils.isEmpty(name)) {
                name = "The device has no name.";
            }
            showLog("Device is not bonded, device address: " + device.getAddress() + " device name: " + name);
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
                if (mIsAutoEnableBt.get()) {
                    enableBt();
                }
            } else if (previousState == BluetoothAdapter.STATE_TURNING_ON && mBtEnableState == BluetoothAdapter.STATE_ON
                    || previousState == BluetoothAdapter.STATE_OFF && mBtEnableState == BluetoothAdapter.STATE_ON) {
                //蓝牙自动打开骤2：如果状态是从"正在打开"到"打开"或者从"关闭"到"打开"，说明蓝牙模块刚被打开。
                //这里需要做一下判断：是否属于蓝牙模块被认用户关闭后又被这个库强制打开的情况？
                //如果是，尝试重新连接
                if (mBtGatt != null) {
                    //先把通知清单提取出来，因为待会mTgiBtGattCallback将被重新初始化,这里需要事先备份。
                    showLog("蓝牙模块被重新打开了");
                    BluetoothDevice device = mBtGatt.getDevice();
                    reconnect(device, mTgiBtDeviceConnectListener);
                }
                //如果不是，什么也不用做。
            }
        }
    }

    private void reconnect(final BluetoothDevice device, @Nullable final TgiBtDeviceConnectListener listener) {
        //蓝牙自动打开步骤3：尝试重新连接之前的蓝牙设备。
        //这种情况下如果直接调用mBtGatt的connect()会报android.os.DeadObjectException。
        //需要重新初始化adapter，重新连接蓝牙
        showLog("开始重连。");
        if (mTgiBleServiceBinder != null) {
            mTgiBleServiceBinder.disConnectDevice();
            //蓝牙自动打开骤4：重新连接蓝牙
            mTgiBleServiceBinder.connectDevice(
                    device,
                    new TgiBtDeviceConnectListener() {
                        @Override
                        public void onConnectSuccess(BluetoothGatt gatt) {
                            super.onConnectSuccess(gatt);
                            //蓝牙自动打开步骤5：蓝牙重新连接后，重新设置通知
                            showLog("重新连接成功，开始设置通知。。。");
                            restoreNotifications();
                        }

                        @Override
                        public void onConnectFail(String errorMsg) {
                            super.onConnectFail(errorMsg);
                            //如果连接失败，会自动重连，
                            // 这里不需要写重新连接的逻辑，因为自动重连的逻辑已经在onConnectionStateChange()回调中写好了。
                        }

                        @Override
                        public void onDisconnectedBecauseDeviceUnbound(BluetoothDevice device) {
                            super.onDisconnectedBecauseDeviceUnbound(device);
                            if (listener != null) {
                                //这里跟上面两个回调不一样，上面的两个回调只会发生一次，这个回调在今后运行过程中
                                //可能会重复出现，因此即使覆盖了，也要连续继承下去。
                                listener.onDisconnectedBecauseDeviceUnbound(device);
                            }
                        }
                    }
            );
        }
    }

    private void restoreNotifications() {
        //蓝牙自动打开步骤6：遍历之前的通知清单，重新设置通知
        if (mCurrentNotifications == null) {
            return;
        }
        showLog("开始重新设置通知，当前注册通知数：" + mCurrentNotifications.size());
        for (TgiToggleNotificationSession note : mCurrentNotifications) {
            //根据NotificationSessionUUID反推出各项uuid
            String[] params = SessionUUIDGenerator.decryptNotificationSessionUUID(note.getSessionUUID());
            if (params != null && mTgiBleServiceBinder != null) {
                //重新设置通知
                mTgiBleServiceBinder.toggleNotification(
                        params[1],
                        params[2],
                        params[3],
                        true,
                        note.getTgiToggleNotificationCallback()
                );
            }
        }
        showLog("重新设置通知完毕，蓝牙重连结束。");
        //蓝牙自动打开步骤7：通知设置完毕，流程结束
    }


    private class DevicePairingStatesReceiver extends BroadcastReceiver {

        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            //Broadcast Action: Indicates a change in the bond state of a remote device. For example, if a device is bonded (paired).
            //Always contains the extra fields EXTRA_DEVICE, EXTRA_BOND_STATE and EXTRA_PREVIOUS_BOND_STATE.
            //Requires BLUETOOTH to receive.
            //Constant Value: "android.bluetooth.device.action.BOND_STATE_CHANGED"

            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int currentState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            int previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
            showLog("蓝牙设备：" + device.getName() + " " + device.getAddress() + "先前配对状态：" + mTgiBleClientModel.getBondStateDescription(previousState));
            showLog("蓝牙设备：" + device.getName() + " " + device.getAddress() + "当前配对状态：" + mTgiBleClientModel.getBondStateDescription(currentState));
            if (mTgiDeviceParingStateListener != null) {
                //蓝牙配对第5步：获取配对结果
                mTgiDeviceParingStateListener.onDevicePairingStateChanged(device, previousState, currentState);
            }
            //当最新结果为以下两种时，表示配对结果已经确定了。
            if (previousState == BluetoothDevice.BOND_BONDING) {
                if (currentState == BluetoothDevice.BOND_BONDED || currentState == BluetoothDevice.BOND_NONE) {
                    if (mTgiDeviceParingStateListener != null) {
                        //如果是，蓝牙配对第6步：释放资源，流程结束。
                        mTgiDeviceParingStateListener.onParingSessionEnd(currentState);
                        mTgiDeviceParingStateListener = null;
                    }
                }
            }
            //            //特殊情况：MC21自动解绑
            //            //如果当前状态为解绑中，需要检查一下是否由调用方主动解绑引起的
            //            if (previousState == BluetoothDevice.BOND_BONDED && currentState == BluetoothDevice.BOND_BONDING) {
            //                if (mUnboundBtDevice != null && mUnboundBtDevice.getAddress().equals(device.getAddress())) {
            //                    return;
            //                }
            //                //如果不是由调用方解绑引起的，属于MC21自动解绑，需要恢复原状
            //                mTgiBleServiceBinder.disConnectDevice();
            //                mTgiBleServiceBinder.pairDeviceWithoutUserConsent(
            //                        device,
            //                        new TgiDeviceParingStateListener() {
            //                            @Override
            //                            public void onParingSessionEnd(int endState) {
            //                                super.onParingSessionEnd(endState);
            //                                if (endState != BluetoothDevice.BOND_BONDED) {
            //                                    //如果不成功，一直调到成功
            //                                    mTgiBleServiceBinder.pairDeviceWithoutUserConsent(device, this);
            //                                } else {
            //                                    //成功后，重新连接蓝牙
            //                                    reconnect(device, null);
            //                                }
            //                            }
            //                        }
            //                );
            //            }
        }
    }


}
