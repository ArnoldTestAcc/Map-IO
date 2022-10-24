package com.opsc.map_io;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;
import com.google.android.libraries.places.api.model.Place;
import com.opsc.map_io.databinding.InfoWindowBinding;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class InfoAdapter implements GoogleMap.InfoWindowAdapter {
    private final InfoWindowBinding binding;
    private final String distanceToDestination;
    private final String estimatedTimeToDestination;
    private List<Address> addresses;

    public InfoAdapter(
            Context context,
            String distanceToDestination,
            String estimatedTimeToDestination,
            Place targetLocation) {
        this.distanceToDestination = distanceToDestination;
        this.estimatedTimeToDestination = estimatedTimeToDestination;

        //Geocoder and addresses initialization
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            addresses = geocoder.getFromLocation(
                    Objects.requireNonNull(targetLocation.getLatLng()).latitude,
                    Objects.requireNonNull(targetLocation.getLatLng()).longitude,
                    1);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //binding
        binding = InfoWindowBinding.inflate(LayoutInflater.from(context), null, false);
    }

    @Nullable
    @Override
    public View getInfoContents(@NonNull Marker marker) {
        binding.locationNameText.setText(marker.getTitle());
        binding.locationDistanceText.setText(distanceToDestination);
        binding.locationEstTimeText.setText(estimatedTimeToDestination);

        if (addresses != null && addresses.size() > 0) {
            String address = addresses.get(0).getAddressLine(0);
            String city = addresses.get(0).getLocality();
            String province = addresses.get(0).getAdminArea();
            String country = addresses.get(0).getCountryName();
            String postalCode = addresses.get(0).getPostalCode();

            binding.addressText.setText(address);
            binding.cityText.setText(city);
            binding.provinceText.setText(province);
            binding.countryText.setText(country);
            binding.postalCodeText.setText(postalCode);
        }

        //CONVERSION TO MILES

        return binding.getRoot();
    }

    @Nullable
    @Override
    public View getInfoWindow(@NonNull Marker marker) {
        binding.locationNameText.setText(marker.getTitle());
        binding.locationDistanceText.setText(distanceToDestination);
        binding.locationEstTimeText.setText(estimatedTimeToDestination);

        if (addresses != null && addresses.size() > 0) {
            String address = addresses.get(0).getAddressLine(0);
            String city = addresses.get(0).getLocality();
            String province = addresses.get(0).getAdminArea();
            String country = addresses.get(0).getCountryName();
            String postalCode = addresses.get(0).getPostalCode();

            binding.addressText.setText(address);
            binding.cityText.setText(city);
            binding.provinceText.setText(province);
            binding.countryText.setText(country);
            binding.postalCodeText.setText(postalCode);
        }

        //CONVERSION TO MILES

        return binding.getRoot();
    }
}
