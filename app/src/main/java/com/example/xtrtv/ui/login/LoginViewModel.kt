package com.example.xtrtv.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xtrtv.api.ApiClient
import com.example.xtrtv.data.Prefs
import com.example.xtrtv.data.UserData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    var url by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private val _loginSuccess = MutableSharedFlow<UserData>()
    val loginSuccess = _loginSuccess.asSharedFlow()

    fun login() {
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            errorMessage = "Fyll i alla fält"
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val formattedUrl = if (!url.startsWith("http")) "http://$url" else url
                val apiService = ApiClient.createService(formattedUrl)
                val response = apiService.login(username, password)

                if (response.isSuccessful && response.body()?.userInfo?.auth == 1) {
                    _loginSuccess.emit(UserData(formattedUrl, username, password))
                } else {
                    errorMessage = "Felaktiga uppgifter"
                }
            } catch (e: Exception) {
                errorMessage = "Ett fel uppstod: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }
}
