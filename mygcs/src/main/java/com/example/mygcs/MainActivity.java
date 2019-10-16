package com.example.mygcs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.internal.GetServiceRequest;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolygonOverlay;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.MissionApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
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
import com.o3dr.services.android.lib.util.MathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName( );

    MapFragment mNaverMapFragment = null;
    private Drone mDrone;
    private ControlTower mControlTower;
    private int mDroneType = Type.TYPE_UNKNOWN;
    private final Handler mHandler = new Handler( );

    Marker mDroneMarker = new Marker( );

    Marker mGuideMarker = new Marker( );

    private Spinner mModeSelector;
    NaverMap mMap;

    double mDroneAltitudeValue = 3;

    ArrayList<LatLng> mFlightPath = new ArrayList<>( );
    PolylineOverlay mFlightDronePolyline = new PolylineOverlay( );

    Context mContext = this;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    Marker[] mIntervalMarkerAB = new Marker[4];
    List<Marker> mListIntervalMarkers = new ArrayList<>();
    //mIntervalMarkerAB[0] = start A, mIntervalMarkerAB[1] = sub A, mIntervalMarkerAB[2] = start B, mIntervalMarkerAB[3] = sub B

    PolygonOverlay mIntervalPolygon = new PolygonOverlay( );
    int mIntervlaCountValue = 0;
    
    ArrayList<LatLng> mCountMeter = new ArrayList<>( );
    PolylineOverlay mIntervalPolyline = new PolylineOverlay( );


    double mIntervalMaxMeter;

    int mIndex;
    Mission mIntervalMission = new Mission();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, getString(R.string.start_mainactivity));
        super.onCreate(savedInstanceState);

        getWindow( ).setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideNavigationBar( );

        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        final Context context = getApplicationContext( );
        this.mControlTower = new ControlTower(context);
        this.mDrone = new Drone(context);

        FragmentManager fm = getSupportFragmentManager( );
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            mNaverMapFragment = MapFragment.newInstance( );
            fm.beginTransaction( ).add(R.id.map, mNaverMapFragment).commit( );
        }

        this.mModeSelector = (Spinner) findViewById(R.id.spinner);
        this.mModeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener( ) {
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

//////////////////////////////////////(내위치)//////////////////////////////////////////////////////

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

///////////////////////////////////////////(하단바 없애기)//////////////////////////////////////////

    private void hideNavigationBar() {
        int uiOptions = getWindow( ).getDecorView( ).getSystemUiVisibility( );
        int newUiOptions = uiOptions;

        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow( ).getDecorView( ).setSystemUiVisibility(newUiOptions);

    }

///////////////////////////////////////////(arming 부분)////////////////////////////////////////////

    public void onArmButtonTap(View view) {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying( )) {
            // Land
            VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener( ) {
                @Override
                public void onError(int executionError) {
                    alertUser(getString(R.string.not_to_landing));
                }

                @Override
                public void onTimeout() {
                    alertUser(getString(R.string.not_to_landing));
                }
            });
        } else if (vehicleState.isArmed( )) {
            // Take off
            ControlApi.getApi(this.mDrone).takeoff(mDroneAltitudeValue, new AbstractCommandListener( ) {

                @Override
                public void onSuccess() {
                    alertUser(getString(R.string.on_take_off));
                }

                @Override
                public void onError(int i) {
                    alertUser(getString(R.string.cannot_take_off));
                }

                @Override
                public void onTimeout() {
                    alertUser(getString(R.string.cannot_take_off));
                }

            });
            Log.d("altitude_drone", "값 : " + mDroneAltitudeValue);
        } else if (!vehicleState.isConnected( )) {
            // Connect
            alertUser(getString(R.string.have_to_connect_to_drone));
        } else {
            if (vehicleState.getVehicleMode( ) == VehicleMode.COPTER_LAND) {
                VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_ALT_HOLD, new SimpleCommandListener( ) {
                    @Override
                    public void onError(int executionError) {
                        alertUser(getString(R.string.cannot_change_althold));
                    }

                    @Override
                    public void onTimeout() {
                        alertUser(getString(R.string.cannot_change_althold));
                    }
                });
            }
            armingDialog( );
        }
    }

    protected void updateArmButton() {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.buttonTakeoff);


        if (vehicleState.isFlying( )) {
            armButton.setText(getString(R.string.land));
        } else if (vehicleState.isArmed( )) {
            armButton.setText(getString(R.string.take_off));
        } else if (vehicleState.isConnected( )) {
            armButton.setText(getString(R.string.arm));
        }
    }

    public void armingButton() {

        VehicleApi.getApi(this.mDrone).arm(true, false, new SimpleCommandListener( ) {
            @Override
            public void onError(int executionError) {
                alertUser(getString(R.string.cannot_arming));
            }

            @Override
            public void onTimeout() {
                alertUser(getString(R.string.time_out_arming));
            }
        });
    }

    private void armingDialog() {
        AlertDialog.Builder dialogArm = new AlertDialog.Builder(this);
        dialogArm.setMessage(getString(R.string.set_a_machine)).setCancelable(false).setPositiveButton(getString(R.string.no),
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel( );
                    }
                }).setNegativeButton(getString(R.string.yes),
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        armingButton( );
                        dialog.cancel( );
                    }
                });
        AlertDialog alert = dialogArm.create( );

        alert.setTitle(getString(R.string.arming));
        alert.show( );
    }

