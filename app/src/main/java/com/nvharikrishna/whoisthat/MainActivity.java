package com.nvharikrishna.whoisthat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.service.notification.NotificationListenerService;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.system.ErrnoException;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final int CHECK_CODE = 0x1;
    private final int LONG_DURATION = 5000;
    private final int SHORT_DURATION = 1200;

    private static Speaker speaker;

    private ToggleButton toggle;
    private CompoundButton.OnCheckedChangeListener toggleListener;

    private TextView smsText;
    private TextView smsSender;

    private BroadcastReceiver smsReceiver;

    private static TextToSpeech t1;
    private static SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, NotificationListenerService.class));
        setContentView(R.layout.activity_main);

        toggle = (ToggleButton)findViewById(R.id.speechToggle);
        smsText = (TextView)findViewById(R.id.sms_text);
        smsSender = (TextView)findViewById(R.id.sms_sender);

        toggleListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton view, boolean isChecked) {
                if(isChecked){
                    speaker.allow(true);
                    speaker.speak(getString(R.string.start_speaking));
                }else{
                    speaker.speak(getString(R.string.stop_speaking));
                    speaker.allow(false);
                }
            }
        };
        toggle.setOnCheckedChangeListener(toggleListener);

//        checkTTS();
//        initializeSMSReceiver();
//        registerSMSReceiver();

//        launchSpeechRecognizer();

//        IntentFilter recognizeFilter = new IntentFilter();
//        recognizeFilter.addAction("whoisthat.Recognize");
//        RecognizeReceiver recognizeReceiver = new RecognizeReceiver();
//        registerReceiver(recognizeReceiver, recognizeFilter);
//
//        IntentFilter speakFilter = new IntentFilter();
//        speakFilter.addAction(("whoisthat.Speak"));
//        SpeakReceiver speakReceiver = new SpeakReceiver();
//        registerReceiver(speakReceiver, speakFilter);

        t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener(){

            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR){
                    t1.setLanguage(Locale.US);
                }
            }
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        VoiceCommandListener voiceCommandListener = new VoiceCommandListener();
        speechRecognizer.setRecognitionListener(voiceCommandListener);

        IntentFilter recognizeAndSpeakFilter = new IntentFilter();
        recognizeAndSpeakFilter.addAction("whoisthat.Recognize.Speak");
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle results = getResultExtras(true);
                String hierarchy = results.getString("hierarchy");

                results.putString("hierarchy", hierarchy);
                Log.d("MAIN ACTIVITY", "***Inside register receiver****");

            }
        }, recognizeAndSpeakFilter);
    }

    private void checkTTS(){
        Intent check = new Intent();
        check.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(check, CHECK_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                speaker = new Speaker(this);
            } else {
                Intent install = new Intent();
                install.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(install);
            }
        }
    }
    private void initializeSMSReceiver(){
        smsReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {

                Bundle bundle = intent.getExtras();
                if(bundle!=null){
                    Object[] pdus = (Object[])bundle.get("pdus");
                    for(int i=0;i<pdus.length;i++){
                        byte[] pdu = (byte[])pdus[i];
                        SmsMessage message = SmsMessage.createFromPdu(pdu);
                        String text = message.getDisplayMessageBody();
                        String sender = getContactName(message.getOriginatingAddress());
                        speaker.pause(LONG_DURATION);
                        speaker.speak("You have a new message from" + sender + "!");
                        speaker.pause(SHORT_DURATION);
                        speaker.speak(text);
                        smsSender.setText("Message from " + sender);
                        smsText.setText(text);
                    }
                }

            }
        };
    }

    private String getContactName(String phone){
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone));
        String projection[] = new String[]{ContactsContract.Data.DISPLAY_NAME};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if(cursor.moveToFirst()){
            return cursor.getString(0);
        }else {
            return "unknown number";
        }
    }

    private void registerSMSReceiver() {
        IntentFilter intentFilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(smsReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(smsReceiver);
        speaker.destroy();
    }

//    public void launchSpeechRecognizer(){
//        SpeechRecognizer speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this.getApplicationContext());
//        speechRecognizer.setRecognitionListener(new VoiceCommandListener());
//        speechRecognizer.startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH));
//    }


    public static class RecognizeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("RECOGNISE RECEIVER", "recognise received message ******");
            String message = intent.getStringExtra("message_to_speak");
            launchSpeechRecognizer(context, message);
//            Intent speakIntent = new Intent("whoisthat.Speak");
//            speakIntent.putExtra("message_to_speak", message);
//            context.sendBroadcast(speakIntent);
        }

        public void launchSpeechRecognizer(Context context, String message){
//            SpeechRecognizer speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
//            Speaker speaker = new Speaker(context);


            speechRecognizer.startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH));

        }
    }

    public static class SpeakReceiver extends BroadcastReceiver {

//        private Speaker speaker;

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("SPEAK RECEIVER", "speak received message ******");
//            speaker = new Speaker(context);
//            speaker.allow(true);
            t1.playSilence(5000, TextToSpeech.QUEUE_ADD, null);
            t1.speak(intent.getStringExtra("message_to_speak"),TextToSpeech.QUEUE_FLUSH, null);

        }

    }

}
