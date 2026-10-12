package com.xyron.game.launcher.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import com.xyron.game.R;
import com.xyron.game.launcher.EntryActivity;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.SharedPreferenceCore;
import org.ini4j.Wini;
import java.io.File;
import java.io.IOException;

public class SettingsFragment extends Fragment {

    // Same preferences file the game reads (SAMP.java: HUD_RENDER_PREFS).
    private static final String RENDER_PREFS = "xyron_hud_render_settings";
    private static final int FPS_MIN = 30;
    private static final int FPS_MAX = 120;
    private static final int[] CHAT_VALUES = {5, 10, 20};

    private Wini mWini;
    private TextView nicknameValue;
    private TextView fpsValue;
    private SeekBar fpsSeek;
    private SwitchCompat keyboardSwitch;
    private final TextView[] chatButtons = new TextView[3];

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        nicknameValue = view.findViewById(R.id.player_name_value);
        fpsValue = view.findViewById(R.id.settings_fps_value);
        fpsSeek = view.findViewById(R.id.settings_fps_seek);
        keyboardSwitch = view.findViewById(R.id.keyboard_switch);
        chatButtons[0] = view.findViewById(R.id.chat_size_small);
        chatButtons[1] = view.findViewById(R.id.chat_size_medium);
        chatButtons[2] = view.findViewById(R.id.chat_size_large);
        TextView editNameButton = view.findViewById(R.id.button_edit_profile);
        TextView updateDataButton = view.findViewById(R.id.button_update_data);

        openSettingsFile();
        loadNickname();
        setupFps();
        setupChatSize();
        setupKeyboard();

        if (editNameButton != null) {
            editNameButton.setOnTouchListener(new ButtonAnimator(requireContext(), editNameButton));
            editNameButton.setOnClickListener(v -> showNicknameDialog());
        }
        if (updateDataButton != null) {
            updateDataButton.setOnTouchListener(new ButtonAnimator(requireContext(), updateDataButton));
            updateDataButton.setOnClickListener(v -> confirmDataUpdate());
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        openSettingsFile();
        loadNickname();
    }

    // ---------------- FPS ----------------

    private SharedPreferences renderPrefs() {
        return requireContext().getSharedPreferences(RENDER_PREFS, Context.MODE_PRIVATE);
    }

