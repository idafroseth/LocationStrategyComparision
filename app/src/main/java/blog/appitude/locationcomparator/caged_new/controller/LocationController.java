package blog.appitude.locationcomparator.caged_new.controller;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import blog.appitude.locationcomparator.common.IMyLocationConsumer;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import blog.appitude.locationcomparator.caged_new.entity.UserActivityState;
import blog.appitude.locationcomparator.caged_new.listener.ActivityChangeListener;
import blog.appitude.locationcomparator.caged_new.listener.ILocationProvider;

/**
 * Created by Ida Marie Fr&oslash;seth on 30/01/2017.
 */
public class LocationController implements LocationListener, ILocationProvider, ActivityChangeListener {

    private static final String LOG_TAG = "LocationController";
    private static LocationController controllerSingleton = null;

    //Location listener values when EAGER updates we are listening every second or 2 meter change
    private static final Long NORMAL_UPDATE_FREQUENCY_MILI = 3000L;
    private static final float NORMAL_UPDATE_FREQUENCY_METER = 3f;
    private static final Long LAZY_UPDATE_FREQUENCY_MILI = 25000L;
    private static final float LAZY_UPDATE_FREQUENCY_METER = 10f;


    private static float MIN_UPDATE_DISTANCE_METER = NORMAL_UPDATE_FREQUENCY_METER;
    private static long MIN_UPDATE_FREQUENCY_MILI = NORMAL_UPDATE_FREQUENCY_MILI;

    //Decision algorithm constants
    private Long NEWER_THRESHOLD_SECONDS = 30L;
    private Long NEWER_THRESHOLD_NANO = NEWER_THRESHOLD_SECONDS*1000000000;
    private Long SIGNIFICANT_NEWER_THRESHOLD_NANO = 3*NEWER_THRESHOLD_SECONDS*1000000000;
    private Integer NEWER_ACCURACY_THRESHOLD_METER = 500;
    private Integer SIGNIFICANT_NEWER_ACCURACY_THRESHOLD_METER = 1000;
    private static final float BEARING_DRIFT = 10;
    private static final Integer ACCURACY_REQUIREMENT_METERS = 5;

    //StartListener status parameters
    public static final Integer STARTED_SUCCESS_STATUS_CODE = 1;
    public static final Integer STARTED_ERROR_USER_STATIONARY_STATUS_CODE = 2;
    public static final Integer STARTED_ERROR_NO_PROVIDER_AVAILABLE_STATUS_CODE = 3;

    //Instance variables
    private Context context;
    private LocationManager mLocationManager;
    private ActivityMonitor mActivityMonitor;

    private HashSet<IMyLocationConsumer> locationConsumers = new HashSet<IMyLocationConsumer>();

    private Location lastLocation;
    private boolean isStationary;
    private boolean locationUpdateStarted = false;
    private Integer stationaryCount = 0;

    private LinkedList<Location> lastLocationQueue = new LinkedList<Location>();

    private boolean gpsIsAvailable = false;
    private boolean networkIsAvailable = false;
    private String activeProvider = "";


    private float lastLocationAverageSpeed = 0.0f;
    private float currentAverageSpeed = 0.0f;


    private int locationCount = 0;
    private int locationConsumedCount = 0;
    ///// START SINGLETON INITIALIZATION/////

    /**
     * The LocationController is implemented as a singleton.
     * Use this static method to get a single instance.
     * @return locationController instance and initiate if it has not been done earlier
     */
    public static LocationController getInstance(Context context) {// throws ContextNullException {
        if(context == null){
            throw new RuntimeException("Context is null!");
        }
        if(controllerSingleton == null){
            controllerSingleton = new LocationController(context);
        }
        return controllerSingleton;
    }

