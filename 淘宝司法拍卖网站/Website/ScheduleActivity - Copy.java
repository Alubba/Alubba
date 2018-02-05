package com.vuspex.contractor.ui.schedule;


import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.birbit.android.jobqueue.JobManager;
import com.vuspex.contractor.BusProvider;
import com.vuspex.contractor.R;
import com.vuspex.contractor.common.ScheduleInspectionJob;
import com.vuspex.contractor.common.VSPApplication;
import com.vuspex.contractor.model.Appointments;
import com.vuspex.contractor.ui.uicommon.BaseActionBarActivity;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class ScheduleActivity extends BaseActionBarActivity implements View.OnClickListener {


    @BindView(R.id.ivBack)
    ImageView ivBack;
    @BindView(R.id.llLeft)
    LinearLayout llLeft;
    @BindView(R.id.tvTitle)
    TextView tvTitle;
    @BindView(R.id.tvArrowDown)
    ImageView tvArrowDown;
    @BindView(R.id.llCenter)
    LinearLayout llCenter;
    @BindView(R.id.tvUpload)
    TextView tvUpload;
    @BindView(R.id.llRight)
    LinearLayout llRight;
    @BindView(R.id.tvToolBar)
    RelativeLayout tvToolBar;
    @BindView(R.id.schedule_btn_all)
    RadioButton scheduleBtnAll;
    @BindView(R.id.schedule_btn_live)
    RadioButton scheduleBtnLive;
    @BindView(R.id.schedule_btn_offline)
    RadioButton scheduleBtnOffline;
    @BindView(R.id.lldown)
    RelativeLayout lldown;
    @BindView(R.id.updateTime)
    TextView updateTime;

    private InspectionPopup inspectionPopup;
    private FragmentManager mFragmentManager;
    private Fragment sf, sOnlinefra, sOfflineFra;

    public static String scheduleDateRange = "Today's Schedule";
    private FragmentTransaction transaction;
    private int typeId;
    private static Fragment mCurrentFragmen = null;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);
        ButterKnife.bind(this);
        mFragmentManager = getFragmentManager();
        // Set a toolbar to replace the action bar.
