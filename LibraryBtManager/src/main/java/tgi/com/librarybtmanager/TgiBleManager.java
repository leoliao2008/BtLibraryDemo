package tgi.com.librarybtmanager;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;

import java.lang.ref.WeakReference;

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


    private TgiBleManager() {
        mBleClientModel = new BleClientModel();
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
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                //4，返回一个binder，用来操纵service
                mTgiBleServiceBinder = (TgiBleService.TgiBleServiceBinder) service;
                //5，在binder中设置一个回调，用来接收service返回的有用数据
                mTgiBleServiceBinder.setCallBack(new TgiBleServiceCallback() {
                    //6，当在回调中发现本机蓝牙模块关闭时，尝试重新打开。1-6流程到此完结。以上是本机蓝牙初始化的工作。
                    @Override
                    public void onBtAvailabilityChanged(int previousState, int currentState) {
                        super.onBtAvailabilityChanged(previousState, currentState);
                        LogUtils.showLog("onBtAvailabilityChanged pre:" + previousState + " current:" + currentState);
                        if (currentState == BluetoothAdapter.STATE_OFF) {
                            mTgiBleServiceBinder.enableBt();
                        }
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };
        boolean isSuccess = activity.bindService(
                new Intent(activity, TgiBleService.class),
                mServiceConnection,
                Context.BIND_AUTO_CREATE
        );
    }


    public void stopBtService(Activity activity) {
        if (mServiceConnection != null) {
            activity.unbindService(mServiceConnection);
            mServiceConnection = null;
        }
    }

    //扫描周围蓝牙设备
    public void startScanBtDevices(TgiBleScanCallback callback){
        if(mTgiBleServiceBinder==null){
            return;
        }
        mTgiBleScanCallback=callback;
        mTgiBleServiceBinder.startScanDevice(callback);
    }

    //停止扫描周围蓝牙设备
    public void stopScanBtDevcie(){
        if(mTgiBleServiceBinder==null||mTgiBleScanCallback==null){
            return;
        }
        mTgiBleServiceBinder.stopScanDevice(mTgiBleScanCallback);
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
