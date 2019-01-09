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
public class TgiReadCharSession {
    private TgiBtGattCallback mBtGattCallback;
    private TgiReadCharCallback mTgiReadCharCallback;
    private BluetoothGattCharacteristic mBtChar;
    private String mSessionUUID;

    public TgiReadCharSession(
            String devAddress,
            BluetoothGattCharacteristic btChar,
            TgiBtGattCallback tgiBtGattCallback) {
        mBtGattCallback = tgiBtGattCallback;
        mBtChar = btChar;
        mSessionUUID = SessionUUIDGenerator.genReadWriteSessionUUID(devAddress, btChar);
    }

    void read(BluetoothGatt gatt,TgiReadCharCallback callback){
        mTgiReadCharCallback=callback;
        //把自身作为对象，保存到mBtGattCallback中，然后在onCharRead()回调中使用。
        //这样保证了可以同时处理多个read sessions.
        mBtGattCallback.registerReadSession(this);
        boolean isInitSuccess = gatt.readCharacteristic(mBtChar);
        //如果一开始就无法启动读取，则释放资源，放弃操作。
        if(!isInitSuccess){
            callback.onError("Read operation fails to initiate.");
            mBtGattCallback.unRegisterReadSession(this);
            close();
        }

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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TgiReadCharSession session = (TgiReadCharSession) o;
        return Objects.equals(mSessionUUID, session.mSessionUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSessionUUID);
    }
}
