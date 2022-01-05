package com.twis.travelpasscnchecker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class InformationActivity extends AppCompatActivity {
    ServerConnection app;
    private Socket socket;
    Button btnAccept, btnCancel, btnCamAttach1, btnCamAttach2;
    private TextInputEditText tname, tforigin, tfdestination, tfvehicle, tfplate_no, tfbooked_date, tfpurpose;
    private SharedPreferences prefs;
    private ImageView attach1, attach2;
    private static String TAG="InformationActivity";
    static final int REQUEST_IMAGE_CAPTURE = 1;
    private String attachment1 = "no_image1", attachment2 = "no_image2", filename, currentAttach;
    Uri uriSavedImage;
    File image;
    private ProgressDialog dialogLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_information);

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        app = (ServerConnection) getApplicationContext();

        tname = findViewById(R.id.tname);
        tforigin = findViewById(R.id.tforigin);
        tfdestination = findViewById(R.id.tfdestination);
        tfvehicle = findViewById(R.id.tfvehicle);
        tfplate_no = findViewById(R.id.tfplate_no);
        tfbooked_date = findViewById(R.id.tfbooked_date);
        tfpurpose = findViewById(R.id.tfpurpose);
        attach1 = findViewById(R.id.attach1);
        attach2 = findViewById(R.id.attach2);
        btnCamAttach1 = findViewById(R.id.btnCamAttach1);
        btnCamAttach2 = findViewById(R.id.btnCamAttach2);
        btnCamAttach1.setOnClickListener(ClickListener);
        btnCamAttach2.setOnClickListener(ClickListener);

        btnAccept = findViewById(R.id.btnAccept);
        btnAccept.setOnClickListener(ClickListener);
        btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(ClickListener);

        dialogLoading = ProgressDialog.show(this, "",
                "Processing. Please wait...", true);
        dialogLoading.setCancelable(false);
        dialogLoading(false);

        tname.setText(prefs.getString("name", ""));
        tforigin.setText(prefs.getString("origin", ""));
        tfdestination.setText(prefs.getString("destination", ""));
        tfvehicle.setText(prefs.getString("vehicle", ""));
        tfplate_no.setText(prefs.getString("plate_no", ""));
        tfbooked_date.setText(prefs.getString("booked_date", ""));
        tfpurpose.setText(prefs.getString("purpose", ""));
        attachment1 = prefs.getString("attachment1", "");
        attachment2 = prefs.getString("attachment2", "");
        if(prefs.getString("attachment1", "").equals("no_data")){
            btnCamAttach1.setVisibility(View.VISIBLE);
            attach1.setVisibility(View.GONE);
        }else{
            btnCamAttach1.setVisibility(View.GONE);
            attach1.setVisibility(View.VISIBLE);
            Picasso.get().load("http://" + prefs.getString("serverip", "") +"/storage/attachment1/" + prefs.getString("attachment1", "")).into(attach1);
        }
        if(prefs.getString("attachment2", "").equals("no_data")){
            btnCamAttach2.setVisibility(View.VISIBLE);
            attach2.setVisibility(View.GONE);
         }else{
            btnCamAttach2.setVisibility(View.GONE);
            attach2.setVisibility(View.VISIBLE);
            Picasso.get().load("http://" + prefs.getString("serverip", "") +"/storage/attachment2/" + prefs.getString("attachment2", "")).into(attach2);

        }

        app.ip = prefs.getString("serverip", "");
        socket = app.getSocket();
        socket.on(Socket.EVENT_CONNECT, onConnect);
        socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        socket.on(Socket.EVENT_RECONNECT, onReconnect);
        socket.on("onGetStat", onGetStat);
        socket.connect();
    }

    private Emitter.Listener onGetStat = args -> runOnUiThread(() -> {
        dialogLoading(false);
            AlertDialog.Builder builder1 = new AlertDialog.Builder(InformationActivity.this);
            builder1.setMessage("Travel Pass Accepted");
            builder1.setCancelable(false);
            builder1.setPositiveButton(
                    "Ok",
                    (dialog, id) -> {
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        startActivity(intent);
                        finish();
                        dialog.cancel();
                    });
            AlertDialog alert11 = builder1.create();
            alert11.show();
    });

    public View.OnClickListener ClickListener = v -> {
        switch (v.getId()) {
            case R.id.btnAccept:
                JSONObject codeData = new JSONObject();
                try {
                    codeData.put("personId", prefs.getString("personId", ""));
                    codeData.put("id_tp", prefs.getString("qr", ""));
                    codeData.put("id", prefs.getString("id", ""));
                    codeData.put("attachment1", attachment1);
                    codeData.put("attachment2", attachment2);
                    if(attachment1.equals("no_image1")){
                        codeData.put("binary_attachment1", attachment1);
                    }else{
                        codeData.put("binary_attachment1", encodeImage(((BitmapDrawable)attach1.getDrawable()).getBitmap()));
                    }
                    if(attachment2.equals("no_image")){
                        codeData.put("binary_attachment2", attachment2);
                    }else{
                        codeData.put("binary_attachment2", encodeImage(((BitmapDrawable)attach2.getDrawable()).getBitmap()));
                    }
//                    codeData.put("binary_attachment1", encodeImage(((BitmapDrawable)attach1.getDrawable()).getBitmap()));
//                    codeData.put("binary_attachment2", encodeImage(((BitmapDrawable)attach2.getDrawable()).getBitmap()));
                    socket.emit("isAccepted", codeData);
                    dialogLoading(true);
                } catch (JSONException e) { e.printStackTrace(); }
                break;
            case R.id.btnCancel:
                Intent intentMain = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intentMain);
                finish();
                break;
            case R.id.btnCamAttach1:

                try {
                        //camera stuff
                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                        attachment1 = app.getDate() + "_mobile" + ".png";
                        filename = app.getDate() + "_mobile" + ".png";

                        currentAttach = "attach1";

                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Images.Media.TITLE, "TPCNC");
                        values.put(MediaStore.Images.Media.DESCRIPTION, "attachment1");
                        uriSavedImage = getContentResolver().insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        image = new File(getRealPathFromURI(uriSavedImage));

                        intent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);
                        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
                }catch (SQLiteException e){
                    e.printStackTrace();
                }catch (ActivityNotFoundException e){
                    e.printStackTrace();
                }
                break;
            case R.id.btnCamAttach2:
                try {
                    //camera stuff
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    attachment2 = app.getDate() + "_mobile" + ".png";
                    filename = app.getDate() + "_mobile" + ".png";

                    currentAttach = "attach2";

                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.TITLE, "TPCNC");
                    values.put(MediaStore.Images.Media.DESCRIPTION, "attachment2");
                    uriSavedImage = getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    image = new File(getRealPathFromURI(uriSavedImage));

                    intent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);
                    startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
                }catch (SQLiteException e){
                    e.printStackTrace();
                }catch (ActivityNotFoundException e){
                    e.printStackTrace();
                }
                break;
        }
    };

    public String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = managedQuery(contentUri, proj, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    private String encodeImage(Bitmap bm)
    {
        // File imagefile = new File(path);
//        FileInputStream fis = null;
//        try{
//            fis = new FileInputStream(imagefile);
//        }catch(FileNotFoundException e){
//            e.printStackTrace();
//        }
//        Bitmap bm = BitmapFactory.decodeStream(fis);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG,30,baos);
        byte[] b = baos.toByteArray();
        String encImage = Base64.encodeToString(b, Base64.DEFAULT);
        //Base64.de
        return encImage;
    }

    private void saveImageToExternalStorage(Bitmap bitmap, String filename) {
        File pictureFile = getOutputMediaFile(filename);
        if (pictureFile == null) {
            Log.e(TAG,
                    "Error creating media file, check storage permissions: ");// e.getMessage());
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
    }

    /** Create a File for saving an image or video */
    private File getOutputMediaFile(String filename){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/"
                + getApplicationContext().getPackageName()
                + "/Files");

        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }
        // Create a media file name
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + filename);
        return mediaFile;
    }

    // Star activity for result method to Set captured image on image view after click.
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Adding captured image in bitmap.
            // Bundle extras = data.getExtras();
            // bitmap = (Bitmap) extras.get("data");

            // adding captured image in imageview.
            Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath());

            ExifInterface ei;
            try {
                ei = new ExifInterface(image.getAbsolutePath());

                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED);

                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        bitmap = rotateImage(bitmap, 90);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_180:
                        bitmap = rotateImage(bitmap, 90);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_270:
                        bitmap = rotateImage(bitmap, 90);
                        break;

                    case ExifInterface.ORIENTATION_NORMAL:
                    default:
                }
                saveImageToExternalStorage(bitmap, filename);
                if(currentAttach.equals("attach1")){
                    attach1.setImageBitmap(bitmap);
                    attach1.setVisibility(View.VISIBLE);

                }else{
                    attach2.setImageBitmap(bitmap);
                    attach2.setVisibility(View.VISIBLE);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
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

    private Emitter.Listener onConnect = args -> runOnUiThread(() -> {
        System.out.println("Connected");
    });

    private Emitter.Listener onReconnect = args -> runOnUiThread(() -> {
        System.out.println("Re Connect");
    });

    @Override
    protected void onDestroy() {
        if(socket != null){
            socket.off(Socket.EVENT_CONNECT, onConnect);
            socket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
            socket.off(Socket.EVENT_RECONNECT, onReconnect);
            socket.off("onGetStat", onGetStat);
            socket.disconnect();
        }
        super.onDestroy();
    }
}