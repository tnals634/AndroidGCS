package com.example.mygcs;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;

import com.naver.maps.map.MapFragment;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.android.client.utils.video.DecoderListener;
import com.o3dr.android.client.utils.video.MediaCodecManager;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener {

    MapFragment mNaverMapFragment = null;
    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    private static final int DEFAULT_UDP_PORT=14550;
    private static final int DEFAULT_USB_BAUD_RATE=57600;

    private Spinner modeSelector;

    private Button startVideoStream;
    private Button stopVideoStream;

    private Button startVideoStreamUsingObserver;
    private Button stopVideoStreamUsingObserver;

    private MediaCodecManager mediaCodecManager;

    private TextureView videoView;
    private String videoTag = "testvideotag";

    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                onFlightModeSelected(view);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent){
                //Do nothing
            }
        });

        final Button takePic = (Button) findViewById(R.id.take_photo_button);
        takePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });

        final Button toggleVideo  = (Button) findViewById(R.id.toggle_video_recording);
        toggleVideo.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                toggleVideoRecording();
            }
        });

        videoView = (TextureView) findViewById(R.id.video_content);
        videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height ){
                alertUser("Video display is available");
                startVideoStream.setEnabled(true);
                startVideoStreamUsingObserver.setEnabled(true);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height){

            }
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){
                startVideoStream.setEnabled(false);
                startVideoStreamUsingObserver.setEnabled(false);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface){

            }
        });

        startVideoStream = (Button) findViewById(R.id.start_video_stream);
        startVideoStream.setEnabled(false);
        startVideoStream.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                alertUser("Starting video stream.");
                startVideoStream(new Surface(videoView.getSurfaceTexture()));
            }
        });

        stopVideoStream = (Button) findViewById(R.id.stop_video_stream);
        stopVideoStream.setEnabled(false);
        stopVideoStream.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                alertUser("Stopping video stream.");
                stopVideoStream();
            }
        });

        startVideoStreamUsingObserver = (Button) findViewById(R.id.start_video_stream_using_observer);
        startVideoStreamUsingObserver.setEnabled(false);
        startVideoStreamUsingObserver.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                alertUser("Starting video stream using observer for video stream packets.");
                startVideoStreamForObserver();
            }
        });

        stopVideoStreamUsingObserver = (Button) findViewById(R.id.stop_video_stream_using_observer);
        stopVideoStreamUsingObserver.setEnabled(false);
        stopVideoStreamUsingObserver.setOnClickListener(new View.OnClickListener( ) {
            @Override
            public void onClick(View view) {
                alertUser("Stopping video stream using doserver for video stream packets.");
                stopVideoStreamForObserver();
            }
        });

        HandlerThread mediaCodecHandlerThread = new HandlerThread("MediaCodeHandlerThread");
        mediaCodecHandlerThread.start();
        Handler mediaCodecHandler = new Handler(mediaCodecHandlerThread.getLooper());
        mediaCodecManager = new MediaCodecManager(mediaCodecHandler);

        mainHandler = new Handler(getApplicationContext().getMainLooper());
    }

    @Override
    public void onStart(){
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop(){
        super.onStop();
        if(this.drone.isConnected()){
            this.drone.disconnect();
            updateConnectedButton(false);
        }
        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    //DroneKit-Android Listener===============================

    @Override
    public void onTowerConnected(){
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone,this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected(){
        alertUser("DroneKit-Android Interrupted");
    }

    //Drone Listener=========================================

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event){
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                checkSoloState();
                break;
            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;
            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if(newDroneType.getDroneType() != this.droneType){
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;
            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;
            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;
            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;
            case AttributeEvent.HOME_UPDATED:
                updateDistanceFromHome();
                break;
            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    private void checkSoloState(){
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if(soloState == null){
            alertUser("Unable to retrieve the solo state.");
        }
        else {
            alertUser("Solo state is up to date.");
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    //UI Events============================================

    public void onBtnConnectTap(View view){
        if(this.drone.isConnected()){
            this.drone.disconnect();
        }
        else {
            Spinner connectionSelector = (Spinner) findViewById(R.id.selectConnectionType);
            int selectedConnectionType = connectionSelector.getSelectedItemPosition();

            ConnectionParameter connectionParams = selectedConnectionType == ConnectionType.TYPE_USB
                    ? ConnectionParameter.newUsbConnection(null)
                    :ConnectionParameter.newUdpConnection(null);

            this.drone.connect(connectionParams);
        }
    }

    public void onFlightModeSelected(View view){
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VahicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener(){
            @Override
            public void onSuccess(){
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(){
                alertUser("Vehicle mode change failed : "+executionError);
            }

            @Override
            public void onTimeout(){
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    public void onArmButtonTap(View view){

    }

    //UI updating ==============================================================

    public void updateConnectedButton(Boolean isConnected){

    }

    protected void updateArmButton(){

    }

    protected void updateAltitude(){

    }

    protected void updateSpeed(){

    }

    protected void updateDistanceFromHome(){

    }

    protected void updateVehicleModesForType(int droneType){

    }

    protected void updateVehicleMode(){

    }

    //Helper methods =============================================================

    protected void alertUser(String message){

    }

    private void runOnMainThread(Runnable runnable){

    }

    protected double distanceBetweenPints(LatLongAlt pointA, LatLongAlt pointB){

    }

    private void takePhoto(){

    }

    private void toggleVideoRecording(){

    }

    private void startVideoStream(Surface videoSurface){

    }

    DecoderListener decoderListener = new DecoderListener( ) {
        @Override
        public void onDecodingStarted() {

        }

        @Override
        public void onDecodingError() {

        }

        @Override
        public void onDecodingEnded() {

        }
    };
}
