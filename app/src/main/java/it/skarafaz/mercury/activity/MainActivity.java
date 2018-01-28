/*
 * Mercury-SSH
 * Copyright (C) 2017 Skarafaz
 *
 * This file is part of Mercury-SSH.
 *
 * Mercury-SSH is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Mercury-SSH is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mercury-SSH.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.skarafaz.mercury.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import it.skarafaz.mercury.MercuryApplication;
import it.skarafaz.mercury.R;
import it.skarafaz.mercury.adapter.ServerPagerAdapter;
import it.skarafaz.mercury.fragment.ProgressDialogFragment;
import it.skarafaz.mercury.manager.ConfigManager;
import it.skarafaz.mercury.manager.ExportPublicKeyStatus;
import it.skarafaz.mercury.manager.LoadConfigFilesStatus;
import it.skarafaz.mercury.manager.SshManager;
import it.skarafaz.mercury.ssh.SshCommandPubKey;
import org.greenrobot.eventbus.EventBus;

public class MainActivity extends MercuryActivity {
    private static final int PRC_WRITE_EXT_STORAGE_LOAD_CONFIG_FILES = 101;
    private static final int PRC_WRITE_EXT_STORAGE_EXPORT_PUBLIC_KEY = 102;
    private static final int RC_START_APP_INFO = 201;

    @BindView(R.id.progress)
    protected ProgressBar progressBar;
    @BindView(R.id.empty)
    protected LinearLayout emptyLayout;
    @BindView(R.id.message)
    protected TextView emptyMessage;
    @BindView(R.id.settings)
    protected TextView settingsButton;
    @BindView(R.id.pager)
    protected ViewPager serverPager;

    private ServerPagerAdapter serverPagerAdapter;
    private MainActivityEventSubscriber mainActivityEventSubscriber;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        serverPagerAdapter = new ServerPagerAdapter(getSupportFragmentManager());
        serverPager.setAdapter(serverPagerAdapter);

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAppInfo();
            }
        });

        mainActivityEventSubscriber = new MainActivityEventSubscriber(this);

        loadConfigFiles();
    }

    @Override
    protected void onStart() {
        super.onStart();

        EventBus.getDefault().register(mainActivityEventSubscriber);
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(mainActivityEventSubscriber);

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_reload:
                loadConfigFiles();
                return true;
            case R.id.action_export_public_key:
                exportPublicKey();
                return true;
            case R.id.action_send_public_key:
                new SshCommandPubKey().start();
                return true;
            case R.id.action_log:
                startActivity(new Intent(this, LogActivity.class));
                return true;
            case R.id.action_help:
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case PRC_WRITE_EXT_STORAGE_LOAD_CONFIG_FILES:
                    loadConfigFiles();
                    break;
                case PRC_WRITE_EXT_STORAGE_EXPORT_PUBLIC_KEY:
                    exportPublicKey();
                    break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RC_START_APP_INFO:
                loadConfigFiles();
                break;
        }
    }

    private void loadConfigFiles() {
        if (!busy) {
            new AsyncTask<Void, Void, LoadConfigFilesStatus>() {
                @Override
                protected void onPreExecute() {
                    busy = true;
                    progressBar.setVisibility(View.VISIBLE);
                    emptyLayout.setVisibility(View.INVISIBLE);
                    serverPager.setVisibility(View.INVISIBLE);
                }

                @Override
                protected LoadConfigFilesStatus doInBackground(Void... params) {
                    return ConfigManager.getInstance().loadConfigFiles();
                }

                @Override
                protected void onPostExecute(LoadConfigFilesStatus status) {
                    progressBar.setVisibility(View.INVISIBLE);
                    if (ConfigManager.getInstance().getServers().size() > 0) {
                        serverPagerAdapter.updateServers(ConfigManager.getInstance().getServers());
                        serverPager.setVisibility(View.VISIBLE);
                        if (status == LoadConfigFilesStatus.ERROR) {
                            Toast.makeText(MainActivity.this, getString(status.message()), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        emptyMessage.setText(getString(status.message(), ConfigManager.getInstance().getConfigDir()));
                        emptyLayout.setVisibility(View.VISIBLE);
                        if (status == LoadConfigFilesStatus.PERMISSION) {
                            settingsButton.setVisibility(View.VISIBLE);
                            MercuryApplication.requestPermission(MainActivity.this, PRC_WRITE_EXT_STORAGE_LOAD_CONFIG_FILES, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        } else {
                            settingsButton.setVisibility(View.GONE);
                        }
                    }
                    busy = false;
                }
            }.execute();
        }
    }

    private void exportPublicKey() {
        if (!busy) {
            new AsyncTask<Void, Void, ExportPublicKeyStatus>() {
                @Override
                protected void onPreExecute() {
                    showProgressDialog(getString(R.string.exporting_public_key));
                }

                @Override
                protected ExportPublicKeyStatus doInBackground(Void... params) {
                    return SshManager.getInstance().exportPublicKey();
                }

                @Override
                protected void onPostExecute(ExportPublicKeyStatus status) {
                    dismissProgressDialog();

                    boolean toast = true;
                    if (status == ExportPublicKeyStatus.PERMISSION) {
                        toast = !MercuryApplication.requestPermission(MainActivity.this, PRC_WRITE_EXT_STORAGE_EXPORT_PUBLIC_KEY, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    }
                    if (toast) {
                        Toast.makeText(MainActivity.this, getString(status.message(), SshManager.getInstance().getPublicKeyExportedFile()), Toast.LENGTH_LONG).show();
                    }
                }
            }.execute();
        }
    }

    private void startAppInfo() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivityForResult(intent, RC_START_APP_INFO);
    }

    protected void showProgressDialog(String content) {
        FragmentTransaction ft = this.getSupportFragmentManager().beginTransaction();
        ft.add(ProgressDialogFragment.newInstance(content), ProgressDialogFragment.TAG);
        ft.commitAllowingStateLoss();
    }

    protected void dismissProgressDialog() {
        FragmentManager fm = this.getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        Fragment frag = fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (frag != null) {
            ft.remove(frag);
        }
        ft.commitAllowingStateLoss();
    }
}
