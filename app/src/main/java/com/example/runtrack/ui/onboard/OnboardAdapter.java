package com.example.runtrack.ui.onboard;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.runtrack.R;

public class OnboardAdapter extends RecyclerView.Adapter<OnboardAdapter.VH> {
    private final Context ctx;
    private final int[] layouts = new int[] { R.layout.onboard_slide1, R.layout.onboard_slide2, R.layout.onboard_slide3 };

    public OnboardAdapter(Context ctx) { this.ctx = ctx; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(viewType, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) { }

    @Override
    public int getItemCount() { return layouts.length; }

    @Override
    public int getItemViewType(int position) { return layouts[position]; }

    static class VH extends RecyclerView.ViewHolder {
        public VH(@NonNull View itemView) { super(itemView); }
    }
}