///////////////////////////////////////////(가이드 모드)////////////////////////////////////////////

    public void startGuideMode() {
        mMap.setOnMapLongClickListener((PointF, latLng) ->
                guideModeDialog(PointF, latLng)
        );
    }

    public void guideMarker(PointF point, LatLng latLng) {

        mGuideMarker.setPosition(latLng);
        mGuideMarker.setIcon(OverlayImage.fromResource(R.drawable.marker_end));
        mGuideMarker.setWidth(100);
        mGuideMarker.setHeight(100);
        mGuideMarker.setAnchor(point);
        mGuideMarker.setMap(mMap);

        ControlApi.getApi(this.mDrone).goTo(new LatLong(latLng.latitude, latLng.longitude),
                true, new AbstractCommandListener( ) {
                    @Override
                    public void onSuccess() {
                        alertUser(getString(R.string.go_to_target));
                    }

                    @Override
                    public void onError(int i) {
                        alertUser(getString(R.string.cannot_go_target));
                    }

                    @Override
                    public void onTimeout() {
                        alertUser(getString(R.string.cannot_go_target));
                    }
                });
        Log.d("guide", "guidePosition : " + new LatLong(latLng.latitude, latLng.longitude));
    }

    public void updateDistanceFromTarget() {
        LatLng gMarker = new LatLng(mGuideMarker.getPosition( ).latitude, mGuideMarker.getPosition( ).longitude);
        LatLng dMarker = new LatLng(mDroneMarker.getPosition( ).latitude, mDroneMarker.getPosition( ).longitude);
        double btween = gMarker.distanceTo(dMarker);

        if (btween <= 1) {
            Toast.makeText(this, getString(R.string.arrived_target), Toast.LENGTH_SHORT).show( );
        }
        Log.d("guide", "gMarker.distanceTo(dMarker); : " + gMarker.distanceTo(dMarker));
    }

    public void guideModeDialog(PointF point, LatLng latlng) {
        AlertDialog.Builder dialogGuided = new AlertDialog.Builder(this);
        dialogGuided.setMessage(getString(R.string.change_to_guide_mode)).setCancelable(false).setPositiveButton(getString(R.string.no),
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel( );
                    }
                }).setNegativeButton(getString(R.string.yes),
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        guideMarker(point, latlng);
                        dialog.cancel( );
                    }
                });
        AlertDialog alert = dialogGuided.create( );

        alert.setTitle(getString(R.string.guide_mode));
        alert.show( );
    }

///////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onStart() {
        super.onStart( );
        this.mControlTower.connect(this);
        updateVehicleModesForType(this.mDroneType);
    }

    @Override
    public void onStop() {
        super.onStop( );
        if (this.mDrone.isConnected( )) {
            this.mDrone.disconnect( );
        }

        this.mControlTower.unregisterDrone(this.mDrone);
        this.mControlTower.disconnect( );
    }

///////////////////////////////////////////(EVENT 부분)/////////////////////////////////////////////

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser(getString(R.string.connect_to_drone));
                updateArmButton( );
                updateMapTypeButton( );
                updateYAW( );
                updateAltitudeSetting( );
                updateDroneMode( );
                missionTransmissionButton();
                updateClearButton(mFlightDronePolyline);
                break;

            case AttributeEvent.GPS_POSITION:
                updateDronePosition( );
                startGuideMode( );
                updateDistanceFromTarget( );
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.mDrone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType( ) != this.mDroneType) {
                    this.mDroneType = newDroneType.getDroneType( );
                    updateVehicleModesForType(this.mDroneType);
                }
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

            case AttributeEvent.GPS_COUNT:
                updateSatellites( );
                break;

            default:
                elapsedTime( );
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

///////////////////////////////////////////(map type 버튼)//////////////////////////////////////////

    public void updateMapTypeButton() {
        final Button btnMapType = (Button) findViewById(R.id.map_type);
        final Button btnMapBasic = (Button) findViewById(R.id.basic_map);
        final Button btnMapTerrain = (Button) findViewById(R.id.terrain_map);
        final Button btnMapSatellite = (Button) findViewById(R.id.satellite_map);

        final Button btnMapOn = (Button) findViewById(R.id.intellectual_map_on);
        final Button btnMapOff = (Button) findViewById(R.id.intellectual_map_off);

        final Button btnMapLock = (Button) findViewById(R.id.lock);
        final Button btnMapUnLock = (Button) findViewById(R.id.unlock);

        btnMapType.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {

                if (btnMapBasic.getVisibility( ) != v.VISIBLE) {

                    btnMapBasic.setVisibility(v.VISIBLE);
                    btnMapTerrain.setVisibility(v.VISIBLE);
                    btnMapSatellite.setVisibility(v.VISIBLE);

                    btnMapOn.setVisibility(v.GONE);
                    btnMapOff.setVisibility(v.GONE);

                    btnMapLock.setVisibility(v.GONE);
                    btnMapUnLock.setVisibility(v.GONE);

                    if (btnMapType.getText( ) == btnMapBasic.getText( )) {
                        btnMapBasic.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    } else if (btnMapType.getText( ) == btnMapTerrain.getText( )) {
                        btnMapTerrain.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    } else if (btnMapType.getText( ) == btnMapSatellite.getText( )) {
                        btnMapSatellite.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    }
                } else if (btnMapBasic.getVisibility( ) == v.VISIBLE) {
                    btnMapBasic.setVisibility(v.INVISIBLE);
                    btnMapTerrain.setVisibility(v.INVISIBLE);
                    btnMapSatellite.setVisibility(v.INVISIBLE);
                }
            }
        });
        btnMapBasic.setOnClickListener(new Button.OnClickListener( ) { //일반지도로 변경
            @Override
            public void onClick(View v) {
                btnMapType.setText(getString(R.string.map_basic));
                mMap.setMapType(NaverMap.MapType.Basic);
                btnMapBasic.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnMapTerrain.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapSatellite.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapBasic.setVisibility(v.GONE);
                btnMapTerrain.setVisibility(v.GONE);
                btnMapSatellite.setVisibility(v.GONE);
            }
        });
        btnMapTerrain.setOnClickListener(new Button.OnClickListener( ) { //지형도로 변경
            @Override
            public void onClick(View v) {
                btnMapType.setText(getString(R.string.map_terrain));
                mMap.setMapType(NaverMap.MapType.Terrain);
                btnMapBasic.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapTerrain.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnMapSatellite.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapBasic.setVisibility(v.GONE);
                btnMapTerrain.setVisibility(v.GONE);
                btnMapSatellite.setVisibility(v.GONE);
            }
        });
        btnMapSatellite.setOnClickListener(new Button.OnClickListener( ) { //위성지도로 변경
            @Override
            public void onClick(View v) {

                btnMapType.setText(getString(R.string.map_satellite));
                mMap.setMapType(NaverMap.MapType.Satellite);
                btnMapBasic.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapTerrain.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapSatellite.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnMapBasic.setVisibility(v.GONE);
                btnMapTerrain.setVisibility(v.GONE);
                btnMapSatellite.setVisibility(v.GONE);
            }
        });
    }

