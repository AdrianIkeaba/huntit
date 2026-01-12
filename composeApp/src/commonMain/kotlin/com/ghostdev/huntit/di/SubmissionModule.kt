package com.ghostdev.huntit.di

import com.ghostdev.huntit.ui.screens.game.SubmissionViewModel
import com.ghostdev.huntit.utils.ImageProcessor
import org.koin.dsl.module

val submissionModule = module {
    factory { ImageProcessor() }
    single { SubmissionViewModel() }
}