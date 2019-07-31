package com.example.mygcs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
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
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName( );

    MapFragment mNaverMapFragment = null;
    private Drone drone;
    private ControlTower controlTower;
    private int droneType = Type.TYPE_UNKNOWN;
    private final Handler handler = new Handler( );
    private int guidepositionCheck = 0;

    Marker marker = new Marker( );

    Marker guideMarker = new Marker( );

    private Spinner modeSelector;
    NaverMap naverMap;

    ArrayList<LatLng> flight_path = new ArrayList<>();
    PolylineOverlay polylineOverlay = new PolylineOverlay( );

    //Marker guideMarker = new Marker( );

    Context context = this;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hidNavigationBar( );

        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        final Context context = getApplicationContext( );
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        FragmentManager fm = getSupportFragmentManager( );
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            mNaverMapFragment = MapFragment.newInstance( );
            fm.beginTransaction( ).add(R.id.map, mNaverMapFragment).commit( );
        }

        this.modeSelector = (Spinner) findViewById(R.id.spinner);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener( ) {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((TextView) parent.getChildAt(0)).setTextColor(Color.WHITE);
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        mNaverMapFragment.getMapAsync(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,  @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    private void hidNavigationBar() {
        int uiOptions = getWindow( ).getDecorView( ).getSystemUiVisibility( );
        int newUiOptions = uiOptions;
        boolean isImmersiveModeEnabled =
                ((uiOptions | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) == uiOptions);
        if (isImmersiveModeEnabled) {
            Log.d(TAG, "Turning immersive mode mode off.");
        } else {
            Log.d(TAG, "Turning immersive mode mode on.");
        }
        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow( ).getDecorView( ).setSystemUiVisibility(newUiOptions);

    }

    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(3, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser("Taking off...");
                }

                @Override
                public void onError(int i) {
                    alertUser("Unable to take off.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to take off.");
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        }
        else {
            // Connected but not Armed
//            VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
//                @Override
//                public void onError(int executionError) {
//                    alertUser("Unable to arm vehicle.");
//                }
//
//                @Override
//                public void onTimeout() {
//                    alertUser("Arming operation timed out.");
//                }
//            });
            if(vehicleState.getVehicleMode() == VehicleMode.COPTER_LAND){
                VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_ALT_HOLD, new SimpleCommandListener() {
                    @Override
                    public void onError(int executionError) {
                        alertUser("Unable to land the vehicle.");
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("Unable to alt_hold the vehicle.");
                    }
                });
            }
            DialogArming();
        }
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.buttonTakeoff);


        if (vehicleState.isFlying( )) {
            armButton.setText("LAND");
        } else if (vehicleState.isArmed( )) {
            armButton.setText("TAKE_OFF");
        } else if (vehicleState.isConnected( )) {
            armButton.setText("ARM");
        }
    }

    public void ArmButton(){

        VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
            @Override
            public void onError(int executionError) {
                alertUser("Unable to arm vehicle.");
            }

            @Override
            public void onTimeout() {
                alertUser("Arming operation timed out.");
            }
        });
    }

    private void DialogArming(){
        AlertDialog.Builder arm_diaglog = new AlertDialog.Builder(this);
        arm_diaglog.setMessage("시동을 거시겠습니까?").setCancelable(false).setPositiveButton("아니오",
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).setNegativeButton("예",
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ArmButton();
                        dialog.cancel();
                    }
                });
        AlertDialog alert = arm_diaglog.create();

        alert.setTitle("Arming");
        alert.show();
    }

    public void Guide(){
        naverMap.setOnMapLongClickListener((PointF,latLng) ->
                GuideDialog(PointF, latLng)
        );
    }

    public void GuideMarker(PointF point, LatLng latLng) {

        guideMarker.setPosition(latLng);
        guideMarker.setIcon(OverlayImage.fromResource(R.drawable.marker_end));
        guideMarker.setWidth(100);
        guideMarker.setHeight(100);
        guideMarker.setAnchor(point);
        guideMarker.setMap(naverMap);
        //GuideModeSelect(latLng);

        ControlApi.getApi(this.drone).goTo(new LatLong(latLng.latitude, latLng.longitude), true, new AbstractCommandListener( ) {
            @Override
            public void onSuccess() {
                alertUser("Go to TargetPoint...");
            }

            @Override
            public void onError(int i) {
                alertUser("Unable to go.");
            }

            @Override
            public void onTimeout() {
                alertUser("Unable to go.");
            }
        });
        updateDistanceFromeTarget();
        Log.d("guide","guidePosition : " + new LatLong(latLng.latitude,latLng.longitude));
    }

    public double distanceBetweenPoint(LatLng pointA, LatLng pointB) {
        if (pointA == null || pointB == null) {
            return 0;
        }
        double dx = pointA.latitude - pointB.latitude;
        double dy = pointA.longitude - pointB.longitude;
        return Math.sqrt((dx*dx)+(dy*dy));
    }

    public void updateDistanceFromeTarget(){
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);

        double distanceFromTarget = 0;
        double a = 0;
        if (droneGps.isValid()) {
            distanceFromTarget = distanceBetweenPoint(guideMarker.getPosition(),marker.getPosition());
            a = Double.parseDouble(String.format("%3f",distanceFromTarget));
        } else {
            distanceFromTarget = 0;
        }
        if(distanceFromTarget <= 1){
            Toast.makeText(this,"목적지에 도착했습니다.",Toast.LENGTH_SHORT).show();
        }
        Log.d("guide","between : " + distanceFromTarget);
        Log.d("guide","guideMarker : " + guideMarker.getPosition());
        Log.d("guide","droneMarker : " + marker.getPosition());
        Log.d("guide","a : " + a);
    }

    public void GuideDialog(PointF point, LatLng latlng){
        AlertDialog.Builder guide_diaglog = new AlertDialog.Builder(this);
        guide_diaglog.setMessage("가이드모드로 전환하시겠습니까?").setCancelable(false).setPositiveButton("아니오",
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).setNegativeButton("예",
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        GuideMarker(point,latlng);
                        dialog.cancel();
                    }
                });
        AlertDialog alert = guide_diaglog.create();

        alert.setTitle("가이드모드");
        alert.show();

    }

    @Override
    public void onStart() {
        super.onStart( );
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop( );
        if (this.drone.isConnected( )) {
            this.drone.disconnect( );
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect( );
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateArmButton();
                updateMapTypeButton( );
                break;

            case AttributeEvent.GPS_POSITION:
                updateDronePosition( );
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType( ) != this.droneType) {
                    this.droneType = newDroneType.getDroneType( );
                    updateVehicleModesForType(this.droneType);
                }
                Guide();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude( );
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBattery( );
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode( );
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed( );
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton( );
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    public void updateMapTypeButton() {
        final Button mapType = (Button) findViewById(R.id.map_type);
        final Button mapBasic = (Button) findViewById(R.id.basic_map);
        final Button mapTerrain = (Button) findViewById(R.id.terrain_map);
        final Button mapSatellite = (Button) findViewById(R.id.satellite_map);

        final Button map_on = (Button) findViewById(R.id.intellectual_map_on);
        final Button map_off = (Button) findViewById(R.id.intellectual_map_off);

        final Button mapLock = (Button) findViewById(R.id.lock);
        final Button mapUnlock = (Button) findViewById(R.id.unlock);

        mapType.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {

                if (mapBasic.getVisibility( ) != v.VISIBLE) {

                    mapBasic.setVisibility(v.VISIBLE);
                    mapTerrain.setVisibility(v.VISIBLE);
                    mapSatellite.setVisibility(v.VISIBLE);

                    map_on.setVisibility(v.GONE);
                    map_off.setVisibility(v.GONE);

                    mapLock.setVisibility(v.GONE);
                    mapUnlock.setVisibility(v.GONE);

                    if (mapType.getText( ) == mapBasic.getText( )) {
                        mapBasic.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    } else if (mapType.getText( ) == mapTerrain.getText( )) {
                        mapTerrain.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    } else if (mapType.getText( ) == mapSatellite.getText( )) {
                        mapSatellite.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    }
                } else if (mapBasic.getVisibility( ) == v.VISIBLE) {
                    mapBasic.setVisibility(v.INVISIBLE);
                    mapTerrain.setVisibility(v.INVISIBLE);
                    mapSatellite.setVisibility(v.INVISIBLE);
                }
            }
        });
        mapBasic.setOnClickListener(new Button.OnClickListener( ) { //일반지도로 변경
            @Override
            public void onClick(View v) {
                mapType.setText("일반지도");
                naverMap.setMapType(NaverMap.MapType.Basic);
                mapBasic.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                mapTerrain.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                mapSatellite.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                mapBasic.setVisibility(v.GONE);
                mapTerrain.setVisibility(v.GONE);
                mapSatellite.setVisibility(v.GONE);
            }
        });
        mapTerrain.setOnClickListener(new Button.OnClickListener( ) { //지형도로 변경
            @Override
            public void onClick(View v) {
                mapType.setText("지형도");
                naverMap.setMapType(NaverMap.MapType.Terrain);
                mapBasic.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                mapTerrain.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                mapSatellite.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                mapBasic.setVisibility(v.GONE);
                mapTerrain.setVisibility(v.GONE);
                mapSatellite.setVisibility(v.GONE);
            }
        });
        mapSatellite.setOnClickListener(new Button.OnClickListener( ) { //위성지도로 변경
            @Override
            public void onClick(View v) {

                mapType.setText("위성지도");
                naverMap.setMapType(NaverMap.MapType.Satellite);
                mapBasic.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                mapTerrain.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                mapSatellite.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                mapBasic.setVisibility(v.GONE);
                mapTerrain.setVisibility(v.GONE);
                mapSatellite.setVisibility(v.GONE);
            }
        });
    }

    public void updateMapLock(final LatLng latLng) {

        final Button mapLocking = (Button) findViewById(R.id.locking);
        final Button mapLock = (Button) findViewById(R.id.lock);
        final Button mapUnlock = (Button) findViewById(R.id.unlock);

        final Button mapBasic = (Button) findViewById(R.id.basic_map);
        final Button mapTerrain = (Button) findViewById(R.id.terrain_map);
        final Button mapSatellite = (Button) findViewById(R.id.satellite_map);

        final Button map_on = (Button) findViewById(R.id.intellectual_map_on);
        final Button map_off = (Button) findViewById(R.id.intellectual_map_off);

        if(mapLocking.getText() == mapLock.getText()){
            CameraUpdate cameraupdate = CameraUpdate.scrollTo(latLng);
            naverMap.moveCamera(cameraupdate); //찍히는 좌표마다 카메라가 따라다님
        }

        mapLocking.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                if (mapLock.getVisibility( ) != v.VISIBLE) {
                    mapLock.setVisibility(v.VISIBLE);
                    mapUnlock.setVisibility(v.VISIBLE);

                    mapBasic.setVisibility(v.GONE);
                    mapTerrain.setVisibility(v.GONE);
                    mapSatellite.setVisibility(v.GONE);

                    map_on.setVisibility(v.GONE);
                    map_off.setVisibility(v.GONE);

                    if (mapLocking.getText( ) == mapLock.getText( )) {
                        mapLock.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    } else if (mapLocking.getText( ) == mapUnlock.getText( )) {
                        mapUnlock.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    }
                } else if (mapLock.getVisibility( ) == v.VISIBLE) {
                    mapLock.setVisibility(v.INVISIBLE);
                    mapUnlock.setVisibility(v.INVISIBLE);
                }
            }
        });
        mapLock.setOnClickListener(new Button.OnClickListener( ) { //맵 잠금
            @Override
            public void onClick(View v) {
                mapLocking.setText("맵잠금");
                CameraUpdate cameraupdate = CameraUpdate.scrollTo(latLng);
                naverMap.moveCamera(cameraupdate); //찍히는 좌표마다 카메라가 따라다님
                mapLock.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                mapUnlock.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                mapLock.setVisibility(v.GONE);
                mapUnlock.setVisibility(v.GONE);
            }
        });

        mapUnlock.setOnClickListener(new Button.OnClickListener( ) { //맵 이동
            @Override
            public void onClick(View v) {
                mapLocking.setText("맵이동");
                mapLock.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                mapUnlock.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                mapLock.setVisibility(v.GONE);
                mapUnlock.setVisibility(v.GONE);
            }
        });
    }

    public void updateIntellectualMap() {

        final Button map_on = (Button) findViewById(R.id.intellectual_map_on);
        final Button map_off = (Button) findViewById(R.id.intellectual_map_off);
        final Button map_check = (Button) findViewById(R.id.intellectual_map);

        final Button mapLock = (Button) findViewById(R.id.lock);
        final Button mapUnlock = (Button) findViewById(R.id.unlock);

        final Button mapBasic = (Button) findViewById(R.id.basic_map);
        final Button mapTerrain = (Button) findViewById(R.id.terrain_map);
        final Button mapSatellite = (Button) findViewById(R.id.satellite_map);

        map_check.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                if (map_on.getVisibility( ) != v.VISIBLE) {
                    map_on.setVisibility(v.VISIBLE);
                    map_off.setVisibility(v.VISIBLE);

                    mapBasic.setVisibility(v.GONE);
                    mapTerrain.setVisibility(v.GONE);
                    mapSatellite.setVisibility(v.GONE);


                    mapLock.setVisibility(v.GONE);
                    mapUnlock.setVisibility(v.GONE);

                    if (map_check.getText( ) == map_off.getText( )) {
                        map_off.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    } else if (map_check.getText( ) == map_on.getText( )) {
                        map_on.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                    }
                } else if (map_on.getVisibility( ) == v.VISIBLE) {
                    map_on.setVisibility(v.INVISIBLE);
                    map_off.setVisibility(v.INVISIBLE);
                }
            }
        });
        map_on.setOnClickListener(new Button.OnClickListener( ) { //지적도 on
            @Override
            public void onClick(View v) {
                map_check.setText("지적도on");
                naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                map_on.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                map_off.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                map_on.setVisibility(v.GONE);
                map_off.setVisibility(v.GONE);

            }
        });

        map_off.setOnClickListener(new Button.OnClickListener( ) { // 지적도 off
            @Override
            public void onClick(View v) {
                map_check.setText("지적도off");
                naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                map_on.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button));
                map_off.setBackground(ContextCompat.getDrawable(context, R.drawable.round_button_arm));
                map_on.setVisibility(v.GONE);
                map_off.setVisibility(v.GONE);
            }
        });
    }

    public void updateClearButton(final PolylineOverlay polyline) {
        Button clearBtn = (Button) findViewById(R.id.map_clear);

        clearBtn.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                flight_path.clear();
                polyline.setMap(null);
            }
        });
    }

    private void updateSpeed() {

        Speed speed = this.drone.getAttribute(AttributeType.SPEED);
        TextView droneSpeed = (TextView) findViewById(R.id.speed);
        droneSpeed.setText("속도 " + Math.round(speed.getGroundSpeed()) + "m/s");
        Log.d("speed", "속도  " + Math.round(speed.getGroundSpeed()) + "m/s");

    }

    private void updateBattery() {

        Battery battery = this.drone.getAttribute(AttributeType.BATTERY);
        TextView droneBattery = (TextView) findViewById(R.id.voltage);
        String strBattery = String.format("%.1f", battery.getBatteryVoltage( ));
        droneBattery.setText("전압 " + strBattery + "v");
        Log.d("battery", "배터리1 " + strBattery);

    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {
        switch (connectionStatus.getStatusCode( )) {
            case LinkConnectionStatus.FAILED:
                Bundle extras = connectionStatus.getExtras( );
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
        Toast.makeText(getApplicationContext( ), message, Toast.LENGTH_SHORT).show( );
        Log.d(TAG, message);
    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem( );

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener( ) {
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
        VehicleMode vehicleMode = vehicleState.getVehicleMode( );
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter( );
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    public void updateAltitude() {

        Altitude altitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        TextView droneAltitude = (TextView) findViewById(R.id.altitude);
        droneAltitude.setText("고도 " + Math.round(altitude.getTargetAltitude( )) + "m");
        Log.d("altitude", "고도2 " + Math.round(altitude.getTargetAltitude( )) + "m");
    }

    public void updateDronePosition() {

        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong vehiclePosition = droneGps.getPosition( );
        Log.d("position", "드론 위치 : " + vehiclePosition);
        Log.d("check", "위성갯수 : " + droneGps.getSatellitesCount( ));
        TextView textview = (TextView) findViewById(R.id.textView);
        textview.setText("위성 " + droneGps.getSatellitesCount( ));

        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        TextView droneYaw = (TextView) findViewById(R.id.yaw);
        float angle = (float) attitude.getYaw( );
        if(angle < 0){
            angle =(float)attitude.getYaw() + 360;
        }
        droneYaw.setText("Yaw " + Math.round(angle) + "deg");
        Log.d("yaw", "YAW : " + Math.round(angle));


        marker.setMap(null);//그 전 마커들 지워줌
        marker.setPosition(new LatLng(vehiclePosition.getLatitude( ), vehiclePosition.getLongitude( )));
        marker.setAngle(angle); // 드론을 돌리면 yaw값이 변경되며 마커 모양을 변경시켜줌
        marker.setIcon(OverlayImage.fromResource(R.drawable.next));
        marker.setWidth(80);
        marker.setHeight(330);
        marker.setAnchor(new PointF(0.5F,0.9F));
        marker.setMap(naverMap); // 찍히는 좌표마다 marker 표시

        updateMapLock(new LatLng(vehiclePosition.getLatitude( ), vehiclePosition.getLongitude( ))); // 맵 잠금 / 이동 버튼

        Collections.addAll(
                flight_path,
                new LatLng(vehiclePosition.getLatitude( ), vehiclePosition.getLongitude( )));
        polylineOverlay.setCoords(flight_path);
        polylineOverlay.setColor(Color.MAGENTA);
        polylineOverlay.setMap(naverMap);
        updateClearButton(polylineOverlay); // clear 버튼

        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.NoFollow);

    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
        this.drone.connect(params);
        this.naverMap = naverMap;
        UiSettings uiSettings = naverMap.getUiSettings( );
        uiSettings.setZoomControlEnabled(false);
        uiSettings.setLogoMargin(16, 500, 1200, 1);
        uiSettings.setScaleBarEnabled(false);
        updateMapTypeButton( ); // 지도 타입 변경 버튼
        updateIntellectualMap( ); // 지적도 on / off 버튼
    }
}