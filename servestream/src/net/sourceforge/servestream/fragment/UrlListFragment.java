/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this.getActivity() file except in compliance with the License.
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

package net.sourceforge.servestream.fragment;

import java.util.ArrayList;
import java.util.List;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.app.SherlockListFragment;

import net.sourceforge.servestream.transport.AbsTransport;
import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.utils.LoadingDialog;
import net.sourceforge.servestream.utils.LoadingDialog.LoadingDialogListener;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.RateDialog;

import net.sourceforge.servestream.R;

import net.sourceforge.servestream.adapter.UrlListAdapter;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.utils.PreferenceConstants;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import net.sourceforge.servestream.utils.DetermineActionTask;

public class UrlListFragment extends SherlockListFragment implements
		DetermineActionTask.MusicRetrieverPreparedListener,
		LoadingDialogListener {

	public final static String TAG = UrlListFragment.class.getName();

	public static final String UPDATE_LIST = "net.sourceforge.servestream.updatelist";

	private final static String LOADING_DIALOG = "loading_dialog";
	private final static String RATE_DIALOG = "rate_dialog";

	public static final String ARG_TARGET_URI = "target_uri";

	private StreamDatabase mStreamdb = null;

	private SharedPreferences mPreferences = null;
	private DetermineActionTask mDetermineActionTask;

	private UrlListAdapter mAdapter;

	private BrowseIntentListener mListener;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			mListener = (BrowseIntentListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement BrowseIntentListener");
		}

		// connect with streams database and populate list
		mStreamdb = new StreamDatabase(this.getActivity());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Tell the framework to try to keep this fragment around
		// during a configuration change.
		setRetainInstance(true);
		setHasOptionsMenu(true);
	}

	public void refresh(Bundle args) {
		// If the intent is a request to create a shortcut, we'll do that and
		// exit
		String targetUri = args.getString(ARG_TARGET_URI);

		processUri(targetUri);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View result = inflater.inflate(R.layout.fragment_uri_list, container,
				false);
		ListView list = (ListView) result.findViewById(android.R.id.list);
		list.setEmptyView(result.findViewById(android.R.id.empty));

		return result;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mPreferences = PreferenceManager.getDefaultSharedPreferences(this
				.getActivity());

		String targetUri = getArguments().getString(ARG_TARGET_URI);

		// see if the user wants to rate the application after 5 uses
		if (targetUri == null) {
			int rateApplicationFlag = mPreferences.getInt(
					PreferenceConstants.RATE_APPLICATION_FLAG, 0);
			if (rateApplicationFlag != -1) {
				rateApplicationFlag++;
				Editor ed = mPreferences.edit();
				ed.putInt(PreferenceConstants.RATE_APPLICATION_FLAG,
						rateApplicationFlag);
				ed.commit();
				if (rateApplicationFlag == 10) {
					showDialog(RATE_DIALOG);
				}
			}
		}

		ListView list = getListView();
		list.setOnItemClickListener(new OnItemClickListener() {
			public synchronized void onItemClick(AdapterView<?> parent,
					View view, int position, long id) {

				UriBean uriBean = (UriBean) parent.getAdapter().getItem(
						position);
				processUri(uriBean.getUri().toString());
			}
		});

		mAdapter = new UrlListAdapter(this.getActivity(),
				new ArrayList<UriBean>());
		setListAdapter(mAdapter);

		processUri(targetUri);
	}

	@Override
	public void onStart() {
		super.onStart();

		IntentFilter f = new IntentFilter();
		f.addAction(UPDATE_LIST);
		getActivity().registerReceiver(mUpdateListListener, f);
	}

	@Override
	public void onResume() {
		super.onResume();

		updateList();
	}

	@Override
	public void onStop() {
		super.onStop();

		getActivity().unregisterReceiver(mUpdateListListener);
	}

	@Override
	public void onDetach() {
		super.onDetach();

		mStreamdb.close();
	}

	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (requestCode == 0) {
			if (resultCode == SherlockFragmentActivity.RESULT_OK) {
				String contents = intent.getStringExtra("SCAN_RESULT");
				String format = intent.getStringExtra("SCAN_RESULT_FORMAT");
				// Handle successful scan
				Log.v(TAG, contents.toString());
				Log.v(TAG, format.toString());
			} else if (resultCode == SherlockFragmentActivity.RESULT_CANCELED) {
				// Handle cancel
			}
		}
	}

	private boolean processUri(String input) {
		Uri uri = TransportFactory.getUri(input);

		if (uri == null) {
			return false;
		}

		UriBean uriBean = TransportFactory.findUri(mStreamdb, uri);
		if (uriBean == null) {
			uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(
					uri);

			AbsTransport transport = TransportFactory.getTransport(uriBean
					.getProtocol());
			transport.setUri(uriBean);
			if (mPreferences.getBoolean(PreferenceConstants.AUTOSAVE, true)
					&& transport.shouldSave()) {
				mStreamdb.saveUri(uriBean);
				updateList();
			}
		}

		showDialog(LOADING_DIALOG);
		mDetermineActionTask = new DetermineActionTask(this.getActivity(),
				uriBean, this);
		mDetermineActionTask.execute();

		return true;
	}

	public void updateList() {
		mAdapter.clear();

		List<UriBean> uris = mStreamdb.getUris();
		for (int i = 0; i < uris.size(); i++) {
			mAdapter.add(uris.get(i));
		}

		mAdapter.notifyDataSetChanged();
	}

	private void showUrlNotOpenedToast() {
		Toast.makeText(this.getActivity(), R.string.url_not_opened_message,
				Toast.LENGTH_SHORT).show();
	}

	public void onMusicRetrieverPrepared(String action, UriBean uri, long[] list) {
		dismissDialog(LOADING_DIALOG);

		if (action.equals(DetermineActionTask.URL_ACTION_UNDETERMINED)) {
			showUrlNotOpenedToast();
		} else if (action.equals(DetermineActionTask.URL_ACTION_BROWSE)) {
			if (mPreferences.getBoolean(PreferenceConstants.AUTOSAVE, true)) {
				mStreamdb.touchUri(uri);
			}

			mListener.browseToUri(uri.getScrubbedUri());
		} else if (action.equals(DetermineActionTask.URL_ACTION_PLAY)) {
			if (mPreferences.getBoolean(PreferenceConstants.AUTOSAVE, true)) {
				mStreamdb.touchUri(uri);
			}

			MusicUtils.playAll(getActivity(), list, 0);
		}
	}

	private void showDialog(String tag) {
		// DialogFragment.show() will take care of adding the fragment
		// in a transaction. We also want to remove any currently showing
		// dialog, so make our own transaction and take care of that here.
		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		Fragment prev = getChildFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			ft.remove(prev);
		}

		DialogFragment newFragment = null;

		// Create and show the dialog.
		if (tag.equals(LOADING_DIALOG)) {
			newFragment = LoadingDialog.newInstance(this,
					getString(R.string.opening_url_message));
		} else if (tag.equals(RATE_DIALOG)) {
			newFragment = RateDialog.newInstance();
		}

		ft.add(0, newFragment, tag);
		ft.commitAllowingStateLoss();
	}

	private void dismissDialog(String tag) {
		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		DialogFragment prev = (DialogFragment) getChildFragmentManager()
				.findFragmentByTag(tag);
		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}
		ft.commitAllowingStateLoss();
	}

	@Override
	public void onLoadingDialogCancelled(DialogFragment dialog) {
		if (mDetermineActionTask != null) {
			mDetermineActionTask.cancel(true);
			mDetermineActionTask = null;
		}
	}

	private BroadcastReceiver mUpdateListListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateList();
		}
	};

	// Container Activity must implement this interface
	public interface BrowseIntentListener {
		public void browseToUri(Uri uri);
	}
}
