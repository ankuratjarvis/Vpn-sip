/* $Id: MainActivity.java 5022 2015-03-25 03:41:21Z nanang $ */
/*
 * Copyright (C) 2013 Teluu Inc. (http://www.teluu.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.example.myapplication;

import static com.example.myapplication.common.UtilsKt.netCheck;

import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.myapplication.common.Constants;
import com.example.myapplication.model.Server;
import com.example.myapplication.utils.MSG_TYPE;
import com.example.myapplication.utils.MyAppObserver;
import com.example.myapplication.utils.MyBuddy;
import com.example.myapplication.utils.MyCall;
import com.example.myapplication.utils.MyAccount;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.AuthCredInfo;
import org.pjsip.pjsua2.AuthCredInfoVector;
import org.pjsip.pjsua2.BuddyConfig;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.StringVector;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;

import de.blinkt.openvpn.OpenVpnApi;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.OpenVPNThread;
import de.blinkt.openvpn.core.VpnStatus;

public class SipActivity extends Activity
        implements Handler.Callback, MyAppObserver {
    private static final String TAG = "SipActivity";
    public static SipApp app = null;

    @Override
    public void notifyBuddyState(MyBuddy buddy) {

    }
    private NotificationService myService;
    private boolean isServiceBound = false;
    public static MyCall currentCall = null;
    public static MyAccount account = null;
    public static AccountConfig accCfg = null;
    public static MyBroadcastReceiver receiver = null;
    public static IntentFilter intentFilter = null;

    private ListView buddyListView;
    private SimpleAdapter buddyListAdapter;
    private int buddyListSelectedIdx = -1;
    ArrayList<Map<String, String>> buddyList;
    private String lastRegStatus = "";
    Boolean vpnStart = false;
    private final Handler handler = new Handler(this);
    public static SipActivity instance = null;


    TextView durationTv;
    TextView logTV;
    TextView lastPacketReceiveTv;
    TextView byteInTv;
    TextView byteOutTv;
    Button vpnBtn, startSipStack, stopSipStack, endCall;
    RelativeLayout activeCallView;
    Server server;
    TextView logTextView;
    Intent foregroundServiceIntent;
     /*   public class MSG_TYPE {
            public final static int INCOMING_CALL = 1;
            public final static int CALL_STATE = 2;
            public final static int REG_STATE = 3;
            public final static int BUDDY_STATE = 4;
            public final static int CALL_MEDIA_STATE = 5;
            public final static int CHANGE_NETWORK = 6;
        }*/

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            stopService(foregroundServiceIntent);
            String data = intent.getStringExtra(Constants.INCOMING_CALL);
            Log.d(TAG, "On New Intent---->" + data);

        }
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        private String conn_name = "";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (isNetworkChange(context))
                notifyChangeNetwork();
        }

        private boolean isNetworkChange(Context context) {
            boolean network_changed = false;
            ConnectivityManager connectivity_mgr =
                    ((ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE));

            NetworkInfo net_info = connectivity_mgr.getActiveNetworkInfo();
            if (net_info != null && net_info.isConnectedOrConnecting() &&
                    !conn_name.equalsIgnoreCase("")) {
                String new_con = net_info.getExtraInfo();
                if (new_con != null && !new_con.equalsIgnoreCase(conn_name))
                    network_changed = true;

                conn_name = (new_con == null) ? "" : new_con;
            } else {
                if (conn_name.equalsIgnoreCase(""))
                    conn_name = net_info.getExtraInfo();
            }
            return network_changed;
        }
    }

    private HashMap<String, String> putData(String uri, String status) {
        HashMap<String, String> item = new HashMap<String, String>();
        item.put("uri", uri);
        item.put("status", status);
        return item;
    }

    private void showCallActivity() {
        foregroundServiceIntent = new Intent(this, NotificationService.class);
        CallOpParam prm = new CallOpParam();
        prm.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
        try {
            SipActivity.currentCall.answer(prm);
            activeCallView.setVisibility(View.VISIBLE);
            logTextView.setVisibility(View.GONE);
            Bundle b = new Bundle();

           /* foregroundServiceIntent.putExtra(Constants.INCOMING_CALL,"pick_up");
                startService(foregroundServiceIntent);*/

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        /*Intent intent = new Intent(this, SipActivecall.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);*/
    }

    public static SipActivity getInstance() {
        return instance;
    }

    public void log(String msg) {
        runOnUiThread(() -> {
            logTextView.append(msg);

        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.myapplication.R.layout.activity_main_pjsip);
        instance = this;
        vpnBtn = findViewById(R.id.vpn_Btn);
        durationTv = findViewById(R.id.duration_Tv);
        logTV = findViewById(R.id.log_TV);
        lastPacketReceiveTv = findViewById(R.id.lastPacketReceive_Tv);
        byteInTv = findViewById(R.id.byteIn_Tv);
        byteOutTv = findViewById(R.id.byteOut_Tv);
        startSipStack = findViewById(R.id.startSipStack_btn);
        stopSipStack = findViewById(R.id.stopSIP);
        logTextView = findViewById(R.id.log_Text_View);
        ImageButton accDialog = findViewById(R.id.buttonEditBuddy);
        activeCallView = findViewById(R.id.active_call_Container);
        endCall = findViewById(R.id.endCallButton);
        logTextView.setMovementMethod(new ScrollingMovementMethod());

       /* if (app == null) {
            app = new SipApp();
            // Wait for GDB to init, for native debugging only
            if (false &&
                    (getApplicationInfo().flags &
                            ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
            }

            app.init(this, getFilesDir().getAbsolutePath());
        }*/

    /*    if (app.accList.size() == 0) {
            accCfg = new AccountConfig();
            accCfg.setIdUri("sip:localhost");
            accCfg.getNatConfig().setIceEnabled(true);
            accCfg.getVideoConfig().setAutoTransmitOutgoing(true);
            accCfg.getVideoConfig().setAutoShowIncoming(true);
            account = app.addAcc(accCfg);
        } else {
            account = app.accList.get(0);
            accCfg = account.cfg;
        }

        buddyList = new ArrayList<Map<String, String>>();
        for (int i = 0; i < account.buddyList.size(); i++) {
            buddyList.add(putData(account.buddyList.get(i).cfg.getUri(),
                    account.buddyList.get(i).getStatusText()));
        }

        String[] from = {"uri", "status"};
        int[] to = {android.R.id.text1, android.R.id.text2};
        buddyListAdapter = new SimpleAdapter(
                this, buddyList,
                android.R.layout.simple_list_item_2,
                from, to);

        buddyListView = (ListView) findViewById(R.id.listViewBuddy);
        ;
        buddyListView.setAdapter(buddyListAdapter);
        buddyListView.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent,
                                            final View view,
                                            int position, long id) {
                        view.setSelected(true);
                        buddyListSelectedIdx = position;
                    }
                }
        );
        if (receiver == null) {
            receiver = new MyBroadcastReceiver();
            intentFilter = new IntentFilter(
                    ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(receiver, intentFilter);
        }*/

        accDialog.setOnClickListener(v -> {
            dlgAccountSetting();
        });
        vpnBtn.setOnClickListener(v -> {
            if (vpnStart) {
                confirmDisconnect();
            } else {
                prepareVpn();
            }
        });
        startSipStack.setOnClickListener(v -> {
            dlgAccountSetting();
        });
        stopSipStack.setOnClickListener(v -> {
            Message message = Message.obtain(handler, 0);
            message.sendToTarget();

        });
        endCall.setOnClickListener(v -> {
            CallOpParam prm = new CallOpParam();
            prm.setStatusCode(pjsip_status_code.PJSIP_SC_DECLINE);
            try {
                SipActivity.currentCall.hangup(prm);
                activeCallView.setVisibility(View.GONE);
                logTextView.setVisibility(View.VISIBLE);

            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        });
    }
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {


            NotificationService.LocalBinder binder = (NotificationService.LocalBinder) iBinder;
            myService = binder.getInstance();
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isServiceBound = false;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar
        // if it is present.
        getMenuInflater().inflate(com.example.myapplication.R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case com.example.myapplication.R.id.action_acc_config:
                dlgAccountSetting();
                break;

            case com.example.myapplication.R.id.action_quit:
                Message m = Message.obtain(handler, 0);
                m.sendToTarget();
                break;

            default:
                break;
        }

        return true;
    }

    @Override
    public boolean handleMessage(Message m) {
        if (m.what == 0) {

            app.deinit();
//            finish();
//            Runtime.getRuntime().gc();
//            android.os.Process.killProcess(android.os.Process.myPid());

        } else if (m.what == MSG_TYPE.CALL_STATE) {

            CallInfo ci = (CallInfo) m.obj;

            if (currentCall == null || ci == null || ci.getId() != currentCall.getId()) {
                System.out.println("Call state event received, but call info is invalid");
                return true;
            }

            /* Forward the call info to CallActivity */
            if (SipActivecall.handler_ != null) {
                Message m2 = Message.obtain(SipActivecall.handler_, MSG_TYPE.CALL_STATE, ci);
                m2.sendToTarget();
            }

            if (ci.getState() == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                currentCall.delete();
                currentCall = null;
            }

        } else if (m.what == MSG_TYPE.CALL_MEDIA_STATE) {

            /* Forward the message to CallActivity */
            if (SipActivecall.handler_ != null) {
                Message m2 = Message.obtain(SipActivecall.handler_,
                        MSG_TYPE.CALL_MEDIA_STATE,
                        null);
                m2.sendToTarget();
            }

        } else if (m.what == MSG_TYPE.BUDDY_STATE) {

            MyBuddy buddy = (MyBuddy) m.obj;
            int idx = account.buddyList.indexOf(buddy);

            /* Update buddy status text, if buddy is valid and
             * the buddy lists in account and UI are sync-ed.
             */
            if (idx >= 0 && account.buddyList.size() == buddyList.size()) {
                buddyList.get(idx).put("status", buddy.getStatusText());
                buddyListAdapter.notifyDataSetChanged();
                // TODO: selection color/mark is gone after this,
                //       dont know how to return it back.
                //buddyListView.setSelection(buddyListSelectedIdx);
                //buddyListView.performItemClick(buddyListView,
                //                                   buddyListSelectedIdx,
                //                                   buddyListView.
                //                  getItemIdAtPosition(buddyListSelectedIdx));

                /* Return back Call activity */
                notifyCallState(currentCall);
            }

        } else if (m.what == MSG_TYPE.REG_STATE) {

            String msg_str = (String) m.obj;
            lastRegStatus = msg_str;

        } else if (m.what == MSG_TYPE.INCOMING_CALL) {

            /* Incoming call */
            final MyCall call = (MyCall) m.obj;
            CallOpParam prm = new CallOpParam();

            /* Only one casuper.onCreate()ll at anytime */
            if (currentCall != null) {
                /*
                prm.setStatusCode(pjsip_status_code.PJSIP_SC_BUSY_HERE);
                try {
                call.hangup(prm);
                } catch (Exception e) {}
                */
                // TODO: set status code
                call.delete();
                return true;
            }

            /* Answer with ringing */
            prm.setStatusCode(pjsip_status_code.PJSIP_SC_RINGING);
            try {
                call.answer(prm);
            } catch (Exception e) {
            }

            currentCall = call;
            showCallActivity();

        } else if (m.what == MSG_TYPE.CHANGE_NETWORK) {
            app.handleNetworkChange();
        } else {

            /* Message not handled */
            return false;

        }

        return true;
    }

    void dlgAccountSetting(String id,String proxy,String username,String password){
        foregroundServiceIntent = new Intent(this,NotificationService.class);
        Bundle bundle = new Bundle();
        bundle.putString("domain_id",id);
        bundle.putString("proxy",proxy);
        bundle.putString("username",username);
        bundle.putString("password",password);
        foregroundServiceIntent.putExtra("data_bundle",bundle);
        startService(foregroundServiceIntent);

        bindService(foregroundServiceIntent,serviceConnection,Context.BIND_AUTO_CREATE);
    }

    public void dlgAccountSetting() {
        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(com.example.myapplication.R.layout.dlg_account_config, null);

        if (lastRegStatus.length() != 0) {
            TextView tvInfo = (TextView) view.findViewById(com.example.myapplication.R.id.textViewInfo);
            tvInfo.setText("Last status: " + lastRegStatus);
        }

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setView(view);
        adb.setTitle("Account Settings");

        final EditText etId = (EditText) view.findViewById(com.example.myapplication.R.id.editTextId);
        final EditText etReg = (EditText) view.findViewById(com.example.myapplication.R.id.editTextRegistrar);
        final EditText etProxy = (EditText) view.findViewById(com.example.myapplication.R.id.editTextProxy);
        final EditText etUser = (EditText) view.findViewById(com.example.myapplication.R.id.editTextUsername);
        final EditText etPass = (EditText) view.findViewById(com.example.myapplication.R.id.editTextPassword);

//        etId.setText(accCfg.getIdUri());
        etId.setText(Constants.VPN_DOMAIN_ID);
//        etReg.setText(accCfg.getRegConfig().getRegistrarUri());
//        StringVector proxies = accCfg.getSipConfig().getProxies();
//        if (proxies.size() > 0)
//            etProxy.setText(proxies.get(0));
//        else
//            etProxy.setText("");
        etProxy.setText(Constants.VPN_AGENT_DOMAIN);
        etReg.setText(Constants.VPN_AGENT_DOMAIN);
//        AuthCredInfoVector creds = accCfg.getSipConfig().getAuthCreds();
//        if (creds.size() > 0) {
//            etUser.setText(creds.get(0).getUsername());
//            etPass.setText(creds.get(0).getData());
//        } else {
//            etUser.setText("");
//            etPass.setText("");
//        }
        etUser.setText(Constants.VPN_AGENT_USERNAME);
        etPass.setText(Constants.VPN_AGENT_PASSWORD);
        adb.setCancelable(false);
        adb.setPositiveButton("OK",
                (dialog, id) -> {
                    String acc_id = etId.getText().toString();
                    String registrar = etReg.getText().toString();
                    String proxy = etProxy.getText().toString();
                    String username = etUser.getText().toString();
                    String password = etPass.getText().toString();
            dlgAccountSetting(acc_id,proxy,username,password);
                    /*try {
                        if (!SipApp.ep.libIsThreadRegistered()) {
                            Log.d(TAG, "Registering new thread");
                            app.init(SipActivity.this, getFilesDir().getAbsolutePath());
                        }
                        String acc_id = etId.getText().toString();
                        String registrar = etReg.getText().toString();
                        String proxy = etProxy.getText().toString();
                        String username = etUser.getText().toString();
                        String password = etPass.getText().toString();

                        accCfg.setIdUri(acc_id);
                        accCfg.getRegConfig().setRegistrarUri(registrar);
                        AuthCredInfoVector creds1 = accCfg.getSipConfig().
                                getAuthCreds();
                        creds1.clear();
                        if (username.length() != 0) {
                            creds1.add(new AuthCredInfo("Digest", "*", username, 0,
                                    password));
                        }
                        StringVector proxies1 = accCfg.getSipConfig().getProxies();
                        proxies1.clear();
                        if (proxy.length() != 0) {
                            proxies1.add(proxy);
                        }

                        *//* Enable ICE *//*
                        accCfg.getNatConfig().setIceEnabled(true);

                        *//* Finally *//*
                        lastRegStatus = "";

                        account.modify(accCfg);

                    } catch (Exception e) {
                        Log.d(TAG, "Exception occured while starting sIP Server" + e.getMessage());
                    }*/
                }
        );
        adb.setNegativeButton("Cancel",
                (dialog, id) -> dialog.cancel()
        );

        AlertDialog ad = adb.create();
        ad.show();
    }


    public void makeCall(View view) {
        if (buddyListSelectedIdx == -1)
            return;

        /* Only one call at anytime */
        if (currentCall != null) {
            return;
        }

        HashMap<String, String> item = (HashMap<String, String>) buddyListView.
                getItemAtPosition(buddyListSelectedIdx);
        String buddy_uri = item.get("uri");

        MyCall call = new MyCall(account, -1);
        CallOpParam prm = new CallOpParam(true);

        try {
            call.makeCall(buddy_uri, prm);
        } catch (Exception e) {
            call.delete();
            return;
        }

        currentCall = call;
        showCallActivity();
    }

    private void dlgAddEditBuddy(BuddyConfig initial) {
        final BuddyConfig cfg = new BuddyConfig();
        final BuddyConfig old_cfg = initial;
        final boolean is_add = initial == null;

        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(com.example.myapplication.R.layout.dlg_add_buddy, null);

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setView(view);

        final EditText etUri = (EditText) view.findViewById(com.example.myapplication.R.id.editTextUri);
        final CheckBox cbSubs = (CheckBox) view.findViewById(com.example.myapplication.R.id.checkBoxSubscribe);

        if (is_add) {
            adb.setTitle("Add Buddy");
        } else {
            adb.setTitle("Edit Buddy");
            etUri.setText(initial.getUri());
            cbSubs.setChecked(initial.getSubscribe());
        }

        adb.setCancelable(false);
        adb.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        cfg.setUri(etUri.getText().toString());
                        cfg.setSubscribe(cbSubs.isChecked());

                        if (is_add) {
                            account.addBuddy(cfg);
                            buddyList.add(putData(cfg.getUri(), ""));
                            buddyListAdapter.notifyDataSetChanged();
                            buddyListSelectedIdx = -1;
                        } else {
                            if (!old_cfg.getUri().equals(cfg.getUri())) {
                                account.delBuddy(buddyListSelectedIdx);
                                account.addBuddy(cfg);
                                buddyList.remove(buddyListSelectedIdx);
                                buddyList.add(putData(cfg.getUri(), ""));
                                buddyListAdapter.notifyDataSetChanged();
                                buddyListSelectedIdx = -1;
                            } else if (old_cfg.getSubscribe() !=
                                    cfg.getSubscribe()) {
                                MyBuddy bud = account.buddyList.get(
                                        buddyListSelectedIdx);
                                try {
                                    bud.subscribePresence(cfg.getSubscribe());
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                }
        );
        adb.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                }
        );

        AlertDialog ad = adb.create();
        ad.show();
    }

    public void addBuddy(View view) {
        dlgAddEditBuddy(null);
    }

    public void editBuddy(View view) {
        if (buddyListSelectedIdx == -1)
            return;

        BuddyConfig old_cfg = account.buddyList.get(buddyListSelectedIdx).cfg;
        dlgAddEditBuddy(old_cfg);
    }

    public void delBuddy(View view) {
        if (buddyListSelectedIdx == -1)
            return;

        final HashMap<String, String> item = (HashMap<String, String>)
                buddyListView.getItemAtPosition(buddyListSelectedIdx);
        String buddy_uri = item.get("uri");

        DialogInterface.OnClickListener ocl =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                account.delBuddy(buddyListSelectedIdx);
                                buddyList.remove(item);
                                buddyListAdapter.notifyDataSetChanged();
                                buddyListSelectedIdx = -1;
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                break;
                        }
                    }
                };

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle(buddy_uri);
        adb.setMessage("\nDelete this buddy?\n");
        adb.setPositiveButton("Yes", ocl);
        adb.setNegativeButton("No", ocl);
        adb.show();
    }


    /*
     * === MyAppObserver ===
     *
     * As we cannot do UI from worker thread, the callbacks mostly just send
     * a message to UI/main thread.
     */

    public void notifyIncomingCall(MyCall call) {
        Message m = Message.obtain(handler, MSG_TYPE.INCOMING_CALL, call);
        m.sendToTarget();
    }

    public void notifyRegState(int code, String reason,
                               long expiration) {
        String msg_str = "";
        if (expiration == 0)
            msg_str += "Unregistration";
        else
            msg_str += "Registration";

        if (code / 100 == 2)
            msg_str += " successful";
        else
            msg_str += " failed: " + reason;

        Message m = Message.obtain(handler, MSG_TYPE.REG_STATE, msg_str);
        m.sendToTarget();
    }

    public void notifyCallState(MyCall call) {
        if (currentCall == null || call.getId() != currentCall.getId())
            return;

        CallInfo ci = null;
        try {
            ci = call.getInfo();
        } catch (Exception e) {
        }

        if (ci == null)
            return;

        Message m = Message.obtain(handler, MSG_TYPE.CALL_STATE, ci);
        m.sendToTarget();
    }

    public void notifyCallMediaState(MyCall call) {
        Message m = Message.obtain(handler, MSG_TYPE.CALL_MEDIA_STATE, null);
        m.sendToTarget();
    }


    public void notifyChangeNetwork() {
        Message m = Message.obtain(handler, MSG_TYPE.CHANGE_NETWORK, null);
        m.sendToTarget();
    }

    /* === end of MyAppObserver ==== */

    void connectVpn() {


    }

    private void isServiceRunning() {
        setStatus(OpenVPNService.getStatus());
    }

    void setStatus(String connectionState) {
        Log.d(TAG, "VPN Status ---> $connectionState");
        if (connectionState != null) switch (connectionState) {

            case "DISCONNECTED": {
                status("connect");
                vpnStart = false;
                OpenVPNService.setDefaultStatus();
                logTV.setText("");
            }
            break;

            case "CONNECTED": {
                vpnStart = true; // it will use after restart this activity
                status("connected");
                logTV.setText("");

            }
            break;

            case "WAIT":
                logTV.setText("Connection Status waiting for server connection!!");

                break;
            case "AUTH":
                logTV.setText("Connection Status server authenticating!!");
                break;

            case "RECONNECTING": {
                status("connecting");
                logTV.setText("Connection Status Reconnecting...");
            }
            break;


            case "NONETWORK":
                logTV.setText("Connection Status No network connection");
                break;

        }
    }

    private void startVpn() {
        server = new Server("bangalore.ovpn", Constants.VPN_USERNAME, Constants.VPN_PASSWORD);
        isServiceRunning();

        VpnStatus.initLogCache(getCacheDir());
        try {
            // .ovpn file
            InputStream conf = getAssets().open(server.getOvpn());
            Log.d("Sip Fragment", "File name----> ${server.ovpn}");
            InputStreamReader isr = new InputStreamReader(conf);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder config = new StringBuilder();
            String line;
            while (true) {
                line = br.readLine();
                if (line == null) break;
                config.append(line).append("\n");

            }
            br.readLine();
            OpenVpnApi.startVpn(
                    this, config.toString(), "India", server.getUsername(), server.getPassword()
            );


            // Update log
//            logTV.setText("Connecting...");
            vpnStart = true;
        } catch (IOException e) {
            Log.d(TAG, "Exception---> " + e.getMessage());
        } catch (RemoteException e) {
            Log.d(TAG, "Exception--->" + e.getMessage());
        }
    }

    private void status(String status) {
        switch (status) {
            case "connect": {
                vpnBtn.setText(getString(R.string.connect));

            }
            break;

            case "connecting": {
                vpnBtn.setText(getString(R.string.connecting));
            }
            break;

            case "connected": {
                vpnBtn.setText(getString(R.string.disconnect));

            }
            break;

            case "tryDifferentServer": {
                vpnBtn.setText(getString(R.string.try_different_server));


            }
            break;

            case "loading": {
                vpnBtn.setText(getString(R.string.loading_server));

            }
            break;

            case "invalidDevice": {
                vpnBtn.setText(getString(R.string.invalid_device));

            }
            break;

            case "authenticationCheck": {
                vpnBtn.setText(getString(R.string.authentication_checking));

            }
            break;
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {


        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                setStatus(intent.getStringExtra("state"));
            } catch (java.lang.Exception e) {
                Log.d(TAG, e.getMessage());
            }
            try {
                String duration = intent.getStringExtra("duration");
                String lastPacketReceive = intent.getStringExtra("lastPacketReceive");
                String byteIn = intent.getStringExtra("byteIn");
                String byteOut = intent.getStringExtra("byteOut");
                if (duration == null) duration = "00:00:00";
                if (lastPacketReceive == null) lastPacketReceive = "0";
                if (byteIn == null) byteIn = " ";
                if (byteOut == null) byteOut = " ";
                updateConnectionStatus(duration, lastPacketReceive, byteIn, byteOut);
            } catch (java.lang.Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }
    };

    void updateConnectionStatus(
            String duration, String lastPacketReceive, String byteIn, String byteOut
    ) {
        durationTv.setText("Duration: " + duration);
        lastPacketReceiveTv.setText("Packet Received: " + lastPacketReceive + " second ago");
        byteInTv.setText("Bytes In: " + byteIn);
        byteOutTv.setText("Bytes Out:" + byteOut);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {

            //Permission granted, start the VPN
            startVpn();
        } else {
            showToast("Permission Deny !! ");
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(broadcastReceiver, new IntentFilter("connectionState"));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        super.onPause();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
//        stopVpn();

    }


    private void prepareVpn() {
        if (!vpnStart) {
            if (getInternetStatus()) {

                // Checking permission for network monitor
                Intent intent = VpnService.prepare(this);
                if (intent != null) {

                    startActivityForResult(intent, 1);
                } else {
                    Log.d(TAG, "Starting VPN--->");

                    startVpn();
                } //have already permission

                // Update confection status
                status("connecting");
            } else {

                // No internet connection available
                showToast("you have no internet connection !!");
            }
        } else if (stopVpn()) {

            // VPN is stopped, show a Toast message.
            showToast("Disconnect Successfully");
        }
    }

    private Boolean getInternetStatus() {
        return netCheck(this);
    }

    private Boolean stopVpn() {
        try {
//        stopSip();
            OpenVPNThread.stop();
            status("connect");
            vpnStart = false;
            return true;
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
        return false;
    }

    void checkPermissions() {
        if (Build.VERSION.SDK_INT >= 23 && this.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            //we need RECORD_AUDIO permission before to make/receive any call
            ActivityCompat.requestPermissions(
                    SipActivity.this,
                    new String[]{(android.Manifest.permission.RECORD_AUDIO)},
                    555
            );
            /*
                better permission management: https://developer.android.com/training/permissions/requesting
                some example code:
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA))
                        {
                            Toast tst = Toast.makeText(ctx, "Audio record permission required", Toast.LENGTH_LONG);
                            tst.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                            tst.show();
                            Handler handlerRating = new Handler();
                            handlerRating.postDelayed(new Runnable()
                            {
                                public void run()
                                {
                                        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 555);
                                }
                            }, 1000);
                        }else
                        {
                            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 555);
                        }

                        //put this function outside:
                        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
                        {
                            switch (requestCode) {
                                case 555:{
                                        // If request is cancelled, the result arrays are empty.
                                        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                                        {
                                            //continue from here
                                        }else
                                        {
                                            //display failure
                                        }
                                }
                            }
                        }

            */
            /* startActivity<MainActivity> {

             }*/
        }
    }

    private void confirmDisconnect() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.connection_close_confirm));
        builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                stopVpn();
            }
        });
        builder.setNegativeButton("no", (dialog, which) -> {

        });


        // Create the AlertDialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

}






