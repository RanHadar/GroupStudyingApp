package com.example.groupstudyingapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class AddQuestionActivity extends AppCompatActivity {
    public static final int CAMERA_ACTION = 0;
    public static final int GALLERY_ACTION = 1;
    public static final String IMAGE_UPLOADED = "image_uploaded";
    public static final String IMAGE_FILE_CREATION_FAILURE = "image_file_creation_failure";
    public static final String PACKEGE_NAME = "com.example.android.fileprovider";
    public static final String PLS_UPLOAD_IMG = "Please upload an image";
    public static final String FINISHED_UPLOAD = "finished upload";
    public static final String FAILED_TO_UPLOAD = "failed to upload";

    private EditText titleInput;
    private Button cameraButton;
    private Button galleryButton;
    private Button saveButton;
    private ImageView questionImage;


    boolean isPhotoEntered = false;
    CoursePageAdapter adapter;
    private ArrayList<Question> questions;
    AppData appData;
    FireStoreHandler fireStoreHandler;
    private Course course;
    private String newQuestionImagePath;
    private Uri newImageUri;

    /**
     * The broadcast receiver of the activity
     **/
    private BroadcastReceiver br;

    /**
     * The local path of the last image taken by the camera
     **/
    private String currentPhotoPath;
    private CircularProgressDrawable circularProgressDrawable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_question);
        getAppData();
        initializeUi();

        br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(FINISHED_UPLOAD) ||
                        intent.getAction().equals(FAILED_TO_UPLOAD)) {
                    finish();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(FINISHED_UPLOAD);
        filter.addAction(FAILED_TO_UPLOAD);
        this.registerReceiver(br, filter);
    }

    private void getAppData() {
        //TODO - the data won't be loaded again like this, were gonna send only the relevant course from main activity
        appData = (AppData) getApplicationContext();
        fireStoreHandler = appData.fireStoreHandler;
        course = fireStoreHandler.getCurrentCourse();
    }


    private void initializeUi() {
        titleInput = findViewById(R.id.userTitle);
        titleInput.setText("");
        questionImage = findViewById(R.id.questionImage);
        cameraButton = findViewById(R.id.cameraButton);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchTakePictureIntent();
            }
        });
        galleryButton = findViewById(R.id.galleryButton);
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pickPhoto = new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pickPhoto, GALLERY_ACTION);
            }
        });
        saveButton = findViewById(R.id.saveButton);
        circularProgressDrawable = new CircularProgressDrawable(this);
        circularProgressDrawable.setStrokeWidth(10f);
        circularProgressDrawable.setCenterRadius(60f);
        circularProgressDrawable.start();
        saveQuestion();
    }

    private void saveQuestion() {
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (titleInput.getText().toString().equals("")) {
                    Toast.makeText(AddQuestionActivity.this, "Please write a title!", Toast.LENGTH_SHORT).show();
                } else {
                    String questionTitleInput = titleInput.getText().toString();
                    if (!isPhotoEntered) {
                        Toast.makeText(AddQuestionActivity.this, PLS_UPLOAD_IMG, Toast.LENGTH_SHORT).show();
                    } else {
                        Question newQuestion = new Question(questionTitleInput,
                                newQuestionImagePath);
                        fireStoreHandler.uploadQuestionImage(newImageUri,
                                newQuestionImagePath,
                                newQuestion,
                                AddQuestionActivity.this);
                    }
                }
            }
        });
    }

    //////////////////////////// onActivityResult related methods //////////////////////////////////
    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case CAMERA_ACTION:
                    handleCameraImageCase();
                    break;

                case GALLERY_ACTION:
                    handleGalleryImageCase(imageReturnedIntent);
                    break;
            }
        } else {
            Log.i("image_save_error", "image was'nt saved");
        }
    }


    /**
     * Sets everything that is needed for handling an image from the camera and calls the relevant
     * activity
     */
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.i(IMAGE_FILE_CREATION_FAILURE, "Error occurred while creating the File");
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, PACKEGE_NAME, photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, CAMERA_ACTION);
            }
        }
    }

    /**
     * Once you decide the directory for the file, you need to create a collision-resistant file name.
     * You may wish also to save the path in a member variable for later use. Here's an example
     * solution in a method that returns a unique file name for a new photo using a date-time stamp.
     *
     * @return
     * @throws IOException
     */
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    /**
     * @param imageReturnedIntent handle the GALLERY_ACTION case in onActivityResult
     */
    private void handleGalleryImageCase(Intent imageReturnedIntent) {
        isPhotoEntered = true;
        newImageUri = imageReturnedIntent.getData();
        if (newImageUri != null) {
            newQuestionImagePath = "questions/" + newImageUri.getLastPathSegment();
            Glide.with(AddQuestionActivity.this).load(newImageUri).placeholder(circularProgressDrawable).into(questionImage);

        }
    }

    /**
     * handles the CAMERA_ACTION case in onActivityResult
     */
    private void handleCameraImageCase() {
        File imgFile = new File(currentPhotoPath);
        if (imgFile.exists()) {
            isPhotoEntered = true;
            newImageUri = Uri.fromFile(imgFile);
            newQuestionImagePath = "questions/" + newImageUri.getLastPathSegment();
            Glide.with(AddQuestionActivity.this).load(newImageUri).placeholder(circularProgressDrawable).into(questionImage);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(br);
    }
}
