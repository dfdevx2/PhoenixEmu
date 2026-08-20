package com.dfdx047.phoenixemu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dfdx047.phoenixemu.R

// 1. Criamos a família da fonte apontando o MESMO arquivo para pesos diferentes.
// Como é uma Fonte Variável, o Compose extrai o peso certo automaticamente!
val GoogleSans = FontFamily(
    Font(R.font.google_sans, FontWeight.Normal),
    Font(R.font.google_sans, FontWeight.Medium),
    Font(R.font.google_sans, FontWeight.Bold)
)

// 2. Aplicamos a Google Sans como padrão para todas as tipografias do Material Design 3
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)