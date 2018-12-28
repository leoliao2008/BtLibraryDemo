package tgi.com.librarybtmanager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

/**
 * Author: Administrator
 * Date: 2018/12/21
 * Project: BtLibraryDemo
 * Description:
 */
public class TgiBleScanCallback implements BluetoothAdapter.LeScanCallback {

    public void onPreScan(){

    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

    }

    public void onPostScan(){

    }

    public void onError(String errorMsg) {

    }
}
