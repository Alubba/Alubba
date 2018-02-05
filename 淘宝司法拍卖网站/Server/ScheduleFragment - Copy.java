package com.vuspex.contractor.ui.schedule;

import android.app.Fragment;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.vuspex.contractor.ui.imageEdit.StringUtils;
import com.vuspex.contractor.ui.inspection.InspectionDetailActivity;
import com.vuspex.contractor.utils.Log;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.vuspex.contractor.BusProvider;
import com.vuspex.contractor.Config;
import com.vuspex.contractor.Prefs;
import com.vuspex.contractor.R;
import com.vuspex.contractor.api.ApiHelper;
import com.vuspex.contractor.api.parser.ScheduledParser;
import com.vuspex.contractor.api.parser.SignInParser;
import com.vuspex.contractor.database.VuSpexContract;
import com.vuspex.contractor.utils.NetworkUtils;
import com.vuspex.contractor.utils.UIUtils;
import com.vuspex.contractor.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class ScheduleFragment1 extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    private RecyclerView lvSchedule;
    private RecyclerView lvScheduleOnline;
    private RecyclerView lvScheduleOffline;
    private ProgressBar progress;
    private TextView tvNoData;
    private ScheduleAdapter adapter;

    private int sortType = 0;
    private int scheduleTypeInt = ScheduleType.ALL.ordinal();
    private String scheduleDateRange;

    private String noAppointmentsMsg;
    private SwipeRefreshLayout srlMain;
    private ProgressDialog progressDialog;
    private boolean hasSynced = false;
    private String sortOrder;
    private boolean isRefresh = false;
    public static TextView updateTime;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_schedule, container, false);
        this.lvSchedule = (RecyclerView) view.findViewById(R.id.schedule_list);
        this.lvScheduleOnline = (RecyclerView) view.findViewById(R.id.schedule_list_online);
        this.lvScheduleOffline = (RecyclerView) view.findViewById(R.id.schedule_list_offline);
        this.progress = (ProgressBar) view.findViewById(R.id.schedule_pb_progress);
        this.tvNoData = (TextView) view.findViewById(R.id.schedule_tv_no_schedule);
        this.srlMain = (SwipeRefreshLayout) view.findViewById(R.id.srlMain);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapter = new ScheduleAdapter();
        lvSchedule.setLayoutManager(new LinearLayoutManager(getActivity()));
        lvSchedule.setItemAnimator(new DefaultItemAnimator());
        lvSchedule.setAdapter(adapter);


        lvScheduleOnline.setLayoutManager(new LinearLayoutManager(getActivity()));
        lvScheduleOnline.setItemAnimator(new DefaultItemAnimator());
        lvScheduleOnline.setAdapter(adapter);


        lvScheduleOffline.setLayoutManager(new LinearLayoutManager(getActivity()));
        lvScheduleOffline.setItemAnimator(new DefaultItemAnimator());
        lvScheduleOffline.setAdapter(adapter);


        srlMain.setDistanceToTriggerSync(300);
        srlMain.setSize(SwipeRefreshLayout.LARGE);
        srlMain.setOnRefreshListener(this);


        ScheduleAdapter.updateTime = updateTime;
        if (!NetworkUtils.isInternetAvailable(getActivity())) {

            getLoaderManager().initLoader(0, null, scheduleTypeLoader);
            return;

        }
