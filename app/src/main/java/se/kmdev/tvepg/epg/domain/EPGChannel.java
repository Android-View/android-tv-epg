package se.kmdev.tvepg.epg.domain;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Created by Kristoffer.
 */
public class EPGChannel {

    private final int channelID;
    private final String name;
    private final String imageURL;

    private List<EPGEvent> events = Lists.newArrayList();
    private EPGChannel previousChannel;
    private EPGChannel nextChannel;

    public EPGChannel(String imageURL, String name, int channelID) {
        this.imageURL = imageURL;
        this.name = name;
        this.channelID = channelID;
    }

    public int getChannelID() {
        return channelID;
    }

    public String getName() {
        return name;
    }

    public String getImageURL() {
        return imageURL;
    }

    public List<EPGEvent> getEvents() {
        return events;
    }

    public EPGChannel getPreviousChannel() {
        return previousChannel;
    }

    public void setPreviousChannel(EPGChannel previousChannel) {
        this.previousChannel = previousChannel;
    }

    public EPGChannel getNextChannel() {
        return nextChannel;
    }

    public void setNextChannel(EPGChannel nextChannel) {
        this.nextChannel = nextChannel;
    }

    public EPGEvent addEvent(EPGEvent event) {
        this.events.add(event);
        return event;
    }
}