///////////////////////////////////////////(맵 잠금 버튼)//////////////////////////////////////////

    public void updateMapLock(final LatLng latLng) {

        final Button btnMapLoking = (Button) findViewById(R.id.locking);
        final Button btnMapLock = (Button) findViewById(R.id.lock);
        final Button btnMapUnLock = (Button) findViewById(R.id.unlock);

        final Button btnMapBasic = (Button) findViewById(R.id.basic_map);
        final Button btnMapTerrain = (Button) findViewById(R.id.terrain_map);
        final Button btnMapSatellite = (Button) findViewById(R.id.satellite_map);

        final Button btnMapOn = (Button) findViewById(R.id.intellectual_map_on);
        final Button btnMapOff = (Button) findViewById(R.id.intellectual_map_off);

        if (btnMapLoking.getText( ) == btnMapLock.getText( )) {
            CameraUpdate cameraupdate = CameraUpdate.scrollTo(latLng);
            mMap.moveCamera(cameraupdate); //찍히는 좌표마다 카메라가 따라다님
        }

        btnMapLoking.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                if (btnMapLock.getVisibility( ) != v.VISIBLE) {
                    btnMapLock.setVisibility(v.VISIBLE);
                    btnMapUnLock.setVisibility(v.VISIBLE);

                    btnMapBasic.setVisibility(v.GONE);
                    btnMapTerrain.setVisibility(v.GONE);
                    btnMapSatellite.setVisibility(v.GONE);

                    btnMapOn.setVisibility(v.GONE);
                    btnMapOff.setVisibility(v.GONE);

                    if (btnMapLoking.getText( ) == btnMapLock.getText( )) {
                        btnMapLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    } else if (btnMapLoking.getText( ) == btnMapUnLock.getText( )) {
                        btnMapUnLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    }
                } else if (btnMapLock.getVisibility( ) == v.VISIBLE) {
                    btnMapLock.setVisibility(v.INVISIBLE);
                    btnMapUnLock.setVisibility(v.INVISIBLE);
                }
            }
        });
        btnMapLock.setOnClickListener(new Button.OnClickListener( ) { //맵 잠금
            @Override
            public void onClick(View v) {
                btnMapLoking.setText(getString(R.string.map_lock));
                CameraUpdate cameraupdate = CameraUpdate.scrollTo(latLng);
                mMap.moveCamera(cameraupdate); //찍히는 좌표마다 카메라가 따라다님
                btnMapLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnMapUnLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapLock.setVisibility(v.GONE);
                btnMapUnLock.setVisibility(v.GONE);
            }
        });

        btnMapUnLock.setOnClickListener(new Button.OnClickListener( ) { //맵 이동
            @Override
            public void onClick(View v) {
                btnMapLoking.setText(getString(R.string.map_unlock));
                btnMapLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapUnLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnMapLock.setVisibility(v.GONE);
                btnMapUnLock.setVisibility(v.GONE);
            }
        });
    }

///////////////////////////////////////////(지적도 버튼)////////////////////////////////////////////

    public void updateIntellectualMap() {

        final Button btnMapOn = (Button) findViewById(R.id.intellectual_map_on);
        final Button btnMapOff = (Button) findViewById(R.id.intellectual_map_off);
        final Button btnCheckMap = (Button) findViewById(R.id.intellectual_map);

        final Button btnMapLock = (Button) findViewById(R.id.lock);
        final Button btnMapUnLock = (Button) findViewById(R.id.unlock);

        final Button btnMapBasic = (Button) findViewById(R.id.basic_map);
        final Button btnMapTerrain = (Button) findViewById(R.id.terrain_map);
        final Button btnMapSatellite = (Button) findViewById(R.id.satellite_map);

        btnCheckMap.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                if (btnMapOn.getVisibility( ) != v.VISIBLE) {
                    btnMapOn.setVisibility(v.VISIBLE);
                    btnMapOff.setVisibility(v.VISIBLE);

                    btnMapBasic.setVisibility(v.GONE);
                    btnMapTerrain.setVisibility(v.GONE);
                    btnMapSatellite.setVisibility(v.GONE);


                    btnMapLock.setVisibility(v.GONE);
                    btnMapUnLock.setVisibility(v.GONE);

                    if (btnCheckMap.getText( ) == btnMapOff.getText( )) {
                        btnMapOff.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    } else if (btnCheckMap.getText( ) == btnMapOn.getText( )) {
                        btnMapOn.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    }
                } else if (btnMapOn.getVisibility( ) == v.VISIBLE) {
                    btnMapOn.setVisibility(v.INVISIBLE);
                    btnMapOff.setVisibility(v.INVISIBLE);
                }
            }
        });
        btnMapOn.setOnClickListener(new Button.OnClickListener( ) { //지적도 on
            @Override
            public void onClick(View v) {
                btnCheckMap.setText(getString(R.string.intellectual_map_on));
                mMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                btnMapOn.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnMapOff.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapOn.setVisibility(v.GONE);
                btnMapOff.setVisibility(v.GONE);

            }
        });

        btnMapOff.setOnClickListener(new Button.OnClickListener( ) { // 지적도 off
            @Override
            public void onClick(View v) {
                btnCheckMap.setText(getString(R.string.intellectual_map_off));
                mMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                btnMapOn.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapOff.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnMapOn.setVisibility(v.GONE);
                btnMapOff.setVisibility(v.GONE);
            }
        });
    }

///////////////////////////////////////////(polyline clear 버튼)////////////////////////////////////

    public void updateClearButton(final PolylineOverlay polyline) {
        Button btnClear = (Button) findViewById(R.id.map_clear);

        btnClear.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                mFlightPath.clear( );
                polyline.setMap(null);
                removeInterval( );

            }
        });
    }

