package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;

import java.util.Objects;

import static tgi.com.librarybtmanager.LogUtils.showLog;

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
    private String mSessionUUID;
    private boolean mIsToTurnOn;

    TgiToggleNotificationSession(
            String devAddress,
            BluetoothGattDescriptor descriptor,
            boolean isToTurnOn,
            TgiBtGattCallback tgiBtGattCallback) {
        mDescriptor = descriptor;
        mBtGattCallback = tgiBtGattCallback;
        mIsToTurnOn = isToTurnOn;
        mSessionUUID = SessionUUIDGenerator.genToggleNotificationSessionUUID(devAddress, mDescriptor);
    }


    void start(BluetoothGatt gatt,TgiToggleNotificationCallback callback) {
        boolean isInitSuccess = gatt.setCharacteristicNotification(mDescriptor.getCharacteristic(), mIsToTurnOn);
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
        //开始设置通知，后续步骤将会在TgiBtGattCallback的onDescriptorWrite回调中进行。
        isInitSuccess = gatt.writeDescriptor(mDescriptor);
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



}
