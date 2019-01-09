package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import java.util.Objects;

import static tgi.com.librarybtmanager.LogUtils.showLog;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
public class TgiWriteCharSession {
    private TgiBtGattCallback mBtGattCallback;
    private TgiWriteCharCallback mTgiWriteCharCallback;
    private BluetoothGattCharacteristic mBtChar;
    private String mSessionUUID;
    private byte[] mWriteContent;

    TgiWriteCharSession(
            String deviceAddress,
            BluetoothGattCharacteristic btChar,
            TgiBtGattCallback tgiBtGattCallback) {

        mBtGattCallback = tgiBtGattCallback;
        mBtChar = btChar;
        mSessionUUID = SessionUUIDGenerator.genReadWriteSessionUUID(deviceAddress, btChar);
    }

    void write(BluetoothGatt gatt,byte[] data, TgiWriteCharCallback callback) {
        boolean isSet = mBtChar.setValue(data);
        if (!isSet) {
            callback.onWriteFailed("Fail to set data to local characteristic.");
            showLog("蓝牙写入时需要先把目标值写入本地内存，这里内存写入失败，导致蓝牙写入失败。");
            return;
        }
        mWriteContent = data;
        mTgiWriteCharCallback = callback;
        mBtGattCallback.registerWriteSession(this);
        boolean isInitSuccess = gatt.writeCharacteristic(mBtChar);
        //如果一开始就无法启动写入，则直接取消操作，释放资源。
        if (!isInitSuccess) {
            callback.onWriteFailed("Write operation fails to initiate.");
            showLog("无法启动写入，取消操作，释放资源，蓝牙写入失败。");
            mBtGattCallback.unRegisterWriteSession(this);
            close();
        }

    }

    String getSessionUUID() {
        return mSessionUUID;
    }

    TgiWriteCharCallback getTgiWriteCharCallback() {
        return mTgiWriteCharCallback;
    }

    byte[] getWriteContent() {
        return mWriteContent;
    }

    void close() {
        mWriteContent = null;
        mTgiWriteCharCallback = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TgiWriteCharSession session = (TgiWriteCharSession) o;
        return Objects.equals(mSessionUUID, session.mSessionUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSessionUUID);
    }


}