///////////////////////////////////////////(altitude setting 버튼)//////////////////////////////////

    public void updateAltitudeSetting() {

        final Button btnSettingAltitude = (Button) findViewById(R.id.altitude_setting);
        final Button btnSettingUpAltitude = (Button) findViewById(R.id.altitude_up);
        final Button btnSettingDownAltitude = (Button) findViewById(R.id.altitude_down);

        TextView droneAltitdue = (TextView) findViewById(R.id.altitude_setting);
        droneAltitdue.setText(Math.round(mDroneAltitudeValue) + getString(R.string.meter_and_take_off_altitude));
        btnSettingAltitude.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                if (btnSettingUpAltitude.getVisibility( ) != v.VISIBLE) {
                    btnSettingUpAltitude.setVisibility(v.VISIBLE);
                    btnSettingDownAltitude.setVisibility(v.VISIBLE);

                } else if (btnSettingUpAltitude.getVisibility( ) == v.VISIBLE) {
                    btnSettingUpAltitude.setVisibility(v.INVISIBLE);
                    btnSettingDownAltitude.setVisibility(v.INVISIBLE);
                }
            }
        });
        btnSettingUpAltitude.setOnClickListener(new Button.OnClickListener( ) { //고도 높임
            @Override
            public void onClick(View v) {
                settingUpAltitude( );
            }
        });

        btnSettingDownAltitude.setOnClickListener(new Button.OnClickListener( ) { //고도 낮춤
            @Override
            public void onClick(View v) {
                settingDownAltitude( );
            }
        });
    }

    public void settingUpAltitude() {
        ++mDroneAltitudeValue;
        TextView droneAltitdue = (TextView) findViewById(R.id.altitude_setting);
        droneAltitdue.setText(Math.round(mDroneAltitudeValue) + getString(R.string.meter_and_take_off_altitude));
    }

    public void settingDownAltitude() {
        --mDroneAltitudeValue;
        TextView droneAltitdue = (TextView) findViewById(R.id.altitude_setting);
        droneAltitdue.setText(Math.round(mDroneAltitudeValue) + getString(R.string.meter_and_take_off_altitude));
    }

///////////////////////////////////////////(드론 모드 변경)/////////////////////////////////////////

    public void updateDroneMode() {

        final Button btnModeSelect = (Button) findViewById(R.id.drone_mode); //모드설정
        final Button btnBasicMode = (Button) findViewById(R.id.basicMode); // 일반모드
        final Button btnFlightRoutes = (Button) findViewById(R.id.flightRoutes); // 경로비행
        final Button btnIntervalMonitoring = (Button) findViewById(R.id.intervalMonitoring); // 간격 감시
        final Button btnAreaMonitoring = (Button) findViewById(R.id.areaMonitoring); // 면적 감시
        final Button btnMissionComplete = (Button) findViewById(R.id.complete); // 간격 감시 후 임무 전송 버튼

        btnModeSelect.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                if (btnBasicMode.getVisibility( ) != v.VISIBLE) {
                    btnBasicMode.setVisibility(v.VISIBLE);
                    btnFlightRoutes.setVisibility(v.VISIBLE);
                    btnIntervalMonitoring.setVisibility(v.VISIBLE);
                    btnAreaMonitoring.setVisibility(v.VISIBLE);

                } else if (btnBasicMode.getVisibility( ) == v.VISIBLE) {
                    btnBasicMode.setVisibility(v.INVISIBLE);
                    btnFlightRoutes.setVisibility(v.INVISIBLE);
                    btnIntervalMonitoring.setVisibility(v.INVISIBLE);
                    btnAreaMonitoring.setVisibility(v.INVISIBLE);
                }

            }
        });
        btnBasicMode.setOnClickListener(new Button.OnClickListener( ) { // 일반모드
            @Override
            public void onClick(View v) {
                btnModeSelect.setText(getString(R.string.basic_mode));
                btnBasicMode.setVisibility(v.INVISIBLE);
                btnFlightRoutes.setVisibility(v.INVISIBLE);
                btnIntervalMonitoring.setVisibility(v.INVISIBLE);
                btnAreaMonitoring.setVisibility(v.INVISIBLE);
                removeInterval( );
            }
        });

        btnFlightRoutes.setOnClickListener(new Button.OnClickListener( ) { // 경로비행
            @Override
            public void onClick(View v) {
                btnModeSelect.setText(getString(R.string.flight_routes));
                btnBasicMode.setVisibility(v.INVISIBLE);
                btnFlightRoutes.setVisibility(v.INVISIBLE);
                btnIntervalMonitoring.setVisibility(v.INVISIBLE);
                btnAreaMonitoring.setVisibility(v.INVISIBLE);
                removeInterval( );
            }
        });

        btnIntervalMonitoring.setOnClickListener(new Button.OnClickListener( ) { // 간격 감시
            @Override
            public void onClick(View v) {
                btnModeSelect.setText(getString(R.string.interval_monitoring));
                intervalDialog();
                btnMissionComplete.setVisibility(v.VISIBLE);
                btnBasicMode.setVisibility(v.INVISIBLE);
                btnFlightRoutes.setVisibility(v.INVISIBLE);
                btnIntervalMonitoring.setVisibility(v.INVISIBLE);
                btnAreaMonitoring.setVisibility(v.INVISIBLE);

            }
        });

        btnAreaMonitoring.setOnClickListener(new Button.OnClickListener( ) { // 면적 감시
            @Override
            public void onClick(View v) {
                btnModeSelect.setText(getString(R.string.area_monitoring));
                btnBasicMode.setVisibility(v.INVISIBLE);
                btnFlightRoutes.setVisibility(v.INVISIBLE);
                btnIntervalMonitoring.setVisibility(v.INVISIBLE);
                btnAreaMonitoring.setVisibility(v.INVISIBLE);
                removeInterval( );
            }
        });
    }

///////////////////////////////////////////(스피드 확인)////////////////////////////////////////////

    private void updateSpeed() {

        Speed speed = this.mDrone.getAttribute(AttributeType.SPEED);
        TextView droneSpeed = (TextView) findViewById(R.id.speed);
        droneSpeed.setText(getString(R.string.speed) + Math.round(speed.getGroundSpeed( )) + getString(R.string.meters_per_second));
        Log.d("speed", "속도  " + Math.round(speed.getGroundSpeed( )) + "m/s");

    }

