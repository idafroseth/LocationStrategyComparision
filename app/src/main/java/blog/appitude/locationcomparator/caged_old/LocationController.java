package blog.appitude.locationcomparator.caged_old;

import android.location.Location;
import android.location.LocationListener;
import android.util.Log;

import blog.appitude.locationcomparator.common.IMyLocationConsumer;
import blog.appitude.locationcomparator.common.IMyLocationProvider;

/**
 * Created by Ida Marie Frøseth on 22/02/2017.
 * This class contains the methods used in different CAGED classes
 */

public class LocationController implements IMyLocationConsumer {
    private static final int ACCURACY_LIMIT = 500;
    private static String LOG_TAG = "LocationOverlay";
    private float lastAccuracy = 20000;

    private IMyLocationConsumer locationListener;

    public LocationController(IMyLocationConsumer locationListener){
        this.locationListener = locationListener;
    }

    @Override
    public void onLocationChanged(Location location, IMyLocationProvider source) {
        if (location != null && determineIfLocationIsBetter(location)) {
            if (lastAccuracy != 0f) {
                lastAccuracy = location.getAccuracy();
            }
            if (location.getAccuracy() > 10000f) {
                location.setAccuracy(9999f);
            }
            //pushLocationToServer(location.getLatitude(), location.getLongitude());

            //super.onLocationChanged(location, source);
            locationListener.onLocationChanged(location,null);
            Log.d(LOG_TAG, "Updated location to: " + location);
        }
    }
    private boolean determineIfLocationIsBetter(Location location) {
        return lastAccuracy + ACCURACY_LIMIT >= location.getAccuracy();
    }



}