//        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//
//        toolbar.setNavigationIcon(R.drawable.ic_back);
//        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                finish();
//            }
//        });
//        getSupportActionBar().setDisplayShowTitleEnabled(false);


        //Modify the Radio button to 3 : All , live , offline
        final RadioButton btnAll = (RadioButton) findViewById(R.id.schedule_btn_all);
        final RadioButton btnLive = (RadioButton) findViewById(R.id.schedule_btn_live);
        final RadioButton btnOffline = (RadioButton) findViewById(R.id.schedule_btn_offline);
        sf = new ScheduleFragment();
        sOfflineFra = new ScheduleOfflineFragment();
        sOnlinefra = new ScheduleOnlineFragment();


        mCurrentFragmen = sf;

        transaction = mFragmentManager.beginTransaction();
        transaction.add(R.id.details_container, mCurrentFragmen, "All");
        transaction.commitAllowingStateLoss();
        btnAll.setChecked(true);

        btnAll.setOnCheckedChangeListener(onSortCheckedListener);
        btnLive.setOnCheckedChangeListener(onSortCheckedListener);
        btnOffline.setOnCheckedChangeListener(onSortCheckedListener);
        tvUpload.setOnClickListener(uploadAllClickListner);
        ScheduleFragment.updateTime = updateTime;

    }

    private View.OnClickListener uploadAllClickListner = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            List<Appointments> appointmentsList = Appointments.queryScheduledAppointments();

            JobManager jobManager = VSPApplication.getInstance().getJobManager();

            for (int i = 0; i < appointmentsList.size(); i++) {
                jobManager.addJobInBackground(new ScheduleInspectionJob(appointmentsList.get(i).id));
            }

        }
    };

    private CompoundButton.OnCheckedChangeListener onSortCheckedListener = new CompoundButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {

            switch (compoundButton.getId()) {
                case R.id.schedule_btn_all:
                    if (isChecked) {
                        //BusProvider.getBus().post(new ScheduleSortEvent(ScheduleSort.DAY.ordinal()));
                        typeId = R.id.schedule_btn_all;
                        switchFragment(sf, "All");

                    }
                    break;
                case R.id.schedule_btn_live:
                    if (isChecked) {
                        // BusProvider.getBus().post(new ScheduleSortEvent(ScheduleSort.WEEK.ordinal()));
//                        BusProvider.getBus().post(new ScheduleOnlineAndOfflineInspectionEvent(ScheduleType.LIVE.ordinal()));
                        typeId = R.id.schedule_btn_live;
                        switchFragment(sOnlinefra, "Online");
                    }
                    break;
                case R.id.schedule_btn_offline:
                    if (isChecked) {
//                        BusProvider.getBus().post(new ScheduleOnlineAndOfflineInspectionEvent(ScheduleType.OFFLINE.ordinal()));
                        typeId = R.id.schedule_btn_offline;
                        switchFragment(sOfflineFra, "Offline");
                    }
                    break;
                default:

                    break;
            }
        }
    };

    @OnClick({R.id.ivBack, R.id.llLeft, R.id.llCenter})
    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.ivBack:
            case R.id.llLeft:
                finish();
                scheduleDateRange = "Today's Schedule";
                break;

            case R.id.llCenter:
                inspectionPopup = new InspectionPopup(ScheduleActivity.this,
                        new InspectionPopup.SelectCallback() {
                            @Override
                            public void selectCallback(String selectScheduleDateRange) {
                                scheduleDateRange = selectScheduleDateRange;

                                tvTitle.setText(selectScheduleDateRange);

//                                sOfflineFra.setArguments(bundle);
//                                sOnlinefra.setArguments(bundle);
                                //scheduleDateRange = selectScheduleDateRange;
//                                BusProvider.getBus().post(new ScheduleRangeOnChangeEvent(selectScheduleDateRange));
                                switch (typeId) {
                                    case R.id.schedule_btn_all:
                                        BusProvider.getBus().post(new ScheduleRangeOnChangeEvent(selectScheduleDateRange));
                                        break;
                                    case R.id.schedule_btn_live:
                                        BusProvider.getBus().post(new ScheduleRangeOnChangeOnlineEvent(selectScheduleDateRange));
                                        break;
                                    case R.id.schedule_btn_offline:
                                        BusProvider.getBus().post(new ScheduleRangeOnChangeOfflineEvent(selectScheduleDateRange));
                                        break;
                                }

                            }
                        }
                );
                inspectionPopup.showAsDropDown(tvToolBar);
                inspectionPopup.onScheduleRangeChange(ScheduleActivity.this, scheduleDateRange);
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish();
            scheduleDateRange = "Today's Schedule";
        }
        return super.onKeyDown(keyCode, event);
    }


    public class ScheduleRangeOnChangeEvent {
        public String selectScheduleDateRange;

        public ScheduleRangeOnChangeEvent(String selectScheduleDateRange) {
            this.selectScheduleDateRange = selectScheduleDateRange;
        }

    }
    public class ScheduleRangeOnChangeOnlineEvent {
        public String selectScheduleDateRange;

        public ScheduleRangeOnChangeOnlineEvent(String selectScheduleDateRange) {
            this.selectScheduleDateRange = selectScheduleDateRange;
        }

    }
    public class ScheduleRangeOnChangeOfflineEvent {
        public String selectScheduleDateRange;

        public ScheduleRangeOnChangeOfflineEvent(String selectScheduleDateRange) {
            this.selectScheduleDateRange = selectScheduleDateRange;
        }

    }

    public class ScheduleSortEvent {
        public int sortType;
        public int scheduleType;

        public ScheduleSortEvent(int sortType, int scheduleType) {
            this.sortType = sortType;

        }
    }

    public static String getScheduleDateRange(){

        return scheduleDateRange;
    }

    public void switchFragment(Fragment fragment, String tag) {
        if (mCurrentFragmen != fragment) {
            FragmentTransaction transaction = mFragmentManager.beginTransaction();
            if (!fragment.isAdded()) {
                // 没有添加过:
                // 隐藏当前的，添加新的，显示新的
                transaction.hide(mCurrentFragmen).add(R.id.details_container, fragment, tag).show(fragment);
            } else {
                // 隐藏当前的，显示新的
                transaction.hide(mCurrentFragmen).show(fragment);
            }
            mCurrentFragmen = fragment;
            transaction.commitAllowingStateLoss();

        }
    }
}