///////////////////////////////////////////(배터리 확인)////////////////////////////////////////////

    private void updateBattery() {

        Battery battery = this.mDrone.getAttribute(AttributeType.BATTERY);
        TextView droneBattery = (TextView) findViewById(R.id.voltage);
        String strBattery = String.format("%.1f", battery.getBatteryVoltage( ));
        droneBattery.setText(getString(R.string.voltage) + strBattery + getString(R.string.voltage_unit));
        Log.d("battery", "배터리1 " + strBattery);

    }

///////////////////////////////////////////////////////////////////////////////////////////////////

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
                alertUser(getString(R.string.disconnected) + msg);
                break;
        }
    }

    @Override
    public void onTowerConnected() {
        alertUser(getString(R.string.connected_dronekit_and_android));
        this.mControlTower.registerDrone(this.mDrone, this.mHandler);
        this.mDrone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser(getString(R.string.disconnected_dronekit_and_android));
    }

//////////////////////////////(recyclerView (메시지 뷰) 생성)///////////////////////////////////////

    ArrayList<String> mList = new ArrayList<>( );
    ArrayList<Long> mCheckTime = new ArrayList<>( );
    long mStartTime;
    long mEndTime;
    int mCountTimeCheck = 0;
    SimpleTextAdapter mAdapter;

    //recycle에 나오게 설정
    protected void alertUser(String message) {

        Toast.makeText(getApplicationContext( ), message, Toast.LENGTH_SHORT).show( );
        Log.d(TAG, message);
        mList.add(" ♬ " + message + " ");
        mStartTime = SystemClock.elapsedRealtime( );
        mCheckTime.add(mStartTime);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        mAdapter = new SimpleTextAdapter(mList);
        recyclerView.setAdapter(mAdapter);

        recyclerView.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false));

    }

    //recycle 시간 설정
    public void elapsedTime() {

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        double lastTime = (mEndTime - mCheckTime.get(mCountTimeCheck)) / 1000.0;
        Log.d("time1", "==============================================");
        Log.d("time1", "lastTime : " + lastTime);
        Log.d("time1", "==============================================");
        if (mCountTimeCheck < mCheckTime.size( )) {
            if (lastTime >= 5) {
                mList.remove(0);
                Log.d("time1", "countCheck : " + mCountTimeCheck);
                Log.d("time1", "listSIZE : " + mList.size( ));
                Log.d("time1", "mCheckTimeSIZE : " + mCheckTime.size( ));
                Log.d("time1", "mStartTime : " + mCheckTime.get(mCountTimeCheck));
                Log.d("time1", "mEndTime : " + mEndTime);
                if (mCountTimeCheck + 1 < mCheckTime.size( )) {
                    mCountTimeCheck++;
                }
                Log.d("time1", "===================");
                Log.d("time1", "nextCountCheck : " + mCountTimeCheck);
                Log.d("time1", "mCheckTimeSIZE : " + mCheckTime.size( ));
                Log.d("time1", "nextmStartTime : " + mCheckTime.get(mCountTimeCheck));
                Log.d("time1", "nextmEndTime : " + mEndTime);
                Log.d("time1", "checkTime : " + lastTime);
                mAdapter.notifyDataSetChanged( );
                recyclerView.setAdapter(mAdapter);
            }
        }
        mEndTime = SystemClock.elapsedRealtime( );
    }

