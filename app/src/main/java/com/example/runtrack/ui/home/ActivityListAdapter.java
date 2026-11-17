package com.example.runtrack.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.runtrack.R;
import com.example.runtrack.model.RunActivity;
import com.example.runtrack.util.ActivityCardMapBinder;
import com.example.runtrack.util.ActivityStatsHelper;
import com.example.runtrack.util.UnitUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.osmdroid.views.MapView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ActivityListAdapter extends RecyclerView.Adapter<ActivityListAdapter.ViewHolder> {

    private final List<RunActivity> items = new ArrayList<>();
    private final Context context;
    private OnActivityClickListener listener;
    private OnShareClickListener shareListener;
    private OnDeleteClickListener deleteListener;
    private OnRestoreClickListener restoreListener;
    private OnHardDeleteClickListener hardDeleteListener;
    private boolean showShareButton;
    private boolean trashMode;
    private String currentUserName;

    public interface OnActivityClickListener {
        void onActivityClick(RunActivity activity);
    }

    public interface OnShareClickListener {
        void onShare(RunActivity activity);
    }

    public interface OnDeleteClickListener {
        void onDelete(RunActivity activity);
    }

    public interface OnRestoreClickListener {
        void onRestore(RunActivity activity);
    }

    public interface OnHardDeleteClickListener {
        void onHardDelete(RunActivity activity);
    }

    public ActivityListAdapter(Context context) {
        this.context = context;
    }

    public void setOnActivityClickListener(OnActivityClickListener listener) {
        this.listener = listener;
    }

    public void setOnShareClickListener(OnShareClickListener listener) {
        this.shareListener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }

    public void setOnRestoreClickListener(OnRestoreClickListener listener) {
        this.restoreListener = listener;
    }

    public void setOnHardDeleteClickListener(OnHardDeleteClickListener listener) {
        this.hardDeleteListener = listener;
    }

    public void setShowShareButton(boolean show) {
        this.showShareButton = show;
    }

    public void setTrashMode(boolean trashMode) {
        this.trashMode = trashMode;
        notifyDataSetChanged();
    }

    public void setCurrentUserName(String name) {
        this.currentUserName = name;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RunActivity activity = items.get(position);

        holder.tvActivityName.setText(activity.activityName != null
                ? activity.activityName : "Lari");

        String name = resolveUserName();
        holder.tvUserName.setText(name);
        holder.tvAvatarInitial.setText(initialOf(name));

        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM • HH:mm", Locale.getDefault());
        holder.tvActivityTime.setText(dateFormat.format(new Date(activity.startTime)));

        holder.tvDistanceStat.setText(UnitUtils.distance(context, activity.getDistanceKm()));
        holder.tvElevationStat.setText(String.format(Locale.getDefault(),
                "⛰ %.0f m", activity.elevationGain));
        holder.tvDurationStat.setText(ActivityStatsHelper.formatDurationShort(activity));
        holder.tvPaceStat.setText(UnitUtils.pace(context,
                ActivityStatsHelper.getAvgPaceSecondsPerKm(activity)));
        holder.tvCaloriesStat.setText(String.format(Locale.getDefault(),
                "🔥 %.0f Cal", activity.calories));
        holder.tvHeartRateStat.setText(activity.maxSpeed > 0
                ? "⚡ " + UnitUtils.speedWithUnit(context, activity.maxSpeed)
                : "⚡ -- " + UnitUtils.speedUnit(context));

        bindMapPreview(holder, activity);

        if (holder.btnShare != null) {
            LinearLayout.LayoutParams shareParams =
                    (LinearLayout.LayoutParams) holder.btnShare.getLayoutParams();
            if (trashMode) {
                holder.btnShare.setVisibility(View.VISIBLE);
                holder.btnShare.setText("Hapus Permanen");
                shareParams.width = 0;
                shareParams.weight = 1f;
                holder.btnShare.setLayoutParams(shareParams);
                holder.btnShare.setOnClickListener(v -> {
                    if (hardDeleteListener != null) hardDeleteListener.onHardDelete(activity);
                });
            } else {
                holder.btnShare.setVisibility(showShareButton ? View.VISIBLE : View.GONE);
                holder.btnShare.setText("");
                shareParams.width = (int) (48 * context.getResources().getDisplayMetrics().density);
                shareParams.weight = 0f;
                holder.btnShare.setLayoutParams(shareParams);
                holder.btnShare.setOnClickListener(v -> {
                    if (shareListener != null) shareListener.onShare(activity);
                });
            }
        }

        if (holder.btnViewActivity != null) {
            if (trashMode) {
                holder.btnViewActivity.setText("Pulihkan");
                holder.btnViewActivity.setOnClickListener(v -> {
                    if (restoreListener != null) restoreListener.onRestore(activity);
                });
            } else {
                holder.btnViewActivity.setText("Lihat Detail");
                holder.btnViewActivity.setOnClickListener(v -> {
                    if (listener != null) listener.onActivityClick(activity);
                });
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (!trashMode && listener != null) listener.onActivityClick(activity);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (!trashMode && deleteListener != null) {
                deleteListener.onDelete(activity);
                return true;
            }
            return false;
        });
    }

    private String resolveUserName() {
        if (currentUserName != null && !currentUserName.isEmpty()) return currentUserName;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        return "Runner";
    }

    private String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "R";
        return name.trim().substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private void bindMapPreview(ViewHolder holder, RunActivity activity) {
        if (holder.mapPreview == null) return;
        String mapKey = activity.id != null ? activity.id : String.valueOf(activity.startTime);

        if (holder.tvMapPlaceholder != null) {
            holder.tvMapPlaceholder.setVisibility(View.GONE);
        }

        if (mapKey.equals(holder.boundMapKey)) return;
        holder.boundMapKey = mapKey;
        ActivityCardMapBinder.bind(holder.mapPreview, activity);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder.mapPreview != null) holder.mapPreview.onResume();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        if (holder.mapPreview != null) holder.mapPreview.onPause();
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        ActivityCardMapBinder.release(holder.mapPreview);
        holder.boundMapKey = null;
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateItems(List<RunActivity> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatarInitial;
        MapView mapPreview;
        TextView tvMapPlaceholder;
        TextView tvUserName;
        TextView tvActivityTime;
        TextView tvActivityName;
        TextView tvDistanceStat;
        TextView tvElevationStat;
        TextView tvDurationStat;
        TextView tvPaceStat;
        TextView tvCaloriesStat;
        TextView tvHeartRateStat;
        MaterialButton btnViewActivity;
        MaterialButton btnShare;
        String boundMapKey;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatarInitial = itemView.findViewById(R.id.tv_avatar_initial);
            mapPreview = itemView.findViewById(R.id.map_activity_preview);
            tvMapPlaceholder = itemView.findViewById(R.id.tv_map_placeholder);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvActivityTime = itemView.findViewById(R.id.tv_activity_time);
            tvActivityName = itemView.findViewById(R.id.tv_activity_name);
            tvDistanceStat = itemView.findViewById(R.id.tv_distance_stat);
            tvElevationStat = itemView.findViewById(R.id.tv_elevation_stat);
            tvDurationStat = itemView.findViewById(R.id.tv_duration_stat);
            tvPaceStat = itemView.findViewById(R.id.tv_pace_stat);
            tvCaloriesStat = itemView.findViewById(R.id.tv_calories_stat);
            tvHeartRateStat = itemView.findViewById(R.id.tv_heart_rate_stat);
            btnViewActivity = itemView.findViewById(R.id.btn_view_activity);
            btnShare = itemView.findViewById(R.id.btn_share_activity);
        }
    }
}
