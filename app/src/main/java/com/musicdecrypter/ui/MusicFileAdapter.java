package com.musicdecrypter.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MusicFileAdapter extends RecyclerView.Adapter<MusicFileAdapter.ViewHolder> {

    private final List<String> musicFiles;

    public MusicFileAdapter(List<String> musicFiles) {
        this.musicFiles = musicFiles;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String fileName = musicFiles.get(position);
        holder.textView.setText(fileName);
        holder.textView.setTextSize(16f);
        holder.textView.setPadding(16, 16, 16, 16);
    }

    @Override
    public int getItemCount() {
        return musicFiles.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
