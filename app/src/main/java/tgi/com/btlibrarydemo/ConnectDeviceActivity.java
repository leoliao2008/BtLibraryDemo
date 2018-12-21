package tgi.com.btlibrarydemo;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
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
import java.util.List;

import tgi.com.librarybtmanager.BtDeviceConnectStateListener;
import tgi.com.librarybtmanager.BtNotBondedException;
import tgi.com.librarybtmanager.BtNotEnabledException;
import tgi.com.librarybtmanager.TgiBleManager;

public class ConnectDeviceActivity extends AppCompatActivity {
    private static final String BT_DEVICE_ADDRESS="BT_DEVICE_ADDRESS";
    private ListView mListView;
    private ArrayList<BluetoothGattService> mServices=new ArrayList<>();
    private ArrayAdapter<BluetoothGattService> mAdapter;

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
        mAdapter = new ArrayAdapter<BluetoothGattService>(
                this,
                android.R.layout.simple_list_item_1,
                mServices
        ){
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                BluetoothGattService service = mServices.get(position);
                StringBuilder sb=new StringBuilder();
                sb.append("Service:\r\n");
                sb.append(service.getUuid().toString());
                view.setText(sb.toString());
                return view;
            }
        };
        mListView.setAdapter(mAdapter);

        updateServicesList();

    }

    private void updateServicesList() {
        String address = getIntent().getStringExtra(BT_DEVICE_ADDRESS);
        try {
            TgiBleManager.getInstance().connectDevice(address,new BtDeviceConnectStateListener(){
                @Override
                public void onConnectSessionBegins() {
                    super.onConnectSessionBegins();
                    showLog("onConnectSessionBegins");
                    mServices.clear();
                    mAdapter.notifyDataSetChanged();
                }

                @Override
                public void onConnect(BluetoothGatt gatt, BluetoothDevice device) {
                    super.onConnect(gatt, device);
                    showLog("onConnect Success device:"+device.getAddress());
                    List<BluetoothGattService> services = gatt.getServices();
                    mServices.addAll(services);
                    mAdapter.notifyDataSetChanged();
                }

                @Override
                public void onConnectFail(String errorMsg) {
                    super.onConnectFail(errorMsg);
                    showLog("onConnectFail");
                }

                @Override
                public void onConnectSessionEnds() {
                    super.onConnectSessionEnds();
                    showLog("onConnectSessionEnds");
                }
            });
        } catch (BtNotBondedException e) {
            e.printStackTrace();
            showLog("BtNotBondedException");
        } catch (BtNotEnabledException e) {
            e.printStackTrace();
            showLog("BtNotBondedException");
        }
    }

    private void showLog(String msg){
        Log.e(getClass().getSimpleName(),msg);
    }
}
