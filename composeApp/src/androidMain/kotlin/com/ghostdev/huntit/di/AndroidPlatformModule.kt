package com.ghostdev.huntit.di

import android.content.Context
import com.ghostdev.huntit.data.repository.AndroidSoundSettingsRepositoryImpl
import com.ghostdev.huntit.data.repository.SoundSettingsRepository
import com.ghostdev.huntit.utils.AudioPlayer
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidPlatformModule(applicationContext: Context): Module = module {
    single<SoundSettingsRepository> { 
        AndroidSoundSettingsRepositoryImpl(get(), applicationContext)
    }
}