////////////////////////////////////////(A,B지점 지정 )/////////////////////////////////////////////

    //pointA 마커 생성
    public void intervalPointA(LatLng latLng, double maxMeter, double gapMeter) {

//        start_A.setPosition(latLng);
//        start_A.setWidth(80);
//        start_A.setHeight(80);
//        start_A.setIcon(OverlayImage.fromResource(R.drawable.a_point));
//        start_A.setMap(mMap);

        Marker intervalMarker = new Marker();

        intervalMarker.setPosition(latLng);
        intervalMarker.setWidth(80);
        intervalMarker.setHeight(80);
        intervalMarker.setIcon(OverlayImage.fromResource(R.drawable.a_point));
        mListIntervalMarkers.add(0,intervalMarker);
        intervalMarker.setMap(mMap);
//        mIntervalMarkerAB[0].setPosition(latLng);
//        mIntervalMarkerAB[0].setWidth(80);
//        mIntervalMarkerAB[0].setHeight(80);
//        mIntervalMarkerAB[0].setIcon(OverlayImage.fromResource(R.drawable.a_point));
//        mIntervalMarkerAB[0].setMap(mMap);
        pointB(maxMeter, gapMeter);
        mIntervalMaxMeter = maxMeter;
    }

    //pointB 마커 생성
    public void intervalPointB(LatLng latLng, double maxMeter, double gapMeter) {

        Marker intervalMarker = new Marker();
        if (mIntervlaCountValue == 0) {
//            start_B.setPosition(latLng);
//            start_B.setWidth(80);
//            start_B.setHeight(80);
//            start_B.setIcon(OverlayImage.fromResource(R.drawable.b_point));
//            start_B.setMap(mMap);

            intervalMarker.setPosition(latLng);
            intervalMarker.setWidth(80);
            intervalMarker.setHeight(80);
            intervalMarker.setIcon(OverlayImage.fromResource(R.drawable.b_point));
            mListIntervalMarkers.add(1,intervalMarker);
            intervalMarker.setMap(mMap);

//            mIntervalMarkerAB[2].setPosition(latLng);
//            mIntervalMarkerAB[2].setWidth(80);
//            mIntervalMarkerAB[2].setHeight(80);
//            mIntervalMarkerAB[2].setIcon(OverlayImage.fromResource(R.drawable.b_point));
//            mIntervalMarkerAB[2].setMap(mMap);

            interval(maxMeter);
            checkMeter(maxMeter,gapMeter);
        }
    }

    //pointA 생성할 곳 클릭
    public void pointA(double maxMeter, double gapMeter) {
        mMap.setOnMapClickListener((PointF, latLng) ->
                intervalPointA(latLng, maxMeter, gapMeter)
        );
    }

    //pointB 생성할 곳 클릭
    public void pointB(double maxMeter,double gapMeter) {
        mMap.setOnMapClickListener((PointF, latLng) ->
                intervalPointB(latLng, maxMeter, gapMeter)
        );
    }

    //간격감시 polygon 생성
    public void interval(double maxMeter) {

        Marker intervalMarker1 = new Marker();
        Marker intervalMarker2 = new Marker();

//        LatLong A_latLong = new LatLong(mIntervalMarkerAB[0].getPosition( ).latitude, mIntervalMarkerAB[0].getPosition( ).longitude);
//        LatLong B_latLong = new LatLong(mIntervalMarkerAB[2].getPosition( ).latitude, mIntervalMarkerAB[2].getPosition( ).longitude);

        LatLong ALatLong = new LatLong(mListIntervalMarkers.get(0).getPosition().latitude, mListIntervalMarkers.get(0).getPosition().longitude);
        LatLong BLatLong = new LatLong(mListIntervalMarkers.get(1).getPosition().latitude, mListIntervalMarkers.get(1).getPosition().longitude);

        double degree = MathUtils.getHeadingFromCoordinates(ALatLong,BLatLong);

        LatLong positionA = MathUtils.newCoordFromBearingAndDistance(ALatLong, degree + 90, maxMeter);
        LatLng positionSubA = new LatLng(positionA.getLatitude( ), positionA.getLongitude( ));
        
//        mIntervalMarkerAB[1].setPosition(positionSubA);
//        mIntervalMarkerAB[1].setWidth(80);
//        mIntervalMarkerAB[1].setHeight(80);
//        mIntervalMarkerAB[1].setIcon(OverlayImage.fromResource(R.drawable.sub_point));
//        mIntervalMarkerAB[1].setMap(mMap);

        intervalMarker1.setPosition(positionSubA);
        intervalMarker1.setWidth(80);
        intervalMarker1.setHeight(80);
        intervalMarker1.setIcon(OverlayImage.fromResource(R.drawable.sub_point));
        intervalMarker1.setMap(mMap);

        mListIntervalMarkers.add(2,intervalMarker1);


        LatLong positionB = MathUtils.newCoordFromBearingAndDistance(BLatLong, degree + 90, maxMeter);
        LatLng positionSubB = new LatLng(positionB.getLatitude( ), positionB.getLongitude( ));
        
//        mIntervalMarkerAB[3].setPosition(positionSubB);
//        mIntervalMarkerAB[3].setWidth(80);
//        mIntervalMarkerAB[3].setHeight(80);
//        mIntervalMarkerAB[3].setIcon(OverlayImage.fromResource(R.drawable.sub_point));
//        mIntervalMarkerAB[3].setMap(mMap);

        intervalMarker2.setPosition(positionSubB);
        intervalMarker2.setWidth(80);
        intervalMarker2.setHeight(80);
        intervalMarker2.setIcon(OverlayImage.fromResource(R.drawable.sub_point));
        intervalMarker2.setMap(mMap);

        mListIntervalMarkers.add(3,intervalMarker2);

        for(int i = 0; i < 4; i++)
        {
            mListIntervalMarkers.get(i).setMap(mMap);
        }
        mIntervalPolygon.setCoords(Arrays.asList(
                mListIntervalMarkers.get(0).getPosition( ),
                mListIntervalMarkers.get(2).getPosition( ),
                mListIntervalMarkers.get(3).getPosition( ),
                mListIntervalMarkers.get(1).getPosition( )
        ));
        mIntervalPolygon.setColor(getColor(R.color.color_interval_polygon));
        Log.d("point_interval", "A_angle : " + mListIntervalMarkers.get(0).getAngle( ));
        mIntervalPolygon.setMap(mMap);

        alertUser(getString(R.string.drone_horizon_longs)+ maxMeter);

    }

    //다른 모드 버튼 클릭시 간격감시 제거

    public void removeInterval() {

        //간격감시 제거 후에 맵을 눌렀을 시 나오지 않게 함
        mMap.setOnMapClickListener((PointF, latLng) ->
                remove(latLng)
        );
        remove(mListIntervalMarkers.get(0).getPosition( ));
    }

    //간격감시때 그린 polygone과 polyline을 제거
    public void remove(LatLng latLng) {

        Button btnMissionComplete = (Button) findViewById(R.id.complete);

        mListIntervalMarkers.get(0).setMap(null);
        mListIntervalMarkers.get(2).setMap(null);
        mListIntervalMarkers.get(1).setMap(null);
        mListIntervalMarkers.get(3).setMap(null);
        mIntervalPolygon.setMap(null);
        mIntervalPolyline.setMap(null);
        mCountMeter.clear();
        mIntervlaCountValue = 0;
        mIntervalMission.clear();

        btnMissionComplete.setVisibility(View.INVISIBLE);
        btnMissionComplete.setText(getString(R.string.mission_termination));
        alertUser(getString(R.string.mission_removal));
    }

