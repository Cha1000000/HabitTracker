package com.habittracker.data.provider

import android.content.Context
import com.habittracker.domain.provider.StringProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StringProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StringProvider {
    override fun getString(resId: Int): String {
        return context.getString(resId)
    }

    override fun getString(resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
