package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
class SessionUUIDGenerator {
    private SessionUUIDGenerator() {
    }

    static String genReadWriteSessionUUID(BluetoothDevice device, BluetoothGattCharacteristic btChar) {
        return new StringBuilder().append(device.getAddress())
                .append("@")
                .append(btChar.getService().getUuid().toString())
                .append("@")
                .append(btChar.getUuid().toString())
                .toString();
    }

    static String genToggleNotificationSessionUUID(BluetoothDevice device, BluetoothGattDescriptor descriptor) {
        return new StringBuilder().append(device.getAddress())
                .append("@")
                .append(descriptor.getCharacteristic().getService().getUuid().toString())
                .append("@")
                .append(descriptor.getCharacteristic().getUuid().toString())
                .append("@")
                .append(descriptor.getUuid().toString())
                .toString();
    }

    /**
     * 解析NotificationSessionUUID，返回一个长度为4的字符串数组，下标0-3分别表示bt device address, service uuid,char uuid,descriptor uuid。
     * @param notificationSessionUUID
     * @return
     */
    static String[] decypherNotificationSessionUUID(String notificationSessionUUID) {
        String[] split = notificationSessionUUID.split("@");
        if (split.length == 4) {
            return split;
        }
        return null;
    }
}