////////////////////////////////////(간격 감시 기능)////////////////////////////////////////////////


    //설정 간격에 따른 polyline 그림
    public void checkMeter(double maxMeter, double check_Miter) {

        double MulCheck = 0;
        int MeterCount = 0;

        LatLong A_latLong = new LatLong(mListIntervalMarkers.get(0).getPosition( ).latitude, mListIntervalMarkers.get(0).getPosition( ).longitude);
        LatLong B_latLong = new LatLong(mListIntervalMarkers.get(1).getPosition( ).latitude, mListIntervalMarkers.get(1).getPosition( ).longitude);
        LatLong end_A = new LatLong(mListIntervalMarkers.get(2).getPosition().latitude,mListIntervalMarkers.get(2).getPosition().longitude);
        LatLong end_B = new LatLong(mListIntervalMarkers.get(3).getPosition().latitude,mListIntervalMarkers.get(3).getPosition().longitude);

        for(int i = 0; i < 4; i++)
        {
            mListIntervalMarkers.get(i).setMap(mMap);
        }

        Log.d("miterLatLng","start A : " + mListIntervalMarkers.get(0).getPosition());
        Log.d("miterLatLng","start B : " + mListIntervalMarkers.get(1).getPosition());
        double degree = MathUtils.getHeadingFromCoordinates(A_latLong, B_latLong);

        LatLong startPoint_A;
        LatLong startPoint_B;

        for (int i = 0; MulCheck < maxMeter; i++) {

            MulCheck = check_Miter * i;

            if ((i == 0) || (i % 2 == 0)) {
                startPoint_A = MathUtils.newCoordFromBearingAndDistance(A_latLong, degree + 90,
                        MulCheck);

                startPoint_B = MathUtils.newCoordFromBearingAndDistance(B_latLong, degree + 90,
                        MulCheck);

                Collections.addAll(
                        mCountMeter,
                        new LatLng(startPoint_A.getLatitude( ), startPoint_A.getLongitude( )),
                        new LatLng(startPoint_B.getLatitude( ), startPoint_B.getLongitude( ))
                );
                if (MulCheck >= maxMeter) {
                    mCountMeter.set(MeterCount, new LatLng(end_A.getLatitude( ), end_A.getLongitude( )));
                    mCountMeter.set(MeterCount + 1, new LatLng(end_B.getLatitude( ), end_B.getLongitude( )));
                }
                MeterCount += 2;
                Log.d("miter1", "pointA : " + new LatLng(startPoint_A.getLatitude( ), startPoint_A.getLongitude( )));
                Log.d("miter1", "pointB : " + new LatLng(startPoint_B.getLatitude( ), startPoint_B.getLongitude( )));

            } else if (i % 2 != 0) {
                startPoint_A = MathUtils.newCoordFromBearingAndDistance(A_latLong, degree + 90,
                        MulCheck);

                startPoint_B = MathUtils.newCoordFromBearingAndDistance(B_latLong, degree + 90,
                        MulCheck);

                Collections.addAll(
                        mCountMeter,
                        new LatLng(startPoint_B.getLatitude( ), startPoint_B.getLongitude( )),
                        new LatLng(startPoint_A.getLatitude( ), startPoint_A.getLongitude( ))
                );
                if (MulCheck >= maxMeter) {
                    mCountMeter.set(MeterCount, new LatLng(end_B.getLatitude( ), end_B.getLongitude( )));
                    mCountMeter.set(MeterCount + 1, new LatLng(end_A.getLatitude( ), end_A.getLongitude( )));
                }
                MeterCount += 2;
                Log.d("miter1", "pointB : " + new LatLng(startPoint_B.getLatitude( ), startPoint_B.getLongitude( )));
                Log.d("miter1", "pointA : " + new LatLng(startPoint_A.getLatitude( ), startPoint_A.getLongitude( )));
            }
        }
        alertUser("드론 간격거리(m) : " + check_Miter);
        mIntervalPolyline.setCoords(mCountMeter);
        mIntervalPolyline.setMap(mMap);
        ++mIntervlaCountValue;

        for(int i = 0; i<mCountMeter.size(); i++){
            Log.d("miterLatLng", "mCountMeter : " + mCountMeter.get(i));
        }
        Log.d("miterLatLng","mCountMeter start A : " + mCountMeter.get(0));
        Log.d("miterLatLng","mCountMeter start B : " + mCountMeter.get(1));
    }

    //가로길이와 간격거리 설정
    public void intervalDialog(){

        View dialogView = getLayoutInflater().inflate(R.layout.activity_sub,null);
        final EditText maxNumText = (EditText)dialogView.findViewById(R.id.intervalMaxNum);
        final EditText gapNumText = (EditText)dialogView.findViewById(R.id.intervalGapNum);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);


        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener( ) {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String MaxNumber = maxNumText.getText().toString();
                String GapNumber = gapNumText.getText().toString();
                pointA(Double.parseDouble(MaxNumber), Double.parseDouble(GapNumber));
                Log.d("number1","pointA Check");
                dialog.cancel();

            }
        }).setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener( ) {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.setTitle(getString(R.string.interval_monitoring));
        alertDialog.show();

    }

//////////////////////////////////(간격감시 모드시 auto모드 작동)///////////////////////////////////

    //AUTO 모드 변환전 MISSION에 WAYPOINT전송
    public void autoInterval(){


        for(mIndex = 0; mIndex<mCountMeter.size();mIndex++) {
            Waypoint intervalWaypoint = new Waypoint();

            intervalWaypoint.setCoordinate(new LatLongAlt(mCountMeter.get(mIndex).latitude, mCountMeter.get(mIndex).longitude, mRecentAltitude));
            intervalWaypoint.setDelay(1);
            mIntervalMission.addMissionItem(intervalWaypoint);
            Log.d("interval1","mIndex : " + mIndex);
            Log.d("interval1","mission : " + mIntervalMission.getMissionItem(mIndex));
            Log.d("interval1","wayPoint : " + intervalWaypoint);
        }
        MissionApi.getApi(this.mDrone).setMission(mIntervalMission, true);

    }

    //받은 MISSION으로 AUTO모드로 전환
    public void startInterval(){

        VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_AUTO, new SimpleCommandListener( ) {

            @Override
            public void onSuccess() {
                alertUser(getString(R.string.start_mission));
            }

            @Override
            public void onError(int executionError) {
                alertUser(getString(R.string.cannot_change_auto_mode));
            }

            @Override
            public void onTimeout() {
                alertUser(getString(R.string.cannot_change_auto_mode));
            }
        });
    }

    //AUTO모드를 LOITER로 변환 / 멈춤
    public void stoppedInterval(){
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);

        if (vehicleState.getVehicleMode( ) == VehicleMode.COPTER_AUTO) {
            VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener( ) {

                @Override
                public void onSuccess() {
                    alertUser(getString(R.string.stop_mission));
                }

                @Override
                public void onError(int executionError) {
                    alertUser(getString(R.string.cannot_change_loiter_mode));
                }

                @Override
                public void onTimeout() {
                    alertUser(getString(R.string.cannot_change_loiter_mode));
                }
            });
        }
    }

    //중지후 재시작시 중지전 전MISSION을 수행
    public void restartInterval(){

        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);

        if (vehicleState.getVehicleMode( ) == VehicleMode.COPTER_LOITER) {
            VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_AUTO, new SimpleCommandListener( ) {

                @Override
                public void onSuccess() {
                    alertUser(getString(R.string.restart_mission));
                }

                @Override
                public void onError(int executionError) {
                    alertUser(getString(R.string.cannot_change_auto_mode));
                }

                @Override
                public void onTimeout() {
                    alertUser(getString(R.string.cannot_change_auto_mode));
                }
            });
        }
    }

    //임무 전송 버튼
    public void missionTransmissionButton(){

        Button btnMissionComplete = (Button) findViewById(R.id.complete);

        btnMissionComplete.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {

                if (mCountMeter.size( ) > 0) {
                    if (btnMissionComplete.getText( ).equals(getString(R.string.mission_transmission))) {

                        btnMissionComplete.setText(getString(R.string.mission_start));
                        autoInterval( );
                        alertUser(getString(R.string.complete_transmission_mission));

                    } else if (btnMissionComplete.getText( ).equals(getString(R.string.mission_start))) {

                        btnMissionComplete.setText(getString(R.string.mission_stop));
                        startInterval( );
                    } else if (btnMissionComplete.getText( ).equals(getString(R.string.mission_stop))) {

                        if (mDroneMarker.getPosition( ) == mCountMeter.get(mCountMeter.size( ) - 1)) {
                            btnMissionComplete.setText(getString(R.string.mission_transmission));
                        } else {
                            btnMissionComplete.setText(getString(R.string.mission_restart));
                        }
                        stoppedInterval( );
                    } else if (btnMissionComplete.getText( ).equals(getString(R.string.mission_restart))) {

                        btnMissionComplete.setText(getString(R.string.mission_stop));
                        restartInterval( );
                    }

                }
            }
        });
    }

