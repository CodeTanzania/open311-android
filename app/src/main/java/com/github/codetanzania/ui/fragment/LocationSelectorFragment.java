package com.github.codetanzania.ui.fragment;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.github.codetanzania.util.Util;
import com.tooltip.Tooltip;

import org.osmdroid.api.IMapController;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import tz.co.codetanzania.R;

public class LocationSelectorFragment extends Fragment {

    // LocalLocationLister -- used to receive location updates
    private LocationListener mLocationListener;
    // LocationManager -- used to facilitate the reception of location updates
    private LocationManager mLocationManager;

    // Current location from network|gps
    private Location mCurrentLocation;
    private double mLongitude = -1;
    private double mLatitude  = -1;

    // Map Controller
    private IMapController mMapController;

    // reference to the views
    private MapView mMapView;
    private TextView mLocationTextContent;
    private View mLocationFetchIndicator;
    private FloatingActionButton mGPSSelectionStrategyFab;

    // marker
    private Marker mMarker;

    // Tooltip
    Tooltip mTooltip;

    // Event Listener for the map
    MapEventsReceiver mMapEventsReceiver;

    public static LocationSelectorFragment getNewInstance(Bundle args) {
        LocationSelectorFragment self = new LocationSelectorFragment();
        self.setArguments(args);
        return self;
    }

    /**
     * The interface bridges communication between #LocationSelectorFragment,
     * and the context where it is attached. The context must implement this
     * interface in order to receive location coordinates whenever necessary
     */
    public interface OnSelectLocation {
        /**
         * Interface's only method. The callback is invoked when the current device's
         * location is approximated.
         */
        void selectLocation(double lats, double longs);
    }

    private OnSelectLocation mOnSelectLocation;

    @Override public void onResume() {
        super.onResume();
        // every time the fragment is resumed, we should start receiving location updates
        // from the network or gps
        if (mLocationListener == null) {
            mLocationListener = new LocalLocationListener();
        }

        showInstructionsTooltip();
        turnOnGPSStrategy();
    }

    @Override public void onAttach(Context ctx) {
        super.onAttach(ctx);
        try {
            mOnSelectLocation = (OnSelectLocation) ctx;
        } catch (ClassCastException cce) {
            throw new ClassCastException(ctx.toString() + " must implement LocationSelectorFragment#OnSelectLocation" +
                    " interface");
        }
    }

    @Override
    public void onHiddenChanged(boolean flag) {
        hideInstructionsTooltip();
        super.onHiddenChanged(flag);
    }

    @Override public void onDestroy() {
        // every time the fragment is destroyed, we should stop receiving location updates
        // release resources
        turnOffGPSStrategy();
        super.onDestroy();
    }

    @Override public void onPause() {
        super.onPause();
        hideInstructionsTooltip();
        turnOffGPSStrategy();
    }

    @Override public View onCreateView(
            LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.frag_location_selector, parent, false);

        prepareOSM(rootView);
        handleEvents(rootView);

