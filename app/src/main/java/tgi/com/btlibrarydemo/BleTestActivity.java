package tgi.com.btlibrarydemo;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;

import tgi.com.librarybtmanager.TgiBleInitCallBack;
import tgi.com.librarybtmanager.TgiBleManager;

public class BleTestActivity extends BaseActionBarActivity {
    private TgiBleManager mTgiBleManager;

    public static void start(Context context) {
        Intent starter = new Intent(context, BleTestActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_test_actvity);
        mTgiBleManager = TgiBleManager.getInstance();
        mTgiBleManager.setDebugMode(true);

        mTgiBleManager.startBtService(this, new TgiBleInitCallBack() {
            @Override
            public void onError(String msg) {
                super.onError(msg);
                Log.e("onError", msg);
            }

            @Override
            public void onInitSuccess() {
                super.onInitSuccess();
                Log.e(getClass().getSimpleName(),"onInitSuccess");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mTgiBleManager.onRequestBtPermissionsResult(this, requestCode,
                    permissions, grantResults);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTgiBleManager.stopBtService(this);
    }

    public void toNext(View view) {
        ScanDeviceActivity.start(this);

    }
}
