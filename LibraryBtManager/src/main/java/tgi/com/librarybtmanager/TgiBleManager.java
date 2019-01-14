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
public class TgiBleManager {
    private static final int REQUEST_CODE_LOCATION_PERMISSION = 9528;
    private static TgiBleManager tgiBleManager;
    private TgiBleClientModel mTgiBleClientModel;
    private TgiBleInitCallBack mTgiBleInitCallBack;
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
        mTgiBleClientModel = new TgiBleClientModel();
        mHandler = new Handler();
    }

    public static synchronized TgiBleManager getInstance() {
        if (tgiBleManager == null) {
            tgiBleManager = new TgiBleManager();
        }
        return tgiBleManager;
    }

    public void startBtService(Activity activity, TgiBleInitCallBack callBack) {
        mTgiBleInitCallBack = callBack;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //1，检查是否有定位权限，如果没有，申请权限
            boolean isAllGranted = TgiBtPermissionsChecker.checkBtPermissions(activity);
            if (isAllGranted) {
                //2，检查蓝牙硬件是否支持BLE
                checkIfBtAvailable(activity);
            } else {
                TgiBtPermissionsChecker.requestBtPermissions(activity, REQUEST_CODE_LOCATION_PERMISSION);
            }
        } else {
            //2，检查蓝牙硬件是否支持BLE
            checkIfBtAvailable(activity);
        }
    }

    private void checkIfBtAvailable(Activity activity) {
        if (!mTgiBleClientModel.isBtSupported(activity.getPackageManager())) {
            if (mTgiBleInitCallBack != null) {
                mTgiBleInitCallBack.onError("This device does not support bluetooth functions.");
            }
            return;
        }
        if (!mTgiBleClientModel.hasBleFeature(activity.getPackageManager())) {
            if (mTgiBleInitCallBack != null) {
                mTgiBleInitCallBack.onError("This device does not support BLE features.");
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
        return mTgiBleClientModel.getDeviceByAddress(devAddress);
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
                if (mTgiBleInitCallBack != null) {
                    mTgiBleInitCallBack.onInitSuccess();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };
        //5，然后再bind蓝牙后台服务，目的是获取binder
        //Android 5.0及以上的设备，google出于安全的角度禁止了隐式声明Intent来启动Service.也禁止使用Intent filter.否则就会抛个异常出来.
        Intent bindIntent=new Intent(activity, TgiBleService.class);
        boolean isSuccess = activity.bindService(
                bindIntent,
                mServiceConnection,
                Context.BIND_AUTO_CREATE
        );
        //如果没有粘结成功，返回相关信息方便debug。
        if (isSuccess) {
            if (mTgiBleInitCallBack != null) {
                mTgiBleInitCallBack.onError("Fail to bind service.");
            }
        }
    }


    public void stopBtService(Activity activity) {
        //先解除绑定
        if (mServiceConnection != null && mTgiBleServiceBinder != null) {
            try {
                activity.unbindService(mServiceConnection);
            }catch (Exception e){
                //java.lang.IllegalArgumentException: Service not registered:
                e.printStackTrace();
            }
            mServiceConnection = null;
            mTgiBleServiceBinder = null;
        }
        //释放资源
        if (mTgiBleInitCallBack != null) {
            mTgiBleInitCallBack = null;
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
        return mTgiBleClientModel.getBondedDevices();
    }

    public boolean checkIfDeviceBonded(String deviceAddress) {
        return checkIfDeviceBonded(mTgiBleClientModel.getDeviceByAddress(deviceAddress));
    }

    @SuppressLint("MissingPermission")
    public boolean checkIfDeviceBonded(BluetoothDevice device) {
        return device.getBondState() == BluetoothDevice.BOND_BONDED;
    }

    //根据远程设备地址进行蓝牙配对。
    public void pairDevice(String deviceAddress, TgiDeviceParingStateListener listener) {
        pairDevice(mTgiBleClientModel.getDeviceByAddress(deviceAddress), listener);
    }

    //蓝牙配对。是否需要增加取消配对状态的方法？不需要。连接哪个设备由主程序决定，跟配对清单没关系。
    public void pairDevice(BluetoothDevice device, TgiDeviceParingStateListener listener) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.pairDevice(device, listener);
        } else {
            listener.onError("Bt Service is not bonded.");
        }
    }

    public boolean pairDeviceWithoutUserConsent(String devAddress) {
        return pairDeviceWithoutUserConsent(mTgiBleClientModel.getDeviceByAddress(devAddress));
    }

    public boolean pairDeviceWithoutUserConsent(BluetoothDevice device) {
        return mTgiBleClientModel.pairDeviceWithoutUserConsent(device);
    }

    public void pairDeviceWithoutUserConsent(String devAddress, TgiDeviceParingStateListener listener) {
        pairDeviceWithoutUserConsent(mTgiBleClientModel.getDeviceByAddress(devAddress),listener);
    }

    public void pairDeviceWithoutUserConsent(BluetoothDevice bluetoothDevice, TgiDeviceParingStateListener listener) {
        mTgiBleClientModel.pairDeviceWithoutUserConsent(bluetoothDevice,listener);
    }

    public boolean removePairedDeviceWithoutUserConsent(String devAddress) {
        return removePairedDeviceWithoutUserConsent(mTgiBleClientModel.getDeviceByAddress(devAddress));
    }

    public boolean removePairedDeviceWithoutUserConsent(BluetoothDevice device) {
        return mTgiBleClientModel.removePairedDeviceWithoutUserConsent(device);
    }

    //知道蓝牙地址，连接蓝牙设备
    public void connectDevice(String deviceAddress, TgiBtDeviceConnectListener listener) {
        connectDevice(mTgiBleClientModel.getDeviceByAddress(deviceAddress), listener);
    }

    //知道蓝牙对象，连接蓝牙设备
    public void connectDevice(BluetoothDevice device, TgiBtDeviceConnectListener listener) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.connectDevice(device, listener);
        } else {
            listener.onConnectFail("Bt Service is not bonded.");
        }
    }

    public void pairAndConnectAnotherDeviceOfTheSameType(String deviceAddress) {
        pairAndConnectAnotherDeviceOfTheSameType(mTgiBleClientModel.getDeviceByAddress(deviceAddress));
    }

    public void pairAndConnectAnotherDeviceOfTheSameType(BluetoothDevice device) {
        if (checkIfServiceAvailable()) {
            mTgiBleServiceBinder.pairAndConnectToAnotherDeviceOfTheSameType(device);
        } else {
            showLog("Bt Service is not bonded.");
        }
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
        TgiBtPermissionsChecker.onRequestBtPermissionsResult(
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
        TgiBtManagerLogUtils.setIsDebug(isDebugMode);
    }


    public String getBondSateDescription(int bondStateCode) {
        return mTgiBleClientModel.getBondStateDescription(bondStateCode);
    }

    public String getBtEnableSateDescription(int enableSateCode) {
        return mTgiBleClientModel.getBtEnableStateDescription(enableSateCode);
    }


}
