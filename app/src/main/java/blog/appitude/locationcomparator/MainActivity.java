package blog.appitude.locationcomparator;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

import blog.appitude.locationcomparator.caged_new.controller.LocationController;
import blog.appitude.locationcomparator.caged_old.GpsLocationController;
import blog.appitude.locationcomparator.common.IMyLocationConsumer;
import blog.appitude.locationcomparator.common.IMyLocationProvider;
import blog.appitude.locationcomparator.google.GoogleLocationController;

public class MainActivity extends AppCompatActivity implements IMyLocationConsumer, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    static String LOG_TAG = "MainActivity";

    LocationController newController;
    GpsLocationController oldController;
    GoogleLocationController googleHighAccuracyController;
    GoogleLocationController googlePowerBalancedController;
    GoogleLocationController googleBaselineController;
    BatteryManager mBatteryManager;
    Gson gson;
    GoogleApiClient mGoogleApiClient;

    Handler batterySpyHandler;

    View startButtonContainer;
    Button startNewControllerButton;
    Button startOldControllerButton;
    Button startHighAccuracyButton;
    Button startPowerBalancedButton;
    Button startBaselineButton;

    Button stopButton;
    Button timestampButton;

    TextView powerOutput;
    TextView feedbackText;

    Integer counter = 0;

    String activeStrategy = "<trying to connect to play services!>";

    private Runnable batteryStatusTask = new Runnable() {
        @Override
        public void run() {
            printBatteryStatus();
            batterySpyHandler.postDelayed(this, 1000);          // reschedule the handler
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestApplicationPermissions();
        setContentView(R.layout.activity_main);

        gson = new Gson();
        buildGoogleApiClient();
        mGoogleApiClient.connect();

        feedbackText = (TextView) findViewById(R.id.text_feedback);
        feedbackText.setText(activeStrategy);
        powerOutput = (TextView) findViewById(R.id.text_power);

        mBatteryManager = (BatteryManager)getSystemService(BATTERY_SERVICE);

        startButtonContainer = findViewById(R.id.btn_view);
        stopButton = (Button) findViewById(R.id.btn_stop);
        timestampButton = (Button) findViewById(R.id.btn_timestamp);

        stopButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                onStopButtonClicked(v);
            }
        });
        timestampButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendLog("Timestamp: " + SystemClock.elapsedRealtimeNanos());
            }
        });
        batterySpyHandler = new Handler();
        batterySpyHandler.postDelayed(batteryStatusTask, 1000);
        printBatteryStatus();

    }

    private String getBatteryString(){
        int currentAvgInMiliAmperes = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        int currentNowInMiliAmperes = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        int capacityPrecentage = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        int capacityMah = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        long energyLeft = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
        return
                "Current Avg: \t" + currentAvgInMiliAmperes + "mA\n" +
                "Current Now: \t" + currentNowInMiliAmperes + "mA\n" +
                "Battery left: \t" + capacityPrecentage + "%\n" +
                "Battery mah: \t" + capacityMah + "mAh\n" +
                "Energy left: \t" + energyLeft + "nWh";
    }
    private void printBatteryStatus(){
        powerOutput.setText(getBatteryString());
    }

    private String getDtgString(){
        Calendar c = Calendar.getInstance();
        int hours = c.get(Calendar.HOUR);
        int min = c.get(Calendar.MINUTE);
        int seconds = c.get(Calendar.SECOND);
        int ms = c.get(Calendar.MILLISECOND);
        return hours+ ":"+min+ ":"+seconds + "." +ms;
    }
    private void writeBatteryStatusToLog(String status){
        String batteryString = "BatteryConsumption on "+ status +" \n"+
                "DTG: " + getDtgString()+ "\n"+
                getBatteryString() +"\n";
        appendLog(batteryString);
    }

    private void buildGoogleApiClient(){
        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    private void initButtons(){
        View.OnClickListener clickListener = new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                onStartButtonClicked(v);
            }
        };

        startNewControllerButton = (Button) findViewById(R.id.btn_geo_new);
        startNewControllerButton.setOnClickListener(clickListener);

        startOldControllerButton = (Button) findViewById(R.id.btn_geo_old);
        startOldControllerButton.setOnClickListener(clickListener);

        startPowerBalancedButton = (Button) findViewById(R.id.btn_geo_google_balance);
        startPowerBalancedButton.setOnClickListener(clickListener);

       /* startHighAccuracyButton = (Button) findViewById(R.id.btn_geo_accurate);
        startHighAccuracyButton.setOnClickListener(clickListener);*/

        startBaselineButton = (Button) findViewById(R.id.btn_geo_baseline);
        startBaselineButton.setOnClickListener(clickListener);
    }

    private void onStartButtonClicked(View v){
        String activeState ="<could not start>";
        cancelAndKillAllLocationUpdates();
        removePressedStateFromAllButtons();

        switch (v.getId()){
            case R.id.btn_geo_new:
                startNewStrategy();
                activeState = "New Strategy";
                break;
            case R.id.btn_geo_old:
                startOldStrategy();
                activeState = "Old Strategy";
                break;
            case R.id.btn_geo_google_balance:
                startGooglePowerBalancedStrategy();
                activeState = "Google balanced";
                break;
            /*case R.id.btn_geo_accurate:
                startGoogleAccurateStrategy();
                activeState = "Google accurate";
                break;*/
            case R.id.btn_geo_baseline:
                startGoogleBaselineStrategy();
                activeState = "Google baseline";
                break;
        }
        startButtonContainer.setVisibility(View.GONE);
        stopButton.setVisibility(View.VISIBLE);
        setAndPrintActiveState(activeState);
        writeBatteryStatusToLog("Start");

    }

    public void onStopButtonClicked(View v){
        cancelAndKillAllLocationUpdates();
        startButtonContainer.setVisibility(View.VISIBLE);
        v.setVisibility(View.GONE);
        writeBatteryStatusToLog("Stop");
        setAndPrintActiveState("<Ready>");

    }
    private void setAndPrintActiveState(String state){

        activeStrategy=state;
        feedbackText.setText("Active: " + activeStrategy);
    }

    private void removePressedStateFromAllButtons(){
        startNewControllerButton.setPressed(false);
        startOldControllerButton.setPressed(false);
//        startHighAccuracyButton.setPressed(false);
        startPowerBalancedButton.setPressed(false);
    }

    @Override
    protected void onDestroy(){
        if(mGoogleApiClient!=null) {
            mGoogleApiClient.disconnect();
        }
        super.onDestroy();
        cancelAndKillAllLocationUpdates();
        batterySpyHandler.removeCallbacks(batteryStatusTask);
    }

    @Override
    public void onResume() {
        super.onResume();
        feedbackText.setText(activeStrategy);
       // initEagerUpdates();
    }

    /**
     * Should be run before choosing a location provider
     */
    private void cancelAndKillAllLocationUpdates(){
        if(newController != null){
            newController.stopLocationUpdates();
            newController.kill();
            newController = null;
        }
        if (oldController != null){
            oldController.stopLocationProvider();
            oldController.destroy();
            oldController = null;
        }
        if (googleHighAccuracyController != null){
            googleHighAccuracyController.stopLocationProvider();
            googleHighAccuracyController = null;
        }
        if (googlePowerBalancedController != null){
            googlePowerBalancedController.stopLocationProvider();
            googlePowerBalancedController = null;
        }
        if (googleBaselineController != null){
            googleBaselineController.stopLocationProvider();
            googleBaselineController = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
      //  initLazyUpdates();
    }

    private void initLazyUpdates() {
        float LOCATION_UPDATE_MIN_DISTANCE = 3f;
        long GPS_LOCATION_UPDATES_IN_BACKGROUND = 25000;

        if(newController != null){
            newController.enableLazyUpdates();
        }
        if (oldController != null){
            oldController.stopLocationProvider();
            oldController.setLocationUpdateMinTime(GPS_LOCATION_UPDATES_IN_BACKGROUND);
            oldController.setLocationUpdateMinDistance(LOCATION_UPDATE_MIN_DISTANCE);
            oldController.startLocationProvider(this);
        }
        if (googleHighAccuracyController != null){
            googleHighAccuracyController.stopLocationProvider();
            googleHighAccuracyController.setUpdateFrequency(GPS_LOCATION_UPDATES_IN_BACKGROUND);
            googleHighAccuracyController.startLocationProvider();
        }
        if (googlePowerBalancedController != null){
            googlePowerBalancedController.stopLocationProvider();
            googlePowerBalancedController.setUpdateFrequency(GPS_LOCATION_UPDATES_IN_BACKGROUND);
            googlePowerBalancedController.startLocationProvider();
        }
        if (googleBaselineController !=  null){
            //Do Nothing
        }
    }

    private void initEagerUpdates(){
        float LOCATION_UPDATE_MIN_DISTANCE = 3f;
        long MIN_GPS_LOCATION_UPDATES_MILLIS = 3000;
        if(newController != null){
            Log.d(LOG_TAG, "InitEager/newController");
            newController.enableEagerUpdates();
        }
        if (oldController != null){
            Log.d(LOG_TAG, "InitEager/oldController");
            oldController.stopLocationProvider();
            oldController.setLocationUpdateMinTime(MIN_GPS_LOCATION_UPDATES_MILLIS);
            oldController.setLocationUpdateMinDistance(LOCATION_UPDATE_MIN_DISTANCE);
            oldController.startLocationProvider(this);
        }
        if (googleHighAccuracyController != null){
            Log.d(LOG_TAG, "InitEager/powerBalanced");
            googleHighAccuracyController.stopLocationProvider();
            //Set the same as the new Controller
            googleHighAccuracyController.setUpdateFrequency(1000);
            googleHighAccuracyController.startLocationProvider();
        }
        if (googlePowerBalancedController != null){
            Log.d(LOG_TAG, "InitEager/powerBalanced");
            googlePowerBalancedController.stopLocationProvider();
            googlePowerBalancedController.setUpdateFrequency(MIN_GPS_LOCATION_UPDATES_MILLIS);
            googlePowerBalancedController.startLocationProvider();
        }
        if (googleBaselineController != null) {
            Log.d(LOG_TAG, "InitEager/googleBaseline");
            //Do nothing
        }
    }

    @Override
    public void onLocationChanged(Location location, IMyLocationProvider source) {
        Log.i("onLocationChanged", gson.toJson(location));
        appendLog(gson.toJson(location));
    }

    private void startNewStrategy(){
        newController = LocationController.getInstance(this);
        newController.addLocationConsumer(this);
        newController.startLocationUpdates();
    }
    private void startOldStrategy(){
        //Crate LocationController
        blog.appitude.locationcomparator.caged_old.LocationController listener = new blog.appitude.locationcomparator.caged_old.LocationController(this);

        if(oldController == null){
            oldController = new GpsLocationController(this);
        }
        initEagerUpdates();
    }

    private void startGooglePowerBalancedStrategy(){
        if(googlePowerBalancedController ==null){
            googlePowerBalancedController = new GoogleLocationController(this,mGoogleApiClient,LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        }
        initEagerUpdates();
    }
    private void startGoogleAccurateStrategy(){
        if(googleHighAccuracyController == null){
            googleHighAccuracyController = new GoogleLocationController(this,mGoogleApiClient, LocationRequest.PRIORITY_HIGH_ACCURACY);

        }
        initEagerUpdates();
    }

    private void startGoogleBaselineStrategy(){
        if(googleBaselineController == null){
            googleBaselineController = new GoogleLocationController(this,mGoogleApiClient, LocationRequest.PRIORITY_HIGH_ACCURACY);
        }
        googleBaselineController.startLocationProvider();
    }


    public void requestApplicationPermissions() {
        ArrayList<String> arrayList = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            arrayList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            arrayList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            arrayList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (arrayList.size() > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                super.requestPermissions(arrayList.toArray(new String[arrayList.size()]), 10);
            }else{
                throw new RuntimeException("Cannot request permissions");
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        initButtons();
        setAndPrintActiveState("<Ready>");
    }

    @Override
    public void onConnectionSuspended(int i) {
        setAndPrintActiveState("<Suspended..>");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        setAndPrintActiveState("<ConnectionToGoogleServicesFailed..>" +connectionResult.getErrorCode());
        initButtons();
    }

    /**
     * From stackoverflow: http://stackoverflow.com/questions/1756296/android-writing-logs-to-text-file
     */
    public void appendLog(String text)
    {
        File logFile = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), activeStrategy+".log");

        if (!logFile.exists()){
            try {
                logFile.createNewFile();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e){
            e.printStackTrace();
        }
        finally{

        }
    }
}
