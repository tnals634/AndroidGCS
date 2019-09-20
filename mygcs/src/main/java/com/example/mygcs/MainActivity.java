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
    NaverMap mNaverMap;

    double mDonreAltitude = 3;

    ArrayList<LatLng> mFlightPath = new ArrayList<>( );
    ArrayList<String> mList = new ArrayList<>( );
    ArrayList<Long> mCheckTime = new ArrayList<>( );
    PolylineOverlay mFlightDronePolyline = new PolylineOverlay( );

    Context mContext = this;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    long mStartTime;
    long mEndTime;
    int mCountTime = 0;
    SimpleTextAdapter mAdapter;

    PolygonOverlay mIntervalPolygon = new PolygonOverlay( );
    Marker[][] mIntervalMarkerAB = new Marker[2][2]; // mIntervalMarkerAB[0][0] = start_A, mIntervalMarkerAB[0][1] = sub_A, mIntervalMarkerAB[1][0] = start_B, mIntervalMarkerAB[1][1] = sub_B
    int mIntervalCount = 0;

    ArrayList<LatLng> mCountMeter = new ArrayList<>( );
    PolylineOverlay mIntervalPolyline = new PolylineOverlay( );
    
    double mIntervalMaxMeter;
    int mIndex;
    Mission mIntervalMission = new Mission();

    protected double mRecentAltitude = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);

        getWindow( ).setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideNavigation( );

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

    private void hideNavigation() {
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

///////////////////////////////////////////(arming 부분)////////////////////////////////////////////

    public void onArmButtonTap(View view) {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying( )) {
            // Land
            VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener( ) {
                @Override
                public void onError(int executionError) {
                    alertUser("착륙 시킬 수 없습니다.");
                }

                @Override
                public void onTimeout() {
                    alertUser("착륙 시킬 수 없습니다.");
                }
            });
        } else if (vehicleState.isArmed( )) {
            // Take off
            ControlApi.getApi(this.mDrone).takeoff(mDonreAltitude, new AbstractCommandListener( ) {

                @Override
                public void onSuccess() {
                    alertUser("이륙 중...");
                }

                @Override
                public void onError(int i) {
                    alertUser("이륙할 수 없습니다.");
                }

                @Override
                public void onTimeout() {
                    alertUser("이륙할 수 없습니다.");
                }

            });
            Log.d("mDonreAltitude", "값 : " + mDonreAltitude);
        } else if (!vehicleState.isConnected( )) {
            // Connect
            alertUser("먼저 드론과 연결하십시오.");
        } else {
            if (vehicleState.getVehicleMode( ) == VehicleMode.COPTER_LAND) {
                VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_ALT_HOLD, new SimpleCommandListener( ) {
                    @Override
                    public void onError(int executionError) {
                        alertUser("alt_hold 모드로 전환할 수 없습니다.");
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("alt_hold 모드로 전환할 수 없습니다.");
                    }
                });
            }
            armingDialog( );
        }
    }

    protected void updatearmButton() {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.buttonTakeoff);


        if (vehicleState.isFlying( )) {
            armButton.setText("LAND");
        } else if (vehicleState.isArmed( )) {
            armButton.setText("TAKE_OFF");
        } else if (vehicleState.isConnected( )) {
            armButton.setText("ARM");
        }
    }

    public void armButton() {

        VehicleApi.getApi(this.mDrone).arm(true, false, new SimpleCommandListener( ) {
            @Override
            public void onError(int executionError) {
                alertUser("arming 할 수 없습니다.");
            }

            @Override
            public void onTimeout() {
                alertUser("Arming 시간이 초과되었습니다.");
            }
        });
    }

    private void armingDialog() {
        AlertDialog.Builder arm_diaglog = new AlertDialog.Builder(this);
        arm_diaglog.setMessage("시동을 거시겠습니까?").setCancelable(false).setPositiveButton("아니오",
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel( );
                    }
                }).setNegativeButton("예",
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        armButton( );
                        dialog.cancel( );
                    }
                });
        AlertDialog alert = arm_diaglog.create( );

        alert.setTitle("Arming");
        alert.show( );
    }