    /** Private constructors to ensure only a single instance of this class **/
    public LocationController(Context context){
        setContext(context);
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        setActivityMonitor(new ActivityMonitor(sensorManager, this));
        addLocationConsumer(mActivityMonitor);

        //Only start monitoring if sensor exists
        if(mActivityMonitor.sensorExists(Sensor.TYPE_SIGNIFICANT_MOTION)) {
            mActivityMonitor.startMonitor(UserActivityState.USER_STATIONARY_STATE);
        }
        else{
            Log.d(LOG_TAG, "Significant motion sensor does not exists");
        }

    }

    ///// END SINGLETON INITALIZATION //////


    ////START implementation of LocationListener interface methods //////////
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(LOG_TAG, "Status changed: " + provider + " status: "+ status);
    }

    /**
     * Restart location updates if the GPS provider get enabled and we are not already listening for GPS updates.
     * @param provider name
     */
    @Override
    public void onProviderEnabled(String provider) {
        if(provider.equals(LocationManager.GPS_PROVIDER) && !activeProvider.equals(LocationManager.GPS_PROVIDER)) {
            restartLocationUpdates();
        }
    }

    /**
     * Restart the location updates if the active provider is disabled.
     * @param provider by name
     */
    @Override
    public void onProviderDisabled(String provider) {
        if(provider.equals(LocationManager.GPS_PROVIDER)) {
            restartLocationUpdates();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(LOG_TAG, "Source "+ location.getProvider() + " accuracy " + location.getAccuracy());
        if(mActivityMonitor.getCurrentState() == UserActivityState.USER_STATIONARY_STATE || location == null){
            return;
        }
        normalizeLocation(location);

        locationCount++;
        if(decisionAlgorithm(location)) {
            locationConsumedCount++;
            informLocationListeners(location);
        }
    }


    private void informLocationListeners(Location location){

        lastLocation = location;
        for(IMyLocationConsumer consumer : locationConsumers){
            consumer.onLocationChanged(lastLocation, this);
        }

    }


    private boolean decisionAlgorithm(Location location){

        //If this is the first location consume it
        if (lastLocation == null){
            return true;
        }

        float bearingFromLastLocation = lastLocation.bearingTo(location);

        if (!location.hasBearing()){
            location.setBearing(bearingFromLastLocation);
        }

        if(location.getAccuracy() <= ACCURACY_REQUIREMENT_METERS){
            return true;
        }
        //If the accuracy has improved consume it
        else if(location.getAccuracy() < lastLocation.getAccuracy()){
            return true;
        }

        //If the accuracy is not improved but is within the movement path
        else if(isProbableLocation(location, bearingFromLastLocation)){
            return true;
        }

        //If it is not within the movement path and the accuracy is not improved but the last location is very old
        else if (((location.getElapsedRealtimeNanos()-lastLocation.getElapsedRealtimeNanos()) > NEWER_THRESHOLD_NANO) && (location.getAccuracy() < NEWER_ACCURACY_THRESHOLD_METER)){
            return true;
        }else if ((location.getElapsedRealtimeNanos()-lastLocation.getElapsedRealtimeNanos()) > SIGNIFICANT_NEWER_THRESHOLD_NANO && location.getAccuracy() < SIGNIFICANT_NEWER_ACCURACY_THRESHOLD_METER ){
            return true;
        }
        return false;
    }

    private boolean isProbableLocation(Location location, float bearingFromLastLocation){
        bearingFromLastLocation = normalizeDegree(bearingFromLastLocation);
        float distanceBetweenLocations = lastLocation.distanceTo(location);
        long elapsedTimeInNanos = location.getElapsedRealtimeNanos()-lastLocation.getElapsedRealtimeNanos();
        long elapsedTimeInSeconds = elapsedTimeInNanos/1000000000;
        float calculatedDistanceTraveled = (lastLocationAverageSpeed * elapsedTimeInSeconds);
        ////     showToast("MovementPath? BearingFromLast "+bearingFromLastLocation + " lastBearing " +lastLocation.getBearing() + " distance " + distanceBetweenLocations + " estimated travel "+ calculatedDistanceTraveled);

        if(lastLocation.hasBearing()){
            //Is moving in the right direction?
            if((Math.abs(bearingFromLastLocation - lastLocation.getBearing())< BEARING_DRIFT) || (bearingFromLastLocation + lastLocation.getBearing())%360< BEARING_DRIFT ){
                if(distanceBetweenLocations <  (calculatedDistanceTraveled + lastLocation.getAccuracy()) ){
                    return true;
                }
            }
        }
        return false;

    }

    /**
     * Computes the average speed if the location does not have speed.
     * @param location
     */
    private void normalizeLocation(Location location){
        if(lastLocation != null && location != null){
            float distanceBetweenLocations = lastLocation.distanceTo(location);
            long elapsedTimeInNanos = location.getElapsedRealtimeNanos()-lastLocation.getElapsedRealtimeNanos();
            long elapsedTimeInSeconds = elapsedTimeInNanos/1000000000;
            float avgSpeed =  distanceBetweenLocations / elapsedTimeInSeconds;

            if (location.hasSpeed()){
                if(location.getSpeed() == 0.0f){
                    lastLocationAverageSpeed = avgSpeed;
                }else{
                    lastLocationAverageSpeed = location.getSpeed();
                }
            }else{
                lastLocationAverageSpeed  = avgSpeed;
            }
        }
    }

    /**
     * Normalize the degree to be in the range of 0-360degrees and removes negative degree
     * @param degree
     * @return
     */
    private float normalizeDegree(float degree) {
        if (degree >= 0.0f && degree <= 180.0f) {
            return degree;
        } else {
            return 180 + (180 + degree);
        }
    }

    ////END LocationListener interface methods //////////


    /**
     * Disable location updates when user goes into USER_STATIONARY_STATE and enable when goes into USER_MOVING_STATE
     * @param status
     */
    @Override
    public void onActivityChanged(UserActivityState status) {
        if(status != null){
            //showToast("Detected change in state: " + status.name());
            switch(status){
                case USER_MOVING_STATE:
                    isStationary = false;
                    startLocationUpdates();
                    mActivityMonitor.startMonitor(UserActivityState.USER_STATIONARY_STATE);
                    break;
                case USER_STATIONARY_STATE:
                    isStationary = true;
                    stopLocationUpdates();
                    mActivityMonitor.startMonitor(UserActivityState.USER_MOVING_STATE);
                    lastLocation.removeBearing();
                    informLocationListeners(lastLocation);
                    break;
            }
        }
    }



    ////START implementation of ILocationProvider interface methods //////////

    private void restartLocationUpdates(){
        stopLocationUpdates();
        startLocationUpdates();
    }

    /**
     * Disabling the updates will effect all the consumer of the locationControllerSingleton
     */
    public void stopLocationUpdates() {
        if( getLocationManager() != null){
            getLocationManager().removeUpdates(this);
        }
        activeProvider = "";
        locationUpdateStarted = false;
    }

    public Integer startLocationUpdates(){
        //If the location updates are already started it does not have to be started again.
        //Starting it twice will cause issues
        if(isLocationUpdatesStarted()) {
            return STARTED_SUCCESS_STATUS_CODE;
        }

        //If the user is stantionary it should not start again. It will be started when active state detected
        if(isStationary){
            return STARTED_ERROR_USER_STATIONARY_STATUS_CODE;
        }

        gpsIsAvailable = getLocationManager().isProviderEnabled(LocationManager.GPS_PROVIDER);
        networkIsAvailable = getLocationManager().isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        getLocationManager().requestLocationUpdates(LocationManager.NETWORK_PROVIDER,MIN_UPDATE_FREQUENCY_MILI, MIN_UPDATE_DISTANCE_METER,this);
        activeProvider = LocationManager.NETWORK_PROVIDER;

        getLocationManager().requestLocationUpdates(LocationManager.GPS_PROVIDER,MIN_UPDATE_FREQUENCY_MILI, MIN_UPDATE_DISTANCE_METER,this);
        activeProvider = LocationManager.GPS_PROVIDER;

        if(!gpsIsAvailable && !networkIsAvailable){
            activeProvider = "";
            return STARTED_ERROR_NO_PROVIDER_AVAILABLE_STATUS_CODE;
        }

        locationUpdateStarted = true;
        return STARTED_SUCCESS_STATUS_CODE;
    }

    private boolean isLocationUpdatesStarted(){
        return locationUpdateStarted;
    }

    @Override
    public void enableLazyUpdates(){
        MIN_UPDATE_FREQUENCY_MILI = LAZY_UPDATE_FREQUENCY_MILI;
        MIN_UPDATE_DISTANCE_METER = LAZY_UPDATE_FREQUENCY_METER;
        if(!isStationary) {
            restartLocationUpdates();
        }
    }

    @Override
    public void enableEagerUpdates(){
        MIN_UPDATE_FREQUENCY_MILI = NORMAL_UPDATE_FREQUENCY_MILI;
        MIN_UPDATE_DISTANCE_METER = NORMAL_UPDATE_FREQUENCY_METER;
        if(!isStationary) {
            restartLocationUpdates();
        }
    }


    /**
     * This method is deprecated. Use the startLocationUpdates or enableEagerUpdates instead
     * @param myLocationConsumer
     * @return
     */
    @Deprecated
    @Override
    public boolean startLocationProvider(IMyLocationConsumer myLocationConsumer) {
        // addLocationConsumer(myLocationConsumer);
        // Integer result = startLocationUpdates();
        /*switch (result){
            case 1:
                return true;
            default:
                return false;
        }*/
        return true;
    }

    @Override
    public void stopLocationProvider() {
        //stopLocationUpdates();
    }

    public void kill(){
        if(mActivityMonitor != null) {
            mActivityMonitor.stopMonitors();
            mActivityMonitor.destroy();
            mActivityMonitor = null;
        }
        controllerSingleton = null;
    }


    @Override
    public Location getLastKnownLocation() {
        if(lastLocation != null){
            return lastLocation;
        }
        Location locationGps =  getLocationManager().getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location locationNetwork =  getLocationManager().getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        //If both providers has a last known location choose the most trustworthy one
        // We have implemented when the network location is newer than 2 minutes than the
        // gps location or the network location has better accuracy consume the network location
        if(locationGps != null && locationNetwork != null){
            if(locationNetwork.getAccuracy()<locationGps.getAccuracy()){
                return locationNetwork;
            }
        }
        else if (locationGps == null && locationNetwork != null){
            return locationNetwork;
        }
        return locationGps;

    }

    @Override
    public void destroy() {
        Log.d(LOG_TAG, " DESTROYED!!");
        stopLocationProvider();
    }

    //////////////////GETTERS AND SETTERS/////////////////////////
    public HashSet<IMyLocationConsumer> getLocationConsumers () {
        return this.locationConsumers;
    }
    public void setLocationConsumers(Set<IMyLocationConsumer> consumers){
        this.locationConsumers = (HashSet<IMyLocationConsumer>) consumers;
    }

    @Override
    public void addLocationConsumer(IMyLocationConsumer consumer){
        if(locationConsumers == null){
            locationConsumers = new HashSet<IMyLocationConsumer>();
        }
        locationConsumers.add(consumer);
    }
    @Override
    public void removeLocationConsumer(IMyLocationConsumer consumer){
        if(locationConsumers == null){
            locationConsumers = new HashSet<IMyLocationConsumer>();
        }
        locationConsumers.remove(consumer);
    }

    public LocationManager getLocationManager() {
        if(mLocationManager == null && context != null ){
            // Acquire a reference to the system Location Manager is this safe in a singleton method?
            setLocationManager((LocationManager) context.getSystemService(Context.LOCATION_SERVICE));
        }
        return mLocationManager;
    }

    public void setLocationManager(LocationManager mLocationManager) {
        this.mLocationManager = mLocationManager;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setActivityMonitor(ActivityMonitor monitor){
        this.mActivityMonitor = monitor;
    }

    //////END GETTERS AND SETTERS////

}
