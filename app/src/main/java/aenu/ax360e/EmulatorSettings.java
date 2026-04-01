// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;

import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.json.JSONObject;

import aenu.preference.CheckBoxPreference;
import aenu.preference.ListPreference;
import aenu.preference.SeekBarPreference;

import java.io.File;
import java.util.Set;

public class EmulatorSettings extends AppCompatActivity {

    static final String EXTRA_CONFIG_PATH="config_path";
    static final int WARNING_COLOR=0xffff8000;
    static final String Vulkan$vulkan_lib_path="Vulkan|vulkan_lib_path";
    static final int REQUEST_CODE_SELECT_CUSTOM_DRIVER=6101;

    @SuppressLint("ValidFragment")
    public static class SettingsFragment extends PreferenceFragmentCompat implements
            Preference.OnPreferenceClickListener,Preference.OnPreferenceChangeListener{

        boolean is_global;
        String config_path;
        Emulator.Config original_config;
        Emulator.Config config;
        PreferenceScreen root_pref;

        SettingsFragment(String config_path,boolean is_global){
            this.config_path=config_path;
            this.is_global=is_global;
        }

        OnBackPressedCallback back_callback=new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                String current=SettingsFragment.this.getPreferenceScreen().getKey();
                if (current==null){
                    requireActivity().finish();
                    return;
                }
                int p=current.lastIndexOf('|');
                if (p==-1)
                    setPreferenceScreen(root_pref);
                else
                    setPreferenceScreen(root_pref.findPreference(current.substring(0,p)));
            }
        };

        final PreferenceDataStore data_store=new PreferenceDataStore(){
            public void putString(String key, @Nullable String value) {
                config.save_config_entry(key,value);
            }
            public void putStringSet(String key, @Nullable Set<String> values) {
                throw new UnsupportedOperationException();
            }
            public void putInt(String key, int value) {
                config.save_config_entry(key,Integer.toString(value));
            }
            public void putBoolean(String key, boolean value) {
                config.save_config_entry(key,Boolean.toString(value));
            }
            public String getString(String key, @Nullable String defValue) {
                return config.load_config_entry(key);
            }
            public int getInt(String key, int defValue) {
                String v=config.load_config_entry(key);
                return v!=null?Integer.parseInt(v):defValue;
            }
            public boolean getBoolean(String key, boolean defValue) {
                String v=config.load_config_entry(key);
                return v!=null?Boolean.parseBoolean(v):defValue;
            }
        };

        // ✅ FIX METHODS ADDED
        boolean ensure_default_config_exists(){
            try{
                File default_config_file=Application.get_default_config_file();
                if(default_config_file.exists())
                    return true;
                String default_config_str=Application.load_default_config_str(requireContext());
                if(default_config_str==null)
                    return false;
                Utils.save_string(default_config_file,default_config_str);
                return default_config_file.exists();
            }catch(Exception e){
                Log.e("EmulatorSettings","ensure_default_config_exists",e);
                return false;
            }
        }

        boolean restore_config_from_default(){
            try{
                if(!ensure_default_config_exists())
                    return false;
                File config_file=new File(config_path);
                File parent=config_file.getParentFile();
                if(parent!=null && !parent.exists())
                    parent.mkdirs();
                Utils.copy_file(Application.get_default_config_file(),config_file);
                return config_file.exists();
            }catch(Exception e){
                Log.e("EmulatorSettings","restore_config_from_default",e);
                return false;
            }
        }

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {

            setPreferencesFromResource(R.xml.emulator_settings, rootKey);
            root_pref=getPreferenceScreen();

            requireActivity().getOnBackPressedDispatcher().addCallback(back_callback);

            // ✅ FIX 1
            if(!new File(config_path).exists()){
                if(!restore_config_from_default()){
                    root_pref.setEnabled(false);
                    Toast.makeText(requireContext(), config_path, Toast.LENGTH_LONG).show();
                    return;
                }
            }

            try{
                config=Emulator.Config.open_config_file(config_path);
                original_config=Emulator.Config.open_config_from_string(Application.load_default_config_str(getContext()));
            }catch(Exception e){
                Log.e("EmulatorSettings","Failed to open config, trying to restore",e);

                // ✅ FIX 2
                if(!restore_config_from_default()){
                    root_pref.setEnabled(false);
                    return;
                }

                try{
                    config=Emulator.Config.open_config_file(config_path);
                    original_config=Emulator.Config.open_config_from_string(Application.load_default_config_str(getContext()));
                }catch(Exception e2){
                    root_pref.setEnabled(false);
                    return;
                }
            }
        }
    }

    SettingsFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_emulator_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        String config_path=getIntent().getStringExtra(EXTRA_CONFIG_PATH);

        if(config_path!=null)
            fragment=new SettingsFragment(config_path,false);
        else
            fragment=new SettingsFragment(Application.get_global_config_file().getAbsolutePath(),true);

        getSupportFragmentManager().beginTransaction().replace(R.id.settings_container,fragment).commit();
    }
}
