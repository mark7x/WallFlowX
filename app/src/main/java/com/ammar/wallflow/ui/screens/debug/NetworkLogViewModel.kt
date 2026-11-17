package com.ammar.wallflow.ui.screens.debug

import androidx.lifecycle.ViewModel
import com.ammar.wallflow.data.debug.NetworkLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NetworkLogViewModel @Inject constructor(
    private val networkLogRepository: NetworkLogRepository,
) : ViewModel() {
    val entries = networkLogRepository.entries

    fun clear() = networkLogRepository.clear()
}
