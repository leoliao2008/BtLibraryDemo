package tgi.com.btlibrarydemo;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import tgi.com.librarybtmanager.BtDeviceConnectListener;
import tgi.com.librarybtmanager.TgiBleManager;
import tgi.com.librarybtmanager.TgiReadCharCallback;
import tgi.com.librarybtmanager.TgiToggleNotificationCallback;
import tgi.com.librarybtmanager.TgiWriteCharCallback;

public class ConnectDeviceActivity extends BaseActionBarActivity {
    private static final String BT_DEVICE_ADDRESS="BT_DEVICE_ADDRESS";
    private ListView mListView;
    private ArrayList<String> mLogs =new ArrayList<>();
    private ArrayAdapter<String> mAdapter;
    private Handler mHandler;
    private Semaphore mSemaphore=new Semaphore(1);
    private volatile boolean isNotificationOn=false;

    public static void start(Context context,String btDeviceAddr) {
        Intent starter = new Intent(context, ConnectDeviceActivity.class);
        starter.putExtra(BT_DEVICE_ADDRESS,btDeviceAddr);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_device);
        mListView=findViewById(R.id.activity_connect_device_list_view);
        mHandler=new Handler();
        mAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                mLogs
        ){
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(mLogs.get(position));
                return view;
            }
        };
        mListView.setAdapter(mAdapter);

        connectDevice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectDevice();
    }

    private void disconnectDevice() {
        TgiBleManager.getInstance().disConnectDevice();
    }

    private void connectDevice() {
        String address = getIntent().getStringExtra(BT_DEVICE_ADDRESS);
        showLog("onConnectSessionBegins");
        mLogs.clear();
        mAdapter.notifyDataSetChanged();
        TgiBleManager.getInstance().connectDevice(address,new BtDeviceConnectListener(){

            @Override
            public void onConnectSuccess(final BluetoothGatt gatt) {
                super.onConnectSuccess(gatt);
                final long timeMillis = System.currentTimeMillis();
                showLog("xxxxx,start:"+ timeMillis);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long timeMillis1 = System.currentTimeMillis();
                        showLog("xxxxx,stop:"+timeMillis1);
                        showLog("duration: "+(timeMillis1-timeMillis));
                        showLog("onConnect Success device:"+gatt.getDevice().getAddress());
                        TgiBleManager.getInstance().toggleNotification(
                                Constants.MASTER_SERVICE_UUID,
                                Constants.STATUS_CHAR_UUID,
                                Constants.STATUS_DESCRIPTOR_UUID,
                                true,
                                new TgiToggleNotificationCallback(){
                                    @Override
                                    public void onToggleNotificationSuccess(BluetoothGattDescriptor descriptor) {
                                        super.onToggleNotificationSuccess(descriptor);
                                        showLog("onToggleNotificationSuccess");
                                        isNotificationOn=true;
                                    }

                                    @Override
                                    public void onError(String errorMsg) {
                                        super.onError(errorMsg);
                                        showLog("onError:"+errorMsg);
                                    }

                                    @Override
                                    public void onCharChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                                        super.onCharChanged(gatt, characteristic);
                                        byte[] value = characteristic.getValue();
                                        String temp=convertToHexString(value);
                                        showLog("onCharChanged: "+temp);
                                    }
                                }
                        );
                    }
                });

            }

        });
    }

    private String convertToHexString(byte[] value) {
        StringBuilder sb=new StringBuilder();
        for(byte temp:value){
            sb.append("0x");
            String s = Integer.toHexString(temp);
            if(s.length()<2){
                sb.append("0");
            }
            sb.append(s).append(" ");
        }
        return sb.toString();
    }

    private synchronized void showLog(final String msg){
        if(mSemaphore.tryAcquire()){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.e(getClass().getSimpleName(),msg);
                    mLogs.add(msg);
                    if(mLogs.size()>50){
                        mLogs.remove(0);
                    }
                    mAdapter.notifyDataSetChanged();
                    mListView.smoothScrollToPosition(Integer.MAX_VALUE);
                    mSemaphore.release();
                }
            });
        }


    }

    public void toggleNotification(View view) {
        final boolean isToTurnOn=!isNotificationOn;
        TgiBleManager.getInstance().toggleNotification(
                Constants.MASTER_SERVICE_UUID,
                Constants.STATUS_CHAR_UUID,
                Constants.STATUS_DESCRIPTOR_UUID,
                isToTurnOn,
                new TgiToggleNotificationCallback(){
                    @Override
                    public void onToggleNotificationSuccess(BluetoothGattDescriptor descriptor) {
                        super.onToggleNotificationSuccess(descriptor);
                        isNotificationOn=isToTurnOn;
                        showLog(isNotificationOn?"通知重新打开了":"通知关闭了");
                    }

                    @Override
                    public void onCharChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                        super.onCharChanged(gatt, characteristic);
                        byte[] value = characteristic.getValue();
                        String temp=convertToHexString(value);
                        showLog("onCharChanged: "+temp);
                    }

                    @Override
                    public void onError(String errorMsg) {
                        super.onError(errorMsg);
                        showLog(errorMsg);
                    }
                }
        );
    }

    public void write(View view) {
        TgiBleManager.getInstance().writeCharacteristic(
                new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20},
                Constants.MASTER_SERVICE_UUID,
                Constants.FUNCTION_CHAR_UUID,
                new TgiWriteCharCallback(){
                    @Override
                    public void onWriteSuccess(BluetoothGattCharacteristic characteristic) {
                        super.onWriteSuccess(characteristic);
                        showLog("char被写入了："+convertToHexString(characteristic.getValue()));
                    }

                    @Override
                    public void onWriteFailed(String errorMsg) {
                        super.onWriteFailed(errorMsg);
                        showLog("char 写入失败:"+errorMsg);
                    }
                }
        );
    }

    public void read(View view) {
        TgiBleManager.getInstance().readCharacteristic(
                Constants.MASTER_SERVICE_UUID,
                Constants.FUNCTION_CHAR_UUID,
                new TgiReadCharCallback(){
                    @Override
                    public void onCharRead(BluetoothGattCharacteristic btChar, byte[] value) {
                        super.onCharRead(btChar, value);
                        showLog("char被读取了："+convertToHexString(value));
                    }

                    @Override
                    public void onError(String errorMsg) {
                        super.onError(errorMsg);
                        showLog("onError:"+errorMsg);
                    }

                }
        );

    }
}
