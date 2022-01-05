package com.twis.travelpasscnchecker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {
    ServerConnection app;
    private Socket socket;
    CodeScannerView scannerView;
    private ViewGroup mainLayout;
    private static final int MY_PERMISSION_REQUEST_CAMERA = 0;
    boolean showD = false;
    private final String TAG = "MainActivity";
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private ProgressDialog dialogLoading, dialogDisplay;
    private CodeScanner mCodeScanner;
    private String qrcode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        editor = prefs.edit();

        dialogDisplay = new ProgressDialog(this);
        dialogDisplay.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialogDisplay.setCancelable(false);

        app = (ServerConnection) getApplicationContext();
        mainLayout = (ViewGroup) findViewById(R.id.main_layout);
        scannerView = findViewById(R.id.scanner_view);

        dialogLoading = ProgressDialog.show(this, "",
                "Processing. Please wait...", true);
        dialogLoading.setCancelable(false);
        dialogLoading(false);

        //online connection
//        editor.putString("serverip", "207.148.66.52");
//        editor.apply();

        //offline connection
        //connectToServerDialog();

        app.ip = prefs.getString("serverip", "");
        socket = app.getSocket();
        socket.on(Socket.EVENT_CONNECT, onConnect);
        socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        socket.on(Socket.EVENT_RECONNECT, onReconnect);
        socket.on("onGetData", onGetData);
        socket.connect();
    }

    public void connectToServerDialog(){
        dialogDisplay.dismiss();
        showD = true;
        Toast.makeText(MainActivity.this, "Can't connect to server!", Toast.LENGTH_SHORT).show();
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final EditText edittext = new EditText(getApplicationContext());
        alert.setMessage("Enter IP Address");
        alert.setTitle("Connection");
        alert.setCancelable(false);
        edittext.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        if(!prefs.getString("serverip", "").equals(null)){
            edittext.setText(prefs.getString("serverip", ""));
        }

        alert.setView(edittext);

        alert.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                editor.putString("serverip", edittext.getText().toString());
                editor.apply();
                showD = false;
                app.ip = prefs.getString("serverip", "");
                socket = app.getSocket();
                socket.on(Socket.EVENT_CONNECT, onConnect);
                socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
                socket.on(Socket.EVENT_RECONNECT, onReconnect);
                socket.on("onGetData", onGetData);
                showDialog("Connection", "Connecting...");
                socket.connect();
            }
        });
        alert.show();
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Snackbar.make(mainLayout, "Camera access is required to display the camera preview.",
                    Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                            Manifest.permission.CAMERA
                    }, MY_PERMISSION_REQUEST_CAMERA);
                }
            }).show();
        }else {
            Snackbar.make(mainLayout, "Permission is not available.",
                    Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.CAMERA
            }, MY_PERMISSION_REQUEST_CAMERA);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MY_PERMISSION_REQUEST_CAMERA) {
            return;
        }

        if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(mainLayout, "Permission was granted.", Snackbar.LENGTH_SHORT).show();
          //  if(extras.getString("scanC").equals("ex")){
          //  }else{
                initScanQR();
           // }
        } else {
            Snackbar.make(mainLayout, "Permission request was denied.", Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    private void initScanQR(){
        mCodeScanner = new CodeScanner(this, scannerView);
        scannerView.setVisibility(View.VISIBLE);
        mCodeScanner.setDecodeCallback(result -> runOnUiThread(() -> insertBarcode(result.getText())));
        scannerView.setOnClickListener(view -> mCodeScanner.startPreview());
    }

    private void insertBarcode(String barCode){
        if(!barCode.contains("null")){
            vibrateOn();
            JSONObject codeData = new JSONObject();
            try {
                qrcode = barCode;
                codeData.put("id_tp", barCode);
                socket.emit("qrcode_Data", codeData);
                dialogLoading(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }else{
            //vStat.setBackgroundColor(getResources().getColor(R.color.error));
            Snackbar.make(mainLayout, "null Scanned! - " + barCode, Snackbar.LENGTH_SHORT).show();
            //tvStat.setText("Error\nPlease scan again!");
        }
    }

    private void vibrateOn(){
        Vibrator vibrate = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrate != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrate.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //deprecated in API 26
                vibrate.vibrate(200);
            }
        }
    }

    private Emitter.Listener onGetData = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
                Log.d(TAG, "onGetData");
                runOnUiThread(() -> {
                            try {
                                dialogLoading(false);
                                JSONObject data  = (JSONObject) args[0];
                                if(data.getBoolean("isSuccess")){
                                    editor.putString("personId", "1");
                                    editor.putString("id", data.getString("id"));
                                    editor.putString("qr", data.getString("qr"));
                                    editor.putString("origin", data.getString("origin"));
                                    editor.putString("destination", data.getString("destination"));
                                    editor.putString("vehicle", data.getString("vehicle"));
                                    editor.putString("plate_no", data.getString("plate_no"));
                                    editor.putString("booked_date", data.getString("booked_date"));
                                    Log.d(TAG, data.getString("attachment1"));
                                    Log.d(TAG, data.getString("attachment2"));
                                    String atch1 = data.getString("attachment1");

                                    if(atch1.equals("no_image1") || atch1 == null){
                                        editor.putString("attachment1", "no_data");
                                        Log.d(TAG, "no data1");
                                    }else{
                                        editor.putString("attachment1", data.getString("attachment1"));
                                    }
                                    String atch2 = data.getString("attachment2");
                                    if(atch2.equals("no_image2") || atch2 == null){
                                        editor.putString("attachment2", "no_data");
                                        Log.d(TAG, "no data2");
                                    }else{
                                        editor.putString("attachment2", data.getString("attachment2"));
                                    }
//                                    editor.putString("attachment1", data.getString("attachment1"));
//                                    editor.putString("attachment2", data.getString("attachment2"));
                                    editor.putString("purpose", data.getString("purpose"));
                                    editor.putString("name", data.getString("name"));
                                    editor.apply();

                                    Intent intent = new Intent(getApplicationContext(), InformationActivity.class);
                                    startActivity(intent);
                                    finish();
                                }else{
                                    AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
                                    builder1.setMessage("Travel Pass is Evaluated!");
                                    builder1.setCancelable(false);

                                    builder1.setPositiveButton(
                                            "Ok",
                                            (dialog, id) -> {
                                                dialog.cancel();
                                                if(mCodeScanner != null){
                                                    mCodeScanner.startPreview();
                                                }
                                            });
                                    AlertDialog alert11 = builder1.create();
                                    alert11.show();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                });
        }
    };

    private void showDialog(String t, String m) {
        if(dialogDisplay != null){
            dialogDisplay.dismiss();
        }
        TextView tittle = new TextView(this);
        TextView msg = new TextView(this);
        tittle.setText(t);
        tittle.setTextColor(Color.BLACK);
        tittle.setTextSize(20);
        msg.setText(m);
        msg.setTextColor(Color.BLACK);
        msg.setTextSize(50);

        dialogDisplay.setCustomTitle(tittle);
        dialogDisplay.setMessage(msg.getText());

        dialogDisplay.show();
    }

    private void dialogLoading(boolean sc) {
        if(dialogLoading != null){
            if (sc) {
                dialogLoading.show();
            } else {
                dialogLoading.cancel();
            }
        }
    }

    private Emitter.Listener onConnectError = args -> runOnUiThread(() -> {
        System.out.println("Not Connected");
    });

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            runOnUiThread(() -> {
                System.out.println("Connected");
                dialogDisplay.dismiss();
                requestCameraPermission();
            });
        }
    };

    private Emitter.Listener onReconnect = args -> runOnUiThread(() -> {
        System.out.println("Re Connect");
    });

    @Override
    protected void onResume() {
        super.onResume();
        if(mCodeScanner != null){
                mCodeScanner.startPreview();
        }
    }

    @Override
    protected void onPause() {
        if(mCodeScanner != null){
                mCodeScanner.releaseResources();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if(socket != null){
            socket.off(Socket.EVENT_CONNECT, onConnect);
            socket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
            socket.off(Socket.EVENT_RECONNECT, onReconnect);
            socket.off("onGetData", onGetData);
            socket.disconnect();
        }
        super.onDestroy();
    }
}