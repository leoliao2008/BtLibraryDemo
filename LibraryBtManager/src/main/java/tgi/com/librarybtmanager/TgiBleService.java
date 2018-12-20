package tgi.com.librarybtmanager;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

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
    private TgiBleServiceBinder mBinder;
    private TgiBtEnableStatesReceiver mBtConnStatesReceiver;
    private int mBtEnableState;
    private Handler mHandler;
    private TgiBleServiceCallback mTgiBleServiceCallback;


    //bindService 生命流程1
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

    //bindService 生命流程2
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        LogUtils.showLog("service is bounded.");
        mBinder = new TgiBleServiceBinder();
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
        return mBinder;
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
    }


    class TgiBleServiceBinder extends Binder {
        //3，TgiBleManager端会先设置回调，以保证及时收到TgiBtEnableStatesReceiver返回的数据
        void setCallBack(TgiBleServiceCallback callback) {
            mTgiBleServiceCallback = callback;
        }


        void enableBt() {
            TgiBleService.this.enableBt();
        }

        void startScanDevice(final TgiBleScanCallback callback) {
            checkBtStateBeforeProceed(new Runnable(){
                @Override
                public void run() {
                    mBleClientModel.startScanBtDevices(callback, mHandler);
                }
            });

        }

        void stopScanDevice(TgiBleScanCallback callback) {
            mBleClientModel.stopScanBtDevices(callback);
        }

    }

    private void checkBtStateBeforeProceed(Runnable onProceed) {
        if(mBtEnableState!=BluetoothAdapter.STATE_ON){
            enableBt();
        }else {
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
            if (mBinder != null && mBtEnableState != -1 && previous != -1) {
                if (mTgiBleServiceCallback != null) {
                    mTgiBleServiceCallback.onBtAvailabilityChanged(previous, mBtEnableState);
                }
            }
        }
    }


}
