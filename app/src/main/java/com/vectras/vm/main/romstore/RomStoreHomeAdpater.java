package com.vectras.vm.main.romstore;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.vectras.vm.R;
import com.vectras.vm.RomInfo;
import com.vectras.vm.download.DownloadItemState;
import com.vectras.vm.download.DownloadStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RomStoreHomeAdpater extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    Context context;
    private final LayoutInflater inflater;
    static List<DataRoms> dataRom = Collections.emptyList();
    private final Map<String, DownloadItemState> downloadsByRomId = new HashMap<>();

    public RomStoreHomeAdpater(Context context, List<DataRoms> data) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        dataRom = data;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.container_roms, parent, false);
        return new MyHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        bind((MyHolder) holder, dataRom.get(position), position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        bindDownloadState((MyHolder) holder, payloads.get(payloads.size() - 1) instanceof DownloadItemState ? (DownloadItemState) payloads.get(payloads.size() - 1) : null);
    }

    private void bind(@NonNull MyHolder myHolder, @NonNull DataRoms current, int position) {
        Glide.with(context).load(current.romIcon).placeholder(R.drawable.ic_computer_180dp_with_padding).error(R.drawable.ic_computer_180dp_with_padding).into(myHolder.ivIcon);
        myHolder.textName.setText(current.romName);
        myHolder.textSize.setText(current.romArch + " - " + current.fileSize);
        if (Boolean.TRUE.equals(current.romAvail)) {
            myHolder.textAvail.setText(context.getString(R.string.available));
            myHolder.textAvail.setTextColor(myHolder.textName.getCurrentTextColor());
            myHolder.linearItem.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setClass(context, RomInfo.class);
                intent.putExtra("title", current.romName);
                intent.putExtra("shortdesc", current.romSize);
                intent.putExtra("getrom", current.romUrl);
                intent.putExtra("desc", current.desc);
                intent.putExtra("icon", current.romIcon);
                intent.putExtra("filename", current.romPath);
                intent.putExtra("finalromfilename", current.finalromfilename);
                intent.putExtra("extra", current.romExtra);
                intent.putExtra("arch", current.romArch);
                intent.putExtra("verified", current.verified);
                intent.putExtra("creator", current.creator);
                intent.putExtra("size", current.fileSize);
                intent.putExtra("id", current.id);
                intent.putExtra("vecid", current.vecid);
                intent.putExtra("hash", current.hash);
                intent.putExtra("isRomInfo", true);
                context.startActivity(intent);
            });
        } else {
            myHolder.textAvail.setText(context.getString(R.string.unavailable));
            myHolder.textAvail.setTextColor(Color.RED);
            myHolder.linearItem.setOnClickListener(null);
        }

        bindDownloadState(myHolder, downloadsByRomId.get(resolveRomId(current)));

        if (dataRom.size() == 1) {
            myHolder.linearItem.setBackground(AppCompatResources.getDrawable(context, R.drawable.object_shape_single));
        } else if (position == 0) {
            myHolder.linearItem.setBackground(AppCompatResources.getDrawable(context, R.drawable.object_shape_top));
        } else if (position == dataRom.size() - 1) {
            myHolder.linearItem.setBackground(AppCompatResources.getDrawable(context, R.drawable.object_shape_bottom));
        } else {
            myHolder.linearItem.setBackground(AppCompatResources.getDrawable(context, R.drawable.object_shape_middle));
        }
    }

    private void bindDownloadState(@NonNull MyHolder holder, @Nullable DownloadItemState state) {
        if (state == null) {
            holder.downloadProgress.setVisibility(View.GONE);
            holder.downloadBadge.setVisibility(View.GONE);
            return;
        }
        holder.downloadBadge.setVisibility(View.VISIBLE);
        holder.downloadBadge.setText(state.status);

        if (DownloadStatus.RUNNING.equals(state.status) || DownloadStatus.QUEUED.equals(state.status) || DownloadStatus.PAUSED.equals(state.status)) {
            holder.downloadProgress.setVisibility(View.VISIBLE);
            int percent = state.totalBytes > 0L ? (int) ((state.bytesDownloaded * 100L) / state.totalBytes) : 0;
            holder.downloadProgress.setProgress(Math.max(0, Math.min(100, percent)));
        } else {
            holder.downloadProgress.setVisibility(View.GONE);
        }

        if (state.totalBytes > 0L) {
            int percent = (int) ((state.bytesDownloaded * 100L) / state.totalBytes);
            holder.textAvail.setText(String.format(Locale.US, "%s • %d%%", holder.downloadBadge.getText(), Math.max(0, Math.min(100, percent))));
        } else {
            holder.textAvail.setText(state.status);
        }
    }

    public void updateDownloadState(@NonNull String romId, @Nullable DownloadItemState state) {
        if (state == null) {
            downloadsByRomId.remove(romId);
        } else {
            downloadsByRomId.put(romId, state);
        }

        int position = findPositionByRomId(romId);
        if (position >= 0) {
            notifyItemChanged(position, state);
        }
    }

    public int findPositionByRomId(@NonNull String romId) {
        for (int i = 0; i < dataRom.size(); i++) {
            if (romId.equals(resolveRomId(dataRom.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    public static String resolveRomId(@NonNull DataRoms rom) {
        if (rom.id != null && !rom.id.trim().isEmpty()) {
            return rom.id;
        }
        if (rom.vecid != null && !rom.vecid.trim().isEmpty()) {
            return rom.vecid;
        }
        String url = rom.romUrl == null ? "" : rom.romUrl;
        return String.valueOf(Math.abs(url.hashCode()));
    }

    @Override
    public int getItemCount() {
        return dataRom == null ? 0 : dataRom.size();
    }

    static class MyHolder extends RecyclerView.ViewHolder {

        TextView textName, textAvail, textSize, downloadBadge;
        ImageView ivIcon;
        LinearLayout linearItem;
        ProgressBar downloadProgress;

        public MyHolder(View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.textName);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            textSize = itemView.findViewById(R.id.textSize);
            textAvail = itemView.findViewById(R.id.textAvail);
            downloadBadge = itemView.findViewById(R.id.textDownloadBadge);
            downloadProgress = itemView.findViewById(R.id.progressDownloadMini);
            linearItem = itemView.findViewById(R.id.linearItem);
        }

    }
}