///////////////////////////////////////////(가이드 모드)////////////////////////////////////////////

    public void startGuideMode() {
        mNaverMap.setOnMapLongClickListener((PointF, latLng) ->
                guideDialog(PointF, latLng)
        );
    }

    public void guideMarker(PointF point, LatLng latLng) {

        mGuideMarker.setPosition(latLng);
        mGuideMarker.setIcon(OverlayImage.fromResource(R.drawable.marker_end));
        mGuideMarker.setWidth(100);
        mGuideMarker.setHeight(100);
        mGuideMarker.setAnchor(point);
        mGuideMarker.setMap(mNaverMap);

        ControlApi.getApi(this.mDrone).goTo(new LatLong(latLng.latitude, latLng.longitude),
                true, new AbstractCommandListener( ) {
                    @Override
                    public void onSuccess() {
                        alertUser("목적지로 향합니다.");
                    }

                    @Override
                    public void onError(int i) {
                        alertUser("갈 수 없습니다.");
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("갈 수 없습니다.");
                    }
                });
        Log.d("guide", "guidePosition : " + new LatLong(latLng.latitude, latLng.longitude));
    }

    public void updateDistanceFromTarget() {
        LatLng gMarker = new LatLng(mGuideMarker.getPosition( ).latitude, mGuideMarker.getPosition( ).longitude);
        LatLng dMarker = new LatLng(mDroneMarker.getPosition( ).latitude, mDroneMarker.getPosition( ).longitude);
        double btween = gMarker.distanceTo(dMarker);

        if (btween <= 1) {
            Toast.makeText(this, "목적지에 도착했습니다.", Toast.LENGTH_SHORT).show( );
        }
        Log.d("guide", "gMarker.distanceTo(dMarker); : " + gMarker.distanceTo(dMarker));
    }

    public void guideDialog(PointF point, LatLng latlng) {
        AlertDialog.Builder guide_diaglog = new AlertDialog.Builder(this);
        guide_diaglog.setMessage("가이드모드로 전환하시겠습니까?").setCancelable(false).setPositiveButton("아니오",
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel( );
                    }
                }).setNegativeButton("예",
                new DialogInterface.OnClickListener( ) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        guideMarker(point, latlng);
                        dialog.cancel( );
                    }
                });
        AlertDialog alert = guide_diaglog.create( );

        alert.setTitle("가이드모드");
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
                alertUser("드론 연결했습니다.");
                updatearmButton( );
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
                updatearmButton( );
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
        final Button btnBasicMap = (Button) findViewById(R.id.basic_map);
        final Button btnTerrainMap = (Button) findViewById(R.id.terrain_map);
        final Button btnSatelliteMap = (Button) findViewById(R.id.satellite_map);

        final Button btnOnMap = (Button) findViewById(R.id.intellectual_map_on);
        final Button btnOffMap = (Button) findViewById(R.id.intellectual_map_off);

        final Button btnMapLock = (Button) findViewById(R.id.lock);
        final Button btnMapUnLock = (Button) findViewById(R.id.unlock);

        btnMapType.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {

                if (btnBasicMap.getVisibility( ) != v.VISIBLE) {

                    btnBasicMap.setVisibility(v.VISIBLE);
                    btnTerrainMap.setVisibility(v.VISIBLE);
                    btnSatelliteMap.setVisibility(v.VISIBLE);

                    btnOnMap.setVisibility(v.GONE);
                    btnOffMap.setVisibility(v.GONE);

                    btnMapLock.setVisibility(v.GONE);
                    btnMapUnLock.setVisibility(v.GONE);

                    if (btnMapType.getText( ) == btnBasicMap.getText( )) {
                        btnBasicMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    } else if (btnMapType.getText( ) == btnTerrainMap.getText( )) {
                        btnTerrainMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    } else if (btnMapType.getText( ) == btnSatelliteMap.getText( )) {
                        btnSatelliteMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    }
                } else if (btnBasicMap.getVisibility( ) == v.VISIBLE) {
                    btnBasicMap.setVisibility(v.INVISIBLE);
                    btnTerrainMap.setVisibility(v.INVISIBLE);
                    btnSatelliteMap.setVisibility(v.INVISIBLE);
                }
            }
        });
        btnBasicMap.setOnClickListener(new Button.OnClickListener( ) { //일반지도로 변경
            @Override
            public void onClick(View v) {
                btnMapType.setText("일반지도");
                mNaverMap.setMapType(NaverMap.MapType.Basic);
                btnBasicMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnTerrainMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnSatelliteMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnBasicMap.setVisibility(v.GONE);
                btnTerrainMap.setVisibility(v.GONE);
                btnSatelliteMap.setVisibility(v.GONE);
            }
        });
        btnTerrainMap.setOnClickListener(new Button.OnClickListener( ) { //지형도로 변경
            @Override
            public void onClick(View v) {
                btnMapType.setText("지형도");
                mNaverMap.setMapType(NaverMap.MapType.Terrain);
                btnBasicMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnTerrainMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnSatelliteMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnBasicMap.setVisibility(v.GONE);
                btnTerrainMap.setVisibility(v.GONE);
                btnSatelliteMap.setVisibility(v.GONE);
            }
        });
        btnSatelliteMap.setOnClickListener(new Button.OnClickListener( ) { //위성지도로 변경
            @Override
            public void onClick(View v) {

                btnMapType.setText("위성지도");
                mNaverMap.setMapType(NaverMap.MapType.Satellite);
                btnBasicMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnTerrainMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnSatelliteMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnBasicMap.setVisibility(v.GONE);
                btnTerrainMap.setVisibility(v.GONE);
                btnSatelliteMap.setVisibility(v.GONE);
            }
        });
    }

