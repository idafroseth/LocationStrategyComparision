package blog.appitude.locationcomparator.google;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import blog.appitude.locationcomparator.common.IMyLocationConsumer;

/**
 * Created by Zelus on 22/02/2017.
 */

public class GoogleLocationController implements LocationListener {


    private int count = 0;
    private long updateFrequency = 0;
    private IMyLocationConsumer consumer;
    private GoogleApiClient mGoogleApiClient;
    private int priority;

    public GoogleLocationController(IMyLocationConsumer consumer, GoogleApiClient mGoogleApiClient, int priority) {
        this.consumer = consumer;
        this.mGoogleApiClient = mGoogleApiClient;
        this.priority = priority;
    }

    public void startLocationProvider() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(updateFrequency);
        mLocationRequest.setPriority(priority);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    public void stopLocationProvider(){
        if(count > 5) {
            return;
        }
        if(mGoogleApiClient != null) {
            if (mGoogleApiClient.isConnected()) {
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                count = 0;
            }
            else{
                count++;
                stopLocationProvider();
            }
        }
    }

    public void setUpdateFrequency(long updateFrequency){
        this.updateFrequency = updateFrequency;
    }

    @Override
    public void onLocationChanged(Location location) {
        consumer.onLocationChanged(location, null);
    }
}
