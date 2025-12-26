package com.lonx.lyrico

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import com.lonx.lyrico.ui.theme.LyricoTheme
import com.lonx.lyrico.utils.PermissionUtil
import com.lonx.lyrico.viewmodel.SettingsViewModel
import org.koin.android.ext.android.inject

open class MainActivity : ComponentActivity() {

    @JvmField
    protected var hasPermission = false
    private val settingsViewModel: SettingsViewModel by inject()

    private lateinit var openDirectoryLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasPermission = PermissionUtil.hasNecessaryPermission(this)
        if (!hasPermission) {

            XXPermissions.with(this)
                // 申请多个权限
                .permission(PermissionLists.getReadMediaAudioPermission())

                .request(object : OnPermissionCallback {

                    override fun onResult(grantedList: MutableList<IPermission>, deniedList: MutableList<IPermission>) {
                        val allGranted = deniedList.isEmpty()
                        if (!allGranted) {
                            // 判断请求失败的权限是否被用户勾选了不再询问的选项
                            Toast.makeText(this@MainActivity, "已拒绝权限", Toast.LENGTH_SHORT).show()
                            return
                        }

                    }


                })
        }
        openDirectoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                // Persist access permissions for the URI
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)

                settingsViewModel.addScannedFolder(it.toString())
            }
        }

        enableEdgeToEdge()
        setContent {
            LyricoTheme {
                LyricoApp(
                )
            }
        }
    }
}