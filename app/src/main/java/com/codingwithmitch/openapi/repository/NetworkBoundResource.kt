package com.codingwithmitch.openapi.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.codingwithmitch.openapi.models.AuthToken
import com.codingwithmitch.openapi.ui.DataState
import com.codingwithmitch.openapi.ui.Response
import com.codingwithmitch.openapi.ui.ResponseType
import com.codingwithmitch.openapi.ui.auth.state.AuthViewState
import com.codingwithmitch.openapi.util.*
import com.codingwithmitch.openapi.util.Constants.Companion.NETWORK_TIMEOUT
import com.codingwithmitch.openapi.util.Constants.Companion.TESTING_CACHE_DELAY
import com.codingwithmitch.openapi.util.Constants.Companion.TESTING_NETWORK_DELAY
import com.codingwithmitch.openapi.util.ErrorHandling.Companion.ERROR_CHECK_NETWORK_CONNECTION
import com.codingwithmitch.openapi.util.ErrorHandling.Companion.ERROR_UNKNOWN
import com.codingwithmitch.openapi.util.ErrorHandling.Companion.UNABLE_TODO_OPERATION_WO_INTERNET
import com.codingwithmitch.openapi.util.ErrorHandling.Companion.UNABLE_TO_RESOLVE_HOST
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main

abstract class NetworkBoundResource<ResponseObject, ViewStateType> (
    isNetworkAvailable: Boolean, // is there a network connection?
    isNetworkRequest: Boolean // is this a network request
) {
    private val TAG = "NetworkBoundResource"

    protected val result = MediatorLiveData<DataState<ViewStateType>>()
    protected lateinit var job: CompletableJob
    protected lateinit var coroutineScope: CoroutineScope

    init {
        setJob(initNewJob())
        setValue(DataState.loading(isLoading = true, cachedData = null))

        if (isNetworkRequest) {
            if (isNetworkAvailable) {
                coroutineScope.launch {
                    // simulate a network delay for testing
                    delay(TESTING_NETWORK_DELAY)

                    withContext(Main) {
                        // make network call
                        val apiResponse = createCall()
                        result.addSource(apiResponse) { response ->
                            result.removeSource(apiResponse)

                            coroutineScope.launch {
                                handleNetworkCall(response)
                            }
                        }
                    }
                }
                GlobalScope.launch(IO) {
                    delay(NETWORK_TIMEOUT)

                    if (!job.isCompleted) {
                        Log.e(TAG, "JOB NETWORK TIMEOUT " )
                        job.cancel(CancellationException(UNABLE_TO_RESOLVE_HOST))
                    }
                }
            } else {
                onErrorReturn(UNABLE_TODO_OPERATION_WO_INTERNET, shouldUseDialog = true, shouldUseToast = false)
            }
        } else {
            coroutineScope.launch {

                // fake delay for testing cache
                delay(TESTING_CACHE_DELAY)

                // View data from cache ONLY and return
                createCacheRequestAndReturn()
            }
        }


    }

    suspend fun handleNetworkCall(response: GenericApiResponse<ResponseObject>?) {
        when(response) {

            is ApiSuccessResponse -> {
                handleApiSuccessResponse(response)
            }

            is ApiEmptyResponse -> {
                Log.e(TAG, "handleNetworkCall: Request returned NOTHING (HTTP 204)")
                onErrorReturn(ERROR_UNKNOWN, shouldUseDialog = true, shouldUseToast = false)
            }

            is ApiErrorResponse -> {
                Log.e(TAG, "handleNetworkCall: ${response.errorMessage}" )
                onErrorReturn(response.errorMessage, shouldUseDialog = true, shouldUseToast = false)
            }
        }
    }

    fun onCompleteJob(dataState: DataState<ViewStateType>) {
        GlobalScope.launch(Main) { 
            job.complete()
            setValue(dataState)
        }
    }

    private fun setValue(dataState: DataState<ViewStateType>) {
        result.value = dataState
    }

    fun onErrorReturn(errorMessage: String?, shouldUseDialog: Boolean, shouldUseToast: Boolean) {
        var msg = errorMessage
        var useDialog = shouldUseDialog
        var responseType: ResponseType = ResponseType.None()
        if (msg == null) {
            msg = ERROR_UNKNOWN
        } else if (ErrorHandling.isNetworkError(msg)) {
            msg = ERROR_CHECK_NETWORK_CONNECTION
            useDialog = false
        }
        if (shouldUseToast) {
            responseType = ResponseType.Toast()
        }
        if (useDialog) {
            responseType = ResponseType.Dialog()
        }

        onCompleteJob(DataState.error(
            response = Response(
                message = msg,
                responseType = responseType
            )
        ))
    }

    @UseExperimental(InternalCoroutinesApi::class)
    private fun initNewJob(): Job {
        Log.d(TAG, "initNewJob: called...")
        job = Job()
        job.invokeOnCompletion(onCancelling = true, invokeImmediately = true, handler = object : CompletionHandler{
            override fun invoke(cause: Throwable?) {

                if (job.isCancelled) {
                    Log.e(TAG, "invoke: Job has been cancelled." )
                    cause?.let{
                        onErrorReturn(it.message, shouldUseDialog = false, shouldUseToast = true)
                    }?: onErrorReturn(ERROR_UNKNOWN, shouldUseDialog = false, shouldUseToast = true)
                } else if (job.isCompleted) {
                    Log.e(TAG, "invoke: Job has been completed")
                    // Do nothing. Should be handled already.
                }
            }
        })
        coroutineScope = CoroutineScope(IO + job)
        return job
    }

    fun asLiveData() = result as LiveData<DataState<ViewStateType>>

    abstract suspend fun createCacheRequestAndReturn()

    abstract suspend fun handleApiSuccessResponse(response: ApiSuccessResponse<ResponseObject>)

    abstract fun createCall(): LiveData<GenericApiResponse<ResponseObject>>

    abstract fun setJob(job: Job)
}