//        getLoaderManager().initLoader(0, null, scheduleTypeLoader);


        //progress.setVisibility(View.VISIBLE);
        noAppointmentsMsg = getString(R.string.text_no_scheduled_today);


    }

    @Override
    public void onStart() {
        super.onStart();

        if (NetworkUtils.isInternetAvailable(getActivity()) && !hasSynced) {
            showProgressDialog();
            new ApiHelper(getActivity()).getScheduledAppointments(scheduleCallback);
            hasSynced = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        BusProvider.getBus().unregister(ScheduleFragment.this);
    }

    @Override
    public void onResume() {
        super.onResume();
        BusProvider.getBus().register(ScheduleFragment.this);
    }

    private Callback<Response> scheduleCallback = new Callback<Response>() {

        @Override
        public void success(Response result, Response response) {
            new ScheduledParser(getActivity()).execute(result);
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            dismissProgressDialog();
            if (srlMain.isRefreshing()) {
                srlMain.setRefreshing(false);
            }
            if (NetworkUtils.isUnauthorized(retrofitError)) {
                reloginUser();
                return;
            }
            if (tvNoData != null) {
                tvNoData.setText(noAppointmentsMsg);
                tvNoData.setVisibility(View.VISIBLE);
            }

            Log.e(Config.REST_LOG_TAG, "Unable to get scheduled appointments. " + retrofitError.getMessage());
            UIUtils.showToast(getActivity(), R.string.error_unable_to_get_scheduled_appointments);
        }
    };


    private Callback<Response> signInCallback = new Callback<Response>() {

        @Override
        public void success(Response result, Response response) {
            new SignInParser(getActivity()).execute(result);
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            if (progress != null)
                progress.setVisibility(View.GONE);

            if (tvNoData != null) {
                tvNoData.setText(noAppointmentsMsg);
                tvNoData.setVisibility(View.VISIBLE);
            }

            UIUtils.showToast(getActivity(), getString(R.string.error_internal_server_error));
            Log.e(Config.REST_LOG_TAG, "Unable to sign in", retrofitError);
        }
    };

    private LoaderManager.LoaderCallbacks<Cursor> scheduleLoader = new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
            final String sortOrder = VuSpexContract.Appointments.SCHEDULED_MS + " ASC";

            return new CursorLoader(getActivity(), VuSpexContract.Appointments.CONTENT_URI, null, ScheduleHelper.getSelection(scheduleDateRange, sortOrder), null, sortOrder);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
            if (progress != null) {
                if (cursor.getCount() == 0 && progress.getVisibility() == View.GONE) {
                    if (tvNoData != null) {
                        tvNoData.setText(noAppointmentsMsg);
                        tvNoData.setVisibility(View.VISIBLE);
                    }
                    return;
                }
            }
            if (tvNoData != null)
                tvNoData.setVisibility(View.GONE);
            if (srlMain.isRefreshing()) {
                srlMain.setRefreshing(false);
            }
            adapter.swapCursor(cursor, getActivity());
        }

        @Override
        public void onLoaderReset(Loader<Cursor> cursorLoader) {
            adapter.swapCursor(null, getActivity());
        }
    };


    private LoaderManager.LoaderCallbacks<Cursor> scheduleTypeLoader = new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
