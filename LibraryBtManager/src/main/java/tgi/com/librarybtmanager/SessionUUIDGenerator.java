package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import java.lang.ref.SoftReference;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
class SessionUUIDGenerator {
    private SessionUUIDGenerator() {
    }

    static String genReadWriteSessionUUID(String devAddress, String charUUID, String serviceUUID) {
        return new StringBuilder().append(devAddress)
                .append("@")
                .append(serviceUUID)
                .append("@")
                .append(charUUID)
                .toString();
    }

    static String genToggleNotificationSessionUUID(String devAddress, BluetoothGattDescriptor descriptor) {
        return new StringBuilder().append(devAddress)
                .append("@")
                .append(descriptor.getCharacteristic().getService().getUuid().toString())
                .append("@")
                .append(descriptor.getCharacteristic().getUuid().toString())
                .append("@")
                .append(descriptor.getUuid().toString())
                .toString();
    }

    static String genToggleNotificationSessionUUID(String devAddress, String descriptorUUID,String charUUID,String serviceUUID) {
        return new StringBuilder().append(devAddress)
                .append("@")
                .append(serviceUUID)
                .append("@")
                .append(charUUID)
                .append("@")
                .append(descriptorUUID)
                .toString();
    }

    /**
     * 解析NotificationSessionUUID，返回一个长度为4的字符串数组，下标0-3分别表示bt device address,service uuid,char uuid,descriptor uuid。
     *
     * @param notificationSessionUUID
     * @return
     */
    static String[] decryptNotificationSessionUUID(String notificationSessionUUID) {
        String[] split = notificationSessionUUID.split("@");
        if (split.length == 4) {
            return split;
        }
        return null;
    }
}