///////////////////////////////////////////(맵 잠금 버튼)//////////////////////////////////////////

    public void updatebtnMapLock(final LatLng latLng) {

        final Button btnMapLocking = (Button) findViewById(R.id.locking);
        final Button btnMapLock = (Button) findViewById(R.id.lock);
        final Button btnMapUnLock = (Button) findViewById(R.id.unlock);

        final Button btnBasicMap = (Button) findViewById(R.id.basic_map);
        final Button btnTerrainMap = (Button) findViewById(R.id.terrain_map);
        final Button btnSatelliteMap = (Button) findViewById(R.id.satellite_map);

        final Button btnOnMap = (Button) findViewById(R.id.intellectual_map_on);
        final Button btnOffMap = (Button) findViewById(R.id.intellectual_map_off);

        if (btnMapLocking.getText( ) == btnMapLock.getText( )) {
            CameraUpdate cameraupdate = CameraUpdate.scrollTo(latLng);
            mNaverMap.moveCamera(cameraupdate); //찍히는 좌표마다 카메라가 따라다님
        }

        btnMapLocking.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                if (btnMapLock.getVisibility( ) != v.VISIBLE) {
                    btnMapLock.setVisibility(v.VISIBLE);
                    btnMapUnLock.setVisibility(v.VISIBLE);

                    btnBasicMap.setVisibility(v.GONE);
                    btnTerrainMap.setVisibility(v.GONE);
                    btnSatelliteMap.setVisibility(v.GONE);

                    btnOnMap.setVisibility(v.GONE);
                    btnOffMap.setVisibility(v.GONE);

                    if (btnMapLocking.getText( ) == btnMapLock.getText( )) {
                        btnMapLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    } else if (btnMapLocking.getText( ) == btnMapUnLock.getText( )) {
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
                btnMapLocking.setText("맵잠금");
                CameraUpdate cameraupdate = CameraUpdate.scrollTo(latLng);
                mNaverMap.moveCamera(cameraupdate); //찍히는 좌표마다 카메라가 따라다님
                btnMapLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnMapUnLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapLock.setVisibility(v.GONE);
                btnMapUnLock.setVisibility(v.GONE);
            }
        });

        btnMapUnLock.setOnClickListener(new Button.OnClickListener( ) { //맵 이동
            @Override
            public void onClick(View v) {
                btnMapLocking.setText("맵이동");
                btnMapLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnMapUnLock.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnMapLock.setVisibility(v.GONE);
                btnMapUnLock.setVisibility(v.GONE);
            }
        });
    }

///////////////////////////////////////////(지적도 버튼)////////////////////////////////////////////

    public void updateIntellectualMap() {

        final Button btnOnMap = (Button) findViewById(R.id.intellectual_map_on);
        final Button btnOffMap = (Button) findViewById(R.id.intellectual_map_off);
        final Button map_check = (Button) findViewById(R.id.intellectual_map);

        final Button btnMapLock = (Button) findViewById(R.id.lock);
        final Button btnMapUnLock = (Button) findViewById(R.id.unlock);

        final Button btnBasicMap = (Button) findViewById(R.id.basic_map);
        final Button btnTerrainMap = (Button) findViewById(R.id.terrain_map);
        final Button btnSatelliteMap = (Button) findViewById(R.id.satellite_map);

        map_check.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                if (btnOnMap.getVisibility( ) != v.VISIBLE) {
                    btnOnMap.setVisibility(v.VISIBLE);
                    btnOffMap.setVisibility(v.VISIBLE);

                    btnBasicMap.setVisibility(v.GONE);
                    btnTerrainMap.setVisibility(v.GONE);
                    btnSatelliteMap.setVisibility(v.GONE);


                    btnMapLock.setVisibility(v.GONE);
                    btnMapUnLock.setVisibility(v.GONE);

                    if (map_check.getText( ) == btnOffMap.getText( )) {
                        btnOffMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    } else if (map_check.getText( ) == btnOnMap.getText( )) {
                        btnOnMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                    }
                } else if (btnOnMap.getVisibility( ) == v.VISIBLE) {
                    btnOnMap.setVisibility(v.INVISIBLE);
                    btnOffMap.setVisibility(v.INVISIBLE);
                }
            }
        });
        btnOnMap.setOnClickListener(new Button.OnClickListener( ) { //지적도 on
            @Override
            public void onClick(View v) {
                map_check.setText("지적도on");
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
                btnOnMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnOffMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnOnMap.setVisibility(v.GONE);
                btnOffMap.setVisibility(v.GONE);

            }
        });

        btnOffMap.setOnClickListener(new Button.OnClickListener( ) { // 지적도 off
            @Override
            public void onClick(View v) {
                map_check.setText("지적도off");
                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
                btnOnMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button));
                btnOffMap.setBackground(ContextCompat.getDrawable(mContext, R.drawable.round_button_arm));
                btnOnMap.setVisibility(v.GONE);
                btnOffMap.setVisibility(v.GONE);
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
                removeAll( );
            }
        });
    }

