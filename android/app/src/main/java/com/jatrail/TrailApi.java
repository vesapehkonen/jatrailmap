package com.jatrail;

import retrofit2.Call;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import retrofit2.http.POST;
import retrofit2.http.Url;

import java.util.List;

public interface TrailApi {
    @Multipart
    @POST
    Call<TrailUploadModels.UploadResponse> uploadTrail(
            @Url String url,
            @Part("manifest") RequestBody manifest,
            @Part List<MultipartBody.Part> photos);
}
