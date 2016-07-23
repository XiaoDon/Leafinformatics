package flaremars.com.somethingdemo.utils.network;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * Created by FlareMars on 2015/10/21.
 */
public class NetworkHandler extends Handler {
    private WeakReference<INetworkContext> context;

    public NetworkHandler(INetworkContext context) {
        this.context = new WeakReference<>(context);
    }

    private int MSG_WHAT = -1;

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        if (MSG_WHAT == -1) {
            Log.e("NetworkHandler","didn't set the msg_what~");
            return;
        }
        if (msg.what == MSG_WHAT) {
            this.context.get().invalidate(msg.arg1);
        }
    }

    public int getMSG_WHAT() {
        return MSG_WHAT;
    }

    public void setMSG_WHAT(int MSG_WHAT) {
        this.MSG_WHAT = MSG_WHAT;
    }
}
