package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
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
    boolean isBtEnabled(BluetoothAdapter adapter) {
        return adapter.isEnabled();
    }


}
