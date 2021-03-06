/*
 * Copyright 2017.  Dmitry Malkovich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dmitrymalkovich.android.githubanalytics.traffic;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.dmitrymalkovich.android.githubanalytics.R;
import com.dmitrymalkovich.android.githubanalytics.data.source.GithubRepository;
import com.dmitrymalkovich.android.githubanalytics.data.source.local.LoaderProvider;
import com.dmitrymalkovich.android.githubanalytics.data.source.local.contract.ClonesContract;
import com.dmitrymalkovich.android.githubanalytics.data.source.local.contract.RepositoryContract;
import com.dmitrymalkovich.android.githubanalytics.data.source.local.contract.ViewsContract;
import com.dmitrymalkovich.android.githubanalytics.settings.SettingsActivity;
import com.dmitrymalkovich.android.githubanalytics.Utils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.AxisValueFormatter;
import com.google.firebase.crash.FirebaseCrash;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * https://github.com/PhilJay/MPAndroidChart/wiki/Getting-Started
 */
public class TrafficFragment extends Fragment implements TrafficContract.View {
    @SuppressWarnings("unused")
    private static final String LOG_TAG = TrafficFragment.class.getSimpleName();
    public static String ARG_REPOSITORY_ID = "ARG_REPOSITORY_ID";
    public
    @BindView(R.id.progress)
    ProgressBar mProgressBar;
    @BindView(R.id.empty_state)
    public View mEmptyState;
    @BindView(R.id.recycler_view_for_referrers)
    public RecyclerView mRecyclerView;
    @BindView(R.id.chart_clones)
    public LineChart mChartClones;
    @BindView(R.id.chart_views)
    public LineChart mChartViews;
    @BindView(R.id.subtitle)
    public TextView mSubtitleView;
    @BindView(R.id.stars_total)
    public TextView mStarsTotalView;
    @BindView(R.id.language)
    public TextView mLanguageView;
    @BindView(R.id.language_icon)
    public ImageView mLanguageIconView;
    @BindView(R.id.forks_total)
    public TextView mTotalForksView;
    @BindView(R.id.empty_state_title)
    public TextView mEmptyStateTextView;
    private TrafficContract.Presenter mPresenter;
    private Unbinder unbinder;
    private ReferrersListAdapter mAdapter;

    public static TrafficFragment newInstance(long repositoryId) {
        TrafficFragment trafficFragment = new TrafficFragment();
        Bundle bundle = new Bundle();
        bundle.putLong(ARG_REPOSITORY_ID, repositoryId);
        trafficFragment.setArguments(bundle);
        return trafficFragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_traffic, container, false);
        unbinder = ButterKnife.bind(this, root);

        if (getActivity() instanceof AppCompatActivity) {
            setTitle("");
        }

        if (getArguments() != null && getArguments().containsKey(ARG_REPOSITORY_ID)) {
            long repositoryId = getArguments().getLong(ARG_REPOSITORY_ID);
            new TrafficPresenter(
                    GithubRepository.Injection.provideGithubRepository(getActivity()),
                    this,
                    new LoaderProvider(getContext()),
                    getLoaderManager());
            mPresenter.start(savedInstanceState, repositoryId);
        }

        int columnCount = getResources().getInteger(R.integer.trending_grid_column_count);
        StaggeredGridLayoutManager staggeredGridLayoutManager =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(staggeredGridLayoutManager);
        mRecyclerView.setNestedScrollingEnabled(false);

        mAdapter = new ReferrersListAdapter(null, this);
        mAdapter.setHasStableIds(true);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setNestedScrollingEnabled(false);
        mRecyclerView.setHasFixedSize(false);

