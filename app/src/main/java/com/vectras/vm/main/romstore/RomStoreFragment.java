package com.vectras.vm.main.romstore;

import android.os.Bundle;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.vectras.vm.download.DownloadViewModel;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.transition.MaterialFadeThrough;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.vectras.vm.AppConfig;
import com.vectras.vm.network.RequestNetwork;
import com.vectras.vm.network.RequestNetworkController;
import com.vectras.vm.databinding.FragmentHomeRomStoreBinding;
import com.vectras.vm.main.MainUiStateViewModel;
import com.vectras.vm.main.core.SharedData;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RomStoreFragment extends Fragment {

    private static final String TAG = "RomStoreFragment";
    FragmentHomeRomStoreBinding binding;
    private RequestNetwork net;
    private RequestNetwork.RequestListener _net_request_listener;
    private String contentJSON = "[]";
    HomeRomStoreViewModel homeRomStoreViewModel;
    MainUiStateViewModel mainUiStateViewModel;
    RomStoreHomeAdpater mAdapter;
    List<DataRoms> data = new ArrayList<>();
    DownloadViewModel downloadViewModel;

    private RomStoreCallToHomeListener romStoreCallToHomeListener;
    public interface RomStoreCallToHomeListener {
        void updateSearchStatus(boolean isReady);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialFadeThrough());
        setReturnTransition(new MaterialFadeThrough());
        setExitTransition(new MaterialFadeThrough());
        setReenterTransition(new MaterialFadeThrough());
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof RomStoreCallToHomeListener) {
            romStoreCallToHomeListener = (RomStoreCallToHomeListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        romStoreCallToHomeListener = null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentHomeRomStoreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAdapter = new RomStoreHomeAdpater(getContext(), data);
        binding.rvRomlist.setAdapter(mAdapter);
        binding.rvRomlist.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));

        homeRomStoreViewModel = new ViewModelProvider(requireActivity()).get(HomeRomStoreViewModel.class);
        mainUiStateViewModel = new ViewModelProvider(requireActivity()).get(MainUiStateViewModel.class);
        downloadViewModel = new ViewModelProvider(requireActivity()).get(DownloadViewModel.class);
        homeRomStoreViewModel.getRomsList().observe(getViewLifecycleOwner(), roms -> {
            if (roms == null || roms.isEmpty()) {
                loadFromServer();
            } else {
                binding.linearload.setVisibility(View.GONE);
                data.clear();
                data.addAll(roms);
                mAdapter.notifyDataSetChanged();
                bindDownloadObservers();
            }
        });
    }

    private void loadFromServer() {
        if (mainUiStateViewModel != null) {
            mainUiStateViewModel.setSearchReady(false);
        }
        if (romStoreCallToHomeListener != null) {
            romStoreCallToHomeListener.updateSearchStatus(false);
        }

        net = new RequestNetwork(requireActivity());
        _net_request_listener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                if (!response.isEmpty())
                    contentJSON = response;
                loadData();
                binding.linearload.setVisibility(View.GONE);
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                binding.linearload.setVisibility(View.GONE);
                binding.linearnothinghere.setVisibility(View.VISIBLE);
            }
        };

        binding.buttontryagain.setOnClickListener(v -> {
            binding.linearload.setVisibility(View.VISIBLE);
            net.startRequestNetwork(RequestNetworkController.GET,AppConfig.vectrasRaw + "vroms-store.json","",_net_request_listener);
        });

        net.startRequestNetwork(RequestNetworkController.GET, AppConfig.vectrasRaw + "vroms-store.json","",_net_request_listener);
    }

    private void bindDownloadObservers() {
        if (downloadViewModel == null) {
            return;
        }
        for (DataRoms rom : data) {
            String romId = RomStoreHomeAdpater.resolveRomId(rom);
            downloadViewModel.observeState(romId).observe(getViewLifecycleOwner(), state ->
                    mAdapter.updateDownloadState(romId, state));
        }
    }

    private List<DataRoms> deduplicateByVecid(@Nullable List<DataRoms> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, DataRoms> uniqueById = new LinkedHashMap<>();
        for (DataRoms rom : source) {
            if (rom == null) {
                continue;
            }
            String key = RomStoreHomeAdpater.resolveRomId(rom);
            uniqueById.put(key, rom);
        }
        return new ArrayList<>(uniqueById.values());
    }

    private void loadData() {
        List<DataRoms> dataRoms = new ArrayList<>();

        try {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<DataRoms>>() {}.getType();
            dataRoms = gson.fromJson(contentJSON, listType);
        } catch (Exception e) {
            binding.linearload.setVisibility(View.GONE);
            binding.linearnothinghere.setVisibility(View.VISIBLE);
        }

        dataRoms = filterInvalidSha256(dataRoms);
        dataRoms = deduplicateByVecid(dataRoms);

        homeRomStoreViewModel.setRomsList(dataRoms);
        data.clear();
        data.addAll(dataRoms);
        mAdapter.notifyDataSetChanged();
        bindDownloadObservers();
        SharedData.dataRomStore.clear();
        SharedData.dataRomStore.addAll(dataRoms);
        if (mainUiStateViewModel != null) {
            mainUiStateViewModel.setSearchReady(true);
        }
        if (romStoreCallToHomeListener != null) {
            romStoreCallToHomeListener.updateSearchStatus(true);
        }
    }


    private List<DataRoms> filterInvalidSha256(List<DataRoms> source) {
        List<DataRoms> filtered = new ArrayList<>();
        if (source == null) {
            return filtered;
        }
        for (DataRoms item : source) {
            if (item == null) {
                Log.w(TAG, "Skipping null ROM item from catalog");
                continue;
            }
            if (!isValidSha256(item.sha256)) {
                Log.w(TAG, "Skipping ROM item without valid sha256. name=" + item.romName + " id=" + item.id + " vecid=" + item.vecid + " sha256=" + item.sha256);
                continue;
            }
            item.sha256 = item.sha256.trim().toLowerCase(Locale.US);
            filtered.add(item);
        }
        return filtered;
    }

    private boolean isValidSha256(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.length() != 64) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lowerHex = c >= 'a' && c <= 'f';
            boolean upperHex = c >= 'A' && c <= 'F';
            if (!(digit || lowerHex || upperHex)) {
                return false;
            }
        }
        return true;
    }


}
