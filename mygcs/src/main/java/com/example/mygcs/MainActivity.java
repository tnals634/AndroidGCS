package com.example.mygcs;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
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
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
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
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);
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
            updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());
                //updateArmButton();

                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                //updateArmButton();
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

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText(getText(R.string.button_disconnect));
        } else {
            connectButton.setText(getText(R.string.button_connect));
        }
    }

    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(params);
        }
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

    public void updateDronePosition() {

        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong vehiclePosition = droneGps.getPosition( );
        Log.d("myCheck", "111111111111" + vehiclePosition);
        Log.d("myCheck","123123123 " + droneGps.getSatellitesCount());
        TextView textview = (TextView) findViewById(R.id.textView);
        textview.setText("위성 : "+droneGps.getSatellitesCount());

        marker.setMap(null);//그 전 마커들 지워줌
        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        float angle = (float)attitude.getYaw();
        marker.setPosition(new LatLng(vehiclePosition.getLatitude(),vehiclePosition.getLongitude()));
        marker.setAngle(angle); // 드론을 돌리면 yaw값이 변경되며 마커 모양을 변경시켜줌
        marker.setIcon(OverlayImage.fromResource(R.drawable.next_4));
        marker.setWidth(50);
        marker.setHeight(50);
        marker.setMap(naverMap); // 찍히는 좌표마다 marker 표시

        CameraPosition cameraPosition =
                new CameraPosition(new LatLng(vehiclePosition.getLatitude(),vehiclePosition.getLongitude()), 18,0,0);
        naverMap.setCameraPosition(cameraPosition); //찍히는 좌표마다 카메라가 따라다님


//        PolylineOverlay polyline = new PolylineOverlay();
//
//        LatLng[] polylineCount = new LatLng[polylineCheck + 1];
//        polylineCount[polylineCheck] = new LatLng(vehiclePosition.getLatitude(),vehiclePosition.getLongitude());
//        for(int check = 0; check < polylineCheck + 1; check++)
//        {
//            List<LatLng> coords = new ArrayList<>();
//            Collections.addAll(coords,
//                    polylineCount[check]
//            );
//            polyline.setCoords(coords);
//        }
//        polyline.setMap(naverMap);
//        polylineCheck++;
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
    }
}