///////////////////////////////////////////(altitude setting 버튼)//////////////////////////////////

    public void updateAltitudeSetting() {

        final Button btnAltitudeSetting = (Button) findViewById(R.id.altitude_setting);
        final Button btnAltitudeUp = (Button) findViewById(R.id.altitude_up);
        final Button btnAltitudeDown = (Button) findViewById(R.id.altitude_down);

        TextView droneAltitudeText = (TextView) findViewById(R.id.altitude_setting);
        droneAltitudeText.setText(Math.round(mDonreAltitude) + "m\n이륙고도");
        btnAltitudeSetting.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {
                if (btnAltitudeUp.getVisibility( ) != v.VISIBLE) {
                    btnAltitudeUp.setVisibility(v.VISIBLE);
                    btnAltitudeDown.setVisibility(v.VISIBLE);

                } else if (btnAltitudeUp.getVisibility( ) == v.VISIBLE) {
                    btnAltitudeUp.setVisibility(v.INVISIBLE);
                    btnAltitudeDown.setVisibility(v.INVISIBLE);
                }
            }
        });
        btnAltitudeUp.setOnClickListener(new Button.OnClickListener( ) { //고도 높임
            @Override
            public void onClick(View v) {
                updateSettingAltitudeUp( );
            }
        });

        btnAltitudeDown.setOnClickListener(new Button.OnClickListener( ) { //고도 낮춤
            @Override
            public void onClick(View v) {
                updateSettingAltitudeDown( );
            }
        });
    }

    public void updateSettingAltitudeUp() {
        ++mDonreAltitude;
        TextView droneAltitudeText = (TextView) findViewById(R.id.altitude_setting);
        droneAltitudeText.setText(Math.round(mDonreAltitude) + "m\n이륙고도");
    }

    public void updateSettingAltitudeDown() {
        --mDonreAltitude;
        TextView droneAltitudeText = (TextView) findViewById(R.id.altitude_setting);
        droneAltitudeText.setText(Math.round(mDonreAltitude) + "m\n이륙고도");
    }

///////////////////////////////////////////(드론 모드 변경)/////////////////////////////////////////

    public void updateDroneMode() {

        final Button btnModeSelect = (Button) findViewById(R.id.drone_mode); //모드설정
        final Button btnBasicMode = (Button) findViewById(R.id.basicMode); // 일반모드
        final Button btnFlightRoutes = (Button) findViewById(R.id.flightRoutes); // 경로비행
        final Button btnIntervalMonitoring = (Button) findViewById(R.id.intervalMonitoring); // 간격 감시
        final Button btnAreaMonitoring = (Button) findViewById(R.id.areaMonitoring); // 면적 감시
        final Button btnCompleteMission = (Button) findViewById(R.id.complete); // 간격 감시 후 임무 전송 버튼

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
                btnModeSelect.setText("일반모드");
                btnBasicMode.setVisibility(v.INVISIBLE);
                btnFlightRoutes.setVisibility(v.INVISIBLE);
                btnIntervalMonitoring.setVisibility(v.INVISIBLE);
                btnAreaMonitoring.setVisibility(v.INVISIBLE);
                removeAll( );
            }
        });

        btnFlightRoutes.setOnClickListener(new Button.OnClickListener( ) { // 경로비행
            @Override
            public void onClick(View v) {
                btnModeSelect.setText("경로비행");
                btnBasicMode.setVisibility(v.INVISIBLE);
                btnFlightRoutes.setVisibility(v.INVISIBLE);
                btnIntervalMonitoring.setVisibility(v.INVISIBLE);
                btnAreaMonitoring.setVisibility(v.INVISIBLE);
                removeAll( );
            }
        });

        btnIntervalMonitoring.setOnClickListener(new Button.OnClickListener( ) { // 간격 감시
            @Override
            public void onClick(View v) {
                btnModeSelect.setText("간격감시");
                intervalDialog();
                btnCompleteMission.setVisibility(v.VISIBLE);
                btnBasicMode.setVisibility(v.INVISIBLE);
                btnFlightRoutes.setVisibility(v.INVISIBLE);
                btnIntervalMonitoring.setVisibility(v.INVISIBLE);
                btnAreaMonitoring.setVisibility(v.INVISIBLE);

            }
        });

        btnAreaMonitoring.setOnClickListener(new Button.OnClickListener( ) { // 면적 감시
            @Override
            public void onClick(View v) {
                btnModeSelect.setText("면적감시");
                btnBasicMode.setVisibility(v.INVISIBLE);
                btnFlightRoutes.setVisibility(v.INVISIBLE);
                btnIntervalMonitoring.setVisibility(v.INVISIBLE);
                btnAreaMonitoring.setVisibility(v.INVISIBLE);
                removeAll( );
            }
        });
    }

