package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;

import java.util.Objects;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
public class TgiToggleNotificationSession {
    private BluetoothGatt mBluetoothGatt;
    private TgiBtGattCallback mBtGattCallback;
    private TgiToggleNotificationCallback mTgiToggleNotificationCallback;
    private BluetoothGattDescriptor mDescriptor;
    private String mSessionUUID;
    private boolean mIsToTurnOn;

    TgiToggleNotificationSession(
            BluetoothGatt bluetoothGatt,
            BluetoothGattDescriptor descriptor,
            boolean isToTurnOn,
            TgiBtGattCallback tgiBtGattCallback) {
        mBluetoothGatt = bluetoothGatt;
        mDescriptor = descriptor;
        mBtGattCallback = tgiBtGattCallback;
        mIsToTurnOn = isToTurnOn;
        mSessionUUID = SessionUUIDGenerator.genToggleNotificationSessionUUID(bluetoothGatt.getDevice(), mDescriptor);
    }


    void start(TgiToggleNotificationCallback callback) {
        boolean isInitSuccess = mBluetoothGatt.setCharacteristicNotification(mDescriptor.getCharacteristic(), mIsToTurnOn);
        if (!isInitSuccess) {
            callback.onError("Target characteristic value cannot be set.");
            return;
        }
        if (mIsToTurnOn) {
            isInitSuccess = mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            isInitSuccess = mDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }

        if (!isInitSuccess) {
            callback.onError("Target descriptor value cannot be set.");
            return;
        }
        mTgiToggleNotificationCallback = callback;
        mBtGattCallback.registerToggleNotificationSession(this);
        isInitSuccess = mBluetoothGatt.writeDescriptor(mDescriptor);
        //如果一开始就无法启动流程，则直接结束，释放资源。
        if (!isInitSuccess) {
            callback.onError("Target descriptor cannot be written into characteristic.");
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