        return rootView;
    }

    private void handleEvents(View rootView) {
        // the event to execute when user selects next
        rootView.findViewById(R.id.btn_Next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnSelectLocation != null && (mLatitude != -1 && (mLongitude != -1))) {
                    mOnSelectLocation.selectLocation(mLatitude, mLongitude);
                }
            }
        });

        // event to toggle location Selection Strategy
        mGPSSelectionStrategyFab = (FloatingActionButton)
                rootView.findViewById(R.id.fab_GPSDetectionStrategy);

        // toggle location strategy when the fab is selected
        mGPSSelectionStrategyFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                turnOnGPSStrategy();
            }
        });
    }

    private void prepareOSM(View rootView) {
        mMapView = (MapView) rootView.findViewById(R.id.map);
        mLocationTextContent = (TextView) rootView.findViewById(R.id.tv_LocationTextContent);
        mLocationFetchIndicator = rootView.findViewById(R.id.pb_LocationFetchIndicator);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapController = mMapView.getController();
        mMapController.setZoom(20);
        mMapEventsReceiver = new LocalMapEventReceiver();
        MapEventsOverlay mEventsOverlay = new MapEventsOverlay(mMapEventsReceiver);
        mMapView.getOverlays().add(mEventsOverlay);

        // setup the marker
        mMarker = new Marker(mMapView);
        mMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mMarker.setIcon(ContextCompat.getDrawable(getActivity(), R.drawable.ic_location_searching_black_24dp));
        mMapView.getOverlays().add(mMarker);
    }

    private void registerForLocationUpdates(String provider) {

        // unregister any previous listeners
        if (mLocationListener != null && mLocationManager != null) {
            mLocationManager.removeUpdates(mLocationListener);
        } else {
            mLocationListener = new LocalLocationListener();
            // initialize the location manager
            mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        }

        // register for location updates
        if (Util.isGPSOn(getActivity())) {
            // noinspection MissingPermission
            mLocationManager.requestLocationUpdates(provider, 0, 0, mLocationListener);
        } else {
            // ask user to turn gps on for high accuracy
            requestGPSTurnOn();
        }
    }

    private void requestGPSTurnOn() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
        alertBuilder.setMessage(R.string.text_allow_gps_location_access)
            .setNegativeButton(R.string.action_decline_access_location, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getActivity().finish();
                }
            }).setPositiveButton(R.string.action_confirm_access_location ,new DialogInterface.OnClickListener() {
                // -- let user turn on gps. by the time he gets back, we will resume location update
                // through the user of onResume() callback, this time with gps turned on.
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
        }).create().show();
    }

    private void updateMap(final double lats, final double longs, boolean isLongTap) {
        mLongitude = longs;
        mLatitude  = lats;
        GeoPoint point = new GeoPoint(lats, longs);
        try {
            // Play around with the marker.
            mMarker.setPosition(point);

            // if is not the result of long tap then we need to center the map
            // to make sure that, automatic GPS discovery mode does not update
            // the map by placing the marker to the point where it is not visible
            // on the screen
            if (!isLongTap) {
                mMapController.setCenter(point);
            }

            // there is a chance we might try to update while the fragment is detached from the activity
            if (!isDetached()) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                    if (!isDetached()) {
                        if (mLocationTextContent.getVisibility() == View.GONE) {
                            mLocationTextContent.setVisibility(View.VISIBLE);
                            mLocationFetchIndicator.setVisibility(View.GONE);
                        }
                    }
                    }
                }, 10);
            }
        } catch (Exception ignore) {

        }
    }

    private void turnOffGPSStrategy() {
        // turn off automatic location discovery using GPS
        if (mLocationListener != null && mLocationManager != null) {
            mLocationManager.removeUpdates(mLocationListener);
            mLocationListener = null;
            mLocationManager  = null;
        }
    }

    private void turnOnGPSStrategy() {
        // turn on automatic location discovery using GPS if we're not doing so
        if (mLocationManager == null || mLocationListener == null) {
            registerForLocationUpdates(LocationManager.GPS_PROVIDER);
            registerForLocationUpdates(LocationManager.NETWORK_PROVIDER);
        }
    }

    private void showInstructionsTooltip() {
        mTooltip = new Tooltip.Builder(mGPSSelectionStrategyFab)
                .setText(R.string.text_enable_gps_discovery)
                .setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.colorPrimary))
                .setCornerRadius(4.0f)
                .setDismissOnClick(true)
                .show();
    }

    public void hideInstructionsTooltip() {
        if (mTooltip != null && mTooltip.isShowing()) {
            mTooltip.dismiss();
            mTooltip = null;
        }
    }

    private class LocalLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            if (Util.isBetterLocation(location, mCurrentLocation)) {
                mCurrentLocation = location;
                updateMap(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), false);
                // inform users that an app is now using gps to discover location
                Toast.makeText(getActivity(), R.string.text_gps_discovery_on, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    }

    private class LocalMapEventReceiver implements MapEventsReceiver {

        @Override
        public boolean singleTapConfirmedHelper(GeoPoint p) {
            return false;
        }

        @Override
        public boolean longPressHelper(GeoPoint p) {
            turnOffGPSStrategy();
            updateMap(p.getLatitude(), p.getLongitude(), true);
            // for some reasons, map refuses to render immediately when user
            // selects a location
            // we fix this by forcing the map to redo layout rendering
            mMapView.invalidate();
            mMapView.requestLayout();

            // inform user that he/she can enable gps
            Toast.makeText(getActivity(), R.string.text_gps_discovery_off, Toast.LENGTH_SHORT).show();

            return true;
        }
    }
}