package net.sourceforge.servestream.activity;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.fragment.UrlListFragment;
import net.sourceforge.servestream.fragment.UrlListFragment.BrowseIntentListener;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

import com.actionbarsherlock.app.SherlockFragmentActivity;

public class HomeActivity extends SherlockFragmentActivity implements
		ServiceConnection, BrowseIntentListener {

	private static UrlListFragment mUrlListFragment;
	private ServiceToken mToken;

	@Override
	protected void onCreate(Bundle arg0) {
		super.onCreate(arg0);
		setContentView(R.layout.home);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		Bundle args = new Bundle();
		args.putString(UrlListFragment.ARG_TARGET_URI, getUri());

		mUrlListFragment = (UrlListFragment) Fragment.instantiate(this,
				UrlListFragment.class.getName(), args);

		FragmentTransaction manager = getSupportFragmentManager()
				.beginTransaction();
		manager.add(R.id.details, mUrlListFragment);
		manager.commit();

		mToken = MusicUtils.bindToService(this, this);

	}

	private String getUri() {
		String intentUri = null;
		String contentType = null;

		Intent intent = getIntent();

		// check to see if we were called from a home screen shortcut
		if ((contentType = intent.getType()) != null) {
			if (contentType.contains("net.sourceforge.servestream/")) {
				intentUri = intent.getType().toString()
						.replace("net.sourceforge.servestream/", "");
				setIntent(null);
				return intentUri;
			}
		}

		// check to see if we were called by clicking on a URL
		if (intent.getData() != null) {
			intentUri = intent.getData().toString();
		}

		// check to see if the application was opened from a share intent
		if (intent.getExtras() != null
				&& intent.getExtras().getCharSequence(Intent.EXTRA_TEXT) != null) {
			intentUri = intent.getExtras().getCharSequence(Intent.EXTRA_TEXT)
					.toString();
		}

		setIntent(null);

		return intentUri;
	}

	@Override
	public void onResume() {
		super.onResume();

		IntentFilter f = new IntentFilter();
		f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
		f.addAction(MediaPlaybackService.META_CHANGED);
		f.addAction(MediaPlaybackService.QUEUE_CHANGED);
		registerReceiver(mTrackListListener, f);
	}

	@Override
	public void onPause() {
		super.onPause();

		unregisterReceiver(mTrackListListener);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		MusicUtils.unbindFromService(mToken);
	}

	private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			MusicUtils.updateNowPlaying(HomeActivity.this);
		}
	};

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		MusicUtils.updateNowPlaying(this);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		finish();
	}

	@Override
	public void browseToUri(Uri uri) {

	}

}
