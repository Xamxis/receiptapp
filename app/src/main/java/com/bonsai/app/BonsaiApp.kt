package com.bonsai.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BonsaiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PDFBox lädt seine Schrift-Ressourcen aus den Assets und muss dafür
        // einmalig initialisiert werden – sonst schlägt der PDF-Export zur Laufzeit fehl.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
