package com.jatrail;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public final class TrailHistoryActivity extends AppCompatActivity {
    private TrailRepository trailRepository;
    private LinearLayout trailList;
    private TextView emptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowInsetsHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_trail_history);
        WindowInsetsHelper.setUpToolbar(this, R.string.trail_history_title, true);
        WindowInsetsHelper.applyContentInsets(this);
        trailRepository = new TrailRepository(this);
        trailList = findViewById(R.id.trail_history_list);
        emptyMessage = findViewById(R.id.trail_history_empty);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrails();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadTrails() {
        trailRepository.getTrailSummariesAsync(summaries -> {
            trailList.removeAllViews();
            emptyMessage.setVisibility(summaries.isEmpty() ? View.VISIBLE : View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (TrailRepository.TrailSummary summary : summaries) {
                View row = inflater.inflate(R.layout.item_trail_history, trailList, false);
                ((TextView) row.findViewById(R.id.trail_item_name))
                        .setText(displayName(summary.trail));
                ((TextView) row.findViewById(R.id.trail_item_date))
                        .setText(displayDate(summary.trail.createdAt));
                ((TextView) row.findViewById(R.id.trail_item_stats)).setText(getString(
                        R.string.trail_summary_stats,
                        summary.pointCount,
                        displayDuration(summary.durationMs),
                        displayDistance(summary.distanceMeters)));
                ((TextView) row.findViewById(R.id.trail_item_upload_state))
                        .setText(uploadStateLabel(summary.trail.uploadState));
                row.setOnClickListener(view -> startActivity(
                        new Intent(this, TrailDetailActivity.class)
                                .putExtra(TrailDetailActivity.EXTRA_TRAIL_ID, summary.trail.id)));
                trailList.addView(row);
            }
        });
    }

    String displayName(TrailEntity trail) {
        return trail.name.trim().isEmpty()
                ? getString(R.string.trail_default_name, displayDate(trail.createdAt))
                : trail.name;
    }

    static String displayDate(String timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.length() >= 16
                ? timestamp.substring(0, 10) + " " + timestamp.substring(11, 16)
                : timestamp;
    }

    static String displayDuration(long durationMs) {
        long totalSeconds = Math.max(0, durationMs) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = totalSeconds % 3600 / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
    }

    String displayDistance(double distanceMeters) {
        return DistanceFormatter.format(this, distanceMeters);
    }

    int uploadStateLabel(String state) {
        if (TrailEntity.UPLOAD_QUEUED.equals(state)) {
            return R.string.trail_upload_queued;
        }
        if (TrailEntity.UPLOAD_UPLOADING.equals(state)) {
            return R.string.trail_upload_uploading;
        }
        if (TrailEntity.UPLOAD_FAILED.equals(state)) {
            return R.string.trail_upload_failed_state;
        }
        if (TrailEntity.UPLOAD_UPLOADED.equals(state)) {
            return R.string.trail_upload_uploaded;
        }
        return R.string.trail_upload_local;
    }
}
