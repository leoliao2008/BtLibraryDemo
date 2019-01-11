package tgi.com.librarybtmanager;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 11/1/2019</i>
 * <p><b>Project:</b></p>
 * <i>MC21</i>
 * <p><b>Description:</b></p>
 */
class TgiBluetoothAdapterSwitcher {
    static AtomicBoolean IS_CONFIGURING = new AtomicBoolean(false);
    private Thread mEnableThread;

    private TgiBluetoothAdapterSwitcher() {
    }


    /**
     * 启动本地蓝牙模块
     *
     * @param listener
     * @param isRetryOnFailure
     */
    @SuppressLint("MissingPermission")
    void enableBt(final TgiBtAdapterEnablingStateListener listener, final boolean isRetryOnFailure) {
        //先检查蓝牙模块是否早已启动，如果是，直接返回成功。
        if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            IS_CONFIGURING.set(false);
            listener.onSessionEnd(BluetoothAdapter.STATE_ON);
            return;
        }
        //If this call returns false then there was an immediate problem that will prevent
        //the adapter from being turned on :such as Airplane mode, or the adapter is already turned on.
        if (IS_CONFIGURING.compareAndSet(false, true)) {
            final boolean enable = BluetoothAdapter.getDefaultAdapter().enable();
            //启动蓝牙模块，开始监听最新状态
            if (enable) {
                mEnableThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long startTime = SystemClock.currentThreadTimeMillis();
                        int preSate = BluetoothAdapter.getDefaultAdapter().getState();
                        while (true) {
                            int currentState = BluetoothAdapter.getDefaultAdapter().getState();
                            //更新最新状态
                            listener.onStateUpdate(preSate, currentState);
                            //当最终结果为打开或关闭时，停止线程，返回最终结果。
                            if (preSate == BluetoothAdapter.STATE_TURNING_ON && currentState == BluetoothAdapter.STATE_ON
                                    || preSate == BluetoothAdapter.STATE_TURNING_ON && currentState == BluetoothAdapter.STATE_OFF) {
                                IS_CONFIGURING.compareAndSet(true, false);
                                listener.onSessionEnd(currentState);
                                break;
                            }
                            preSate = currentState;

                            long currentTime = SystemClock.currentThreadTimeMillis();
                            //10秒后自动终止线程
                            if (currentTime - startTime > 10000) {
                                IS_CONFIGURING.compareAndSet(true, false);
                                if (isRetryOnFailure) {
                                    enableBt(listener, true);
                                } else {
                                    listener.onSessionEnd(currentState);
                                }
                                break;
                            }

                            //休眠500毫秒后再返回循环
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                IS_CONFIGURING.compareAndSet(true, false);
                                break;
                            }
                            //检查一下线程是否被中断了
                            if (Thread.interrupted()) {
                                IS_CONFIGURING.compareAndSet(true, false);
                                break;
                            }

                        }
                    }
                });
            } else {
                IS_CONFIGURING.compareAndSet(true, false);
                listener.onError("The Adapter fails to be turned on :such as Airplane mode, or the adapter is already turned on.");
            }
        } else {
            listener.onError("Bluetooth Adapter is currently operating state changes, current task is ignored.");
        }
    }

    void disableBt() {

    }
}
