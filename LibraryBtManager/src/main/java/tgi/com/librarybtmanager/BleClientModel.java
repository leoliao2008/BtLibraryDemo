package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import static tgi.com.librarybtmanager.LogUtils.showLog;

class BleClientModel {

    boolean isBtSupported(PackageManager manager) {
        return manager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    }

    boolean hasBleFeature(PackageManager manager) {
        return manager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    @SuppressLint("MissingPermission")
    boolean isBtEnabled() {
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }


    @SuppressLint("MissingPermission")
    void startScanBtDevices(final TgiBleScanCallback callback) {
        callback.onPreScan();
        BluetoothAdapter.getDefaultAdapter().startLeScan(callback);
    }

    @SuppressLint("MissingPermission")
    void stopScanBtDevices(TgiBleScanCallback callback) {
        BluetoothAdapter.getDefaultAdapter().stopLeScan(callback);
        callback.onPostScan();
    }

    @SuppressLint("MissingPermission")
    boolean pairDevice(BluetoothDevice device) {
        return device.createBond();
    }


    BluetoothDevice getDeviceByAddress(String deviceAddress) {
        //判断蓝牙地址是否符合规范
        if (BluetoothAdapter.checkBluetoothAddress(deviceAddress.toUpperCase())) {
            return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
        } else {
            //如果不符合规范返回null
            showLog("DeviceAddress " + deviceAddress + " is not a valid MAC address!");
            return null;
        }
    }

    @SuppressLint("MissingPermission")
    boolean checkIfDeviceConnected(Context context, BluetoothDevice device) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        int state = bluetoothManager.getConnectionState(device, BluetoothProfile.GATT);
        showLog("实时检查当前连接状态：" + getBtDeviceConnectionStateDescription(state));
        return state == BluetoothProfile.STATE_CONNECTED;
    }


    boolean pairDeviceWithoutUserConsent(BluetoothDevice device) {
        boolean result = false;
        try {
            Method createBondMethod = BluetoothDevice.class.getMethod("createBond");
            result = (Boolean) createBondMethod.invoke(device);
        } catch (Exception e) {
            e.getStackTrace();
        }
        return result;
    }

    /**
     * 强制配对，并附有回调实时跟踪配对结果。
     *
     * @param device
     * @param listener
     * @return
     */
    @SuppressLint("MissingPermission")
    boolean pairDeviceWithoutUserConsent(final BluetoothDevice device, final DeviceParingStateListener listener) {
        listener.onParingSessionBegin();
        boolean result = false;
        //1，反射启动配对
        try {
            Method createBondMethod = BluetoothDevice.class.getMethod("createBond");
            result = (Boolean) createBondMethod.invoke(device);
        } catch (Exception e) {
            e.getStackTrace();
            listener.onError(e.getMessage());
            listener.onParingSessionEnd(device.getBondState());
        }
        //2，新建一个子线程实时检测配对结果
        new Thread(new Runnable() {
            @Override
            public void run() {
                @SuppressLint("MissingPermission")
                int preState = device.getBondState();
                while (true) {
                    try {
                        //3，每隔200毫秒检查一次
                        Thread.sleep(200);
                        @SuppressLint("MissingPermission")
                        int currentState = device.getBondState();
                        //4，返回最新状态
                        listener.onDevicePairingStateChanged(device, preState, currentState);
                        //5，如果配对结果返回成功/失败，结束流程
                        if (preState == BluetoothDevice.BOND_BONDING && currentState == BluetoothDevice.BOND_BONDED
                                || preState == BluetoothDevice.BOND_BONDING && currentState == BluetoothDevice.BOND_NONE
                                || currentState == BluetoothDevice.BOND_BONDED) {
                            listener.onParingSessionEnd(currentState);
                            break;
                        }
                        preState = currentState;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        listener.onError(e.getMessage());
                        listener.onParingSessionEnd(device.getBondState());
                        break;
                    }
                }
            }
        }).start();
        return result;
    }

    boolean removePairedDeviceWithoutUserConsent(BluetoothDevice device) {
        showLog("removePairedDeviceWithoutUserConsent");
        boolean result = false;
        try {
            Method removeBondMethod = BluetoothDevice.class.getMethod("removeBond");
            result = (Boolean) removeBondMethod.invoke(device);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @SuppressLint("MissingPermission")
    ArrayList<BluetoothDevice> getBondedDevices() {
        ArrayList<BluetoothDevice> list = new ArrayList<>();
        Set<BluetoothDevice> devices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        Iterator<BluetoothDevice> iterator = devices.iterator();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }

    String getBondStateDescription(int state) {
        String desc = "UNKNOWN_BOND_STATE";
        switch (state) {
            case BluetoothDevice.BOND_NONE:
                desc = "BOND_NONE";
                break;
            case BluetoothDevice.BOND_BONDED:
                desc = "BOND_BONDED";
                break;
            case BluetoothDevice.BOND_BONDING:
                desc = "BOND_BONDING";
                break;
        }
        return desc;
    }

    String getBtEnableStateDescription(int btEnableSateCode) {
        String desc = "UNKNOWN_ENABLE_STATE";
        switch (btEnableSateCode) {
            case BluetoothAdapter.STATE_OFF:
                desc = "STATE_OFF";
                break;
            case BluetoothAdapter.STATE_ON:
                desc = "STATE_ON";
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
                desc = "STATE_TURNING_ON";
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                desc = "STATE_TURNING_OFF";
                break;
        }
        return desc;
    }

    String getBtDeviceConnectionStateDescription(int state) {
        String desc="UNKNOWN_CONNECTION_STATE";
        switch (state){
            case BluetoothProfile.STATE_CONNECTED:
                desc="STATE_CONNECTED";
                break;
            case BluetoothProfile.STATE_CONNECTING:
                desc="STATE_CONNECTING";
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                desc="STATE_DISCONNECTED";
                break;
            case BluetoothProfile.STATE_DISCONNECTING:
                desc="STATE_DISCONNECTING";
                break;
        }
        return desc;
    }
}
