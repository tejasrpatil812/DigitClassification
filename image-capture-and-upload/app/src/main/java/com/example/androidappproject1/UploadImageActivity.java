package com.example.androidappproject1;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;


public class UploadImageActivity extends AppCompatActivity {
    File tempImgFilePath;
    String[] base64EncodedChunks = new String[4];
    String[] ipAddresses = {"192.168.0.125", "192.168.0.156", "192.168.0.206", "192.168.0.76"};
    int[] portNumbers = {8050, 8050, 8050, 8050};
    String base64Img;
    float[][] outputProbability = new float[4][10];
    String imagePath;

    private class SocketClientRunnable implements Runnable {

        private String ipAddress;
        private int portNumber;
        private int idx;
        private String socketMessage;

        public SocketClientRunnable(String ipAddress, int portNumber, String socketMessage, int i) {
            this.ipAddress = ipAddress;
            this.portNumber = portNumber;
            this.socketMessage = socketMessage;
            this.idx = i;
        }

        public void run() {
            Socket socket = null;
            try {
                socket = new Socket(ipAddress, portNumber);
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                outputStream.writeUTF(String.format("%d|%s", idx, this.socketMessage));
                String strInputStream = inputStream.readUTF();
                outputProbability[idx] = deserializeOutput(strInputStream);
            } catch (Exception e) {
                e.printStackTrace();
                Log.i("TEST", "FAILED TO CONNECT!!");
            }
        }
    }

