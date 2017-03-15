package blog.appitude.locationcomparator.caged_new.controller;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.location.Location;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import blog.appitude.locationcomparator.caged_new.entity.UserActivityState;
import blog.appitude.locationcomparator.caged_new.listener.ActivityChangeListener;
import blog.appitude.locationcomparator.common.IMyLocationProvider;
import blog.appitude.locationcomparator.common.IMyLocationConsumer;

/*
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
*/

/**
 * Created by Ida Marie Fr&oslash;seth  on 02/02/2017.
 */

public class ActivityMonitor extends TriggerEventListener implements IMyLocationConsumer {


    private static final String LOG_TAG = "ActivityMonitor";
    private UserActivityState currentState = UserActivityState.USER_MOVING_STATE;
    private static final Integer STATIONARY_THRESHOLD_SECONDS = 120;
    private static final Integer STATIONARY_THRESHOLD_MILI = STATIONARY_THRESHOLD_SECONDS * 1000;

    private SensorManager mSensorManager;

    private ActivityChangeListener listener;

    private Handler handler = new Handler();

    private boolean isListeningForStationaryState = false;
    private boolean isListeningForMovingState = false;

    private int nullLocationObjectCounter = 0;

    private Long lastLocationWithSpeedTimestampMili = 0L;
    private Long lastLocationTimestampMili = 0L;

    private Runnable stationaryStateMonitor = new Runnable() {
        @Override
        public void run() {
            //Only listen for updates every two minutes
            Log.d(LOG_TAG, "Checking if user is stationary");

            boolean result = false;

            if(lastLocationTimestampMili == 0L){
                nullLocationObjectCounter ++;
            }
            else if ((SystemClock.elapsedRealtime() - lastLocationTimestampMili) > STATIONARY_THRESHOLD_MILI) {
                Log.d(LOG_TAG, "We have not got a new location the last two minutes = user stationary");
                result = true;
            }
            else if((lastLocationWithSpeedTimestampMili > 0L) && ((SystemClock.elapsedRealtime() - lastLocationWithSpeedTimestampMili) > STATIONARY_THRESHOLD_MILI)){
                Log.d(LOG_TAG, "It is more than two minutes since the user had speed");
                result = true;
            }

            //if the result is null or we have not got a new location the last two minutes
            if(result || nullLocationObjectCounter > 2){
                isListeningForMovingState = false;
                currentState = UserActivityState.USER_STATIONARY_STATE;
                Log.d(LOG_TAG , "LISTENER IS NULL?? "+ listener + "");
                if(listener != null) {
                    resetCounters();
                    listener.onActivityChanged(UserActivityState.USER_STATIONARY_STATE);
                    stopStationaryStateMonitor();
                }
            }

            if(isListeningForStationaryState) {
                handler.postDelayed(stationaryStateMonitor, STATIONARY_THRESHOLD_MILI);
            }
        }
    };


    public ActivityMonitor(SensorManager sensorManager, ActivityChangeListener listener){
        Log.d(LOG_TAG, "Activity monitor created");
        if(sensorManager == null) {
            throw new RuntimeException("SensorManager NULL!!");
        }
        this.listener = listener;
        this.mSensorManager = sensorManager;

    }

    public boolean startMonitor(UserActivityState state){
        stopMonitors();
        switch (state){
            case USER_MOVING_STATE:

                return startActiveStateMonitor();
            case USER_STATIONARY_STATE:
                //Start a loop which checks if user is moving by inspecting the last user activities..
                return startStationaryStateMonitor();
            default:
                return false;
        }
    }

    @Override
    public void onTrigger(TriggerEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_SIGNIFICANT_MOTION) {
            isListeningForMovingState = false;
            currentState = UserActivityState.USER_MOVING_STATE;
            if(listener != null) {
                listener.onActivityChanged(UserActivityState.USER_MOVING_STATE);
                resetCounters();
            }
        }

    }

    private void resetCounters(){
        lastLocationWithSpeedTimestampMili = 0L;
        lastLocationTimestampMili = 0L;
        nullLocationObjectCounter = 0;
    }

    /////////IMyLocationConsumer methods///////////
    @Override
    public void onLocationChanged(Location location, IMyLocationProvider source) {
        if(location != null){
            nullLocationObjectCounter = 0;
            lastLocationTimestampMili = SystemClock.elapsedRealtime();

            Log.d(LOG_TAG, "Checking if locaiton has speed. The provider is: " + location.getProvider() + " while the speed is " +location.getSpeed() + " location.hasSpeed()? " + location.hasSpeed());
            if(location.hasSpeed()){
                if(location.getSpeed()>0.0f) {
                    lastLocationWithSpeedTimestampMili = SystemClock.elapsedRealtime();
                }
            }else{
                //If the last location did not have any speed we cannot be sure whether the user is moving or not.
                lastLocationWithSpeedTimestampMili = 0L;
            }
        }
    }
    /////////////////////////////////////////////



    //////////MOTION SENSOR METHODS ////////
    /**
     * Significant motion sensor
     */
    public boolean sensorExists(Integer sensorType){
        if(getSensorManager() != null){
            Log.d(LOG_TAG,"If this is null significant motion does not exists.. " +  getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER) );
            return getSensorManager().getDefaultSensor(sensorType) != null;
        }
        return false;
    }

    public void stopMonitors(){
        stopStationaryStateMonitor();
        stopActiveStateMonitor();
    }


    private boolean startStationaryStateMonitor(){
        if(!isListeningForStationaryState) {
            //Initializing the checkForUserMovement runnable
            handler.postDelayed(stationaryStateMonitor, STATIONARY_THRESHOLD_MILI);
            isListeningForStationaryState = true;
        }
        return isListeningForStationaryState;
    }


    private void stopStationaryStateMonitor(){
        if(handler!=null) {
            handler.removeCallbacks(stationaryStateMonitor);
        }
        isListeningForStationaryState = false;
    }


    private boolean startActiveStateMonitor(){
        if(!isListeningForMovingState) {
            if (sensorExists(Sensor.TYPE_SIGNIFICANT_MOTION)) {
                isListeningForMovingState = true;
                Sensor significantMotionSensor = getSensorManager().getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
                getSensorManager().requestTriggerSensor(this, significantMotionSensor);

            }
        }
        return isListeningForMovingState;
    }

    private void stopActiveStateMonitor() {
        if (sensorExists(Sensor.TYPE_SIGNIFICANT_MOTION)) {

            Sensor significantMotionSensor = getSensorManager().getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
            getSensorManager().cancelTriggerSensor(this, significantMotionSensor);
        }
        isListeningForMovingState = false;
    }

    public void destroy(){
        this.mSensorManager = null;
        this.listener = null;
        this.handler = null;
    }
    ////////////////////////////////////////////

    public UserActivityState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(UserActivityState currentState) {
        this.currentState = currentState;
    }


    public SensorManager getSensorManager(){
        return mSensorManager;
    }

    public void setSensorManager(SensorManager sensorManager){
        this.mSensorManager = sensorManager;
    }

}
