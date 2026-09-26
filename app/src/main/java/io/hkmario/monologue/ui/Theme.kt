package io.hkmario.monologue.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import io.hkmario.monologue.domain.AppSettingsUiState

val PaperColors=lightColorScheme(
    primary=Color(0xFF9F412C),onPrimary=Color.White,primaryContainer=Color(0xFFF3DDD1),onPrimaryContainer=Color(0xFF542718),inversePrimary=Color(0xFFFFB59E),
    secondary=Color(0xFF655A50),onSecondary=Color.White,secondaryContainer=Color(0xFFEADFD2),onSecondaryContainer=Color(0xFF30271F),
    tertiary=Color(0xFF4B6551),onTertiary=Color.White,tertiaryContainer=Color(0xFFDCE8D8),onTertiaryContainer=Color(0xFF233C29),
    background=Color(0xFFF5EFE4),onBackground=Color(0xFF2E2722),surface=Color(0xFFFBF7EF),onSurface=Color(0xFF2E2722),
    surfaceVariant=Color(0xFFEEE5D9),onSurfaceVariant=Color(0xFF655A50),surfaceTint=Color(0xFF9F412C),
    inverseSurface=Color(0xFF332D28),inverseOnSurface=Color(0xFFF8F0E5),
    error=Color(0xFF9C2039),onError=Color.White,errorContainer=Color(0xFFFFDADF),onErrorContainer=Color(0xFF590018),
    outline=Color(0xFF8A7C6E),outlineVariant=Color(0xFFD8CABC),scrim=Color.Black,
    surfaceBright=Color(0xFFFBF7EF),surfaceDim=Color(0xFFE0D8CC),surfaceContainerLowest=Color(0xFFFFFFFF),surfaceContainerLow=Color(0xFFF8F1E8),surfaceContainer=Color(0xFFEEE5D9),surfaceContainerHigh=Color(0xFFE9DFD2),surfaceContainerHighest=Color(0xFFE2D7CA)
)
val NightColors=darkColorScheme(
    primary=Color(0xFFFFB59E),onPrimary=Color(0xFF542718),primaryContainer=Color(0xFF713420),onPrimaryContainer=Color(0xFFF3DDD1),inversePrimary=Color(0xFFA74932),
    secondary=Color(0xFFD1C2B3),onSecondary=Color(0xFF372D24),secondaryContainer=Color(0xFF504438),onSecondaryContainer=Color(0xFFEADFD2),
    tertiary=Color(0xFFB7CFB8),onTertiary=Color(0xFF233C29),tertiaryContainer=Color(0xFF384F3C),onTertiaryContainer=Color(0xFFDCE8D8),
    background=Color(0xFF1A1714),onBackground=Color(0xFFF0E7DC),surface=Color(0xFF211D19),onSurface=Color(0xFFF0E7DC),surfaceVariant=Color(0xFF443C34),onSurfaceVariant=Color(0xFFD1C2B3),surfaceTint=Color(0xFFFFB59E),
    inverseSurface=Color(0xFFF0E7DC),inverseOnSurface=Color(0xFF332D28),error=Color(0xFFFFB1C0),onError=Color(0xFF590018),errorContainer=Color(0xFF7B1730),onErrorContainer=Color(0xFFFFDADF),
    outline=Color(0xFFA69687),outlineVariant=Color(0xFF54473A),scrim=Color.Black,
    surfaceBright=Color(0xFF403832),surfaceDim=Color(0xFF191512),surfaceContainerLowest=Color(0xFF140F0C),surfaceContainerLow=Color(0xFF211D19),surfaceContainer=Color(0xFF29231E),surfaceContainerHigh=Color(0xFF342C25),surfaceContainerHighest=Color(0xFF3D342C)
)
private val serif=FontFamily.Serif
private val sans=FontFamily.SansSerif
val MonologueTypography=Typography(
    displayLarge=TextStyle(fontFamily=serif,fontWeight=FontWeight.Normal,fontSize=52.sp,lineHeight=58.sp),displayMedium=TextStyle(fontFamily=serif,fontWeight=FontWeight.Normal,fontSize=44.sp,lineHeight=50.sp),displaySmall=TextStyle(fontFamily=serif,fontWeight=FontWeight.Normal,fontSize=36.sp,lineHeight=42.sp),
    headlineLarge=TextStyle(fontFamily=serif,fontWeight=FontWeight.Normal,fontSize=32.sp,lineHeight=38.sp),headlineMedium=TextStyle(fontFamily=serif,fontWeight=FontWeight.Normal,fontSize=28.sp,lineHeight=34.sp),headlineSmall=TextStyle(fontFamily=serif,fontWeight=FontWeight.Normal,fontSize=24.sp,lineHeight=30.sp),
    titleLarge=TextStyle(fontFamily=serif,fontWeight=FontWeight.Medium,fontSize=22.sp,lineHeight=28.sp),titleMedium=TextStyle(fontFamily=sans,fontWeight=FontWeight.Medium,fontSize=16.sp,lineHeight=24.sp),titleSmall=TextStyle(fontFamily=sans,fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=20.sp),
    bodyLarge=TextStyle(fontFamily=sans,fontWeight=FontWeight.Normal,fontSize=16.sp,lineHeight=24.sp),bodyMedium=TextStyle(fontFamily=sans,fontWeight=FontWeight.Normal,fontSize=14.sp,lineHeight=21.sp),bodySmall=TextStyle(fontFamily=sans,fontWeight=FontWeight.Normal,fontSize=12.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontFamily=sans,fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=20.sp),labelMedium=TextStyle(fontFamily=sans,fontWeight=FontWeight.Medium,fontSize=12.sp,lineHeight=18.sp),labelSmall=TextStyle(fontFamily=sans,fontWeight=FontWeight.Medium,fontSize=11.sp,lineHeight=16.sp)
)
val MonologueShapes=Shapes(RoundedCornerShape(4.dp),RoundedCornerShape(8.dp),RoundedCornerShape(14.dp),RoundedCornerShape(20.dp),RoundedCornerShape(28.dp))
object Dimensions { val page=24.dp; val compactPage=20.dp; val touch=48.dp; val play=76.dp; val mini=72.dp; val gap=8.dp; val card=14.dp; val divider=1.dp }
val TimeStyle=TextStyle(fontFamily=sans,fontSize=12.sp,fontFeatureSettings="tnum")
@Composable fun MonologueTheme(settings: AppSettingsUiState=AppSettingsUiState(), content: @Composable ()->Unit) {
    val dark=when(settings.text("theme","paper")) { "dark" -> true; "system" -> isSystemInDarkTheme(); else -> false }
    val colors=if(settings.bool("dynamic") && Build.VERSION.SDK_INT>=31) { if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current) } else if(dark) NightColors else PaperColors
    MaterialTheme(colorScheme=colors,typography=MonologueTypography,shapes=MonologueShapes) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides colors.onBackground,content=content)
    }
}
