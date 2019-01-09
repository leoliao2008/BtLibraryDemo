package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.RequiresApi;

import java.util.ArrayList;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 20/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>BtLibraryDemo</i>
 * <p><b>Description:</b></p>
 */
public class TgiBleManager {
    private static final int REQUEST_CODE_LOCATION_PERMISSION = 9528;
    private static TgiBleManager tgiBleManager;
    private BleClientModel mBleClientModel;
    private BleInitCallBack mBleInitCallBack;
    private TgiBleService.TgiBleServiceBinder mTgiBleServiceBinder;
    private ServiceConnection mServiceConnection;
    private TgiBleScanCallback mTgiBleScanCallback;
    private Handler mHandler;
    private Runnable mRunnableStopScanningDevices = new Runnable() {
        @Override
        public void run() {
            stopScanBtDevice();
        }
    };


    private TgiBleManager() {
        mBleClientModel = new BleClientModel();
        mHandler = new Handler();
    }

    public static synchronized TgiBleManager getInstance() {
        if (tgiBleManager == null) {
            tgiBleManager = new TgiBleManager();
        }
        return tgiBleManager;
    }

    public void startBtService(Activity activity, BleInitCallBack callBack) {
        mBleInitCallBack = callBack;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //1，检查是否有定位权限，如果没有，申请权限
            boolean isAllGranted = BtPermissionsChecker.checkBtPermissions(activity);
            if (isAllGranted) {
                //2，检查蓝牙硬件是否支持BLE
                checkIfBtAvailable(activity);
            } else {
                BtPermissionsChecker.requestBtPermissions(activity, REQUEST_CODE_LOCATION_PERMISSION);
            }
        } else {
            //2，检查蓝牙硬件是否支持BLE
            checkIfBtAvailable(activity);
        }
    }

    private void checkIfBtAvailable(Activity activity) {
        if (!mBleClientModel.isBtSupported(activity.getPackageManager())) {
            if (mBleInitCallBack != null) {
                mBleInitCallBack.onError("This device does not support bluetooth functions.");
            }
            return;
        }
        if (!mBleClientModel.hasBleFeature(activity.getPackageManager())) {
            if (mBleInitCallBack != null) {
                mBleInitCallBack.onError("This device does not support BLE features.");
            }
            return;
        }
        //3，打开蓝牙后台服务
        startBtService(activity);
    }

    /**
     * 一般来说这个方法用不着，因为在执行startBtService（Activity activity）的时候会返回一个TgiBleServiceBinder
     * 对象。这个方法的作用是：如果应用层直接跳过这个类绑定了蓝牙后台服务，产生了TgiBleServiceBinder对象后，通过这个方法同步一下
     * TgiBleServiceBinder，这样这个类的主要方法才有效。
     *
     * @param tgiBleServiceBinder
     */
    public void setTgiBleServiceBinder(TgiBleService.TgiBleServiceBinder tgiBleServiceBinder) {
        mTgiBleServiceBinder = tgiBleServiceBinder;
    }

    public void setIsAutoEnableBt(boolean isAutoEnableBt) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.setAutoEnableBt(isAutoEnableBt);
        }
    }

    public BluetoothDevice getDeviceByAddress(String devAddress) {
        return mBleClientModel.getDeviceByAddress(devAddress);
    }


    private void startBtService(Activity activity) {
        //4,先直接启动蓝牙后台服务，这样以后unbind的时候也不会退出。
        Intent intent = new Intent(activity, TgiBleService.class);
        activity.startService(intent);

        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                //6，返回一个binder，用来操纵service。
                //1-6流程到此完结。以上是本机蓝牙初始化的工作。
                mTgiBleServiceBinder = (TgiBleService.TgiBleServiceBinder) service;
                if (mBleInitCallBack != null) {
                    mBleInitCallBack.onInitSuccess();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };
        //5，然后再bind蓝牙后台服务，目的是获取binder
        boolean isSuccess = activity.getApplicationContext().bindService(
                new Intent(activity, TgiBleService.class),
                mServiceConnection,
                Context.BIND_AUTO_CREATE
        );
        //如果没有粘结成功，返回相关信息方便debug。
        if (isSuccess) {
            if (mBleInitCallBack != null) {
                mBleInitCallBack.onError("Fail to bind service.");
            }
        }
    }


    public void stopBtService(Activity activity) {
        //先解除绑定
        if (mServiceConnection != null) {
            activity.unbindService(mServiceConnection);
            mServiceConnection = null;
            mTgiBleServiceBinder = null;
        }
        //释放资源
        if (mBleInitCallBack != null) {
            mBleInitCallBack = null;
        }
        //然后正式停止
        Intent intent = new Intent(activity, TgiBleService.class);
        activity.stopService(intent);
    }

    //扫描周围蓝牙设备
    @SuppressLint("MissingPermission")
    public void startScanBtDevices(final TgiBleScanCallback callback) {
        if (checkIfServiceAvailable()) {
            mTgiBleScanCallback = callback;
            mTgiBleServiceBinder.startScanDevice(callback);
            //5秒后自动停止扫描
            mHandler.postDelayed(mRunnableStopScanningDevices, 5000);
        } else {
            callback.onError("Bt Service is not bonded.");
        }

    }

    //停止扫描周围蓝牙设备
    public void stopScanBtDevice() {
        if (checkIfServiceAvailable() && mTgiBleScanCallback != null) {
            mTgiBleServiceBinder.stopScanDevice(mTgiBleScanCallback);
            //确保消息队列里停止扫描的操作被清除，因为已经没意义。
            mHandler.removeCallbacks(mRunnableStopScanningDevices);
        } else {
            if (mTgiBleScanCallback != null) {
                mTgiBleScanCallback.onError("Bt Service is not bonded.");
            }
        }
    }

    public ArrayList<BluetoothDevice> getBondedDevices() {
        return mBleClientModel.getBondedDevices();
    }

    public boolean checkIfDeviceBonded(String deviceAddress) {
        return checkIfDeviceBonded(mBleClientModel.getDeviceByAddress(deviceAddress));
    }

    @SuppressLint("MissingPermission")
    public boolean checkIfDeviceBonded(BluetoothDevice device) {
        return device.getBondState() == BluetoothDevice.BOND_BONDED;
    }

    //根据远程设备地址进行蓝牙配对。
    public void pairDevice(String deviceAddress, DeviceParingStateListener listener) {
        pairDevice(mBleClientModel.getDeviceByAddress(deviceAddress), listener);
    }

    //蓝牙配对。是否需要增加取消配对状态的方法？不需要。连接哪个设备由主程序决定，跟配对清单没关系。
    public void pairDevice(BluetoothDevice device, DeviceParingStateListener listener) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.pairDevice(device, listener);
        } else {
            listener.onError("Bt Service is not bonded.");
        }
    }

    public boolean pairDeviceWithoutUserConsent(String devAddress) {
        return pairDeviceWithoutUserConsent(mBleClientModel.getDeviceByAddress(devAddress));
    }

    public boolean pairDeviceWithoutUserConsent(BluetoothDevice device) {
        return mBleClientModel.pairDeviceWithoutUserConsent(device);
    }

    public boolean removePairedDeviceWithoutUserConsent(String devAddress) {
        return removePairedDeviceWithoutUserConsent(mBleClientModel.getDeviceByAddress(devAddress));
    }

    public boolean removePairedDeviceWithoutUserConsent(BluetoothDevice device) {
        return mBleClientModel.removePairedDeviceWithoutUserConsent(device);
    }

    //知道蓝牙地址，连接蓝牙设备
    public void connectDevice(String deviceAddress, BtDeviceConnectListener listener) {
        connectDevice(mBleClientModel.getDeviceByAddress(deviceAddress), listener);
    }

    //知道蓝牙对象，连接蓝牙设备
    public void connectDevice(BluetoothDevice device, BtDeviceConnectListener listener) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.connectDevice(device, listener);
        } else {
            listener.onConnectFail("Bt Service is not bonded.");
        }
    }

    public void pairAndConnectAnotherDeviceOfTheSameType(String deviceAddress) {
        pairAndConnectAnotherDeviceOfTheSameType(mBleClientModel.getDeviceByAddress(deviceAddress));
    }

    public void pairAndConnectAnotherDeviceOfTheSameType(BluetoothDevice device) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.pairAndConnectToAnotherDeviceOfTheSameType(device);
        } else {
            showLog("Bt Service is not bonded.");
        }
    }

    private void showLog(String msg) {
        LogUtils.showLog(msg);
    }

    //断开蓝牙
    public void disConnectDevice() {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.disConnectDevice();
        }
    }

    //写入特性
    public void writeCharacteristic(byte[] data, String serviceUUID, String charUUID, TgiWriteCharCallback callback) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.writeChar(data, serviceUUID, charUUID, callback);
        } else {
            callback.onWriteFailed("Bt Service is not bonded.");
        }

    }

    //读取特性
    public void readCharacteristic(String serviceUUID, String charUUID, TgiReadCharCallback callback) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.readChar(serviceUUID, charUUID, callback);
        } else {
            callback.onError("Bt Service is not bonded.");
        }
    }

    //注册/取消通知
    public void toggleNotification(String serviceUUID, String charUUID, String descUUID,
                                   boolean isToTurnOn, TgiToggleNotificationCallback callback) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.toggleNotification(serviceUUID, charUUID, descUUID, isToTurnOn, callback);
        } else {
            callback.onError("Bt Service is not bonded.");
        }
    }

    private boolean checkIfServiceAvailable() {
        return mTgiBleServiceBinder != null;
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public void onRequestBtPermissionsResult(
            final Activity activity,
            final int requestCode,
            String[] permissions,
            int[] grantResults) {
        BtPermissionsChecker.onRequestBtPermissionsResult(
                activity,
                requestCode,
                permissions,
                grantResults,
                new Runnable() {
                    @Override
                    public void run() {
                        //2，检查蓝牙硬件是否支持BLE
                        checkIfBtAvailable(activity);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        //默认如果不授权就退出当前界面
                        activity.onBackPressed();
                    }
                }
        );
    }


    public void setDebugMode(boolean isDebugMode) {
        LogUtils.setIsDebug(isDebugMode);
    }


    public String getBondSateDescription(int bondStateCode) {
        return mBleClientModel.getBondStateDescription(bondStateCode);
    }

    public String getBtEnableSateDescription(int enableSateCode) {
        return mBleClientModel.getBtEnableStateDescription(enableSateCode);
    }
}
