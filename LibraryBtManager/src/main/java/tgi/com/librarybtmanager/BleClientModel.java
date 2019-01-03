package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;

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
}
