package com.twis.travelpasscnchecker;

import android.app.Application;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.socket.client.IO;
import io.socket.client.Socket;


public class ServerConnection extends Application {
    private Socket mSocket;
    public String ip;

    public Socket getSocket() {
        try {
            //online connection----------
            //mSocket = IO.socket("http://207.148.66.52:8080");
            mSocket = IO.socket("http://"+ ip +":8080");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return mSocket;
    }

//    public Socket getSocket(String ip) {
//        try {
//            //Local connection----------
//            mSocket = IO.socket("http://"+ ip +":8080");
//        } catch (URISyntaxException e) {
//            throw new RuntimeException(e);
//        }
//        return mSocket;
//    }

    public String getDate(){
        return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date().getTime());
    }
}
