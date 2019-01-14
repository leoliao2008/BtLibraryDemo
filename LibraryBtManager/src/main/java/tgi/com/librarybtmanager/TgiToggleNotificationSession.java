package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.Objects;
import java.util.UUID;

import static tgi.com.librarybtmanager.TgiBtManagerLogUtils.showLog;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
public class TgiToggleNotificationSession {
    private TgiBtGattCallback mBtGattCallback;
    private TgiToggleNotificationCallback mTgiToggleNotificationCallback;
    private String mServiceUUID;
    private String mCharUUID;
    private String mDesUUID;
    private String mSessionUUID;
    private boolean mIsToTurnOn;

    TgiToggleNotificationSession(
            String devAddress,
            String serviceUUID,
            String charUUID,
            String descUUID,
            boolean isToTurnOn,
            TgiBtGattCallback tgiBtGattCallback) {
        mBtGattCallback = tgiBtGattCallback;
        mIsToTurnOn = isToTurnOn;
        mDesUUID = descUUID;
        mCharUUID = charUUID;
        mServiceUUID = serviceUUID;
        mSessionUUID = SessionUUIDGenerator.genToggleNotificationSessionUUID(devAddress, descUUID, charUUID, serviceUUID);
    }


    void start(BluetoothGatt gatt, TgiToggleNotificationCallback callback) {
        BluetoothGattService service = gatt.getService(UUID.fromString(mServiceUUID));
        if (service == null) {
            callback.onError("Target service cannot be reached.");
            showLog("通知设置失败：找不到目标服务");
            return;
        }
        BluetoothGattCharacteristic btChar = service.getCharacteristic(UUID.fromString(mCharUUID));
        if (btChar == null) {
            callback.onError("Target characteristic cannot be reached.");
            showLog("通知设置失败：找不到目标特性");
            return;
        }
        BluetoothGattDescriptor descriptor = btChar.getDescriptor(UUID.fromString(mDesUUID));
        if (descriptor == null) {
            callback.onError("Target descriptor cannot be reached.");
            showLog("通知设置失败：找不到目标描述");
            return;
        }
        boolean isInitSuccess = gatt.setCharacteristicNotification(btChar, mIsToTurnOn);
        if (!isInitSuccess) {
            callback.onError("Target characteristic value cannot be set.");
            showLog("通知设置失败：更新本地特性值失败");
            return;
        }else {
            showLog("通知设置第一步成功："+mIsToTurnOn);
        }
        if (mIsToTurnOn) {
            isInitSuccess = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            isInitSuccess = descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        if (!isInitSuccess) {
            callback.onError("Target descriptor value cannot be set.");
            showLog("通知设置失败：更新本地描述值失败");
            return;
        }else {
            showLog("通知设置第二步成功："+mIsToTurnOn);
        }
        mTgiToggleNotificationCallback = callback;
        mBtGattCallback.registerToggleNotificationSession(this);
        //开始设置通知，后续步骤将会在TgiBtGattCallback的onDescriptorWrite回调中进行。
        isInitSuccess = gatt.writeDescriptor(descriptor);
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
