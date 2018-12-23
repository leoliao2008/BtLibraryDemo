package tgi.com.librarybtmanager;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Author: Administrator
 * Date: 2018/12/23
 * Project: BtLibraryDemo
 * Description:
 */
public class TgiBtChar implements Parcelable {
    private String mDeviceAddress;
    private String mCharUUID;
    private String mServiceUUID;
    private byte[] data;

    public TgiBtChar() {
    }

    protected TgiBtChar(Parcel in) {
        mDeviceAddress = in.readString();
        mCharUUID = in.readString();
        mServiceUUID = in.readString();
        data = in.createByteArray();
    }

    public static final Creator<TgiBtChar> CREATOR = new Creator<TgiBtChar>() {
        @Override
        public TgiBtChar createFromParcel(Parcel in) {
            return new TgiBtChar(in);
        }

        @Override
        public TgiBtChar[] newArray(int size) {
            return new TgiBtChar[size];
        }
    };

    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    public void setDeviceAddress(String deviceAddress) {
        mDeviceAddress = deviceAddress;
    }

    public String getCharUUID() {
        return mCharUUID;
    }

    public void setCharUUID(String charUUID) {
        mCharUUID = charUUID;
    }

    public String getServiceUUID() {
        return mServiceUUID;
    }

    public void setServiceUUID(String serviceUUID) {
        mServiceUUID = serviceUUID;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data.clone();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDeviceAddress);
        dest.writeString(mCharUUID);
        dest.writeString(mServiceUUID);
        dest.writeByteArray(data);
    }
}
