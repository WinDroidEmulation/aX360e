package aenu.ax360e;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;

// Created by aenu on 2025/7/29.
// SPDX-License-Identifier: WTFPL
public class EmulatorActivity extends Activity implements SurfaceHolder.Callback, View.OnGenericMotionListener {

    static final int DELAY_ON_CREATE=0xaeae0001;
    public static final String EXTRA_GAME_URI="game_uri";
    static SurfaceView sf=null;
    private SparseIntArray keysMap = new SparseIntArray();
    private Vibrator vibrator=null;
    private VibrationEffect vibrationEffect=null;
    boolean started=false;
    Dialog delay_dialog=null;
    final Handler delay_on_create=new Handler(new Handler.Callback(){
        @Override
        public boolean handleMessage(@NonNull Message msg) {

            if(msg.what!=DELAY_ON_CREATE) return false;
            if(delay_dialog!=null){
                delay_dialog.dismiss();
                delay_dialog=null;
            }
            on_create();
            return true;
        }
    });
    void on_create(){
        String uri=getIntent().getStringExtra(EXTRA_GAME_URI);
        aenu.emulator.Emulator.Path path=aenu.emulator.Emulator.Path.from(uri,-1);
        Emulator.get.setup_context(this);
        Emulator.get.setup_document_file_tree(DocumentFile.fromTreeUri(this,MainActivity.load_pref_game_dir( this)));
        Emulator.get.setup_game_path(path);
        Emulator.get.setup_launch_args(new String[]{
                "--storage_root="+Application.get_app_data_dir().getAbsolutePath(),
                "--config="+Application.get_global_config_file().getAbsolutePath(),
                "--log_file="+Application.get_app_data_dir().getAbsolutePath()+"/xe.log",
                /*"--storage_root=/storage/emulated/0/Download/ax360e",
                "--log_file=/storage/emulated/0/Download/ax360e/xe.log",*/
        });
        Emulator.get.setup_uri_info_list_file(Application.get_uri_info_list_file().getAbsolutePath());
        setContentView(R.layout.activity_emulator);
        sf = (SurfaceView) findViewById(R.id.surface_view);
        sf.getHolder().addCallback(EmulatorActivity.this);

        sf.setFocusable(true);
        sf.setFocusableInTouchMode(true);
        sf.requestFocus();
        sf.setOnGenericMotionListener(this);

        load_key_map_and_vibrator();
    }
    void vibrator(){
        if(vibrator!=null) {
            vibrator.vibrate(vibrationEffect);
        }
    }

