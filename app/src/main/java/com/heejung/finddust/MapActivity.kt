package com.heejung.finddust

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.heejung.finddust.databinding.ActivityMapBinding

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    lateinit var binding: ActivityMapBinding    //뷰 바인딩 설정

    private var mMap: GoogleMap? = null
    var currentLat: Double = 0.0 //MainActivity.kt에서 전달된 위도
    var currentLng: Double = 0.0 //MainActivity.kt에서 전달된 경도

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)    //뷰 바인딩 설정

        //MainActivity.kt 에서 intent로 전달된 값을 가져옴
        currentLat = intent.getDoubleExtra("currentLat", 0.0)
        currentLng = intent.getDoubleExtra("currentLng", 0.0)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)  //getMapAsync는 OnMapReadyCallback 인터페이스를 등록시켜 지도가 준비되면 onMapReady() 함수가 자동으로 실행하게 함.

        binding.btnCheckHere.setOnClickListener {
            mMap?.let {//mMap이 null이 아닌 경우 아래 코드 블록을 실행함
                val intent = Intent()
                //버튼이 눌린 시점의 카메라 포지션을 가져옴(현재 보이는 지도의 중앙 좌푯값)
                intent.putExtra("latitude", it.cameraPosition.target.latitude)
                intent.putExtra("longitude", it.cameraPosition.target.longitude)
                //onActivityResult() 함수 실행
                setResult(Activity.RESULT_OK, intent)
                finish()    //지도 액티비티 종료
            }
        }
        
    }

    //지도가 준비되었을 때 실행되는 콜백
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap?.let{
            val currentLocation = LatLng(currentLat, currentLng)
            it.setMaxZoomPreference(20.0f) //줌 최대값 설정
            it.setMinZoomPreference(12.0f) //줌 최솟값 설정
            it.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f))
        }

        setMarker()

        //플로팅 액션 버튼이 눌렸을 때
        binding.fabCurrentLocation.setOnClickListener {
            val locationProvider = LocationProvider(this@MapActivity)
            //위도와 경도 정보를 가져옴
            val latitude = locationProvider.getLocationLatitude()
            val longitude = locationProvider.getLocationLongitude()
            //지도의 위치를 움직일 수 있게함
            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 16f))
            setMarker()
        }
    }

    //지도에 마커 설정하는 함수
    private fun setMarker(){
        mMap?.let{
            it.clear() //지도에 있는 마커를 먼저 삭제함
            val markerOptions = MarkerOptions()
            markerOptions.position(it.cameraPosition.target) //마커 위치 설정
            markerOptions.title("마커 위치") //마커 이름 설정
            val marker = it.addMarker(markerOptions) //지도에 마커를 추가하고, 마커 객체를 반환함
            it.setOnCameraMoveListener {    //지도가 움직일 때 마커도 함께 움직이도록 함.
                marker?.let { marker ->
                    marker.setPosition(it.cameraPosition.target)
                }
            }
        }
    }

}