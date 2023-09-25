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
import android.nfc.Tag;
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

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.myapplication.common.Constants;
import com.example.myapplication.common.StorageImpl;
import com.example.myapplication.model.Server;
import com.example.myapplication.utils.MSG_TYPE;
import com.example.myapplication.utils.MyAppObserver;
import com.example.myapplication.utils.MyBuddy;
import com.example.myapplication.utils.MyCall;
import com.example.myapplication.utils.MyAccount;
import com.example.myapplication.utils.ServiceCallback;

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
        implements ServiceCallback {
    private static final String TAG = "SipActivity";
    public static MyCall currentCall=null;
    public static MyAccount account=null;

    private NotificationService myService;
    private boolean isServiceBound = false;

    private String lastRegStatus = "";
    Boolean vpnStart = false;
    public static SipActivity instance = null;


    TextView durationTv, lastPacketReceiveTv;
    TextView logTV;
    TextView byteInTv, byteOutTv;
    Button vpnBtn, startSipStack, stopSipStack, endCall;
    RelativeLayout activeCallView;
    Server server;
    TextView logTextView;
    Intent foregroundServiceIntent;
    StorageImpl storage;
    Boolean isServiceRunning = false;
    Boolean isCallActive = false;


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
       /* if (intent != null) {
            stopService(foregroundServiceIntent);
            String data = intent.getStringExtra(Constants.INCOMING_CALL);
            Log.d(TAG, "On New Intent---->" + data);

        }*/
    }

    @Override
    public void onAnswer(Boolean isActive) {
        Log.d(TAG, "OnAnswered Triggered");
        if (isActive) {
            isCallActive = true;
            activeCallView.setVisibility(View.VISIBLE);
            logTextView.setVisibility(View.GONE);
        } else {
            isCallActive = false;
            activeCallView.setVisibility(View.GONE);
            logTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void stopService() {
        myService.stopSip();
        isServiceRunning = false;
        isCallActive = false;
        logTextView.setText(" ");
        stopService(foregroundServiceIntent);

    }


    private void showCallActivity() {
        foregroundServiceIntent = new Intent(this, NotificationService.class);
        CallOpParam prm = new CallOpParam();
        prm.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
        try {
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
            logTextView.append(msg + "\n\n");

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
        storage = new StorageImpl(this);

        foregroundServiceIntent = new Intent(this, NotificationService.class);
        if(Boolean.TRUE.equals(storage.readData(Constants.SERVICE))){
            isServiceRunning = true;
        }
        if (Boolean.TRUE.equals(storage.readData(Constants.IS_CALL_ACTIVE))) {
            bindService(foregroundServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

            isCallActive = true;
            storage.clearData();
            onAnswer(true);

        } else {
            isCallActive = false;
            onAnswer(false);

        }


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
                logTextView.setText("");
                dlgAccountSetting();


        });
        stopSipStack.setOnClickListener(v -> {

            if (isServiceRunning) {
                myService.stopSip();
            }else{
                showToast("Service Not Active Yet...!");

            }

        });
        endCall.setOnClickListener(v -> {
            if (myService != null) {
                myService.hangupCall();
            }

        });
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {


            NotificationService.LocalBinder binder = (NotificationService.LocalBinder) iBinder;
            myService = binder.getInstance();
            Log.d(TAG, "Invoking Service Callback");
            if (myService != null) {
                myService.registerCallback(SipActivity.this);
                showToast("Service connected");
                Log.d(TAG, "Service Callback Registered");

            }
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isServiceBound = false;
        }
    };


    void dlgAccountSetting(String id, String proxy, String username, String password) {
        foregroundServiceIntent = new Intent(this, NotificationService.class);
        Bundle bundle = new Bundle();
        bundle.putString("domain_id", id);
        bundle.putString("proxy", proxy);
        bundle.putString("username", username);
        bundle.putString("password", password);
        foregroundServiceIntent.putExtra("data_bundle", bundle);
        startService(foregroundServiceIntent);
        isServiceRunning = true;
        Log.d(TAG,"Initial Service Started");
        bindService(foregroundServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
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
                    dlgAccountSetting(acc_id, proxy, username, password);
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

    private void isServiceRunning() {
        setStatus(OpenVPNService.getStatus());
    }

    void setStatus(String connectionState) {
//        Log.d(TAG, "VPN Status ---> $connectionState");
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

        if (isCallActive ) {
            storage.saveData(Constants.IS_CALL_ACTIVE, true);

        } else {
            storage.saveData(Constants.IS_CALL_ACTIVE, false);

        }
        if (isServiceRunning){
            storage.saveData(Constants.SERVICE,true);
            unbindService(serviceConnection);

        }
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
            stopService();
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






