package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;

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
    void startScanBtDevices(final TgiBleScanCallback callback, Handler handler) {
        callback.onPreScan();
        BluetoothAdapter.getDefaultAdapter().startLeScan(callback);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
               stopScanBtDevices(callback);
            }
        }, 5000);
    }

    @SuppressLint("MissingPermission")
    void stopScanBtDevices(TgiBleScanCallback callback){
        BluetoothAdapter.getDefaultAdapter().stopLeScan(callback);
        callback.onPostScan();
    }
}
