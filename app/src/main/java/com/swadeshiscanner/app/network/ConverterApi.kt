package com.swadeshiscanner.app.network

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ConverterApi {

    // --- EXPORT: PDF -> FORMAT ---
    @Multipart
    @POST("/pdf-to-word")
    suspend fun convertPdfToWord(@Part file: MultipartBody.Part): Response<ResponseBody>

    @Multipart
    @POST("/pdf-to-excel")
    suspend fun convertPdfToExcel(@Part file: MultipartBody.Part): Response<ResponseBody>

    @Multipart
    @POST("/pdf-to-ppt")
    suspend fun convertPdfToPpt(@Part file: MultipartBody.Part): Response<ResponseBody>

    // --- IMPORT: FORMAT -> PDF ---
    @Multipart
    @POST("/word-to-pdf")
    suspend fun convertWordToPdf(@Part file: MultipartBody.Part): Response<ResponseBody>

    @Multipart
    @POST("/excel-to-pdf")
    suspend fun convertExcelToPdf(@Part file: MultipartBody.Part): Response<ResponseBody>

    @Multipart
    @POST("/ppt-to-pdf")
    suspend fun convertPptToPdf(@Part file: MultipartBody.Part): Response<ResponseBody>

    companion object {
        // Ensure this URL matches your active Hugging Face space
        private const val BASE_URL = "https://jsujalkumar7899-swadeshi-converter.hf.space"

        fun create(): ConverterApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ConverterApi::class.java)
        }
    }
}