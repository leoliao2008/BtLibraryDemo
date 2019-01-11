package tgi.com.btlibrarydemo.utils;

import android.util.Log;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 11/1/2019</i>
 * <p><b>Project:</b></p>
 * <i>BtLibraryDemo</i>
 * <p><b>Description:</b></p>
 */
public class LogUtils {
    private LogUtils(){}

    public static void showLog(String msg){
        StackTraceElement element = new Throwable().getStackTrace()[1];
        String className = element.getClassName().replace("tgi.com.btlibrarydemo", "").trim();
        StringBuilder sb=new StringBuilder();
        sb.append(className)
                .append("->")
                .append(element.getMethodName())
                .append("->")
                .append(element.getMethodName())
                .append("-> Line:")
                .append(element.getLineNumber())
                .append("->")
                .append(msg);
        Log.e("BtDemo",sb.toString());
    }
}
