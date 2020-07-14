package com.example.ownchat;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import live.chatkit.android.AuthRepository;
import live.chatkit.android.BaseActivity;
import live.chatkit.android.ChatKit;
import live.chatkit.android.ChatRepository;
import live.chatkit.android.ChatUtil;
import live.chatkit.android.FileRepository;
import live.chatkit.android.ResultListener;
import live.chatkit.android.UserRepository;
import live.chatkit.android.model.UserVO;
import com.stfalcon.chatkit.sample.common.data.model.User;

import get.avatar.android.AvatarDialog;
import get.avatar.android.AvatarUtil;

public class SettingsActivity extends BaseActivity implements AvatarDialog.AvatarDialogListener {

    private ProgressDialog progress;
    private Button loginBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        loginBtn = findViewById(R.id.btn_login);
        login();
    }

    public void openAdminLogin(View view) {
        View layout = getLayoutInflater().inflate(R.layout.dialog_signin, null);
        final EditText username = layout.findViewById(R.id.username);
        final EditText password = layout.findViewById(R.id.password);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(layout)
                .setPositiveButton(R.string.signin, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        showProgress();
                        logout();
                        reset();
                        login(username.getText().toString(), password.getText().toString());
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        builder.create().show();
    }

    @Override
    public void onAvatarUpdate(String avatarUri, Bitmap avatarBitmap) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putString("chatkit.photo", avatarUri).apply();

        SettingsFragment fragmentSettings = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("fragment_settings");
        if (fragmentSettings != null) fragmentSettings.showAvatar(avatarBitmap);
    }

    @Override
    protected void onInit(User currentUser) {
        ChatKit.getInstance().onInit(currentUser.getId());
        //if (getSupportFragmentManager().findFragmentByTag("fragment_settings") == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment(), "fragment_settings")
                    .commit();
        //}
        loginBtn.setEnabled(AuthRepository.getInstance().isAnonymous());
        loginBtn.setVisibility(ChatKit.isEncryption() ? View.GONE : View.VISIBLE);
        hideProgress(false);
    }

    public void saveProfile() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        UserVO user = new UserVO();
        user.id = getCurrentUser().getId();
        user.photo = prefs.getString("chatkit.photo", null);
        user.name = prefs.getString("chatkit.name", getString(live.chatkit.android.R.string.guest));
        user.bio = prefs.getString("chatkit.status", null);

        UserRepository.getInstance().updateProfile(user, null);
    }

    public void deleteAccount() {
        if (!NetworkUtil.isConnected(getApplicationContext())) {
            Toast.makeText(this, R.string.err_network, Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress();

        FileRepository.getInstance().deleteData(ChatUtil.getStorageDir(getApplicationContext()), new ResultListener() {
            @Override
            public void onSuccess(Object result) {
                ChatRepository.getInstance().leaveChats(getCurrentUser().getId(), new ResultListener() {
                    @Override
                    public void onSuccess(Object result) {
                        UserRepository.getInstance().deleteUser(getCurrentUser().getId(), new ResultListener() {
                            @Override
                            public void onSuccess(Object result) {
                                logout();
                                reset();
                                hideProgress(false);
                                finishAffinity();
                            }

                            @Override
                            public void onFailure(@NonNull Exception e) { hideProgress(true); }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Exception e) { hideProgress(true); }
                });
            }

            @Override
            public void onFailure(@NonNull Exception e) { hideProgress(true); }
        });
    }

    private void reset() {
        ChatKit.getInstance().reset();
        FileRepository.getInstance().clear();
        ChatRepository.getInstance().clear();
        UserRepository.getInstance().clear();

        SharedPreferences defaultPrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        defaultPrefs.edit().putString("chatkit.photo", null).putString("chatkit.name", null).putString("chatkit.status", null).commit();
        ChatUtil.getPrefs(getApplicationContext()).edit().clear().commit();
    }

    private void showProgress() {
        if (progress == null) {
            progress = new ProgressDialog(this);
            progress.setMessage(getString(R.string.pls_wait));
        }
        if (!progress.isShowing()) progress.show();
    }

    private void hideProgress(boolean error) {
        if (progress == null) return;
        if (progress.isShowing()) progress.dismiss();
        if (error) Toast.makeText(this, R.string.err_unknown, Toast.LENGTH_LONG).show();
    }

    //----------------------------------------------------------------------------------------------

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        public void showAvatar(Bitmap bitmap) {
            if (bitmap == null || !isAdded()) return;
            Preference avatarPref = findPreference("chatkit.avatar_key");
            if (avatarPref != null) {
                avatarPref.setIcon(new BitmapDrawable(getResources(), bitmap));
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            EditTextPreference namePref = findPreference("chatkit.name");
            namePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (!ChatKitUtil.validateText(getContext(), String.valueOf(newValue))) {
                        Toast.makeText(getActivity(), R.string.err_text, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    return true;
                }
            });
            namePref.setOnBindEditTextListener(
                new EditTextPreference.OnBindEditTextListener() {
                    @Override
                    public void onBindEditText(@NonNull EditText editText) {
                        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                        editText.setMaxLines(1);

                        InputFilter[] editFilters = editText.getFilters();
                        InputFilter[] newFilters = new InputFilter[editFilters.length + 1];
                        System.arraycopy(editFilters, 0, newFilters, 0, editFilters.length);
                        newFilters[editFilters.length] = new InputFilter.LengthFilter(20);
                        editText.setFilters(newFilters);
                    }
                });

            EditTextPreference statusPref = findPreference("chatkit.status");
            statusPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (!ChatKitUtil.validateText(getContext(), String.valueOf(newValue))) {
                        Toast.makeText(getActivity(), R.string.err_text, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    return true;
                }
            });
            statusPref.setOnBindEditTextListener(
                    new EditTextPreference.OnBindEditTextListener() {
                        @Override
                        public void onBindEditText(@NonNull EditText editText) {
                            editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                            editText.setMaxLines(1);

                            InputFilter[] editFilters = editText.getFilters();
                            InputFilter[] newFilters = new InputFilter[editFilters.length + 1];
                            System.arraycopy(editFilters, 0, newFilters, 0, editFilters.length);
                            newFilters[editFilters.length] = new InputFilter.LengthFilter(50);
                            editText.setFilters(newFilters);
                        }
                    });

            Preference deletePref = findPreference("chatkit.delete_key");
            deletePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.delete_confirm)
                        .setPositiveButton(android.R.string.yes,  new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((SettingsActivity) getActivity()).deleteAccount();
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create().show();
                    return true;
                }
            });

            Preference avatarPref = findPreference("chatkit.avatar_key");
            avatarPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    AvatarDialog avatarDialog = AvatarDialog.newInstance(prefs.getString("chatkit.photo", null));
                    avatarDialog.show(getFragmentManager(), "fragment_avatar");
                    return true;
                }
            });
        }

        @Override
        public void onStart() {
            super.onStart();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            showAvatar(AvatarUtil.getAvatarBitmap(getContext(), prefs.getString("chatkit.photo", null), Color.TRANSPARENT));
        }

        @Override
        public void onStop() {
            super.onStop();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("chatkit.photo".equals(key) || "chatkit.name".equals(key) || "chatkit.status".equals(key))
                ((SettingsActivity) getActivity()).saveProfile();
        }

    }

}