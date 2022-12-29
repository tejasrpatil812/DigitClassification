package com.example.androidappproject1;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> imageCaptureResultLauncher;
    private String photoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageCaptureResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    //If the activity result code is ok
                    if (result.getResultCode() == Activity.RESULT_OK) {

                        //creating a new intent to switch to the upload image activity page
                        Intent uploadPhotoIntent = new Intent(this, UploadImageActivity.class);

                        //putting the image result as a parameter in the intent and then starting it.
                        uploadPhotoIntent.putExtra("IMAGE PATH", photoPath);
                        startActivity(uploadPhotoIntent);
                    }
                });
    }

    public void takePicture(View view) throws IOException {
        //intent to switch to the android camera
        Intent clickPictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        //if the camera intent is successfully resolved, ie the app is able to properly access the camera feature,
        //then start the camera activity. Else show an error message
        if (clickPictureIntent.resolveActivity(getPackageManager()) != null) {

            File picture = generatePictureFile();

            if (picture != null) {
                Uri photoURI = FileProvider.getUriForFile(this, "com.example.android.fileprovider", picture);
                clickPictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                imageCaptureResultLauncher.launch(clickPictureIntent);
            }
        } else
            Toast.makeText(MainActivity.this, "Unable to access the camera", Toast.LENGTH_SHORT).show();
    }

    private File generatePictureFile() throws IOException {
        // Create an image file name
        String tempFileName = "TEMP_" + String.valueOf(new Date().getTime());

        File fileDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(tempFileName, ".jpg", fileDirectory);

        photoPath = image.getAbsolutePath();

        return image;
    }

}