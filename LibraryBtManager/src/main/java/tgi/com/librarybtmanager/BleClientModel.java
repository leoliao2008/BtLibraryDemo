package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

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
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
    }

    public boolean pairDeviceWithoutUserConsent(BluetoothDevice device) {
        boolean result = false;
        try {
            Method createBondMethod = BluetoothDevice.class.getMethod("createBond");
            result = (Boolean) createBondMethod.invoke(device);
        } catch (Exception e) {
            e.getStackTrace();
        }
        return result;
    }

    public boolean removePairedDeviceWithoutUserConsent(BluetoothDevice device) {
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
    public ArrayList<BluetoothDevice> getBondedDevices(){
        ArrayList<BluetoothDevice> list=new ArrayList<>();
        Set<BluetoothDevice> devices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        Iterator<BluetoothDevice> iterator = devices.iterator();
        while (iterator.hasNext()){
            list.add(iterator.next());
        }
        return list;
    }

    public String getBondStateDescription(int state) {
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

    public String getBtEnableStateDescription(int btEnableSateCode) {
        String desc = "UNKNOWN_ENABLE_STATE";
        switch (btEnableSateCode){
            case BluetoothAdapter.STATE_OFF:
                desc="STATE_OFF";
                break;
            case BluetoothAdapter.STATE_ON:
                desc="STATE_ON";
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
                desc="STATE_TURNING_ON";
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                desc="STATE_TURNING_OFF";
                break;
        }
        return desc;
    }
}
