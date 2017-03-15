package blog.appitude.locationcomparator.caged_new.listener;

import android.location.Location;

import blog.appitude.locationcomparator.common.IMyLocationConsumer;
import blog.appitude.locationcomparator.common.IMyLocationProvider;

/**
 * Created by Ida Marie Frøseth on 30/01/2017.
 */

public interface ILocationProvider extends IMyLocationProvider {


    /**
     * Get the last known location from whatever provider available.
     * @return the last known location or null if not available
     */
    public abstract Location getLastKnownLocation();


    public abstract void enableLazyUpdates();
    public abstract void enableEagerUpdates();
    public abstract void addLocationConsumer(IMyLocationConsumer consumer);
    public abstract void removeLocationConsumer(IMyLocationConsumer consumer);



}
