package com.xyron.game.launcher.fragments;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.xyron.game.BuildConfig;
import com.xyron.game.R;
import com.xyron.game.launcher.MainActivity;
import com.xyron.game.launcher.UpdateActivity;
import com.xyron.game.launcher.util.ButtonAnimator;
import com.xyron.game.launcher.util.GameDataVerifier;
import com.xyron.game.launcher.util.ServerConfigManager;

public class HomeFragment extends Fragment {
    private static final int NEXT_STEP_PLAY = 0;
    private static final int NEXT_STEP_UPDATE_DATA = 1;
    private static final int NEXT_STEP_ADD_SERVER = 2;
    private static final int NEXT_STEP_CHOOSE_SERVER = 3;

    private View playButton;
    private TextView selectServerButton;
    private TextView selectedServerName;
    private TextView selectedServerAddress;
    private TextView selectedServerHint;
    private TextView readinessTitle;
    private TextView readinessBody;
    private TextView nextStepButton;
    private int nextStepAction = NEXT_STEP_PLAY;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        playButton = root.findViewById(R.id.play_button);
        selectServerButton = root.findViewById(R.id.select_server_button);
        selectedServerName = root.findViewById(R.id.selected_server_name);
        selectedServerAddress = root.findViewById(R.id.selected_server_address);
        selectedServerHint = root.findViewById(R.id.selected_server_hint);
        readinessTitle = root.findViewById(R.id.home_readiness_title);
        readinessBody = root.findViewById(R.id.home_readiness_body);
        nextStepButton = root.findViewById(R.id.home_next_step_button);

        if (playButton != null) {
            playButton.setOnTouchListener(new ButtonAnimator(requireContext(), playButton));
            playButton.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).startGta();
                }
            });
        }

        if (selectServerButton != null) {
            selectServerButton.setOnTouchListener(new ButtonAnimator(requireContext(), selectServerButton));
            selectServerButton.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openTab(
                            MainActivity.TAB_SERVERS
                    );
                }
            });
        }


        if (nextStepButton != null) {
            nextStepButton.setOnTouchListener(new ButtonAnimator(requireContext(), nextStepButton));
            nextStepButton.setOnClickListener(v -> performNextStep());
        }

        updateSelectedServer();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSelectedServer();
    }

    private void configureTabShortcut(View root, int viewId, int tabIndex) {
        View shortcut = root.findViewById(viewId);
        if (shortcut == null) {
            return;
        }

        if (getActivity() instanceof MainActivity
                && !((MainActivity) getActivity()).isTabAllowed(tabIndex)) {
            shortcut.setVisibility(View.GONE);
            return;
        }

        shortcut.setOnTouchListener(new ButtonAnimator(requireContext(), shortcut));
        shortcut.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openTab(tabIndex);
            }
        });
    }

    private void updateSelectedServer() {
        Context context = getContext();
        if (context == null || selectedServerName == null || selectedServerAddress == null || selectedServerHint == null) {
            return;
        }

        boolean hasGameData = isLauncherRole() || GameDataVerifier.hasRequiredGameData(context);
        boolean hasServers = ServerConfigManager.hasConfiguredServers(context);

        if (!hasServers) {
            if (selectServerButton != null) {
                selectServerButton.setText("زیادکردنی سێرڤەر");
            }
            selectedServerName.setText("هیچ سێرڤەرێکی چالاک نییە");
            selectedServerAddress.setText("دوگمەی «زیادکردنی سێرڤەر» دابگرە");
            selectedServerHint.setText("دوای پاشەکەوتکردنی IP، لێرە ئامادە دەبێت بۆ بەکارهێنان.");
            updateReadiness(hasGameData, false, false);
            return;
        }

        ServerConfigManager.ServerOption selectedServer = ServerConfigManager.getSelectedServer(context);
        if (selectedServer == null || !selectedServer.isValid()) {
            if (selectServerButton != null) {
                selectServerButton.setText("هەڵبژاردنی سێرڤەر");
            }
            selectedServerName.setText("هیچ سێرڤەرێکی چالاک نییە");
            selectedServerAddress.setText("IPـیەکی پاشەکەوتکراو لە سێرڤەرەکان هەڵبژێرە");
            selectedServerHint.setText("سێرڤەری هەڵبژێردراو خۆکارانە لە یارییەکەدا جێبەجێ دەبێت.");
            updateReadiness(hasGameData, true, false);
            return;
        }

        if (selectServerButton != null) {
            selectServerButton.setText("گۆڕینی سێرڤەر");
        }
        selectedServerName.setText("ئامادەیە بۆ یاری");
        selectedServerAddress.setText(selectedServer.getAddress());
        selectedServerHint.setText(selectedServer.favorite
                ? "سێرڤەری دڵخوازە و ئامادەیە بۆ کردنەوە لە یارییەکەدا."
                : "سێرڤەر ئامادەیە بۆ کردنەوە لە یارییەکەدا. بۆ گۆڕین «گۆڕینی سێرڤەر» بەکاربهێنە.");
        updateReadiness(hasGameData, true, true);
    }

    private void updateReadiness(boolean hasGameData, boolean hasServers, boolean hasSelectedServer) {
        if (readinessTitle == null || readinessBody == null || nextStepButton == null) {
            return;
        }

        if (!hasGameData) {
            nextStepAction = NEXT_STEP_UPDATE_DATA;
            readinessTitle.setText("داتا ماوە");
            readinessBody.setText("فایلەکانی یاری دابگرە");
            nextStepButton.setText("داگرتنی داتا");
            return;
        }

        if (!hasServers) {
            nextStepAction = NEXT_STEP_ADD_SERVER;
            readinessTitle.setText("سێرڤەر کەمە");
            readinessBody.setText("IP:PORT زیاد بکە");
            nextStepButton.setText("زیادکردنی سێرڤەر");
            return;
        }

        if (!hasSelectedServer) {
            nextStepAction = NEXT_STEP_CHOOSE_SERVER;
            readinessTitle.setText("سێرڤەر هەڵبژێرە");
            readinessBody.setText("IPـی چالاک دیاری بکە");
            nextStepButton.setText("هەڵبژاردنی سێرڤەر");
            return;
        }

        nextStepAction = NEXT_STEP_PLAY;
        if (isLauncherRole()) {
            readinessTitle.setText("سێرڤەر ئامادەیە");
            readinessBody.setText("لە APKـی یاری بیکەرەوە");
            nextStepButton.setText("کردنەوەی یاری");
        } else {
            readinessTitle.setText("هەموو شتێک ئامادەیە");
            readinessBody.setText("دەتوانیت بچیتە ناو یارییەکە");
            nextStepButton.setText("ئێستا یاری بکە");
        }
    }

    private void performNextStep() {
        if (!(getActivity() instanceof MainActivity)) {
            return;
        }

        MainActivity activity = (MainActivity) getActivity();
        if (nextStepAction == NEXT_STEP_UPDATE_DATA) {
            Intent intent = new Intent(requireContext(), UpdateActivity.class);
            intent.putExtra("mode", UpdateActivity.UpdateMode.GameDataUpdate.name());
            startActivity(intent);
            return;
        }

        if (nextStepAction == NEXT_STEP_ADD_SERVER) {
            activity.openTab(MainActivity.TAB_SERVERS);
            return;
        }

        if (nextStepAction == NEXT_STEP_CHOOSE_SERVER) {
            activity.openTab(MainActivity.TAB_SERVERS);
            return;
        }

        activity.startGta();
    }

    private boolean isLauncherRole() {
        return "launcher".equals(BuildConfig.XYRON_APK_ROLE);
    }
}
