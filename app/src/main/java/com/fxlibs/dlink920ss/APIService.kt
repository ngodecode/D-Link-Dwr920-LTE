package com.fxlibs.dlink920ss

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface APIService {
    @GET("data.ria?DynUpdate=up_5s")
    fun getStatus(@Query("_") time:Long): Call<ResponseBody>
}
