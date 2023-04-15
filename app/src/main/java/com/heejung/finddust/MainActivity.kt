package com.heejung.finddust

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.heejung.finddust.databinding.ActivityMainBinding
import com.heejung.finddust.retrofit.FinddustResponse
import com.heejung.finddust.retrofit.FinddustService
import com.heejung.finddust.retrofit.RetrofitConnection
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding //뷰 바인딩 설정

    private val PERMISSIONS_REQUEST_CODE = 100

    //요청할 권한 목록
    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent> //위치 서비스 요청에 필요한 런처

    lateinit var locationProvider: LocationProvider //위도와 경도를 가져옴

    //위도와 경도 저장하는 변수 생성 --> MapActivity.kt로 보내고 다시 받아와야 하기 때문에 변수로 설정함
    var latitude: Double = 0.0
    var longitude: Double = 0.0

    //메인 페이지의 위치 정보를 지도 페이지로, 지도 페이지의 위치 정보를 메인 페이지로 보냄
    val startMapActivityResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            object : ActivityResultCallback<ActivityResult> {
                override fun onActivityResult(result: ActivityResult?) {
                    if (result?.resultCode ?: RESULT_CANCELED == RESULT_OK) {
                        latitude = result?.data?.getDoubleExtra("latitude", 0.0) ?: 0.0
                        longitude = result?.data?.getDoubleExtra("longitude", 0.0) ?: 0.0
                        updateUI()  //다시 위에 저장된 위도와 경도 정보를 이용해 미세먼지 농도를 구함
                    }
                }
            })

    //전면 광고 변수
    var mInterstitialAd : InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater) //뷰 바인딩 설정
        setContentView(binding.root)

        checkAllPermissions() //권한 확인
        updateUI()
        setRefreshButton()

        setFab()

        setBannerAds()

    }

    //map 액티비티에서 돌아올 때 setInterstitialAds()가 호출됨
    override fun onResume() {
        super.onResume()
        setInterstitialAds()
    }

    //전면 광고 설정 함수
    private fun setInterstitialAds(){
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this,"ca-app-pub-3418534336332076/2184364889", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("ads log", "전면 광고가 로드 실패했습니다. ${adError.responseInfo}")
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d("ads log", "전면 광고가 로드되었습니다.")
                mInterstitialAd = interstitialAd
            }
        })
    }

    //하단 배너 광고 설정 함수
    private fun setBannerAds(){
        MobileAds.initialize(this)  //광고 SDK 초기화
        val adRequest = AdRequest.Builder().build() //AdRequest 객체 생성
        binding.adView.loadAd(adRequest)    //adView에 광고 로드

        //adView 리스너 추가
        binding.adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("ads log","배너 광고가 로드되었습니다.")
            }

            override fun onAdFailedToLoad(adError : LoadAdError) {
                Log.d("ads log","배너 광고가 로드 실패했습니다. ${adError.responseInfo} ${adError.code}")
            }

            override fun onAdOpened() {
                Log.d("ads log","배너 광고를 열었습니다.") //전면에 광고가 오버레이 되었을 때
            }

            override fun onAdClicked() {
                Log.d("ads log","배너 광고를 클릭했습니다.")
            }

            override fun onAdClosed() {
                Log.d("ads log", "배너 광고를 닫았습니다.")
            }
        }
    }

    //현재 위도와 경도 정보를 담아 지도 페이지로 보내는 함수
    private fun setFab() {
        binding.fab.setOnClickListener {
            if (mInterstitialAd != null) {
                mInterstitialAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("ads log", "전면 광고가 닫혔습니다.")

                        //전면 광고가 닫히고 나서 실행됨
                        val intent = Intent(this@MainActivity, MapActivity::class.java)
                        intent.putExtra("currentLat", latitude)
                        intent.putExtra("currentLng", longitude)
                        startMapActivityResult.launch(intent)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError?) {
                        Log.d("ads log", "전면 광고가 열리는 데 실패했습니다.")
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("ads log", "전면 광고가 성공적으로 열렸습니다.")
                        mInterstitialAd = null
                    }
                }
                mInterstitialAd!!.show(this@MainActivity)
            } else {
                Log.d("InterstitialAd", "전면 광고가 로딩되지 않았습니다.")
                Toast.makeText(
                    this@MainActivity,
                    "잠시 후 다시 시도해주세요.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    //새로고침 버튼을 누르면 updateUI() 함수를 실행하여 새로고침 함
    private fun setRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            updateUI()
        }
    }

    //위도와 경도 정보를 LocationProvider를 통해 가져오는 함수
    private fun updateUI() {
        locationProvider = LocationProvider(this@MainActivity)

        //변수를 이용해 위도와 경도 정보를 가져옴
        if (latitude == 0.0 || longitude == 0.0) {
            latitude = locationProvider.getLocationLatitude()
            longitude = locationProvider.getLocationLongitude()
        }

        if (latitude != 0.0 || longitude != 0.0) {

            //현재 위치 가져오기
            val address = getCurrentAddress(latitude, longitude)
            //주소가 null 이 아닐 경우 UI 업데이트
            address?.let {
                binding.tvLocationTitle.text = "${it.thoroughfare}"
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}"
            }

            //updateUI()에서 가져온 정보로 현재 미세먼지 농도 가져오고 UI 업데이트
            getFinddustData(latitude, longitude)

        } else {
            Toast.makeText(this@MainActivity,
                "위도, 경도 정보를 가져올 수 없었습니다. 새로고침을 눌러주세요.", Toast.LENGTH_LONG
            ).show()
        }
    }

    //레트로핏 클래스를 이용하여 미세먼지 오염 정보를 가져옴
    private fun getFinddustData(latitude: Double, longitude: Double) {
        val retrofitAPI = RetrofitConnection.getInstance().create(FinddustService::class.java)

        retrofitAPI.getAirQualityData(latitude.toString(), longitude.toString(), 
            "f39c119c-e79a-4e8a-b81a-c322fbcb149c")
            .enqueue(object : Callback<FinddustResponse> {
                override fun onResponse(
                    call: Call<FinddustResponse>,
                    response: Response<FinddustResponse>,
                ) { //정상적인 response가 왔다면 UI 업데이트
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, 
                            "최신 정보 업데이트 완료!", Toast.LENGTH_SHORT).show()
                        //만약 response.body()가 null이 아니라면 updateAirUI()
                        response.body()?.let { updateAirUI(it) }
                    } else {
                        Toast.makeText(this@MainActivity, 
                            "업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<FinddustResponse>, t: Throwable) {
                    t.printStackTrace()
                }
            })
    }
    
    //가져온 데이터 정보를 바탕으로 화면을 업데이트함
    private fun updateAirUI(airQualityData: FinddustResponse) {
        val pollutionData = airQualityData.data.current.pollution

        //미세먼지 수치 지정
        binding.tvCount.text = pollutionData.aqius.toString()

        //서울 시간대 적용
        val dateTime = ZonedDateTime.parse(pollutionData.ts).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()
        //"2023-03-04T14:00:00.000Z" 형식을  "2023-03-04 23:00"로 변경
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        binding.tvCheckTime.text = dateTime.format(dateFormatter).toString()

        when (pollutionData.aqius) {
            in 0..50 -> {
                binding.tvTitle.text = "좋음"
                binding.imgBg.setImageResource(R.drawable.bg_good)
            }

            in 51..150 -> {
                binding.tvTitle.text = "보통"
                binding.imgBg.setImageResource(R.drawable.bg_soso)
            }

            in 151..200 -> {
                binding.tvTitle.text = "나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_bad)
            }

            else -> {
                binding.tvTitle.text = "매우 나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_worst)
            }
        }
    }

    //지오코딩을 통해 위도와 경도로부터 주소를 가져옴.
    fun getCurrentAddress(latitude: Double, longitude: Double): Address? {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses: List<Address>?

        addresses = try {
            //Geocoder 객체를 이용하여 위도와 경도로부터 리스트를 가져옴.
            geocoder.getFromLocation(latitude, longitude, 7)
        } catch (ioException: IOException) {
            Toast.makeText(this, "지오코더 서비스 사용불가합니다.",
                Toast.LENGTH_LONG).show()
            return null
        } catch (illegalArgumentException: IllegalArgumentException) {
            Toast.makeText(this, "잘못된 위도, 경도 입니다.",
                Toast.LENGTH_LONG).show()
            return null
        }

        //에러는 아니지만 주소가 발견되지 않은 경우
        if (addresses == null || addresses.size == 0) {
            Toast.makeText(this, "주소가 발견되지 않았습니다.",
                Toast.LENGTH_LONG).show()
            return null
        }

        val address: Address = addresses[0]

        return address
    }

    private fun checkAllPermissions() {
        //1. 위치 서비스(GPS)가 켜져있는지 확인
        if (!isLocationServicesAvailable()) {
            showDialogForLocationServiceSetting()
            //2. 런타임 앱 권한이 모두 허용되어 있는지 확인
        } else {
            isRunTimePermissionsGranted()
        }
    }

    //위치 서비스가 켜져 있는지 체크
    fun isLocationServicesAvailable(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        //GPS 프로바이더나 NETWORK 프로바이더 중 하나로 설정되어 있다면 true를 반환함. (위치 서비스가 켜져 있는 것)
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    //위치 퍼미션을 가지고 있는지 체크
    fun isRunTimePermissionsGranted() {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED || hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            //권한이 한 개라도 없다면 퍼미션을 요청함
            ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.size == REQUIRED_PERMISSIONS.size) {

            //요청 코드가 PERMISSIONS_REQUEST_CODE 이고, 요청한 퍼미션 개수만큼 수신되었다면
            var checkResult = true

            //모든 퍼미션을 허용했는지 체크함
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }

            if (checkResult) {
                //위치 값을 가져올 수 있음
                updateUI()
            } else {    //퍼미션이 거부되었다면 앱을 종료함.
                Toast.makeText(this@MainActivity, "위치 서비스 동의가 거부되었습니다. 앱을 다시 실행하여 위치 서비스를 허용해주세요.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    //위치 서비스가 꺼져 있다면, 다이얼로그를 사용하여 위치 서비스를 설정하도록 함
    private fun showDialogForLocationServiceSetting() {
        getGPSPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            //결과 값을 받았을 때의 로직
            if (result.resultCode == RESULT_OK) {
                //사용자가 GPS 를 활성화 시켰는지 확인함
                if (isLocationServicesAvailable()) {
                    isRunTimePermissionsGranted()
                } else {    //위치 서비스가 허용되지 않았다면 앱을 종료함.
                    Toast.makeText(this@MainActivity, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                    finish()    //액티비티 종료
                }
            }
        }

        //사용자에게 의사를 물어보는 AlertDialog
        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage("위치 서비스가 꺼져있습니다. 설정해야 앱을 사용할 수 있습니다.")
        builder.setCancelable(true)     //다이얼로그 창 바깥을 터치하면 창을 닫음.
        builder.setPositiveButton("설정", DialogInterface.OnClickListener { dialog, id ->
            //확인 버튼 설정
            val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            getGPSPermissionLauncher.launch(callGPSSettingIntent)
        })
        builder.setNegativeButton("취소", DialogInterface.OnClickListener { dialog, id ->
            //취소 버튼 설정
            dialog.cancel()
            Toast.makeText(this@MainActivity, "기기에서 위치서비스(GPS) 설정 후 사용해주세요.", Toast.LENGTH_SHORT).show()
            finish()
        })
        builder.create().show()     //다이얼로그 생성 후 보여줌.
    }
}
