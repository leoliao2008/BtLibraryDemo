package tgi.com.librarybtmanager;

import android.nfc.tech.IsoDep;
import android.util.Log;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 20/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>BtLibraryDemo</i>
 * <p><b>Description:</b></p>
 */
class LogUtils {
    private static boolean IS_DEBUG = false;

    private LogUtils() {
    }

    static void setIsDebug(boolean isDebug) {
        IS_DEBUG = isDebug;
    }

    static void showLog(String msg) {
        if(IS_DEBUG){
            Log.e("TGI BT MANAGER",msg);
        }
    }

}
