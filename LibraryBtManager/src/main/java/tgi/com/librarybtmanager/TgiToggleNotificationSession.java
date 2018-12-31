package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.media.FaceDetector;

import java.util.Objects;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
public class TgiToggleNotificationSession {
    private TgiBtGattCallback mBtGattCallback;
    private TgiToggleNotificationCallback mTgiToggleNotificationCallback;
    private BluetoothGattDescriptor mDescriptor;
    private BluetoothGatt mBluetoothGatt;
    private String mSessionUUID;
    private boolean mIsToTurnOn;

    TgiToggleNotificationSession(
            BluetoothGatt btGatt,
            BluetoothGattDescriptor descriptor,
            boolean isToTurnOn,
            TgiBtGattCallback tgiBtGattCallback) {
        mDescriptor = descriptor;
        mBtGattCallback = tgiBtGattCallback;
        mIsToTurnOn = isToTurnOn;
        mBluetoothGatt=btGatt;
        mSessionUUID = SessionUUIDGenerator.genToggleNotificationSessionUUID(btGatt.getDevice(), mDescriptor);
    }


    void start(TgiToggleNotificationCallback callback) {
        boolean isInitSuccess = mBluetoothGatt.setCharacteristicNotification(mDescriptor.getCharacteristic(), mIsToTurnOn);
        if (!isInitSuccess) {
            callback.onError("Target characteristic value cannot be set.");
            showLog("通知设置失败：更新本地特性值失败");
            return;
        }
        if (mIsToTurnOn) {
            isInitSuccess = mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            isInitSuccess = mDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        if (!isInitSuccess) {
            callback.onError("Target descriptor value cannot be set.");
            showLog("通知设置失败：更新本地描述值失败");
            return;
        }
        mTgiToggleNotificationCallback = callback;
        mBtGattCallback.registerToggleNotificationSession(this);
        isInitSuccess = mBluetoothGatt.writeDescriptor(mDescriptor);
        //如果一开始就无法启动流程，则直接结束，释放资源。
        if (!isInitSuccess) {
            callback.onError("Target descriptor cannot be written into characteristic.");
            showLog("通知设置失败：启动不了远程更新。");
            mBtGattCallback.unRegisterToggleNotificationSession(this);
            close();
        }
    }

    String getSessionUUID() {
        return mSessionUUID;
    }

    TgiToggleNotificationCallback getTgiToggleNotificationCallback() {
        return mTgiToggleNotificationCallback;
    }

    boolean isToTurnOn() {
        return mIsToTurnOn;
    }

    void close() {
        mTgiToggleNotificationCallback = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TgiToggleNotificationSession session = (TgiToggleNotificationSession) o;
        return Objects.equals(mSessionUUID, session.mSessionUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSessionUUID);
    }

    private void showLog(String msg){
        LogUtils.showLog(msg);
    }


}
