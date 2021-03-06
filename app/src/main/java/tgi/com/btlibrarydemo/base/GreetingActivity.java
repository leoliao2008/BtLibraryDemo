package tgi.com.btlibrarydemo.base;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import tgi.com.btlibrarydemo.R;
import tgi.com.btlibrarydemo.activities.BaseActionBarActivity;

public class GreetingActivity extends BaseActionBarActivity {
    private GreetingActivity mThis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mThis=this;
        //permissions
//        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
            for(String p:permissions){
                int i = checkSelfPermission(p);
                if(i== PackageManager.PERMISSION_DENIED){
                    if(shouldShowRequestPermissionRationale(p)){
                        Toast.makeText(mThis,"Need permission to go on.",Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(
                            permissions,
                            123
                    );
                    break;
                }
            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        for(int i:grantResults){
            if(i==PackageManager.PERMISSION_DENIED){
                checkAndRequestPermissions();
                break;
            }
        }
    }

    public void toBleTestActivity(View view) {
        InitBtActivity.start(this);
    }
}
