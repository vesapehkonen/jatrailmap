package com.jatrailmap.justanothertrailmap;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Url;

public interface TrailApi {
    @POST
    Call<TrailUploadModels.UploadResponse> uploadTrail(
            @Url String url,
            @Body TrailUploadModels.UploadRequest request);
}
