/* $Id: MyApp.java 5361 2016-06-28 14:32:08Z nanang $ */
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


import android.util.Log;

import com.example.myapplication.utils.MyAccount;
import com.example.myapplication.utils.MyAccountConfig;
import com.example.myapplication.utils.MyAppObserver;
import com.example.myapplication.utils.MyBuddy;
import com.example.myapplication.utils.MyLogWriter;

import java.io.File;
import java.util.ArrayList;

import org.pjsip.pjsua2.*;

public class SipApp extends pjsua2 {
    public static Endpoint ep;
    public static MyAppObserver observer;
    public ArrayList<MyAccount> accList = new ArrayList<MyAccount>();

    private ArrayList<MyAccountConfig> accCfgs =
            new ArrayList<MyAccountConfig>();
    private EpConfig epConfig;
    private TransportConfig sipTpConfig;
    private String appDir;

    /* Maintain reference to log writer to avoid premature cleanup by GC */
    private MyLogWriter logWriter;

    private final String configName = "pjsua2.json";
    private final int SIP_PORT  = 6000;
    private final int LOG_LEVEL = 4;

    public void init(MyAppObserver obs, String app_dir)
    {
        init(obs, app_dir, false);
    }

    public void init(MyAppObserver obs, String app_dir,
                     boolean own_worker_thread)
    {
        observer = obs;
        appDir = app_dir;

        /* Create endpoint */
        try {
            ep = new Endpoint();

            epConfig = new EpConfig();
            sipTpConfig = new TransportConfig();
            ep.libCreate();
//            ep.libRegisterThread("sip");
        } catch (Exception e) {
            e.printStackTrace();
        }


        /* Load config */
        String configPath = appDir + "/" + configName;
        File f = new File(configPath);
       /* if (f.exists()) {
            loadConfig(configPath);
        } else {
            *//* Set 'default' values *//*
            sipTpConfig.setPort(SIP_PORT);
        }*/

        /* Override log level setting */
        epConfig.getLogConfig().setLevel(LOG_LEVEL);
        epConfig.getLogConfig().setConsoleLevel(LOG_LEVEL);

        /* Set log config. */
        LogConfig log_cfg = epConfig.getLogConfig();
        logWriter = new MyLogWriter();
        log_cfg.setWriter(logWriter);
        log_cfg.setDecor(log_cfg.getDecor() &
                ~(pj_log_decoration.PJ_LOG_HAS_CR |
                        pj_log_decoration.PJ_LOG_HAS_NEWLINE));

        /* Write log to file (just uncomment whenever needed) */
        //String log_path = android.os.Environment.getExternalStorageDirectory().toString();
        //log_cfg.setFilename(log_path + "/pjsip.log");

        /* Set ua config. */
        UaConfig ua_cfg = epConfig.getUaConfig();
        ua_cfg.setUserAgent("Pjsua2 Android " + ep.libVersion().getFull());

        /* STUN server. */
        //StringVector stun_servers = new StringVector();
        //stun_servers.add("stun.pjsip.org");
        //ua_cfg.setStunServer(stun_servers);

        /* No worker thread */
        if (own_worker_thread) {
            ua_cfg.setThreadCnt(0);
            ua_cfg.setMainThreadOnly(true);
        }

        /* Init endpoint */
        try {
            ep.libInit(epConfig);
        } catch (Exception e) {
            Log.d("SipActivity","Exception occurred while initializing library"+e.getMessage());
        }

        /* Create transports. */
        try {
            ep.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP,
                    sipTpConfig);
        } catch (Exception e) {
            System.out.println(e);
        }

