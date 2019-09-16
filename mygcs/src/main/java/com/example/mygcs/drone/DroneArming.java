package com.example.mygcs.drone;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.mygcs.R;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

public class DroneArming extends AppCompatActivity {

    private Drone drone;
    double altitude_drone = 3;

    RecyclerViewCreate recyclerViewCreate = new RecyclerViewCreate();

///////////////////////////////////////////(arming 부분)////////////////////////////////////////////

    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying( )) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener( ) {
                @Override
                public void onError(int executionError) {
                    recyclerViewCreate.alertUser("착륙 시킬 수 없습니다.");
                }

                @Override
                public void onTimeout() {
                    recyclerViewCreate.alertUser("착륙 시킬 수 없습니다.");
                }
            });
        } else if (vehicleState.isArmed( )) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(altitude_drone, new AbstractCommandListener( ) {

                @Override
                public void onSuccess() {
                    recyclerViewCreate.alertUser("이륙 중...");
                }

                @Override
                public void onError(int i) {
                    recyclerViewCreate.alertUser("이륙할 수 없습니다.");
                }

                @Override
                public void onTimeout() {
                    recyclerViewCreate.alertUser("이륙할 수 없습니다.");
                }

            });
            Log.d("altitude_drone", "값 : " + altitude_drone);
        } else if (!vehicleState.isConnected( )) {
            // Connect
            recyclerViewCreate.alertUser("먼저 드론과 연결하십시오.");
        } else {
            if (vehicleState.getVehicleMode( ) == VehicleMode.COPTER_LAND) {
                VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_ALT_HOLD, new SimpleCommandListener( ) {
                    @Override
                    public void onError(int executionError) {
                        recyclerViewCreate.alertUser("alt_hold 모드로 전환할 수 없습니다.");
                    }

                    @Override
                    public void onTimeout() {
                        recyclerViewCreate.alertUser("alt_hold 모드로 전환할 수 없습니다.");
                    }
                });
            }
            DialogArming( );
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

    public void ArmButton() {

        VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener( ) {
            @Override
            public void onError(int executionError) {
                recyclerViewCreate.alertUser("arming 할 수 없습니다.");
            }

            @Override
            public void onTimeout() {
                recyclerViewCreate.alertUser("Arming 시간이 초과되었습니다.");
            }
        });
    }

    private void DialogArming() {
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
                        ArmButton( );
                        dialog.cancel( );
                    }
                });
        AlertDialog alert = arm_diaglog.create( );

        alert.setTitle("Arming");
        alert.show( );
    }

}
