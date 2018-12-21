package tgi.com.librarybtmanager;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 21/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>BtLibraryDemo</i>
 * <p><b>Description:</b></p>
 */
public class BtNotEnabledException extends Exception {
    public BtNotEnabledException() {
        super("Bluetooth device is not enabled, you need to enable it first.");
    }
}
