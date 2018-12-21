package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.content.Context;
import android.content.pm.PackageManager;

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
    void stopScanBtDevices(TgiBleScanCallback callback){
        BluetoothAdapter.getDefaultAdapter().stopLeScan(callback);
        callback.onPostScan();
    }

    @SuppressLint("MissingPermission")
    public boolean pairDevice(BluetoothDevice device) {
        return device.createBond();
    }


    public BluetoothDevice getDeviceByAddress(String deviceAddress) {
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
    }
}
