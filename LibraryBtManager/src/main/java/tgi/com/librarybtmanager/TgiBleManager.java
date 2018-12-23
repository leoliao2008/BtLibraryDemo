package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.RequiresApi;

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
    static TgiBleManager tgiBleManager;
    private BleClientModel mBleClientModel;
    private BleInitCallBack mBleInitCallBack;
    private TgiBleService.TgiBleServiceBinder mTgiBleServiceBinder;
    private ServiceConnection mServiceConnection;
    private TgiBleScanCallback mTgiBleScanCallback;
    private Handler mHandler;
    private Runnable mRunnableStopScanningDevices = new Runnable() {
        @Override
        public void run() {
            try {
                stopScanBtDevice();
            } catch (BtNotEnabledException e) {
                e.printStackTrace();
            }
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
        //3，打开蓝牙后台服务，在服务中进行后续操作
        startBtService(activity);
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
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };
        //5，然后再bind蓝牙后台服务，目的是获取binder
        boolean isSuccess = activity.bindService(
                new Intent(activity, TgiBleService.class),
                mServiceConnection,
                Context.BIND_AUTO_CREATE
        );
    }


    public void stopBtService(Activity activity) {
        //先解除绑定
        if (mServiceConnection != null) {
            activity.unbindService(mServiceConnection);
            mServiceConnection = null;
        }
        //然后正式停止
        Intent intent = new Intent(activity, TgiBleService.class);
        activity.stopService(intent);
    }

    //扫描周围蓝牙设备
    @SuppressLint("MissingPermission")
    public void startScanBtDevices(TgiBleScanCallback callback) throws BtNotEnabledException {
        if (mTgiBleServiceBinder == null) {
            return;
        }
        mTgiBleScanCallback = callback;
        //        Set<BluetoothDevice> bondedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        //        //先把曾经配对过的设备返回
        //        for(BluetoothDevice device:bondedDevices){
        //            callback.onLeScan(device,0,new byte[0]);
        //        }
        mTgiBleServiceBinder.startScanDevice(callback);
        //5秒后自动停止扫描
        mHandler.postDelayed(mRunnableStopScanningDevices, 5000);
    }

    //停止扫描周围蓝牙设备
    public void stopScanBtDevice() throws BtNotEnabledException {
        if (mTgiBleServiceBinder == null || mTgiBleScanCallback == null) {
            return;
        }
        mTgiBleServiceBinder.stopScanDevice(mTgiBleScanCallback);
        //确保消息队列里停止扫描的操作被清除，因为已经没意义。
        mHandler.removeCallbacks(mRunnableStopScanningDevices);
    }

    //蓝牙配对。是否需要增加取消配对状态的方法？不需要。连接哪个设备由主程序决定，跟配对清单没关系。
    public void pairDevice(BluetoothDevice device, DeviceParingStateListener listener) throws BtNotEnabledException {
        mTgiBleServiceBinder.pairDevice(device, listener);
    }

    //知道蓝牙地址，连接蓝牙设备
    public void connectDevice(String deviceAddress, BtDeviceConnectStateListener listener)
            throws BtNotBondedException, BtNotEnabledException {
        connectDevice(mBleClientModel.getDeviceByAddress(deviceAddress), listener);
    }

    //知道蓝牙对象，连接蓝牙设备
    public void connectDevice(BluetoothDevice device, BtDeviceConnectStateListener listener)
            throws BtNotBondedException, BtNotEnabledException {
        mTgiBleServiceBinder.connectDevice(device, listener);
    }

    //写入特性
    public void writeCharacteristic(byte[] data, String serviceUUID, String charUUID,TgiWriteCharCallback callback)
            throws BtNotConnectedYetException, BtNotEnabledException, BtNotBondedException {
        mTgiBleServiceBinder.writeChar(data,serviceUUID,charUUID,callback);
    }

    //读取特性
    public void readCharacteristic(String serviceUUID,String charUUID,TgiReadCharCallback callback)
            throws BtNotConnectedYetException, BtNotEnabledException, BtNotBondedException {
        mTgiBleServiceBinder.readChar(serviceUUID,charUUID,callback);
    }

    //注册/取消通知
    public void toggleNotification(Activity context,String serviceUUID,String charUUID,String descUUID,boolean isToTurnOn,TgiToggleNotificationCallback callback)
            throws BtNotConnectedYetException, BtNotEnabledException, BtNotBondedException {
        mTgiBleServiceBinder.toggleNotification(serviceUUID,charUUID,descUUID,isToTurnOn,callback);
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
                        activity.onBackPressed();
                    }
                }
        );
    }


    public void setDebugMode(boolean isDebugMode) {
        LogUtils.setIsDebug(isDebugMode);
    }
}
