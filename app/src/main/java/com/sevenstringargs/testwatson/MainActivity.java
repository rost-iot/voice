package com.sevenstringargs.testwatson;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.google.gson.Gson;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.ISpeechDelegate;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.dto.SpeechConfiguration;
import com.sevenstringargs.testwatson.model.Alternative;
import com.sevenstringargs.testwatson.model.Message;
import com.sevenstringargs.testwatson.model.Wrapper;

import java.io.IOException;
import java.net.URI;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity implements ISpeechDelegate {
    private Gson gson;
    private String[] buttonState = new String[]{"Enable", "Disable"};
    private int state = 0;
    private TextView textView;
    private TextView textView2;
    private CheckBox checkBox;
    private int currentState = 0;

    private int toggleState(){
        if (state == 1){
            state = 0;
            SpeechToText.sharedInstance().stopRecording();
            SpeechToText.sharedInstance().stopRecognition();
            updateCurrentState(4);
            textView2.setText("");
        } else {
            state = 1;
            updateCurrentState(1);
            SpeechToText.sharedInstance().recognize();
        }

        return state;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gson = new Gson();


        Log.i("ssa", "Starting");

        SpeechToText.sharedInstance().initWithContext(getHost(), getApplicationContext(), new SpeechConfiguration());
        SpeechToText.sharedInstance().setCredentials(getString(R.string.username), getString(R.string.password));
        SpeechToText.sharedInstance().setDelegate(this);
        SpeechToText.sharedInstance().setModel("en-US_BroadbandModel");

        final Button button = (Button) findViewById(R.id.button);
        if (button != null){
            button.setText(buttonState[state]);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i("ssa", "Clicked");
                    button.setText(buttonState[toggleState()]);
                }
            });
            button.setText(buttonState[0]);
        }

        textView = (TextView) findViewById(R.id.textView);
        if (textView != null){
            textView.setText("");
        }

        textView2 = (TextView) findViewById(R.id.textView2);
        if (textView2 != null){
            textView2.setText("");
        }

        checkBox = (CheckBox) findViewById(R.id.checkBox);
        if (checkBox != null){
            checkBox.setChecked(false);
        }
    }

    private String getCurrentStateString(int s){
        String statestr = "";
        switch(s){
            case 0:
                statestr = "Disconnected";
                break;
            case 1:
                statestr = "Connecting";
                break;
            case 2:
                statestr = "Connected";
                break;
            case 3:
                if (state == 0){
                   statestr = "Disconnected";
                } else {
                    statestr = "Error / Disconnected";
                }
                break;
            case 4:
                statestr = "Disconnecting";
                break;
        }

        return statestr;
    }

    private URI getHost(){
        return URI.create("wss://stream.watsonplatform.net/speech-to-text/api");
    }

    private void updateCurrentState(int state){
        currentState = state;
        textView.setText(getCurrentStateString(state));
    }

    @Override
    public void onOpen() {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateCurrentState(2);
            }
        });
        Log.i("ssa", "onOpen()");
    }

    @Override
    public void onError(String s) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateCurrentState(3);
            }
        });
        Log.i("ssa", "onError()");
        Log.i("ssa", s);

    }

    @Override
    public void onClose(int i, String s, boolean b) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateCurrentState(0);
            }
        });
        Log.i("ssa", "onClose()");
        Log.i("ssa", s);
    }

    @Override
    public void onMessage(String s) {
        Alternative a = null;

        final String finalS = s;
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView2.setText(finalS);
            }
        });
        if (s.contains(": true")){
            s = s.replaceAll("final", "over");
            Log.i("ssa", "onMessage() " + s);

            try {
                Wrapper wrapper = gson.fromJson(s, Wrapper.class);
                if (wrapper.results.length > 0){
                    if (wrapper.results[0].alternatives.size() > 0){
                        a = wrapper.results[0].alternatives.get(0);
                    }
                }
            } catch(Exception e){
                e.printStackTrace();
                Log.i("ssa", "Failed to parse from json");
                return;
            }

            final Alternative finalA = a;
            if (finalA != null){
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Message m = new Message();
                        m.voice = finalA.transcript;
                        m.fuzzy = checkBox.isChecked();
                        String json = gson.toJson(m);
                        try {
                            String res = post("http://rost.eu-gb.mybluemix.net/api/voice", json);
                            Log.i("ssa", "Response: " + res);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.i("ssa", "Failed to post");
                        }
                    }
                });
                t.start();
            }
        }

    }

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    OkHttpClient client = new OkHttpClient();

    String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    @Override
    public void onAmplitude(double v, double v1) {
    }
}
