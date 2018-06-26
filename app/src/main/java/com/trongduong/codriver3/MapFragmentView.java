package com.trongduong.codriver3;

/**
 * Created by trongduong on 6/26/2018.
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapFragment;
import com.here.android.mpa.mapping.MapState;
import com.here.android.positioning.StatusListener;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * This class encapsulates the properties and functionality of the Map view.
 */

public class MapFragmentView extends Activity implements PositioningManager.OnPositionChangedListener, Map.OnTransformListener {
    private Activity mActivity;
    private Map mMap;
    private MapFragment mMapFragment;

    // positioning manager instance
    private PositioningManager mPositioningManager;

    // HERE location data source instance
    private LocationDataSourceHERE mHereLocation;

    // flag that indicates whether maps is being transformed
    private boolean mTransforming;

    // callback that is called when transforming ends
    private Runnable mPendingUpdate;

    // text view instance for showing location information
    private TextView mLocationInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocationInfo = findViewById(R.id.textViewLocationInfo);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPositioningManager != null) {
            mPositioningManager.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPositioningManager != null) {
            mPositioningManager.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mHereLocation == null) {
            return false;
        }
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_set_location_method:
                setLocationMethod();
                return true;
            case R.id.action_set_indoor_mode:
                setIndoorMode();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPositionUpdated(final PositioningManager.LocationMethod locationMethod, final GeoPosition geoPosition, final boolean mapMatched) {
        final GeoCoordinate coordinate = geoPosition.getCoordinate();
        if (mTransforming) {
            mPendingUpdate = new Runnable() {
                @Override
                public void run() {
                    onPositionUpdated(locationMethod, geoPosition, mapMatched);
                }
            };
        } else {
            mMap.setCenter(coordinate, Map.Animation.BOW);
            updateLocationInfo(locationMethod, geoPosition);
        }
    }

    @Override
    public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod, PositioningManager.LocationStatus locationStatus) {
        //ignore
    }

    @Override
    public void onMapTransformStart() {
        mTransforming = true;
    }

    @Override
    public void onMapTransformEnd(MapState mapState) {
        mTransforming = false;
        if (mPendingUpdate != null) {
            mPendingUpdate.run();
            mPendingUpdate = null;
        }
    }


    public MapFragmentView(Activity activity) {
        mActivity = activity;
        initMapFragment_Positioning();
    }

    private void initMapFragment_Positioning() {
        /* Locate the mapFragment UI element */
        //TextView mLocationInfo = new TextView();

        mMapFragment = (MapFragment) mActivity.getFragmentManager()
                .findFragmentById(R.id.mapfragment);
        mMapFragment.setRetainInstance(false);

        // Set path of isolated disk cache
        String diskCacheRoot = Environment.getExternalStorageDirectory().getPath()
                + File.separator + ".isolated-here-maps";
        // Retrieve intent name from manifest
        String intentName = "";
        try {
            ApplicationInfo ai = mActivity.getPackageManager().getApplicationInfo(mActivity.getPackageName(),
                    PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            intentName = bundle.getString("INTENT_NAME"); ///////////////////////////////
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(this.getClass().toString(), "Failed to find intent name, NameNotFound: " + e.getMessage());
        }

        boolean success = com.here.android.mpa.common.MapSettings.setIsolatedDiskCacheRootPath(diskCacheRoot,
                intentName);
        if (!success) {
            // Setting the isolated disk cache was not successful, please check if the path is valid and
            // ensure that it does not match the default location
            // (getExternalStorageDirectory()/.here-maps).
            // Also, ensure the provided intent name does not match the default intent name.
        } else {
            if (mMapFragment != null) {
            /* Initialize the MapFragment, results will be given via the called back. */
                mMapFragment.init(new OnEngineInitListener() {
                    @Override
                    public void onEngineInitializationCompleted(OnEngineInitListener.Error error) {

                        if (error == Error.NONE) {
                        /*
                         * If no error returned from map fragment initialization, the map will be
                         * rendered on screen at this moment.Further actions on map can be provided
                         * by calling Map APIs.
                         */
                            mMap = mMapFragment.getMap();

                            /*
                             * Map center can be set to a desired location at this point.
                             * It also can be set to the current location ,which needs to be delivered by the PositioningManager.
                             * Please refer to the user guide for how to get the real-time location.
                             */

                            mMap.setCenter(new GeoCoordinate(49.258576, -123.008268), Map.Animation.NONE);
                            mMap.setZoomLevel(mMap.getMaxZoomLevel() - 1);
                            mMap.addTransformListener(MapFragmentView.this);
                            mPositioningManager = PositioningManager.getInstance();
                            mHereLocation = LocationDataSourceHERE.getInstance(new StatusListener() {
                                @Override
                                public void onOfflineModeChanged(boolean b) {
                                    // called when offline mode changes
                                }

                                @Override
                                public void onAirplaneModeEnabled() {
                                    // called when airphane mode is enabled
                                }

                                @Override
                                public void onWifiScansDisabled() {
                                    // called when Wi-Fi scans are disabled
                                }

                                @Override
                                public void onBluetoothDisabled() {
                                    // called when Bluetooth is disabled
                                }

                                @Override
                                public void onCellDisabled() {
                                    // called when Cell radios are switch off
                                }

                                @Override
                                public void onGnssLocationDisabled() {
                                    // called when GPS positioning is disabled
                                }

                                @Override
                                public void onNetworkLocationDisabled() {
                                    // called when network positioning is disabled
                                }

                                @Override
                                public void onServiceError(ServiceError serviceError) {
                                    // called on HERE service error
                                }

                                @Override
                                public void onPositioningError(PositioningError positioningError) {
                                    // called when positioning fails
                                }
                            });
                            if (mHereLocation == null) {
                                Toast.makeText(MapFragmentView.this, "LocationDataSourceHERE.getInstance(): failed, exiting", Toast.LENGTH_LONG).show();
                                finish();
                            }
                            mPositioningManager.setDataSource(mHereLocation);
                            mPositioningManager.addListener(new WeakReference<PositioningManager.OnPositionChangedListener>(
                                    MapFragmentView.this));
                            // start position updates, accepting GPS, network or indoor positions
                            if (mPositioningManager.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR)) {
                                mMapFragment.getPositionIndicator().setVisible(true);
                            } else {
                                Toast.makeText(MapFragmentView.this, "PositioningManager.start: failed, exiting", Toast.LENGTH_LONG).show();
                                finish();
                            }

                        } else {
                            Toast.makeText(mActivity,
                                    "ERROR: Cannot initialize Map with error " + error,
                                    Toast.LENGTH_LONG).show();
                            finish();
                            //Log.e(TAG, "ERROR: Cannot initialize Map with error");
                        }
                    }
                });
            }
        }
    }
    /**
     * Update location information.
     * @param geoPosition Latest geo position update.
     */
    private void updateLocationInfo(PositioningManager.LocationMethod locationMethod, GeoPosition geoPosition) {
        if (mLocationInfo == null) {
            return;
        }
        final StringBuffer sb = new StringBuffer();
        final GeoCoordinate coord = geoPosition.getCoordinate();
        sb.append("Type: ").append(String.format(Locale.US, "%s\n", locationMethod.name()));
        sb.append("Coordinate:").append(String.format(Locale.US, "%.6f, %.6f\n", coord.getLatitude(), coord.getLongitude()));
        if (coord.getAltitude() != GeoCoordinate.UNKNOWN_ALTITUDE) {
            sb.append("Altitude:").append(String.format(Locale.US, "%.2fm\n", coord.getAltitude()));
        }
        if (geoPosition.getHeading() != GeoPosition.UNKNOWN) {
            sb.append("Heading:").append(String.format(Locale.US, "%.2f\n", geoPosition.getHeading()));
        }
        if (geoPosition.getSpeed() != GeoPosition.UNKNOWN) {
            sb.append("Speed:").append(String.format(Locale.US, "%.2fm/s\n", geoPosition.getSpeed()));
        }
        if (geoPosition.getBuildingName() != null) {
            sb.append("Building: ").append(geoPosition.getBuildingName());
            if (geoPosition.getBuildingId() != null) {
                sb.append(" (").append(geoPosition.getBuildingId()).append(")\n");
            } else {
                sb.append("\n");
            }
        }
        if (geoPosition.getFloorId() != null) {
            sb.append("Floor: ").append(geoPosition.getFloorId()).append("\n");
        }
        sb.deleteCharAt(sb.length() - 1);
        mLocationInfo.setText(sb.toString());
    }

    /**
     * Called when set location method -menu item is selected.
     */

    private void setLocationMethod() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final String[] names = getResources().getStringArray(R.array.locationMethodNames);
        builder.setTitle(R.string.title_select_location_method)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            final String[] values = getResources().getStringArray(R.array.locationMethodValues);
                            final PositioningManager.LocationMethod method =
                                    PositioningManager.LocationMethod.valueOf(values[which]);
                            setLocationMethod(method);
                        } catch (IllegalArgumentException ex) {
                            Toast.makeText(MapFragmentView.this, "setLocationMethod failed: "
                                    + ex.getMessage(), Toast.LENGTH_LONG).show();
                        } finally {
                            dialog.dismiss();
                        }
                    }
                });
        builder.create().show();
    }

    /**
     * Called when set indoor mode -menu item is selected.
     */
    private void setIndoorMode() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final String[] names = getResources().getStringArray(R.array.indoorPositioningModeNames);
        builder.setTitle(R.string.title_select_indoor_mode)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            final String[] values = getResources().getStringArray(R.array.indoorPositioningModeValues);
                            final LocationDataSourceHERE.IndoorPositioningMode mode =
                                    LocationDataSourceHERE.IndoorPositioningMode.valueOf(values[which]);
                            setIndoorMode(mode);
                        } catch (IllegalArgumentException ex) {
                            Toast.makeText(MapFragmentView.this, "setIndoorMode failed: "
                                    + ex.getMessage(), Toast.LENGTH_LONG).show();
                        } finally {
                            dialog.dismiss();
                        }
                    }
                });
        builder.create().show();
    }

    /**
     * Sets location method for the PositioningManager.
     * @param method New location method.
     */
    private void setLocationMethod(PositioningManager.LocationMethod method) {
        if (!mPositioningManager.start(method)) {
            Toast.makeText(MapFragmentView.this, "PositioningManager.start(" + method + "): failed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Sets indoor positioning method.
     * @param mode New indoor positioning mode.
     */
    private void setIndoorMode(LocationDataSourceHERE.IndoorPositioningMode mode) {
        final LocationDataSourceHERE.IndoorPositioningModeSetResult result = mHereLocation.setIndoorPositioningMode(mode);
        switch (result) {
            case FEATURE_NOT_LICENSED:
                Toast.makeText(MapFragmentView.this, mode + ": is not licensed", Toast.LENGTH_LONG).show();
                break;
            case INTERNAL_ERROR:
                Toast.makeText(MapFragmentView.this, mode + ": internal error", Toast.LENGTH_LONG).show();
                break;
            case MODE_NOT_ALLOWED:
                Toast.makeText(MapFragmentView.this, mode + ": is not allowed", Toast.LENGTH_LONG).show();
                break;
            case PENDING:
            case OK:
            default:
                break;
        }
    }

}