package com.twis.travelpasscnchecker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class LoginActivity extends AppCompatActivity {
    ServerConnection app;
    private Socket socket;
    private final String TAG = "LoginActivity";
    private Button btnLogin;
    private TextInputEditText txtUname, txtPassword;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private ProgressDialog dialogLoading, dialogDisplay;
    private TextView tvStat;
    boolean showD = false;

    private final int PERMS_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        hasPermissions();
        dialogDisplay = new ProgressDialog(this);
        dialogDisplay.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialogDisplay.setCancelable(false);

        tvStat = findViewById(R.id.tvStat);
        tvStat.setVisibility(View.GONE);
        txtUname = findViewById(R.id.uname);
        txtPassword = findViewById(R.id.password);

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        editor = prefs.edit();
        app = (ServerConnection) getApplicationContext();

        //online connection
        editor.putString("serverip", "139.180.217.207");
        editor.apply();

        app.ip = prefs.getString("serverip", "");
        socket = app.getSocket();
        socket.on(Socket.EVENT_CONNECT, onConnect);
        socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        socket.on(Socket.EVENT_RECONNECT, onReconnect);
        socket.on("login_onUser", login_onUser);
        showDialog("Connection", "Connecting...");
        socket.connect();

        //local connection
//        if(prefs.getString("serverip", "") == null || prefs.getString("serverip", "").equals("")){
//                connectToServerDialog();
//        }else{
//            app.ip = prefs.getString("serverip", "");
//            socket = app.getSocket();
//            socket.on(Socket.EVENT_CONNECT, onConnect);
//            socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
//            socket.on(Socket.EVENT_RECONNECT, onReconnect);
//            socket.on("login_onLeaderUser", login_onLeaderUser);
//            showDialog("Connection", "Connecting...");
//            socket.connect();
//        }

        //local connection
        //connectToServerDialog();

        dialogLoading = ProgressDialog.show(this, "",
                "Logging in. Please wait...", true);
        dialogLoading.setCancelable(false);
        dialogLoading(false);

        btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(ClickListener);
//        btnRegister = findViewById(R.id.btnRegister);
//        btnRegister.setOnClickListener(ClickListener);
    }

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

    public View.OnClickListener ClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnLogin:
                    try {
                        dialogLoading(true);
                        JSONObject loginData = new JSONObject()
                                .put("uname", txtUname.getText().toString())
                                .put("password", txtPassword.getText().toString());
                        socket.emit("login_user", loginData);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    };

    private Emitter.Listener login_onUser = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            try{
                Log.d(TAG, "login_onUser");
                JSONObject jsonObject = (JSONObject) args[0];
                if(jsonObject.getBoolean("isSuccess")){
                    Log.d(TAG, "isSuccessLogin");
                    runOnUiThread(() -> {
                        dialogLoading(false);
                        try {
                                editor.putString("personId", jsonObject.getString("id"));
                                editor.putString("displayName", jsonObject.getString("name"));
                                editor.putString("role", jsonObject.getString("role"));
                                editor.apply();
                                Log.d(TAG, "Data Save" + jsonObject.getString("id"));

                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                startActivity(intent);
                                finish();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    });
                }else{
                    Log.d(TAG, "isNotSuccessLogin");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStat.setVisibility(View.VISIBLE);
                            tvStat.setText("* Wrong username/password!");
                            dialogLoading(false);
                        }
                    });
                }
            }catch (JSONException e){
                e.printStackTrace();
            }
        }
    };

    private Emitter.Listener onConnectError = args -> runOnUiThread(() -> {
        System.out.println("Not Connected");
        dialogLoading(false);
    });

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            runOnUiThread(() -> {
                System.out.println("Connected");
                dialogDisplay.dismiss();
                dialogLoading(false);
            });
        }
    };

    private Emitter.Listener onReconnect = args -> runOnUiThread(() -> {
        System.out.println("Re Connect");
    });

    public void connectToServerDialog(){
        dialogDisplay.dismiss();
        showD = true;
        Toast.makeText(LoginActivity.this, "Can't connect to server!", Toast.LENGTH_SHORT).show();
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
                socket.on("login_onUser", login_onUser);
                showDialog("Connection", "Connecting...");
                socket.connect();
            }
        });
        alert.show();
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

    @Override
    protected void onDestroy() {
        if(socket != null){
            socket.off(Socket.EVENT_CONNECT, onConnect);
            socket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
            socket.off(Socket.EVENT_RECONNECT, onReconnect);
            socket.off("login_onLeaderUser", login_onUser);
            socket.disconnect();
        }
        super.onDestroy();
    }

    private void hasPermissions(){
        int res;
        String[] permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};

        for (String perms : permissions){
            res = checkCallingOrSelfPermission(perms);
            if (!(res == PackageManager.PERMISSION_GRANTED)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                    requestPermissions(permissions,PERMS_REQUEST_CODE);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissions.length == 0) {
            return;
        }
        boolean allPermissionsGranted = true;
        if (grantResults.length > 0) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
        }

        if (!allPermissionsGranted) {
            boolean somePermissionsForeverDenied = false;
            for (String permission : permissions) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    Log.e("denied", permission);
                    somePermissionsForeverDenied = true;
                } else {
                    if (ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                        Log.e("allowed", permission);

                    } else {
                        Log.e("set to never ask again", permission);
                        somePermissionsForeverDenied = true;
                    }
                }
            }

            if (somePermissionsForeverDenied) {
                final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setTitle("Permissions Required")
                        .setMessage("You have forcefully denied some of the required permissions " +
                                "for this action. Please open settings, go to permissions and allow them.")
                        .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", getPackageName(), null));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setCancelable(false)
                        .create()
                        .show();
            }
        } else {
            switch (requestCode) {
                case PERMS_REQUEST_CODE:
                    /*for (int res : grantResults){
                        allowed = allowed && (res == PackageManager.PERMISSION_GRANTED);
                    }*/
                    //loadDatabase();
                    break;
                default:
                    //allowed = false;
                    break;
            }
        }
    }
}