///////////////////////////////////////////(스피드 확인)////////////////////////////////////////////

    private void updateSpeed() {

        Speed speed = this.mDrone.getAttribute(AttributeType.SPEED);
        TextView droneSpeed = (TextView) findViewById(R.id.speed);
        droneSpeed.setText("속도 " + Math.round(speed.getGroundSpeed( )) + "m/s");
        Log.d("speed", "속도  " + Math.round(speed.getGroundSpeed( )) + "m/s");

    }

///////////////////////////////////////////(배터리 확인)////////////////////////////////////////////

    private void updateBattery() {

        Battery battery = this.mDrone.getAttribute(AttributeType.BATTERY);
        TextView droneBattery = (TextView) findViewById(R.id.voltage);
        String strBattery = String.format("%.1f", battery.getBatteryVoltage( ));
        droneBattery.setText("전압 " + strBattery + "v");
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
                alertUser("연결 실패 : " + msg);
                break;
        }
    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android 연결되었습니다.");
        this.mControlTower.registerDrone(this.mDrone, this.mHandler);
        this.mDrone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android 중단되었습니다.");
    }

//////////////////////////////(recyclerView (메시지 뷰) 생성)///////////////////////////////////////

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
        double lastTime = (mEndTime - mCheckTime.get(mCountTime)) / 1000.0;
        Log.d("time1", "==============================================");
        Log.d("time1", "lastTime : " + lastTime);
        Log.d("time1", "==============================================");
        if (mCountTime < mCheckTime.size( )) {
            if (lastTime >= 5) {
                mList.remove(0);
                Log.d("time1", "countCheck : " + mCountTime);
                Log.d("time1", "listSIZE : " + mList.size( ));
                Log.d("time1", "mCheckTimeSIZE : " + mCheckTime.size( ));
                Log.d("time1", "mStartTime : " + mCheckTime.get(mCountTime));
                Log.d("time1", "mEndTime : " + mEndTime);
                if (mCountTime + 1 < mCheckTime.size( )) {
                    mCountTime++;
                }
                Log.d("time1", "===================");
                Log.d("time1", "nextCountCheck : " + mCountTime);
                Log.d("time1", "mCheckTimeSIZE : " + mCheckTime.size( ));
                Log.d("time1", "nextmStartTime : " + mCheckTime.get(mCountTime));
                Log.d("time1", "nextmEndTime : " + mEndTime);
                Log.d("time1", "checkTime : " + lastTime);
                mAdapter.notifyDataSetChanged( );
                recyclerView.setAdapter(mAdapter);
            }
        }
        mEndTime = SystemClock.elapsedRealtime( );
    }

////////////////////////////////////////(A,B지점 지정 )/////////////////////////////////////////////