    @SuppressLint("WrongThread")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_image);

        // getting the intent that triggered the upload image activity page, and getting the
        // captured image from the intent
        Intent uploadImgIntent = getIntent();
        imagePath = uploadImgIntent.getStringExtra("IMAGE PATH");

        //converting the image to bitmap
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, bitmapOptions);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 28, 28, false);
        Bitmap grayScaleBitmap = convertToGrayScale(scaledBitmap);
        Bitmap invertedGrayScaleBitmap = invertImage(grayScaleBitmap);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        invertedGrayScaleBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        base64Img = Base64.encodeToString(byteArray, Base64.DEFAULT);
        Bitmap[] divideBitmapImg = divideBitmapImage(invertedGrayScaleBitmap);

        for (int i = 0; i < 4; i++) {
            ByteArrayOutputStream byteArrayOutputStream1 = new ByteArrayOutputStream();
            divideBitmapImg[i].compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream1);
            byte[] imgByteArr = byteArrayOutputStream1.toByteArray();
            base64EncodedChunks[i] = Base64.encodeToString(imgByteArr, Base64.DEFAULT);
        }

        ImageView capturedImgDisplayWidget = findViewById(R.id.capturedImg);
        capturedImgDisplayWidget.setImageBitmap(bitmap);

        tempImgFilePath = new File(imagePath);

        // creating a text view to show status of the upload. Setting it to invisible initially.
        TextView uploadImgStatus = findViewById(R.id.uploadImgStatus);
        uploadImgStatus.setVisibility(View.INVISIBLE);
    }

    public static Bitmap convertToGrayScale(Bitmap bitmap) {
        int bitmapWidth, bitmapHeight;
        bitmapHeight = bitmap.getHeight();
        bitmapWidth = bitmap.getWidth();
        Bitmap bitmapGrayScale = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmapGrayScale);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(colorFilter);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return bitmapGrayScale;
    }

    public Bitmap invertImage(Bitmap srcBitmap) {
        int bitmapHeight = srcBitmap.getHeight();
        int bitmapWidth = srcBitmap.getWidth();
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        ColorMatrix grayScaleColorMatrix = new ColorMatrix();
        grayScaleColorMatrix.setSaturation(0);

        ColorMatrix invertedColorMatrix = new ColorMatrix();
        invertedColorMatrix.set(new float[]{
                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        });
        invertedColorMatrix.preConcat(grayScaleColorMatrix);
        ColorMatrixColorFilter matrixColorFilter = new ColorMatrixColorFilter(invertedColorMatrix);
        paint.setColorFilter(matrixColorFilter);
        canvas.drawBitmap(srcBitmap, 0, 0, paint);
        return bitmap;
    }

    public void uploadImageToServer(View v) {
        Thread clients[] = new Thread[4];

        for (int i = 0; i < 4; i++) {
            clients[i] = new Thread(new SocketClientRunnable(ipAddresses[i], portNumbers[i], base64EncodedChunks[i], i));
            clients[i].start();
            try {
                clients[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for (int i = 0; i < 4; i++) {
            Log.i("TEST", String.format("Data for %d", i));
            for (int j = 0; j < 10; j++) {
                Log.i("TEST", String.valueOf(j) + ":" + String.valueOf(outputProbability[i][j]));
            }
        }

        String classifiedDigit = decideOutput(outputProbability);
        Log.i("TEST", "CLASSIFIED IMG LABEL:" + classifiedDigit);
        try {
            createImageCategoryDirectory(classifiedDigit, imagePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(UploadImageActivity.this);
        builder.setMessage(Html.fromHtml("<font>Successfully uploaded the image.</font>"))
                .setTitle("Success")
                .setCancelable(false);

        builder.setPositiveButton("OK", (DialogInterface.OnClickListener) (dialog, which) -> {
            finish();
            Toast.makeText(this, "Classified Image label is : " + classifiedDigit,
                    Toast.LENGTH_LONG).show();
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private float[] deserializeOutput(String output) {
        float[] resArr = new float[10];
        String[] stringRes = output.split(",");
        for (int i = 0; i < 10; i++) {
            resArr[i] = Float.valueOf(stringRes[i]);
        }
        return resArr;
    }

    private Bitmap[] divideBitmapImage(Bitmap bitmap) {
        Bitmap[] bitmapArr = new Bitmap[4];
        bitmapArr[0] = Bitmap.createBitmap(
                bitmap,
                0, 0,
                bitmap.getWidth() / 2, bitmap.getHeight() / 2
        );
        bitmapArr[1] = Bitmap.createBitmap(
                bitmap,
                bitmap.getWidth() / 2, 0,
                bitmap.getWidth() / 2, bitmap.getHeight() / 2
        );
        bitmapArr[2] = Bitmap.createBitmap(
                bitmap,
                0, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2, bitmap.getHeight() / 2
        );
        bitmapArr[3] = Bitmap.createBitmap(
                bitmap,
                bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2, bitmap.getHeight() / 2
        );
        return bitmapArr;
    }

    private String decideOutput(float[][] probabilityArr) {
        Map<Integer, Float> hm = new HashMap<>();
        int digit = 0;
        float digitProbability = 0.0f;
        int devices = probabilityArr.length;
        int digits = probabilityArr[0].length;

        for (int j = 0; j < digits; j++) {
            float probValSum = 0;
            for (int i = 0; i < devices; i++) {
                probValSum += probabilityArr[i][j];
            }
            hm.put(j, probValSum);
            Log.i("TEST SUM", String.valueOf(j) + ":" + String.valueOf(probValSum));
        }
        for (Map.Entry<Integer, Float> entry : hm.entrySet()) {
            if (entry.getValue() >= digitProbability) {
                digit = entry.getKey();
                digitProbability = entry.getValue();
            }
        }
        return String.valueOf(digit);
    }

    private String createImageCategoryDirectory(String label, String photoPath) throws IOException {
        File classifiedDir = new File(Environment.getExternalStorageDirectory(), "Download" + "/" + label);
        if (!classifiedDir.exists()) {
            boolean a = classifiedDir.mkdir();
            Log.i("TEST FILE", String.valueOf(a));
        }
        String fileName = photoPath.substring(photoPath.lastIndexOf("/") + 1);
        String fileNameWithoutExtension = fileName.split("\\.")[0];
        File newImageFile = File.createTempFile(fileNameWithoutExtension, ".jpg", classifiedDir);
        InputStream inputStream = new FileInputStream(photoPath);
        try {
            OutputStream outputStream = new FileOutputStream(newImageFile);
            try {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, len);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                outputStream.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            inputStream.close();
        }
        return label;
    }
}


