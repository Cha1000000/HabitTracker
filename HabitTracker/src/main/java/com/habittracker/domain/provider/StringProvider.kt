package com.habittracker.domain.provider

interface StringProvider {
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg args: Any): String
}
