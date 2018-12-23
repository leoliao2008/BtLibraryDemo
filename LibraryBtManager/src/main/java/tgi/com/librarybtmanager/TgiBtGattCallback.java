package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

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
                    session.getTgiReadCharCallback().onError("Target characteristic read fails.");
                }
                session.close();
                iterator.remove();
                break;
            }
        }

    }


    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        //支持同时写入多个特性，不冲突
        String uuid = SessionUUIDGenerator.genReadWriteSessionUUID(gatt.getDevice(), characteristic);
        Iterator<TgiWriteCharSession> iterator = mWriteSessions.iterator();
        while (iterator.hasNext()) {
            TgiWriteCharSession session = iterator.next();
            if (session.getSessionUUID().equals(uuid)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    byte[] valueBeWritten = characteristic.getValue();
                    if (Arrays.equals(session.getWriteContent(), valueBeWritten)) {
                        session.getTgiWriteCharCallback().onWriteSuccess(characteristic);
                    } else {
                        session.getTgiWriteCharCallback().onWriteFailed("Data is not fully written.");
                    }
                } else {
                    session.getTgiWriteCharCallback().onWriteFailed("Target characteristic write fails.");
                }
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
                    byte[] expectValue = toTurnOn ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                    if (Arrays.equals(currentValue, expectValue)) {
                        session.getTgiToggleNotificationCallback().onToggleNotificationSuccess(descriptor);
                    } else {
                        session.getTgiToggleNotificationCallback().onError("The value of descriptor dose not match the target value.");
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
                }
                session.close();
                iterator.remove();
                break;
            }
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        mGattCallback.onCharacteristicChanged(gatt,characteristic);
    }

    void registerWriteSession(TgiWriteCharSession tgiWriteCharSession) {
        if (!mWriteSessions.contains(tgiWriteCharSession)) {
            mWriteSessions.add(tgiWriteCharSession);
        }
    }

    void registerReadSession(TgiReadCharSession tgiReadCharSession) {
        if (!mReadSessions.contains(tgiReadCharSession)) {
            mReadSessions.add(tgiReadCharSession);
        }

    }

    void registerToggleNotificationSession(TgiToggleNotificationSession tgiToggleNotificationSession) {
        if (!mToggleNotificationSessions.contains(tgiToggleNotificationSession)) {
            mToggleNotificationSessions.add(tgiToggleNotificationSession);
        }
    }

    void clear() {
        mWriteSessions.clear();
        mReadSessions.clear();
        mToggleNotificationSessions.clear();
    }
}
