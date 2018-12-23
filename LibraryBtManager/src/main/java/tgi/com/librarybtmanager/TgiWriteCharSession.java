package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import java.util.Objects;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
public class TgiWriteCharSession {
    private BluetoothGatt mBluetoothGatt;
    private TgiBtGattCallback mBtGattCallback;
    private TgiWriteCharCallback mTgiWriteCharCallback;
    private BluetoothGattCharacteristic mBtChar;
    private String mSessionUUID;
    private byte[] mWriteContent;

    TgiWriteCharSession(
            BluetoothGatt bluetoothGatt,
            BluetoothGattCharacteristic btChar,
            TgiBtGattCallback tgiBtGattCallback) {

        mBluetoothGatt = bluetoothGatt;
        mBtGattCallback = tgiBtGattCallback;
        mBtChar = btChar;
        mSessionUUID = SessionUUIDGenerator.genReadWriteSessionUUID(bluetoothGatt.getDevice(), btChar);
    }

    void write(byte[] data, TgiWriteCharCallback callback) {
        boolean isSet = mBtChar.setValue(data);
        if (!isSet) {
            callback.onWriteFailed("Fail to set data to local characteristic.");
            return;
        }
        boolean isInitSuccess = mBluetoothGatt.writeCharacteristic(mBtChar);
        if (!isInitSuccess) {
            callback.onWriteFailed("Write operation fails to initiate.");
            return;
        }
        mWriteContent = data;
        mTgiWriteCharCallback = callback;
        mBtGattCallback.registerWriteSession(this);
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
