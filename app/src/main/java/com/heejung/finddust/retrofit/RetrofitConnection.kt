package com.heejung.finddust.retrofit

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitConnection {
    companion object{
        private const val BASE_URL = "https://api.airvisual.com/v2/" //API 서버의 주소를 BASE_URL로 설정함
        private  var INSTANCE: Retrofit? = null

        fun getInstance(): Retrofit {
            if(INSTANCE == null){   //null인 경우에만 생성
                INSTANCE = Retrofit.Builder()
                    .baseUrl(BASE_URL)  //API 베이스 URL 설정
                    .addConverterFactory(GsonConverterFactory.create()) //JSON 응답을 데이터 클래스 객체로 변환해줌
                    .build()
            }
            return INSTANCE!!
        }
    }
}