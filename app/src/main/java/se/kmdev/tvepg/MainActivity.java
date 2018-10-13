package se.kmdev.tvepg;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import se.kmdev.tvepg.epg.EPG;
import se.kmdev.tvepg.epg.EPGClickListener;
import se.kmdev.tvepg.epg.EPGData;
import se.kmdev.tvepg.epg.domain.EPGChannel;
import se.kmdev.tvepg.epg.domain.EPGEvent;
import se.kmdev.tvepg.epg.misc.EPGDataImpl;
import se.kmdev.tvepg.epg.misc.EPGDataListener;
import se.kmdev.tvepg.epg.misc.MockDataService;
import se.kmdev.tvepg.epg.service.MedialaanEPGService;


public class MainActivity extends AppCompatActivity {

    private EPG epg;
    private ImageView programImage;
    private TextView currentTimeTextView;
    private TextView currentEventTextView;
    private TextView currentEventTimeTextView;
    private SimpleDateFormat currentTimeFormat = new SimpleDateFormat("HH:mm");
    private Handler periodicTaskHandler;

    private int devEnterCounter = 0;
    private boolean devMode = false;

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        updateTime();
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER) {
            devEnterCounter += 1;
            if (devEnterCounter >= 5) {
                devMode = !devMode;
                devEnterCounter = 0;
            }
        } else {
            devEnterCounter = 0;
        }
        if (devMode) {
            Toast.makeText(MainActivity.this, event + " clicked", Toast.LENGTH_SHORT).show();
        }
        if (epg != null) {
            return epg.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    private void updateTime() {
        Date now = new Date();
        currentTimeTextView.setText(currentTimeFormat.format(now));
    }

    private ImageView getProgramImageView() {
        ImageView imageView = (ImageView) findViewById(R.id.program_image);
        return imageView;
    }

    private void updateImageCropping(ImageView imageView){
        final Matrix matrix = imageView.getImageMatrix();
        final float imageWidth = imageView.getDrawable().getIntrinsicWidth();
        final float imageHeight = imageView.getDrawable().getIntrinsicHeight();
        final int screenWidth = getResources().getDisplayMetrics().widthPixels;
        final float scaleRatio = screenWidth / imageWidth;
        matrix.postScale(scaleRatio, scaleRatio);
        matrix.postTranslate(0, -1 * imageHeight * 0.30f);
        imageView.setImageMatrix(matrix);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        epg = (EPG) findViewById(R.id.epg);
        programImage = getProgramImageView();
        epg.setProgramImageView(programImage);

        currentTimeTextView = (TextView) findViewById(R.id.current_time);
        currentEventTextView = (TextView) findViewById(R.id.current_event);
        currentEventTimeTextView = (TextView) findViewById(R.id.current_event_time);

        epg.setCurrentEventTextView(currentEventTextView);
        epg.setCurrentEventTimeTextView(currentEventTimeTextView);

        periodicTaskHandler = new Handler();

        epg.setEPGClickListener(new EPGClickListener() {
            @Override
            public void onChannelClicked(int channelPosition, EPGChannel epgChannel) {
                Toast.makeText(MainActivity.this, epgChannel.getName() + " clicked", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onEventClicked(int channelPosition, int programPosition, EPGEvent epgEvent) {
                Toast.makeText(MainActivity.this, epgEvent.getTitle() + " clicked", Toast.LENGTH_SHORT).show();
                epg.selectEvent(epgEvent, true);
                updateTime();
            }

            @Override
            public void onResetButtonClicked() {
                epg.recalculateAndRedraw(null, true);
            }
        });

        // Do initial load of data.
        new AsyncLoadEPGData(epg).execute();


    }

    @Override
    public void onStart() {
        super.onStart();
        periodicTaskHandler.postDelayed(new TaskRunnable(), 50000);
        setLayout(this.getResources().getConfiguration().orientation);
    }

    @Override
    public void onStop() {
        super.onStop();
        periodicTaskHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setLayout(newConfig.orientation);
    }

    /**
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {

        int hrs = (TimeZone.getDefault().getRawOffset() + TimeZone.getDefault().getDSTSavings()) / 1000 / 60 / 60;
        Toast.makeText(MainActivity.this, TimeZone.getDefault().getDisplayName() + " " + hrs, Toast.LENGTH_SHORT).show();

        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            EPGDataListener epgDataListener = new EPGDataListener(epg);
            new MedialaanEPGService(this).getData(epgDataListener, 0);
            new MedialaanEPGService(this).getData(epgDataListener, 1);
        }

        setLayout(this.getResources().getConfiguration().orientation);
        updateTime();
    }
    */

    private void setLayout(int orientation) {
        epg.setOrientation(orientation);
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            currentTimeTextView.setVisibility(View.GONE);
        } else {
            currentTimeTextView.setVisibility(View.VISIBLE);
        }
        //set text items properly on Program Image
        //Program Title (@left)
        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        Double timeTopMargin = programImage.getHeight() * 0.20;
        Double timeStartMargin = programImage.getWidth() * 0.05;
        rlp.setMargins(timeStartMargin.intValue(), timeTopMargin.intValue(), 0, 0); // rlp.setMargins(left, top, right, bottom);
        currentEventTextView.setLayoutParams(rlp);

        //Program Timing (@left)
        rlp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        timeTopMargin = programImage.getHeight() * 0.48;
        timeStartMargin = programImage.getWidth() * 0.05;
        rlp.setMargins(timeStartMargin.intValue(), timeTopMargin.intValue(), 0, 0); // rlp.setMargins(left, top, right, bottom);
        currentEventTimeTextView.setLayoutParams(rlp);


        //Current Time (@right)
        rlp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        timeTopMargin = programImage.getHeight() * 0.20;
        timeStartMargin = programImage.getWidth() * 0.85;
        rlp.setMargins(timeStartMargin.intValue(), timeTopMargin.intValue(), 0, 0); // rlp.setMargins(left, top, right, bottom);
        currentTimeTextView.setLayoutParams(rlp);
    }

    @Override
    protected void onDestroy() {
        if (epg != null) {
            epg.clearEPGImageCache();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
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

    private class TaskRunnable implements Runnable {
        @Override
        public void run() {
            try {
                updateTime();
                epg.redraw();
            } catch (Throwable e) {
                //too bad
                //TODO: log failure
            }
            periodicTaskHandler.postDelayed(this, 30000);
        }
    }

    /**
     * NOT USED ANYMORE, left as snippet
     */
    private static class AsyncLoadEPGData extends AsyncTask<Void, Void, EPGData> {

        EPG epg;

        public AsyncLoadEPGData(EPG epg) {
            this.epg = epg;
        }

        @Override
        protected EPGData doInBackground(Void... voids) {
            return new EPGDataImpl(MockDataService.getMockData());
        }

        @Override
        protected void onPostExecute(EPGData epgData) {
            epg.setEPGData(epgData);
            epg.recalculateAndRedraw(null, false);
        }
    }
}