    void load_key_map_and_vibrator() {
        final SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        keysMap.clear();
        for (int i = 0; i < KeyMapConfig.KEY_NAMEIDS.length; i++) {
            String keyName = Integer.toString(KeyMapConfig.KEY_NAMEIDS[i]);
            int keyCode = sPrefs.getInt(keyName, KeyMapConfig.DEFAULT_KEYMAPPERS[i]);
            keysMap.put(keyCode, KeyMapConfig.KEY_VALUES[i]);
        }
        if(sPrefs.getBoolean("enable_vibrator",false)){
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrationEffect = VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE);
        }
    }
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!Application.should_delay_load()){
            on_create();
            return;
        }

        delay_dialog=ProgressTask.create_progress_dialog( this,getString(R.string.loading));
        delay_dialog.show();
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    Emulator.load_library();
                    Thread.sleep(100);
                    delay_on_create.sendEmptyMessage(DELAY_ON_CREATE);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }.start();
        return;
    }

    @Override
    public void onBackPressed()
    {

        if(delay_dialog!=null)
            return;

        AlertDialog.Builder ab=new AlertDialog.Builder(this);
        ab.setPositiveButton(R.string.quit, new DialogInterface.OnClickListener(){

            @Override
            public void onClick(DialogInterface p1, int p2)
            {
                p1.cancel();
                finish();
            }


        });

        /*ab.setNegativeButton("TE", new DialogInterface.OnClickListener(){

                @Override
                public void onClick(DialogInterface p1, int p2)
                {
                    if(Emulator.get.is_running())
                         Emulator.get.pause();
                     else if(Emulator.get.is_paused())
                         Emulator.get.resume();
                }


        });*/
        //if(Emulator.get.is_running())
        //Emulator.get.pause();
        ab.create().show();
    }

    /*@Override
    protected void onPause()
    {
        super.onPause();
        if(started)
            if(Emulator.get.is_running())
                Emulator.get.pause();;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if(started)
            if(Emulator.get.is_paused())
                Emulator.get.resume();
    }*/

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        System.exit(0);
    }

    final int KEY_NO_MAPPED = -1;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int gameKey = keysMap.get(keyCode, KEY_NO_MAPPED);
        if (gameKey == KEY_NO_MAPPED) return super.onKeyDown(keyCode, event);
        if (event.getRepeatCount() == 0){
            vibrator();
            Emulator.get.key_event(gameKey, true,VirtualControl.KEY_VALUE_UNUSED);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int gameKey = keysMap.get(keyCode, KEY_NO_MAPPED);
        if (gameKey != KEY_NO_MAPPED) {
            Emulator.get.key_event(gameKey, false,VirtualControl.KEY_VALUE_UNUSED);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {

        if(!started){
            started=true;

            Emulator.get.setup_surface(holder.getSurface());
            try {
                Emulator.get.boot();
            } catch (aenu.emulator.Emulator.BootException e) {
                throw new RuntimeException(e);
            }
        }
        else{
            Emulator.get.setup_surface(holder.getSurface());
            if(Emulator.get.is_paused())
                Emulator.get.resume();
        }


    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if(!started) return;
        if(width==0||height==0) return;
        Emulator.get.change_surface(width,height);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if(!started) return;
        Emulator.get.setup_surface(null);
    }


    boolean handle_dpad(InputEvent event) {

        boolean pressed=false;
        if (event instanceof MotionEvent) {

            // Use the hat axis value to find the D-pad direction
            MotionEvent motionEvent = (MotionEvent) event;
            float xaxis = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_X);
            float yaxis = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_Y);

            // Check if the AXIS_HAT_X value is -1 or 1, and set the D-pad
            // LEFT and RIGHT direction accordingly.
            if (Float.compare(xaxis, -1.0f) == 0) {
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_LEFT, true,VirtualControl.KEY_VALUE_UNUSED);
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_RIGHT, false,VirtualControl.KEY_VALUE_UNUSED);
                vibrator();
                pressed=true;
            } else if (Float.compare(xaxis, 1.0f) == 0) {
                Emulator.get.key_event( VirtualControl.KEY_CODE_DPAD_RIGHT, true,VirtualControl.KEY_VALUE_UNUSED);
                Emulator.get.key_event( VirtualControl.KEY_CODE_DPAD_LEFT, false,VirtualControl.KEY_VALUE_UNUSED);

                vibrator();
                pressed=true;
            }
            // Check if the AXIS_HAT_Y value is -1 or 1, and set the D-pad
            // UP and DOWN direction accordingly.
            if (Float.compare(yaxis, -1.0f) == 0) {
                Emulator.get.key_event(  VirtualControl.KEY_CODE_DPAD_UP, true,VirtualControl.KEY_VALUE_UNUSED);
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_DOWN, false,VirtualControl.KEY_VALUE_UNUSED);

                vibrator();
                pressed=true;
            } else if (Float.compare(yaxis, 1.0f) == 0) {
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_DOWN, true,VirtualControl.KEY_VALUE_UNUSED);
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_UP, false,VirtualControl.KEY_VALUE_UNUSED);

                vibrator();
                pressed=true;
            }
        }
        else if (event instanceof KeyEvent) {

            // Use the key code to find the D-pad direction.
            KeyEvent keyEvent = (KeyEvent) event;
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_LEFT, true,VirtualControl.KEY_VALUE_UNUSED);
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_RIGHT, false,VirtualControl.KEY_VALUE_UNUSED);

                vibrator();
                pressed=true;

            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_RIGHT, true,VirtualControl.KEY_VALUE_UNUSED);
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_LEFT, false,VirtualControl.KEY_VALUE_UNUSED);

                vibrator();
                pressed=true;

            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_UP, true,VirtualControl.KEY_VALUE_UNUSED);
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_DOWN, false,VirtualControl.KEY_VALUE_UNUSED);

                vibrator();
                pressed=true;

            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_DOWN, true,VirtualControl.KEY_VALUE_UNUSED);
                Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_UP, false,VirtualControl.KEY_VALUE_UNUSED);

                vibrator();
                pressed=true;

            }
        }

        if(pressed) return true;
        Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_LEFT, false,VirtualControl.KEY_VALUE_UNUSED);
        Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_UP, false,VirtualControl.KEY_VALUE_UNUSED);
        Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_RIGHT, false,VirtualControl.KEY_VALUE_UNUSED);
        Emulator.get.key_event(VirtualControl.KEY_CODE_DPAD_DOWN, false,VirtualControl.KEY_VALUE_UNUSED);
        return false;
    }


    private static boolean isDpadDevice(MotionEvent event) {
        // Check that input comes from a device with directional pads.
        if ((event.getSource() & InputDevice.SOURCE_DPAD)
                != InputDevice.SOURCE_DPAD) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onGenericMotion(View v, MotionEvent event) {

        if(isDpadDevice(event)&& handle_dpad(event)) return true;

        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK/*&&
			event.getAction() == MotionEvent.ACTION_MOVE*/) {
            float laxisX = event.getAxisValue(MotionEvent.AXIS_X);
            float laxisY = event.getAxisValue(MotionEvent.AXIS_Y);
            float raxisX = event.getAxisValue(MotionEvent.AXIS_Z);
            float raxisY = event.getAxisValue(MotionEvent.AXIS_RZ);

            final short _0=0;

            //左摇杆
            {
                if(laxisX!=0){
                    if(laxisX<0){
                        Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_RIGHT,false,_0);
                        Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_LEFT,true,(short) (laxisX*32768.0f));
                    }
                    else{
                        Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_LEFT,false,_0);
                        Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_RIGHT,true,(short)(Math.abs(laxisX)*32767.0f));
                    }
                }
                else{
                    Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_RIGHT,false,_0);
                    Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_LEFT,false,_0);
                }

                //Joystick 左上角为-1.0,-1.0
                //X360左下角为 -32768,-32768
                if(laxisY!=0){
                    if(laxisY<0){
                        Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_DOWN,false,_0);
                        Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_UP,true,(short)(-laxisY*32767.0f));
                    }else{
                        Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_UP,false,_0);
                        Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_DOWN,true,(short)(-laxisY*32768.0f));
                    }
                }
                else{
                    Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_DOWN,false,_0);
                    Emulator.get.key_event(VirtualControl.KEY_CODE_LTHUMB_UP,false,_0);
                }
            }
            //右摇杆
            {
                if(raxisX!=0){
                    if(raxisX<0){
                        Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_RIGHT,false,_0);
                        Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_LEFT,true,(short)(raxisX*32768.f));
                    }else{
                        Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_LEFT,false,_0);
                        Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_RIGHT,true,(short)(raxisX*32767.0f));
                    }
                }
                else{
                    Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_RIGHT,false,_0);
                    Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_LEFT,false,_0);
                }

                //Joystick 左上角为-1.0,-1.0
                //X360左下角为 -32768,-32768
                if(raxisY!=0){
                    if(raxisY<0){
                        Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_DOWN,false,_0);
                        Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_UP,true,(short)(-raxisY*32767.0f));
                    }else{
                        Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_UP,false,_0);
                        Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_DOWN,true,(short)(-raxisY*32768.0f));
                    }
                }
                else{
                    Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_DOWN,false,_0);
                    Emulator.get.key_event(VirtualControl.KEY_CODE_RTHUMB_UP,false,_0);
                }
            }
            return true;
        }

        return super.onGenericMotionEvent(event);
    }


    // 🔥 SAFE IMMERSIVE MODE (NO CRASH)
    void hideSystemUI(){
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.view.WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(android.view.WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }
            } else {
                View decorView = getWindow().getDecorView();
                if (decorView != null) {
                    decorView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    );
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().post(() -> hideSystemUI());
        }
    }

}