        chartStyling(mChartClones);
        chartStyling(mChartViews);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mChartViews.requestLayout();
                mChartClones.requestLayout();
            }
        }, 50);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void setPresenter(TrafficContract.Presenter presenter) {
        mPresenter = presenter;
    }

    @Override
    public void setLoadingIndicator(boolean active) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(active ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void setEmptyState(boolean active) {
        if (mEmptyState != null) {
            mEmptyState.setVisibility(active ? View.VISIBLE : View.GONE);

            if (!Utils.isNetworkAvailable()) {
                mEmptyStateTextView.setText(R.string.no_internet_connection);
            } else {
                mEmptyStateTextView.setText(R.string.dashboard_empty_view_title);
            }

        }
    }

    @Override
    public void openUrl(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            getActivity().startActivity(browserIntent);
        } catch (ActivityNotFoundException e) {
            FirebaseCrash.report(e);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void showRepository(Cursor cursor) {

        String repositoryName = cursor.getString(RepositoryContract.RepositoryEntry.COL_REPOSITORY_NAME);
        String desc = cursor.getString(RepositoryContract.RepositoryEntry.COL_REPOSITORY_DESCRIPTION);
        String stars = cursor.getString(RepositoryContract.RepositoryEntry.COL_REPOSITORY_WATCHERS);
        String forks = cursor.getString(RepositoryContract.RepositoryEntry.COL_REPOSITORY_FORKS);
        String language = cursor.getString(RepositoryContract.RepositoryEntry.COL_REPOSITORY_LANGUAGE);

        setTitle(repositoryName);

        mSubtitleView.setText(desc);
        mStarsTotalView.setText(stars);
        mTotalForksView.setText(forks);
        mLanguageView.setText(language);
        mLanguageIconView.setBackgroundDrawable(Utils.getColor(getContext(), language));
        mLanguageIconView.setVisibility(mLanguageView.getText() != null && mLanguageView.getText().length() != 0 ? View.VISIBLE : View.GONE);
        mLanguageView.setVisibility(mLanguageIconView.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);

        showTraffic(cursor);
    }

    private void showTraffic(Cursor cursor) {
        String clonesCount = cursor.getString(RepositoryContract.RepositoryEntry.COL_CLONES_COUNT);
        String clonesCountYesterday = cursor.getString(RepositoryContract.RepositoryEntry.COL_CLONES_UNIQUES_YESTERDAY);
        String clonesCountTwoWeeks = cursor.getString(RepositoryContract.RepositoryEntry.COL_CLONES_UNIQUES_TWO_WEEKS);
        String viewsUniques = cursor.getString(RepositoryContract.RepositoryEntry.COL_VIEWS_UNIQUES);
        String viewsUniquesYesterday = cursor.getString(RepositoryContract.RepositoryEntry.COL_VIEWS_UNIQUES_YESTERDAY);
        String viewsUniquesTwoWeeks = cursor.getString(RepositoryContract.RepositoryEntry.COL_VIEWS_UNIQUES_TWO_WEEKS);
        String stargazersToday = cursor.getString(RepositoryContract.RepositoryEntry.COL_STARGAZERS_STARS);
        String stargazersYesterday = cursor.getString(RepositoryContract.RepositoryEntry.COL_STARGAZERS_STARS_YESTERDAY);
        String stargazersTwoWeeks = cursor.getString(RepositoryContract.RepositoryEntry.COL_STARGAZERS_STARS_TWO_WEEKS);


        if (getView() != null) {
            View todayView = getView().findViewById(R.id.today);
            TextView clonesCountTodayView = (TextView) todayView.findViewById(R.id.clones_uniques);
            TextView viewsUniquesTodayView = (TextView) todayView.findViewById(R.id.views_uniques);
            TextView starsTodayView = (TextView) todayView.findViewById(R.id.stars_today);

            View yesterdayView = getView().findViewById(R.id.yesterday);
            TextView clonesCountYesterdayView = (TextView) yesterdayView.findViewById(R.id.clones_uniques);
            TextView viewsUniquesYesterdayView = (TextView) yesterdayView.findViewById(R.id.views_uniques);
            TextView starsYesterdayView = (TextView) yesterdayView.findViewById(R.id.stars_today);

            View twoWeeksView = getView().findViewById(R.id.two_weeks);
            TextView clonesCountTwoWeeksView = (TextView) twoWeeksView.findViewById(R.id.clones_uniques);
            TextView viewsUniquesTwoWeeksView = (TextView) twoWeeksView.findViewById(R.id.views_uniques);
            TextView starsTwoWeeksView = (TextView) twoWeeksView.findViewById(R.id.stars_today);

            String defaultValue = "0";

            clonesCountTodayView.setText(clonesCount != null ? clonesCount : defaultValue);
            clonesCountYesterdayView.setText(clonesCountYesterday != null ? clonesCountYesterday : defaultValue);
            clonesCountTwoWeeksView.setText(clonesCountTwoWeeks != null ? clonesCountTwoWeeks : defaultValue);

            viewsUniquesTodayView.setText(viewsUniques != null ? viewsUniques : defaultValue);
            viewsUniquesYesterdayView.setText(viewsUniquesYesterday != null ? viewsUniquesYesterday : defaultValue);
            viewsUniquesTwoWeeksView.setText(viewsUniquesTwoWeeks != null ? viewsUniquesTwoWeeks : defaultValue);

            starsTodayView.setText(stargazersToday != null ? stargazersToday : defaultValue);
            starsYesterdayView.setText(stargazersYesterday != null ? stargazersYesterday : defaultValue);
            starsTwoWeeksView.setText(stargazersTwoWeeks != null ? stargazersTwoWeeks : defaultValue);
        }
    }

    @Override
    public void showReferrers(Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void showClones(Cursor data) {
        List<Entry> entries = new ArrayList<>();
        if (data.moveToFirst()) {
            do {
                int uniquesClones = data.getInt(ClonesContract.ClonesEntry.COL_CLONES_UNIQUES);
                long timestamp = data.getLong(ClonesContract.ClonesEntry.COL_CLONES_TIMESTAMP);
                entries.add(new Entry(timestamp, uniquesClones));
            } while (data.moveToNext());
        }
        LineDataSet lineDataSet = new LineDataSet(entries, getString(R.string.git_clones));
        chartDataSetStyling(lineDataSet);
        LineData lineData = new LineData(lineDataSet);
        mChartClones.setData(lineData);
    }

    @Override
    public void showViews(Cursor data) {
        List<Entry> values = new ArrayList<>();
        if (data.moveToFirst()) {
            do {
                int uniquesViews = data.getInt(ViewsContract.ViewsEntry.COL_VIEWS_UNIQUES);
                long timestamp = data.getLong(ViewsContract.ViewsEntry.COL_VIEWS_TIMESTAMP);
                values.add(new Entry(timestamp, uniquesViews));
            } while (data.moveToNext());
        }
        LineDataSet lineDataSet = new LineDataSet(values, getString(R.string.visitors));
        chartDataSetStyling(lineDataSet);
        LineData lineData = new LineData(lineDataSet);
        mChartViews.setData(lineData);
    }

    @SuppressWarnings("deprecation")
    private void chartStyling(LineChart chart) {
        chart.setTouchEnabled(false);
        chart.setDescription("");
        chart.setAutoScaleMinMaxEnabled(false);
        chart.setNoDataTextColor(SettingsActivity.ThemePreferenceFragment.isLight(getContext()) ? Color.BLACK : Color.WHITE);
        YAxis axisRight = chart.getAxisRight();
        axisRight.setEnabled(false);
        chart.getLegend().setEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            chart.setNestedScrollingEnabled(false);
        }

        XAxis xAxis = chart.getXAxis();
        chartXAxisStyling(xAxis);
        YAxis yAxis = chart.getAxisLeft();
        chartYAxisStyling(yAxis);
    }

    @SuppressWarnings("deprecation")
    private void chartXAxisStyling(XAxis xAxis) {
        xAxis.setPosition(XAxis.XAxisPosition.TOP);
        xAxis.setTextColor(getResources().getColor(R.color.traffic_chart_text_color_light));
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setCenterAxisLabels(true);
        xAxis.setValueFormatter(new AxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return Utils.humanReadable((long) value);
            }

            @Override
            public int getDecimalDigits() {
                return 0;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void chartYAxisStyling(YAxis yAxis) {
        yAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        yAxis.setTextColor(SettingsActivity.ThemePreferenceFragment.isLight(getContext()) ?
                getResources().getColor(R.color.traffic_chart_text_color_light) :
                getResources().getColor(R.color.traffic_chart_text_color_dark));
        yAxis.setDrawGridLines(false);
        yAxis.setGranularityEnabled(true);
        yAxis.setDrawAxisLine(true);
    }

    @SuppressWarnings("deprecation")
    private void chartDataSetStyling(LineDataSet dataSet) {
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSet.setColor(SettingsActivity.ThemePreferenceFragment.isLight(getContext()) ?
                getResources().getColor(R.color.traffic_chart_line_color_light) :
                getResources().getColor(R.color.traffic_chart_line_color_dark));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircleHole(false);
        dataSet.setCircleColor(SettingsActivity.ThemePreferenceFragment.isLight(getContext()) ?
                getResources().getColor(R.color.traffic_chart_line_color_light) :
                getResources().getColor(R.color.traffic_chart_line_color_dark));
    }

    private void setTitle(String title) {
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(title);
            }
        }
    }
}