//    Marker start_A = new Marker( );
//    Marker sub_A = new Marker( );
//    Marker start_B = new Marker( );
//    Marker sub_B = new Marker( );
    //pointA 마커 생성
    public void IntervalPointA(LatLng latLng, double maxMeter, double gapMeter) {

//        start_A.setPosition(latLng);
//        start_A.setWidth(80);
//        start_A.setHeight(80);
//        start_A.setIcon(OverlayImage.fromResource(R.drawable.a_point));
//        start_A.setMap(mNaverMap);
        
        mIntervalMarkerAB[0][0].setPosition(latLng);
        mIntervalMarkerAB[0][0].setWidth(80);
        mIntervalMarkerAB[0][0].setHeight(80);
        mIntervalMarkerAB[0][0].setIcon(OverlayImage.fromResource(R.drawable.a_point));
        mIntervalMarkerAB[0][0].setMap(mNaverMap);
        pointB(maxMeter, gapMeter);
        mIntervalMaxMeter = maxMeter;
    }

    //pointB 마커 생성
    public void intervalPointB(LatLng latLng, double maxMeter, double gapMeter) {

        if (mIntervalCount == 0) {
//            start_B.setPosition(latLng);
//            start_B.setWidth(80);
//            start_B.setHeight(80);
//            start_B.setIcon(OverlayImage.fromResource(R.drawable.b_point));
//            start_B.setMap(mNaverMap);

            mIntervalMarkerAB[1][0].setPosition(latLng);
            mIntervalMarkerAB[1][0].setWidth(80);
            mIntervalMarkerAB[1][0].setHeight(80);
            mIntervalMarkerAB[1][0].setIcon(OverlayImage.fromResource(R.drawable.b_point));
            mIntervalMarkerAB[1][0].setMap(mNaverMap);
            interval(maxMeter);
            checkMiter(maxMeter,gapMeter);
        }
    }

    //pointA 생성할 곳 클릭
    public void pointA(double maxMeter, double gapMeter) {
        mNaverMap.setOnMapClickListener((PointF, latLng) ->
                IntervalPointA(latLng, maxMeter, gapMeter)
        );
    }

    //pointB 생성할 곳 클릭
    public void pointB(double maxMeter,double gapMeter) {
        mNaverMap.setOnMapClickListener((PointF, latLng) ->
                intervalPointB(latLng, maxMeter, gapMeter)
        );
    }

    //간격감시 polygon 생성
    public void interval(double maxMeter) {

        LatLong latLongA = new LatLong(mIntervalMarkerAB[0][0].getPosition( ).latitude, mIntervalMarkerAB[0][0].getPosition( ).longitude);
        LatLong latLongB = new LatLong(mIntervalMarkerAB[1][0].getPosition( ).latitude, mIntervalMarkerAB[1][0].getPosition( ).longitude);
        double degree = MathUtils.getHeadingFromCoordinates(latLongA, latLongB);

        LatLong positionA = MathUtils.newCoordFromBearingAndDistance(latLongA, degree + 90, maxMeter);
        LatLng position_subA = new LatLng(positionA.getLatitude( ), positionA.getLongitude( ));
//        sub_A.setPosition(position_subA);
//        sub_A.setWidth(80);
//        sub_A.setHeight(80);
//        sub_A.setIcon(OverlayImage.fromResource(R.drawable.sub_point));
//        sub_A.setMap(mNaverMap);

        mIntervalMarkerAB[0][1].setPosition(position_subA);
        mIntervalMarkerAB[0][1].setWidth(80);
        mIntervalMarkerAB[0][1].setHeight(80);
        mIntervalMarkerAB[0][1].setIcon(OverlayImage.fromResource(R.drawable.sub_point));
        mIntervalMarkerAB[0][1].setMap(mNaverMap);
        LatLong positionB = MathUtils.newCoordFromBearingAndDistance(latLongB, degree + 90, maxMeter);
        LatLng position_subB = new LatLng(positionB.getLatitude( ), positionB.getLongitude( ));
//        sub_B.setPosition(position_subB);
//        sub_B.setWidth(80);
//        sub_B.setHeight(80);
//        sub_B.setIcon(OverlayImage.fromResource(R.drawable.sub_point));
//        sub_B.setMap(mNaverMap);

        mIntervalMarkerAB[1][1].setPosition(position_subB);
        mIntervalMarkerAB[1][1].setWidth(80);
        mIntervalMarkerAB[1][1].setHeight(80);
        mIntervalMarkerAB[1][1].setIcon(OverlayImage.fromResource(R.drawable.sub_point));
        mIntervalMarkerAB[1][1].setMap(mNaverMap);

        mIntervalPolygon.setCoords(Arrays.asList(
//                start_A.getPosition( ),
//                sub_A.getPosition( ),
//                sub_B.getPosition( ),
//                start_B.getPosition( )

                mIntervalMarkerAB[0][0].getPosition( ),
                mIntervalMarkerAB[0][1].getPosition( ),
                mIntervalMarkerAB[1][1].getPosition( ),
                mIntervalMarkerAB[1][0].getPosition( )
        ));
        int color_polygon = Color.parseColor("#59FF0F00");
        mIntervalPolygon.setColor(color_polygon);
        Log.d("point_interval", "A_angle : " + mIntervalMarkerAB[0][0].getAngle( ));
        mIntervalPolygon.setMap(mNaverMap);

        alertUser("드론 가로길이(m) : " + maxMeter);

    }

    //다른 모드 버튼 클릭시 간격감시 제거

    public void removeAll() {

        //간격감시 제거 후에 맵을 눌렀을 시 나오지 않게 함
        mNaverMap.setOnMapClickListener((PointF, latLng) ->
                removeInterval(latLng)
        );
        removeInterval(mIntervalMarkerAB[0][0].getPosition( ));
    }

    //간격감시때 그린 polygone과 polyline을 제거
    public void removeInterval(LatLng latLng) {

        Button btnCompleteMission = (Button) findViewById(R.id.complete);

//        start_A.setMap(null);
//        start_B.setMap(null);
//        sub_A.setMap(null);
//        sub_B.setMap(null);
        for(int i=0;i<2;i++)
        {
            for(int j=0;j<2;j++)
            {
                mIntervalMarkerAB[i][j].setMap(null);
            }
        }
        mIntervalPolygon.setMap(null);
        mIntervalPolyline.setMap(null);
        mCountMeter.clear();
        mIntervalCount = 0;
        mIntervalMission.clear();

        btnCompleteMission.setVisibility(View.INVISIBLE);
        btnCompleteMission.setText("임무전송");
        alertUser("임무 제거");
    }

