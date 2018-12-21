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
public class BtNotConnectedYetException extends Exception {
    public BtNotConnectedYetException(){
        super("Bluetooth device not connected yet. Need to connect first.");
    }
}
