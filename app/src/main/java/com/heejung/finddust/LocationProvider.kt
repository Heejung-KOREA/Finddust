package com.heejung.finddust

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

//GPS나 네트워크 위치를 사용하여 위도와 경도를 가져오는 클래스
class LocationProvider(val context: Context) {

    //Location --> 위도, 경도, 고도와 같이 위치에 관련된 정보를 가지고 있는 데이터 클래스
    private var location: Location? = null

    //Location Manager --> 시스템 위치 서비스에 접근을 제공하는 클래스
    private var locationManager: LocationManager? = null

    init {
        //초기화시 위치를 가져옴
        getLocation()
    }

    private fun getLocation(): Location? {
        try {
            //위치 시스템 서비스를 가져옴
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            var gpsLocation: Location? = null
            var networkLocation: Location? = null

            //GPS Provider 와 Network Provider가 활성화 되어있는지 확인
            val isGPSEnabled: Boolean =
                locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled: Boolean =
                locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGPSEnabled && !isNetworkEnabled) {
                //GPS, Network Provider 둘 다 사용 불가능한 상황이면 null을 반환함
                return null
            } else {
                val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                    //ACCESS_COARSE_LOCATION 보다 더 정밀한 위치 정보를 얻기
                )
                val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                    //도시 Block 단위의 정밀도의 위치 정보 얻기
                )
                //만약 위 두 개 권한 없다면 null을 반환함.
                if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED ||
                    hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED
                ) return null

                //네트워크를 통한 위치 파악이 가능한 경우에 위치를 가져옴.
                if (isNetworkEnabled) {
                    networkLocation =
                        locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
                //GPS를 통한 위치 파악이 가능한 경우에 위치를 가져옴.
                if (isGPSEnabled) {
                    gpsLocation =
                        locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }

                if (gpsLocation != null && networkLocation != null) {
                    //만약 GPS와 네트워크를 통한 위치 파악이 모두 가능하다면 정확도가 높은 것으로 선택함
                    if (gpsLocation.accuracy > networkLocation.accuracy) {
                        location = gpsLocation
                        return gpsLocation
                    } else {
                        location = networkLocation
                        return networkLocation
                    }
                } else {
                    //만약 가능한 위치 정보가 한 개만 있는 경우
                    if (gpsLocation != null) {
                        location = gpsLocation
                    }

                    if (networkLocation != null) {
                        location = networkLocation
                    }
                }

            }
        } catch (e: Exception) {
            e.printStackTrace()     //에러 출력
        }
        return location
    }

    //위도 정보를 가져오는 함수
    fun getLocationLatitude(): Double {
        return location?.latitude ?: 0.0    //null이면 0.0 반환
    }

    //경도 정보르 가져오는 함수
    fun getLocationLongitude(): Double {
        return location?.longitude ?: 0.0   //null이면 0.0 반환
    }

}