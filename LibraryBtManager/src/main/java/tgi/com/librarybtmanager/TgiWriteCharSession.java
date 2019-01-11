package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
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
public class TgiWriteCharSession {
    private TgiBtGattCallback mBtGattCallback;
    private TgiWriteCharCallback mTgiWriteCharCallback;
    private String mSessionUUID;
    private byte[] mWriteContent;
    private String mCharUUID;
    private String mServiceUUID;

    TgiWriteCharSession(
            String deviceAddress,
            String btCharUUID,
            String btServiceUUID,
            TgiBtGattCallback tgiBtGattCallback) {

        mBtGattCallback = tgiBtGattCallback;
        mCharUUID=btCharUUID;
        mServiceUUID=btServiceUUID;
        mSessionUUID = SessionUUIDGenerator.genReadWriteSessionUUID(deviceAddress, mCharUUID,mServiceUUID);
    }

    void write(BluetoothGatt gatt,byte[] data, TgiWriteCharCallback callback) {
        BluetoothGattService service = gatt.getService(UUID.fromString(mServiceUUID));
        if(service==null){
            showLog("蓝牙写入前无法找到对应的Service。");
            callback.onWriteFailed("Cannot find target BluetoothGattService when commencing write operation.");
            return;
        }
        BluetoothGattCharacteristic btChar=service.getCharacteristic(UUID.fromString(mCharUUID));
        if(btChar==null){
            showLog("蓝牙写入前无法找到对应的Char。");
            callback.onWriteFailed("Cannot find target BluetoothGattCharacteristic when commencing write operation.");
            return;
        }
        boolean isSet = btChar.setValue(data);
        if (!isSet) {
            callback.onWriteFailed("Fail to set data to local characteristic.");
            showLog("蓝牙写入时需要先把目标值写入本地内存，这里内存写入失败，导致蓝牙写入失败。");
            return;
        }
        mWriteContent = data;
        mTgiWriteCharCallback = callback;
        mBtGattCallback.registerWriteSession(this);
        showLog("开启式启动蓝牙写入...");
        boolean isInitSuccess = gatt.writeCharacteristic(btChar);
        //如果一开始就无法启动写入，则直接取消操作，释放资源。
        if (!isInitSuccess) {
            callback.onWriteFailed("Write operation fails to initiate.");
            showLog("无法启动写入，取消操作，释放资源，蓝牙写入失败。");
            mBtGattCallback.unRegisterWriteSession(this);
            close();
        }else {
            showLog("蓝牙写入启动成功，等待返回回调...");
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
