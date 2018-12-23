package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
public class TgiReadCharSession {
    private BluetoothGatt mBtGatt;
    private TgiBtGattCallback mBtGattCallback;
    private TgiReadCharCallback mTgiReadCharCallback;
    private BluetoothGattCharacteristic mBtChar;
    private String mSessionUUID;

    public TgiReadCharSession(
            BluetoothGatt btGatt,
            BluetoothGattCharacteristic btChar,
            TgiBtGattCallback tgiBtGattCallback) {
        mBtGatt = btGatt;
        mBtGattCallback = tgiBtGattCallback;
        mBtChar = btChar;
        mSessionUUID = SessionUUIDGenerator.genReadWriteSessionUUID(btGatt.getDevice(), btChar);
    }

    void read(TgiReadCharCallback callback){
        boolean isInitSuccess = mBtGatt.readCharacteristic(mBtChar);
        if(!isInitSuccess){
            callback.onError("Read operation fails to initiate.");
            return;
        }
        mTgiReadCharCallback=callback;
        mBtGattCallback.registerReadSession(this);
    }

    String getSessionUUID() {
        return mSessionUUID;
    }

    TgiReadCharCallback getTgiReadCharCallback() {
        return mTgiReadCharCallback;
    }

    void close() {
        mTgiReadCharCallback = null;
    }


}
