package tgi.com.librarybtmanager;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 20/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>BtLibraryDemo</i>
 * <p><b>Description:</b></p>
 */
class TgiBtPermissionsChecker {
    private static int requestCode;
    private static AlertDialog alertDialog;
    private static String[] permissions = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private TgiBtPermissionsChecker() {
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    static boolean checkBtPermissions(Activity activity) {
        boolean isGranted = true;
        for (String p : permissions) {
            int i = activity.checkSelfPermission(p);
            if (i == PackageManager.PERMISSION_DENIED) {
                isGranted = false;
                break;
            }
        }
        return isGranted;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    static void requestBtPermissions(Activity activity, int requestCode) {
        TgiBtPermissionsChecker.requestCode = requestCode;
        activity.requestPermissions(permissions, requestCode);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    static boolean onRequestBtPermissionsResult(
            final Activity activity,
            final int requestCode,
            String[] permissions,
            int[] grantResults,
            Runnable onGranted,
            final Runnable onDenied) {

        boolean isConsume = false;
        boolean isAllGranted = true;
        if (requestCode == TgiBtPermissionsChecker.requestCode) {
            isConsume = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    isAllGranted = false;
                    boolean shouldRationale = activity.shouldShowRequestPermissionRationale(permissions[i]);
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    if(shouldRationale){
                        StringBuilder sb = new StringBuilder();
                        sb.append("You need to grant the following permissions in order to run this app:\r\n");
                        for (String temp : permissions) {
                            sb.append(temp).append("\r\n");
                        }
                        sb.append("Press Agree to grant, press Deny to cancel.");
                        alertDialog = builder.setMessage(sb.toString())
                                .setPositiveButton("Agree", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        alertDialog.dismiss();
                                        requestBtPermissions(activity, requestCode);
                                    }
                                })
                                .setNegativeButton("Deny", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        alertDialog.dismiss();
                                        onDenied.run();

                                    }
                                })
                                .setCancelable(false)
                                .create();
                        alertDialog.show();
                    }else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("You have banned the permissions :\r\n");
                        for (String temp : permissions) {
                            sb.append(temp).append("\r\n");
                        }
                        sb.append("These permissions are vital for this app, press Setting to go to system setting and grant those permissions, " +
                                "press Quit to close this activity. ");
                        alertDialog=builder.setMessage(sb.toString())
                                .setPositiveButton("Setting", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        alertDialog.dismiss();
                                        activity.onBackPressed();
                                        goToAppSetting(activity);
                                    }
                                })
                                .setNegativeButton("Quit", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        alertDialog.dismiss();
                                        activity.onBackPressed();

                                    }
                                })
                                .setCancelable(false)
                                .create();
                        alertDialog.show();
                    }

                    break;
                }
            }
            if (isAllGranted) {
                onGranted.run();
            }
        }
        return isConsume;
    }

    //https://blog.csdn.net/luckrr/article/details/78211465
    private static void goToAppSetting(Activity activity) {
        Intent localIntent = new Intent();
        if (Build.VERSION.SDK_INT >= 9) {
            localIntent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
            localIntent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        } else if (Build.VERSION.SDK_INT <= 8) {
            localIntent.setAction(Intent.ACTION_VIEW);
            localIntent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails");
            localIntent.putExtra("com.android.settings.ApplicationPkgName", activity.getPackageName());
        }
        activity.startActivity(localIntent);
    }



}
