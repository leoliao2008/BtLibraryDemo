package tgi.com.btlibrarydemo;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

import tgi.com.librarybtmanager.TgiBleManager;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 4/1/2019</i>
 * <p><b>Project:</b></p>
 * <i>BtLibraryDemo</i>
 * <p><b>Description:</b></p>
 */
public class PairedDevicesAdapter extends RecyclerView.Adapter<PairedDevicesAdapter.ViewHolder> {
    private ArrayList<BluetoothDevice> mList;
    private Context mContext;
    private ItemClickListener mItemClickListener;
    private ArrayList<BluetoothDevice> mBondedDevices;

    public PairedDevicesAdapter(ArrayList<BluetoothDevice> list, Context context) {
        mList = list;
        mContext = context;
        mBondedDevices = TgiBleManager.getInstance().getBondedDevices();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_paired_device_adapter, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, final int i) {
        BluetoothDevice device = mList.get(i);
        StringBuilder sb=new StringBuilder();
        sb.append(TextUtils.isEmpty(device.getName())?"Unknown Device":device.getName());
        sb.append("\r\n");
        sb.append(device.getAddress());
        viewHolder.mTvDeviceName.setText(sb.toString());
        viewHolder.mRootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mItemClickListener!=null){
                    mItemClickListener.onItemClick(i);
                }
            }
        });

        if(mBondedDevices.contains(device)){
            viewHolder.mIvChecked.setVisibility(View.VISIBLE);
        }else {
            viewHolder.mIvChecked.setVisibility(View.GONE);
        }
    }

    public void updateBondedListAndNotifyDataSetChanged(){
        mBondedDevices = TgiBleManager.getInstance().getBondedDevices();
        super.notifyDataSetChanged();
    }


    @Override
    public int getItemCount() {
        return mList.size();
    }

    public void setOnItemClickListener(ItemClickListener listener) {
        mItemClickListener=listener;
    }

    interface ItemClickListener{
        void onItemClick(int position);
    }

    class ViewHolder extends RecyclerView.ViewHolder{
        TextView mTvDeviceName;
        View mRootView;
        ImageView mIvChecked;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            mRootView=itemView;
            mTvDeviceName=itemView.findViewById(R.id.item_paired_device_adapter_tv_device);
            mIvChecked=itemView.findViewById(R.id.item_paired_device_adapter_iv_checked);
        }
    }
}