////////////////////////////////////(간격 감시 기능)////////////////////////////////////////////////

    //설정 간격에 따른 polyline 그림
    public void checkMiter(double maxMeter, double check_Miter) {

        double checkMul = 0;
        int meterCount = 0;
        
        LatLong[][] latLongAB = new LatLong[2][2];
        for(int i = 0; i < 2; i++){
            for(int j = 0; j < 2; j++){
                latLongAB[i][j] = new LatLong(mIntervalMarkerAB[i][j].getPosition().latitude, mIntervalMarkerAB[i][j].getPosition().longitude);
            }
        }
//        latLongAB[0][0] = new LatLong(mIntervalMarkerAB[0][0].getPosition().latitude, mIntervalMarkerAB[0][0].getPosition().longitude); //latLongA(startA)
//        latLongAB[0][1] = new LatLong(mIntervalMarkerAB[0][1].getPosition().latitude, mIntervalMarkerAB[0][1].getPosition().longitude); //end_A(sub_A)
//        latLongAB[1][0] = new LatLong(mIntervalMarkerAB[1][0].getPosition().latitude, mIntervalMarkerAB[1][0].getPosition().longitude); //latLongB(startB)
//        latLongAB[1][1] = new LatLong(mIntervalMarkerAB[1][1].getPosition().latitude, mIntervalMarkerAB[1][1].getPosition().longitude); //end_B(sub_B)

        Log.d("miterLatLng","start A : " + mIntervalMarkerAB[0][0].getPosition());
        Log.d("miterLatLng","start B : " + mIntervalMarkerAB[1][0].getPosition());
        double degree = MathUtils.getHeadingFromCoordinates(latLongAB[0][0],latLongAB[1][0]);

        LatLong startPointA;
        LatLong startPointB;

        for (int countCheckMeter = 0; checkMul < maxMeter; countCheckMeter++) {

            checkMul = check_Miter * countCheckMeter;

            if ((countCheckMeter == 0) || (countCheckMeter % 2 == 0)) {
                startPointA = MathUtils.newCoordFromBearingAndDistance(latLongAB[0][0], degree + 90,
                        checkMul);

                startPointB = MathUtils.newCoordFromBearingAndDistance(latLongAB[1][0], degree + 90,
                        checkMul);

                Collections.addAll(
                        mCountMeter,
                        new LatLng(startPointA.getLatitude( ), startPointA.getLongitude( )),
                        new LatLng(startPointB.getLatitude( ), startPointB.getLongitude( ))
                );
                if (checkMul >= maxMeter) {
                    mCountMeter.set(meterCount, new LatLng(latLongAB[0][1].getLatitude( ), latLongAB[0][1].getLongitude( )));
                    mCountMeter.set(meterCount + 1, new LatLng(latLongAB[1][1].getLatitude( ), latLongAB[1][1].getLongitude( )));
                }
                meterCount += 2;
                Log.d("miter1", "pointA : " + new LatLng(startPointA.getLatitude( ), startPointA.getLongitude( )));
                Log.d("miter1", "pointB : " + new LatLng(startPointB.getLatitude( ), startPointB.getLongitude( )));

            } else if (countCheckMeter % 2 != 0) {
                startPointA = MathUtils.newCoordFromBearingAndDistance(latLongAB[0][0], degree + 90,
                        checkMul);

                startPointB = MathUtils.newCoordFromBearingAndDistance(latLongAB[1][0], degree + 90,
                        checkMul);

                Collections.addAll(
                        mCountMeter,
                        new LatLng(startPointB.getLatitude( ), startPointB.getLongitude( )),
                        new LatLng(startPointA.getLatitude( ), startPointA.getLongitude( ))
                );
                if (checkMul >= maxMeter) {
                    mCountMeter.set(meterCount, new LatLng(latLongAB[1][1].getLatitude( ), latLongAB[1][1].getLongitude( )));
                    mCountMeter.set(meterCount + 1, new LatLng(latLongAB[0][1].getLatitude( ), latLongAB[0][1].getLongitude( )));
                }
                meterCount += 2;
                Log.d("miter1", "pointB : " + new LatLng(startPointB.getLatitude( ), startPointB.getLongitude( )));
                Log.d("miter1", "pointA : " + new LatLng(startPointA.getLatitude( ), startPointA.getLongitude( )));
            }
        }
        alertUser("드론 간격거리(m) : " + check_Miter);
        mIntervalPolyline.setCoords(mCountMeter);
        mIntervalPolyline.setMap(mNaverMap);
        ++mIntervalCount;

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


        builder.setPositiveButton("OK", new DialogInterface.OnClickListener( ) {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String MaxNumber = maxNumText.getText().toString();
                String GapNumber = gapNumText.getText().toString();
                pointA(Double.parseDouble(MaxNumber), Double.parseDouble(GapNumber));
                Log.d("number1","pointA Check");
                dialog.cancel();

            }
        }).setNegativeButton("NO", new DialogInterface.OnClickListener( ) {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.setTitle("간격감시");
        alertDialog.show();

    }

//////////////////////////////////(간격감시 모드시 auto모드 작동)///////////////////////////////////

    //AUTO 모드 변환전 MISSION에 WAYPOINT전송
    public void autoInterval(){

        for(mIndex = 0; mIndex < mCountMeter.size(); mIndex++) {
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
                alertUser("임무를 시작합니다.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("AUTO 모드로 전환할 수 없습니다.");
            }

            @Override
            public void onTimeout() {
                alertUser("AUTO 모드로 전환할 수 없습니다.");
            }
        });
    }

    //AUTO모드를 LOITER로 변환 / 멈춤
    public void terminateInterval(){
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);

        if (vehicleState.getVehicleMode( ) == VehicleMode.COPTER_AUTO) {
            VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener( ) {

                @Override
                public void onSuccess() {
                    alertUser("임무를 중지합니다.");
                }

                @Override
                public void onError(int executionError) {
                    alertUser("LOITER 모드로 전환할 수 없습니다.");
                }

                @Override
                public void onTimeout() {
                    alertUser("LOITER 모드로 전환할 수 없습니다.");
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
                    alertUser("임무를 재시작 합니다.");
                }

                @Override
                public void onError(int executionError) {
                    alertUser("AUTO 모드로 전환할 수 없습니다.");
                }

                @Override
                public void onTimeout() {
                    alertUser("AUTO 모드로 전환할 수 없습니다.");
                }
            });
        }
    }

    //임무 전송 버튼
    public void missionTransmissionButton(){
        Button btnCompleteMission = (Button) findViewById(R.id.complete);

        btnCompleteMission.setOnClickListener(new Button.OnClickListener( ) {
            @Override
            public void onClick(View v) {

                if (mCountMeter.size( ) > 0) {
                    if (btnCompleteMission.getText( ).equals("임무전송")) {

                        btnCompleteMission.setText("임무시작");
                        autoInterval( );
                        alertUser("임무 전송 완료");

                    } else if (btnCompleteMission.getText( ).equals("임무시작")) {

                        btnCompleteMission.setText("임무중지");
                        startInterval( );
                    } else if (btnCompleteMission.getText( ).equals("임무중지")) {

                        if (mDroneMarker.getPosition( ) == mCountMeter.get(mCountMeter.size( ) - 1)) {
                            btnCompleteMission.setText("임무전송");
                        } else {
                            btnCompleteMission.setText("임무재시작");
                        }
                        terminateInterval( );
                    } else if (btnCompleteMission.getText( ).equals("임무재시작")) {

                        btnCompleteMission.setText("임무중지");
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
                alertUser("드론 모드 변경 성공하였습니다.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("드론 모드 변경 실패 : " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("드론 모드 변경 시간 초과되었습니다.");
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

    public void updateAltitude() {

        Altitude currentAltitude = this.mDrone.getAttribute(AttributeType.ALTITUDE);
        mRecentAltitude = currentAltitude.getRelativeAltitude();
        TextView droneAltitude = (TextView) findViewById(R.id.altitude);
        String straltitude = String.format("%.1f",mRecentAltitude);
        droneAltitude.setText("고도 " + straltitude + "m");
    }

///////////////////////////////////////////(YAW값 확인)/////////////////////////////////////////////

    public float updateYAW() {

        Attitude attitude = this.mDrone.getAttribute(AttributeType.ATTITUDE);
        TextView droneYaw = (TextView) findViewById(R.id.yaw);
        float angle = (float) attitude.getYaw( );
        if (angle < 0) {
            angle = (float) attitude.getYaw( ) + 360;
        }
        droneYaw.setText("Yaw " + Math.round(angle) + "deg");
        Log.d("yaw", "YAW : " + Math.round(angle));
        return angle;
    }

///////////////////////////////////////////(위성갯수 확인)//////////////////////////////////////////

    public void updateSatellites() {
        Gps droneGps = this.mDrone.getAttribute(AttributeType.GPS);
        TextView gps = (TextView) findViewById(R.id.textView);
        gps.setText("위성 " + droneGps.getSatellitesCount( ));
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
        mDroneMarker.setMap(mNaverMap); // 찍히는 좌표마다 marker 표시

        updatebtnMapLock(new LatLng(vehiclePosition.getLatitude( ), vehiclePosition.getLongitude( ))); // 맵 잠금 / 이동 버튼

        Collections.addAll(
                mFlightPath,
                new LatLng(vehiclePosition.getLatitude( ), vehiclePosition.getLongitude( )));
        mFlightDronePolyline.setCoords(mFlightPath);
        mFlightDronePolyline.setColor(Color.MAGENTA);
        mFlightDronePolyline.setMap(mNaverMap);

        mNaverMap.setLocationSource(locationSource);
        mNaverMap.setLocationTrackingMode(LocationTrackingMode.NoFollow);

    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        ConnectionParameter params = ConnectionParameter.newUdpConnection(null);

        this.mDrone.connect(params);

        this.mNaverMap = naverMap;

        UiSettings uiSettings = naverMap.getUiSettings( );

        uiSettings.setZoomControlEnabled(false);

        uiSettings.setLogoMargin(16, 500, 1200, 1);

        uiSettings.setScaleBarEnabled(false);

        updateMapTypeButton( ); // 지도 타입 변경 버튼

        updateIntellectualMap( ); // 지적도 on / off 버튼
    }
}