package tgi.com.librarybtmanager;

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
class TgiBtManagerLogUtils {
    private static boolean IS_DEBUG = false;

    private TgiBtManagerLogUtils() {
    }

    static void setIsDebug(boolean isDebug) {
        IS_DEBUG = isDebug;
    }

    static void showLog(String msg) {
        if(IS_DEBUG){
            StackTraceElement element = new Throwable().getStackTrace()[1];
            StringBuilder sb=new StringBuilder();
            String className=element.getClassName().replace("tgi.com.librarybtmanager.","").trim();
            sb.append(className)
                    .append("->")
                    .append(element.getMethodName())
                    .append("->Line:")
                    .append(element.getLineNumber())
                    .append("->")
                    .append(msg);
            Log.e("TgiBtLib",sb.toString());
        }
    }

}
