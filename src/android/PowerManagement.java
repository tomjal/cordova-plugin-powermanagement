/*
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

package org.apache.cordova.powermanagement;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.os.PowerManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.util.Log;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;

/**
 * Plugin class which does the actual handling
 */
public class PowerManagement extends CordovaPlugin {
	// As we only allow one wake-lock, we keep a reference to it here
	private PowerManager.WakeLock wakeLock = null;
	private PowerManager powerManager = null;
	private boolean releaseOnPause = true;

	private WifiManager wifiManager = null;
	private WifiLock wifiLock = null;

	/**
	 * Fetch a reference to the power-service when the plugin is initialized
	 */
	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);

		this.powerManager = (PowerManager) cordova.getActivity().getSystemService(Context.POWER_SERVICE);

		Context context = cordova.getActivity().getApplicationContext();
		this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
	}

	@Override
	public boolean execute(String action, JSONArray args,
			CallbackContext callbackContext) throws JSONException {

		PluginResult result = null;
		Log.d("PowerManagementPlugin", "Plugin execute called - " + this.toString() );
		Log.d("PowerManagementPlugin", "Action is " + action );

		try {
			if( action.equals("acquire") ) {
				if( args.length() > 0 && args.getBoolean(0) ) {
					Log.d("PowerManagementPlugin", "Partial wake lock" );
					result = this.acquire( PowerManager.PARTIAL_WAKE_LOCK );
				}
				else {
					result = this.acquire( PowerManager.FULL_WAKE_LOCK );
				}
			} else if( action.equals("release") ) {
				result = this.release();
			} else if( action.equals("setReleaseOnPause") ) {
				try {
					this.releaseOnPause = args.getBoolean(0);
					result = new PluginResult(PluginResult.Status.OK);
				} catch (Exception e) {
					result = new PluginResult(PluginResult.Status.ERROR, "Could not set releaseOnPause");
				}
			}
		}
		catch( JSONException e ) {
			result = new PluginResult(Status.JSON_EXCEPTION, e.getMessage());
		}

		callbackContext.sendPluginResult(result);
		return true;
	}

	/**
	 * Acquire a wake-lock
	 * @param p_flags Type of wake-lock to acquire
	 * @return PluginResult containing the status of the acquire process
	 */
	private PluginResult acquire( int p_flags ) {
		PluginResult result = null;

		if (this.wakeLock == null && this.wifiLock == null) {
			this.wakeLock = this.powerManager.newWakeLock(p_flags, "PowerManagementPlugin");
			this.wifiLock = this.wifiManager.createWifiLock("wifiLock");
			try {
				this.wakeLock.acquire();
				this.wifiLock.acquire();
				result = new PluginResult(PluginResult.Status.OK);
			}
			catch( Exception e ) {
				this.wakeLock = null;
				this.wifiLock = null;
				result = new PluginResult(PluginResult.Status.ERROR,"Can't acquire wake or wifi lock - check your permissions!");
			}
		}
		else {
			result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION,"WakeLock or wifi lock already active - release first");
		}

		return result;
	}

	/**
	 * Release an active wake-lock
	 * @return PluginResult containing the status of the release process
	 */
	private PluginResult release() {
		PluginResult result = null;

		if( this.wakeLock != null || this.wifiLock != null) {
			try {
				this.wakeLock.release();
				this.wifiLock.release();
				result = new PluginResult(PluginResult.Status.OK, "OK");
			}
			catch (Exception e) {
				result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, "WakeLock or wifi lock already released");
			}

			this.wakeLock = null;
			this.wifiLock = null;
		}
		else {
			result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, "No WakeLock or wifi lock active - acquire first");
		}

		return result;
	}

	/**
	 * Make sure any wakelock is released if the app goes into pause
	 */
	@Override
	public void onPause(boolean multitasking) {
		if( this.releaseOnPause && this.wakeLock != null ) {
			this.wakeLock.release();
		}

		if( this.releaseOnPause && this.wifiLock != null ) {
			this.wifiLock.release();
		}

		super.onPause(multitasking);
	}

	/**
	 * Make sure any wakelock is acquired again once we resume
	 */
	@Override
	public void onResume(boolean multitasking) {
		if( this.releaseOnPause && this.wakeLock != null ) {
			this.wakeLock.acquire();
		}

		if( this.releaseOnPause && this.wifiLock != null ) {
			this.wifiLock.acquire();
		}

		super.onResume(multitasking);
	}
}
