package se.kmdev.tvepg.epg.service;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.common.collect.Maps;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import se.kmdev.tvepg.epg.EPGData;
import se.kmdev.tvepg.epg.domain.EPGChannel;
import se.kmdev.tvepg.epg.domain.EPGEvent;
import se.kmdev.tvepg.epg.misc.EPGDataImpl;
import se.kmdev.tvepg.epg.misc.EPGDataListener;

/**
 * Created by MVRM on 10/04/2017.
 */

public class MedialaanEPGService {

    private Context context;

    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static SimpleDateFormat nowFormat = new SimpleDateFormat("yyyy-MM-dd");

    public MedialaanEPGService(Context context) {
        this.context = context;
    }

    public void getData(final EPGDataListener listener) {
        getData(listener, 0);
    }

    public void getData(final EPGDataListener listener, final int dayOffset) {
        RequestQueue queue = Volley.newRequestQueue(context);

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DATE, dayOffset);
        Date epgDate = cal.getTime();

        String today = nowFormat.format(epgDate);

        String query = null;
        try {
            query = "date=" + today +
                    "&" + URLEncoder.encode("channels[]", "utf-8") + "=een" +
                    "&" + URLEncoder.encode("channels[]", "utf-8") + "=vtm" +
                    "&" + URLEncoder.encode("channels[]", "utf-8") + "=vier" +
                    "&" + URLEncoder.encode("channels[]", "utf-8") + "=2be" +
                    "&" + URLEncoder.encode("channels[]", "utf-8") + "=caz" +
                    "&" + URLEncoder.encode("channels[]", "utf-8") + "=vitaya" +
                    "&" + URLEncoder.encode("channels[]", "utf-8") + "=kzoom" +
                    "&" + URLEncoder.encode("channels[]", "utf-8") + "=kadet"
            ;

            final String URL = "https://epg.medialaan.io/epg/v2/schedule?" + query;

            StringRequest jsonRequest = new StringRequest(Request.Method.GET, URL,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {

                            try {
                                JSONObject obj = new JSONObject(response);
                                listener.processData(parseData(obj));
                            } catch (Exception e) {
                                e.printStackTrace();
                                //TODO
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    System.err.println(error);
                }
            }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("Authorization", "apikey=stievie-html5-2.1-jFyfXXsekWSaj7ZsYaSZsYy8rqaueh6l");
                    params.put("Accept-Language", "nl-BE");
                    return params;
                }
            };
            queue.add(jsonRequest);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private EPGData parseData(JSONObject object) {
        EPGChannel firstChannel = null;
        EPGChannel prevChannel = null;
        EPGChannel currentChannel = null;
        EPGEvent prevEvent = null;

        long wrongMedialaanTime = TimeZone.getDefault().getRawOffset() + TimeZone.getDefault().getDSTSavings();

        try {
            Map<EPGChannel, List<EPGEvent>> map = Maps.newLinkedHashMap();
            JSONArray jsonChannels = object.getJSONArray("channels");
            for (int i = 0; i < jsonChannels.length(); i++) {
                JSONObject jsonChannel = jsonChannels.getJSONObject(i);
                String channelName = jsonChannel.getString("id");
                currentChannel = new EPGChannel("http://resolvethis.com/epg/be/" + channelName + ".png", channelName, i);
                if (firstChannel == null) {
                    firstChannel = currentChannel;
                }
                if (prevChannel != null) {
                    currentChannel.setPreviousChannel(prevChannel);
                    prevChannel.setNextChannel(currentChannel);
                }
                prevChannel = currentChannel;
                List<EPGEvent> epgEvents = new ArrayList<>();
                map.put(currentChannel, epgEvents);
                JSONArray jsonEvents = jsonChannel.getJSONArray("items");
                for (int j = 0; j < jsonEvents.length(); j++) {
                    JSONObject jsonEvent = jsonEvents.getJSONObject(j);
                    JSONArray programURLarray = jsonEvent.getJSONArray("images");
                    String programURL = null;
                    try {
                        if (programURLarray != null) {
                            JSONObject programURLs = programURLarray.getJSONObject(0);
                            JSONObject programURLStyles = programURLs.getJSONObject("styles");
                            programURL = programURLStyles.getString("800x450");
                        }
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                    }

                    Date startDate = dateFormat.parse(jsonEvent.getString("startTime"));
                    long startTime = startDate.getTime() + wrongMedialaanTime;
                    Date stopDate = dateFormat.parse(jsonEvent.getString("stopTime"));
                    long endTime = stopDate.getTime() + wrongMedialaanTime;
                    EPGEvent epgEvent = new EPGEvent(currentChannel, startTime, endTime, jsonEvent.getString("title"), programURL);
                    if (prevEvent != null) {
                        epgEvent.setPreviousEvent(prevEvent);
                        prevEvent.setNextEvent(epgEvent);
                    }
                    prevEvent = epgEvent;
                    currentChannel.addEvent(epgEvent);
                    epgEvents.add(epgEvent);
                }
            }
            currentChannel.setNextChannel(firstChannel);
            firstChannel.setPreviousChannel(currentChannel);
            EPGData data = new EPGDataImpl(map);
            return data;
        } catch (Throwable ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }
}
