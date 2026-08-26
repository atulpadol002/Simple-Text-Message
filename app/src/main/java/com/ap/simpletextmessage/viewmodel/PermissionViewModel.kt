package com.ap.simpletextmessage.viewmodel

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS
    )

    private val _runtimePermissionsGranted = MutableStateFlow(false)
    val runtimePermissionsGranted: StateFlow<Boolean> = _runtimePermissionsGranted.asStateFlow()

    private val _isDefaultSmsApp = MutableStateFlow(false)
    val isDefaultSmsApp: StateFlow<Boolean> = _isDefaultSmsApp.asStateFlow()

    private val _isRequestingDefaultRole = MutableStateFlow(false)
    val isRequestingDefaultRole: StateFlow<Boolean> = _isRequestingDefaultRole.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        _runtimePermissionsGranted.value = checkRuntimePermissions()
        _isDefaultSmsApp.value = checkIsDefaultSmsApp()
    }

    private fun checkRuntimePermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkIsDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    fun getDefaultRoleRequestIntentOrNull(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
            if (intent != null) {
                _isRequestingDefaultRole.value = true
            }
            intent
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
            _isRequestingDefaultRole.value = true
            intent
        }
    }

    fun onDefaultRoleRequestFinished() {
        _isRequestingDefaultRole.value = false
        refreshStatus()
    }
}
