package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.hardware.camera2.CaptureRequest;
import android.os.SystemClock;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static tgi.com.librarybtmanager.LogUtils.showLog;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
class TgiBtGattCallback extends BluetoothGattCallback {
    private ArrayList<TgiWriteCharSession> mWriteSessions = new ArrayList<>();
    private ArrayList<TgiReadCharSession> mReadSessions = new ArrayList<>();
    private ArrayList<TgiToggleNotificationSession> mToggleNotificationSessions = new ArrayList<>();
    private BluetoothGattCallback mGattCallback;
    private HashMap<String, TgiToggleNotificationSession> mCharChangedListeners = new HashMap<>();

    TgiBtGattCallback(BluetoothGattCallback gattCallback) {
        mGattCallback = gattCallback;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        mGattCallback.onConnectionStateChange(gatt, status, newState);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        mGattCallback.onServicesDiscovered(gatt, status);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        //支持同时读取多个特性，不冲突
        String uuid = SessionUUIDGenerator.genReadWriteSessionUUID(gatt.getDevice(), characteristic);
        Iterator<TgiReadCharSession> iterator = mReadSessions.iterator();
        while (iterator.hasNext()) {
            TgiReadCharSession session = iterator.next();
            if (session.getSessionUUID().equals(uuid)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    byte[] value = characteristic.getValue();
                    session.getTgiReadCharCallback().onCharRead(characteristic, value.clone());
                } else {
                    session.getTgiReadCharCallback().onError("Target characteristic read fails. Status code:"+status);
                }
                session.close();
                iterator.remove();
            }
        }

    }


    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        //支持同时写入多个特性，不冲突
        String uuid = SessionUUIDGenerator.genReadWriteSessionUUID(gatt.getDevice(), characteristic);
        Iterator<TgiWriteCharSession> iterator = mWriteSessions.iterator();
//        byte[] value = characteristic.getValue();
//        showLog("onCharacteristicWrite:");
//        StringBuilder sb=new StringBuilder();
//        for(byte temp:value){
//            sb.append("0x").append(Integer.toHexString(temp)).append(" ");
//        }
//        showLog(sb.toString());
        while (iterator.hasNext()) {
            TgiWriteCharSession session = iterator.next();
            if (session.getSessionUUID().equals(uuid)) {
                byte[] valueBeWritten = characteristic.getValue();
                if (Arrays.equals(session.getWriteContent(), valueBeWritten)) {
                    session.getTgiWriteCharCallback().onWriteSuccess(characteristic);
                } else {
                    session.getTgiWriteCharCallback().onWriteFailed("Target characteristic write fails: data is not fully written.");
                }
//                if (status == BluetoothGatt.GATT_SUCCESS) {
//                    byte[] valueBeWritten = characteristic.getValue();
//                    if (Arrays.equals(session.getWriteContent(), valueBeWritten)) {
//                        session.getTgiWriteCharCallback().onWriteSuccess(characteristic);
//                    } else {
//                        session.getTgiWriteCharCallback().onWriteFailed("Data is not fully written.");
//                    }
//                } else {
//                    session.getTgiWriteCharCallback().onWriteFailed("Target characteristic write fails. Status code:"+status);
//                }
                session.close();
                iterator.remove();
                break;
            }
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        //支持同时设置多个通知，不冲突
        String uuid = SessionUUIDGenerator.genToggleNotificationSessionUUID(gatt.getDevice(), descriptor);
        Iterator<TgiToggleNotificationSession> iterator = mToggleNotificationSessions.iterator();
        while (iterator.hasNext()) {
            TgiToggleNotificationSession session = iterator.next();
            if (session.getSessionUUID().equals(uuid)) {
                boolean toTurnOn = session.isToTurnOn();
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    byte[] currentValue = descriptor.getValue();
                    byte[] expectValue = toTurnOn ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                    if (Arrays.equals(currentValue, expectValue)) {
                        session.getTgiToggleNotificationCallback().onToggleNotificationSuccess(descriptor);
                    } else {
                        session.getTgiToggleNotificationCallback().onError("The value of descriptor dose not match the target value.");
                    }
                    //更新map内容，这是为了onCharacteristicChanged中注册了通知的char的值发生变化时，用来传递变化的数据。
                    //为什么key没有用到descriptor的UUID,而是只用了device地址和Char的UUID?因为在onCharChange()回调中，没有提供descriptor
                    //的对象，无法实现完美匹配。
                    String key = SessionUUIDGenerator.genReadWriteSessionUUID(gatt.getDevice(), descriptor.getCharacteristic());
                    if (toTurnOn) {
                        mCharChangedListeners.put(key, session);
                    } else {
                        mCharChangedListeners.remove(key);
                        session.close();
                    }
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Fails to ");
                    if (toTurnOn) {
                        sb.append("enable notification.");
                    } else {
                        sb.append("disable notification.");
                    }
                    session.getTgiToggleNotificationCallback().onError(sb.toString());
                    session.close();
                }
                iterator.remove();
            }
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        //在mCharChangedListeners根据UUID找到之前注册通知的回调，逐个返回最新数据。
        String uuid = SessionUUIDGenerator.genReadWriteSessionUUID(gatt.getDevice(), characteristic);
        if(mCharChangedListeners.size()>0){
            Iterator<Map.Entry<String, TgiToggleNotificationSession>> iterator = mCharChangedListeners.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, TgiToggleNotificationSession> next = iterator.next();
                String key = next.getKey();
                if (key.equals(uuid)) {
                    TgiToggleNotificationSession session = next.getValue();
                    if (session != null) {
                        session.getTgiToggleNotificationCallback().onCharChanged(gatt, characteristic);
                    }
                }else {
                    showLog("Key not matched: ");
                    showLog("uuid="+uuid);
                    showLog("uuid of history log:"+key);
                }
            }
        }else {
            showLog("mCharChangedListeners size=0");
        }


    }

    void registerWriteSession(TgiWriteCharSession tgiWriteCharSession) {
        if (!mWriteSessions.contains(tgiWriteCharSession)) {
            mWriteSessions.add(tgiWriteCharSession);
        }
    }

    void unRegisterWriteSession(TgiWriteCharSession session) {
        mWriteSessions.remove(session);

    }

    void registerReadSession(TgiReadCharSession tgiReadCharSession) {
        if (!mReadSessions.contains(tgiReadCharSession)) {
            mReadSessions.add(tgiReadCharSession);
        }

    }

    void unRegisterReadSession(TgiReadCharSession session) {
        mReadSessions.remove(session);
    }

    void registerToggleNotificationSession(TgiToggleNotificationSession tgiToggleNotificationSession) {
        if (!mToggleNotificationSessions.contains(tgiToggleNotificationSession)) {
            mToggleNotificationSessions.add(tgiToggleNotificationSession);
        }
    }

    void unRegisterToggleNotificationSession(TgiToggleNotificationSession session) {
        mToggleNotificationSessions.remove(session);
    }

    void clear() {
        mWriteSessions.clear();
        mReadSessions.clear();
        mToggleNotificationSessions.clear();
        mCharChangedListeners.clear();
    }


}
