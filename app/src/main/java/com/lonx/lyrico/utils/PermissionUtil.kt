package com.lonx.lyrico.utils

import android.app.Activity
import android.content.Context
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists

object PermissionUtil {

    /**
     * 普通必要权限判断（比如读取媒体音频）
     */
    fun hasNecessaryPermission(context: Context): Boolean {
        val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            PermissionLists.getReadMediaAudioPermission()
        } else {
            // 低于 Android 13 使用老的外部存储读权限
            PermissionLists.getReadExternalStoragePermission()
        }

        return XXPermissions.isGrantedPermission(context, perms)
    }



    /**
     * 判断是否拥有管理所有文件权限（MANAGE_EXTERNAL_STORAGE）
     */
    fun hasManageExternalStoragePermission(context: Context): Boolean {
        // XXPermissions 内部已处理 Android 版本兼容逻辑
        return XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getManageExternalStoragePermission()
        )
    }

    /**
     * 请求管理所有文件权限
     */
    fun requestManageExternalStorage(
        activity: Activity,
        callback: OnPermissionCallback
    ) {
        XXPermissions.with(activity)
            .permission(PermissionLists.getManageExternalStoragePermission())
            .request(callback)
    }
}