//////////////////////////////////////////(드론 모드 변경)//////////////////////////////////////////

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.mModeSelector.getSelectedItem( );

        VehicleApi.getApi(this.mDrone).setVehicleMode(vehicleMode, new AbstractCommandListener( ) {
            @Override
            public void onSuccess() {
                alertUser(getString(R.string.complete_change_drone_mode));
            }

            @Override
            public void onError(int executionError) {
                alertUser(getString(R.string.failure_change_drone_mode) + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser(getString(R.string.time_out_change_drone_mode));
            }
        });
    }

    protected void updateVehicleModesForType(int droneType) {

        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this,
                android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.mModeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode( );
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.mModeSelector.getAdapter( );
        this.mModeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

///////////////////////////////////////////(고도 확인)//////////////////////////////////////////////

    protected double mRecentAltitude = 0;

    public void updateAltitude() {

        Altitude currentAltitude = this.mDrone.getAttribute(AttributeType.ALTITUDE);
        mRecentAltitude = currentAltitude.getRelativeAltitude();
        TextView droneAltitude = (TextView) findViewById(R.id.altitude);
        String straltitude = String.format("%.1f",mRecentAltitude);
        droneAltitude.setText(getString(R.string.altitude) + straltitude + getString(R.string.meter_unit));
    }

///////////////////////////////////////////(YAW값 확인)/////////////////////////////////////////////

    public float updateYAW() {

        Attitude attitude = this.mDrone.getAttribute(AttributeType.ATTITUDE);
        TextView droneYaw = (TextView) findViewById(R.id.yaw);
        float angle = (float) attitude.getYaw( );
        if (angle < 0) {
            angle = (float) attitude.getYaw( ) + 360;
        }
        droneYaw.setText(getString(R.string.yaw) + Math.round(angle) + getString(R.string.yaw_unit));
        Log.d("yaw", "YAW : " + Math.round(angle));
        return angle;
    }

///////////////////////////////////////////(위성갯수 확인)//////////////////////////////////////////

    public void updateSatellites() {
        Gps droneGps = this.mDrone.getAttribute(AttributeType.GPS);
        TextView gps = (TextView) findViewById(R.id.textView);
        gps.setText(getString(R.string.satellite) + droneGps.getSatellitesCount( ));
        Log.d("check", "위성 : " + droneGps.getSatellitesCount( ));
    }

///////////////////////////////////////////(실시간 드론위치)////////////////////////////////////////

    public void updateDronePosition() {

        Gps droneGps = this.mDrone.getAttribute(AttributeType.GPS);
        LatLong vehiclePosition = droneGps.getPosition( );
        Log.d("position", "드론 위치 : " + vehiclePosition);

        float angle = updateYAW( );

        mDroneMarker.setMap(null);//그 전 마커들 지워줌
        mDroneMarker.setPosition(new LatLng(vehiclePosition.getLatitude( ), vehiclePosition.getLongitude( )));
        mDroneMarker.setAngle(angle); // 드론을 돌리면 yaw값이 변경되며 마커 모양을 변경시켜줌
        mDroneMarker.setIcon(OverlayImage.fromResource(R.drawable.next));
        mDroneMarker.setWidth(80);
        mDroneMarker.setHeight(330);
        mDroneMarker.setAnchor(new PointF(0.5F, 0.9F));
        mDroneMarker.setMap(mMap); // 찍히는 좌표마다 marker 표시

        updateMapLock(new LatLng(vehiclePosition.getLatitude( ), vehiclePosition.getLongitude( ))); // 맵 잠금 / 이동 버튼

        Collections.addAll(
                mFlightPath,
                new LatLng(vehiclePosition.getLatitude( ), vehiclePosition.getLongitude( )));
        mFlightDronePolyline.setCoords(mFlightPath);
        mFlightDronePolyline.setColor(Color.MAGENTA);
        mFlightDronePolyline.setMap(mMap);

        mMap.setLocationSource(locationSource);
        mMap.setLocationTrackingMode(LocationTrackingMode.NoFollow);

    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        ConnectionParameter params = ConnectionParameter.newUdpConnection(null);

        this.mDrone.connect(params);

        this.mMap = naverMap;

        UiSettings uiSettings = naverMap.getUiSettings( );

        uiSettings.setZoomControlEnabled(false);

        uiSettings.setLogoMargin(16, 500, 1200, 1);

        uiSettings.setScaleBarEnabled(false);

        updateMapTypeButton( ); // 지도 타입 변경 버튼

        updateIntellectualMap( ); // 지적도 on / off 버튼
    }

    // branch 새로 생성
}