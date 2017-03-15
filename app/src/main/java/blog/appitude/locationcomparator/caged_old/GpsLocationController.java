package blog.appitude.locationcomparator.caged_old;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import blog.appitude.locationcomparator.common.IMyLocationConsumer;
import blog.appitude.locationcomparator.common.IMyLocationProvider;
import blog.appitude.locationcomparator.common.NetworkLocationIgnorer;

public class GpsLocationController implements IMyLocationProvider, LocationListener {

    private static final String LOG_TAG = "GpsLocController";
    private final LocationManager mLocationManager;
    private Location mLocation;

    private IMyLocationConsumer mMyLocationConsumer;
    private long mLocationUpdateMinTime;
    private float mLocationUpdateMinDistance;
    private final NetworkLocationIgnorer mIgnorer = new NetworkLocationIgnorer();

    public GpsLocationController(Context context) {
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    public long getLocationUpdateMinTime() {
        return mLocationUpdateMinTime;
    }

    public LocationManager getmLocationManager() {
        return mLocationManager;
    }

    /**
     * Set the minimum interval for location updates. See
     * {@link LocationManager#requestLocationUpdates(String, long, float, LocationListener)}. Note
     * that you should call this before calling .
     *
     * @param milliSeconds
     */
    public void setLocationUpdateMinTime(final long milliSeconds) {
        mLocationUpdateMinTime = milliSeconds;
    }

    public float getLocationUpdateMinDistance() {
        return mLocationUpdateMinDistance;
    }

    /**
     * Set the minimum distance for location updates. See
     * {@link LocationManager#requestLocationUpdates(String, long, float, LocationListener)}. Note
     * that you should call this before calling
     *
     * @param meters
     */
    public void setLocationUpdateMinDistance(final float meters) {
        mLocationUpdateMinDistance = meters;
    }

    //
    // IMyLocationProvider
    //

    /**
     * Enable location updates and show your current location on the map. By default this will
     * request location updates as frequently as possible, but you can change the frequency and/or
     * distance by calling other methods in this class.
     */
    @Override
    public boolean startLocationProvider(IMyLocationConsumer myLocationConsumer) {
        mMyLocationConsumer = myLocationConsumer;
        boolean result = false;
        for (final String provider : mLocationManager.getProviders(true)) {
            if (LocationManager.GPS_PROVIDER.equals(provider)
                    || LocationManager.NETWORK_PROVIDER.equals(provider)) {
                result = true;
                mLocationManager.requestLocationUpdates(provider, mLocationUpdateMinTime, mLocationUpdateMinDistance, this);
            }
        }
        return result;
    }

    @Override
    public void stopLocationProvider() {
        mMyLocationConsumer = null;
        mLocationManager.removeUpdates(this);
    }

    @Override
    public Location getLastKnownLocation() {
        return mLocation;
    }

    @Override
    public void destroy() {

    }

    public Location getLastKnownLocationFromManager() {

        if (mLocation != null) {
            return mLocation;
        } else {
            Location locationGps = mLocationManager.getLastKnownLocation("gps");
            Location locationNetwork = mLocationManager.getLastKnownLocation("gps");

            if (locationGps != null && locationNetwork != null) {
                if (locationGps.getAccuracy() > locationNetwork.getAccuracy()) {
                    mLocation = locationNetwork;
                    mLocation = locationNetwork;
                    return locationNetwork;
                } else if (locationNetwork.getAccuracy() > locationGps.getAccuracy()) {
                    mLocation = locationGps;
                    return locationGps;
                }
            } else if (locationGps == null && locationNetwork != null) {
                mLocation = locationNetwork;
                return locationNetwork;
            }
            mLocation = locationGps;
            return locationGps;
        }
    }

    //
    // LocationListener
    //

    @Override
    public void onLocationChanged(final Location location) {
        // If there are multiple location providers,
        // i.e. network and GPS, then you want to ignore network locations shortly after a GPS location
        // because you will get another GPS location soon.
        if (mIgnorer.shouldIgnore(location.getProvider(), System.currentTimeMillis()))
            return;

        mLocation = location;
        if (mMyLocationConsumer != null)
            mMyLocationConsumer.onLocationChanged(mLocation, this);
    }

    @Override
    public void onProviderDisabled(final String provider) {
    }

    @Override
    public void onProviderEnabled(final String provider) {
    }

    @Override
    public void onStatusChanged(final String provider, final int status, final Bundle extras) {
    }
}