        try {
            ep.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP,
                    sipTpConfig);
        } catch (Exception e) {
            System.out.println(e);
        }

        try {
            sipTpConfig.setPort(SIP_PORT+1);
            ep.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS,
                    sipTpConfig);
        } catch (Exception e) {
            System.out.println(e);
        }

        /* Set SIP port back to default for JSON saved config */
        sipTpConfig.setPort(SIP_PORT);

        /* Create accounts. */
        for (int i = 0; i < accCfgs.size(); i++) {
            MyAccountConfig my_cfg = accCfgs.get(i);

            /* Customize account config */
            my_cfg.accCfg.getNatConfig().setIceEnabled(true);
            my_cfg.accCfg.getVideoConfig().setAutoTransmitOutgoing(true);
            my_cfg.accCfg.getVideoConfig().setAutoShowIncoming(true);

            /* Enable SRTP optional mode and without requiring SIP TLS transport */
            my_cfg.accCfg.getMediaConfig().setSrtpUse(pjmedia_srtp_use.PJMEDIA_SRTP_OPTIONAL);
            my_cfg.accCfg.getMediaConfig().setSrtpSecureSignaling(0);

            MyAccount acc = addAcc(my_cfg.accCfg);
            if (acc == null)
                continue;

            /* Add Buddies */
            for (int j = 0; j < my_cfg.buddyCfgs.size(); j++) {
                BuddyConfig bud_cfg = my_cfg.buddyCfgs.get(j);
                acc.addBuddy(bud_cfg);
            }
        }

        /* Start. */
        try {
            ep.libStart();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public MyAccount addAcc(AccountConfig cfg)
    {
        MyAccount acc = new MyAccount(cfg);
        try {
            acc.create(cfg);
        } catch (Exception e) {
            System.out.println(e);
            acc = null;
            return null;
        }

        accList.add(acc);
        return acc;
    }

    public void delAcc(MyAccount acc)
    {
        accList.remove(acc);
    }

    private void loadConfig(String filename)
    {
        /*JsonDocument json = new JsonDocument();

        try {
            *//* Load file *//*
            json.loadFile(filename);
            ContainerNode root = json.getRootContainer();

            *//* Read endpoint config *//*
            epConfig.readObject(root);

            *//* Read transport config *//*
            ContainerNode tp_node = root.readContainer("SipTransport");
            sipTpConfig.readObject(tp_node);

            *//* Read account configs *//*
            accCfgs.clear();
            ContainerNode accs_node = root.readArray("accounts");
            while (accs_node.hasUnread()) {
                MyAccountConfig acc_cfg = new MyAccountConfig();
                acc_cfg.readObject(accs_node);
                accCfgs.add(acc_cfg);
            }
        } catch (Exception e) {
            System.out.println(e);
        }

        *//* Force delete json now, as I found that Java somehow destroys it
         * after lib has been destroyed and from non-registered thread.
         *//*
        json.delete();*/
    }

    private void buildAccConfigs()
    {
        /* Sync accCfgs from accList */
        accCfgs.clear();
        for (int i = 0; i < accList.size(); i++) {
            MyAccount acc = accList.get(i);
            MyAccountConfig my_acc_cfg = new MyAccountConfig();
            my_acc_cfg.accCfg = acc.cfg;

            my_acc_cfg.buddyCfgs.clear();
            for (int j = 0; j < acc.buddyList.size(); j++) {
                MyBuddy bud = acc.buddyList.get(j);
                my_acc_cfg.buddyCfgs.add(bud.cfg);
            }

            accCfgs.add(my_acc_cfg);
        }
    }

    private void saveConfig(String filename)
    {
       /* JsonDocument json = new JsonDocument();

        try {
            *//* Write endpoint config *//*
            json.writeObject(epConfig);

            *//* Write transport config *//*
            ContainerNode tp_node = json.writeNewContainer("SipTransport");
            sipTpConfig.writeObject(tp_node);

            *//* Write account configs *//*
            buildAccConfigs();
            ContainerNode accs_node = json.writeNewArray("accounts");
            for (int i = 0; i < accCfgs.size(); i++) {
                accCfgs.get(i).writeObject(accs_node);
            }

            *//* Save file *//*
            json.saveFile(filename);
        } catch (Exception e) {}

        *//* Force delete json now, as I found that Java somehow destroys it
         * after lib has been destroyed and from non-registered thread.
         *//*
        json.delete();*/
    }

    public void handleNetworkChange()
    {
        try{
            System.out.println("Network change detected");
            IpChangeParam changeParam = new IpChangeParam();
            ep.handleIpChange(changeParam);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void deinit()
    {
             /* Try force GC to avoid late destroy of PJ objects as they should be
         * deleted before lib is destroyed.
         */

        /* Shutdown pjsua. Note that Endpoint destructor will also invoke
         * libDestroy(), so this will be a test of double libDestroy().
         */

        try {
            if(ep!=null){
                ep.libDestroy();
                ep.delete();
                ep = null;
                Runtime.getRuntime().gc();
            }


        } catch (Exception e) {
            Log.d("SipActivity","Exception occurred while destroying PJSIP"+e.getMessage());
        }

        /* Force delete Endpoint here, to avoid deletion from a non-
         * registered thread (by GC?).
         */

    }
}