//            final String sortOrder = VuSpexContract.Appointments.SCHEDULED_MS + " ASC";
            String where = null;
            //selection4schedule : All , Live , Offline
            String selection4Schedule = ScheduleHelper.getSelection4Schedule(scheduleTypeInt);
            noAppointmentsMsg = ScheduleHelper.getMessageLabel(scheduleTypeInt);
            //selectionDate :  Today , In 7 days , Past due scheduleDateRange
            String selectionDate = ScheduleHelper.getSelection(scheduleDateRange, sortOrder);
            sortOrder = sortOrder + "," + VuSpexContract.Appointments.PERMIT_NUM + " ASC ";
            if (!selection4Schedule.isEmpty()) {
                where = selection4Schedule + " and " + selectionDate;
            } else {
                where = selectionDate;
            }
            where = where + " and " + VuSpexContract.Appointments.CONTRACTOR_ID + " = '" + Utils.getCurrentUserId() + "'";
            return new CursorLoader(getActivity(), VuSpexContract.Appointments.CONTENT_URI, null, where, null, sortOrder);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
            if (progress != null) {
                if (cursor.getCount() == 0 && progress.getVisibility() == View.GONE) {
                    if (tvNoData != null) {
                        tvNoData.setText(noAppointmentsMsg);
                        tvNoData.setVisibility(View.VISIBLE);
                        lvSchedule.setVisibility(View.GONE);
                    }
                    return;
                }
            }
            if (tvNoData != null)
                lvSchedule.setVisibility(View.VISIBLE);
            tvNoData.setVisibility(View.GONE);

            if (srlMain.isRefreshing()) {
                srlMain.setRefreshing(false);
            }

            adapter.swapCursor(cursor, getActivity());
        }

        @Override
        public void onLoaderReset(Loader<Cursor> cursorLoader) {
            adapter.swapCursor(null, getActivity());
        }
    };

    public void onEventMainThread(ScheduledParser.AppointmentsRetrievedEvent event) {
        getLoaderManager().initLoader(0, null, scheduleTypeLoader);
        final ScheduleType[] scheduleTypes = ScheduleType.values();
        switch (scheduleTypes[scheduleTypeInt]) {
            case ALL:
                noAppointmentsMsg = getString(R.string.text_no_scheduled_today);
                if (event.hasData) {
                    progress.setVisibility(View.GONE);
                    getLoaderManager().restartLoader(0, null, scheduleTypeLoader);

                } else {
                    progress.setVisibility(View.GONE);
                    tvNoData.setText(noAppointmentsMsg);
                    tvNoData.setVisibility(View.VISIBLE);
                }
                break;
            case LIVE:
                noAppointmentsMsg = getString(R.string.text_no_online_inspection);
                getLoaderManager().restartLoader(0, null, scheduleLoader);
                break;
            default:
                break;
        }
        //getLoaderManager().initLoader(0, null, scheduleTypeLoader);

        UIUtils.showToastInCenter(getActivity(), R.string.sync_success);

        String lastUpdateDate = Utils.parseDate(new Date(System.currentTimeMillis()), "MMM dd hh:mm aa");

        updateTime.setText("Last updated:" + lastUpdateDate);

        ContentValues values = new ContentValues();
        values.put(VuSpexContract.Appointments.LAST_REFRESH_DATE, "Last updated:" + lastUpdateDate);
        final ContentResolver resolver = getActivity().getContentResolver();
        int i = resolver.update(VuSpexContract.Appointments.CONTENT_URI, values, null, null);
        dismissProgressDialog();


//        getLoaderManager().restartLoader(0, null, scheduleTypeLoader);
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(ScheduledParser.AppointmentsRetrievingFailed event) {
        progress.setVisibility(View.GONE);
        UIUtils.showToast(getActivity(), R.string.error_unable_to_get_scheduled_appointments);

        getLoaderManager().initLoader(0, null, scheduleLoader);
    }

    public void onEventMainThread(ScheduleActivity.ScheduleSortEvent event) {
        sortType = event.sortType;
        scheduleTypeInt = event.scheduleType;
        getLoaderManager().restartLoader(0, null, scheduleTypeLoader);
    }


//    public void onEventMainThread(ScheduleActivity.ScheduleOnlineAndOfflineInspectionEvent event) {
//        scheduleTypeInt = event._scheduleType;
//        getLoaderManager().restartLoader(0, null, scheduleTypeLoader);
//    }


    public void onEventMainThread(ScheduleAdapter.AppointmentClickEvent event) {
        final Intent intent = new Intent(getActivity(), AppointmentDetailsActivity.class);
        intent.putExtra("id", event.id);
        startActivity(intent);
    }

    /**
     * On successful signing up
     */
    @SuppressWarnings("unused")
    public void onEventMainThread(SignInParser.SignedInEvent event) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        Prefs.setStringProperty(getActivity(), R.string.key_access_token, event.token);
        new ApiHelper(getActivity()).getScheduledAppointments(scheduleCallback);
    }

    /**
     * On error during signup validation
     */
    @SuppressWarnings("unused")
    public void onEventMainThread(SignInParser.SignErrorEvent event) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        progress.setVisibility(View.GONE);
        tvNoData.setText(noAppointmentsMsg);
        tvNoData.setVisibility(View.VISIBLE);

        UIUtils.showToast(getActivity(), event.message);
    }

    /**
     * @param date - string of date
     * @return formatted string of date
     */
    public static String getConvertedScheduleDate(String date) {
        final SimpleDateFormat incomingFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        final SimpleDateFormat outgoingFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH);

        try {
            Date parsed = incomingFormat.parse(date);
            return outgoingFormat.format(parsed);
        } catch (Exception exc) {
            Log.d("Parse schedule date", "Unable to parse schedule date string to Date", exc);
        }

        return date;
    }

    private void reloginUser() {

        progressDialog = progressDialog == null ? new ProgressDialog(getActivity()) : progressDialog;
        progressDialog.setMessage(getString(R.string.text_login_you_back));
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();

        final String email = Prefs.getStringProperty(getActivity(), R.string.key_user_email);
        final String password = Prefs.getStringProperty(getActivity(), R.string.key_user_password);

        new ApiHelper(getActivity()).signIn(email, password, signInCallback);
    }


    public void onEventMainThread(ScheduleAdapter.InspectionClickEvent event) {
        final Intent intent = new Intent(getActivity(), InspectionDetailActivity.class);
        intent.putExtra("appointments", event.appointments);
        startActivity(intent);
    }

    public void onEventMainThread(ScheduleActivity.ScheduleRangeOnChangeEvent event) {

        scheduleDateRange = event.selectScheduleDateRange;

        getLoaderManager().restartLoader(0, null, scheduleTypeLoader);


    }

    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(getActivity());
        }

        progressDialog.setMessage(getString(R.string.text_syncing));
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.show();
    }

    /**
     * Dismiss progress dialog
     */
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        progressDialog = null;
    }

    @Override
    public void onRefresh() {
        if (!NetworkUtils.isInternetAvailable(getActivity())) {
            srlMain.setRefreshing(false);
            UIUtils.showToastInCenter(getActivity(), R.string.error_no_internet_connection);
            return;
        }

        if (ScheduleType.OFFLINE.ordinal() == scheduleTypeInt) {
            srlMain.setRefreshing(false);
            return;
        }

        if (!isRefresh) {
            isRefresh = true;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    new ApiHelper(getActivity()).getScheduledAppointments(scheduleCallback);

                }
            }, 300);

        }
        isRefresh = false;
    }
}
