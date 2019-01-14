package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static tgi.com.librarybtmanager.TgiBtManagerLogUtils.showLog;

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
        String uuid = SessionUUIDGenerator.genReadWriteSessionUUID(
                gatt.getDevice().getAddress(),
                characteristic.getUuid().toString(),
                characteristic.getService().getUuid().toString());
        Iterator<TgiReadCharSession> iterator = mReadSessions.iterator();
        while (iterator.hasNext()) {
            TgiReadCharSession session = iterator.next();
            if (session.getSessionUUID().equals(uuid)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    byte[] value = characteristic.getValue();
                    session.getTgiReadCharCallback().onCharRead(characteristic, value.clone());
                } else {
                    session.getTgiReadCharCallback().onError("Target characteristic read fails. Status code:" + status);
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
        showLog("蓝牙写入回调被触发了。");
        String uuid = SessionUUIDGenerator.genReadWriteSessionUUID(
                gatt.getDevice().getAddress(),
                characteristic.getUuid().toString(),
                characteristic.getService().getUuid().toString());
        int size = mWriteSessions.size();
        if (size < 1) {
            showLog("mWriteSessions 数量为0，跳过。");
            return;
        }
        Iterator<TgiWriteCharSession> iterator = mWriteSessions.iterator();
        while (iterator.hasNext()) {
            TgiWriteCharSession session = iterator.next();
            if (session.getSessionUUID().equals(uuid)) {
                byte[] valueBeWritten = characteristic.getValue();
                if (Arrays.equals(session.getWriteContent(), valueBeWritten)) {
                    showLog("写入成功。");
                    session.getTgiWriteCharCallback().onWriteSuccess(characteristic);
                } else {
                    showLog("写入失败：信息未能全部写入");
                    session.getTgiWriteCharCallback().onWriteFailed("Target characteristic write fails: data is not fully written.");
                }
                session.close();
                iterator.remove();
                break;
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        //支持同时设置多个通知，不冲突
        String uuid = SessionUUIDGenerator.genToggleNotificationSessionUUID(gatt.getDevice().getAddress(), descriptor);
        Iterator<TgiToggleNotificationSession> iterator = mToggleNotificationSessions.iterator();
        while (iterator.hasNext()) {
            TgiToggleNotificationSession session = iterator.next();
            String sessionUUID = session.getSessionUUID();
            if (sessionUUID.equals(uuid)) {
                boolean toTurnOn = session.isToTurnOn();
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    showLog("Descriptor被写入了。");
                    byte[] currentValue = descriptor.getValue();
                    byte[] expectValue = toTurnOn ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                    if (Arrays.equals(currentValue, expectValue)) {
                        session.getTgiToggleNotificationCallback().onToggleNotificationSuccess(descriptor);
                    } else {
                        session.getTgiToggleNotificationCallback().onError("The value of descriptor dose not match the target value.");
                    }
                    //更新map内容，这是为了onCharacteristicChanged中注册了通知的char的值发生变化时，用来传递变化的数据。
                    String key = sessionUUID;
                    if (toTurnOn) {
                        mCharChangedListeners.put(key, session);
                    } else {
                        mCharChangedListeners.remove(key);
                        session.close();
                    }
                } else {
                    //如果不成功，就一直设置到成功为止。
                    showLog("Descriptor写入失败，重来...");
                    if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
                        session.start(gatt, session.getTgiToggleNotificationCallback());
                    }
                    break;
                }
                iterator.remove();
            }
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        //在mCharChangedListeners根据UUID找到之前注册通知的回调，逐个返回最新数据。
        String uuid = SessionUUIDGenerator.genReadWriteSessionUUID(
                gatt.getDevice().getAddress(),
                characteristic.getUuid().toString(),
                characteristic.getService().getUuid().toString());
        if (mCharChangedListeners.size() > 0) {
            Iterator<Map.Entry<String, TgiToggleNotificationSession>> iterator = mCharChangedListeners.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, TgiToggleNotificationSession> next = iterator.next();
                String key = next.getKey();
                //key还包含了descriptor的UUID，因此不会和uuid一样，用contains()判断即可。
                //为什么要保留descriptor的UUID？蓝牙模块关闭后重新打开时要用来重新设置通知
                if (key.contains(uuid)) {
                    TgiToggleNotificationSession session = next.getValue();
                    if (session != null) {
                        session.getTgiToggleNotificationCallback().onCharChanged(gatt, characteristic);
                    }
                }

            }
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
        mGattCallback.onMtuChanged(gatt, mtu, status);
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
        boolean contains = mToggleNotificationSessions.contains(tgiToggleNotificationSession);
        if (!contains) {
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

    //这个函数专门为了蓝牙模块被关闭后又被本库重新打开后，后续重新连接而设。
    HashMap<String, TgiToggleNotificationSession> getCurrentNotificationCallbacks() {
        return mCharChangedListeners;
    }
}