    private void setupFps() {
        if (fpsSeek == null) {
            return;
        }
        int fps = renderPrefs().getInt("fps_limit", 60);
        fps = Math.max(FPS_MIN, Math.min(FPS_MAX, fps));
        fpsSeek.setProgress(fps - FPS_MIN);
        updateFpsLabel(fps);

        fpsSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateFpsLabel(FPS_MIN + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int value = FPS_MIN + seekBar.getProgress();
                renderPrefs().edit().putInt("fps_limit", value).apply();
                writeRenderIni(value);
            }
        });
    }

    private void updateFpsLabel(int fps) {
        if (fpsValue != null) {
            fpsValue.setText(fps + " FPS");
        }
    }

    private void writeRenderIni(int fps) {
        if (getContext() == null || requireContext().getExternalFilesDir(null) == null) {
            return;
        }
        File file = new File(requireContext().getExternalFilesDir(null), "SAMP/render_settings.ini");
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            Wini wini = new Wini(file);
            wini.put("render", "fps_limit", fps);
            wini.store();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------------- Chat size ----------------

    private void setupChatSize() {
        int current = 5;
        try {
            current = Integer.parseInt(readIniValue("gui", "ChatMaxMessages", "5"));
        } catch (NumberFormatException ignored) {
        }
        int selected = current <= 5 ? 0 : (current <= 10 ? 1 : 2);
        applyChatSelection(selected);

        for (int i = 0; i < chatButtons.length; i++) {
            final int index = i;
            TextView button = chatButtons[i];
            if (button == null) {
                continue;
            }
            button.setOnClickListener(v -> {
                saveIniValue("gui", "ChatMaxMessages", CHAT_VALUES[index]);
                applyChatSelection(index);
            });
        }
    }

    private void applyChatSelection(int selected) {
        for (int i = 0; i < chatButtons.length; i++) {
            if (chatButtons[i] != null) {
                chatButtons[i].setBackgroundResource(
                        i == selected
                                ? R.drawable.home_server_select_button
                                : R.drawable.launcher_field_surface_dark
                );
            }
        }
    }

    // ---------------- Keyboard ----------------

    private void setupKeyboard() {
        if (keyboardSwitch == null) {
            return;
        }
        keyboardSwitch.setChecked(new SharedPreferenceCore().getBoolean(
                requireContext().getApplicationContext(),
                "ANDROID_KEYBOARD"
        ));
        keyboardSwitch.setOnCheckedChangeListener((button, checked) -> {
            new SharedPreferenceCore().setBoolean(
                    requireContext().getApplicationContext(),
                    "ANDROID_KEYBOARD",
                    checked
            );
            saveIniValue("gui", "androidkeyboard23123", checked);
        });
    }

    // ---------------- Nickname ----------------

    private void loadNickname() {
        if (nicknameValue == null) {
            return;
        }
        String nickname = readIniValue("client", "name", "");
        nicknameValue.setText(nickname.isEmpty() ? "هیچ ناوێک دیاری نەکراوە" : nickname);
    }

    private void showNicknameDialog() {
        if (getContext() == null) {
            return;
        }
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_name, null, false);
        EditText input = dialogView.findViewById(R.id.dialog_name_input);
        TextView cancelButton = dialogView.findViewById(R.id.dialog_name_cancel);
        TextView saveButton = dialogView.findViewById(R.id.dialog_name_save);

        if (input != null) {
            input.setText(readIniValue("client", "name", ""));
            input.setSelection(input.getText() != null ? input.getText().length() : 0);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        dialog.setCancelable(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        if (cancelButton != null) {
            cancelButton.setOnTouchListener(new ButtonAnimator(getContext(), cancelButton));
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }
        if (saveButton != null) {
            saveButton.setOnTouchListener(new ButtonAnimator(getContext(), saveButton));
            saveButton.setOnClickListener(v -> {
                String nickname = input != null && input.getText() != null
                        ? input.getText().toString().trim()
                        : "";
                if (nickname.isEmpty()) {
                    Toast.makeText(getContext(), "ناوێک بنووسە بۆ پاشەکەوتکردن.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!saveIniValue("client", "name", nickname)) {
                    Toast.makeText(getContext(), "نەتوانرا ناو پاشەکەوت بکرێت.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(getContext(), "ناو پاشەکەوت کرا.", Toast.LENGTH_SHORT).show();
                loadNickname();
                dialog.dismiss();
            });
        }

        dialog.show();
        if (input != null) {
            input.requestFocus();
        }
    }

    // ---------------- Game data ----------------

    private void confirmDataUpdate() {
        if (getContext() == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("نوێکردنەوەی داتا")
                .setMessage("داتای یاری دەپشکنرێت و نوێ دەکرێتەوە. ئەمە فایلەکانی ئێستات ناسڕێتەوە. بەردەوام دەبیت؟")
                .setPositiveButton("بەڵێ", (d, w) -> startDataUpdate())
                .setNegativeButton("نەخێر", null)
                .show();
    }

    private void startDataUpdate() {
        if (getActivity() == null) {
            return;
        }
        // The old code wiped the whole game folder first; the update now runs without deleting it.
        Intent intent = new Intent(getActivity(), EntryActivity.class);
        intent.putExtra(EntryActivity.EXTRA_FORCE_UPDATE_DATA, true);
        startActivity(intent);
    }

    // ---------------- settings.ini helpers ----------------

    private void openSettingsFile() {
        if (getActivity() == null || getActivity().getExternalFilesDir(null) == null) {
            mWini = null;
            return;
        }
        File file = new File(getActivity().getExternalFilesDir(null), "SAMP/settings.ini");
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                file.createNewFile();
            }
            mWini = new Wini(file);
        } catch (IOException e) {
            mWini = null;
            e.printStackTrace();
        }
    }

    private String readIniValue(String section, String key, String fallback) {
        if (mWini == null) {
            return fallback;
        }
        String value = mWini.get(section, key);
        return value == null ? fallback : value.trim();
    }

    private boolean saveIniValue(String section, String key, Object value) {
        if (mWini == null) {
            return false;
        }
        try {
            mWini.put(section, key, value);
            mWini.store();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
