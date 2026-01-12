package com.ghostdev.huntit.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostdev.huntit.data.repository.AuthRepository
import com.ghostdev.huntit.data.model.User
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.profile_picture
import huntit.composeapp.generated.resources.profile_picture_2
import huntit.composeapp.generated.resources.profile_picture_3
import huntit.composeapp.generated.resources.profile_picture_4
import huntit.composeapp.generated.resources.profile_picture_5
import huntit.composeapp.generated.resources.profile_picture_6
import huntit.composeapp.generated.resources.profile_picture_7
import huntit.composeapp.generated.resources.profile_picture_8
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

data class HomeUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showProfileDialog: Boolean = false,
    val showLogoutConfirmation: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutComplete: Boolean = false
)

class HomeViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            try {

                val localUser = getLocalUserProfile()
                if (localUser != null) {
                    _uiState.update { it.copy(user = localUser) }
                }


                _uiState.update { it.copy(isLoading = true) }
                val result = authRepository.getUserProfile()

                if (result.isSuccess) {
                    val serverUser = result.getOrNull()!!
                    _uiState.update {
                        it.copy(user = serverUser, isLoading = false, errorMessage = null)
                    }
                } else {

                    if (localUser != null) {
                        _uiState.update { it.copy(isLoading = false) }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.exceptionOrNull()?.message
                                    ?: "Failed to load profile"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Unexpected error loading profile"
                    )
                }
            }
        }
    }

    private fun getLocalUserProfile(): User? {
        return authRepository.getLocalUserProfile()
    }

    fun showProfileDialog() {
        _uiState.update { it.copy(showProfileDialog = true) }
    }

    fun hideProfileDialog() {
        _uiState.update { it.copy(showProfileDialog = false) }
    }

    fun updateProfile(displayName: String, avatarId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }


            val currentUser = _uiState.value.user
            val nameChanged = currentUser?.displayName != displayName
            val avatarChanged = currentUser?.avatarId != avatarId



            if (nameChanged || avatarChanged) {
                val result = authRepository.updateProfile(displayName, avatarId)
                if (result.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.exceptionOrNull()?.message
                                ?: "Failed to update profile",
                            showProfileDialog = true
                        )
                    }
                    return@launch
                }


                loadUserProfile()
                _uiState.update {
                    it.copy(
                        successMessage = "Profile updated successfully",
                        showProfileDialog = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, showProfileDialog = false) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun showLogoutConfirmation() {
        _uiState.update {
            it.copy(
                showLogoutConfirmation = true,
                showProfileDialog = false
            )
        }
    }

    fun hideLogoutConfirmation() {
        _uiState.update { it.copy(showLogoutConfirmation = false) }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoggingOut = true) }

                delay(800)


                authRepository.logout()


                _uiState.update {
                    it.copy(
                        isLoggingOut = false,
                        successMessage = "Logged out successfully",
                        showLogoutConfirmation = false,
                        logoutComplete = true
                    )
                }

                delay(1000)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoggingOut = false,
                        errorMessage = "Failed to log out: ${e.message ?: "Unknown error"}",
                        showLogoutConfirmation = false
                    )
                }
            }
        }
    }

    fun resetLogoutState() {
        _uiState.update { it.copy(logoutComplete = false) }
    }

    companion object {
        fun getProfilePictureById(id: Int): DrawableResource {
            return when (id) {
                1 -> Res.drawable.profile_picture
                2 -> Res.drawable.profile_picture_2
                3 -> Res.drawable.profile_picture_3
                4 -> Res.drawable.profile_picture_4
                5 -> Res.drawable.profile_picture_5
                6 -> Res.drawable.profile_picture_6
                7 -> Res.drawable.profile_picture_7
                8 -> Res.drawable.profile_picture_8
                else -> Res.drawable.profile_picture
            }
        }
    }
}