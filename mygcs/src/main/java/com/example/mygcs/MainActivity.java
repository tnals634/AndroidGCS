package com.example.mygcs;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();

    MapFragment mNaverMapFragment = null;
    private Drone drone;
    private ControlTower controlTower;
    private int droneType = Type.TYPE_UNKNOWN;
    private final Handler handler = new Handler();
    private int polylineCheck = 0;

    Marker marker = new Marker();

    private Spinner modeSelector;
    NaverMap naverMap;


    Context context = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);

        hidNavigationBar();

        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        FragmentManager fm = getSupportFragmentManager();
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            mNaverMapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mNaverMapFragment).commit();
        }

        this.modeSelector = (Spinner) findViewById(R.id.spinner);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((TextView)parent.getChildAt(0)).setTextColor(Color.WHITE);
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        mNaverMapFragment.getMapAsync(this);
    }

    private void hidNavigationBar(){
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;
        boolean isImmersiveModeEnabled =
                ((uiOptions | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) == uiOptions);
        if(isImmersiveModeEnabled){
            Log.d(TAG,"Turning immersive mode mode off.");
        }else {
            Log.d(TAG, "Turning immersive mode mode on.");
        }
        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);

    }

    protected void updateArmButton(){
        final State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        final Button armButton = (Button)findViewById(R.id.buttonTakeoff);

        armButton.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){

                if(vehicleState.isFlying()){
                    armButton.setText("LAND");
                } else if(vehicleState.isArmed()){
                    armButton.setText("TAKE_OFF");
                } else if(vehicleState.isConnected()){
                    armButton.setText("ARM");
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                //updateArmButton();
                updateMapTypeButton();
                break;

            case AttributeEvent.GPS_POSITION:
                updateDronePosition();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBattery();
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    public void updateMapTypeButton(){
        final Button mapType = (Button) findViewById(R.id.map_type);
        final Button mapBasic = (Button) findViewById(R.id.basic_map);
        final Button mapTerrain = (Button) findViewById(R.id.terrain_map);
        final Button mapSatellite = (Button) findViewById(R.id.satellite_map);

        final Button map_on= (Button) findViewById(R.id.intellectual_map_on);
        final Button map_off = (Button) findViewById(R.id.intellectual_map_off);

        final Button mapLock = (Button) findViewById(R.id.lock);
        final Button mapUnlock = (Button) findViewById(R.id.unlock);

        mapType.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){

                if(v.getId() == R.id.map_type){
                    mapBasic.setVisibility(v.VISIBLE);
                    mapTerrain.setVisibility(v.VISIBLE);
                    mapSatellite.setVisibility(v.VISIBLE);

                    map_on.setVisibility(v.GONE);
                    map_off.setVisibility(v.GONE);

                    mapLock.setVisibility(v.GONE);
                    mapUnlock.setVisibility(v.GONE);

                    if(mapType.getText() == mapBasic.getText()) {
                        mapBasic.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    }
                    else if(mapType.getText() == mapTerrain.getText()){
                        mapTerrain.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    }
                    else if(mapType.getText() == mapSatellite.getText()){
                        mapSatellite.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    }
                }
            }
        });
        mapBasic.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){

                if(v.getId() == R.id.basic_map){
                    mapType.setText("일반지도");
                    naverMap.setMapType(NaverMap.MapType.Basic);
                    mapBasic.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    mapTerrain.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button));
                    mapSatellite.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button));
                    mapBasic.setVisibility(v.GONE);
                    mapTerrain.setVisibility(v.GONE);
                    mapSatellite.setVisibility(v.GONE);
                }
            }
        });
        mapTerrain.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                if(v.getId() == R.id.terrain_map){
                    mapType.setText("지형도");
                    naverMap.setMapType(NaverMap.MapType.Terrain);
                    mapBasic.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                    mapTerrain.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    mapSatellite.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button));
                    mapBasic.setVisibility(v.GONE);
                    mapTerrain.setVisibility(v.GONE);
                    mapSatellite.setVisibility(v.GONE);

                }
            }
        });
        mapSatellite.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                if (v.getId( ) == R.id.satellite_map) {

                    mapType.setText("위성지도");
                    naverMap.setMapType(NaverMap.MapType.Satellite);
                    mapBasic.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                    mapTerrain.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button));
                    mapSatellite.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    mapBasic.setVisibility(v.GONE);
                    mapTerrain.setVisibility(v.GONE);
                    mapSatellite.setVisibility(v.GONE);

                }
            }
        });
    }

    public void updateMapLock(final LatLng latLng){

        final Button mapLocking = (Button) findViewById(R.id.locking);
        final Button mapLock = (Button) findViewById(R.id.lock);
        final Button mapUnlock = (Button) findViewById(R.id.unlock);

        final Button mapBasic = (Button) findViewById(R.id.basic_map);
        final Button mapTerrain = (Button) findViewById(R.id.terrain_map);
        final Button mapSatellite = (Button) findViewById(R.id.satellite_map);

        final Button map_on= (Button) findViewById(R.id.intellectual_map_on);
        final Button map_off = (Button) findViewById(R.id.intellectual_map_off);

        mapLocking.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                if(v.getId() == R.id.locking){
                    mapLock.setVisibility(v.VISIBLE);
                    mapUnlock.setVisibility(v.VISIBLE);

                    mapBasic.setVisibility(v.GONE);
                    mapTerrain.setVisibility(v.GONE);
                    mapSatellite.setVisibility(v.GONE);

                    map_on.setVisibility(v.GONE);
                    map_off.setVisibility(v.GONE);

                    if(mapLocking.getText() == mapLock.getText()){
                        mapLock.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    }
                    else if(mapLocking.getText() == mapUnlock.getText()){
                        mapUnlock.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    }
                }
            }
        });
        mapLock.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                if(v.getId() == R.id.lock){
                    mapLocking.setText("맵잠금");
                    CameraUpdate cameraupdate = CameraUpdate.scrollTo(latLng);
                    naverMap.moveCamera(cameraupdate); //찍히는 좌표마다 카메라가 따라다님
                    mapLock.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    mapUnlock.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button));
                    mapLock.setVisibility(v.GONE);
                    mapUnlock.setVisibility(v.GONE);
                }
            }
        });

        mapUnlock.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                if(v.getId() == R.id.unlock){
                    mapLocking.setText("맵이동");
                    mapLock.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button));
                    mapUnlock.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    mapLock.setVisibility(v.GONE);
                    mapUnlock.setVisibility(v.GONE);
                }
            }
        });
    }

    public void updateIntellectualMap(){

        final Button map_on= (Button) findViewById(R.id.intellectual_map_on);
        final Button map_off = (Button) findViewById(R.id.intellectual_map_off);
        final Button map_check = (Button) findViewById(R.id.intellectual_map);

        final Button mapLock = (Button) findViewById(R.id.lock);
        final Button mapUnlock = (Button) findViewById(R.id.unlock);

        final Button mapBasic = (Button) findViewById(R.id.basic_map);
        final Button mapTerrain = (Button) findViewById(R.id.terrain_map);
        final Button mapSatellite = (Button) findViewById(R.id.satellite_map);

        map_check.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                if(v.getId() == R.id.intellectual_map){
                    map_on.setVisibility(v.VISIBLE);
                    map_off.setVisibility(v.VISIBLE);

                    mapBasic.setVisibility(v.GONE);
                    mapTerrain.setVisibility(v.GONE);
                    mapSatellite.setVisibility(v.GONE);


                    mapLock.setVisibility(v.GONE);
                    mapUnlock.setVisibility(v.GONE);

                    if(map_check.getText() == map_off.getText()){
                        map_off.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    }
                    else if(map_check.getText() == map_on.getText()){
                        map_on.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    }
                }
            }
        });
        map_on.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                if(v.getId() == R.id.intellectual_map_on){
                    map_check.setText("지적도on");
                    naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                    map_on.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    map_off.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button));
                    map_on.setVisibility(v.GONE);
                    map_off.setVisibility(v.GONE);
                }
            }
        });

        map_off.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                if(v.getId() == R.id.intellectual_map_off){
                    map_check.setText("지적도off");
                    naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                    map_on.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button));
                    map_off.setBackground(ContextCompat.getDrawable(context,R.drawable.round_button_arm));
                    map_on.setVisibility(v.GONE);
                    map_off.setVisibility(v.GONE);
                }
            }
        });
    }

    public void updateClearButton(final PolylineOverlay polyline){
        Button clearBtn = (Button)findViewById(R.id.map_clear);

        clearBtn.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                polyline.setMap(null);
            }
        });
    }

    private void updateSpeed() {

        Speed speed = this.drone.getAttribute(AttributeType.SPEED);
        TextView droneSpeed = (TextView) findViewById(R.id.speed);
        droneSpeed.setText("속도 " + Math.round(speed.getAirSpeed())+"m/s");
        Log.d("myCheck","속도  " + Math.round(speed.getAirSpeed())+"m/s");

    }

    private void updateBattery() {

        Battery battery = this.drone.getAttribute(AttributeType.BATTERY);
        TextView droneBattery = (TextView)findViewById(R.id.voltage);
        String strBattery = String.format("%.1f",battery.getBatteryVoltage());
        droneBattery.setText("전압 " + strBattery +"v");
        Log.d("myCheck","배터리1 " + strBattery);

    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {
        switch (connectionStatus.getStatusCode()) {
            case LinkConnectionStatus.FAILED:
                Bundle extras = connectionStatus.getExtras();
                String msg = null;
                if (extras != null) {
                    msg = extras.getString(LinkConnectionStatus.EXTRA_ERROR_MSG);
                }
                alertUser("Connection Failed:" + msg);
                break;
        }
    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    protected void updateVehicleModesForType(int droneType) {

        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    public void updateAltitude(){

        Altitude altitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        TextView droneAltitude = (TextView) findViewById(R.id.altitude);
        droneAltitude.setText("고도 " + Math.round(altitude.getTargetAltitude())+"m");
        Log.d("myCheck","고도2 " + Math.round(altitude.getTargetAltitude())+"m");
    }

    public void updateDronePosition() {

        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong vehiclePosition = droneGps.getPosition( );
        Log.d("myCheck", "드론 위치 : " + vehiclePosition);
        Log.d("myCheck","위성갯수 : " + droneGps.getSatellitesCount());
        TextView textview = (TextView) findViewById(R.id.textView);
        textview.setText("위성 "+ droneGps.getSatellitesCount());

        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        TextView droneYaw = (TextView)findViewById(R.id.yaw);
        float angle = (float)attitude.getYaw();
        droneYaw.setText("Yaw " + Math.round(angle)+"deg");
        Log.d("myCheck","YAW : " + Math.round(angle));


        marker.setMap(null);//그 전 마커들 지워줌
        marker.setPosition(new LatLng(vehiclePosition.getLatitude(),vehiclePosition.getLongitude()));
        marker.setAngle(angle); // 드론을 돌리면 yaw값이 변경되며 마커 모양을 변경시켜줌
        marker.setIcon(OverlayImage.fromResource(R.drawable.next_5));
        marker.setWidth(80);
        marker.setHeight(80);
        marker.setMap(naverMap); // 찍히는 좌표마다 marker 표시

        updateMapLock(new LatLng(vehiclePosition.getLatitude(),vehiclePosition.getLongitude()));

        PolylineOverlay  polylineOverlay = new PolylineOverlay();
        LatLng[] polyline = new LatLng[500];
        polyline[polylineCheck] = new LatLng(vehiclePosition.getLatitude(),vehiclePosition.getLongitude());
        polylineCheck++;

        for(int i=0;i<polylineCheck;i++)
        {
            if(polylineCheck>=500)
            {
                polylineCheck = 0;
            }
            else
            {
                polylineOverlay.setCoords(Arrays.asList(
                        polyline[i]
                ));
            }
            polylineOverlay.setMap(naverMap);
        }

        updateClearButton(polylineOverlay);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
        this.drone.connect(params);
        this.naverMap = naverMap;
        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setZoomControlEnabled(false);
        uiSettings.setLogoMargin(16,500,1200,3);
        uiSettings.setScaleBarEnabled(false);
        updateMapTypeButton();
        updateIntellectualMap();
        updateMapLock(new LatLng(37.5670135, 126.9783740));
    }
}