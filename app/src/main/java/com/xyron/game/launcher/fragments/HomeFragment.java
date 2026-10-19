package com.xyron.game.launcher.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
import com.xyron.game.launcher.util.SampQueryApi;
import com.xyron.game.launcher.util.ServerConfigManager;
import com.xyron.game.launcher.util.Util;

import org.ini4j.Wini;

import java.io.File;

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
    private TextView statusChip;
    private TextView playersChip;
    private TextView pingChip;
    private TextView playerName;
    private TextView versionValue;
    private TextView dataValue;
    private TextView serverCountValue;
    private TextView readinessTitle;
    private TextView readinessBody;
    private TextView nextStepButton;
    private int nextStepAction = NEXT_STEP_PLAY;
    private int queryGeneration = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        playButton = root.findViewById(R.id.play_button);
        selectServerButton = root.findViewById(R.id.select_server_button);
        selectedServerName = root.findViewById(R.id.selected_server_name);
        selectedServerAddress = root.findViewById(R.id.selected_server_address);
        selectedServerHint = root.findViewById(R.id.selected_server_hint);
        statusChip = root.findViewById(R.id.home_status_chip);
        playersChip = root.findViewById(R.id.home_players_chip);
        pingChip = root.findViewById(R.id.home_ping_chip);
        playerName = root.findViewById(R.id.home_player_name);
        versionValue = root.findViewById(R.id.home_version);
        dataValue = root.findViewById(R.id.home_data_status);
        serverCountValue = root.findViewById(R.id.home_server_count);
        readinessTitle = root.findViewById(R.id.home_readiness_title);
        readinessBody = root.findViewById(R.id.home_readiness_body);
        nextStepButton = root.findViewById(R.id.home_next_step_button);
        TextView editNameButton = root.findViewById(R.id.home_edit_name);

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
            selectServerButton.setOnClickListener(v -> openTab(MainActivity.TAB_SERVERS));
        }

        if (editNameButton != null) {
            editNameButton.setOnTouchListener(new ButtonAnimator(requireContext(), editNameButton));
            editNameButton.setOnClickListener(v -> openTab(MainActivity.TAB_SETTINGS));
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

    private void openTab(int tab) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openTab(tab);
        }
    }

    private void updateSelectedServer() {
        Context context = getContext();
        if (context == null || selectedServerName == null || selectedServerAddress == null || selectedServerHint == null) {
            return;
        }

        queryGeneration++;
        updateSystemInfo(context);

        boolean hasGameData = isLauncherRole() || GameDataVerifier.hasRequiredGameData(context);
        boolean hasServers = ServerConfigManager.hasConfiguredServers(context);

        if (!hasServers) {
            if (selectServerButton != null) {
                selectServerButton.setText("زیادکردنی سێرڤەر");
            }
            selectedServerName.setText("هیچ سێرڤەرێکی چالاک نییە");
            selectedServerAddress.setText("-");
            setLiveChips("-", "-", "-");
            selectedServerHint.setText("لە بەشی سێرڤەرەکان IPـیەک زیاد بکە.");
            updateReadiness(hasGameData, false, false);
            return;
        }

        ServerConfigManager.ServerOption selectedServer = ServerConfigManager.getSelectedServer(context);
        if (selectedServer == null || !selectedServer.isValid()) {
            if (selectServerButton != null) {
                selectServerButton.setText("هەڵبژاردنی سێرڤەر");
            }
            selectedServerName.setText("هیچ سێرڤەرێکی چالاک نییە");
            selectedServerAddress.setText("-");
            setLiveChips("-", "-", "-");
            selectedServerHint.setText("IPـیەکی پاشەکەوتکراو لە سێرڤەرەکان هەڵبژێرە.");
            updateReadiness(hasGameData, true, false);
            return;
        }

        if (selectServerButton != null) {
            selectServerButton.setText("گۆڕینی سێرڤەر");
        }
        selectedServerName.setText(selectedServer.getAddress());
        selectedServerAddress.setText(selectedServer.getAddress());
        selectedServerHint.setText(selectedServer.favorite ? "سێرڤەری دڵخوازە." : "");
        setLiveChips("...", "-", "-");
        updateReadiness(hasGameData, true, true);
        queryLive(selectedServer, queryGeneration);
    }

    private void setLiveChips(String status, String players, String ping) {
        if (statusChip != null) {
            statusChip.setText(status);
            statusChip.setTextColor(Color.parseColor("#F7F4EE"));
        }
        if (playersChip != null) {
            playersChip.setText(players);
        }
        if (pingChip != null) {
            pingChip.setText(ping);
        }
    }

    private void queryLive(ServerConfigManager.ServerOption option, int generation) {
        final String host = option.host;
        final int port = option.port;
        new Thread(() -> {
            boolean online = false;
            String name = "";
            int players = 0;
            int maxPlayers = 0;
            int ping = 0;

            SampQueryApi api = new SampQueryApi(host, port, 550);
            try {
                long pingValue = api.measurePingMs(1);
                if (pingValue >= 0) {
                    online = true;
                    ping = (int) pingValue;
                    String[] info = api.getInfo();
                    if (info != null && info.length > 3) {
                        players = parseInt(info[1]);
                        maxPlayers = parseInt(info[2]);
                        name = info[3] == null ? "" : Util.getStringWithoutColors(info[3]).trim();
                    }
                }
            } catch (Exception ignored) {
                online = false;
            } finally {
                api.close();
            }

            final boolean fOnline = online;
            final String fName = name;
            final int fPlayers = players;
            final int fMax = maxPlayers;
            final int fPing = ping;

            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(() -> {
                if (!isAdded() || generation != queryGeneration) {
                    return;
                }
                if (!fOnline) {
                    setLiveChips("ئۆفڵاین", "-", "-");
                    if (statusChip != null) {
                        statusChip.setTextColor(Color.parseColor("#F87171"));
                    }
                    return;
                }
                setLiveChips(
                        "ئۆنڵاین",
                        fMax > 0 ? fPlayers + " / " + fMax : "-",
                        fPing > 0 ? fPing + " ms" : "-"
                );
                if (statusChip != null) {
                    statusChip.setTextColor(Color.parseColor("#4ADE80"));
                }
                if (!fName.isEmpty() && selectedServerName != null) {
                    selectedServerName.setText(fName);
                }
            });
        }, "home-query-" + port).start();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void updateSystemInfo(Context context) {
        if (versionValue != null) {
            versionValue.setText("v" + BuildConfig.VERSION_NAME);
        }
        if (serverCountValue != null) {
            serverCountValue.setText(String.valueOf(ServerConfigManager.getAvailableServers(context).size()));
        }
        if (dataValue != null) {
            boolean ok = isLauncherRole() || GameDataVerifier.hasRequiredGameData(context);
            dataValue.setText(ok ? "دامەزراوە" : "دامەزراو نییە");
            dataValue.setTextColor(Color.parseColor(ok ? "#4ADE80" : "#F87171"));
        }
        if (playerName != null) {
            String nick = readNickname(context);
            playerName.setText(nick.isEmpty() ? "-" : nick);
        }
    }

    private String readNickname(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) {
                return "";
            }
            File file = new File(dir, "SAMP/settings.ini");
            if (!file.exists()) {
                return "";
            }
            String value = new Wini(file).get("client", "name");
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
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

        if (nextStepAction == NEXT_STEP_ADD_SERVER || nextStepAction == NEXT_STEP_CHOOSE_SERVER) {
            activity.openTab(MainActivity.TAB_SERVERS);
            return;
        }

        activity.startGta();
    }

    private boolean isLauncherRole() {
        return "launcher".equals(BuildConfig.XYRON_APK_ROLE);
    }
}
