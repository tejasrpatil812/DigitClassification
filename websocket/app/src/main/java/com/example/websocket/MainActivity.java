package com.example.websocket;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    //https://developer.android.com/studio/run/emulator-networking
    private ArrayList<Interpreter> interpreterList = new ArrayList<>();
    private static String SERVER_SOCKET_LOGGING_TAG = "SocketServerLoggingTag";

    Runnable conn = new Runnable() {
        public void run() {
            try {
                ServerSocket server = new ServerSocket(8050);

                while (true) {
                    Socket socket = server.accept();
                    DataInputStream inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    String msgString = inputStream.readUTF();

                    Log.i(SERVER_SOCKET_LOGGING_TAG, "Data received, processing begins");

                    String[] ipData = msgString.split("\\|");

                    byte[] base64Image = Base64.decode(ipData[1], Base64.DEFAULT);
                    Bitmap bitmapImage = BitmapFactory.decodeByteArray(base64Image, 0, base64Image.length);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ImageView imageView = (ImageView) findViewById(R.id.recImg);
                            imageView.setImageBitmap(bitmapImage);
                        }
                    });

                    ByteBuffer imagePixels = preprocess(bitmapImage);
                    Log.i("TEST Byte buffer", Arrays.toString(imagePixels.array()));
                    float[] outputArr = labelImage(imagePixels, Integer.parseInt(ipData[0]));

                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                    outputStream.writeUTF(serializeOutput(outputArr));
                    outputStream.close();
                    outputStream.flush();

                    inputStream.close();
                    socket.close();
                }
            } catch (IOException ex) {
                Log.e(SERVER_SOCKET_LOGGING_TAG, "An io exception occurred");
                ex.printStackTrace();
            } catch (Exception ex) {
                Log.e(SERVER_SOCKET_LOGGING_TAG, "An exception occurred");
                ex.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        for (int index = 1; index <= 4; index++) {
            Interpreter tflite = null;
            try {
                tflite = new Interpreter(loadModelFile(index));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            interpreterList.add(tflite);
        }
        new Thread(conn).start();
    }

    private MappedByteBuffer loadModelFile(int index) throws IOException {
        AssetFileDescriptor assetFileDescriptor = this.getAssets().openFd("model_" + index + ".tflite");
        FileInputStream fileInputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileInputStreamChannel = fileInputStream.getChannel();
        long startOffset = assetFileDescriptor.getStartOffset();
        long length = assetFileDescriptor.getDeclaredLength();
        return fileInputStreamChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, length);
    }

    private ByteBuffer preprocess(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 14 * 14);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] pixelArr = new int[14 * 14];
        bitmap.getPixels(pixelArr, 0, bitmap.getWidth(), 0, 0, 14, 14);
        for (int pixel : pixelArr) {
            float blueChannel = (pixel) & 0xFF;
            float greenChannel = (pixel >> 8) & 0xFF;
            float redChannel = (pixel >> 16) & 0xFF;
            float pixelValue = (redChannel + greenChannel + blueChannel) / 3 / 255.f;
            byteBuffer.putFloat(pixelValue);
            Log.i("TEST PIXELS", String.valueOf(pixelValue));
        }
        return byteBuffer;
    }

    private float[] labelImage(ByteBuffer imagePixels, int imgQuadrant) {
        float[][] outputArr = new float[1][10];
        interpreterList.get(imgQuadrant).run(imagePixels, outputArr);
        Log.i("TEST", String.format("For imgQuadrant %d %d", imgQuadrant, outputArr.length));
        for (float f : outputArr[0]) {
            Log.i("TEST", String.valueOf(f));
        }
        return outputArr[0];
    }

    private String serializeOutput(float[] output) {
        String result = "";
        for (float out : output) {
            result += String.format("%.2f", out) + ",";
        }
        return result;
    }
}