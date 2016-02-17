package com.mconnect.learn.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.mconnect.learn.R;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalyzeResult;
import com.microsoft.projectoxford.vision.contract.Category;
import com.microsoft.projectoxford.vision.contract.Face;
import com.microsoft.projectoxford.vision.contract.LanguageCodes;
import com.microsoft.projectoxford.vision.contract.Line;
import com.microsoft.projectoxford.vision.contract.OCR;
import com.microsoft.projectoxford.vision.contract.Region;
import com.microsoft.projectoxford.vision.contract.Word;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;
import com.microsoft.speech.tts.Synthesizer;
import com.microsoft.speech.tts.Voice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Timer;

import butterknife.Bind;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {
    private Uri mUriPhotoTaken;
    private static final int REQUEST_TAKE_PHOTO = 0;
    private static final int REQUEST_SELECT_IMAGE_IN_ALBUM = 1;
    @Bind(R.id.image_taken)
    protected ImageView imageTaken;
    @Bind(R.id.text_analyzed)
    protected TextView textAnalyzed;

    // The image selected to detect.
    private Bitmap mBitmap;
    private VisionServiceClient client;
    private Synthesizer m_syn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageTaken= (ImageView) this.findViewById(R.id.image_taken);
        textAnalyzed= (TextView) this.findViewById(R.id.text_analyzed);
        textAnalyzed.setMovementMethod(new ScrollingMovementMethod());
        if (client==null){
            client = new VisionServiceRestClient(getString(R.string.vision_subscription_key));
        }
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto();
            }
        });

        if (m_syn == null) {
            // Create Text To Speech Synthesizer.
            m_syn = new Synthesizer("clientid", getString(R.string.speech_subscription_key));
        }

        Toast.makeText(this, "If the wave is not played, please see the log for more information.", Toast.LENGTH_LONG).show();

        m_syn.SetServiceStrategy(Synthesizer.ServiceStrategy.AlwaysService);

        Voice v = new Voice("en-US", "Microsoft Server Speech Text to Speech Voice (en-US, ZiraRUS)", Voice.Gender.Female, true);
        //Voice v = new Voice("zh-CN", "Microsoft Server Speech Text to Speech Voice (zh-CN, HuihuiRUS)", Voice.Gender.Female, true);
        m_syn.SetVoice(v, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode)
        {
            case REQUEST_TAKE_PHOTO:
                if (resultCode == RESULT_OK) {

                    mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                            mUriPhotoTaken, getContentResolver());
                    if (mBitmap != null) {
                        imageTaken.setImageBitmap(mBitmap);

                        // Add detection log.
                        Timber.d("AnalyzeActivity", "Image: " + imageTaken + " resized to " + mBitmap.getWidth()
                                + "x" + mBitmap.getHeight());

                        doAnalyze();
                    }
                }
                break;
            default:
                break;
        }
    }
    public void doAnalyze() {

        textAnalyzed.setText("Analyzing...");

        try {
            new doRequestRecognizeText().execute();
        } catch (Exception e)
        {
            textAnalyzed.setText("Error encountered. Exception is: " + e.toString());
        }
    }
    private String process() throws VisionServiceException, IOException {
        Gson gson = new Gson();
        String[] features = {"All"};

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        AnalyzeResult v = this.client.analyzeImage(inputStream, features);

        String result = gson.toJson(v);
        Log.d("result", result);

        return result;
    }

    private String recognizeText() throws VisionServiceException, IOException {
        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        OCR ocr;
        ocr = this.client.recognizeText(inputStream, LanguageCodes.AutoDetect, true);

        String result = gson.toJson(ocr);
        Log.d("result", result);

        return result;
    }

    private class doRequestRecognizeText extends AsyncTask<String, String, String> {
        // Store error message
        private Exception e = null;

        public doRequestRecognizeText() {
        }

        @Override
        protected String doInBackground(String... args) {
            try {
                return recognizeText();
            } catch (Exception e) {
                this.e = e;    // Store error
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);
            // Display based on error existence

            if (e != null) {
                textAnalyzed.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                Gson gson = new Gson();
                OCR r = gson.fromJson(data, OCR.class);

                String result = "";
                for (Region reg : r.regions) {
                    for (Line line : reg.lines) {
                        for (Word word : line.words) {
                            result += word.text + " ";
                        }
                        result += "\n";
                    }
                    result += "\n\n";
                }

                textAnalyzed.setText(result);
                m_syn.SpeakToAudio(textAnalyzed.getText().toString());
            }
        }
    }
    private class doRequest extends AsyncTask<String, String, String> {
        // Store error message
        private Exception e = null;

        public doRequest() {
        }

        @Override
        protected String doInBackground(String... args) {
            try {
                return recognizeText();
            } catch (Exception e) {
                this.e = e;    // Store error
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);
            // Display based on error existence

            textAnalyzed.setText("");
            if (e != null) {
                textAnalyzed.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                Gson gson = new Gson();
                AnalyzeResult result = gson.fromJson(data, AnalyzeResult.class);

                textAnalyzed.append("Image format: " + result.metadata.format + "\n");
                textAnalyzed.append("Image width: " + result.metadata.width + ", height:" + result.metadata.height + "\n");
                textAnalyzed.append("Clip Art Type: " + result.imageType.clipArtType + "\n");
                textAnalyzed.append("Line Drawing Type: " + result.imageType.lineDrawingType + "\n");
                textAnalyzed.append("Is Adult Content:" + result.adult.isAdultContent + "\n");
                textAnalyzed.append("Adult score:" + result.adult.adultScore + "\n");
                textAnalyzed.append("Is Racy Content:" + result.adult.isRacyContent + "\n");
                textAnalyzed.append("Racy score:" + result.adult.racyScore + "\n\n") ;

                for (Category category: result.categories) {
                    textAnalyzed.append("Category: " + category.name + ", score: " + category.score + "\n");
                }

                textAnalyzed.append("\n");
                int faceCount = 0;
                for (Face face: result.faces) {
                    faceCount++;
                    textAnalyzed.append("face " + faceCount + ", gender:" + face.gender + "(score: " + face.genderScore + "), age: " + + face.age + "\n");
                    textAnalyzed.append("    left: " + face.faceRectangle.left +  ",  top: " + face.faceRectangle.top + ", width: " + face.faceRectangle.width + "  height: " + face.faceRectangle.height + "\n" );
                }
                if (faceCount == 0) {
                    textAnalyzed.append("No face is detected");
                }
                textAnalyzed.append("\n");

                textAnalyzed.append("\nDominant Color Foreground :" + result.color.dominantColorForeground + "\n");
                textAnalyzed.append("Dominant Color Background :" + result.color.dominantColorBackground + "\n");

                textAnalyzed.append("\n--- Raw Data ---\n\n");
                textAnalyzed.append(data);
            }

        }
    }
    // When the button of "Take a Photo with Camera" is pressed.
    public void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(intent.resolveActivity(getPackageManager()) != null) {
            // Save the photo taken to a temporary file.
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            try {
                File file = File.createTempFile("IMG_", ".jpg", storageDir);
                mUriPhotoTaken = Uri.fromFile(file);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, mUriPhotoTaken);
                startActivityForResult(intent, REQUEST_TAKE_PHOTO);
            } catch (IOException e) {
                Timber.e("", e);
            }
        }
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
