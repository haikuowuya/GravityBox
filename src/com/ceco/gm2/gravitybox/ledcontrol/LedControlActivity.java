/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.gm2.gravitybox.ledcontrol;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ceco.gm2.gravitybox.GravityBoxSettings;
import com.ceco.gm2.gravitybox.R;
import com.ceco.gm2.gravitybox.ledcontrol.LedListAdapter.ListItemActionHandler;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

public class LedControlActivity extends ListActivity implements ListItemActionHandler, OnItemClickListener {

    private static final int REQ_SETTINGS = 1;
    private static final int REQ_SETTINGS_DEFAULT = 2;
    public static final String EXTRA_UUID_REGISTERED = "uuidRegistered";
    public static final String EXTRA_TRIAL_COUNTDOWN = "uncTrialCountdown";

    private ListView mList;
    private AsyncTask<Void, Void, ArrayList<LedListItem>> mAsyncTask;
    private ProgressDialog mProgressDialog;
    private LedListItem mCurrentItem;
    private EditText mSearchEditText;
    private boolean mShowActiveOnly;
    private TextView mTrialInfoView;
    private boolean mUuidRegistered;
    private int mTrialCountdown;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        if (file.exists()) {
            setTheme(android.R.style.Theme_Holo);
        }

        super.onCreate(savedInstanceState);

        if (getIntent() == null || !getIntent().hasExtra(EXTRA_UUID_REGISTERED) ||
                !getIntent().hasExtra(EXTRA_TRIAL_COUNTDOWN)) {
            finish();
            return;
        } else {
            mUuidRegistered = getIntent().getBooleanExtra(EXTRA_UUID_REGISTERED, false);
            mTrialCountdown = getIntent().getIntExtra(EXTRA_TRIAL_COUNTDOWN, 0);
            if (!mUuidRegistered && mTrialCountdown == 0) {
                finish();
                return;
            }
        }

        if (savedInstanceState != null) {
            mShowActiveOnly = savedInstanceState.getBoolean("showActiveOnly", false);
        }

        setContentView(R.layout.led_control_activity);

        mList = getListView();
        mList.setOnItemClickListener(this);

        mTrialInfoView = (TextView) findViewById(R.id.trial_info);
        if (!mUuidRegistered) {
            mTrialInfoView.setText(String.format(getString(R.string.trial_info), mTrialCountdown));
            mTrialInfoView.setVisibility(View.VISIBLE);
        }

        mSearchEditText = (EditText) findViewById(R.id.input_search);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mList.getAdapter() != null) {
                    ((LedListAdapter)mList.getAdapter()).getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        setData();
    }

    @Override
    public void onStop() {
        cancelSetData();
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.led_control_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.lc_activity_menu_show_all:
                mShowActiveOnly = false;
                setData();
                return true;
            case R.id.lc_activity_menu_show_active:
                mShowActiveOnly = true;
                setData();
                return true;
            case R.id.lc_activity_menu_default_settings:
                Intent intent = new Intent(this, LedSettingsActivity.class);
                intent.putExtra(LedSettingsActivity.EXTRA_PACKAGE_NAME, "default");
                intent.putExtra(LedSettingsActivity.EXTRA_APP_NAME, 
                        getString(R.string.lc_activity_menu_default_settings));
                startActivityForResult(intent, REQ_SETTINGS_DEFAULT);
                return true;
            case R.id.lc_activity_menu_active_screen:
                startActivity(new Intent(this, ActiveScreenActivity.class));
                return true;
            case R.id.lc_activity_menu_quiet_hours:
                startActivity(new Intent(this, QuietHoursActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setData() {
        mAsyncTask = new AsyncTask<Void,Void,ArrayList<LedListItem>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                showProgressDialog();
            }

            @Override
            protected ArrayList<LedListItem> doInBackground(Void... arg0) {
                ArrayList<LedListItem> itemList = new ArrayList<LedListItem>();

                PackageManager pm = LedControlActivity.this.getPackageManager();
                List<ApplicationInfo> packages = pm.getInstalledApplications(0);
                Collections.sort(packages, new ApplicationInfo.DisplayNameComparator(pm));
                for(ApplicationInfo ai : packages) {
                    if (isCancelled()) break;
                    if (ai.packageName.equals(LedControlActivity.this.getPackageName())) continue;
                    LedListItem item = new LedListItem(LedControlActivity.this, ai);
                    if (mShowActiveOnly && !item.isEnabled()) continue;
                    itemList.add(item);
                }

                return itemList;
            }

            @Override
            protected void onCancelled() {
                dismissProgressDialog();
            }

            @Override
            protected void onPostExecute(ArrayList<LedListItem> result) {
                dismissProgressDialog();
                mSearchEditText.setText("");
                mList.setAdapter(new LedListAdapter(LedControlActivity.this, result, 
                        LedControlActivity.this));
                ((LedListAdapter)mList.getAdapter()).notifyDataSetChanged();
                mSearchEditText.setVisibility(mShowActiveOnly ? View.GONE : View.VISIBLE);
            }
        }.execute();
    }

    private void showProgressDialog() {
        mProgressDialog = new ProgressDialog(LedControlActivity.this);
        mProgressDialog.setMessage(getString(R.string.lc_please_wait));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
    }

    private void cancelSetData() {
        if (mAsyncTask != null && mAsyncTask.getStatus() == AsyncTask.Status.RUNNING) {
            mAsyncTask.cancel(true);
        }
        mAsyncTask = null;
    }

    @Override
    public void onItemCheckedChanged(LedListItem item, boolean checked) {
        item.setEnabled(checked);
        mList.invalidateViews();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mCurrentItem = (LedListItem) mList.getItemAtPosition(position);
        if (mCurrentItem.isEnabled()) {
            Intent intent = new Intent(this, LedSettingsActivity.class);
            intent.putExtra(LedSettingsActivity.EXTRA_PACKAGE_NAME, mCurrentItem.getAppInfo().packageName);
            intent.putExtra(LedSettingsActivity.EXTRA_APP_NAME, mCurrentItem.getAppName());
            this.startActivityForResult(intent, REQ_SETTINGS);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK && mCurrentItem != null) {
            if (mCurrentItem.getAppInfo().packageName.equals(
                    data.getStringExtra(LedSettingsActivity.EXTRA_PACKAGE_NAME))) {
                mCurrentItem.refreshLedSettings();
                mList.invalidateViews();
            }
        }

        if (requestCode == REQ_SETTINGS_DEFAULT && resultCode == RESULT_OK) {
            for (int i = 0; i < mList.getCount(); i++) {
                ((LedListItem)mList.getItemAtPosition(i)).refreshLedSettings();
            }
            mList.invalidateViews();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putBoolean("showActiveOnly", mShowActiveOnly);
    }
}
