package org.entermediadb.videoedit;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openedit.page.Page;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

public class VideoEditor
{
	private static final Log log = LogFactory.getLog(VideoEditor.class);

	public void split(Page inVideo, double inStartTime, double inStopTime, OutputStream inOutput) throws IOException
	{
			File videofile = new File(inVideo.getContentItem().getAbsolutePath());
			Movie movie = MovieCreator.build(new FileDataSourceImpl(videofile));
			//Movie movie = new MovieCreator().build(new IsoBufferWrapperImpl(videofile));

	        List<Track> tracks = movie.getTracks();
	        movie.setTracks(new LinkedList<Track>());
	        
	        boolean timeCorrected = false;

	        // Here we try to find a track that has sync samples. Since we can only start decoding
	        // at such a sample we SHOULD make sure that the start of the new fragment is exactly
	        // such a frame
	        for (Track track : tracks) {
	            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
	                if (timeCorrected) {
	                    // This exception here could be a false positive in case we have multiple tracks
	                    // with sync samples at exactly the same positions. E.g. a single movie containing
	                    // multiple qualities of the same video (Microsoft Smooth Streaming file)

	                    throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
	                }
	                inStartTime = correctTimeToSyncSample(track, inStartTime, false);
	                timeCorrected = true;
	            }
	        }
	        
	        //Now the ending
	        if( inStopTime > -1)
	        {
		        timeCorrected = false;
		        for (Track track : tracks) 
		        {
		            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) 
		            {
		                if (timeCorrected) {
		                    // This exception here could be a false positive in case we have multiple tracks
		                    // with sync samples at exactly the same positions. E.g. a single movie containing
		                    // multiple qualities of the same video (Microsoft Smooth Streaming file)
	
		                    throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
		                }
		                inStopTime = correctTimeToSyncSample(track, inStartTime, inStopTime, true);
		                timeCorrected = true;
		            }
		        }
	        } 
	        
	        for (Track track : tracks)
	        {
	            long currentSample = 0;
	            double currentTime = 0;
	            long startSample = -1;
	            long endSample = -1;

	            //https://github.com/sannies/mp4parser/blob/master/examples/src/main/java/com/ChopSuey.java
	            //Get the start time
		        for (int i = 0; i < track.getSampleDurations().length; i++) 
		        {
		            long delta = track.getSampleDurations()[i];
	                //find the first sample
	                    // entry.getDelta() is the amount of time the current sample covers.

	                    if (currentTime <= inStartTime) {
	                        // current sample is still before the new starttime
	                        startSample = currentSample;
	                    } else {
	                        // current sample is after the end of the cropped video
	                        break;
	                    }
	                    currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
	                    currentSample++;
	             }
	            if( inStopTime > -1 )
	            {
		            currentSample = 0;
		            currentTime = 0;

		            //Match up the end time
			        for (int i = 0; i < track.getSampleDurations().length; i++) 
			        {
			            long delta = track.getSampleDurations()[i];
		                //find the first sample
	                    currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
	                    endSample = currentSample;
	                    currentSample++;
	                    // entry.getDelta() is the amount of time the current sample covers.
	                    //log.info("Checking " + track.getType() + " checking " + currentTime + ">=" + inStopTime);
	                    if (currentTime >= inStopTime) //past the end
	                    {
                    		//we are good Stop looking 
	                    	log.info("Checking " + track.getName() + " checking " + currentTime + ">=" + inStopTime);
	                    	break;
	                    }
		            }
	            } 
	            else
	            {
	            	//make the end sample the last sample
	            	endSample = track.getSamples().size();
	            }	
	            //log.info("Added track " + track.getType() + " " + startSample + " " +  endSample);
	            movie.addTrack(new CroppedTrack(track, startSample, endSample));
	        }
	        
	        Container out = new DefaultMp4Builder().build(movie);
	        //MovieHeaderBox mvhd = Path.getPath(out, "moov/mvhd");
	        //mvhd.setMatrix(Matrix.ROTATE_180);
	        WritableByteChannel channel = Channels.newChannel(inOutput);
	        out.writeContainer(channel);
	       // channel.close();
	       // inOutput.flush();
	        //inOutput.close();
		}

	    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
	        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
	        long currentSample = 0;
	        double currentTime = 0;
	        for (int i = 0; i < track.getSampleDurations().length; i++) {
	            long delta = track.getSampleDurations()[i];
	            if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
	                // samples always start with 1 but we start with zero therefore +1
	                timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
	            }
	            currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
	            currentSample++;
	        }
	        double previous = 0;
	        for (double timeOfSyncSample : timeOfSyncSamples) {
	            if (timeOfSyncSample > cutHere) {
	                if (next) {
	                    return timeOfSyncSample;
	                } else {
	                    return previous;
	                }
	            }
	            previous = timeOfSyncSample;
	        }
	        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
	    }
	    
	    
	    private static double correctTimeToSyncSample(Track track, double start, double cutHere, boolean next) 
	    {
	        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
	        long currentSample = 0;
	        double currentTime = 0;
	        for (int i = 0; i < track.getSampleDurations().length; i++) {
	            long delta = track.getSampleDurations()[i];
	            if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
	                // samples always start with 1 but we start with zero therefore +1
	                timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
	            }
	            currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
	            currentSample++;
	        }
	        double previous = 0;
	        for (double timeOfSyncSample : timeOfSyncSamples) {
	            if (timeOfSyncSample > cutHere && timeOfSyncSample > start) {  //check start
	                if (next) {
	                    return timeOfSyncSample;
	                } else {
	                    return previous;
	                }
	            }
	            previous = timeOfSyncSample;
	        }
	        return -1; //end of file
	    }
	

}
