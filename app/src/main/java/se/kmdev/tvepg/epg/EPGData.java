package se.kmdev.tvepg.epg;

import java.util.List;

import se.kmdev.tvepg.epg.domain.EPGChannel;
import se.kmdev.tvepg.epg.domain.EPGEvent;

/**
 * Interface to implement and pass to EPG containing data to be used.
 * Implementation can be a simple as simple as a Map/List or maybe an Adapter.
 * Created by Kristoffer on 15-05-23.
 */
public interface EPGData {

    EPGChannel getChannel(int position);

    /**
     * Get or create a channel with the given name
     * @param channelName
     * @return
     */
    EPGChannel getOrCreateChannel(String channelName);

    EPGChannel addNewChannel(String channelName);

    List<EPGEvent> getEvents(int channelPosition);

    EPGEvent getEvent(int channelPosition, int programPosition);

    int getChannelCount();

    boolean hasData();

}
